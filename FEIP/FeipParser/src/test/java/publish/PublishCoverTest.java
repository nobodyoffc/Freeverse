package publish;

import data.feipData.*;
import data.fchData.OpReturn;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FEIP21, FEIP23 and FEIP25 carry an optional {@code cover}: the record id of the FEIP24 Image
 * shown with the work. It must reach the history record, which is all a rollback rebuilds from,
 * and anything that is not a record id is dropped rather than indexed.
 */
public class PublishCoverTest {

    private static final String SIGNER = "FQ8KKsMFYXkmNPDVauEMWFBnd7yfhiqqN8";
    private static final String IMAGE_ID = "ab".repeat(32);
    private final PublishParser parser = new PublishParser();

    private static OpReturn opre(String txid) {
        OpReturn opre = new OpReturn();
        opre.setId(txid);
        opre.setHeight(3_500_000L);
        opre.setTxIndex(1);
        opre.setTime(1_700_000_000L);
        opre.setSigner(SIGNER);
        opre.setCdd(1L);
        return opre;
    }

    private static Feip feip(String op, String idKey, String cover) {
        Map<String, Object> data = new HashMap<>();
        data.put("op", op);
        data.put("title", "U1-01");
        if (idKey != null) data.put(idKey, "cd".repeat(32));
        if (cover != null) data.put("cover", cover);
        Feip feip = new Feip();
        feip.setType("FEIP");
        feip.setData(data);
        return feip;
    }

    @Test
    void aCoverReachesTheHistoryOfEachWork() {
        assertEquals(IMAGE_ID, parser.makeVideo(opre("01".repeat(32)), feip("publish", null, IMAGE_ID)).getCover());
        assertEquals(IMAGE_ID, parser.makeVideo(opre("02".repeat(32)), feip("update", "videoId", IMAGE_ID)).getCover());
        assertEquals(IMAGE_ID, parser.makeSound(opre("03".repeat(32)), feip("publish", null, IMAGE_ID)).getCover());
        assertEquals(IMAGE_ID, parser.makeText(opre("04".repeat(32)), feip("update", "textId", IMAGE_ID.toUpperCase())).getCover(),
                "stored lowercase");
        assertNull(parser.makeVideo(opre("05".repeat(32)), feip("publish", null, null)).getCover());
    }

    @Test
    void aCoverThatIsNotARecordIdIsDropped() {
        VideoHistory hist = parser.makeVideo(opre("06".repeat(32)), feip("publish", null, "https://example.org/cover.png"));
        assertNotNull(hist, "the op itself stands");
        assertNull(hist.getCover());
    }

    @Test
    void theOpFieldsAndMappingsNameIt() throws Exception {
        assertTrue(List.of(VideoOpData.OP_FIELDS.get("update")).contains("cover"));
        assertTrue(List.of(TextOpData.OP_FIELDS.get("publish")).contains("cover"));
        for (String index : List.of("text", "sound", "video", "text_history", "sound_history", "video_history")) {
            try (InputStream in = getClass().getResourceAsStream("/mappings/" + index + ".json")) {
                assertNotNull(in, index);
                assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("\"cover\":{\"type\":\"keyword\"}"), index);
            }
        }
    }
}
