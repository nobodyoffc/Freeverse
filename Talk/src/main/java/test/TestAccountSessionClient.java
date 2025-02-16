package test;

import appTools.Menu;
import appTools.Settings;
import appTools.Starter;
import feip.feipData.Service;
import handlers.AccountHandler;
import handlers.AccountHandler.Income;
import clients.ApipClient;
import handlers.CashHandler;
import handlers.SessionHandler;
import fcData.FcSession;
import redis.clients.jedis.JedisPool;
import tools.JsonTools;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class TestAccountSessionClient {
    private static BufferedReader br;
    public static ApipClient apipClient;
    private static Settings settings;
    private static JedisPool jedisPool;
    public static String clientName = "Test";
    public static Service.ServiceType[] serviceAliases = new Service.ServiceType[]{Service.ServiceType.APIP, Service.ServiceType.REDIS};
    public static Map<String,Object> settingMap = new HashMap<>();
    public static final Object[] modules = new Object[]{
            Service.ServiceType.REDIS,
            Service.ServiceType.APIP
    };

    public static void main(String[] args) {
        br = new BufferedReader(new InputStreamReader(System.in));
        Menu.welcome(clientName);

        settings = Starter.startClient(clientName, settingMap, br, modules);
        if(settings==null) return;

        apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);
//        jedisPool = (JedisPool) settings.getClient(ServiceType.REDIS);

        // Initialize clients
        String sid = null;//"1d01da1b8bcb90bea26c5efcc33c39e66baa24d4d6d28d7bbb6b1a728bb3900e";
        CashHandler cashHandler = new CashHandler(settings);
        cashHandler.updateCashesIfOverJumped();
        AccountHandler accountHandler = new AccountHandler(settings);
        SessionHandler sessionHandler = new SessionHandler(settings);

        // Test AccountClient functionality
        testAccountClient(accountHandler);

        // Test FcSessionClient functionality
        testFcSessionClient(sessionHandler);

        // Clean up
        accountHandler.close();
        sessionHandler.close();
    }

    private static void testAccountClient(AccountHandler accountHandler) {
        System.out.println("\n=== Testing AccountClient ===");

        // Test balance updates
        System.out.println("Testing balance updates...");
        accountHandler.updateMyBalance();
        
        accountHandler.addUserBalance("FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7", 1000L);
        Long balance = accountHandler.checkUserBalance("FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7");
        System.out.println("Test user balance: " + balance);

        // Test income updates
        System.out.println("\nTesting income updates...");
        Map<String, Income> newIncomes = accountHandler.updateIncome();
        System.out.println("New incomes processed: " + newIncomes.size());

        // Test expense updates
        System.out.println("\nTesting expense updates...");
        int newExpenses = accountHandler.updateExpense();
        System.out.println("New expenses processed: " + newExpenses);

        // Test via updates
        System.out.println("\nTesting via updates...");
        accountHandler.updateFidConsumeVia("FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7", "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        System.out.println("Fid via updated: " + accountHandler.getFidConsumeVia("FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7"));

        // Test via balance updates
        System.out.println("\nTesting via balance updates...");
        accountHandler.addViaBalance("FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7", 100000L, "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        accountHandler.addViaBalance("FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7", 200000L, null);
        System.out.println("Via balance updated: " + accountHandler.getViaBalance("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK"));

        // Test distribution
        System.out.println("\nTesting distribution...");
        accountHandler.distribute();
        System.out.println("Distribution result: ");
        JsonTools.printJson(accountHandler.getPayoffMap());
        // Test settlement
        System.out.println("\nTesting settlement...");
        boolean settlementResult = accountHandler.settle();
        System.out.println("Settlement result: " + settlementResult);
    }

    private static void testFcSessionClient(SessionHandler sessionHandler) {
        System.out.println("\n=== Testing FcSessionClient ===");

        // Create a test session
        FcSession testSession = new FcSession();
        testSession.setId("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        testSession.setName("testSession");
        testSession.setPubKey("testPubKey");
        testSession.setKey("testKey");
        
        // Test session storage
        System.out.println("Testing session storage...");
        sessionHandler.putSession(testSession);

        // Test session retrieval by name
        System.out.println("\nTesting session retrieval by name...");
        FcSession retrievedByName = sessionHandler.getSessionByName("testSession");
        System.out.println("Session retrieved by name: " + 
            (retrievedByName != null ? "Success" : "Failed"));
        retrievedByName = sessionHandler.getSessionByName("FPL44YJRwPdd2ipziFvqq6y2tw4VnVvpAv");
        System.out.println("Session retrieved by name: " +
                (retrievedByName != null ? "Success" : "Failed"));

        // Test session retrieval by FID
        System.out.println("\nTesting session retrieval by FID...");
        FcSession retrievedByFid = sessionHandler.getSessionById("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        System.out.println("Session retrieved by FID: " + 
            (retrievedByFid != null ? "Success" : "Failed"));
        retrievedByFid = sessionHandler.getSessionById("FPL44YJRwPdd2ipziFvqq6y2tw4VnVvpAv");
        System.out.println("Session retrieved by FID: " +
                (retrievedByFid != null ? "Success" : "Failed"));

        // Test session removal
        System.out.println("\nTesting session removal...");
        sessionHandler.removeSessionById("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        FcSession afterRemoval = sessionHandler.getSessionById("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        System.out.println("Session removal: " + 
            (afterRemoval == null ? "Success" : "Failed"));
        sessionHandler.removeSessionById("FPL44YJRwPdd2ipziFvqq6y2tw4VnVvpAv");
        afterRemoval = sessionHandler.getSessionById("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        System.out.println("Session removal: " +
                (afterRemoval == null ? "Success" : "Failed"));
    }
}
