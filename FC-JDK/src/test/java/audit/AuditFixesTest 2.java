package audit;

import core.fch.OpReFileUtils;
import core.fch.TxCreator;
import core.fch.Wallet;
import data.fchData.Cash;
import data.fchData.Freer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import utils.FchUtils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for defects confirmed in the 2026-09-07 audit triage.
 *
 * Each test states the pre-fix behaviour it pins down. The rollback/ES and token-accounting
 * paths are not covered here because they need a live Elasticsearch; they are listed in the
 * triage report as needing integration coverage.
 */
public class AuditFixesTest {

    // ---- Finding 10: rollback address INCOME read from the wrong map ----

    private static Map<String, Map<String, Long>> aggs(Map<String, Long> txo, Map<String, Long> stxo,
                                                       Map<String, Long> utxo, Map<String, Long> count,
                                                       Map<String, Long> cdd) {
        Map<String, Map<String, Long>> m = new HashMap<>();
        m.put(FchUtils.TXO_SUM, txo);
        m.put(FchUtils.STXO_SUM, stxo);
        m.put(FchUtils.UTXO_SUM, utxo);
        m.put(FchUtils.UTXO_COUNT, count);
        m.put(FchUtils.CDD, cdd);
        return m;
    }

    private static Map<String, Long> map(String k, Long v) {
        Map<String, Long> m = new HashMap<>();
        if (v != null) m.put(k, v);
        return m;
    }

    @Test
    @DisplayName("INCOME is the received total, not the spent total")
    public void incomeComesFromTxoSumNotStxoSum() {
        String addr = "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK";
        Freer cid = new Freer();
        cid.setId(addr);

        // Received 1000, spent 300, so 700 still unspent.
        FchUtils.applyAggsToCids(
                aggs(map(addr, 1000L), map(addr, 300L), map(addr, 700L), map(addr, 2L), map(addr, 5L)),
                List.of(cid));

        // Pre-fix this returned 300 -- the spent total -- making INCOME equal EXPEND.
        assertEquals(1000L, cid.getIncome(), "INCOME must be TXO_SUM");
        assertEquals(300L, cid.getExpend(), "EXPEND must be STXO_SUM");
        assertNotEquals(cid.getIncome(), cid.getExpend());
    }

    @Test
    @DisplayName("An address with received-but-unspent outputs gets 0 income, never null")
    public void incomeIsZeroNotNullWhenNothingSpent() {
        String addr = "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK";
        Freer cid = new Freer();
        cid.setId(addr);

        // Present in txoSum, absent from stxoSum: nothing has ever been spent.
        FchUtils.applyAggsToCids(
                aggs(map(addr, 500L), new HashMap<>(), map(addr, 500L), map(addr, 1L), new HashMap<>()),
                List.of(cid));

        // Pre-fix this wrote a null INCOME into Elasticsearch.
        assertNotNull(cid.getIncome());
        assertEquals(500L, cid.getIncome());
        assertEquals(0L, cid.getExpend());
    }

    // ---- Finding 14: mergeCashList batching and divide-by-zero ----

    @Test
    @DisplayName("mergeCashList rejects a zero output count instead of dividing by zero")
    public void mergeCashListRejectsZeroIssueNum() {
        List<Cash> cashList = new ArrayList<>();
        Cash cash = new Cash();
        cash.setValue(100_000_000L);
        cashList.add(cash);

        // Pre-fix: ArithmeticException from `sumValue / issueNum`.
        assertNull(TxCreatorHelper.merge(cashList, 0));
        assertNull(TxCreatorHelper.merge(cashList, -1));
    }

    /**
     * A valid secp256k1 private key. It must be valid: an all-zero key throws in prikeyToFid
     * before the division is ever reached, which would make the test pass for the wrong reason.
     * Both clients are null so nothing is broadcast.
     */
    private static final class TxCreatorHelper {
        private static final byte[] PRIKEY = new byte[32];
        static {
            java.util.Arrays.fill(PRIKEY, (byte) 0x11);
        }

        static String merge(List<Cash> cashList, int issueNum) {
            return Wallet.mergeCashList(cashList, issueNum, PRIKEY, null, null);
        }
    }

    // ---- Finding 19: readOpReFromFile trusted the on-disk length ----

    private static File tempOpReFile(byte[] content) throws IOException {
        File f = Files.createTempFile("opre-test", ".byte").toFile();
        f.deleteOnExit();
        Files.write(f.toPath(), content);
        return f;
    }

    private static byte[] beInt(int v) {
        return new byte[]{(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
    }

    @Test
    @DisplayName("A negative record length is refused instead of throwing NegativeArraySizeException")
    public void negativeLengthIsRefused() throws IOException {
        File f = tempOpReFile(beInt(-5));
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            core.fch.opReReadResult result = OpReFileUtils.readOpReFromFile(raf);
            assertTrue(result.isFileEnd(), "corrupt length must be reported as end of file");
            assertEquals(0, raf.getFilePointer(), "the file pointer must be rewound");
        }
    }

    @Test
    @DisplayName("An absurd record length is refused instead of allocating it")
    public void hugeLengthIsRefused() throws IOException {
        File f = tempOpReFile(beInt(Integer.MAX_VALUE));
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            core.fch.opReReadResult result = OpReFileUtils.readOpReFromFile(raf);
            assertTrue(result.isFileEnd());
            assertEquals(0, raf.getFilePointer());
        }
    }

    @Test
    @DisplayName("A length in the impossible band between marker and header is refused")
    public void lengthBetweenMarkerAndHeaderIsRefused() throws IOException {
        // 41 took the ">40" branch and then sliced at offset 52..86, throwing AIOOBE.
        byte[] content = new byte[4 + 41];
        System.arraycopy(beInt(41), 0, content, 0, 4);
        File f = tempOpReFile(content);
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            core.fch.opReReadResult result = OpReFileUtils.readOpReFromFile(raf);
            assertTrue(result.isFileEnd());
            assertEquals(0, raf.getFilePointer());
        }
    }

    // ---- Finding 22: multisig assembly did not bind signatures to one context ----

    @Test
    @DisplayName("buildSignedTx refuses empty input rather than returning a half-built tx")
    public void buildSignedTxRejectsEmptyInput() {
        assertNull(TxCreator.buildSignedTx(new String[]{}, core.fch.FchMainNetwork.MAINNETWORK));
        assertNull(TxCreator.buildSignedTx(null, core.fch.FchMainNetwork.MAINNETWORK));
    }
}
