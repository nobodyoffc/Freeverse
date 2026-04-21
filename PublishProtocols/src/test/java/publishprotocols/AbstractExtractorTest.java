package publishprotocols;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractExtractorTest {

    @Test
    void extractsPlainText() {
        String md = """
                ## Abstract

                First paragraph here.

                ## Next Section
                ignored
                """;
        assertEquals("First paragraph here.", AbstractExtractor.extract(md));
    }

    @Test
    void stripsBoldAndBackticks() {
        String md = """
                ## Abstract

                Use **bold** and `code` markers.

                ## X
                """;
        assertEquals("Use bold and code markers.", AbstractExtractor.extract(md));
    }

    @Test
    void skipsCodeFence() {
        String md = """
                ## Abstract

                Before
                ```
                skip this
                ```
                After

                ## End
                """;
        String s = AbstractExtractor.extract(md);
        assertTrue(s.contains("Before"));
        assertTrue(s.contains("After"));
        assertTrue(!s.contains("skip this"));
    }
}
