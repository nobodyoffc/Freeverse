package fapi.components;

import fapi.AbstractFapiComponent;
import fapi.FapiBalanceManager;
import fapi.FapiBalanceManager.ResultCode;
import fapi.FapiCode;
import fapi.FudpEventAware;
import fapi.components.call.CallRelay;
import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import fudp.connection.PeerConnection;
import fudp.node.FudpNode;
import fudp.transport.DatagramResult;
import utils.FchUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * CALL component: the relay for voice calls, {@code CALL@No1_NrC7}
 * (VOICE_SPEC §7, to become FAPI16). It holds call sessions open, forwards
 * end-to-end sealed audio it cannot read, and bills each participant per
 * minute of traffic. The rules are in {@link CallRelay}; this class wires
 * them to FAPI requests, FUDP events and FAPI4 billing.
 * <p>
 * Every connection is a throwaway transport key; the request's delegation
 * names the FID it speaks for, and that FID is who is admitted and billed.
 * <p>
 * API: call.create, call.join, call.register, call.leave, call.info, call.stats.
 * Meetings ({@code kind = meeting}), call.rekey, call.prove, call.control,
 * call.hand and call.report are Phase 4.
 */
public class CallComponent extends AbstractFapiComponent implements FudpEventAware {

    private static final String COMPONENT_NAME = "CALL";

    private CallRelay relay;
    private FudpNode node;
    private ScheduledExecutorService ticker;

    @Override
    public String getName() {
        return COMPONENT_NAME;
    }

    @Override
    public List<String> getApiList() {
        return List.of("call.create", "call.join", "call.register", "call.leave", "call.info", "call.stats");
    }

    @Override
    protected void doInitialize() {
        node = server.getFudpNode();
        FapiBalanceManager balances = server.getBalanceManager();
        relay = new CallRelay(new CallRelay.Transport() {
            @Override
            public boolean sendDatagram(long connectionId, byte[] data) {
                return node.sendDatagram(connectionId, data) == DatagramResult.SENT;
            }

            @Override
            public void enableDatagrams(long connectionId) {
                node.enableDatagrams(connectionId);
            }

            @Override
            public void notify(String peerId, int dataType, byte[] data) {
                try {
                    node.sendNotify(peerId, data, dataType);
                } catch (Exception e) {
                    log.debug("CALL notify to {} failed: {}", peerId, e.getMessage());
                }
            }
        }, new CallRelay.Billing() {
            @Override
            public boolean canAfford(String fid, long amount) {
                return balances == null || balances.canAfford(fid, amount);
            }

            @Override
            public boolean charge(String key, String fid, long amount, String meta) {
                if (balances == null) return true;
                ResultCode c = balances.charge(key, fid, amount, meta).getCode();
                return c == ResultCode.OK || c == ResultCode.ALREADY_EXISTS
                        || c == ResultCode.INSUFFICIENT_BALANCE_BUT_WITHIN_CREDIT;
            }
        }, loadPricing());

        ticker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "call-relay-tick");
            t.setDaemon(true);
            return t;
        });
        ticker.scheduleWithFixedDelay(() -> {
            try {
                relay.tick(System.currentTimeMillis());
            } catch (RuntimeException e) {
                log.warn("CALL tick failed", e);
            }
        }, 1, 1, TimeUnit.SECONDS);
        log.info("CALL component initialized");
    }

    /** Per-KB prices from the on-chain Service, as ROAD reads them; zero offers a free relay. */
    private CallRelay.Pricing loadPricing() {
        data.feipData.Service service = settings == null ? null : settings.getService();
        if (service == null) return new CallRelay.Pricing(10, 10);
        return new CallRelay.Pricing(price(service.getPricePerKBIn(), service.getPricePerKB()),
                price(service.getPricePerKBOut(), service.getPricePerKB()));
    }

    private static long price(String specific, String general) {
        String p = specific != null && !specific.isEmpty() ? specific : general;
        if (p == null || p.isEmpty()) return 10;
        return FchUtils.coinToSatoshi(Double.parseDouble(p));
    }

    @Override
    @SuppressWarnings("unchecked")
    public FapiResponse handleRequest(FapiRequest request, String peerId) {
        String id = request.getId();
        String method = request.getMethodName();
        if (method == null) return errorResponse(id, FapiCode.BAD_REQUEST, "Method name is missing");
        Map<String, Object> params = request.getParams() == null ? Map.of()
                : parseParams(request.getParams(), Map.class);
        if (params == null) return errorResponse(id, FapiCode.BAD_REQUEST, "params must be an object");
        long now = System.currentTimeMillis();
        try {
            return switch (method) {
                case "create" -> successResponse(id, relay.create(peerId, params, now));
                case "register" -> successResponse(id, relay.register(peerId, params, now));
                case "join" -> {
                    PeerConnection conn = node.getProtocol().getConnectionManager().getAnyConnection(peerId);
                    if (conn == null) yield errorResponse(id, FapiCode.BAD_REQUEST, "no FUDP connection");
                    yield successResponse(id, relay.join(peerId, conn.getConnectionId(), params, now));
                }
                case "leave" -> {
                    PeerConnection conn = node.getProtocol().getConnectionManager().getAnyConnection(peerId);
                    if (conn != null) relay.leave(conn.getConnectionId(), now);
                    yield successResponse(id, Map.of());
                }
                case "info" -> successResponse(id, relay.info(String.valueOf(params.get("meetingId"))));
                case "stats" -> successResponse(id, relay.stats());
                default -> errorResponse(id, FapiCode.NOT_FOUND, "Unknown method: " + method);
            };
        } catch (CallRelay.Refused r) {
            return errorResponse(id, r.code, r.getMessage());
        }
    }

    // ===== FUDP events, dispatched by FapiServer =====

    @Override
    public void onDatagram(String peerId, long connectionId, byte[] data) {
        relay.onDatagram(connectionId, data);
    }

    @Override
    public void onNotifyReceived(String peerId, long messageId, int dataType, byte[] data) {
        relay.onNotify(peerId, dataType, data);
    }

    @Override
    public void onPeerDisconnected(String peerId, long connectionId) {
        relay.leave(connectionId, System.currentTimeMillis());
    }

    @Override
    protected void doClose(long timeoutMs) throws InterruptedException {
        if (ticker != null) ticker.shutdownNow();
    }
}
