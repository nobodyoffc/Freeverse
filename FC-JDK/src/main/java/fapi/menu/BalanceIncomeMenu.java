package fapi.menu;

import handlers.BalanceManager;
import ui.Menu;
import ui.Inputer;
import utils.FchUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

/**
 * Balance & Income Management Menu backed by BalanceManager (FAPI economics model).
 */
public class BalanceIncomeMenu {
    private final BalanceManager balanceManager;
    private final BufferedReader br;

    public BalanceIncomeMenu(BalanceManager balanceManager, BufferedReader br) {
        this.balanceManager = balanceManager;
        this.br = br;
    }

    public void showMenu() {
        if (balanceManager == null) {
            System.out.println("\nEconomics module is not available. BalanceManager was not initialized.");
            Menu.anyKeyToContinue(br);
            return;
        }

        Menu menu = new Menu("Economics (Balance & Recharge)", () -> {});
        menu.add("Show pricing & shares", this::showPricing);
        menu.add("Query peer balance", this::queryBalance);
        menu.add("Manual charge (requestKey)", this::manualCharge);
        menu.add("Manual credit (cashId)", this::manualCredit);
        menu.add("Tail audit log", this::tailAudit);
        menu.add("Refresh pricing from Service", this::refreshPricing);
        menu.showAndSelect(br);
    }

    private void showPricing() {
        BalanceManager.Pricing pricing = balanceManager.getPricing();
        System.out.println("\nCurrent pricing (from Service params):");
        System.out.println("pricePerKB: " + pricing.getPricePerKb() + " sat (" + FchUtils.satoshiToCoin(pricing.getPricePerKb()) + " FCH)");
        System.out.println("orderViaShare: " + pricing.getOrderViaShareBps() + " bps");
        System.out.println("consumeViaShare: " + pricing.getConsumeViaShareBps() + " bps");
        Menu.anyKeyToContinue(br);
    }

    private void queryBalance() {
        String peerId = promptString("PeerId:");
        BalanceManager.BalanceView view = balanceManager.getBalance(peerId);
        System.out.println("\nBalance for " + view.getPeerId());
        System.out.println("  balance: " + view.getBalance() + " sat (" + FchUtils.satoshiToCoin(view.getBalance()) + " FCH)");
        System.out.println("  creditLimit: " + view.getCreditLimit() + " sat");
        System.out.println("  available (balance + credit): " + view.getAvailable() + " sat");
        Menu.anyKeyToContinue(br);
    }

    private void manualCharge() {
        String requestKey = promptString("requestKey:");
        String peerId = promptString("peerId:");
        long amount = promptLong("amount(satoshi):");
        BalanceManager.ChargeResult result = balanceManager.charge(requestKey, peerId, amount, null);
        System.out.println("\nCharge result: " + result.getCode());
        System.out.println("Status: " + result.getStatus());
        System.out.println("Balance: " + result.getBalance() + " sat (creditLimit " + result.getCreditLimit() + ")");
        if (result.getMessage() != null) {
            System.out.println("Message: " + result.getMessage());
        }
        Menu.anyKeyToContinue(br);
    }

    private void manualCredit() {
        String cashId = promptString("cashId:");
        String userId = promptString("userId:");
        long amount = promptLong("amount(satoshi):");
        String statusInput = promptString("status (pending/confirmed/reverted):").toLowerCase();
        BalanceManager.CreditStatus status = switch (statusInput) {
            case "confirmed" -> BalanceManager.CreditStatus.CONFIRMED;
            case "reverted" -> BalanceManager.CreditStatus.REVERTED;
            default -> BalanceManager.CreditStatus.PENDING;
        };
        BalanceManager.CreditResult result = balanceManager.credit(userId, cashId, amount, "manual", status, null, null);
        System.out.println("\nCredit result: " + result.getCode());
        System.out.println("Status: " + result.getStatus());
        System.out.println("Balance: " + result.getBalance() + " sat (creditLimit " + result.getCreditLimit() + ")");
        if (result.getMessage() != null) {
            System.out.println("Message: " + result.getMessage());
        }
        Menu.anyKeyToContinue(br);
    }

    private void tailAudit() {
        List<BalanceManager.AuditRecord> records = balanceManager.getRecentAudit(20);
        System.out.println("\nRecent audit entries (newest last):");
        for (BalanceManager.AuditRecord record : records) {
            System.out.println("ts=" + record.getTs() +
                ", type=" + record.getType() +
                ", requestKey=" + record.getRequestKey() +
                ", cashId=" + record.getCashId() +
                ", amount=" + record.getAmount() +
                ", status=" + record.getStatus());
        }
        Menu.anyKeyToContinue(br);
    }

    private void refreshPricing() {
        boolean ok = balanceManager.refreshPricingFromService();
        System.out.println(ok ? "\nPricing refreshed." : "\nFailed to refresh pricing.");
        Menu.anyKeyToContinue(br);
    }

    private String promptString(String label) {
        return Inputer.inputString(br, label);
    }

    private long promptLong(String label) {
        while (true) {
            try {
                String text = Inputer.inputString(br, label);
                return Long.parseLong(text);
            } catch (Exception e) {
                System.out.println("Invalid number, try again.");
            }
        }
    }
}
