package publishprotocols;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import core.crypto.Hash;
import org.junit.jupiter.api.Test;
import utils.Hex;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishProtocolsIntegrationTest {

    @Test
    void runProducesNdjsonWithSnStringAndDid() throws Exception {
        Path dir = Files.createTempDirectory("pubproto");
        Path md = dir.resolve("P0V1_Test.md");
        String body = """
                ## Summary

                |Field|Content|
                |---|---|
                |Title|TestProto|
                |Type|TEST|
                |SN|2|
                |Ver|3|

                ## Abstract

                Hello.

                ## More
                """;
        Files.writeString(md, body, StandardCharsets.UTF_8);
        Path out = dir.resolve("out.ndjson");

        PublishProtocols.run(new String[]{"--folder", dir.toString(), "--output", out.toString(), "--lang", "en"});

        String line = Files.readString(out, StandardCharsets.UTF_8).trim();
        Gson g = new Gson();
        JsonObject root = g.fromJson(line, JsonObject.class);
        assertEquals("FEIP", root.get("type").getAsString());
        JsonObject data = root.getAsJsonObject("data");
        assertEquals("publish", data.get("op").getAsString());
        assertEquals("2", data.get("sn").getAsString());
        assertEquals("TestProto", data.get("name").getAsString());
        assertEquals("TEST", data.get("type").getAsString());
        assertEquals("3", data.get("ver").getAsString());
        assertEquals("en", data.get("lang").getAsString());
        assertTrue(data.has("desc"));
        String did = data.get("did").getAsString();
        String expected = Hex.toHex(Hash.sha256x2(body.getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, did);
        assertTrue(!data.has("home"));
        assertTrue(!data.has("preDid"));
        assertTrue(!data.has("waiters"));
    }

    @Test
    void runWithPrettyWritesIndentedJson() throws Exception {
        Path dir = Files.createTempDirectory("pubprotoPretty");
        Path md = dir.resolve("P.md");
        Files.writeString(md, """
                ## Summary
                |Field|Content|
                |---|---|
                |Title|Z|
                |Type|TZ|
                |SN|0|
                |Ver|1|
                ## Abstract
                Short.
                ## X
                """, StandardCharsets.UTF_8);
        Path out = dir.resolve("pretty.json");
        PublishProtocols.run(new String[]{"--folder", dir.toString(), "--output", out.toString(), "--pretty"});
        String written = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue(written.contains("\n  \"type\": \"FEIP\""), written);
        assertTrue(written.contains("\n  \"data\": {"), written);
    }

    @Test
    void runAcceptsSummaryTableVersionInsteadOfVer() throws Exception {
        Path dir = Files.createTempDirectory("pubproto");
        Path md = dir.resolve("Doc.md");
        String body = """
                ## Summary

                |Field|Content|
                |---|---|
                |Title|A|
                |Type|B|
                |SN|1|
                |Version|9|

                ## Abstract
                x
                """;
        Files.writeString(md, body, StandardCharsets.UTF_8);
        Path out = dir.resolve("out.ndjson");
        PublishProtocols.run(new String[]{"--folder", dir.toString(), "--output", out.toString()});
        JsonObject data = new Gson().fromJson(Files.readString(out, StandardCharsets.UTF_8).trim(), JsonObject.class)
                .getAsJsonObject("data");
        assertEquals("9", data.get("ver").getAsString());
    }

    @Test
    void stderrShowsAlertWhenSingleJsonUtf8Exceeds4092() throws Exception {
        PrintStream oldErr = System.err;
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capture, true, StandardCharsets.UTF_8));
        try {
            Path dir = Files.createTempDirectory("pubprotoBig");
            Path md = dir.resolve("Big.md");
            String longDesc = "x".repeat(4000);
            String body = """
                    ## Summary

                    |Field|Content|
                    |---|---|
                    |Title|T|
                    |Type|TY|
                    |SN|0|
                    |Ver|1|

                    ## Abstract

                    """ + longDesc + """

                    ## End
                    """;
            Files.writeString(md, body, StandardCharsets.UTF_8);
            Path out = dir.resolve("o.ndjson");
            PublishProtocols.run(new String[]{"--folder", dir.toString(), "--output", out.toString()});
            String err = capture.toString(StandardCharsets.UTF_8);
            assertTrue(err.contains("ALERT"), err);
            assertTrue(err.contains("4092"), err);
            assertTrue(err.contains("exceeds"), err);
        } finally {
            System.setErr(oldErr);
        }
    }
}
