package data.fcData;

import data.feipData.Video;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hat.fromWork. The VECTOR_* cases are shared, verbatim, with FC-AJDK HatFromWorkTest and
 * FreerForMac HatFromWorkTests: a work HAT built on any device must serialize identically.
 */
public class HatFromWorkTest {

    static final String DID = "5DF6E0E2761359D30A8275058E299FCC0381534545F55CF43E41983F5D4C9456";
    static final String ID = DID.toLowerCase();
    static final long NOW = 1762300800000L;

    static final String VECTOR_VIDEO_JSON = "{\"hAlg\":\"sha256x2\",\"born\":1762300800000,\"last\":1762300800000,"
            + "\"name\":\"Lesson 1_ Keys.mp4\",\"desc\":\"Intro to keys\",\"types\":[\"video/mp4\"],"
            + "\"pids\":[\"89c23e636e93e6feb6776720688f02a138a036db338ed72df5510d5129b9ac00\"],"
            + "\"state\":\"ACTIVE\",\"locas\":[\"(sid)abc\",\"https://example.com/v.mp4\"],"
            + "\"id\":\"5df6e0e2761359d30a8275058e299fcc0381534545f55cf43e41983f5d4c9456\"}";

    @Test
    public void vectorVideo() {
        Hat hat = Hat.fromWork(Hat.WorkKind.VIDEO, DID, " Lesson 1: Keys ", "video/mp4", " Intro to keys ",
                Arrays.asList("(sid)abc", " ", "https://example.com/v.mp4", "(sid)abc"), NOW);
        assertEquals(VECTOR_VIDEO_JSON, hat.toJson());
    }

    @Test
    public void vectorMinimalText() {
        Hat hat = Hat.fromWork(Hat.WorkKind.TEXT, DID, null, null, null, null, NOW);
        assertEquals("5df6e0e2.txt", hat.getName());
        assertEquals(List.of("text/plain"), hat.getTypes());
        assertNull(hat.getDesc());
        assertNull(hat.getLocas());
        assertNull(hat.getSize());
    }

    @Test
    public void formatResolution() {
        // bare extension -> its MIME
        assertEquals("audio/mpeg", Hat.workMime(Hat.WorkKind.SOUND, "MP3"));
        assertEquals("image/jpeg", Hat.workMime(Hat.WorkKind.IMAGE, ".jpeg"));
        // MIME with parameters
        assertEquals("text/markdown", Hat.workMime(Hat.WorkKind.TEXT, "text/markdown; charset=utf-8"));
        // not a MIME, not a known extension -> kind default
        assertEquals("video/*", Hat.workMime(Hat.WorkKind.VIDEO, "mp4/h.264"));
        assertEquals("video/*", Hat.workMime(Hat.WorkKind.VIDEO, "H.265"));

        // known MIME -> its extension; title already ending with it (or an alias) is kept
        assertEquals("song.mp3", Hat.workFileName(ID, "song", "audio/mpeg", null));
        assertEquals("Song.MP3", Hat.workFileName(ID, "Song.MP3", "audio/mpeg", null));
        assertEquals("pic.jpeg", Hat.workFileName(ID, "pic.jpeg", "image/jpeg", null));
        // unknown MIME, short bare format -> format as extension
        assertEquals("clip.avi", Hat.workFileName(ID, "clip", "video/*", "avi"));
        // nothing usable -> no extension
        assertEquals("clip", Hat.workFileName(ID, "clip", "video/*", "H.265"));
        // path separators never reach a file name
        assertEquals("a_b_c.txt", Hat.workFileName(ID, "a/b\\c", "text/plain", null));
    }

    @Test
    public void fromVideoEntity() {
        Video v = new Video();
        v.setDid(DID);
        v.setTitle("Talk");
        v.setFormat("webm");
        Hat hat = Hat.fromVideo(v);
        assertEquals(ID, hat.getId());
        assertEquals("Talk.webm", hat.getName());
        assertEquals(List.of("video/webm"), hat.getTypes());
        assertEquals(List.of(Hat.WorkKind.VIDEO.pid), hat.getPids());
    }

