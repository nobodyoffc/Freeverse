package Mycoins;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The decrypted-params parsing shared by the mycoins endpoints. */
public class CommonApiBaseParamsTest {

    @Test
    void stringsAndNumbersParseAsBefore() {
        Map<String, String> p = CommonApiBase.parseParams(
                "{\"coin\":\"FCH\",\"address\":\"FC5yQV66T6pjDAHVmBKGkjjnvkjtirgSmY\",\"amount\":50000,\"fee\":0.001,\"size\":\"100\"}");
        assertEquals("FCH", p.get("coin"));
        assertEquals("50000", p.get("amount"));
        assertEquals("0.001", p.get("fee"));
        assertEquals("100", p.get("size"));
    }

    @Test
    void anArrayCursorNoLongerBreaksTheWholeRequest() {
        Map<String, String> p = CommonApiBase.parseParams("{\"address\":\"F1\",\"after\":[\"123\",\"abc\"],\"x\":null}");
        assertEquals(List.of("123", "abc"), CommonApiBase.parseCursor(p.get("after")));
        assertFalse(p.containsKey("x"));
    }

    @Test
    void cursorAcceptsCommaTextAndEmpty() {
        assertEquals(List.of("123", "abc"), CommonApiBase.parseCursor("123,abc"));
        assertNull(CommonApiBase.parseCursor(""));
        assertNull(CommonApiBase.parseCursor(null));
        assertNull(CommonApiBase.parseCursor("[]"));
    }

    @Test
    void intParamAcceptsJsonNumbers() {
        assertEquals(2, GetUtxos.intParam(Map.of(), "outputSize", 2));
        assertEquals(3, GetUtxos.intParam(Map.of("outputSize", "3"), "outputSize", 2));
        assertEquals(3, GetUtxos.intParam(Map.of("outputSize", "3.0"), "outputSize", 2));
    }
}
