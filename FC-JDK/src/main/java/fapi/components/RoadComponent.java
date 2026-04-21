package fapi.components;

import fapi.AbstractFapiComponent;
import fapi.FapiBalanceManager;
import fapi.FapiCode;
import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import fapi.message.UnifiedCodec.UnifiedResponse;
import fapi.client.FapiClient;
import fapi.components.map.MapEntry;
import fudp.connection.PeerConnection;
import fudp.node.FudpNode;
import utils.FchUtils;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static constants.FieldNames.*;

/**
 * ROAD Component - Relay Of Arbitrary Data
 * <p>
 * A minimal relay service that forwards arbitrary data to target FIDs.
 * ROAD is a "dumb pipe" - it doesn't interpret data content.
 * <p>
 * Design Principles:
 * 1. Data Agnostic: ROAD sees bytes, not messages
 * 2. No Sessions: Each relay is independent
 * 3. Maximum 2 Hops: Direct delivery or one chain relay
 * 4. Clear Boundaries: ROAD relays, MAP addresses, DOCK stores
 * 5. User Protection: maxCost parameter prevents unexpected charges
 * 6. Sender-Driven Discovery: The sender finds the target's ROAD URL
 *    (via BASE freer lookup + home.ROAD) and passes it as a parameter.
 *    ROAD never queries APIP or performs on-chain lookups.
 * <p>
 * API Methods:
 * - road.relay   - Send data to target FID (public API)
 * - road.forward - Receive forwarded data from another ROAD (internal)
 * - road.stats   - Get relay statistics
 */
public class RoadComponent extends AbstractFapiComponent {

    private static final String COMPONENT_NAME = "ROAD";
    private static final long RELAY_ACK_TIMEOUT_MS = 3000;
    private static final long CHAIN_RELAY_TIMEOUT_MS = 10000;
    private static final long MULTI_TARGET_TOTAL_TIMEOUT_MS = 15000;
    
    // Pricing (from Service)
    private long pricePerKBIn;
    private long pricePerKBOut;
    
    // Dependencies
    private MapComponent mapComponent;
    private FapiBalanceManager balanceManager;
    
    // Statistics
    private final AtomicLong totalRelays = new AtomicLong(0);
    private final AtomicLong successfulRelays = new AtomicLong(0);
    private final AtomicLong failedRelays = new AtomicLong(0);
    private final AtomicLong chainRelays = new AtomicLong(0);
    private final AtomicLong bytesIn = new AtomicLong(0);
    private final AtomicLong bytesOut = new AtomicLong(0);
    private final AtomicLong totalChargedIn = new AtomicLong(0);
    private final AtomicLong totalChargedOut = new AtomicLong(0);
    private final Map<String, AtomicLong> errorCounts = new HashMap<>();
    
    @Override
    public String getName() {
        return COMPONENT_NAME;
    }
    
    @Override
    public List<String> getApiList() {
        return List.of(
            "road.relay",    // Send data to target (public API)
            "road.forward",  // Receive forwarded data from another ROAD (internal)
            "road.stats"     // Relay statistics
        );
    }
    
    @Override
    protected void doInitialize() {
        this.mapComponent = getComponent(MapComponent.class);
        if (mapComponent == null) {
            throw new IllegalStateException("MAP component is required for ROAD");
        }
        
        this.balanceManager = server.getBalanceManager();
        
        loadPricing();
        
        errorCounts.put("NOT_FOUND", new AtomicLong(0));
        errorCounts.put("DELIVERY_FAILED", new AtomicLong(0));
        errorCounts.put("MAX_HOPS_REACHED", new AtomicLong(0));
        errorCounts.put("INSUFFICIENT_BALANCE", new AtomicLong(0));
        errorCounts.put("MAX_COST_EXCEEDED", new AtomicLong(0));
        
        log.info("ROAD component initialized: pricePerKBIn={}, pricePerKBOut={}",
                pricePerKBIn, pricePerKBOut);
    }
    