    @Test
    public void rejectsBadDid() {
        for (String bad : new String[]{null, "", "abc", ID + "0", ID.replace('5', 'g')}) {
            try {
                Hat.fromWork(Hat.WorkKind.IMAGE, bad, "x", null, null, null, NOW);
                fail("accepted " + bad);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
    }

    @Test
    public void mergeKeepsUserFields() {
        Hat mine = new Hat();
        mine.setId(ID);
        mine.setName("my copy.mp4");
        mine.setTypes(new ArrayList<>(List.of("video/mp4")));
        mine.setSize(42L);
        mine.setBorn(1L);
        mine.setLast(2L);
        mine.setKey("k");
        mine.setLocas(new ArrayList<>(List.of("local:///tmp/x")));

        Hat work = Hat.fromWork(Hat.WorkKind.VIDEO, DID, "Talk", "video/webm", "about", List.of("(sid)abc"), NOW);
        Hat merged = Hat.mergeWork(mine, work);

        assertSame(mine, merged);
        assertEquals("my copy.mp4", merged.getName());
        assertEquals(List.of("video/mp4"), merged.getTypes());
        assertEquals("about", merged.getDesc());
        assertEquals(Long.valueOf(42), merged.getSize());
        assertEquals(Long.valueOf(1), merged.getBorn());
        assertEquals(Long.valueOf(NOW), merged.getLast());
        assertEquals("k", merged.getKey());
        assertEquals(List.of("local:///tmp/x", "(sid)abc"), merged.getLocas());
        assertEquals(List.of(Hat.WorkKind.VIDEO.pid), merged.getPids());
    }

    @Test
    public void mergeReplacesPlaceholderName() {
        Hat placeholder = new Hat();
        placeholder.setId(ID);
        placeholder.setName(ID.substring(0, 8));
        Hat work = Hat.fromWork(Hat.WorkKind.TEXT, DID, "Notes", "text/markdown", null, null, NOW);
        assertEquals("Notes.md", Hat.mergeWork(placeholder, work).getName());
    }

    @Test
    public void mergeRejectsOtherDid() {
        Hat other = new Hat();
        other.setId("00".repeat(32));
        Hat work = Hat.fromWork(Hat.WorkKind.TEXT, DID, null, null, null, null, NOW);
        assertThrows(IllegalArgumentException.class, () -> Hat.mergeWork(other, work));
    }

    @Test
    public void preDidAndCryptoFieldsSerialize() {
        Hat hat = new Hat();
        hat.setPreDid("p");
        hat.setKey("k");
        hat.setCipherIds(List.of("c"));
        String json = hat.toJson();
        assertTrue(json.contains("\"preDid\":\"p\""), json);
        assertTrue(json.contains("\"key\":\"k\""), json);
        assertTrue(json.contains("\"cipherIds\":[\"c\"]"), json);
    }

    // ---- toWorkDraft (same cases as FreerForMac HatFromWorkTests) ----

    private static Hat hat(String name, List<String> types) {
        Hat h = new Hat();
        h.setId(ID);
        h.setName(name);
        h.setTypes(types);
        return h;
    }

    @Test
    public void draftFromAFileHat() {
        Hat h = hat("Lesson 1.mp4", List.of("video/mp4"));
        h.setId(DID);
        h.setDesc(" Intro ");
        h.setLocas(Arrays.asList("local:///x", "(sid)abc", "fudp://unknown", "https://e.com/v",
                "fudp://1.2.3.4:8500", "(sid)def"));
        assertEquals(new Hat.WorkDraft(Hat.WorkKind.VIDEO, ID, "Lesson 1", "video/mp4", "Intro",
                List.of("(sid)abc", "https://e.com/v", "fudp://1.2.3.4:8500")), h.toWorkDraft());
    }

    @Test
    public void draftFormatAndKindFallbacks() {
        Hat.WorkDraft d = hat("notes.md", List.of("application/octet-stream")).toWorkDraft();
        assertEquals("notes", d.title);
        assertEquals("text/markdown", d.format);
        assertEquals(Hat.WorkKind.TEXT, d.kind);
        d = hat("pic", List.of("image/*")).toWorkDraft();
        assertNull(d.format);
        assertEquals(Hat.WorkKind.IMAGE, d.kind);
        d = hat("book.pdf", null).toWorkDraft();
        assertEquals("application/pdf", d.format);
        assertEquals(Hat.WorkKind.TEXT, d.kind);
        d = hat("data.bin", List.of("application/zip")).toWorkDraft();
        assertEquals("application/zip", d.format);
        assertNull(d.kind);
        d = hat(".bashrc", null).toWorkDraft();
        assertEquals(".bashrc", d.title);
        assertNull(d.format);
        d = hat("archive.tar.gz", null).toWorkDraft();
        assertEquals("archive.tar", d.title);
        assertNull(d.format);
        d = hat(null, null).toWorkDraft();
        assertNull(d.title);
        assertNull(d.locas);
    }

    @Test
    public void draftDropsLocasOfAHatHoldingAPlainKey() {
        Hat h = hat("x.txt", null);
        h.setKey("00ff");
        h.setLocas(List.of("(sid)abc"));
        assertNull(h.toWorkDraft().locas);
    }

    @Test
    public void draftRefusesCipherAndBadDid() {
        Hat cipher = hat(null, null);
        cipher.setRawDid(ID);
        assertThrows(IllegalStateException.class, cipher::toWorkDraft);
        Hat bad = hat("x", null);
        bad.setId("abc");
        assertThrows(IllegalArgumentException.class, bad::toWorkDraft);
        Hat none = hat("x", null);
        none.setId(null);
        assertThrows(IllegalArgumentException.class, none::toWorkDraft);
    }

    @Test
    public void draftRoundTripsAWorkHat() {
        Hat h = Hat.fromWork(Hat.WorkKind.VIDEO, DID, "Talk", "video/webm", "about", List.of("(sid)abc"), NOW);
        assertEquals(new Hat.WorkDraft(Hat.WorkKind.VIDEO, ID, "Talk", "video/webm", "about", List.of("(sid)abc")),
                h.toWorkDraft());
    }
}

