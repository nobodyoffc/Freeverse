package publishprotocols;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SummaryTableParserTest {

    @Test
    void parsesTableUnderSummary() {
        String md = """
                ## Summary

                |Field|Content|
                |---|---|
                |Title|FUDP|
                |Type|FUDP|
                |SN|0|
                |Ver|1|

                ## Abstract
                x
                """;
        Map<String, String> m = SummaryTableParser.parse(md);
        assertEquals("FUDP", m.get("Title"));
        assertEquals("FUDP", m.get("Type"));
        assertEquals("0", m.get("SN"));
        assertEquals("1", m.get("Ver"));
    }

    @Test
    void acceptsVersionInsteadOfVer() {
        String md = """
                ## Summary

                |Field|Content|
                |---|---|
                |Title|X|
                |Type|T|
                |SN|5|
                |Version|2|

                ## Abstract
                a
                """;
        Map<String, String> m = SummaryTableParser.parse(md);
        assertEquals("2", m.get("Version"));
        assertEquals("5", m.get("SN"));
    }

    @Test
    void parsesFirstTableBeforeContents() {
        String md = """
                |Field|Content|
                |---|---|
                |Title|Core Protocol|
                |Type|FAPI|
                |SN|1|
                |Ver|1|

                ## Contents

                - [Abstract](#abstract)
                """;
        Map<String, String> m = SummaryTableParser.parse(md);
        assertEquals("Core Protocol", m.get("Title"));
        assertEquals("FAPI", m.get("Type"));
        assertEquals("1", m.get("SN"));
    }
}
