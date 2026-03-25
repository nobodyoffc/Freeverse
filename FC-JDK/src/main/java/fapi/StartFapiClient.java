package fapi;

import com.google.gson.Gson;
import config.Settings;
import data.apipData.Fcdsl;
import data.fchData.Cash;
import data.feipData.Service;
import fapi.client.FapiClient;
import fapi.client.FapiClient.DiscoveryResult;
import fapi.message.FapiResponse;
import core.crypto.Hash;
import data.fcData.DockItem;
import fudp.message.AppMessage;
import fudp.message.BytesMessage;
import fudp.node.FudpNode;
import fudp.node.NodeEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.Inputer;
import ui.Menu;
import ui.ProgressBar;
import utils.FchUtils;
import utils.JsonUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

/**
 * FAPI客户端启动类（重构版）
 * <p>
 * 基于新的 ServiceBootstrap 框架启动 FAPI 客户端。
 * <p>
 * 主要改进：
 * 1. 使用 ServiceBootstrap.bootstrapClient() 替代 Starter.startClient()
 * 2. 统一的配置和启动流程
 * 3. 自动服务发现（默认端点 → 链上服务列表 → 手动输入）
 * <p>
 * 注意：StartFapiClientOld.java 保留作为参考。
 */
public class StartFapiClient {
    private static final Logger log = LoggerFactory.getLogger(StartFapiClient.class);

    public static final String CLIENT_NAME = "FAPI Client";
    
    private static BufferedReader br;
    private static ServiceBootstrap.ClientBootstrapResult bootstrapResult;
    private static FudpNode fudpNode;
    private static Settings settings;
    private static FapiClient fapiClient;

    public static void main(String[] args) {
        Menu.welcome(CLIENT_NAME);
        br = new BufferedReader(new InputStreamReader(System.in));
        
        try {
            // 1. 使用新的统一启动框架初始化 FudpNode
            ServiceBootstrapStarter config = ServiceBootstrapStarter.forFapiClient()
                .setBr(br);
            
            bootstrapResult = ServiceBootstrap.bootstrapClient(config);
            if (bootstrapResult == null) {
                log.error("Failed to initialize FAPI client");
                return;
            }
            
            fudpNode = bootstrapResult.getFudpNode();
            settings = bootstrapResult.getSettings();
            
            // Set event listener so the client can receive incoming messages (e.g. relayed data)
            fudpNode.setEventListener(new ClientEventListener());
            
            // 2. 服务发现与连接（新的自动发现逻辑）
            fapiClient = bootstrapFapiClient();
            if (fapiClient == null) {
                System.out.println("Failed to connect to any FAPI service. Exiting.");
                cleanup();
                return;
            }
            
            System.out.println("Connected to FAPI service: " + fapiClient.getServicePeerId());
            
            // 3. 主菜单
            showMainMenu();
            
        } catch (Exception e) {
            log.error("Failed to start FAPI Client", e);
        } finally {
            cleanup();
        }
    }
    
    /**
     * 服务发现与连接
     * 优先级：1.默认服务 → 2.链上服务列表 → 3.手动输入
     */
    private static FapiClient bootstrapFapiClient() {
        System.out.println("Discovering FAPI services...");
        
        // 1. 尝试默认服务端点
        DiscoveryResult discovery = FapiServiceDiscovery.discoverViaDefaults(fudpNode);
        
        if (discovery != null && discovery.getServices() != null && !discovery.getServices().isEmpty()) {
            // 创建临时客户端
            FapiClient tempClient = new FapiClient(fudpNode, discovery.getPeerId(),
                    discovery.getServices().get(0).getId(), 
                    FapiDefaults.DEFAULT_REQUEST_TIMEOUT_SEC, settings);
            
            // 2. 查询链上FAPI服务商列表
            List<Service> providers = FapiServiceDiscovery.fetchFapiProviders(tempClient);
            
            if (!providers.isEmpty()) {
                // 显示服务列表供用户选择
                Service selected = selectService(providers);
                if (selected != null) {
                    FapiClient client = connectToService(selected);
                    if (client != null) {
                        return client;
                    }
                }
            }
            
            // 没有链上服务或用户选择当前连接
            System.out.println("Using current connection: " + discovery.getPeerId());
            return tempClient;
        }
        
        // 3. 默认服务全部失败，提示手动输入
        System.out.println("\nCould not connect to default services.");
        System.out.println("Default endpoints tried:");
        for (String ep : FapiDefaults.DEFAULT_ENDPOINTS) {
            System.out.println("  - " + ep);
        }
        
        return manualConnect();
    }
    
    /**
     * 显示服务列表供用户选择
     */
    private static Service selectService(List<Service> services) {
        System.out.println("\n=== Available FAPI Services ===");
        for (int i = 0; i < services.size(); i++) {
            Service s = services.get(i);
            String url = s.getApiUrl() != null ? s.getApiUrl() : "N/A";
            String name = s.getStdName() != null ? s.getStdName() : "Unknown";
            String sidBrief = s.getId() != null && s.getId().length() >= 8 
                    ? s.getId().substring(0, 8) + "..." : s.getId();
            System.out.printf("%2d) %-20s  SID: %s  URL: %s\n", i + 1, name, sidBrief, url);
        }
        System.out.println(" 0) Use current connection");
        System.out.println("-1) Enter address manually");
        
        Long choice = Inputer.inputLong(br, "Select service (default 0)", 0L);
        
        if (choice == null || choice == 0) {
            return null; // 使用当前连接
        } else if (choice > 0 && choice <= services.size()) {
            return services.get(choice.intValue() - 1);
        }
        return null; // 其他情况返回null
    }
    
