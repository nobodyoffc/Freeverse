package construct;

import data.feipData.Service;
import data.feipData.ServiceHistory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A service's dealer follows its dealerPubkey, and an update that names neither keeps both. */
class ServiceDealerTest {

    static final String PUBKEY = "037d81362d2e91e5766b047fba488b5d2f44212eb314bf9039659f4da0004a8186";
    static final String DEALER = "FTxMo6wbWR9G9Poac84d4jHaVUNfViALVD";
    static final String OTHER = "FB7UTj7vH6Qp69pNbKCJjv1WSAz2m9XRe7";

    static ServiceHistory op(String dealer, String pubkey) {
        ServiceHistory h = new ServiceHistory();
        h.setDealer(dealer);
        h.setDealerPubkey(pubkey);
        return h;
    }

    static Service stored(String dealer, String pubkey) {
        Service s = new Service();
        s.setDealer(dealer);
        s.setDealerPubkey(pubkey);
        return s;
    }

    @Test
    void anUpdateWithoutDealerFieldsKeepsTheDealer() {
        // b4b621…: three updates from the app named no dealer, and the dealer was erased
        Service s = stored(DEALER, PUBKEY);
        ConstructParser.applyDealer(s, op(null, null));
        assertEquals(DEALER, s.getDealer());
        assertEquals(PUBKEY, s.getDealerPubkey());
    }

    @Test
    void aRecordLeftWithAPubkeyButNoDealerGetsItsDealerBack() {
        Service s = stored(null, PUBKEY);
        ConstructParser.applyDealer(s, op(null, null));
        assertEquals(DEALER, s.getDealer());
    }

    @Test
    void aPubkeyDecidesTheDealer() {
        Service s = new Service();
        ConstructParser.applyDealer(s, op(null, PUBKEY));
        assertEquals(DEALER, s.getDealer());
        assertEquals(PUBKEY, s.getDealerPubkey());
    }

    @Test
    void aNewDealerWithoutPubkeyDropsTheStaleOne() {
        Service s = stored(DEALER, PUBKEY);
        ConstructParser.applyDealer(s, op(OTHER, null));
        assertEquals(OTHER, s.getDealer());
        assertNull(s.getDealerPubkey(), "the stored pubkey belongs to the old dealer");

        Service same = stored(DEALER, PUBKEY);
        ConstructParser.applyDealer(same, op(DEALER, null));
        assertEquals(PUBKEY, same.getDealerPubkey());
    }

    @Test
    void aServiceWithNoDealerAtAllStaysWithout() {
        Service s = new Service();
        ConstructParser.applyDealer(s, op(null, null));
        assertNull(s.getDealer());
        assertNull(s.getDealerPubkey());
    }
}
