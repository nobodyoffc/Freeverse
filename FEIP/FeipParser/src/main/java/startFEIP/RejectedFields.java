package startFEIP;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes a history document that Elasticsearch refuses because of one field's content.
 *
 * Anyone can carve an op, so an op whose field does not fit its index (a map where the mapping
 * says text, say) must not stop the parser: every parser would stop at the same block. Such a
 * refusal is an HTTP 400 naming the field ("failed to parse field [home] ..."). The document is
 * then written without that field, and the loss is logged. Any other failure (Elasticsearch
 * down, a timeout, a 5xx) is not the op's fault and is rethrown, which stops the parser as before.
 */
final class RejectedFields {

    private static final Logger log = LoggerFactory.getLogger(RejectedFields.class);
    private static final Pattern FIELD = Pattern.compile("failed to parse field \\[([^\\]]+)\\]");
    /** No document has more fields than this to drop. */
    private static final int MAX_DROPS = 8;
    private static final ObjectMapper JSON = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private RejectedFields() {
    }

    interface Writer {
        void write(Map<String, Object> doc) throws IOException;
    }

    /** The top-level field a document-level refusal names, or null if this is not one. */
    static String rejectedField(int status, String reason) {
        if (status != 400 || reason == null) return null;
        Matcher m = FIELD.matcher(reason);
        if (!m.find()) return null;
        String field = m.group(1);
        int dot = field.indexOf('.');
        return dot < 0 ? field : field.substring(0, dot);
    }

    static String rejectedField(ElasticsearchException e) {
        String reason = e.error() != null ? e.error().reason() : null;
        return rejectedField(e.status(), reason != null ? reason : e.getMessage());
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object doc) {
        return JSON.convertValue(doc, LinkedHashMap.class);
    }

    /**
     * Writes `doc`, dropping each field Elasticsearch refuses until it is accepted.
     *
     * @return the fields dropped, empty if it went in whole
     */
    static List<String> writeDropping(Map<String, Object> doc, Writer writer, String what) throws IOException {
        List<String> dropped = new ArrayList<>();
        while (true) {
            try {
                writer.write(doc);
                if (!dropped.isEmpty()) {
                    log.warn("Stored {} without {}: the index refused their content.", what, dropped);
                }
                return dropped;
            } catch (ElasticsearchException e) {
                String field = rejectedField(e);
                if (field == null || !doc.containsKey(field) || dropped.size() >= MAX_DROPS) throw e;
                doc.remove(field);
                dropped.add(field);
            }
        }
    }

    /** {@link #writeDropping} for one document of `index`. */
    static List<String> index(ElasticsearchClient esClient, String index, String id, Object doc, boolean refresh) throws IOException {
        Map<String, Object> map = asMap(doc);
        return writeDropping(map, m -> {
            if (refresh) {
                esClient.index(i -> i.index(index).id(id).document(m).refresh(co.elastic.clients.elasticsearch._types.Refresh.True));
            } else {
                esClient.index(i -> i.index(index).id(id).document(m));
            }
        }, index + "/" + id);
    }
}
