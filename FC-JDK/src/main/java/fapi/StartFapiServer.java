package fapi;

import data.feipData.Service;
import data.feipData.ServiceType;
import fapi.components.DiskComponent;
import fapi.components.DockComponent;
import fapi.components.disk.DiskSyncManager;
import fapi.components.disk.DiskSyncSource;
import fapi.components.disk.DiskSyncState;
import fapi.menu.BalanceIncomeMenu;
import fapi.service.FapiServer;
import fudp.node.FudpNode;
import fudp.security.DDoSConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.Menu;
import utils.Hex;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * FAPI服务端启动类（重构版）
 * 
 * 基于新的 ServiceBootstrap 框架启动 FAPI 服务。
 * 
 * 主要改进：
 * 1. 使用 ServiceBootstrap.bootstrapServer() 替代 Starter.startServer()
 * 2. 统一的配置和启动流程
 * 3. 自动加载链上服务声明的组件类型
 * 4. 使用 FapiServer 内置的 FapiBalanceManager
 */
public class StartFapiServer {
    private static final Logger log = LoggerFactory.getLogger(StartFapiServer.class);

    public static final String SERVER_NAME = "FAPI Server";
    
    private static BufferedReader br;
    private static FapiServer fapiServer;

    public static void main(String[] args) {
        Menu.welcome(SERVER_NAME);
        br = new BufferedReader(new InputStreamReader(System.in));
        
        try {
            while (true) {
                try {
                    ServiceBootstrapStarter starter = ServiceBootstrapStarter.forFapiServer()
                        .setBr(br);
                    
                    fapiServer = ServiceBootstrap.bootstrapServer(starter);
                    if (fapiServer == null) {
                        log.error("Failed to start FAPI server");
                        System.out.println("\nFAPI server bootstrap failed. Press 'q' to quit, or any other key to retry...");
                        String input = br.readLine();
                        if ("q".equalsIgnoreCase(input != null ? input.trim() : "")) {
                            System.out.println("Exiting.");
                            return;
                        }
                        continue;
                    }
                    break;
                } catch (Exception e) {
                    log.error("Failed to bootstrap FAPI server", e);
                    System.out.println("\nBootstrap error: " + e.getMessage());
                    System.out.println("Press 'q' to quit, or any other key to retry...");
                    String input = br.readLine();
                    if ("q".equalsIgnoreCase(input != null ? input.trim() : "")) {
                        System.out.println("Exiting.");
                        return;
                    }
                }
            }
            
            showMainMenu();
            
        } catch (Exception e) {
            log.error("Failed to start FAPI Server", e);
        } finally {
            cleanup();
        }
    }
    
    /**
     * 打印连接信息
     */
    private static void printConnectionInfo() {
        FudpNode fudpNode = fapiServer.getFudpNode();
        Service service = fapiServer.getService();
        
        System.out.println("\n========================================");
        System.out.println("FAPI Service Connection Information:");
        System.out.println("========================================");
        System.out.println("Peer FID: " + fudpNode.getLocalFid());
        System.out.println("Public Key: " + Hex.toHex(fudpNode.getLocalPublicKey()));
        System.out.println("Service SID: " + (service != null ? service.getId() : "N/A"));
        System.out.println("Port: " + fudpNode.getConfig().getPort());
        System.out.println("Components loaded: " + formatComponentList());
        System.out.println("BalanceManager: " + (fapiServer.getBalanceManager() != null ? "active" : "not available"));
        System.out.println("========================================\n");
    }
    
    /**
     * 格式化组件列表
     */
    private static String formatComponentList() {
        if (fapiServer.getComponents() == null || fapiServer.getComponents().isEmpty()) {
            return "None";
        }
        StringBuilder sb = new StringBuilder();
        for (FapiComponent component : fapiServer.getComponents()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(component.getName());
        }
        return sb.toString();
    }
    
