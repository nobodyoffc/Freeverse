package personal;

import data.feipData.*;
import data.fchData.OpReturn;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The make* half of the Contact, Mail and Secret parsers: which ops become history records, and
 * what each record carries. A record is all a rollback has to rebuild an item from, so a field
 * dropped here is a field lost on every reorg.
 */
public class PersonalHistoryMakeTest {

    private static final String SIGNER = "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK";
    private final PersonalParser parser = new PersonalParser();

    private static OpReturn opre(String txid, long height) {
        OpReturn opre = new OpReturn();
        opre.setId(txid);
        opre.setHeight(height);
        opre.setTxIndex(3);
        opre.setTime(1_700_000_000L);
        opre.setSigner(SIGNER);
        opre.setRecipient("FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7");
        opre.setPaid(5000L);
        return opre;
    }

    private static Feip feip(Map<String, Object> data) {
        Feip feip = new Feip();
        feip.setType("FEIP");
        feip.setData(data);
        return feip;
    }

    private static Map<String, Object> data(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], kv[i + 1]);
        return map;
    }

    @Test
    void contactAddRecordsItselfAsTheTouchedContact() {
        ContactHistory h = parser.makeContact(opre("tx1", 100), feip(data("op", "add", "cipher", "C", "alg", "A")));
        assertNotNull(h);
        assertEquals("tx1", h.getId());
        assertEquals("tx1", h.getContactId());
        assertEquals("C", h.getCipher());
        assertEquals("A", h.getAlg());
        assertEquals(100L, h.getHeight());
        assertEquals(3, h.getIndex());
        assertEquals(SIGNER, h.getSigner());
    }

    @Test
    void contactUpdateNeedsAnIdAndACipher() {
        assertNull(parser.makeContact(opre("tx2", 200), feip(data("op", "update", "cipher", "C"))));
        assertNull(parser.makeContact(opre("tx2", 200), feip(data("op", "update", "contactId", "tx1"))));
        assertNull(parser.makeContact(opre("tx2", 200), feip(data("op", "update", "contactId", "tx1", "cipher", ""))));

        ContactHistory h = parser.makeContact(opre("tx2", 200), feip(data("op", "update", "contactId", "tx1", "cipher", "C2")));
        assertNotNull(h);
        assertEquals("tx2", h.getId());
        assertEquals("tx1", h.getContactId());
        assertEquals("C2", h.getCipher());
    }

    @Test
    void contactAddWithoutCipherIsRejected() {
        assertNull(parser.makeContact(opre("tx1", 100), feip(data("op", "add"))));
        assertNull(parser.makeContact(opre("tx1", 100), feip(data("op", "add", "cipher", ""))));
    }

    @Test
    void contactDeleteAndRecoverCarryTheirIds() {
        assertNull(parser.makeContact(opre("tx3", 300), feip(data("op", "delete"))));
        assertNull(parser.makeContact(opre("tx3", 300), feip(data("op", "delete", "contactIds", List.of()))));

        ContactHistory h = parser.makeContact(opre("tx3", 300), feip(data("op", "recover", "contactIds", List.of("a", "b"))));
        assertNotNull(h);
        assertEquals(List.of("a", "b"), h.getContactIds());
        assertNull(h.getContactId());
    }

    @Test
    void unknownOrMissingOpIsRejected() {
        assertNull(parser.makeContact(opre("tx", 1), feip(data("op", "rate"))));
        assertNull(parser.makeContact(opre("tx", 1), feip(data("cipher", "C"))));
        assertNull(parser.makeSecret(opre("tx", 1), feip(data("op", "send"))));
        assertNull(parser.makeMail(opre("tx", 1), feip(data("op", "add"))));
    }

    @Test
    void secretAddFallsBackToLegacyMsg() {
        SecretHistory h = parser.makeSecret(opre("s1", 100), feip(data("op", "add", "msg", "M")));
        assertNotNull(h);
        assertEquals("s1", h.getSecretId());
        assertEquals("M", h.getCipher());

        h = parser.makeSecret(opre("s1", 100), feip(data("op", "add", "cipher", "C", "msg", "M")));
        assertEquals("C", h.getCipher());

        assertNull(parser.makeSecret(opre("s1", 100), feip(data("op", "add"))));
    }

    @Test
    void secretUpdateNeedsAnIdAndACipher() {
        assertNull(parser.makeSecret(opre("s2", 200), feip(data("op", "update", "cipher", "C"))));
        assertNull(parser.makeSecret(opre("s2", 200), feip(data("op", "update", "secretId", "s1", "cipher", ""))));
        SecretHistory h = parser.makeSecret(opre("s2", 200), feip(data("op", "update", "secretId", "s1", "cipher", "C2")));
        assertEquals("s1", h.getSecretId());
        assertEquals("C2", h.getCipher());
    }

    @Test
    void mailSendKeepsRecipientAndPayment() {
        MailHistory h = parser.makeMail(opre("m1", 100), feip(data("op", "send", "cipher", "C")));
        assertNotNull(h);
        assertEquals("m1", h.getMailId());
        assertEquals("FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7", h.getRecipient());
        assertEquals(5000L, h.getPaid());
        assertEquals("C", h.getCipher());
    }

    @Test
    void mailDeleteCarriesItsIds() {
        assertNull(parser.makeMail(opre("m2", 200), feip(data("op", "delete"))));
        MailHistory h = parser.makeMail(opre("m2", 200), feip(data("op", "delete", "mailIds", List.of("m1"))));
        assertEquals(List.of("m1"), h.getMailIds());
    }

    @Test
    void touchedIdsCombineBothFields() {
        assertEquals(List.of("a"), PersonalRollbacker.touched("a", null));
        assertEquals(List.of("b", "c"), PersonalRollbacker.touched(null, java.util.Arrays.asList("b", null, "c")));
        assertEquals(List.of(), PersonalRollbacker.touched(null, null));
    }
}
