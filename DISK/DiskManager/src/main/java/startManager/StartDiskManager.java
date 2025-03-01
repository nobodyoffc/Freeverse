package startManager;

import appTools.Starter;
import clients.ApipClient;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import constants.FieldNames;
import feip.feipData.Service;
import feip.feipData.serviceParams.DiskParams;
import appTools.Inputer;
import appTools.Menu;
import fcData.DiskItem;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import configure.Configure;
import handlers.AccountHandler;
import handlers.Handler;
import server.DiskApiNames;
import tools.EsTools;
import tools.JsonTools;
import redis.clients.jedis.JedisPool;
import server.balance.BalanceInfo;
import server.order.Order;
import server.reward.RewardInfo;
import server.reward.RewardManager;
import server.serviceManagers.DiskManager;
import appTools.Settings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static feip.feipData.Service.ServiceType.*;
import static constants.Constants.UserHome;
import static constants.IndicesNames.ORDER;
import static constants.Strings.*;
import static appTools.Settings.DEFAULT_WINDOW_TIME;
import static handlers.AccountHandler.*;

public class StartDiskManager {
    public static  String STORAGE_DIR;
    private static Settings settings;
    public static ElasticsearchClient esClient;
    public static ApipClient apipClient;
    public static Service service;
    public static DiskParams params;
//    public static DiskCounter counter;

    public static String sid;
    public static final Service.ServiceType serverType = Service.ServiceType.DISK;

//    public static Service.ServiceType[] serviceAliases = new Service.ServiceType[]{
//            Service.ServiceType.APIP,
//            Service.ServiceType.ES,
//            Service.ServiceType.REDIS
//    };
//
//    public static HandlerType[] requiredHandlers = new HandlerType[]{
//            HandlerType.CASH,
//            HandlerType.SESSION,
//            HandlerType.ACCOUNT
//    };

    public static final Object[] modules = new Object[]{
        Service.ServiceType.REDIS,
        Service.ServiceType.ES,
        Service.ServiceType.APIP,
        Handler.HandlerType.CASH,
        Handler.HandlerType.SESSION,
        Handler.HandlerType.ACCOUNT
    };