    /**
     * 显示主菜单
     */
    private static void showMainMenu() {
        while (true) {
            Menu menu = new Menu(SERVER_NAME, StartFapiServer::requestClose);
            
            menu.add("Manage service", () -> fapiServer.menu());
            menu.add("Balance & Income", StartFapiServer::showBalanceMenu);
            menu.add("Update services", () -> {
                byte[] symkey = fapiServer.getSettings().getSymkey();
                fapiServer.updateService(symkey, br);
            });
            menu.add("Component status", StartFapiServer::showComponentStatus);

            menu.add("Settings", () -> {
                fapiServer.getSettings().setting(br, ServiceType.FAPI_No1_NrC7);
            });
            menu.add("DDoS Defence", StartFapiServer::ddosDefenceMenu);
            menu.add("DOCK Forward", StartFapiServer::dockForwardMenu);
            menu.add("DISK Sync", StartFapiServer::diskSyncMenu);

            menu.showAndSelect(br);
        }
    }
    
    /**
     * 显示余额菜单
     */
    private static void showBalanceMenu() {
        FapiBalanceManager balanceManager = fapiServer.getBalanceManager();
        if (balanceManager != null) {
            new BalanceIncomeMenu(balanceManager, br).showMenu();
        } else {
            System.out.println("BalanceManager not available");
            Menu.anyKeyToContinue(br);
        }
    }
    
    /**
     * 显示组件状态
     */
    private static void showComponentStatus() {
        System.out.println("\n=== Component Status ===");
        if (fapiServer.getComponents() == null || fapiServer.getComponents().isEmpty()) {
            System.out.println("No components loaded.");
        } else {
            for (FapiComponent component : fapiServer.getComponents()) {
                System.out.printf("  %-10s : %s (APIs: %d)\n", 
                    component.getName(), 
                    component.getState(),
                    component.getApiList() != null ? component.getApiList().size() : 0);
            }
        }
        System.out.println("========================\n");
        Menu.anyKeyToContinue(br);
    }
    
    /**
     * DDoS防御开关菜单
     */
    private static void ddosDefenceMenu() {
        FudpNode fudpNode = fapiServer.getFudpNode();
        if (fudpNode == null) {
            System.out.println("FudpNode not available.");
            Menu.anyKeyToContinue(br);
            return;
        }

        DDoSConfig ddosConfig = fudpNode.getConfig().getDdosConfig();
        System.out.println("\n=== DDoS Defence ===");
        System.out.println("Current status: " + (ddosConfig.isEnabled() ? "ON" : "OFF"));
        System.out.println("Base difficulty: " + ddosConfig.getBaseDifficulty());
        System.out.println("Max difficulty: " + ddosConfig.getMaxDifficulty());
        System.out.println("Rate limit: " + ddosConfig.getMaxPacketsPerSecondPerIp() + " pkt/s per IP");
        System.out.println();

        try {
            System.out.print("Toggle DDoS defence? (y/N): ");
            String input = br.readLine().trim().toLowerCase();
            if ("y".equals(input) || "yes".equals(input)) {
                boolean newState = !ddosConfig.isEnabled();
                ddosConfig.setEnabled(newState);
                System.out.println("DDoS defence is now " + (newState ? "ON" : "OFF") + " (effective immediately)");
            } else {
                System.out.println("No change.");
            }
        } catch (Exception e) {
            log.error("Error in DDoS defence menu", e);
        }
        Menu.anyKeyToContinue(br);
    }

