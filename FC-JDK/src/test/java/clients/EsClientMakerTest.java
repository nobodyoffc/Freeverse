package clients;

import data.feipData.Image;
import jakarta.json.stream.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/** A document with a field the class lacks is still read: what an older server meets after a parser upgrade. */
public class EsClientMakerTest {
    @Test
    void anUnknownFieldIsSkipped() {
        var mapper = EsClientMaker.tolerantMapper();
        String json = "{\"id\":\"x\",\"title\":\"t\",\"fieldFromANewerParser\":\"ab\"}";
        try (JsonParser p = mapper.jsonProvider().createParser(new StringReader(json))) {
            Image image = mapper.deserialize(p, Image.class);
            assertEquals("x", image.getId());
            assertEquals("t", image.getTitle());
        }
    }
}
