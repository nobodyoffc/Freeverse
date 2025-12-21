package fapi;

import config.Settings;
import config.Starter;
import data.fcData.AutoTask;
import data.fcData.Module;
import data.feipData.Service;
import data.feipData.serviceParams.Params;
import fapi.service.FapiServer;
import fapi.menu.BalanceIncomeMenu;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import handlers.BalanceManager;
import handlers.Manager;
import clients.NaSaClient.NaSaRpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.Menu;
import utils.ObjectUtils;
import utils.Hex;

import fudp.message.PongMessage;
import fudp.node.NodeStats;
import fudp.node.Peer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static config.Settings.CREDIT_LIMIT;
import static config.Settings.LISTEN_PATH;
import static constants.Constants.UserHome;

/**
 * FAPI Manager 启动类
 * 参照 StartApipManager 的启动流程
 */
public class StartFapiServer {
    private static final Logger log = LoggerFactory.getLogger(StartFapiServer.class);

    public static Service service;
    private static BufferedReader br;
    public static NaSaRpcClient naSaRpcClient;
    public static String sid;
    public static Params params;  // 使用通用 Params 类型
    private static Settings settings;
    private static FapiServer fapiServer;  // 保存 FapiService 实例，用于服务管理和 FUDP 请求处理
    private static FudpNode fudpNode;  // 保存 FUDP node 实例，用于显示连接信息
    public static final Service.ServiceType serverType = Service.ServiceType.FAPI;

    public static void main(String[] args) {
        
        // 1. 定义必需的 Modules
        List<Module> modules = new ArrayList<>();
        modules.add(new Module(Service.class.getSimpleName(),
                Service.ServiceType.NASA_RPC.name()));      // 用于获取最佳区块高度
        modules.add(new Module(Service.class.getSimpleName(),
                Service.ServiceType.ES.name()));             // 主要的数据来源
        modules.add(new Module(Manager.class.getSimpleName(),
                Manager.ManagerType.MEMPOOL.name()));        // 处理未确认交易查询（如需要）
        modules.add(new Module(Manager.class.getSimpleName(),
                Manager.ManagerType.CASH.name()));           // 管理cash(UTXO)，用于支付收益分配
        modules.add(new Module(Manager.class.getSimpleName(),
                Manager.ManagerType.BALANCE.name()));         // BalanceManager（经济账本）
        
        // 2. 定义设置参数
        Map<String, Object> settingMap = new HashMap<>();
        settingMap.put(LISTEN_PATH,System.getProperty(UserHome)+"/fc_data/blocks");
        settingMap.put(CREDIT_LIMIT,1000);
        settingMap.put(Settings.LOG_BALANCE_READ_ERROR, Settings.DEFAULT_LOG_BALANCE_READ_ERROR);
        settingMap.put(NodeConfig.FUDP_DATA_DIR, NodeConfig.FUDP_DATA);
        
        // 3. 定义自动任务
        List<AutoTask> autoTaskList = new ArrayList<>();
        
        // 4. 启动服务（使用 Starter.startServer）
        Menu.welcome("FAPI Manager");
        br = new BufferedReader(new InputStreamReader(System.in));
        
        // 注意：Service.ServiceType.FAPI 已在枚举中定义
        // Starter.startServer() 会自动通过 Service.types 过滤包含 "FAPI" 的服务
        settings = Starter.startServer(serverType, settingMap, null, modules, br, autoTaskList);
        if (settings == null) return;
        
        // 5. 获取服务信息
        byte[] symkey = settings.getSymkey();
        service = settings.getService();
        sid = service.getId();
        params = ObjectUtils.objectToClass(service.getParams(), Params.class);
        
        // 6. 初始化 FAPI 服务
        // 创建 FapiService（继承 ServiceManager，同时实现 NodeEventListener）
        fapiServer = new FapiServer(service, null, br, symkey, Params.class, settings);
        fapiServer.initialize();
        BalanceManager balanceManager = (BalanceManager) settings.getManager(Manager.ManagerType.BALANCE);
        
        // 7. 启动 FUDP Node 并注册 FAPI 服务
        try {
            // 获取私钥
            byte[] privateKey = settings.decryptPrikey();
            if (privateKey == null) {
                log.error("Failed to decrypt private key. Cannot start FUDP Node.");
                return;
            }

            Map<String, Object> currentSettingMap = settings.getSettingMap();
            if (currentSettingMap == null) {
                currentSettingMap = new HashMap<>();
                settings.setSettingMap(currentSettingMap);
            }
            currentSettingMap.putIfAbsent(NodeConfig.FUDP_DATA_DIR, NodeConfig.FUDP_DATA);
            
            // 创建 NodeConfig
            NodeConfig config = new NodeConfig();
            int port = extractPortFromUrlHead(params != null ? params.getUrlHead() : null, config.getPort());
            String dataDir = String.valueOf(currentSettingMap.get(NodeConfig.FUDP_DATA_DIR));

            config.setPort(port)
                    .setDataDir(dataDir);
            
            // 创建并启动 FudpNode
            fudpNode = new FudpNode(privateKey, config);
            fapiServer.setFudpNode(fudpNode);
            fudpNode.setEventListener(fapiServer);
            if (balanceManager != null) {
                fudpNode.addMeterListener(record -> {
                    BalanceManager.ChargeResult result = balanceManager.checkAndCharge(record);
                    if (result.getCode() == BalanceManager.ResultCode.CREDIT_EXCEEDED ||
                        result.getCode() == BalanceManager.ResultCode.INVALID_AMOUNT ||
                        result.getCode() == BalanceManager.ResultCode.INVALID_KEY) {
                        log.warn("Charge rejected for peer {}: {}", record.getPeerId(), result.getCode());
                    }
                });
            }
            fudpNode.start();
            
            log.info("FUDP Node started successfully. Local FID: {}", fudpNode.getLocalFid());
            
            // 显示连接信息，方便客户端配置
            System.out.println("\n========================================");
            System.out.println("FAPI Service Connection Information:");
            System.out.println("========================================");
            System.out.println("Peer FID: " + fudpNode.getLocalFid());
            System.out.println("Public Key (hex): " + Hex.toHex(fudpNode.getLocalPublicKey()));
            System.out.println("Service SID: " + sid);
            System.out.println("Host: 127.0.0.1 (or your public IP/hostname)");
            System.out.println("Port: " + config.getPort());
            System.out.println("Clients can now bootstrap with only host:port (HELLO+PING will fetch pubkey and services).");
            System.out.println("========================================\n");
        } catch (Exception e) {
            log.error("Failed to start FUDP Node", e);
            // Check if it's a resource lock issue
            if (e.getMessage() != null && e.getMessage().contains("Unable to acquire lock")) {
                System.err.println("\n========================================");
                System.err.println("Resource Lock Error:");
                System.err.println("Another instance may be running, or a previous");
                System.err.println("instance didn't shut down cleanly.");
                System.err.println("\nTo fix this:");
                System.err.println("1. Check if another FAPI Server is running and stop it");
                System.err.println("2. If no process is using it, check for lock files in fudp_data/");
                System.err.println("========================================\n");
            }
            return;
        }
        
        log.info("FAPI Manager started successfully. Service ID: {}", sid);
        
        // 8. 主菜单循环
        while (true) {
            Menu menu = new Menu("FAPI Manager", () -> close(br));
            menu.add("Manage service", () -> manageService(service, br, symkey));
            menu.add("Balance & Income Management", () -> {
                BalanceIncomeMenu balanceMenu = new BalanceIncomeMenu(
                    balanceManager,
                    br
                );
                balanceMenu.showMenu();
            });
            menu.add("Update services", () -> fapiServer.updateServices());
            menu.add("FUDP Performance", StartFapiServer::showPerformanceStats);
            menu.add("Settings", () -> settings.setting(br, serverType));
            menu.showAndSelect(br);
        }
    }
    