    /**
     * DOCK forward toggle menu
     */
    private static void dockForwardMenu() {
        DockComponent dock = fapiServer.getComponent(DockComponent.class);
        if (dock == null) {
            System.out.println("DOCK component not loaded.");
            Menu.anyKeyToContinue(br);
            return;
        }

        System.out.println("\n=== DOCK Forward ===");
        System.out.println("Current status: " + (dock.isForwardEnabled() ? "ALLOWED" : "FORBIDDEN"));
        System.out.println();

        try {
            System.out.print("Toggle DOCK forward? (y/N): ");
            String input = br.readLine().trim().toLowerCase();
            if ("y".equals(input) || "yes".equals(input)) {
                boolean newState = !dock.isForwardEnabled();
                dock.setForwardEnabled(newState);

                // Persist to settingMap
                Map<String, Object> settingMap = getOrCreateSettingMap();
                settingMap.put(DockComponent.KEY_DOCK_FORWARD_ENABLED, newState);
                saveSettings();

                System.out.println("DOCK forward is now " + (newState ? "ALLOWED" : "FORBIDDEN") + " (effective immediately)");
            } else {
                System.out.println("No change.");
            }
        } catch (Exception e) {
            log.error("Error in DOCK forward menu", e);
        }
        Menu.anyKeyToContinue(br);
    }

    /**
     * DISK data synchronization menu
     */
    private static void diskSyncMenu() {
        DiskComponent disk = fapiServer.getComponent(DiskComponent.class);
        DiskSyncManager syncManager = disk != null ? disk.getDiskSyncManager() : null;

        Menu menu = new Menu("DISK Sync");

        if (disk != null) {
            menu.add("View sync status", () -> showSyncStatus(disk, syncManager));
            menu.add("Trigger sync now", () -> triggerSyncNow(syncManager));
            menu.add("View disk usage", () -> showDiskUsage(disk));
        }
        menu.add("Manage DISK servers", StartFapiServer::manageDiskServers);
        menu.add("View sync parameters", StartFapiServer::showSyncParams);
        menu.add("Update sync parameters", StartFapiServer::updateSyncParams);

        menu.showAndSelect(br);
    }

    private static void showSyncStatus(DiskComponent disk, DiskSyncManager syncManager) {
        System.out.println("\n=== DISK Sync Status ===");
        if (syncManager == null) {
            System.out.println("Sync manager not initialized (no diskSyncSources configured).");
        } else {
            System.out.println("Running: " + syncManager.isRunning());
            System.out.println("Sources:");
            for (DiskSyncSource src : syncManager.getSources()) {
                System.out.printf("  SID: %s  URL: %s  Enabled: %s\n", src.getSid(), src.getUrl(), src.isEnabled());
            }

            Map<String, DiskSyncState> states = syncManager.getSyncStates();
            if (states != null && !states.isEmpty()) {
                System.out.println("\nSync progress:");
                for (Map.Entry<String, DiskSyncState> entry : states.entrySet()) {
                    DiskSyncState s = entry.getValue();
                    System.out.printf("  [%s] lastSince=%s  lastId=%s  items=%d  bytes=%d  lastTime=%s\n",
                            entry.getKey(),
                            s.getLastSyncSince() != null ? s.getLastSyncSince() : "-",
                            s.getLastSyncId() != null ? abbreviate(s.getLastSyncId()) : "-",
                            s.getItemsSynced(),
                            s.getBytesSynced(),
                            s.getLastSyncTime() > 0 ? new java.util.Date(s.getLastSyncTime()).toString() : "never");
                }
            } else {
                System.out.println("\nNo sync progress recorded yet.");
            }
        }
        System.out.println("MaxDataSize: " + formatBytes(disk.getMaxDataSize()));
        System.out.println("MaxTotalDiskUsage: " + formatBytes(disk.getMaxTotalDiskUsage()));
        System.out.println("========================\n");
        Menu.anyKeyToContinue(br);
    }

    private static void triggerSyncNow(DiskSyncManager syncManager) {
        if (syncManager == null) {
            System.out.println("Sync manager not initialized.");
            Menu.anyKeyToContinue(br);
            return;
        }
        System.out.println("Starting manual sync...");
        new Thread(() -> {
            try {
                syncManager.syncAll();
                System.out.println("Manual sync completed.");
            } catch (Exception e) {
                System.out.println("Manual sync failed: " + e.getMessage());
            }
        }, "disk-sync-manual").start();
        System.out.println("Sync triggered in background.");
        Menu.anyKeyToContinue(br);
    }

