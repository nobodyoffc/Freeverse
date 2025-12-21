package handlers;

import config.Configure;
import config.Settings;
import data.fcData.CidInfo;
import data.feipData.Service;
import data.feipData.serviceParams.Params;
import fudp.metrics.MeterDirection;
import fudp.metrics.MeterRecord;
import fudp.message.MessageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class BalanceManagerTest {
    private BalanceManager balanceManager;
    private Path tempDir;

    @AfterEach
    public void tearDown() throws IOException {
        if (balanceManager != null) {
            balanceManager.close();
        }
        if (tempDir != null) {
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
        }
    }

    @Test
    public void chargeWithinCreditAndIdempotent() throws Exception {
        balanceManager = newManager(5_000L, "0.00001");

        MeterRecord meter = MeterRecord.builder()
            .peerId("userA")
            .direction(MeterDirection.OUTBOUND)
            .messageType(MessageType.REQUEST)
            .payloadBytes(1500)
            .sendTimestampMillis(12345L)
            .receiveTimestampMillis(0L)
            .build();

        BalanceManager.ChargeResult first = balanceManager.checkAndCharge(meter);
        assertEquals(BalanceManager.ResultCode.INSUFFICIENT_BALANCE_BUT_WITHIN_CREDIT, first.getCode());
        assertEquals(BalanceManager.RequestStatus.INSUFFICIENT, first.getStatus());
        assertEquals(-2000L, first.getBalance());

        BalanceManager.ChargeResult second = balanceManager.checkAndCharge(meter);
        assertEquals(BalanceManager.ResultCode.ALREADY_EXISTS, second.getCode());
        assertEquals(BalanceManager.RequestStatus.INSUFFICIENT, second.getStatus());
    }

    @Test
    public void creditIdempotentAndConflict() throws Exception {
        balanceManager = newManager(2_000L, "0.00001");

        BalanceManager.CreditResult ok = balanceManager.credit("userB", "cash1", 3_000L,
            "manual", BalanceManager.CreditStatus.CONFIRMED, 10L, "blockX");
        assertEquals(BalanceManager.ResultCode.OK, ok.getCode());
        assertEquals(3_000L, ok.getBalance());

        BalanceManager.CreditResult again = balanceManager.credit("userB", "cash1", 3_000L,
            "manual", BalanceManager.CreditStatus.CONFIRMED, 10L, "blockX");
        assertEquals(BalanceManager.ResultCode.ALREADY_EXISTS, again.getCode());

        BalanceManager.CreditResult conflict = balanceManager.credit("userB", "cash1", 4_000L,
            "manual", BalanceManager.CreditStatus.CONFIRMED, 10L, "blockX");
        assertEquals(BalanceManager.ResultCode.ALREADY_EXISTS_WITH_CONFLICT, conflict.getCode());
    }

    @Test
    public void invalidKeyRejected() throws Exception {
        balanceManager = newManager(1_000L, "0.00001");
        BalanceManager.ChargeResult result = balanceManager.charge("bad key with space", "userC", 100L, null);
        assertEquals(BalanceManager.ResultCode.INVALID_KEY, result.getCode());
    }

    private BalanceManager newManager(long creditLimit, String pricePerKb) throws Exception {
        tempDir = Files.createTempDirectory("balance-manager-test");
        Settings settings = Mockito.mock(Settings.class);

        String fid = "fid-test";
        when(settings.getMainFid()).thenReturn(fid);
        when(settings.getSid()).thenReturn("sid-test");
        when(settings.getSymkey()).thenReturn(new byte[0]);
        when(settings.getDbDir()).thenReturn(tempDir.toString());
        when(settings.getSettingMap()).thenReturn(new HashMap<>(Map.of("creditLimit", creditLimit)));
        when(settings.getClient(Mockito.any())).thenReturn(null);

        Service service = new Service();
        Params params = new Params();
        params.setPricePerKB(pricePerKb);
        params.setOrderViaShare("0.1");
        params.setConsumeViaShare("0.1");
        service.setParams(params);
        when(settings.getService()).thenReturn(service);

        Configure config = new Configure();
        Field mapField = Configure.class.getDeclaredField("mainCidInfoMap");
        mapField.setAccessible(true);
        Map<String, CidInfo> cidMap = new HashMap<>();
        CidInfo cidInfo = new CidInfo();
        cidInfo.setId(fid);
        cidInfo.setPrikeyCipher(null);
        cidMap.put(fid, cidInfo);
        mapField.set(config, cidMap);
        when(settings.getConfig()).thenReturn(config);

        return new BalanceManager(settings);
    }
}