    private void loadPricing() {
        data.feipData.Service service = settings.getService();
        if (service == null) {
            log.warn("Service is null, using default pricing");
            this.pricePerKBIn = 10;
            this.pricePerKBOut = 10;
            return;
        }
        
        if (service.getPricePerKBIn() != null && !service.getPricePerKBIn().isEmpty()) {
            this.pricePerKBIn = FchUtils.coinToSatoshi(Double.parseDouble(service.getPricePerKBIn()));
        } else if (service.getPricePerKB() != null && !service.getPricePerKB().isEmpty()) {
            this.pricePerKBIn = FchUtils.coinToSatoshi(Double.parseDouble(service.getPricePerKB()));
        } else {
            this.pricePerKBIn = 10;
        }
        
        if (service.getPricePerKBOut() != null && !service.getPricePerKBOut().isEmpty()) {
            this.pricePerKBOut = FchUtils.coinToSatoshi(Double.parseDouble(service.getPricePerKBOut()));
        } else if (service.getPricePerKB() != null && !service.getPricePerKB().isEmpty()) {
            this.pricePerKBOut = FchUtils.coinToSatoshi(Double.parseDouble(service.getPricePerKB()));
        } else {
            this.pricePerKBOut = 10;
        }
    }
    
    @Override
    public FapiResponse handleRequest(FapiRequest request, String peerId) {
        String method = request.getMethodName();
        String requestId = request.getId();
        
        if (method == null) {
            return errorResponse(requestId, FapiCode.BAD_REQUEST, "Method name is missing");
        }
        
        return switch (method) {
            case "stats" -> handleStats(request);
            case "relay" -> errorResponse(requestId, FapiCode.BAD_REQUEST, "relay must use unified binary protocol");
            case "forward" -> errorResponse(requestId, FapiCode.BAD_REQUEST, "forward must use unified binary protocol");
            default -> errorResponse(requestId, FapiCode.METHOD_NOT_ALLOWED, "Unknown method: " + method);
        };
    }
    
    @Override
    public boolean returnsBinaryData(String method) {
        return false;
    }
    
    @Override
    public UnifiedResponse handleUnifiedRequest(FapiRequest request, byte[] binaryData, String peerId) {
        String method = request.getMethodName();
        String requestId = request.getId();
        
        if (method == null) {
            return new UnifiedResponse(
                errorResponse(requestId, FapiCode.BAD_REQUEST, "Method name is missing"),
                null);
        }
        
        return switch (method) {
            case "relay" -> handleRelay(request, binaryData, peerId, false);
            case "forward" -> handleRelay(request, binaryData, peerId, true);
            case "stats" -> new UnifiedResponse(handleStats(request), null);
            default -> new UnifiedResponse(
                errorResponse(requestId, FapiCode.METHOD_NOT_ALLOWED, "Unknown method: " + method),
                null);
        };
    }
    
    // ==================== Relay Handler ====================
    
    private static final int MAX_TARGETS = 100;
    