    /**
     * 连接到指定服务
     */
    private static FapiClient connectToService(Service service) {
        if (service == null || service.getApiUrl() == null) {
            return null;
        }
        
        String host = FapiDefaults.getHost(service.getApiUrl());
        int port = FapiDefaults.getPort(service.getApiUrl());
        
        System.out.println("Connecting to " + host + ":" + port + "...");
        
        DiscoveryResult result = FapiServiceDiscovery.discoverViaEndpoint(fudpNode, host, port);
        if (result != null && result.getServices() != null && !result.getServices().isEmpty()) {
            System.out.println("Connected successfully!");
            return new FapiClient(fudpNode, result.getPeerId(), service.getId(),
                    FapiDefaults.DEFAULT_REQUEST_TIMEOUT_SEC, settings);
        }
        
        System.out.println("Failed to connect to " + service.getStdName());
        return null;
    }
    
    /**
     * 手动输入服务地址
     */
    private static FapiClient manualConnect() {
        while (true) {
            String input = Inputer.inputString(br,
                    "Enter FAPI service address (host:port, or 'q' to quit):");
            
            if (input == null || "q".equalsIgnoreCase(input.trim())) {
                return null;
            }
            
            String host = FapiDefaults.getHost(input);
            int port = FapiDefaults.getPort(input);
            
            System.out.println("Connecting to " + host + ":" + port + "...");
            
            DiscoveryResult result = FapiServiceDiscovery.discoverViaEndpoint(fudpNode, host, port);
            if (result != null && result.getServices() != null && !result.getServices().isEmpty()) {
                System.out.println("Connected successfully!");
                return new FapiClient(fudpNode, result.getPeerId(),
                        result.getServices().get(0).getId(),
                        FapiDefaults.DEFAULT_REQUEST_TIMEOUT_SEC, settings);
            }
            
            System.out.println("Failed to connect. Please try again.");
        }
    }
    
    /**
     * 显示主菜单
     */
    private static void showMainMenu() {
        Menu menu = new Menu(CLIENT_NAME, StartFapiClient::cleanup);
        menu.add("Basic APIs", StartFapiClient::basicApis);
        menu.add("Entity Search", StartFapiClient::search);
        menu.add("Entity By IDs", StartFapiClient::entityByIds);
        menu.add("Wallet APIs", StartFapiClient::walletApis);
        menu.add("Disk APIs", StartFapiClient::diskApis);
        menu.add("DOCK APIs", StartFapiClient::dockApis);
        menu.add("MAP APIs", StartFapiClient::mapApis);
        menu.add("ROAD APIs", StartFapiClient::roadApis);
        menu.add("Switch Service", StartFapiClient::switchService);
        menu.add("FUDP Node Info", StartFapiClient::showFudpNodeInfo);
        menu.add("Settings", StartFapiClient::showSettings);
        
        menu.showAndSelect(br);
    }
    
    // ==================== API 菜单 ====================
    
    private static void basicApis() {
        Menu menu = new Menu("Basic APIs");
        menu.add("bestHeight", StartFapiClient::bestHeight);
        menu.add("bestBlock", StartFapiClient::bestBlock);
        menu.add("chainInfo", StartFapiClient::chainInfo);
        menu.add("totals", StartFapiClient::totals);
        menu.showAndSelect(br);
    }
    
    private static void walletApis() {
        Menu menu = new Menu("Wallet APIs");
        menu.add("balanceByIds", StartFapiClient::balanceByIds);
        menu.add("broadcastTx", StartFapiClient::broadcastTx);
        menu.add("estimateFee", StartFapiClient::estimateFee);
        menu.add("cashValid", StartFapiClient::cashValid);
        menu.showAndSelect(br);
    }
    
    private static void diskApis() {
        Menu menu = new Menu("Disk APIs");
        menu.add("put (temporary storage)", StartFapiClient::diskPut);
        menu.add("carve (permanent storage)", StartFapiClient::diskCarve);
        menu.add("get (download by DID)", StartFapiClient::diskGet);
        menu.add("check (file info)", StartFapiClient::diskCheck);
        menu.add("list (query files)", StartFapiClient::diskList);
        menu.showAndSelect(br);
    }
    
    private static void dockApis() {
        Menu menu = new Menu("DOCK APIs (Store-and-Forward Messaging)");
        menu.add("put (store for recipients)", StartFapiClient::dockPut);
        menu.add("get (retrieve by dockId)", StartFapiClient::dockGet);
        menu.add("list (list items)", StartFapiClient::dockList);
        menu.add("check (item status)", StartFapiClient::dockCheck);
        menu.add("delete (remove item)", StartFapiClient::dockDelete);
        menu.add("extend (extend TTL)", StartFapiClient::dockExtend);
        menu.showAndSelect(br);
    }
    
    private static void mapApis() {
        Menu menu = new Menu("MAP APIs (FID-to-Address Mapping)");
        menu.add("register (register self)", StartFapiClient::mapRegister);
        menu.add("find (find FID)", StartFapiClient::mapFind);
        menu.add("unregister (unregister self)", StartFapiClient::mapUnregister);
        menu.add("list (all registered)", StartFapiClient::mapList);
        menu.add("stats (service statistics)", StartFapiClient::mapStats);
        menu.showAndSelect(br);
    }
    
    // ==================== API 实现 ====================
    
