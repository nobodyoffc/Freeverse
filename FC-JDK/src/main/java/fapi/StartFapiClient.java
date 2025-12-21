package fapi;

import clients.ClientGroup;
import com.google.gson.Gson;
import config.ApiAccount;
import config.ApiProvider;
import config.Configure;
import config.Starter;
import config.Settings;
import data.apipData.Fcdsl;
import data.fcData.FcEntity;
import data.fcData.Module;
import data.fchData.*;
import data.feipData.Service;
import data.feipData.serviceParams.Params;
import fapi.client.FapiClient;
import fapi.message.FapiResponse;
import fudp.node.FudpNode;
import fudp.message.PongMessage;
import fudp.node.NodeConfig;
import fudp.node.NodeStats;
import fudp.node.Peer;
import ui.Inputer;
import ui.Menu;
import utils.JsonUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * FAPI 客户端启动类
 * 参考 StartApipClient 的设计，提供交互式测试界面
 */
public class StartFapiClient {
    public static final int DEFAULT_SIZE = 20;
    public static Settings settings;
    public static FudpNode fudpNode;
    public static FapiClient fapiClient;
    public static BufferedReader br;
    public static String clientName = "FAPI Client";


    public static void main(String[] args) {
        Menu.welcome(clientName);

        List<Module> modules = new ArrayList<>();
        modules.add(new Module(Module.ModuleType.NODE.name(), "FUDP"));
        modules.add(new Module(Service.class.getSimpleName(),Service.ServiceType.FAPI.name()));

        br = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Initializing FUDP Node...");

        Map<String,Object> settingMap = new HashMap<>();

        settings = Starter.startClient(clientName, settingMap, br, modules, null);

        setDefaultSettingMap(settings != null ? settings.getSettingMap() : settingMap);

        if (settings == null) return;

        fudpNode = settings.getFudpNode();
        fapiClient = (FapiClient) settings.getClient(Service.ServiceType.FAPI);

        Menu menu = new Menu("FAPI Client", StartFapiClient::close);

        menu.add("Basic APIs", StartFapiClient::basicApi);
        menu.add("Wallet APIs", StartFapiClient::walletApi);
        menu.add("Entity By ID", StartFapiClient::entityByIds);
        menu.add("Entity Search", StartFapiClient::entitySearch);
        menu.add("General Query", StartFapiClient::generalQuery);
        menu.add("Settings", StartFapiClient::settings);

        menu.showAndSelect(br);

    }

    private static void close() {
        if (fudpNode != null) {
            fudpNode.stop();
        }
        if (settings != null) {
            try {
                settings.close();
            } catch (Exception e) {
                System.out.println("Error closing settings:"+e.getMessage());
            }
        }
    }

    private static void setDefaultSettingMap(Map<String, Object> settingMap) {
        if (settingMap == null) {
            return;
        }
        settingMap.putIfAbsent(Settings.BALANCE_TOLERANCE_PCT, Settings.DEFAULT_BALANCE_TOLERANCE_PCT);
        settingMap.putIfAbsent(Settings.BALANCE_TOLERANCE_SAT_MIN, Settings.DEFAULT_BALANCE_TOLERANCE_SAT_MIN);
        settingMap.putIfAbsent(Settings.BALANCE_DRIFT_ACCUM_PCT, Settings.DEFAULT_BALANCE_DRIFT_ACCUM_PCT);
        settingMap.putIfAbsent(Settings.BALANCE_DRIFT_ACCUM_SAT, Settings.DEFAULT_BALANCE_DRIFT_ACCUM_SAT);
        settingMap.putIfAbsent(Settings.BALANCE_DRIFT_STOP_PCT, Settings.DEFAULT_BALANCE_DRIFT_STOP_PCT);
        settingMap.putIfAbsent(Settings.BALANCE_DRIFT_STOP_SAT, Settings.DEFAULT_BALANCE_DRIFT_STOP_SAT);
        settingMap.putIfAbsent(Settings.BALANCE_MAX_CONSECUTIVE_DRIFT, Settings.DEFAULT_BALANCE_MAX_CONSECUTIVE_DRIFT);
        settingMap.putIfAbsent(Settings.BALANCE_DRIFT_ACTION, Settings.DEFAULT_BALANCE_DRIFT_ACTION);
        settingMap.putIfAbsent(Settings.BALANCE_DISPLAY_PRECISION, Settings.DEFAULT_BALANCE_DISPLAY_PRECISION);
        settingMap.putIfAbsent(NodeConfig.FUDP_PORT, 8501L);
        settingMap.putIfAbsent(NodeConfig.FUDP_DATA_DIR, "~/.fudp_client");
    }

    private static boolean tryBootstrap() {
        if (fapiClient != null) {
            return true;
        }
        System.out.println("Bootstrapping FAPI service via default seeds...");
        if (bootstrapAndPersist(FapiClient.loadDefaultEndpoints(), "bootstrap")) {
            return true;
        }
        System.out.println("Bootstrap failed. Please configure manually.");
        return false;
    }
    
