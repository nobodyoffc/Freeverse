package startFEIP;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import data.feipData.CodeHistory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The parser must not stop for an op whose field content its index refuses: anyone can carve one,
 * and every parser would stop at the same block. Height 3470658 did exactly that: a Code carve
 * with home {src, zip} against a text mapping stopped the parser, because the partial-op
 * rollback re-indexed the same history and threw again.
 */
class RejectedFieldsTest {

    static ElasticsearchException refusal(int status, String type, String reason) {
        return new ElasticsearchException("es/index",
                ErrorResponse.of(r -> r.status(status).error(e -> e.type(type).reason(reason))));
    }

    static final String HOME_REFUSED = "[1:448] failed to parse field [home] of type [text] in document with id 'e7fe'. "
            + "Preview of field's value: '{zip=https://x/a.zip, src=https://x}'";

    @Test
    void namesTheFieldOfADocumentRefusal() {
        assertEquals("home", RejectedFields.rejectedField(400, HOME_REFUSED));
        assertEquals("home", RejectedFields.rejectedField(400, "failed to parse field [home.src] of type [long]"));
        assertNull(RejectedFields.rejectedField(503, HOME_REFUSED), "a server error is not the op's fault");
        assertNull(RejectedFields.rejectedField(400, "index_not_found_exception"));
        assertEquals("home", RejectedFields.rejectedField(refusal(400, "document_parsing_exception", HOME_REFUSED)));
    }

    @Test
    void writesTheDocumentWithoutTheRefusedField() throws IOException {
        CodeHistory hist = new CodeHistory();
        hist.setId("e7fe");
        hist.setName("FreerForMac");
        hist.setHome(Map.of("src", "https://x", "zip", "https://x/a.zip"));
        Map<String, Object> doc = RejectedFields.asMap(hist);
        assertTrue(doc.containsKey("home"));

        List<Map<String, Object>> written = new ArrayList<>();
        List<String> dropped = RejectedFields.writeDropping(doc, m -> {
            if (m.containsKey("home")) throw refusal(400, "document_parsing_exception", HOME_REFUSED);
            written.add(Map.copyOf(m));
        }, "code_history/e7fe");

        assertEquals(List.of("home"), dropped);
        assertEquals(1, written.size());
        assertEquals("FreerForMac", written.get(0).get("name"));
        assertFalse(written.get(0).containsKey("home"));
    }

    @Test
    void anyOtherFailureStillStopsTheParser() {
        Map<String, Object> doc = RejectedFields.asMap(new CodeHistory());
        assertThrows(ElasticsearchException.class, () -> RejectedFields.writeDropping(doc,
                m -> { throw refusal(503, "unavailable", "no shards"); }, "x"));
        assertThrows(IOException.class, () -> RejectedFields.writeDropping(doc,
                m -> { throw new IOException("connection refused"); }, "x"));
        // A refused field the document does not have cannot be dropped: rethrow, don't loop.
        assertThrows(ElasticsearchException.class, () -> RejectedFields.writeDropping(doc,
                m -> { throw refusal(400, "document_parsing_exception", "failed to parse field [nope]"); }, "x"));
    }
}
