package fapi.client;

import data.feipData.Service;
import fapi.client.AutoRechargeManager.RechargeResult;
import ui.Menu;
import ui.Inputer;
import utils.FchUtils;

import java.io.BufferedReader;

/**
 * 客户端充值菜单
 * 
 * 提供手动充值和自动充值状态查看功能。
 */
public class RechargeMenu {
    private final FapiClient fapiClient;
    private final BufferedReader br;
    
    public RechargeMenu(FapiClient fapiClient, BufferedReader br) {
        this.fapiClient = fapiClient;
        this.br = br;
    }
    
    public void showMenu() {
        if (fapiClient == null) {
            System.out.println("\nFapiClient is not available.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        Menu menu = new Menu("Recharge & Balance", () -> showCurrentBalance());
        menu.add("Show current balance", this::showCurrentBalance);
        menu.add("Show service pricing", this::showServicePricing);
        menu.add("Manual recharge (default amount)", this::manualRechargeDefault);
        menu.add("Manual recharge (custom amount)", this::manualRechargeCustom);
        menu.add("Show auto-recharge status", this::showAutoRechargeStatus);
        menu.add("Show last recharge info", this::showLastRechargeInfo);
        menu.add("Reset price alert", this::resetPriceAlert);
        menu.showAndSelect(br);
    }
    
    private void showCurrentBalance() {
        Long balance = fapiClient.getLastBalance();
        Long balanceSeq = fapiClient.getLastBalanceSeq();
        Long timestamp = fapiClient.getLastBalanceTimestampMillis();
        
        System.out.println("\n=== Current Balance ===");
        if (balance != null) {
            System.out.println("Balance: " + balance + " satoshi (" + FchUtils.satoshiToCoin(balance) + " FCH)");
            System.out.println("Sequence: " + balanceSeq);
            if (timestamp != null) {
                long ago = (System.currentTimeMillis() - timestamp) / 1000;
                System.out.println("Last updated: " + ago + " seconds ago");
            }
            
            // 检查是否低于阈值
            AutoRechargeManager arm = fapiClient.getAutoRechargeManager();
            if (arm != null) {
                long threshold = arm.getThreshold();
                if (balance <= threshold) {
                    System.out.println("\n⚠ Balance is at or below auto-recharge threshold (" + threshold + " satoshi)");
                    System.out.println("  Auto-recharge will trigger on next request.");
                }
            }
        } else {
            System.out.println("Balance not available. Make a request first to get current balance.");
        }
        System.out.println();
        Menu.anyKeyToContinue(br);
    }
    
    private void showServicePricing() {
        AutoRechargeManager arm = fapiClient.getAutoRechargeManager();
        Service service = (arm != null) ? arm.getCachedService() : null;
        
        if (service == null) {
            // 尝试从服务端获取
            System.out.println("Fetching service info...");
            String sid = fapiClient.getServiceSid();
            if (sid != null) {
                try {
                    var serviceMap = fapiClient.entityByIds(
                            constants.IndicesNames.SERVICE, Service.class, sid);
                    if (serviceMap != null && serviceMap.containsKey(sid)) {
                        service = serviceMap.get(sid);
                    }
                } catch (Exception e) {
                    System.out.println("Error fetching service: " + e.getMessage());
                }
            }
        }
        
        System.out.println("\n=== Service Pricing ===");
        if (service != null) {
            System.out.println("Service Name: " + service.getStdName());
            System.out.println("Service ID: " + service.getId());
            System.out.println("Dealer: " + service.getDealer());
            
            // 解析价格
            if (service.getPricePerKB() != null && !service.getPricePerKB().isEmpty()) {
                try {
                    double pricePerKbFch = Double.parseDouble(service.getPricePerKB());
                    long pricePerKbSat = FchUtils.coinToSatoshi(pricePerKbFch);
                    System.out.println("Price per KB: " + pricePerKbFch + " FCH (" + pricePerKbSat + " satoshi)");
                } catch (NumberFormatException e) {
                    System.out.println("Price per KB: " + service.getPricePerKB() + " (invalid format)");
                }
            } else {
                System.out.println("Price per KB: Not set");
            }
            
            if (service.getMinPayment() != null && !service.getMinPayment().isEmpty()) {
                try {
                    double minPayFch = Double.parseDouble(service.getMinPayment());
                    long minPaySat = FchUtils.coinToSatoshi(minPayFch);
                    System.out.println("Minimum Payment: " + minPayFch + " FCH (" + minPaySat + " satoshi)");
                } catch (NumberFormatException e) {
                    System.out.println("Minimum Payment: " + service.getMinPayment() + " (invalid format)");
                }
            } else {
                System.out.println("Minimum Payment: Not set");
            }
            
            // 显示计算的购买金额
            if (arm != null) {
                long purchaseKb = arm.getPurchaseKb();
                System.out.println("\n--- Calculated Recharge Amount ---");
                System.out.println("Configured purchase: " + purchaseKb + " KB");
                
                double pricePerKbFch = 0;
                double minPayFch = 0;
                try {
                    if (service.getPricePerKB() != null) {
                        pricePerKbFch = Double.parseDouble(service.getPricePerKB());
                    }
                    if (service.getMinPayment() != null) {
                        minPayFch = Double.parseDouble(service.getMinPayment());
                    }
                } catch (NumberFormatException ignored) {}
                
                double calculatedFch = purchaseKb * pricePerKbFch;
                double actualFch = Math.max(calculatedFch, minPayFch);
                System.out.println("Calculated amount: " + calculatedFch + " FCH");
                System.out.println("Actual payment (max with minPayment): " + actualFch + " FCH");
            }
        } else {
            System.out.println("Service info not available.");
        }
        System.out.println();
        Menu.anyKeyToContinue(br);
    }
    
    private void manualRechargeDefault() {
        AutoRechargeManager arm = fapiClient.getAutoRechargeManager();
        if (arm == null) {
            System.out.println("\nAuto-recharge manager not available. Settings required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        if (arm.isRecharging()) {
            System.out.println("\nRecharge is already in progress. Please wait.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("\n=== Manual Recharge (Default Amount) ===");
        System.out.println("Purchase KB: " + arm.getPurchaseKb());
        
        String confirm = Inputer.inputString(br, "Proceed with recharge? (y/n): ");
        if (!"y".equalsIgnoreCase(confirm) && !"yes".equalsIgnoreCase(confirm)) {
            System.out.println("Recharge cancelled.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        executeRecharge(null);
    }
    
    private void manualRechargeCustom() {
        AutoRechargeManager arm = fapiClient.getAutoRechargeManager();
        if (arm == null) {
            System.out.println("\nAuto-recharge manager not available. Settings required.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        if (arm.isRecharging()) {
            System.out.println("\nRecharge is already in progress. Please wait.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("\n=== Manual Recharge (Custom Amount) ===");
        String amountStr = Inputer.inputString(br, "Enter amount in FCH: ");
        
        double amountFch;
        try {
            amountFch = Double.parseDouble(amountStr.trim());
            if (amountFch <= 0) {
                System.out.println("Amount must be positive.");
                Menu.anyKeyToContinue(br);
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid amount: " + amountStr);
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Amount: " + amountFch + " FCH (" + FchUtils.coinToSatoshi(amountFch) + " satoshi)");
        String confirm = Inputer.inputString(br, "Proceed with recharge? (y/n): ");
        if (!"y".equalsIgnoreCase(confirm) && !"yes".equalsIgnoreCase(confirm)) {
            System.out.println("Recharge cancelled.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        executeRecharge(amountFch);
    }
    
    private void executeRecharge(Double amountFch) {
        System.out.println("\nProcessing recharge...");
        
        RechargeResult result = fapiClient.manualRecharge(amountFch);
        
        System.out.println("\n=== Recharge Result ===");
        if (result.isSuccess()) {
            System.out.println("✓ Recharge successful!");
            System.out.println("Transaction ID: " + result.getTxId());
            System.out.println("Amount: " + result.getAmountFch() + " FCH (" + result.getAmountSatoshi() + " satoshi)");
            System.out.println("Recipient: " + result.getRecipientFid());
            System.out.println("\nNote: Your balance will update on the next request.");
        } else {
            System.out.println("✗ Recharge failed!");
            System.out.println("Error: " + result.getMessage());
        }
        System.out.println();
        Menu.anyKeyToContinue(br);
    }
    
    private void showAutoRechargeStatus() {
        AutoRechargeManager arm = fapiClient.getAutoRechargeManager();
        
        System.out.println("\n=== Auto-Recharge Status ===");
        if (arm == null) {
            System.out.println("Auto-recharge manager not initialized.");
            System.out.println("Reason: Settings not provided to FapiClient.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Enabled: " + arm.isEnabled());
        System.out.println("Threshold: " + arm.getThreshold() + " satoshi");
        System.out.println("Purchase amount: " + arm.getPurchaseKb() + " KB");
        System.out.println("Max payment limit: " + arm.getMaxPayment() + " satoshi (" + 
                FchUtils.satoshiToCoin(arm.getMaxPayment()) + " FCH)");
        System.out.println("Currently recharging: " + arm.isRecharging());
        
        // 价格告警状态
        if (arm.isStoppedDueToPriceLimit()) {
            System.out.println("\n⚠ PRICE ALERT: Auto-recharge is STOPPED due to price limit!");
            System.out.println("Alert message: " + arm.getPriceAlertMessage());
            System.out.println("Use 'Reset price alert' to resume after adjusting settings or confirming pricing.");
        }
        
        long lastAttempt = arm.getLastRechargeAttemptMs();
        if (lastAttempt > 0) {
            long ago = (System.currentTimeMillis() - lastAttempt) / 1000;
            System.out.println("\nLast attempt: " + ago + " seconds ago");
        } else {
            System.out.println("\nLast attempt: Never");
        }
        
        long lastSuccess = arm.getLastSuccessfulRechargeMs();
        if (lastSuccess > 0) {
            long ago = (System.currentTimeMillis() - lastSuccess) / 1000;
            System.out.println("Last success: " + ago + " seconds ago");
        } else {
            System.out.println("Last success: Never");
        }
        System.out.println();
        Menu.anyKeyToContinue(br);
    }
    
    private void resetPriceAlert() {
        AutoRechargeManager arm = fapiClient.getAutoRechargeManager();
        
        System.out.println("\n=== Reset Price Alert ===");
        if (arm == null) {
            System.out.println("Auto-recharge manager not initialized.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        if (!arm.isStoppedDueToPriceLimit()) {
            System.out.println("No price alert is currently active.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        System.out.println("Current alert: " + arm.getPriceAlertMessage());
        System.out.println("\nMax payment limit: " + arm.getMaxPayment() + " satoshi (" + 
                FchUtils.satoshiToCoin(arm.getMaxPayment()) + " FCH)");
        System.out.println("\nWARNING: Resetting the alert will allow auto-recharge to attempt again.");
        System.out.println("Make sure the service pricing is reasonable or adjust your maxPayment setting.");
        
        String confirm = Inputer.inputString(br, "Reset price alert? (y/n): ");
        if ("y".equalsIgnoreCase(confirm) || "yes".equalsIgnoreCase(confirm)) {
            arm.resetPriceAlert();
            System.out.println("Price alert has been reset. Auto-recharge can now proceed.");
        } else {
            System.out.println("Reset cancelled.");
        }
        Menu.anyKeyToContinue(br);
    }
    
    private void showLastRechargeInfo() {
        AutoRechargeManager arm = fapiClient.getAutoRechargeManager();
        
        System.out.println("\n=== Last Recharge Info ===");
        if (arm == null) {
            System.out.println("Auto-recharge manager not initialized.");
            Menu.anyKeyToContinue(br);
            return;
        }
        
        String lastTxId = arm.getLastRechargeTxId();
        long lastSuccess = arm.getLastSuccessfulRechargeMs();
        
        if (lastTxId != null && lastSuccess > 0) {
            System.out.println("Transaction ID: " + lastTxId);
            long ago = (System.currentTimeMillis() - lastSuccess) / 1000;
            System.out.println("Time: " + ago + " seconds ago");
            System.out.println("\nYou can view this transaction on a block explorer.");
        } else {
            System.out.println("No successful recharge recorded in this session.");
        }
        System.out.println();
        Menu.anyKeyToContinue(br);
    }
}

