package personal;

import clients.EsClientMaker;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.IndicesNames;
import data.fcData.FcEntity;
import data.feipData.*;
import data.fchData.OpReturn;
import org.junit.jupiter.api.*;
import startFEIP.IndicesFEIP;
import utils.EsUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rollback of Contact, Mail and Secret against a real Elasticsearch: every kind of op applied
 * through the same make -> parse -> write-history steps FileParser uses, then rolled back.
 *
 * The parsers write to the real index names (contact, mail, secret, ...), so this deletes and
 * recreates every FEIP index. It only runs against a throwaway cluster named
 * {@value #SCRATCH_CLUSTER}; on any other cluster it is skipped. Host and port come from
 * -Dfeip.it.host / -Dfeip.it.port (default 127.0.0.1:9291).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PersonalRollbackEsIT {

    static final String SCRATCH_CLUSTER = "feip-it-scratch";
    private static final String OWNER = "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK";
    private static final String OTHER = "FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7";

    private ElasticsearchClient es;
    private final PersonalParser parser = new PersonalParser();
    private int txIndex;

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

    @BeforeEach
    void freshIndices() throws Exception {
        IndicesFEIP.deleteAllIndices(es);
        IndicesFEIP.createAllIndices(es);
        txIndex = 0;
    }

    // ---- op helpers: the same steps FileParser takes for one op ----

    private OpReturn opre(String txid, long height, String signer) {
        OpReturn opre = new OpReturn();
        opre.setId(txid);
        opre.setHeight(height);
        opre.setTxIndex(txIndex++);
        opre.setTime(1_700_000_000L + height);
        opre.setSigner(signer);
        opre.setRecipient(OTHER);
        opre.setPaid(1000L);
        return opre;
    }

    private static Feip feip(Object... kv) {
        Map<String, Object> data = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) data.put((String) kv[i], kv[i + 1]);
        Feip feip = new Feip();
        feip.setType("FEIP");
        feip.setData(data);
        return feip;
    }

    private <H extends FcEntity> boolean apply(String histIndex, H hist, Step<H> step) throws Exception {
        assertNotNull(hist, "op was malformed");
        boolean valid = step.apply(hist);
        if (valid) es.index(i -> i.index(histIndex).id(hist.getId()).document(hist));
        return valid;
    }

    interface Step<H> { boolean apply(H h) throws Exception; }

    private boolean contact(String txid, long height, String signer, Object... kv) throws Exception {
        return apply(IndicesNames.CONTACT_HISTORY, parser.makeContact(opre(txid, height, signer), feip(kv)), h -> parser.parseContact(es, h));
    }

    private boolean secret(String txid, long height, String signer, Object... kv) throws Exception {
        return apply(IndicesNames.SECRET_HISTORY, parser.makeSecret(opre(txid, height, signer), feip(kv)), h -> parser.parseSecret(es, h));
    }

    private boolean mail(String txid, long height, String signer, Object... kv) throws Exception {
        return apply(IndicesNames.MAIL_HISTORY, parser.makeMail(opre(txid, height, signer), feip(kv)), h -> parser.parseMail(es, h));
    }

    private void rollbackTo(long height) throws Exception {
        es.indices().refresh();
        assertFalse(new PersonalRollbacker().rollback(es, height), "rollback reported an error");
        es.indices().refresh();
    }

    private Contact getContact(String id) throws Exception {
        return EsUtils.getById(es, IndicesNames.CONTACT, id, Contact.class);
    }

    private long count(String index) throws Exception {
        es.indices().refresh();
        return es.count(c -> c.index(index)).count();
    }

    // ---- contact ----

    @Test
    void rolledBackUpdateRestoresThePreviousCipher() throws Exception {
        assertTrue(contact("c1", 100, OWNER, "op", "add", "cipher", "A"));
        assertTrue(contact("u1", 200, OWNER, "op", "update", "contactId", "c1", "cipher", "B", "alg", "X"));
        assertEquals("B", getContact("c1").getCipher());

        rollbackTo(150);

        Contact c = getContact("c1");
        assertEquals("A", c.getCipher());
        assertNull(c.getAlg());
        assertEquals(100L, c.getLastHeight());
        assertTrue(c.getActive());
        assertEquals(1, count(IndicesNames.CONTACT_HISTORY));
    }

    @Test
    void rollbackBetweenTwoUpdatesKeepsTheEarlierOne() throws Exception {
        contact("c1", 100, OWNER, "op", "add", "cipher", "A");
        contact("u1", 200, OWNER, "op", "update", "contactId", "c1", "cipher", "B");
        contact("u2", 210, OWNER, "op", "update", "contactId", "c1", "cipher", "C");

        rollbackTo(205);

        assertEquals("B", getContact("c1").getCipher());
        assertEquals(200L, getContact("c1").getLastHeight());
    }

    @Test
    void rolledBackDeleteReactivates() throws Exception {
        contact("c1", 100, OWNER, "op", "add", "cipher", "A");
        contact("d1", 200, OWNER, "op", "delete", "contactIds", List.of("c1"));
        assertFalse(getContact("c1").getActive());

        rollbackTo(150);

        assertTrue(getContact("c1").getActive());
        assertEquals(100L, getContact("c1").getLastHeight());
    }

    @Test
    void rolledBackRecoverLeavesItDeleted() throws Exception {
        contact("c1", 100, OWNER, "op", "add", "cipher", "A");
        contact("d1", 120, OWNER, "op", "delete", "contactIds", List.of("c1"));
        contact("r1", 200, OWNER, "op", "recover", "contactIds", List.of("c1"));

        rollbackTo(150);

        assertFalse(getContact("c1").getActive());
        assertEquals(120L, getContact("c1").getLastHeight());
    }

    @Test
    void contactBornAboveTheRollbackIsRemovedWithItsHistory() throws Exception {
        contact("c1", 100, OWNER, "op", "add", "cipher", "A");
        contact("c2", 200, OWNER, "op", "add", "cipher", "B");
        contact("u2", 210, OWNER, "op", "update", "contactId", "c2", "cipher", "B2");

        rollbackTo(150);

        assertNull(getContact("c2"));
        assertEquals("A", getContact("c1").getCipher());
        assertEquals(1, count(IndicesNames.CONTACT));
        assertEquals(1, count(IndicesNames.CONTACT_HISTORY));
    }

    @Test
    void untouchedContactsSurviveARollback() throws Exception {
        contact("c1", 100, OWNER, "op", "add", "cipher", "A");
        contact("c2", 110, OWNER, "op", "add", "cipher", "B");
        contact("u1", 200, OWNER, "op", "update", "contactId", "c1", "cipher", "A2");

        rollbackTo(150);

        assertEquals("B", getContact("c2").getCipher());
        assertEquals(110L, getContact("c2").getLastHeight());
    }

    @Test
    void onlyTheOwnerCanDeleteOrUpdateAContact() throws Exception {
        contact("c1", 100, OWNER, "op", "add", "cipher", "A");
        assertFalse(contact("d1", 110, OTHER, "op", "delete", "contactIds", List.of("c1")));
        assertFalse(contact("u1", 120, OTHER, "op", "update", "contactId", "c1", "cipher", "X"));
        assertTrue(getContact("c1").getActive());
        assertEquals("A", getContact("c1").getCipher());
        assertEquals(1, count(IndicesNames.CONTACT_HISTORY), "rejected ops must leave no history");
    }

    @Test
    void deletedContactCannotBeUpdatedUntilRecovered() throws Exception {
        contact("c1", 100, OWNER, "op", "add", "cipher", "A");
        contact("d1", 110, OWNER, "op", "delete", "contactIds", List.of("c1"));
        assertFalse(contact("u1", 120, OWNER, "op", "update", "contactId", "c1", "cipher", "B"));
        contact("r1", 130, OWNER, "op", "recover", "contactIds", List.of("c1"));
        assertTrue(contact("u2", 140, OWNER, "op", "update", "contactId", "c1", "cipher", "B"));
        assertEquals("B", getContact("c1").getCipher());
    }

    @Test
    void replayRespectsOrderWithinOneHeight() throws Exception {
        // add, delete and update of the same contact all at height 100: the update must be
        // rejected on replay exactly as it was the first time.
        contact("c1", 100, OWNER, "op", "add", "cipher", "A");
        contact("d1", 100, OWNER, "op", "delete", "contactIds", List.of("c1"));
        contact("r1", 100, OWNER, "op", "recover", "contactIds", List.of("c1"));
        contact("u1", 100, OWNER, "op", "update", "contactId", "c1", "cipher", "B");
        contact("u2", 200, OWNER, "op", "update", "contactId", "c1", "cipher", "C");

        rollbackTo(150);

        assertEquals("B", getContact("c1").getCipher());
        assertTrue(getContact("c1").getActive());
    }

    // ---- secret ----

    @Test
    void secretUpdateAndDeleteRollBack() throws Exception {
        secret("s1", 100, OWNER, "op", "add", "cipher", "A");
        secret("u1", 200, OWNER, "op", "update", "secretId", "s1", "cipher", "B");
        secret("d1", 210, OWNER, "op", "delete", "secretIds", List.of("s1"));

        rollbackTo(205);
        Secret s = EsUtils.getById(es, IndicesNames.SECRET, "s1", Secret.class);
        assertEquals("B", s.getCipher());
        assertTrue(s.getActive());

        rollbackTo(150);
        s = EsUtils.getById(es, IndicesNames.SECRET, "s1", Secret.class);
        assertEquals("A", s.getCipher());
        assertEquals(100L, s.getLastHeight());
        assertEquals(1, count(IndicesNames.SECRET_HISTORY));
    }

    @Test
    void secretOfAnotherOwnerIsSkippedInADelete() throws Exception {
        secret("s1", 100, OWNER, "op", "add", "cipher", "A");
        secret("s2", 100, OTHER, "op", "add", "cipher", "B");
        assertTrue(secret("d1", 110, OWNER, "op", "delete", "secretIds", List.of("s1", "s2")));
        assertFalse(EsUtils.getById(es, IndicesNames.SECRET, "s1", Secret.class).getActive());
        assertTrue(EsUtils.getById(es, IndicesNames.SECRET, "s2", Secret.class).getActive());

        rollbackTo(105);
        assertTrue(EsUtils.getById(es, IndicesNames.SECRET, "s1", Secret.class).getActive());
        assertTrue(EsUtils.getById(es, IndicesNames.SECRET, "s2", Secret.class).getActive());
        assertEquals("B", EsUtils.getById(es, IndicesNames.SECRET, "s2", Secret.class).getCipher());
    }

    // ---- mail ----

    @Test
    void mailDeleteByRecipientRollsBack() throws Exception {
        mail("m1", 100, OWNER, "op", "send", "cipher", "A");
        assertFalse(mail("d0", 150, OWNER, "op", "delete", "mailIds", List.of("m1")), "the sender cannot delete a mail with a recipient");
        assertTrue(mail("d1", 200, OTHER, "op", "delete", "mailIds", List.of("m1")));
        assertFalse(EsUtils.getById(es, IndicesNames.MAIL, "m1", Mail.class).getActive());

        rollbackTo(150);

        Mail m = EsUtils.getById(es, IndicesNames.MAIL, "m1", Mail.class);
        assertTrue(m.getActive());
        assertEquals(OWNER, m.getFrom());
        assertEquals(OTHER, m.getTo());
        assertEquals(1000L, m.getNoticeFee());
        assertEquals(100L, m.getLastHeight());
        assertEquals("A", m.getCipher());
    }

    @Test
    void mailSentAboveTheRollbackIsRemoved() throws Exception {
        mail("m1", 200, OWNER, "op", "send", "cipher", "A");
        rollbackTo(150);
        assertEquals(0, count(IndicesNames.MAIL));
        assertEquals(0, count(IndicesNames.MAIL_HISTORY));
    }

    @Test
    void rollbackWithNothingAboveIsANoOp() throws Exception {
        contact("c1", 100, OWNER, "op", "add", "cipher", "A");
        secret("s1", 100, OWNER, "op", "add", "cipher", "A");
        mail("m1", 100, OWNER, "op", "send", "cipher", "A");
        rollbackTo(150);
        assertEquals(1, count(IndicesNames.CONTACT));
        assertEquals(1, count(IndicesNames.SECRET));
        assertEquals(1, count(IndicesNames.MAIL));
    }

    @Test
    void resumeGuardSeesTheHistoryIndices() throws Exception {
        assertFalse(IndicesFEIP.lacksPersonalHistory(es));
        EsUtils.deleteIndex(es, IndicesNames.MAIL_HISTORY);
        assertTrue(IndicesFEIP.lacksPersonalHistory(es));
    }
}
