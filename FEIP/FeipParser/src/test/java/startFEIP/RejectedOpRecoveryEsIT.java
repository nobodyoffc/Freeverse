package startFEIP;

import clients.EsClientMaker;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import constants.IndicesNames;
import construct.ConstructParser;
import data.fchData.OpReturn;
import data.feipData.Code;
import data.feipData.CodeHistory;
import data.feipData.Feip;
import org.junit.jupiter.api.*;

import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Replays the 2026-10-08 stop at height 3470658 against a real Elasticsearch: a Code publish whose
 * home is a map, on indices whose mapping still says text. Parsing it fails, which is fine (the op
 * is skipped), but the partial-op rollback used to re-index the same history, fail the same way
 * and stop the parser. Runs only against the scratch cluster, like {@code PersonalRollbackEsIT}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RejectedOpRecoveryEsIT {

    static final String SCRATCH_CLUSTER = "feip-it-scratch";
    private ElasticsearchClient es;

    @BeforeAll
    void connect() {
        String host = System.getProperty("feip.it.host", "127.0.0.1");
        int port = Integer.parseInt(System.getProperty("feip.it.port", "9291"));
        try {
            es = new EsClientMaker().getClientHttp(host, port);
            String cluster = es.info().clusterName();
            Assumptions.assumeTrue(SCRATCH_CLUSTER.equals(cluster),
                    "Refusing to run: cluster is '" + cluster + "', not the scratch cluster " + SCRATCH_CLUSTER);
        } catch (Exception e) {
            Assumptions.abort("No scratch Elasticsearch at " + host + ":" + port + ": " + e.getMessage());
        }
    }

    /** All FEIP indices, with code and code_history mapped as before the fix: home is text. */
    @BeforeEach
    void oldMappings() throws Exception {
        IndicesFEIP.deleteAllIndices(es);
        IndicesFEIP.createAllIndices(es);
        for (String index : List.of(IndicesNames.CODE, IndicesNames.CODE_HISTORY)) {
            es.indices().delete(d -> d.index(index));
            es.indices().create(c -> c.index(index).withJson(new StringReader(
                    "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"keyword\"},\"home\":{\"type\":\"text\"}}}}")));
        }
    }

    private CodeHistory freerForMacPublish(long height) {
        OpReturn opre = new OpReturn();
        opre.setId("e7fee950309196f7d10109a1b9aab7ca95aa75087cad2852d1dee8b95644a256");
        opre.setHeight(height);
        opre.setTxIndex(1);
        opre.setTime(1_780_000_000L);
        opre.setSigner("FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7");
        opre.setCdd(10L);
        Map<String, Object> data = new HashMap<>();
        data.put("op", "publish");
        data.put("name", "FreerForMac");
        data.put("ver", "v0.4.1");
        data.put("home", Map.of("src", "https://github.com/nobodyoffc/freer-mac/tree/v0.4.1",
                "zip", "https://github.com/nobodyoffc/freer-mac/releases/download/v0.4.1/FreerForMac-v0.4.1.zip"));
        Feip feip = new Feip();
        feip.setType("FEIP");
        feip.setSn("2");
        feip.setData(data);
        CodeHistory hist = new ConstructParser().makeCode(opre, feip);
        assertNotNull(hist);
        return hist;
    }

    @Test
    void aRefusedOpIsRolledBackInsteadOfStoppingTheParser() throws Exception {
        long height = 3_470_658L;
        CodeHistory hist = freerForMacPublish(height);

        assertThrows(ElasticsearchException.class, () -> new ConstructParser().parseCode(es, hist),
                "the op itself is refused, as in the incident");

        // This threw before the fix, and the parser stopped.
        FileParser.rollBackPartialOp(es, height, FileParser.HistRef.of(IndicesNames.CODE_HISTORY, hist));

        es.indices().refresh(r -> r.index(IndicesNames.CODE, IndicesNames.CODE_HISTORY));
        assertFalse(es.get(g -> g.index(IndicesNames.CODE).id(hist.getCodeId()), Code.class).found());
        assertFalse(es.get(g -> g.index(IndicesNames.CODE_HISTORY).id(hist.getId()), CodeHistory.class).found(),
                "the rollback removes the history it wrote to see what the op touched");
    }

    @Test
    void aRefusedFieldIsDroppedFromTheHistory() throws Exception {
        CodeHistory hist = freerForMacPublish(3_470_658L);
        List<String> dropped = RejectedFields.index(es, IndicesNames.CODE_HISTORY, hist.getId(), hist, true);
        assertEquals(List.of("home"), dropped);
        CodeHistory stored = es.get(g -> g.index(IndicesNames.CODE_HISTORY).id(hist.getId()), CodeHistory.class).source();
        assertNotNull(stored);
        assertEquals("FreerForMac", stored.getName());
        assertNull(stored.getHome());
    }
}