    public static void basicApi() {
        Menu menu = new Menu("Basic APIs");
        menu.add("ping", StartFapiClient::ping);
        menu.add("bestBlock", StartFapiClient::bestBlock);
        menu.add("bestHeight", StartFapiClient::bestHeight);
        menu.add("chainInfo", StartFapiClient::chainInfo);
        menu.add("blockTimeHistory", StartFapiClient::blockTimeHistory);
        menu.add("difficultyHistory", StartFapiClient::difficultyHistory);
        menu.add("hashRateHistory", StartFapiClient::hashRateHistory);
        menu.add("totals", StartFapiClient::totals);

        
        menu.showAndSelect(br);
    }
    
    public static void walletApi() {
        Menu menu = new Menu("Wallet APIs");
        menu.add("balanceByIds", StartFapiClient::balanceByIds);
        menu.add("broadcastTx", StartFapiClient::broadcastTx);
        menu.add("decodeTx", StartFapiClient::decodeTx);
        menu.add("estimateFee", StartFapiClient::estimateFee);
        menu.add("cashValid", StartFapiClient::cashValid);
        menu.add("getUtxo", StartFapiClient::getUtxo);
        
        menu.showAndSelect(br);
    }
    
    private static void bestBlock() {
        if (fapiClient == null) {
            System.out.println("FAPI Client not configured.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Requesting bestBlock...");
        Block block = fapiClient.bestBlock();
        if (block != null) {
            JsonUtils.printJson(block);
        } else {
            System.out.println("Failed to get best block.");
            if (fapiClient.getLastError() != null) {
                System.out.println("Error: " + fapiClient.getLastError().getMessage());
            }
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void bestHeight() {
        if (fapiClient == null) {
            System.out.println("FAPI Client not configured.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Requesting bestHeight...");
        Long height = fapiClient.bestHeight();
        if (height != null) {
        System.out.println("Best height: " + height);
        } else {
            System.out.println("Failed to get best height.");
            if (fapiClient.getLastError() != null) {
                System.out.println("Error: " + fapiClient.getLastError().getMessage());
            }
        }
        Menu.anyKeyToContinue(br);
    }

    private static void chainInfo() {
        if (!ensureClientConfigured()) {
            return;
        }

        String heightInput = Inputer.inputString(br, "Input height (optional, Enter for best):");
        Long height = null;
        if (heightInput != null && !heightInput.isBlank()) {
            try {
                height = Long.parseLong(heightInput.trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid height.");
                Menu.anyKeyToContinue(br);
                return;
            }
        }

        System.out.println("Requesting chainInfo" + (height != null ? (" at height " + height) : " (best)") + "...");
        FchChainInfo info = fapiClient.chainInfo(height);
        if (info != null) {
            JsonUtils.printJson(info);
        } else {
            System.out.println("Failed to get chain info.");
            if (fapiClient.getLastError() != null) {
                System.out.println("Error: " + fapiClient.getLastError().getMessage());
            }
        }
        Menu.anyKeyToContinue(br);
    }

    private static void blockTimeHistory() {
        if (!ensureClientConfigured()) {
            return;
        }

        HistoryParams params = inputHistoryParams();
        if (params == null) {
            return;
        }

        System.out.println("Requesting blockTimeHistory...");
        Map<Long, Long> result = fapiClient.blockTimeHistory(params.startTime, params.endTime, params.count);
        printHistoryResult("blockTimeHistory", result);
    }

    private static void difficultyHistory() {
        if (!ensureClientConfigured()) {
            return;
        }

        HistoryParams params = inputHistoryParams();
        if (params == null) {
            return;
        }

        System.out.println("Requesting difficultyHistory...");
        Map<Long, String> result = fapiClient.difficultyHistory(params.startTime, params.endTime, params.count);
        printHistoryResult("difficultyHistory", result);
    }

    private static void hashRateHistory() {
        if (!ensureClientConfigured()) {
            return;
        }

        HistoryParams params = inputHistoryParams();
        if (params == null) {
            return;
        }

        System.out.println("Requesting hashRateHistory...");
        Map<Long, String> result = fapiClient.hashRateHistory(params.startTime, params.endTime, params.count);
        printHistoryResult("hashRateHistory", result);
    }
    
    private static void ping() {
        if (fudpNode == null) {
            System.out.println("FUDP Node not started.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        String peerId;
        // Use configured service peer ID if available
        if (fapiClient != null && fapiClient.getServicePeerId() != null) {
            peerId = fapiClient.getServicePeerId();
            System.out.println("Using configured service peer ID: " + peerId);
        } else {
            // Only ask if no service is configured
            peerId = Inputer.inputString(br, "Input peer FID to ping:");
            if (peerId.isEmpty()) {
                System.out.println("No peer ID provided.");
                Menu.anyKeyToContinue(br);
                return;
            }
        }
        
        try {
            fudpNode.ping(peerId);
            System.out.println("Ping sent to " + peerId);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        Menu.anyKeyToContinue(br);
    }
    

    
    private static boolean ensureClientConfigured() {
        if (fapiClient != null) {
            return true;
        }
        System.out.println("FAPI Client not configured.");
        Menu.anyKeyToContinue(br);
        return false;
    }

    private static <T> void entityByIds() {
        if (!ensureClientConfigured()) {
            return;
        }

        String entityName = Inputer.inputString(br, "Input entity name:");
        if (entityName == null || entityName.isBlank()) {
            System.out.println("Entity name is required.");
            Menu.anyKeyToContinue(br);
            return;
        }

        entityName = entityName.trim().toLowerCase();
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) FcEntity.getEntityClass(entityName);
        if (clazz == null) {
            System.out.println("Unsupported entity: " + entityName);
            Menu.anyKeyToContinue(br);
            return;
        }

        String[] ids = Inputer.inputStringArrayWithSeparator(br, "IDs", ",");
        if (ids.length == 0) {
            System.out.println("At least one ID is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        System.out.println("Requesting entityByIds for " + entityName + "...");
        Map<String, T> result = fapiClient.entityByIds(entityName, clazz, ids);
        if (result != null) {
            System.out.println("Got " + result.size() + " items.");
            JsonUtils.printJson(fapiClient.getLastResponse());
        } else {
            System.out.println("Failed to get " + entityName + " items.");
            if (fapiClient.getLastError() != null) {
                System.out.println("Error: " + fapiClient.getLastError().getMessage());
            }
        }
        Menu.anyKeyToContinue(br);
    }

    private static <T> void entitySearch() {
        if (!ensureClientConfigured()) {
            return;
        }

        String entityName = Inputer.inputString(br, "Input entity name:");
        if (entityName == null || entityName.isBlank()) {
            System.out.println("Entity name is required.");
            Menu.anyKeyToContinue(br);
            return;
        }

        entityName = entityName.trim().toLowerCase();
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) FcEntity.getEntityClass(entityName);
        if (clazz == null) {
            System.out.println("Unsupported entity: " + entityName);
            Menu.anyKeyToContinue(br);
            return;
        }

        entitySearch(entityName, clazz, DEFAULT_SIZE, "id:asc");
    }
    
    private static void totals() {
        if (!ensureClientConfigured()) {
            return;
        }
        
        System.out.println("Requesting entity list (totals)...");
        Map<String, String> result = fapiClient.totals();
        if (result != null && !result.isEmpty()) {
            System.out.println("Got " + result.size() + " entities:");
            if (fapiClient.getLastResponse() != null) {
                System.out.println("\nResponse details:");
                JsonUtils.printJson(fapiClient.getLastResponse());
            }
        } else {
            System.out.println("Failed to get entity list.");
            if (fapiClient.getLastError() != null) {
                System.out.println("Error: " + fapiClient.getLastError().getMessage());
            }
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void balanceByIds() {
        if (!ensureClientConfigured()) {
            return;
        }
        
        String[] fids = Inputer.inputStringArrayWithSeparator(br, "FIDs (comma separated)", ",");
        if (fids.length == 0) {
            System.out.println("At least one FID is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Requesting balances...");
        Map<String, Long> result = fapiClient.balanceByIds(fids);
        if (result != null) {
            System.out.println("Got " + result.size() + " balances:");
            for (Map.Entry<String, Long> entry : result.entrySet()) {
                System.out.println(entry.getKey() + ": " + utils.FchUtils.satoshiToCoin(entry.getValue()) + " FCH");
            }
        } else {
            System.out.println("Failed to get balances.");
            if (fapiClient.getLastError() != null) {
                System.out.println("Error: " + fapiClient.getLastError().getMessage());
            }
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void broadcastTx() {
        if (!ensureClientConfigured()) {
            return;
        }
        
        String rawTx = Inputer.inputString(br, "Input raw transaction (hex):");
        if (rawTx == null || rawTx.isEmpty()) {
            System.out.println("Raw transaction is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Broadcasting transaction...");
        String txId = fapiClient.broadcastTx(rawTx);
        if (txId != null) {
            System.out.println("Transaction broadcasted successfully!");
            System.out.println("TX ID: " + txId);
        } else {
            System.out.println("Failed to broadcast transaction.");
            if(fapiClient.getLastResponse()!=null)
                System.out.println(new Gson().toJson(fapiClient.getLastResponse()));
            else if (fapiClient.getLastError() != null) {
                System.out.println("Error: " + fapiClient.getLastError().getMessage());
            }
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void decodeTx() {
        if (!ensureClientConfigured()) {
            return;
        }
        
        String rawTx = Inputer.inputString(br, "Input raw transaction (hex):");
        if (rawTx == null || rawTx.isEmpty()) {
            System.out.println("Raw transaction is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Decoding transaction...");
        Object decoded = fapiClient.decodeTx(rawTx);
        if (decoded != null) {
            System.out.println("Decoded transaction:");
            JsonUtils.printJson(decoded);
        } else {
            System.out.println("Failed to decode transaction.");
            if (fapiClient.getLastError() != null) {
                System.out.println("Error: " + fapiClient.getLastError().getMessage());
            }
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void estimateFee() {
        if (!ensureClientConfigured()) {
            return;
        }
        
        System.out.println("Note: Specifying blocks uses estimatesmartfee, leaving empty uses estimatefee");
        String nBlocksStr = Inputer.inputString(br, "Input number of blocks (optional, Enter for default):");
        Integer nBlocks = null;
        if (nBlocksStr != null && !nBlocksStr.isEmpty()) {
            try {
                nBlocks = Integer.parseInt(nBlocksStr);
                if (nBlocks <= 0) {
                    System.out.println("Invalid number, using default (no blocks specified).");
                    nBlocks = null;
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid number, using default (no blocks specified).");
                nBlocks = null;
            }
        }
        
        System.out.println("Estimating fee" + (nBlocks != null ? " for " + nBlocks + " blocks..." : "..."));
        Double feeRate = fapiClient.estimateFee(nBlocks);
        if (feeRate != null) {
            System.out.println("Estimated fee rate: " + feeRate + " FCH/KB");
        } else {
            System.out.println("Failed to estimate fee.");
            if(fapiClient.getLastResponse()!=null)
                System.out.println(new Gson().toJson(fapiClient.getLastResponse()));
            else if (fapiClient.getLastError() != null) {
                System.out.println("Error: " + fapiClient.getLastError().getMessage());
            }
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void cashValid() {
        if (!ensureClientConfigured()) {
            return;
        }
        
        String fid = Inputer.inputString(br, "Input FID:");
        if (fid == null || fid.isEmpty()) {
            System.out.println("FID is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        String amountStr = Inputer.inputString(br, "Input amount (FCH, optional):");
        Double amount = null;
        if (amountStr != null && !amountStr.isEmpty()) {
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                System.out.println("Invalid amount.");
                Menu.anyKeyToContinue(br);
                return;
            }
        }
        
        String cdStr = Inputer.inputString(br, "Input CD (optional):");
        Long cd = null;
        if (cdStr != null && !cdStr.isEmpty()) {
            try {
                cd = Long.parseLong(cdStr);
            } catch (NumberFormatException e) {
                System.out.println("Invalid CD.");
                Menu.anyKeyToContinue(br);
                return;
            }
        }
        
        System.out.println("Requesting valid cashes...");
        List<Cash> result = fapiClient.cashValid(fid, amount, cd, null, 1, 0);
        if (result != null) {
            System.out.println("Got " + result.size() + " valid cashes:");
            JsonUtils.printJson(result);
        } else {
            System.out.println("Failed to get valid cashes.");
            if (fapiClient.getLastError() != null) {
                System.out.println("Error: " + fapiClient.getLastError().getMessage());
            }
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void getUtxo() {
        if (!ensureClientConfigured()) {
            return;
        }
        
        String address = Inputer.inputString(br, "Input address (FID):");
        if (address == null || address.isEmpty()) {
            System.out.println("Address is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        String amountStr = Inputer.inputString(br, "Input amount (FCH, optional):");
        Double amount = null;
        if (amountStr != null && !amountStr.isEmpty()) {
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                System.out.println("Invalid amount.");
                Menu.anyKeyToContinue(br);
                return;
            }
        }
        
        String cdStr = Inputer.inputString(br, "Input CD (optional):");
        Long cd = null;
        if (cdStr != null && !cdStr.isEmpty()) {
            try {
                cd = Long.parseLong(cdStr);
            } catch (NumberFormatException e) {
                System.out.println("Invalid CD.");
                Menu.anyKeyToContinue(br);
                return;
            }
        }
        
        System.out.println("Requesting UTXOs...");
        List<data.apipData.Utxo> result = fapiClient.getUtxo(address, amount, cd);
        if (result != null) {
            System.out.println("Got " + result.size() + " UTXOs:");
            JsonUtils.printJson(result);
        } else {
            System.out.println("Failed to get UTXOs.");
            if (fapiClient.getLastError() != null) {
                System.out.println("Error: " + fapiClient.getLastError().getMessage());
            }
        }
        Menu.anyKeyToContinue(br);
    }

    private static <T> void entitySearch(String entityName, Class<T> clazz, int defaultSize, String defaultSort) {
        if (!ensureClientConfigured()) {
            return;
        }

        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;

        System.out.println("Requesting entitySearch for " + entityName + "...");
        List<T> result = fapiClient.entitySearch(entityName, fcdsl, clazz);
        if (result != null) {
            System.out.println("Got " + result.size() + " items.");
            JsonUtils.printJson(fapiClient.getLastResponse());
        } else {
            System.out.println("Failed to search " + entityName + ".");
            if (fapiClient.getLastError() != null) {
                System.out.println("Error: " + fapiClient.getLastError().getMessage());
            }
        }
        Menu.anyKeyToContinue(br);
    }

    private static HistoryParams inputHistoryParams() {
        HistoryParams params = new HistoryParams();

        String startTimeInput = Inputer.inputString(br, "Start time (unix seconds, optional):");
        if (startTimeInput != null && !startTimeInput.isBlank()) {
            try {
                params.startTime = Long.parseLong(startTimeInput.trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid start time.");
                Menu.anyKeyToContinue(br);
                return null;
            }
        }

        String endTimeInput = Inputer.inputString(br, "End time (unix seconds, optional):");
        if (endTimeInput != null && !endTimeInput.isBlank()) {
            try {
                params.endTime = Long.parseLong(endTimeInput.trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid end time.");
                Menu.anyKeyToContinue(br);
                return null;
            }
        }

        String countInput = Inputer.inputString(br, "Count (optional, default " + FchChainInfo.DEFAULT_COUNT + "):");
        if (countInput != null && !countInput.isBlank()) {
            try {
                params.count = Integer.parseInt(countInput.trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid count.");
                Menu.anyKeyToContinue(br);
                return null;
            }
        }

        return params;
    }

    private static <K, V> void printHistoryResult(String name, Map<K, V> result) {
        if (result != null) {
            System.out.println("Got " + result.size() + " items.");
            JsonUtils.printJson(result);
        } else {
            System.out.println("Failed to get " + name + ".");
            if (fapiClient.getLastError() != null) {
                System.out.println("Error: " + fapiClient.getLastError().getMessage());
            }
        }
        Menu.anyKeyToContinue(br);
    }

    private static class HistoryParams {
        Long startTime;
        Long endTime;
        Integer count;
    }

    public static void generalQuery() {
        if (fapiClient == null) {
            System.out.println("FAPI Client not configured.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        Fcdsl fcdsl = new Fcdsl();
        System.out.println("Input the index name. Enter to exit:");
        String input = Inputer.inputString(br);
        if ("".equals(input)) return;
        fcdsl.setIndex(input);
        
        fcdsl.promoteInput(br);
        
        if (fcdsl.isBadFcdsl()) {
            System.out.println("FCDSL wrong:");
            System.out.println(JsonUtils.toNiceJson(fcdsl));
            return;
        }
        
        System.out.println(JsonUtils.toNiceJson(fcdsl));
        Menu.anyKeyToContinue(br);
        
        System.out.println("Requesting...");
        FapiResponse response = fapiClient.general(fcdsl);
        JsonUtils.printJson(response);
        Menu.anyKeyToContinue(br);
    }
    
    public static void settings() {
        Menu menu = new Menu("Settings");
        menu.add("Configure Service", StartFapiClient::configureService);
        menu.add("Show Current Config", StartFapiClient::showConfig);
        menu.add("Edit Setting Map", StartFapiClient::editSettingMap);
        menu.add("FUDP Performance", StartFapiClient::showPerformanceStats);
        
        menu.showAndSelect(br);
    }

    // ==================== FUDP Performance Monitoring ====================
    
    private static void showPerformanceStats() {
        if (fudpNode == null || !fudpNode.isRunning()) {
            System.out.println("FUDP Node is not running.");
            Menu.anyKeyToContinue(br);
            return;
        }

        Menu statsMenu = new Menu("FUDP Performance Stats");
        statsMenu.add("Node Overview", StartFapiClient::showNodeOverview);
        statsMenu.add("Peer Details", StartFapiClient::showPeerDetails);
        statsMenu.add("Ping Test", StartFapiClient::pingTestWithStats);
        statsMenu.add("List Connected Peers", StartFapiClient::listConnectedPeers);

        statsMenu.showAndSelect(br);
    }

    private static void showNodeOverview() {
        if (fudpNode == null || !fudpNode.isRunning()) {
            System.out.println("FUDP Node is not running.");
            Menu.anyKeyToContinue(br);
            return;
        }

        NodeStats stats = fudpNode.getNodeStats();
        System.out.println("\n" + stats.toString());

        if (!stats.getPeerStatsList().isEmpty()) {
            System.out.println("\n--- Per-Peer Summary ---");
            for (NodeStats.PeerStats ps : stats.getPeerStatsList()) {
                System.out.println(ps.toString());
            }
        }
        Menu.anyKeyToContinue(br);
    }

    private static void showPeerDetails() {
        if (fudpNode == null || !fudpNode.isRunning()) {
            System.out.println("FUDP Node is not running.");
            Menu.anyKeyToContinue(br);
            return;
        }

        try {
            System.out.print("Peer FID or alias: ");
            String peer = br.readLine().trim();

            NodeStats.PeerStats stats = fudpNode.getPeerStats(peer);
            if (stats == null) {
                System.out.println("Peer not found or not connected: " + peer);
                Menu.anyKeyToContinue(br);
                return;
            }

            System.out.println("\n=== Peer Statistics: " + stats.getPeerId() + " ===");
            System.out.println("Connection State: " + stats.getState());
            System.out.println();
            System.out.println("--- Packet Stats ---");
            System.out.println("  Sent:     " + stats.getPacketsSent());
            System.out.println("  Received: " + stats.getPacketsReceived());
            System.out.println("  Bytes Out: " + formatBytes(stats.getBytesOut()));
            System.out.println("  Bytes In:  " + formatBytes(stats.getBytesIn()));
            System.out.println();
            System.out.println("--- Retransmit & Loss Stats ---");
            System.out.println("  Retransmits:      " + stats.getRetransmitCount() + " (" + stats.getRetransmitRatePercent() + " retransmit rate)");
            System.out.println("  Suspected Lost:   " + stats.getSuspectedLostCount() + " (triggered retransmit)");
            System.out.println("  Recovered (ACKed):" + stats.getAckedAfterSuspectedLost() + " (false positives)");
            System.out.println("  Effective Lost:   " + stats.getLostPacketCount() + " (" + stats.getLossRatePercent() + " loss rate)");
            System.out.println();
            System.out.println("--- RTT Stats ---");
            System.out.println("  Smoothed RTT: " + stats.getSmoothedRttMs() + " ms");
            System.out.println("  Min RTT:      " + stats.getMinRttMs() + " ms");
            System.out.println("  RTT Variance: " + stats.getRttVarianceMs() + " ms");
            System.out.println("  RTO:          " + stats.getRtoMs() + " ms");
            System.out.println();
            System.out.println("--- Congestion Control ---");
            System.out.println("  State:           " + stats.getCcState());
            System.out.println("  Congestion Wnd:  " + formatBytes(stats.getCongestionWindow()));
            System.out.println("  Bytes In Flight: " + formatBytes(stats.getBytesInFlight()));

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        Menu.anyKeyToContinue(br);
    }

    private static void pingTestWithStats() {
        if (fudpNode == null || !fudpNode.isRunning()) {
            System.out.println("FUDP Node is not running.");
            Menu.anyKeyToContinue(br);
            return;
        }

        try {
            // Use configured service peer ID if available
            String peer;
            if (fapiClient != null && fapiClient.getServicePeerId() != null) {
                peer = fapiClient.getServicePeerId();
                System.out.println("Using configured service peer ID: " + peer);
            } else {
                System.out.print("Peer FID or alias: ");
                peer = br.readLine().trim();
                if (peer.isEmpty()) {
                    System.out.println("No peer ID provided.");
                    Menu.anyKeyToContinue(br);
                    return;
                }
            }

            System.out.print("Number of pings (default 5): ");
            String countStr = br.readLine().trim();
            int count = countStr.isEmpty() ? 5 : Integer.parseInt(countStr);

            System.out.println("\nPinging " + peer + " with " + count + " packets...\n");

            long totalRtt = 0;
            long minRtt = Long.MAX_VALUE;
            long maxRtt = 0;
            int success = 0;
            int failed = 0;

            for (int i = 0; i < count; i++) {
                try {
                    long start = System.currentTimeMillis();
                    PongMessage pong = fudpNode.pingAwaitPong(peer, false, 5000).get(5, TimeUnit.SECONDS);
                    long rtt = System.currentTimeMillis() - start;

                    System.out.printf("  Reply from %s: time=%d ms%n", peer, rtt);

                    totalRtt += rtt;
                    if (rtt < minRtt) minRtt = rtt;
                    if (rtt > maxRtt) maxRtt = rtt;
                    success++;
                } catch (Exception e) {
                    System.out.println("  Request timed out.");
                    failed++;
                }

                // Wait 1 second between pings
                if (i < count - 1) {
                    Thread.sleep(1000);
                }
            }

            System.out.println();
            System.out.println("--- Ping Statistics for " + peer + " ---");
            System.out.println("  Packets: Sent=" + count + ", Received=" + success + ", Lost=" + failed
                    + " (" + (failed * 100 / count) + "% loss)");
            if (success > 0) {
                System.out.println("  RTT: min=" + minRtt + "ms, avg=" + (totalRtt / success) + "ms, max=" + maxRtt + "ms");
            }

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        Menu.anyKeyToContinue(br);
    }

    private static void listConnectedPeers() {
        if (fudpNode == null) {
            System.out.println("FUDP Node is not initialized.");
            Menu.anyKeyToContinue(br);
            return;
        }

        List<Peer> peers = fudpNode.listPeers();
        if (peers.isEmpty()) {
            System.out.println("No connected peers.");
        } else {
            System.out.println("\n=== Connected Peers (" + peers.size() + ") ===");
            for (int i = 0; i < peers.size(); i++) {
                Peer peer = peers.get(i);
                String alias = peer.getAlias() != null ? " (" + peer.getAlias() + ")" : "";
                String address = peer.hasAddress() ? peer.getHost() + ":" + peer.getPort() : "no address";
                System.out.println((i + 1) + ". " + peer.getId() + alias);
                System.out.println("   Address: " + address);
            }
        }
        Menu.anyKeyToContinue(br);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    // ==================== End FUDP Performance Monitoring ====================

    private static void editSettingMap() {
        if (settings == null) {
            System.out.println("Settings not loaded.");
            Menu.anyKeyToContinue(br);
            return;
        }
        setDefaultSettingMap(settings.getSettingMap());
        settings.checkSetting(br);
        Menu.anyKeyToContinue(br);
    }
    
    private static void configureService() {
        configureFapiService();
        Menu.anyKeyToContinue(br);
    }

    public static boolean bootstrapAndPersist(List<FapiClient.Endpoint> endpoints, String source) {
        if (endpoints == null) return false;
        for (FapiClient.Endpoint endpoint : endpoints) {
            if (configureViaEndpoint(endpoint.host(), endpoint.port(), source)) {
                return true;
            }
        }
        return false;
    }

    public static boolean configureViaEndpoint(String host, int port, String source) {
        if (fudpNode == null) {
            System.out.println("FUDP node not ready, skip configuration.");
            return false;
        }
        try {
            System.out.println("Discovering peer via HELLO+PING at " + host + ":" + port + " (" + source + ")...");
            FapiClient.DiscoveryResult discovery = FapiClient.discoverViaHelloAndPing(
                    fudpNode,
                    host,
                    port,
                    FapiClient.DEFAULT_HELLO_TIMEOUT_MS,
                    FapiClient.DEFAULT_PING_TIMEOUT_MS
            );
            if (discovery == null) {
                System.err.println("Discovery returned null for " + host + ":" + port);
                return false;
            }
            System.out.println("Discovered peer FID: " + discovery.getPeerId());

            List<Service> services = discovery.getServices();
            if (services == null || services.isEmpty()) {
                System.out.println("No FAPI services advertised in PONG. Ensure the server enables pong info.");
                System.out.println("Pong data: " + (discovery.getPong() != null && discovery.getPong().getData() != null ? 
                    new String(discovery.getPong().getData()) : "null"));
                return false;
            }
            System.out.println("Found " + services.size() + " FAPI service(s).");

            Service selected = "bootstrap".equalsIgnoreCase(source) ? services.get(0) : selectService(services);
            if (selected == null) {
                System.out.println("No service selected. FAPI Client not configured.");
                return false;
            }

            System.out.println("Using service SID: " + selected.getId());
            System.out.println("Service name: " + selected.getStdName());
            fapiClient = new FapiClient(fudpNode, discovery.getPeerId(), selected.getId(), 30, settings);
            persistFapiConfiguration(host, port, discovery, selected);
            System.out.println("FAPI Client configured successfully.");
            return true;
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String msg = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
            System.err.println("Failed to configure FAPI Client via HELLO/PING (" + host + ":" + port + "): " + msg);
            System.err.println("Exception type: " + cause.getClass().getName());
            cause.printStackTrace(System.err);
        } catch (Throwable e) {
            System.err.println("Unexpected error during FAPI configuration: " + e.getClass().getName());
            e.printStackTrace(System.err);
            throw new RuntimeException(e);
        }
        return false;
    }
    
    private static void persistFapiConfiguration(String host, int port, FapiClient.DiscoveryResult discovery, Service selected) {
        if (settings == null || settings.getConfig() == null) {
            return;
        }
        Configure config = settings.getConfig();
        if (config.getApiProviderMap() == null) {
            config.setApiProviderMap(new HashMap<>());
        }
        if (config.getApiAccountMap() == null) {
            config.setApiAccountMap(new HashMap<>());
        }
        ApiProvider provider = buildFapiProvider(host, port, selected);
        config.getApiProviderMap().put(provider.getId(), provider);

        ApiAccount account = buildFapiAccount(host, port, discovery, selected, provider);
        config.getApiAccountMap().entrySet().removeIf(entry ->
                entry.getValue() != null
                        && provider.getId().equals(entry.getValue().getProviderId())
                        && !entry.getKey().equals(account.getId()));
        config.getApiAccountMap().put(account.getId(), account);

        if (settings.getClientGroups() == null) {
            settings.setClientGroups(new HashMap<>());
        }
        ClientGroup group = settings.getClientGroups().get(Service.ServiceType.FAPI);
        if (group == null) {
            group = new ClientGroup(Service.ServiceType.FAPI);
        }
        if (!group.getAccountIds().contains(account.getId())) {
            group.getAccountIds().add(account.getId());
        }
        group.addApiAccount(account);
        group.addClient(account.getId(), fapiClient);
        settings.getClientGroups().put(Service.ServiceType.FAPI, group);

        Configure.saveConfig();
        if (settings.getMainFid() != null) {
            settings.saveClientSettings(settings.getMainFid(), clientName);
        }
    }

    private static ApiProvider buildFapiProvider(String host, int port, Service selected) {
        ApiProvider provider = new ApiProvider();
        provider.setId(selected.getId());
        provider.setName(selected.getStdName());
        provider.setType(Service.ServiceType.FAPI);
        provider.setApiUrl(host + ":" + port);
        provider.setService(selected);
        provider.setApiParams(Params.getParamsFromService(selected, Params.class));
        provider.setDealer(selected.getDealer());
        provider.setDealerPubkey(selected.getDealerPubkey());
        provider.setOwner(selected.getOwner());
        provider.setProtocols(selected.getProtocols());
        return provider;
    }

    private static ApiAccount buildFapiAccount(String host, int port, FapiClient.DiscoveryResult discovery, Service selected, ApiProvider provider) {
        ApiAccount account = new ApiAccount();
        account.setUserName(settings.getMainFid());
        String newId = account.makeApiAccountId(provider.getId(), account.getUserName());
        account.setId(newId);
        account.setProviderId(provider.getId());
        account.setApiUrl(provider.getApiUrl());
        account.setService(selected);
        account.setServiceParams(Params.getParamsFromService(selected, Params.class));
        account.setUserId(settings.getMainFid());
        account.setUserPubkey(settings.getMyPubkey());
        account.setUserPrikeyCipher(settings.getMyPrikeyCipher());
        account.setClient(fapiClient);
        return account;
    }

    private static String findExistingFapiAccountId(String providerId) {
        if (settings == null || settings.getConfig() == null || settings.getConfig().getApiAccountMap() == null) {
            return null;
        }
        for (Map.Entry<String, ApiAccount> entry : settings.getConfig().getApiAccountMap().entrySet()) {
            ApiAccount account = entry.getValue();
            if (account != null && providerId.equals(account.getProviderId())) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * 配置 FAPI 服务连接信息
     * 仅需提供 host 和 port，客户端会通过 HELLO+PING 自动发现公钥和可用服务
     */
    private static void configureFapiService() {
        String host = Inputer.inputString(br, "Input FAPI service host (IP or hostname, default: 127.0.0.1):");
        if (host.isEmpty()) {
            host = "127.0.0.1";
        }

        String portStr = Inputer.inputString(br, "Input FAPI service port (default: 8500):");
        int port;
        if (portStr.isEmpty()) {
            port = 8500;  // 默认端口，与 StartFapiManager 保持一致
        } else {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number. Using default 8500.");
                port = 8500;
            }
        }

        if (!configureViaEndpoint(host, port, "manual input")) {
            fapiClient = null;
        }
    }

    private static Service selectService(List<Service> services) {
        if (services == null || services.isEmpty()) {
            return null;
        }
        if (services.size() == 1) {
            Service service = services.get(0);
            System.out.println("Found FAPI service: " + service.getStdName() + " (SID=" + service.getId() + ")");
            return service;
        }
        System.out.println("Multiple FAPI services found on peer:");
        for (int i = 0; i < services.size(); i++) {
            Service svc = services.get(i);
            System.out.println((i + 1) + ") SID=" + svc.getId() + ", name=" + svc.getStdName() + ", ver=" + svc.getVer());
        }
        Long index = Inputer.inputLong(br, "Select a service (default 1)", 1L);
        if (index == null || index < 1 || index > services.size()) {
            System.out.println("Invalid selection. Defaulting to the first service.");
            return services.get(0);
        }
        return services.get(index.intValue() - 1);
    }
    
    private static void showConfig() {
        if (fapiClient != null) {
            System.out.println("Service Peer ID: " + fapiClient.getServicePeerId());
            System.out.println("Service SID: " + fapiClient.getServiceSid());
        } else {
            System.out.println("FAPI Client not configured.");
            if (settings != null) {
                ApiAccount stored = settings.getApiAccount(Service.ServiceType.FAPI);
                if (stored != null) {
                    System.out.println("Stored provider: " + stored.getProviderId() + " at " + stored.getApiUrl());
                }
            }
        }
        if (fudpNode != null) {
            System.out.println("Local FID: " + fudpNode.getLocalFid());
        }
        // 可以显示已添加的 peers（如果需要）
        Menu.anyKeyToContinue(br);
    }
    
    public static Fcdsl inputFcdsl(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.promoteSearch(defaultSize, defaultSort, br);
        
        if (fcdsl.isBadFcdsl()) {
            System.out.println("FCDSL wrong:");
            System.out.println(JsonUtils.toNiceJson(fcdsl));
            return null;
        }
        System.out.println("fcdsl:\n" + JsonUtils.toNiceJson(fcdsl));
        Menu.anyKeyToContinue(br);
        return fcdsl;
    }
}