    /**
     * 管理服务
     * 使用 FapiService 的 ServiceManager 功能（发布、更新、停止等）
     */
    private static void manageService(Service service, BufferedReader br, byte[] symkey) {
        if (fapiServer == null) {
            // 如果 fapiService 未初始化，创建一个新实例（理论上不应该发生）
            fapiServer = new FapiServer(service, null, br, symkey, Params.class, settings);
            fapiServer.initialize();
        } else {
            // 确保使用最新的 service 对象（如果服务被更新了）
            fapiServer.setService(service);
        }
        // 使用主 fapiService 实例的 menu() 方法
        fapiServer.menu();
    }
    
    private static int extractPortFromUrlHead(String urlHead, int defaultPort) {
        if (urlHead == null || urlHead.isBlank()) {
            return defaultPort;
        }
        String normalized = urlHead.contains("://") ? urlHead : "fudp://" + urlHead;
        try {
            URI uri = new URI(normalized);
            int port = uri.getPort();
            if (port <= 0) {
                String scheme = uri.getScheme();
                if ("fudp".equalsIgnoreCase(scheme)) {
                    port = defaultPort;
                } else if ("https".equalsIgnoreCase(scheme)) {
                    port = 443;
                } else if ("http".equalsIgnoreCase(scheme)) {
                    port = 80;
                } else {
                    port = defaultPort;
                }
            }
            if (port > 0) {
                return port;
            }
        } catch (URISyntaxException e) {
            log.warn("Invalid urlHead '{}', using default port {}", urlHead, defaultPort);
        }
        return defaultPort;
    }

    // ==================== FUDP Performance Monitoring ====================
    
    private static void showPerformanceStats() {
        if (fudpNode == null || !fudpNode.isRunning()) {
            System.out.println("FUDP Node is not running.");
            Menu.anyKeyToContinue(br);
            return;
        }

        Menu statsMenu = new Menu("FUDP Performance Stats");
        statsMenu.add("Node Overview", StartFapiServer::showNodeOverview);
        statsMenu.add("Peer Details", StartFapiServer::showPeerDetails);
        statsMenu.add("Ping Test", StartFapiServer::pingTestWithStats);
        statsMenu.add("List Connected Peers", StartFapiServer::listConnectedPeers);

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
            System.out.print("Peer FID or alias: ");
            String peer = br.readLine().trim();

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

    private static void close(BufferedReader br) {
        try {
            System.out.println("Do you want to quit? 'q' to quit.");
            String input = br.readLine();
            if ("q".equals(input)) {
                // Properly shutdown FUDP Node before exiting
                if (fudpNode != null) {
                    try {
                        log.info("Stopping FUDP Node...");
                        fudpNode.stop();
                        log.info("FUDP Node stopped successfully");
                    } catch (Exception e) {
                        log.error("Error stopping FUDP Node", e);
                    }
                }
                br.close();
                if (settings != null) {
                    settings.close();
                }
                System.out.println("Exited, see you again.");
                System.exit(0);
            }
        } catch (IOException e) {
            log.error("Failed to close resources", e);
        }
    }
}