    private static void bestHeight() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Requesting bestHeight...");
        Long height = fapiClient.bestHeight();
        if (height != null) {
            System.out.println("Best height: " + height);
            System.out.println(fapiClient.getLastResponse().toNiceJson());
            if (fapiClient.getLastBalance() != null) {
                System.out.println("Balance: " + FchUtils.satoshiToCoin(fapiClient.getLastBalance()) + " FCH");
            }
        } else {
            System.out.println("Failed to get best height.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void bestBlock() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Requesting bestBlock...");
        Object block = fapiClient.bestBlock();
        if (block != null) {
            JsonUtils.printJson(block);
        } else {
            System.out.println("Failed to get best block.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void chainInfo() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Requesting chainInfo...");
        Object info = fapiClient.chainInfo(null);
        if (info != null) {
            JsonUtils.printJson(info);
        } else {
            System.out.println("Failed to get chain info.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void totals() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Requesting totals...");
        Map<String, String> result = fapiClient.totals();
        if (result != null) {
            System.out.println("Entity counts:");
            JsonUtils.printJson(result);
        } else {
            System.out.println("Failed to get totals.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void balanceByIds() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
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
            System.out.println("Balances:");
            for (Map.Entry<String, Long> entry : result.entrySet()) {
                System.out.println("  " + entry.getKey() + ": " + FchUtils.satoshiToCoin(entry.getValue()) + " FCH");
            }
        } else {
            System.out.println("Failed to get balances.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void broadcastTx() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        String rawTx = Inputer.inputString(br, "Enter raw transaction (hex):");
        if (rawTx == null || rawTx.isEmpty()) {
            System.out.println("Transaction is required.");
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
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void estimateFee() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Estimating fee...");
        Double feeRate = fapiClient.estimateFee(null);
        if (feeRate != null) {
            System.out.println("Estimated fee rate: " + feeRate + " FCH/KB");
        } else {
            System.out.println("Failed to estimate fee.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void cashValid() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        String fid = Inputer.inputString(br, "Enter FID:");
        if (fid == null || fid.isEmpty()) {
            System.out.println("FID is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Requesting valid cashes...");
        List<Cash> result = fapiClient.cashValid(fid, null, null, null, 1, 0);
        if (result != null) {
            System.out.println("Found " + result.size() + " valid cashes:");
            JsonUtils.printJson(result);
        } else {
            System.out.println("Failed to get valid cashes.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    // ==================== Disk API 实现 ====================
    
    private static void diskPut() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        String filePath = Inputer.inputString(br, "Enter file path to upload:");
        if (filePath == null || filePath.isEmpty()) {
            System.out.println("File path is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(filePath);
            if (!java.nio.file.Files.exists(path)) {
                System.out.println("File not found: " + filePath);
                Menu.anyKeyToContinue(br);
                return;
            }
            
            long fileSize = java.nio.file.Files.size(path);
            System.out.println("Uploading " + ProgressBar.formatBytes(fileSize) + " (temporary storage)...");
            
            ProgressBar progressBar = new ProgressBar("Uploading", fileSize);
            data.fcData.DiskItem result = fapiClient.diskPut(path.toFile(), null, progressBar::update);
            
            if (result != null) {
                progressBar.finish();
                System.out.println("Upload successful!");
                System.out.println("DID: " + result.getId());
                System.out.println("Size: " + result.getSize() + " bytes");
                System.out.println("Since: " + result.getSince());
                System.out.println("Expires: " + result.getExpire());
            } else {
                progressBar.fail();
                System.out.println("Upload failed.");
                printLastError();
            }
        } catch (java.io.IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void diskCarve() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        String filePath = Inputer.inputString(br, "Enter file path to upload (permanent):");
        if (filePath == null || filePath.isEmpty()) {
            System.out.println("File path is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(filePath);
            if (!java.nio.file.Files.exists(path)) {
                System.out.println("File not found: " + filePath);
                Menu.anyKeyToContinue(br);
                return;
            }
            
            long fileSize = java.nio.file.Files.size(path);
            System.out.println("Uploading " + ProgressBar.formatBytes(fileSize) + " (permanent storage)...");
            
            ProgressBar progressBar = new ProgressBar("Carving", fileSize);
            data.fcData.DiskItem result = fapiClient.diskCarve(path.toFile(), progressBar::update);
            
            if (result != null) {
                progressBar.finish();
                System.out.println("Upload successful (permanent)!");
                System.out.println("DID: " + result.getId());
                System.out.println("Size: " + result.getSize() + " bytes");
                System.out.println("Since: " + result.getSince());
                System.out.println("Expires: " + (result.getExpire() == null ? "Never (permanent)" : result.getExpire()));
            } else {
                progressBar.fail();
                System.out.println("Upload failed.");
                printLastError();
            }
        } catch (java.io.IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void diskGet() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        String did = Inputer.inputString(br, "Enter DID (64 hex chars):");
        if (did == null || did.isEmpty()) {
            System.out.println("DID is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        String savePath = Inputer.inputString(br, "Enter save path:");
        if (savePath == null || savePath.isEmpty()) {
            System.out.println("Save path is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        // First check the file to get the expected size for the progress bar
        System.out.println("Checking file metadata...");
        data.fcData.DiskItem checkResult = fapiClient.diskCheck(did);
        long expectedSize = (checkResult != null && checkResult.getSize() > 0) 
                ? checkResult.getSize() : -1;
        
        if (expectedSize > 0) {
            System.out.println("File size: " + ProgressBar.formatBytes(expectedSize));
        }
        System.out.println("Downloading file...");
        
        ProgressBar progressBar = new ProgressBar("Downloading", expectedSize);
        java.io.File outputFile = new java.io.File(savePath);
        data.fcData.DiskItem metadata = fapiClient.diskGet(did, outputFile, progressBar::update);
        
        if (metadata != null && outputFile.exists()) {
            progressBar.finish();
            System.out.println("Download successful!");
            System.out.println("DID: " + metadata.getId());
            System.out.println("Size: " + ProgressBar.formatBytes(outputFile.length()));
            System.out.println("Since: " + metadata.getSince());
            System.out.println("Expires: " + metadata.getExpire());
            System.out.println("File saved to: " + savePath);
        } else {
            progressBar.fail();
            System.out.println("Download failed or file not found.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void diskCheck() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        String did = Inputer.inputString(br, "Enter DID (64 hex chars):");
        if (did == null || did.isEmpty()) {
            System.out.println("DID is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Checking file...");
        data.fcData.DiskItem result = fapiClient.diskCheck(did);
        if (result != null) {
            System.out.println("File found!");
            System.out.println("DID: " + result.getId());
            System.out.println("Size: " + result.getSize() + " bytes");
            System.out.println("Since: " + result.getSince());
            System.out.println("Expires: " + (result.getExpire() == null ? "Never (permanent)" : result.getExpire()));
        } else {
            System.out.println("File not found or check failed.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void diskList() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Query stored files (Enter for default query)...");
        
        Fcdsl fcdsl = new Fcdsl();
        Long sizeLimit = Inputer.inputLong(br, "Max results (default 20)", 20L);
        if (sizeLimit != null && sizeLimit > 0) {
            fcdsl.addSize(sizeLimit.intValue());
        }
        
        System.out.println("Listing files...");
        java.util.List<data.fcData.DiskItem> result = fapiClient.diskList(fcdsl);
        
        if (result != null) {
            System.out.println("Found " + result.size() + " files:");
            for (data.fcData.DiskItem item : result) {
                String expire = item.getExpire() == null ? "permanent" : String.valueOf(item.getExpire());
                System.out.printf("  DID: %s  Size: %d  Expire: %s%n", 
                        item.getId(), item.getSize(), expire);
            }
        } else {
            System.out.println("Query failed or returned null.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    // ==================== DOCK API 实现 ====================
    
    private static void dockPut() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        // Get input data (file or text)
        System.out.println("Enter data to store:");
        System.out.println("  1) Text input");
        System.out.println("  2) File input");
        Long choice = Inputer.inputLong(br, "Choose input type", 1L);
        
        byte[] data;
        if (choice != null && choice == 2) {
            String filePath = Inputer.inputString(br, "Enter file path:");
            if (filePath == null || filePath.isEmpty()) {
                System.out.println("File path is required.");
                Menu.anyKeyToContinue(br);
                return;
            }
            try {
                java.nio.file.Path path = java.nio.file.Paths.get(filePath);
                if (!java.nio.file.Files.exists(path)) {
                    System.out.println("File not found: " + filePath);
                    Menu.anyKeyToContinue(br);
                    return;
                }
                data = java.nio.file.Files.readAllBytes(path);
            } catch (java.io.IOException e) {
                System.out.println("Error reading file: " + e.getMessage());
                Menu.anyKeyToContinue(br);
                return;
            }
        } else {
            String text = Inputer.inputString(br, "Enter text to store:");
            if (text == null || text.isEmpty()) {
                System.out.println("Data is required.");
                Menu.anyKeyToContinue(br);
                return;
            }
            data = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        
        // Get recipients
        String[] recipientsArray = Inputer.inputStringArrayWithSeparator(br, "Recipients FIDs (comma-separated)", ",");
        if (recipientsArray.length == 0) {
            System.out.println("At least one recipient is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        java.util.List<String> recipients = java.util.Arrays.asList(recipientsArray);
        
        // Get maxDays
        Long maxDays = Inputer.inputLong(br, "Max days to store (default = server default, 0 = default)", 0L);
        Integer maxDaysInt = (maxDays != null && maxDays > 0) ? maxDays.intValue() : null;
        
        // Get target DOCK URL for forwarding (optional)
        String targetDockUrl = Inputer.inputString(br, "Target DOCK URL for forwarding (Enter to skip, e.g., host:port):");
        if (targetDockUrl != null && targetDockUrl.isEmpty()) {
            targetDockUrl = null;
        }
        
        // Confirm forwarding
        if (targetDockUrl != null) {
            System.out.println("\n*** FORWARDING MODE ***");
            System.out.println("Data will be forwarded to: " + targetDockUrl);
            System.out.println("You will be charged: local forwarding fee + remote DOCK storage fee");
            if (!Inputer.askIfYes(br, "Continue with forwarding?")) {
                System.out.println("Cancelled.");
                Menu.anyKeyToContinue(br);
                return;
            }
        }
        
        System.out.println("Storing " + data.length + " bytes for " + recipients.size() + " recipient(s)...");
        if (targetDockUrl != null) {
            System.out.println("Forwarding to: " + targetDockUrl);
        }
        
        DockItem result = fapiClient.dockPut(data, recipients, maxDaysInt, targetDockUrl);
        if (result != null) {
            System.out.println("\nStore successful!");
            System.out.println("Dock ID: " + result.getId());
            System.out.println("Size: " + result.getSize() + " bytes");
            System.out.println("Max Days: " + result.getMaxDays());
            System.out.println("Expire Height: " + result.getExpireHeight());
            if (result.getStorageFee() != null) {
                System.out.println("Storage Fee: " + result.getStorageFee() + " satoshi");
            }
            if (result.getIngressFee() != null) {
                System.out.println("Ingress Fee: " + result.getIngressFee() + " satoshi");
            }
            // Check if response data has forwarding info (via lastResponse)
            if (fapiClient.getLastResponse() != null && fapiClient.getLastResponse().getData() != null) {
                try {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> respData = utils.ObjectUtils.objectToMap(
                            fapiClient.getLastResponse().getData(), String.class, Object.class);
                    if (respData != null) {
                        if (Boolean.TRUE.equals(respData.get("forwarded"))) {
                            System.out.println("\n--- Forwarding Details ---");
                            System.out.println("Target DOCK: " + respData.get("targetDockUrl"));
                            System.out.println("Local Fee: " + respData.get("localFee") + " satoshi");
                            System.out.println("Remote Fee: " + respData.get("remoteFee") + " satoshi");
                            System.out.println("Total Fee: " + respData.get("totalFee") + " satoshi");
                        }
                    }
                } catch (Exception e) {
                    // Ignore parsing errors
                }
            }
        } else {
            System.out.println("Store failed.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void dockGet() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        String dockId = Inputer.inputString(br, "Enter Dock ID:");
        if (dockId == null || dockId.isEmpty()) {
            System.out.println("Dock ID is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        String savePath = Inputer.inputString(br, "Enter save path (or Enter to display as text):");
        
        System.out.println("Retrieving data...");
        
        fapi.client.FapiClient.DockGetResult result = fapiClient.dockGetWithMetadata(dockId);
        if (result != null && result.content() != null) {
            System.out.println("\nRetrieve successful!");
            System.out.println("Size: " + result.content().length + " bytes");
            if (result.metadata() != null) {
                System.out.println("Sender: " + result.metadata().getSender());
            }
            
            if (savePath != null && !savePath.isEmpty()) {
                try {
                    java.nio.file.Files.write(java.nio.file.Paths.get(savePath), result.content());
                    System.out.println("File saved to: " + savePath);
                } catch (java.io.IOException e) {
                    System.out.println("Error saving file: " + e.getMessage());
                }
            } else {
                // Display as text if small enough
                if (result.content().length <= 1000) {
                    System.out.println("\nContent:");
                    System.out.println(new String(result.content(), java.nio.charset.StandardCharsets.UTF_8));
                } else {
                    System.out.println("\n(Content too large to display, use save path to save to file)");
                }
            }
        } else {
            System.out.println("Retrieve failed or item not found.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void dockList() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Listing DOCK items...");
        
        Fcdsl fcdsl = new Fcdsl();
        Long sizeLimit = Inputer.inputLong(br, "Max results (default 20)", 20L);
        if (sizeLimit != null && sizeLimit > 0) {
            fcdsl.addSize(sizeLimit.intValue());
        }
        
        java.util.List<DockItem> result = fapiClient.dockList(fcdsl);
        if (result != null) {
            System.out.println("Found " + result.size() + " items:");
            for (DockItem item : result) {
                System.out.printf("  ID: %s  Sender: %s  Size: %d  ExpireHeight: %s%n", 
                        item.getId(), item.getSender(), item.getSize(), item.getExpireHeight());
            }
        } else {
            System.out.println("Query failed.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void dockCheck() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        String dockId = Inputer.inputString(br, "Enter Dock ID:");
        if (dockId == null || dockId.isEmpty()) {
            System.out.println("Dock ID is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Checking item status...");
        DockItem result = fapiClient.dockCheck(dockId);
        if (result != null) {
            System.out.println("\nItem found!");
            System.out.println("ID: " + result.getId());
            System.out.println("Sender: " + result.getSender());
            System.out.println("Size: " + result.getSize() + " bytes");
            System.out.println("Max Days: " + result.getMaxDays());
            System.out.println("Create Height: " + result.getCreateHeight());
            System.out.println("Expire Height: " + result.getExpireHeight());
            System.out.println("Recipients: " + result.getRecipients());
        } else {
            System.out.println("Item not found or check failed.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void dockDelete() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        String dockId = Inputer.inputString(br, "Enter Dock ID to delete:");
        if (dockId == null || dockId.isEmpty()) {
            System.out.println("Dock ID is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        if (!Inputer.askIfYes(br, "Are you sure you want to delete this item?")) {
            System.out.println("Cancelled.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Deleting item...");
        java.util.Map<String, Object> result = fapiClient.dockDelete(dockId);
        if (result != null) {
            System.out.println("Delete successful!");
            System.out.println("ID: " + result.get("id"));
            System.out.println("Deleted: " + result.get("deleted"));
            System.out.println("Refund: " + result.get("refund") + " satoshi");
        } else {
            System.out.println("Delete failed.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void dockExtend() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        String dockId = Inputer.inputString(br, "Enter Dock ID to extend:");
        if (dockId == null || dockId.isEmpty()) {
            System.out.println("Dock ID is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        Long extraDays = Inputer.inputLong(br, "Extra days to add:", 7L);
        if (extraDays == null || extraDays <= 0) {
            System.out.println("Extra days must be positive.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Extending TTL...");
        java.util.Map<String, Object> result = fapiClient.dockExtend(dockId, extraDays.intValue());
        if (result != null) {
            System.out.println("Extend successful!");
            System.out.println("ID: " + result.get("id"));
            System.out.println("Extra Days: " + result.get("extraDays"));
            System.out.println("New Expire Height: " + result.get("newExpireHeight"));
            System.out.println("Additional Fee: " + result.get("additionalFee") + " satoshi");
        } else {
            System.out.println("Extend failed.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    // ==================== MAP API 实现 ====================
    
    private static void mapRegister() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Registering on MAP service...");
        fapi.components.map.MapEntry entry = fapiClient.mapRegister();
        if (entry != null) {
            System.out.println("Registration successful!");
            System.out.println("FID: " + entry.getFid());
            System.out.println("Observed IP: " + entry.getObservedIp());
            System.out.println("Observed Port: " + entry.getObservedPort());
            System.out.println("Last Seen: " + new java.util.Date(entry.getLastSeen()));
            System.out.println("Registered At: " + new java.util.Date(entry.getRegisteredAt()));
            System.out.println("\nNote: Call register every ~25 seconds to maintain NAT mapping.");
        } else {
            System.out.println("Registration failed.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void mapFind() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        String fid = Inputer.inputString(br, "Enter FID to find:");
        if (fid == null || fid.isEmpty()) {
            System.out.println("FID is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Finding " + fid + "...");
        fapi.components.map.MapEntry entry = fapiClient.mapFind(fid);
        if (entry != null) {
            System.out.println("Found!");
            System.out.println("FID: " + entry.getFid());
            System.out.println("Public Key: " + entry.getPubkey());
            System.out.println("Observed IP: " + entry.getObservedIp());
            System.out.println("Observed Port: " + entry.getObservedPort());
            System.out.println("Last Seen: " + new java.util.Date(entry.getLastSeen()));
            if (entry.getStale() != null && entry.getStale()) {
                System.out.println("Status: STALE (may be unreachable)");
            } else {
                System.out.println("Status: Fresh");
            }
        } else {
            System.out.println("FID not found or query failed.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void mapUnregister() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Unregistering from MAP service...");
        boolean success = fapiClient.mapUnregister();
        if (success) {
            System.out.println("Unregistration successful!");
        } else {
            System.out.println("Unregistration failed (may not be registered).");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void mapList() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Listing all registered FIDs...");
        java.util.List<fapi.components.map.MapEntry> entries = fapiClient.mapList();
        if (entries != null) {
            System.out.println("Found " + entries.size() + " registered FIDs:");
            System.out.println();
            System.out.printf("%-35s %-15s %-6s %s%n", "FID", "IP", "Port", "Last Seen");
            System.out.println("-".repeat(80));
            for (fapi.components.map.MapEntry entry : entries) {
                String fidShort = entry.getFid().length() > 34 
                        ? entry.getFid().substring(0, 34) + "..." 
                        : entry.getFid();
                String lastSeen = new java.text.SimpleDateFormat("MM-dd HH:mm:ss")
                        .format(new java.util.Date(entry.getLastSeen()));
                System.out.printf("%-35s %-15s %-6d %s%n", 
                        fidShort, entry.getObservedIp(), entry.getObservedPort(), lastSeen);
            }
        } else {
            System.out.println("Query failed.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void mapStats() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Getting MAP service statistics...");
        java.util.Map<String, Object> stats = fapiClient.mapStats();
        if (stats != null) {
            System.out.println("=== MAP Service Statistics ===");
            System.out.println("Total Entries: " + stats.get("totalEntries"));
            System.out.println("Fresh Entries: " + stats.get("freshEntries"));
            System.out.println("Stale Entries: " + stats.get("staleEntries"));
            System.out.println("Fresh Threshold: " + stats.get("freshThresholdMs") + " ms");
            System.out.println("Cleanup Threshold: " + stats.get("cleanupThresholdMs") + " ms");
        } else {
            System.out.println("Query failed.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    // ==================== ROAD API 菜单 ====================
    
    private static void roadApis() {
        Menu menu = new Menu("ROAD APIs (Data Relay)");
        menu.add("relay (send data to FID)", StartFapiClient::roadRelay);
        menu.add("stats (service statistics)", StartFapiClient::roadStats);
        menu.showAndSelect(br);
    }
    
    // ==================== ROAD API 实现 ====================
    
    private static void roadRelay() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        // Support multiple target FIDs
        System.out.println("Enter target FIDs (comma-separated for multiple):");
        String targetFidsInput = Inputer.inputString(br, "Target FID(s):");
        if (targetFidsInput == null || targetFidsInput.isEmpty()) {
            System.out.println("Target FID is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        // Parse comma-separated FIDs
        java.util.List<String> targetFids = java.util.Arrays.stream(targetFidsInput.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(java.util.stream.Collectors.toList());
        
        if (targetFids.isEmpty()) {
            System.out.println("At least one target FID is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Enter data to relay:");
        System.out.println("  1) Text input");
        System.out.println("  2) File input");
        Long choice = Inputer.inputLong(br, "Choose input type", 1L);
        
        byte[] data;
        if (choice != null && choice == 2) {
            // File input
            String filePath = Inputer.inputString(br, "Enter file path:");
            if (filePath == null || filePath.isEmpty()) {
                System.out.println("File path is required.");
                Menu.anyKeyToContinue(br);
                return;
            }
            try {
                java.nio.file.Path path = java.nio.file.Paths.get(filePath);
                if (!java.nio.file.Files.exists(path)) {
                    System.out.println("File not found: " + filePath);
                    Menu.anyKeyToContinue(br);
                    return;
                }
                data = java.nio.file.Files.readAllBytes(path);
            } catch (java.io.IOException e) {
                System.out.println("Error reading file: " + e.getMessage());
                Menu.anyKeyToContinue(br);
                return;
            }
        } else {
            // Text input
            String text = Inputer.inputString(br, "Enter text to relay:");
            if (text == null || text.isEmpty()) {
                System.out.println("Data is required.");
                Menu.anyKeyToContinue(br);
                return;
            }
            data = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        
        Long maxCost = Inputer.inputLong(br, "Max cost in satoshi (0 = no limit)", 0L);
        if (maxCost == null) maxCost = 0L;

        String targetRoad = Inputer.inputString(br, "Target ROAD URL (from freer.home.ROAD, empty for local only):");
        if (targetRoad != null && targetRoad.isEmpty()) targetRoad = null;
        
        System.out.println("Relaying " + data.length + " bytes to " + targetFids.size() + " target(s)...");
        
        FapiClient.RoadRelayResult result = fapiClient.roadRelay(targetFids, data, maxCost, targetRoad);
        if (result != null) {
            System.out.println("\n=== Relay Result ===");
            System.out.println("Overall success: " + result.success());
            System.out.println("Code: " + result.code());
            System.out.println("Message: " + result.message());
            System.out.println("Success count: " + result.successCount() + "/" + result.totalTargets());
            System.out.println("Failed count: " + result.failCount());
            System.out.println("Total charged (in): " + result.chargedIn() + " satoshi");
            System.out.println("Total charged (out): " + result.chargedOut() + " satoshi");
            System.out.println("Total charged: " + result.totalCharged() + " satoshi");
            
            // Show per-target results
            if (result.relayResults() != null && !result.relayResults().isEmpty()) {
                System.out.println("\n--- Per-target Results ---");
                for (java.util.Map.Entry<String, FapiClient.TargetRelayResult> entry : result.relayResults().entrySet()) {
                    FapiClient.TargetRelayResult targetResult = entry.getValue();
                    String status = targetResult.success() ? "OK" : "FAILED";
                    System.out.println("  " + entry.getKey() + ": " + status + " - " + targetResult.message());
                    if (targetResult.chainRelayed()) {
                        System.out.println("    (chain relayed via: " + targetResult.relayedVia() + ")");
                    }
                    System.out.println("    Charged: in=" + targetResult.chargedIn() + ", out=" + targetResult.chargedOut());
                }
            }
        } else {
            System.out.println("Relay failed.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void roadStats() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Getting ROAD service statistics...");
        java.util.Map<String, Object> stats = fapiClient.roadStats();
        if (stats != null) {
            System.out.println("=== ROAD Service Statistics ===");
            System.out.println("Total Relays: " + stats.get("totalRelays"));
            System.out.println("Successful Relays: " + stats.get("successfulRelays"));
            System.out.println("Failed Relays: " + stats.get("failedRelays"));
            System.out.println("Chain Relays: " + stats.get("chainRelays"));
            System.out.println("Bytes In: " + stats.get("bytesIn"));
            System.out.println("Bytes Out: " + stats.get("bytesOut"));
            System.out.println("Total Charged In: " + stats.get("totalChargedIn") + " satoshi");
            System.out.println("Total Charged Out: " + stats.get("totalChargedOut") + " satoshi");
            System.out.println("Price per KB In: " + stats.get("pricePerKBIn") + " satoshi");
            System.out.println("Price per KB Out: " + stats.get("pricePerKBOut") + " satoshi");
            
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> errors = (java.util.Map<String, Object>) stats.get("errorCounts");
            if (errors != null && !errors.isEmpty()) {
                System.out.println("\nError Counts:");
                for (java.util.Map.Entry<String, Object> e : errors.entrySet()) {
                    System.out.println("  " + e.getKey() + ": " + e.getValue());
                }
            }
        } else {
            System.out.println("Query failed.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }
    
    private static void entityByIds() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        String entityName = Inputer.inputString(br, "Entity name (e.g. block, tx, service):");
        if (entityName == null || entityName.isBlank()) {
            System.out.println("Entity name is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "IDs (comma separated)", ",");
        if (ids.length == 0) {
            System.out.println("At least one ID is required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Requesting entityByIds for " + entityName + "...");
        Map<String, Object> result = fapiClient.entityByIds(entityName, Object.class, ids);
        if (result != null) {
            System.out.println("Found " + result.size() + " items:");
            JsonUtils.printJson(result);
        } else {
            System.out.println("Failed to get entities.");
            printLastError();
        }
        Menu.anyKeyToContinue(br);
    }

    private static void search() {
        if (fapiClient == null) {
            System.out.println("Not connected to FAPI service.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Enter entity name (Enter to exit):");
        String entityName = Inputer.inputString(br);
        if (entityName == null || entityName.isEmpty()) {
            return;
        }
        
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setEntity(entityName);
        fcdsl.promoteInput(br);
        
        if (fcdsl.isBadFcdsl()) {
            System.out.println("Invalid FCDSL:");
            JsonUtils.printJson(fcdsl);
            return;
        }
        
        System.out.println("FCDSL: " + JsonUtils.toNiceJson(fcdsl));
        Menu.anyKeyToContinue(br);
        
        System.out.println("Executing query...");
        FapiResponse response = fapiClient.query("base.search", fcdsl);
        JsonUtils.printJson(response);
        Menu.anyKeyToContinue(br);
    }
    
    /**
     * 切换FAPI服务
     */
    private static void switchService() {
        System.out.println("Discovering available FAPI services...");
        List<Service> providers = FapiServiceDiscovery.fetchFapiProviders(fapiClient);
        
        if (providers.isEmpty()) {
            System.out.println("No FAPI services found on chain.");
            
            // 允许手动输入
            String input = Inputer.inputString(br, "Enter service address manually (or Enter to cancel):");
            if (input != null && !input.isBlank()) {
                FapiClient newClient = manualConnectOnce(input);
                if (newClient != null) {
                    fapiClient = newClient;
                    System.out.println("Switched to: " + fapiClient.getServicePeerId());
                }
            }
            Menu.anyKeyToContinue(br);
            return;
        }
        
        Service selected = selectService(providers);
        if (selected != null) {
            FapiClient newClient = connectToService(selected);
            if (newClient != null) {
                fapiClient = newClient;
                System.out.println("Switched to: " + selected.getStdName());
            }
        }
        Menu.anyKeyToContinue(br);
    }
    
    /**
     * 手动连接一次
     */
    private static FapiClient manualConnectOnce(String input) {
        String host = FapiDefaults.getHost(input);
        int port = FapiDefaults.getPort(input);
        
        System.out.println("Connecting to " + host + ":" + port + "...");
        DiscoveryResult result = FapiServiceDiscovery.discoverViaEndpoint(fudpNode, host, port);
        if (result != null && result.getServices() != null && !result.getServices().isEmpty()) {
            System.out.println("Connected successfully!");
            return new FapiClient(fudpNode, result.getPeerId(),
                    result.getServices().get(0).getId(),
                    FapiDefaults.DEFAULT_REQUEST_TIMEOUT_SEC, settings);
        }
        System.out.println("Failed to connect.");
        return null;
    }
    
    /**
     * 显示FUDP节点信息
     */
    private static void showFudpNodeInfo() {
        System.out.println("\n=== Current Configuration ===");
        if (fapiClient != null) {
            System.out.println("Service Peer ID: " + fapiClient.getServicePeerId());
            System.out.println("Service SID: " + fapiClient.getServiceSid());
        } else {
            System.out.println("Not connected to any service.");
        }
        if (fudpNode != null) {
            System.out.println("Local FID: " + fudpNode.getLocalFid());
            System.out.println("Local Port: " + fudpNode.getConfig().getPort());
        }
        System.out.println("\nDefault endpoints:");
        for (String ep : FapiDefaults.DEFAULT_ENDPOINTS) {
            System.out.println("  - " + ep);
        }
        Menu.anyKeyToContinue(br);
    }
    
    /**
     * 显示设置菜单（使用Settings.setting方法）
     */
    private static void showSettings() {
        if (settings == null) {
            System.out.println("Settings not initialized.");
            Menu.anyKeyToContinue(br);
            return;
        }
        // 调用Settings的setting方法，传入null表示这是客户端（不是服务器）
        settings.setting(br, null);
    }
    
    /**
     * 打印最后的错误
     */
    private static void printLastError() {
        if (fapiClient != null && fapiClient.getLastError() != null) {
            Exception err = fapiClient.getLastError();
            System.out.println("Error type: " + err.getClass().getSimpleName());
            System.out.println("Error message: " + err.getMessage());
        }
        if (fapiClient != null && fapiClient.getLastResponse() != null) {
            var resp = fapiClient.getLastResponse();
            System.out.println("Response code: " + resp.getCode() + ", message: " + resp.getMessage());
            if (resp.getData() != null) {
                System.out.println("Response data: " + new Gson().toJson(resp.getData()));
            }
        }
    }
    
    /**
     * 清理资源
     */
    private static void cleanup() {
        if (bootstrapResult != null) {
            bootstrapResult.cleanup();
        } else {
            // 如果 bootstrapResult 为 null，单独清理
            if (fudpNode != null) {
                try {
                    fudpNode.stop();
                } catch (Exception e) {
                    log.error("Error stopping FUDP node", e);
                }
            }
            
            if (settings != null) {
                try {
                    settings.close();
                } catch (Exception e) {
                    log.error("Error closing settings", e);
                }
            }
        }
    }

    // ==================== 客户端事件监听器 ====================

    /**
     * Default directory for storing received relay files.
     */
    private static final String RECEIVED_FILES_DIR = "received";

    /**
     * Event listener for the client's FudpNode.
     * Handles incoming messages such as relayed data from other peers.
     * Files are saved to the received directory named by their DID (sha256x2).
     */
    private static class ClientEventListener implements NodeEventListener {

        @Override
        public void onBytesReceived(String peerId, long messageId, int dataType, byte[] data) {
            String did = saveReceivedData(data);
            String typeStr = switch (dataType) {
                case BytesMessage.DATA_TYPE_RAW -> "raw";
                case BytesMessage.DATA_TYPE_JSON -> "json";
                case BytesMessage.DATA_TYPE_PROTOBUF -> "protobuf";
                case BytesMessage.DATA_TYPE_MSGPACK -> "msgpack";
                default -> "type-" + dataType;
            };
            System.out.println("\n╔══════════════════════════════════════╗");
            System.out.println("║  Incoming Message                    ║");
            System.out.println("╠══════════════════════════════════════╣");
            System.out.println("║ From: " + peerId);
            System.out.println("║ Message ID: " + messageId);
            System.out.println("║ Data type: " + typeStr);
            System.out.println("║ Size: " + data.length + " bytes");
            System.out.println("║ DID: " + did);
            System.out.println("║ Saved to: " + RECEIVED_FILES_DIR + "/" + did);
            System.out.println("╚══════════════════════════════════════╝");
        }

        @Override
        public void onRelayedMessageReceived(String relayPeerId, AppMessage message) {
            System.out.println("\n╔══════════════════════════════════════╗");
            System.out.println("║  Incoming Relayed Message            ║");
            System.out.println("╠══════════════════════════════════════╣");
            System.out.println("║ Relay peer: " + relayPeerId);
            System.out.println("║ Message type: " + message.getType());
            if (message instanceof BytesMessage bytesMsg) {
                byte[] data = bytesMsg.getData();
                String did = saveReceivedData(data);
                System.out.println("║ Size: " + data.length + " bytes");
                System.out.println("║ DID: " + did);
                System.out.println("║ Saved to: " + RECEIVED_FILES_DIR + "/" + did);
            } else {
                System.out.println("║ Message: " + message);
            }
            System.out.println("╚══════════════════════════════════════╝");
        }

        @Override
        public void onRelayedMessageReceived(String relayPeerId, String senderFid, long sessionId, AppMessage message) {
            System.out.println("\n╔══════════════════════════════════════╗");
            System.out.println("║  Incoming Relayed Message            ║");
            System.out.println("╠══════════════════════════════════════╣");
            System.out.println("║ Relay peer: " + relayPeerId);
            System.out.println("║ Sender FID: " + senderFid);
            System.out.println("║ Session ID: " + sessionId);
            System.out.println("║ Message type: " + message.getType());
            if (message instanceof BytesMessage bytesMsg) {
                byte[] data = bytesMsg.getData();
                String did = saveReceivedData(data);
                System.out.println("║ Size: " + data.length + " bytes");
                System.out.println("║ DID: " + did);
                System.out.println("║ Saved to: " + RECEIVED_FILES_DIR + "/" + did);
            } else {
                System.out.println("║ Message: " + message);
            }
            System.out.println("╚══════════════════════════════════════╝");
        }

        @Override
        public void onBytesAck(String peerId, long messageId, long rttMs) {
            log.debug("Bytes ACK from {}, messageId={}, RTT={}ms", peerId, messageId, rttMs);
        }

        @Override
        public void onPeerConnected(String peerId) {
            log.debug("Peer connected: {}", peerId);
        }

        @Override
        public void onPeerDisconnected(String peerId) {
            log.debug("Peer disconnected: {}", peerId);
        }

        @Override
        public void onError(String peerId, int errorCode, String message) {
            log.warn("Error from peer {}: code={}, message={}", peerId, errorCode, message);
        }

        /**
         * Compute DID (sha256x2) of data, save to received directory, and return the DID hex string.
         */
        private String saveReceivedData(byte[] data) {
            byte[] didBytes = Hash.sha256x2(data);
            String did = utils.Hex.toHex(didBytes);
            try {
                java.nio.file.Path dir = java.nio.file.Paths.get(RECEIVED_FILES_DIR);
                java.nio.file.Files.createDirectories(dir);
                java.nio.file.Path filePath = dir.resolve(did);
                java.nio.file.Files.write(filePath, data);
            } catch (java.io.IOException e) {
                log.error("Failed to save received data to {}/{}: {}", RECEIVED_FILES_DIR, did, e.getMessage());
            }
            return did;
        }
    }
}
