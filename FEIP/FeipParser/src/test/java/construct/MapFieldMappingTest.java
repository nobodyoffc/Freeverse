package construct;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every `home` is a Map<String,String> in the data classes, so its index field must be an object.
 * A text mapping makes Elasticsearch refuse the document, and the parser stops at that block:
 * a Release Sync code carve with home {src, zip} stopped FEIP parsing at height 3470658.
 */
class MapFieldMappingTest {

    private static final List<String> INDICES_WITH_HOME = List.of(
            "protocol", "protocol_history", "code", "code_history", "app", "app_history",
            "service", "service_history", "team", "team_history", "square", "square_history", "freer_history");

    @Test
    void homeIsAnObjectEverywhere() throws Exception {
        ObjectMapper json = new ObjectMapper();
        for (String index : INDICES_WITH_HOME) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("mappings/" + index + ".json")) {
                assertNotNull(in, index);
                JsonNode home = json.readTree(in).path("mappings").path("properties").path("home");
                assertEquals("object", home.path("type").asText(), index + ".home");
            }
        }
    }
}