    private static void showDiskUsage(DiskComponent disk) {
        long used = disk.getDiskHandler().getTotalStorageSize();
        long max = disk.getMaxTotalDiskUsage();
        double pct = max > 0 ? (used * 100.0 / max) : 0;
        System.out.println("\n=== DISK Usage ===");
        System.out.printf("Used:  %s\n", formatBytes(used));
        System.out.printf("Limit: %s\n", formatBytes(max));
        System.out.printf("Usage: %.1f%%\n", pct);
        System.out.println("==================\n");
        Menu.anyKeyToContinue(br);
    }

    private static Map<String, Object> getOrCreateSettingMap() {
        Map<String, Object> sm = fapiServer.getSettings().getSettingMap();
        if (sm == null) {
            sm = new java.util.HashMap<>();
            fapiServer.getSettings().setSettingMap(sm);
        }
        return sm;
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<Map<String, Object>> getSyncSourceList(Map<String, Object> settingMap) {
        return (java.util.List<Map<String, Object>>)
                settingMap.computeIfAbsent(DiskSyncManager.KEY_DISK_SYNC_SOURCES, k -> new java.util.ArrayList<>());
    }

    private static void saveSettings() {
        fapiServer.getSettings().saveServerSettings(fapiServer.getService().getId());
        System.out.println("Settings saved.");
    }

    private static void manageDiskServers() {
        Map<String, Object> settingMap = getOrCreateSettingMap();

        Menu menu = new Menu("Manage DISK Servers");
        menu.add("List servers", () -> listDiskServers(settingMap));
        menu.add("Add server", () -> addDiskServer(settingMap));
        menu.add("Remove server", () -> removeDiskServer(settingMap));
        menu.add("Enable/Disable server", () -> toggleDiskServer(settingMap));
        menu.showAndSelect(br);
    }

    private static void listDiskServers(Map<String, Object> settingMap) {
        java.util.List<Map<String, Object>> sources = getSyncSourceList(settingMap);
        if (sources.isEmpty()) {
            System.out.println("\nNo DISK servers configured.");
            Menu.anyKeyToContinue(br);
            return;
        }
        System.out.println("\n=== DISK Servers ===");
        for (int i = 0; i < sources.size(); i++) {
            Map<String, Object> s = sources.get(i);
            boolean enabled = !"false".equals(String.valueOf(s.getOrDefault("enabled", true)));
            System.out.printf("  %d. [%s] sid=%s  url=%s\n",
                    i + 1, enabled ? "ON" : "OFF",
                    s.getOrDefault("sid", "-"), s.getOrDefault("url", "-"));
        }
        System.out.println("====================\n");
        Menu.anyKeyToContinue(br);
    }

    private static void addDiskServer(Map<String, Object> settingMap) {
        String sid = ui.Inputer.inputString(br, "Input the SID of the remote FAPI DISK service:");
        if (sid == null || sid.isEmpty()) return;
        String url = ui.Inputer.inputString(br, "Input the URL (host:port) of the remote FAPI DISK service:");
        if (url == null || url.isEmpty()) return;

        java.util.List<Map<String, Object>> sources = getSyncSourceList(settingMap);
        for (Map<String, Object> existing : sources) {
            if (sid.equals(existing.get("sid"))) {
                System.out.println("Server with SID " + sid + " already exists.");
                Menu.anyKeyToContinue(br);
                return;
            }
        }

        Map<String, Object> entry = new java.util.HashMap<>();
        entry.put("sid", sid);
        entry.put("url", url);
        entry.put("enabled", true);
        sources.add(entry);
        saveSettings();
        System.out.println("Added: sid=" + sid + " url=" + url);
        System.out.println("Restart required to activate the new source.");
        Menu.anyKeyToContinue(br);
    }

    private static void removeDiskServer(Map<String, Object> settingMap) {
        java.util.List<Map<String, Object>> sources = getSyncSourceList(settingMap);
        if (sources.isEmpty()) {
            System.out.println("No DISK servers configured.");
            Menu.anyKeyToContinue(br);
            return;
        }
        System.out.println("\nServers:");
        for (int i = 0; i < sources.size(); i++) {
            Map<String, Object> s = sources.get(i);
            System.out.printf("  %d. sid=%s  url=%s\n", i + 1, s.getOrDefault("sid", "-"), s.getOrDefault("url", "-"));
        }
        Long idx = ui.Inputer.inputLong(br, "Enter number to remove (0 to cancel)", 0L);
        if (idx == null || idx <= 0 || idx > sources.size()) {
            System.out.println("Cancelled.");
            return;
        }
        Map<String, Object> removed = sources.remove(idx.intValue() - 1);
        saveSettings();
        System.out.println("Removed: sid=" + removed.getOrDefault("sid", "-"));
        System.out.println("Restart required to apply.");
        Menu.anyKeyToContinue(br);
    }

    private static void toggleDiskServer(Map<String, Object> settingMap) {
        java.util.List<Map<String, Object>> sources = getSyncSourceList(settingMap);
        if (sources.isEmpty()) {
            System.out.println("No DISK servers configured.");
            Menu.anyKeyToContinue(br);
            return;
        }
        System.out.println("\nServers:");
        for (int i = 0; i < sources.size(); i++) {
            Map<String, Object> s = sources.get(i);
            boolean enabled = !"false".equals(String.valueOf(s.getOrDefault("enabled", true)));
            System.out.printf("  %d. [%s] sid=%s  url=%s\n",
                    i + 1, enabled ? "ON" : "OFF",
                    s.getOrDefault("sid", "-"), s.getOrDefault("url", "-"));
        }
        Long idx = ui.Inputer.inputLong(br, "Enter number to toggle (0 to cancel)", 0L);
        if (idx == null || idx <= 0 || idx > sources.size()) {
            System.out.println("Cancelled.");
            return;
        }
        Map<String, Object> target = sources.get(idx.intValue() - 1);
        boolean wasEnabled = !"false".equals(String.valueOf(target.getOrDefault("enabled", true)));
        target.put("enabled", !wasEnabled);
        saveSettings();
        System.out.println("Server sid=" + target.getOrDefault("sid", "-") + " is now " + (!wasEnabled ? "ON" : "OFF"));
        System.out.println("Restart required to apply.");
        Menu.anyKeyToContinue(br);
    }

    private static void showSyncParams() {
        Map<String, Object> sm = fapiServer.getSettings().getSettingMap();
        if (sm == null) sm = Map.of();

        System.out.println("\n=== DISK Sync Parameters ===");
        System.out.println("maxDataSize:           " + formatBytes(longVal(sm, DiskSyncManager.KEY_MAX_DATA_SIZE, DiskSyncManager.DEFAULT_MAX_DATA_SIZE)));
        System.out.println("maxTotalDiskUsage:     " + formatBytes(longVal(sm, DiskSyncManager.KEY_MAX_TOTAL_DISK_USAGE, DiskSyncManager.DEFAULT_MAX_TOTAL_DISK_USAGE)));
        System.out.println("minDealerBalance:      " + longVal(sm, DiskSyncManager.KEY_MIN_DEALER_BALANCE, DiskSyncManager.DEFAULT_MIN_DEALER_BALANCE) + " sat");
        System.out.println("diskSyncIntervalHours: " + longVal(sm, DiskSyncManager.KEY_DISK_SYNC_INTERVAL_HOURS, DiskSyncManager.DEFAULT_SYNC_INTERVAL_HOURS) + " h");
        System.out.println("============================\n");
        Menu.anyKeyToContinue(br);
    }

    private static void updateSyncParams() {
        Map<String, Object> settingMap = getOrCreateSettingMap();

        Menu menu = new Menu("Update Sync Parameters");

        menu.add("Set maxDataSize", () -> {
            Long v = ui.Inputer.inputLong(br, "maxDataSize (bytes)", longVal(settingMap, DiskSyncManager.KEY_MAX_DATA_SIZE, DiskSyncManager.DEFAULT_MAX_DATA_SIZE));
            settingMap.put(DiskSyncManager.KEY_MAX_DATA_SIZE, v);
            saveSettings();
            System.out.println("Set to " + formatBytes(v));
        });
        menu.add("Set maxTotalDiskUsage", () -> {
            Long v = ui.Inputer.inputLong(br, "maxTotalDiskUsage (bytes)", longVal(settingMap, DiskSyncManager.KEY_MAX_TOTAL_DISK_USAGE, DiskSyncManager.DEFAULT_MAX_TOTAL_DISK_USAGE));
            settingMap.put(DiskSyncManager.KEY_MAX_TOTAL_DISK_USAGE, v);
            saveSettings();
            System.out.println("Set to " + formatBytes(v));
        });
        menu.add("Set minDealerBalance", () -> {
            Long v = ui.Inputer.inputLong(br, "minDealerBalance (satoshi)", longVal(settingMap, DiskSyncManager.KEY_MIN_DEALER_BALANCE, DiskSyncManager.DEFAULT_MIN_DEALER_BALANCE));
            settingMap.put(DiskSyncManager.KEY_MIN_DEALER_BALANCE, v);
            saveSettings();
            System.out.println("Set to " + v + " sat");
        });
        menu.add("Set syncIntervalHours", () -> {
            Long v = ui.Inputer.inputLong(br, "diskSyncIntervalHours", longVal(settingMap, DiskSyncManager.KEY_DISK_SYNC_INTERVAL_HOURS, DiskSyncManager.DEFAULT_SYNC_INTERVAL_HOURS));
            settingMap.put(DiskSyncManager.KEY_DISK_SYNC_INTERVAL_HOURS, v);
            saveSettings();
            System.out.println("Set to " + v + " h");
        });

        menu.showAndSelect(br);
    }

    private static long longVal(Map<String, Object> map, String key, long defaultVal) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).longValue();
        return defaultVal;
    }

    private static String orDefault(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : "-";
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String abbreviate(String s) {
        if (s == null) return null;
        return s.length() > 16 ? s.substring(0, 8) + "..." + s.substring(s.length() - 8) : s;
    }

    /**
     * 请求关闭
     */
    private static void requestClose() {
        try {
            System.out.println("Do you want to quit? 'q' to quit.");
            String input = br.readLine();
            if ("q".equals(input)) {
                cleanup();
                System.out.println("Exited, see you again.");
                System.exit(0);
            }
        } catch (IOException e) {
            log.error("Error reading input", e);
        }
    }
    
    /**
     * 清理资源
     */
    private static void cleanup() {
        if (fapiServer != null) {
            try {
                log.info("Stopping FAPI_No1_NrC7 Server...");
                
                // 停止所有组件
                fapiServer.stopComponents();
                
                // 关闭余额管理器
                fapiServer.closeBalanceManager();
                
                // 停止 FudpNode
                FudpNode fudpNode = fapiServer.getFudpNode();
                if (fudpNode != null) {
                    fudpNode.stop();
                }
                
                // 关闭 Settings
                if (fapiServer.getSettings() != null) {
                    fapiServer.getSettings().close();
                }
                
                log.info("FAPI@No1_NrC7 Server stopped.");
            } catch (Exception e) {
                log.error("Error during cleanup", e);
            }
        }
        
        try {
            if (br != null) br.close();
        } catch (IOException e) {
            // ignore
        }
    }
}