    /**
     * Handle relay request.
     * <p>
     * Params:
     * - targetFid / targetFids: destination FID(s)
     * - targetRoad: (optional) URL of the remote ROAD to forward to if target is not local.
     *   The sender discovers this via BASE freer lookup (home.ROAD).
     * - maxCost: (optional) max satoshi the sender is willing to pay
     */
    private UnifiedResponse handleRelay(FapiRequest request, byte[] data, String peerId, boolean isForwarded) {
        String requestId = request.getId();
        totalRelays.incrementAndGet();
        
        if (data == null || data.length == 0) {
            failedRelays.incrementAndGet();
            return new UnifiedResponse(
                errorResponse(requestId, FapiCode.BAD_REQUEST, "Data is required"),
                null);
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> params = parseParams(request.getParams(), Map.class);
        if (params == null) {
            failedRelays.incrementAndGet();
            return new UnifiedResponse(
                errorResponse(requestId, FapiCode.BAD_REQUEST, "params is required"),
                null);
        }
        
        // Get target FIDs
        List<String> targetFids = new ArrayList<>();
        
        @SuppressWarnings("unchecked")
        List<String> targetFidsList = (List<String>) params.get(TARGET_FIDS);
        if (targetFidsList != null && !targetFidsList.isEmpty()) {
            targetFids.addAll(targetFidsList);
        }
        
        String singleTargetFid = (String) params.get(TARGET_FID);
        if (singleTargetFid != null && !singleTargetFid.isEmpty() && !targetFids.contains(singleTargetFid)) {
            targetFids.add(singleTargetFid);
        }
        
        if (targetFids.isEmpty()) {
            failedRelays.incrementAndGet();
            return new UnifiedResponse(
                errorResponse(requestId, FapiCode.BAD_REQUEST, "targetFid or targetFids is required"),
                null);
        }
        
        if (targetFids.size() > MAX_TARGETS) {
            failedRelays.incrementAndGet();
            return new UnifiedResponse(
                errorResponse(requestId, FapiCode.BAD_REQUEST, 
                    "Too many targets, max: " + MAX_TARGETS),
                null);
        }
        
        // Get optional targetRoad URL (sender-provided, from freer.home.ROAD)
        String targetRoad = (String) params.get(TARGET_ROAD);
        
        long maxCost = 0;
        if (params.containsKey(MAX_COST)) {
            Object maxCostObj = params.get(MAX_COST);
            if (maxCostObj instanceof Number) {
                maxCost = ((Number) maxCostObj).longValue();
            }
        }
        
        bytesIn.addAndGet(data.length);
        
        // Calculate costs
        long sizeKB = (data.length + 1023) / 1024;
        long ingressFeePerTarget = sizeKB * pricePerKBIn;
        long egressFeePerTarget = sizeKB * pricePerKBOut;
        long costPerTarget = ingressFeePerTarget + egressFeePerTarget;
        long maxPossibleCost = costPerTarget * targetFids.size();
        
        if (maxCost > 0 && maxPossibleCost > maxCost) {
            failedRelays.incrementAndGet();
            errorCounts.get("MAX_COST_EXCEEDED").incrementAndGet();
            return new UnifiedResponse(
                buildMultiRelayResponse(requestId, FapiCode.PAYMENT_REQUIRED,
                    "Estimated max cost " + maxPossibleCost + " exceeds max " + maxCost,
                    null, 0, 0),
                null);
        }
        
        if (balanceManager != null && !balanceManager.canAfford(peerId, costPerTarget)) {
            failedRelays.incrementAndGet();
            errorCounts.get("INSUFFICIENT_BALANCE").incrementAndGet();
            return new UnifiedResponse(
                buildMultiRelayResponse(requestId, FapiCode.PAYMENT_REQUIRED,
                    "Insufficient balance. Required per target: " + costPerTarget + " satoshi",
                    null, 0, 0),
                null);
        }
        
        // Process targets (parallel if multiple, inline if single)
        Map<String, RelayResult> relayResults = new LinkedHashMap<>();
        long totalIngressCharged = 0;
        long totalEgressCharged = 0;
        int successCount = 0;
        
        if (targetFids.size() == 1) {
            String targetFid = targetFids.get(0);
            RelayResult result = relayToTarget(targetFid, data, peerId, isForwarded,
                    ingressFeePerTarget, egressFeePerTarget, requestId, targetRoad);
            relayResults.put(targetFid, result);
        } else {
            Map<String, Future<RelayResult>> futures = new LinkedHashMap<>();
            ExecutorService executor = Executors.newFixedThreadPool(
                    Math.min(targetFids.size(), 10));
            try {
                for (String targetFid : targetFids) {
                    futures.put(targetFid, executor.submit(() ->
                        relayToTarget(targetFid, data, peerId, isForwarded,
                                ingressFeePerTarget, egressFeePerTarget, requestId, targetRoad)));
                }
                for (Map.Entry<String, Future<RelayResult>> entry : futures.entrySet()) {
                    try {
                        RelayResult result = entry.getValue().get(
                                MULTI_TARGET_TOTAL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        relayResults.put(entry.getKey(), result);
                    } catch (TimeoutException | InterruptedException | ExecutionException e) {
                        relayResults.put(entry.getKey(), new RelayResult(false, FapiCode.GATEWAY_TIMEOUT,
                                "Relay timed out", 0, 0, false, null));
                    }
                }
            } finally {
                executor.shutdownNow();
            }
        }
        
        for (Map.Entry<String, RelayResult> entry : relayResults.entrySet()) {
            RelayResult result = entry.getValue();
            if (result.success) {
                successCount++;
                totalIngressCharged += result.chargedIn;
                totalEgressCharged += result.chargedOut;
                successfulRelays.incrementAndGet();
            } else {
                if (result.chargedIn > 0) {
                    totalIngressCharged += result.chargedIn;
                }
                failedRelays.incrementAndGet();
            }
        }
        
        int code = successCount > 0 ? FapiCode.SUCCESS : FapiCode.BAD_GATEWAY;
        String message = String.format("Relayed to %d/%d targets", successCount, targetFids.size());
        
        return new UnifiedResponse(
            buildMultiRelayResponse(requestId, code, message, relayResults, 
                totalIngressCharged, totalEgressCharged),
            null);
    }
    
    private record RelayResult(boolean success, int code, String message, 
            long chargedIn, long chargedOut, boolean chainRelayed, String relayedVia) {}
    
    /**
     * Relay data to a single target FID.
     * <p>
     * Logic:
     * 1. Check local MAP -> deliver locally via FUDP
     * 2. If already forwarded (from another ROAD), can't forward again -> NOT_FOUND
     * 3. If targetRoad points to this server -> try direct FUDP send
     * 4. If targetRoad points to a remote server -> forward to that ROAD
     * 5. If forwarding failed -> fallback to direct FUDP (targetRoad may be an
     *    unrecognized alias for this server, e.g. Android emulator's 10.0.2.2)
     * 6. No targetRoad -> target is presumably on this server (the client already
     *    filtered out targetRoad when it equals its own serverUrl) -> try direct FUDP
     * 7. Otherwise -> NOT_FOUND
     */
    private RelayResult relayToTarget(String targetFid, byte[] data, String peerId, 
            boolean isForwarded, long ingressFee, long egressFee, String requestId, String targetRoad) {
        
        log.info("ROAD relay: target={}, from={}, targetRoad={}, isForwarded={}, dataLen={}",
                targetFid, peerId, targetRoad, isForwarded, data.length);
        
        // 1. Try local MAP
        MapEntry localEntry = mapComponent.getEntry(targetFid);
        if (localEntry != null) {
            log.info("ROAD step1: target {} found in MAP at {}:{}", 
                    targetFid, localEntry.getObservedIp(), localEntry.getObservedPort());
            return tryLocalDelivery(targetFid, data, localEntry, peerId, ingressFee, egressFee, requestId);
        }
        log.info("ROAD step1: target {} NOT in MAP (mapEntries={})", targetFid, mapComponent.getEntryCount());
        
        // 2. If already forwarded, max 2 hops reached
        if (isForwarded) {
            errorCounts.get("MAX_HOPS_REACHED").incrementAndGet();
            return new RelayResult(false, FapiCode.NOT_FOUND, "Target not found (max hops reached)", 
                    0, 0, false, null);
        }
        
        if (targetRoad != null && !targetRoad.isEmpty()) {
            // 3. targetRoad points to this server -> try direct FUDP send
            boolean isSelf = server.isSelfUrl(targetRoad);
            log.info("ROAD step3: isSelfUrl('{}') = {}", targetRoad, isSelf);
            if (isSelf) {
                return tryDirectFudpSend(targetFid, data, peerId, ingressFee, egressFee, requestId);
            }
            
            // 4. Forward to remote ROAD
            log.info("ROAD step4: forwarding to remote ROAD: {}", targetRoad);
            RelayResult forwardResult = forwardToRemoteRoad(
                requestId, targetFid, data, peerId, targetRoad, ingressFee, egressFee);
            if (forwardResult != null) {
                return forwardResult;
            }
            
            // 5. Forwarding failed -> try direct FUDP as fallback
            log.info("ROAD step5: remote forwarding to {} failed, trying direct FUDP for {}", targetRoad, targetFid);
            return tryDirectFudpSend(targetFid, data, peerId, ingressFee, egressFee, requestId);
        }
        
        // 6. No targetRoad: the client omits targetRoad when it equals its own serverUrl,
        //    meaning the target's home.ROAD points to this server. Try direct FUDP delivery.
        log.info("ROAD step6: no targetRoad (target is on this server), trying direct FUDP for {}", targetFid);
        RelayResult directResult = tryDirectFudpSend(targetFid, data, peerId, ingressFee, egressFee, requestId);
        if (directResult.success) {
            return directResult;
        }
        
        // 7. All methods exhausted
        log.info("ROAD step7: direct FUDP also failed for {}, returning NOT_FOUND", targetFid);
        errorCounts.get("NOT_FOUND").incrementAndGet();
        return directResult;
    }
    
    /**
     * Deliver via MAP entry (target is registered in local MAP).
     */
    private RelayResult tryLocalDelivery(String targetFid, byte[] data, MapEntry entry,
            String peerId, long ingressFee, long egressFee, String requestId) {
        boolean success = deliverLocally(targetFid, data, entry);
        if (success) {
            chargeRelay(peerId, targetFid, ingressFee, egressFee, requestId);
            bytesOut.addAndGet(data.length);
            return new RelayResult(true, FapiCode.SUCCESS, "Delivered", 
                    ingressFee, egressFee, false, null);
        } else {
            errorCounts.get("DELIVERY_FAILED").incrementAndGet();
            return new RelayResult(false, FapiCode.BAD_GATEWAY, "Delivery failed", 
                    0, 0, false, null);
        }
    }
    
    /**
     * Try sending directly via FUDP when targetRoad points to this server.
     * The target's home.ROAD says they are on this server, so attempt
     * delivery even without a MAP entry.
     * <p>
     * Uses sendBytesWaitAck to confirm the target actually received the data.
     * This runs on a FapiServer worker thread (not the FUDP recv thread),
     * so blocking for the ACK is safe.
     */
    private RelayResult tryDirectFudpSend(String targetFid, byte[] data,
            String peerId, long ingressFee, long egressFee, String requestId) {
        FudpNode fudpNode = server.getFudpNode();
        if (fudpNode == null) {
            log.warn("ROAD tryDirectFudpSend: FudpNode is null");
            errorCounts.get("DELIVERY_FAILED").incrementAndGet();
            return new RelayResult(false, FapiCode.BAD_GATEWAY, "FudpNode not available", 
                    0, 0, false, null);
        }
        
        try {
            ensurePeerInBook(fudpNode, targetFid);
            log.info("ROAD tryDirectFudpSend: calling sendNotifyWaitAck to {} ({} bytes)", targetFid, data.length);
            boolean acked = fudpNode.sendNotifyWaitAck(targetFid, data, RELAY_ACK_TIMEOUT_MS);
            if (!acked) {
                log.warn("ROAD tryDirectFudpSend: NO ACK from {} within {}ms", targetFid, RELAY_ACK_TIMEOUT_MS);
                errorCounts.get("DELIVERY_FAILED").incrementAndGet();
                return new RelayResult(false, FapiCode.NOT_FOUND,
                        "Target did not acknowledge delivery (timeout)",
                        0, 0, false, null);
            }
            chargeRelay(peerId, targetFid, ingressFee, egressFee, requestId);
            bytesOut.addAndGet(data.length);
            log.info("ROAD tryDirectFudpSend: SUCCESS - ACK received, sent {} bytes to {}", data.length, targetFid);
            return new RelayResult(true, FapiCode.SUCCESS, "Delivered (direct)", 
                    ingressFee, egressFee, false, null);
        } catch (Exception e) {
            log.warn("ROAD tryDirectFudpSend: FAILED for {} - {}: {}", targetFid, e.getClass().getSimpleName(), e.getMessage());
            errorCounts.get("DELIVERY_FAILED").incrementAndGet();
            return new RelayResult(false, FapiCode.NOT_FOUND, 
                    "Target home.ROAD is this server but target is not reachable: " + e.getMessage(), 
                    0, 0, false, null);
        }
    }
    
    /**
     * Ensure a peer is in the FudpNode's PeerBook so that sendBytes can reach it.
     * If the peer is not in the PeerBook but has an active connection (they
     * connected to this server as a client), extract their address and public
     * key from the ConnectionManager and add them.
     */
    private void ensurePeerInBook(FudpNode fudpNode, String targetFid) {
        if (fudpNode.getPeer(targetFid) != null) {
            log.info("ROAD ensurePeerInBook: {} already in PeerBook", targetFid);
            return;
        }
        log.info("ROAD ensurePeerInBook: {} NOT in PeerBook, checking ConnectionManager...", targetFid);
        
        var connMgr = fudpNode.getProtocol().getConnectionManager();
        PeerConnection conn = connMgr.getAnyConnection(targetFid);
        if (conn == null) {
            StringBuilder peerIds = new StringBuilder();
            for (PeerConnection c : connMgr.getAllConnections()) {
                if (peerIds.length() > 0) peerIds.append(", ");
                peerIds.append(c.getPeerId()).append("(").append(c.getState()).append(")");
            }
            log.warn("ROAD ensurePeerInBook: {} NOT in ConnectionManager. Total: {}, peers: [{}]",
                    targetFid, connMgr.getConnectionCount(), peerIds);
            return;
        }
        
        log.info("ROAD ensurePeerInBook: {} found in ConnectionManager, state={}, addr={}",
                targetFid, conn.getState(), conn.getPeerAddress());
        
        byte[] pubkey = conn.getPeerPublicKey();
        if (pubkey == null) {
            log.warn("ROAD ensurePeerInBook: {} has connection but no pubkey", targetFid);
            return;
        }
        
        java.net.SocketAddress peerAddr = conn.getPeerAddress();
        if (peerAddr instanceof InetSocketAddress inetAddr) {
            fudpNode.addPeer(targetFid, pubkey, inetAddr.getHostString(), inetAddr.getPort());
            log.info("ROAD ensurePeerInBook: ADDED {} to PeerBook at {}:{}", 
                    targetFid, inetAddr.getHostString(), inetAddr.getPort());
        } else {
            log.warn("ROAD ensurePeerInBook: {} has non-InetSocketAddress: {}", targetFid, peerAddr);
        }
    }
    
    private void chargeRelay(String peerId, String targetFid, long ingressFee, long egressFee, String requestId) {
        if (balanceManager == null) return;
        if (ingressFee > 0) {
            String chargeKey = "road.relay:in:" + requestId + ":" + targetFid + ":" + System.currentTimeMillis();
            balanceManager.charge(chargeKey, peerId, ingressFee, "road.relay:in");
            totalChargedIn.addAndGet(ingressFee);
        }
        if (egressFee > 0) {
            String chargeKey = "road.relay:out:" + requestId + ":" + targetFid + ":" + System.currentTimeMillis();
            balanceManager.charge(chargeKey, peerId, egressFee, "road.relay:out");
            totalChargedOut.addAndGet(egressFee);
        }
    }
    
    /**
     * Forward data to a remote ROAD by URL.
     * Bounded by CHAIN_RELAY_TIMEOUT_MS to prevent blocking the sender for too long.
     */
    private RelayResult forwardToRemoteRoad(String requestId, String targetFid, byte[] data,
            String peerId, String targetRoadUrl, long ingressFee, long egressFee) {
        try {
            FapiClient remoteClient = server.getOrCreateClient(targetRoadUrl);
            if (remoteClient == null) {
                log.warn("Cannot connect to remote ROAD: {}", targetRoadUrl);
                return null;
            }
            
            Map<String, Object> forwardParams = new HashMap<>();
            forwardParams.put(TARGET_FID, targetFid);
            forwardParams.put(ORIGIN_SID, settings.getSid());
            
            FapiRequest forwardRequest = FapiRequest.binaryOperation("road.forward", forwardParams, data.length, null);
            
            Future<UnifiedResponse> future = CompletableFuture.supplyAsync(() ->
                    remoteClient.requestWithBinaryData(forwardRequest, data));
            
            UnifiedResponse remoteResponse;
            try {
                remoteResponse = future.get(CHAIN_RELAY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                log.warn("Chain relay to {} timed out after {}ms", targetRoadUrl, CHAIN_RELAY_TIMEOUT_MS);
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (ExecutionException e) {
                log.warn("Chain relay to {} failed: {}", targetRoadUrl, e.getCause().getMessage());
                return null;
            }
            
            if (remoteResponse == null || remoteResponse.response() == null) {
                log.warn("No response from remote ROAD: {}", targetRoadUrl);
                return null;
            }
            
            if (!remoteResponse.response().isSuccess()) {
                log.warn("Remote ROAD {} returned error: {}", targetRoadUrl, remoteResponse.response().getMessage());
                return null;
            }
            
            if (balanceManager != null) {
                if (ingressFee > 0) {
                    String chargeKey = "road.relay:in:" + requestId + ":" + targetFid + ":" + System.currentTimeMillis();
                    balanceManager.charge(chargeKey, peerId, ingressFee, "road.relay:in:chain");
                    totalChargedIn.addAndGet(ingressFee);
                }
                if (egressFee > 0) {
                    String chargeKey = "road.relay:out:" + requestId + ":" + targetFid + ":" + System.currentTimeMillis();
                    balanceManager.charge(chargeKey, peerId, egressFee, "road.relay:out:chain");
                    totalChargedOut.addAndGet(egressFee);
                }
            }
            
            bytesOut.addAndGet(data.length);
            chainRelays.incrementAndGet();
            
            return new RelayResult(true, FapiCode.SUCCESS, "Delivered via chain relay", 
                    ingressFee, egressFee, true, targetRoadUrl);
            
        } catch (Exception e) {
            log.error("Failed to forward to remote ROAD {}: {}", targetRoadUrl, e.getMessage());
            return null;
        }
    }
    
    /**
     * Deliver data locally via FUDP direct send.
     */
    private boolean deliverLocally(String targetFid, byte[] data, MapEntry entry) {
        try {
            FudpNode fudpNode = server.getFudpNode();
            if (fudpNode == null) {
                log.error("FudpNode is not available");
                return false;
            }
            
            byte[] pubkey = utils.Hex.fromHex(entry.getPubkey());
            fudpNode.addPeer(targetFid, pubkey, entry.getObservedIp(), entry.getObservedPort());
            
            fudpNode.sendNotify(targetFid, data);
            
            log.debug("Delivered {} bytes to {} at {}:{}", 
                data.length, targetFid, entry.getObservedIp(), entry.getObservedPort());
            return true;
            
        } catch (Exception e) {
            log.error("Failed to deliver to {}: {}", targetFid, e.getMessage());
            return false;
        }
    }
    
    // ==================== Stats Handler ====================
    
    private FapiResponse handleStats(FapiRequest request) {
        String requestId = request.getId();
        
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRelays", totalRelays.get());
        stats.put("successfulRelays", successfulRelays.get());
        stats.put("failedRelays", failedRelays.get());
        stats.put("chainRelays", chainRelays.get());
        stats.put("bytesIn", bytesIn.get());
        stats.put("bytesOut", bytesOut.get());
        stats.put("totalChargedIn", totalChargedIn.get());
        stats.put("totalChargedOut", totalChargedOut.get());
        stats.put("pricePerKBIn", pricePerKBIn);
        stats.put("pricePerKBOut", pricePerKBOut);
        
        Map<String, Long> errors = new LinkedHashMap<>();
        for (Map.Entry<String, AtomicLong> e : errorCounts.entrySet()) {
            errors.put(e.getKey(), e.getValue().get());
        }
        stats.put("errorCounts", errors);
        stats.put("timestamp", System.currentTimeMillis());
        
        return successResponse(requestId, stats);
    }
    
    // ==================== Response Builder ====================
    
    private FapiResponse buildMultiRelayResponse(String requestId, int code, String message,
            Map<String, RelayResult> relayResults, long totalChargedIn, long totalChargedOut) {
        
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(CHARGED_IN, totalChargedIn);
        data.put(CHARGED_OUT, totalChargedOut);
        data.put(TOTAL_CHARGED, totalChargedIn + totalChargedOut);
        
        if (relayResults != null && !relayResults.isEmpty()) {
            Map<String, Object> results = new LinkedHashMap<>();
            int successCount = 0;
            int failCount = 0;
            
            for (Map.Entry<String, RelayResult> entry : relayResults.entrySet()) {
                RelayResult result = entry.getValue();
                Map<String, Object> targetResult = new LinkedHashMap<>();
                targetResult.put("success", result.success);
                targetResult.put("code", result.code);
                targetResult.put("message", result.message);
                targetResult.put(CHARGED_IN, result.chargedIn);
                targetResult.put(CHARGED_OUT, result.chargedOut);
                if (result.chainRelayed) {
                    targetResult.put(CHAIN_RELAYED, true);
                    if (result.relayedVia != null) {
                        targetResult.put(RELAYED_VIA, result.relayedVia);
                    }
                }
                results.put(entry.getKey(), targetResult);
                
                if (result.success) {
                    successCount++;
                } else {
                    failCount++;
                }
            }
            
            data.put(RELAY_RESULTS, results);
            data.put("successCount", successCount);
            data.put("failCount", failCount);
            data.put("totalTargets", relayResults.size());
        }
        
        FapiResponse response = new FapiResponse();
        response.setRequestId(requestId);
        response.setCode(code);
        response.setMessage(message);
        response.setData(data);
        
        return response;
    }
    
    // ==================== Getters ====================
    
    public long getPricePerKBIn() {
        return pricePerKBIn;
    }
    
    public long getPricePerKBOut() {
        return pricePerKBOut;
    }
}