    public static 	Map<String,Object> settingMap = new HashMap<>();
    static {
        settingMap.put(Settings.FORBID_FREE_API, false);
        settingMap.put(Settings.WINDOW_TIME, DEFAULT_WINDOW_TIME);
        settingMap.put(Settings.LISTEN_PATH, System.getProperty(UserHome) + "/fc_data/blocks");
        settingMap.put(Settings.FROM_WEBHOOK, true);
        settingMap.put(Settings.MIN_DISTRIBUTE_BALANCE, DEFAULT_DISTRIBUTE_BALANCE);
        settingMap.put(Settings.DEALER_MIN_BALANCE, DEFAULT_DEALER_MIN_BALANCE);
    }
    public static void main(String[] args) throws IOException {
        Menu.welcome("DISK Manager");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        settings = Starter.startServer(serverType, settingMap, DiskApiNames.diskApiList, modules, null, br);
        if(settings==null)return;

        byte[] symKey = settings.getSymKey();
        Configure configure = settings.getConfig();
        service =settings.getService();
        sid = service.getId();
        params = (DiskParams) service.getParams();

        //Prepare API clients
        apipClient = (ApipClient) settings.getClient(APIP);
        esClient = (ElasticsearchClient) settings.getClient(ES);
        JedisPool jedisPool = (JedisPool) settings.getClient(REDIS);

        Configure.checkWebConfig(settings);

        //Check indices in ES
        checkEsIndices(esClient);

        AccountHandler accountHandler = (AccountHandler) settings.getHandler(Handler.HandlerType.ACCOUNT);

        //Show the main menu
        Menu menu = new Menu();
        menu.setTitle("Disk Manager");

        menu.add("Manage the service");
//        menu.add("Manage order");
//        menu.add("Manage balance");
//        menu.add("Manage the rewards");
        menu.add("Manage account");
        menu.add("Reset the price multipliers(nPrice)");
        menu.add("Manage ES indices");
        menu.add("Settings");

        while(true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> new DiskManager(service, settings.getApiAccount(APIP), br,symKey, DiskParams.class).menu();
//                case 2 -> new OrderManager(service, counter, br, esClient, jedisPool).menu();
//                case 3 -> new BalanceManager(service, br, esClient,jedisPool).menu();
                case 2 -> accountHandler.menu(br, false);
                case 3 -> new RewardManager(sid,params.getDealer(),apipClient,esClient,null, jedisPool, br)
                        .menu(params.getConsumeViaShare(), params.getOrderViaShare());
                case 4 -> Order.resetNPrices(br, sid, jedisPool);
                case 5 -> manageIndices(br);//recreateAllIndices(esClient, br);
                case 6 -> settings.setting(br, serverType);
                case 0 -> {
//                    if (counter != null) counter.close();
                    accountHandler.close();
                    close();
                    System.out.println("Exited, see you again.");
                    System.exit(0);
                }
            }
        }
    }

    private static void manageIndices(BufferedReader br) {
        Menu menu = new Menu();

        ArrayList<String> menuItemList = new ArrayList<>();
        menuItemList.add("List All Indices in ES");
        menuItemList.add("Recreate Data index");
        menuItemList.add("Recreate Order Backup index");
        menuItemList.add("Recreate Balance index");
        menuItemList.add("Recreate Reward index");
        menuItemList.add("Recreate All indices");

        menu.add(menuItemList);
        while(true) {
            menu.show();
            menu.setTitle("Manage ES indices");
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> listAllIndices(esClient,br);
                case 2 -> EsTools.recreateIndex(Settings.addSidBriefToName(sid,DATA), DiskItem.MAPPINGS,esClient,br);
                case 3 -> EsTools.recreateIndex(Settings.addSidBriefToName(sid,ORDER), Order.MAPPINGS,esClient, br);
                case 4 -> EsTools.recreateIndex(Settings.addSidBriefToName(sid, FieldNames.BALANCE), BalanceInfo.MAPPINGS,esClient, br);
                case 5 -> EsTools.recreateIndex(Settings.addSidBriefToName(sid,REWARD), RewardInfo.MAPPINGS,esClient, br);
                case 6 -> recreateAllIndices(esClient,br);
                case 0 -> {
                    return;
                }
            }
        }
    }
    public static void listAllIndices(ElasticsearchClient esClient, BufferedReader br) {
        IndicesResponse result = null;
        try {
            result = esClient.cat().indices();
        } catch (IOException e) {
            System.out.println("Failed to get data from ES:"+e.getMessage());
            return;
        }
        List<IndicesRecord> indicesRecordList = result.valueBody();

        Map<String, String> allSumMap = new HashMap<>();
        for (IndicesRecord record : indicesRecordList) {
            allSumMap.put(record.index(), record.docsCount());
        }
        System.out.println(JsonTools.toNiceJson(allSumMap));
    }

//    private static void startCounterThread(byte[] symKey, Settings settings, Params params) {
//        byte[] priKey = Settings.getMainFidPriKey(symKey, settings);
//        counter = new DiskCounter(settings,priKey, params);
//        Thread thread = new Thread(counter);
//        thread.start();
//    }
    private static void close() throws IOException {
        settings.close();
    }

    private static void recreateAllIndices(ElasticsearchClient esClient,BufferedReader br) {
        if(!Inputer.askIfYes(br,"Recreate the disk data, order, balance, reward indices?"))return;
        EsTools.recreateIndex(Settings.addSidBriefToName(sid,DATA), DiskItem.MAPPINGS,esClient, br);
        EsTools.recreateIndex(Settings.addSidBriefToName(sid,ORDER), Order.MAPPINGS,esClient, br);
        EsTools.recreateIndex(Settings.addSidBriefToName(sid, FieldNames.BALANCE), BalanceInfo.MAPPINGS,esClient, br);
        EsTools.recreateIndex(Settings.addSidBriefToName(sid,REWARD), RewardInfo.MAPPINGS,esClient, br);
    }

    private static void checkEsIndices(ElasticsearchClient esClient) {
        Map<String,String> nameMappingList = new HashMap<>();
        nameMappingList.put(Settings.addSidBriefToName(sid,DATA), DiskItem.MAPPINGS);
        nameMappingList.put(Settings.addSidBriefToName(sid,ORDER), Order.MAPPINGS);
        nameMappingList.put(Settings.addSidBriefToName(sid, FieldNames.BALANCE), BalanceInfo.MAPPINGS);
        nameMappingList.put(Settings.addSidBriefToName(sid,REWARD), RewardInfo.MAPPINGS);
        EsTools.checkEsIndices(esClient,nameMappingList);
    }

}
