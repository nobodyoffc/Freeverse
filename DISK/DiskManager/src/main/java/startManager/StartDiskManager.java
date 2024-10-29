package startManager;

import appTools.Starter;
import clients.apipClient.ApipClient;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import configure.ServiceType;
import feip.feipData.Service;
import feip.feipData.serviceParams.DiskParams;
import appTools.Inputer;
import appTools.Menu;
import clients.diskClient.DiskItem;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import configure.Configure;
import constants.ApiNames;
import clients.esClient.EsTools;
import feip.feipData.serviceParams.Params;
import javaTools.JsonTools;
import redis.clients.jedis.JedisPool;
import server.*;
import server.balance.BalanceInfo;
import server.balance.BalanceManager;
import server.order.Order;
import server.order.OrderManager;
import server.reward.RewardInfo;
import server.reward.RewardManager;
import server.reward.Rewarder;
import server.serviceManagers.DiskManager;
import appTools.Settings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static configure.ServiceType.*;
import static constants.Constants.UserHome;
import static constants.IndicesNames.ORDER;
import static constants.Strings.*;
import static appTools.Settings.DEFAULT_WINDOW_TIME;

public class StartDiskManager {
    public static  String STORAGE_DIR;
    private static Settings settings;
    public static ElasticsearchClient esClient;
    public static ApipClient apipClient;
    public static Service service;
    public static DiskParams params;
    public static DiskCounter counter;

    public static String sid;
    private static BufferedReader br;
    public static final ServiceType serverType = ServiceType.DISK;

    public static String[] serviceAliases = new String[]{
        ServiceType.APIP.name(),
                ServiceType.ES.name(),
                ServiceType.REDIS.name()};

    public static 	Map<String,Object> settingMap = new HashMap<>();
    static {
        settingMap.put(Settings.SHARE_API, false);
        settingMap.put(Settings.FORBID_FREE_API, false);
        settingMap.put(Settings.WINDOW_TIME, DEFAULT_WINDOW_TIME);
        settingMap.put(Settings.LISTEN_PATH, System.getProperty(UserHome) + "/fc_data/blocks");
        settingMap.put(Settings.FROM_WEBHOOK, true);
    }
    public static void main(String[] args) throws IOException {
        Menu.welcome("DISK Manager");
        br = new BufferedReader(new InputStreamReader(System.in));
        settings = Starter.startServer(serverType, serviceAliases,settingMap, br);
        if(settings==null)return;

        byte[] symKey = settings.getSymKey();
        Configure configure = settings.getConfig();
        service =settings.getService();
        sid = service.getSid();
        params = (DiskParams) service.getParams();

        //Prepare API clients
        apipClient = (ApipClient) settings.getClient(APIP);
        esClient = (ElasticsearchClient) settings.getClient(ES);
        JedisPool jedisPool = (JedisPool) settings.getClient(REDIS);

        Configure.checkWebConfig(sid,configure, settings,symKey, serverType, jedisPool, br);

        //Check indices in ES
        checkEsIndices(esClient);

        //Check API prices
        Order.setNPrices(sid, ApiNames.DiskApiList, jedisPool, br,false);

        //Check user balance
        Counter.checkUserBalance(sid, jedisPool, esClient, br);

        //Check webhooks for new orders.
        if(settings.getSettingMap().get(Settings.FROM_WEBHOOK).equals(Boolean.TRUE))
            if (!Order.checkWebhook(ApiNames.NewCashByFids, params, apipClient.getApiAccount(), br, jedisPool)){
                close();
                return;
            }

        Rewarder.checkRewarderParams(sid, params, jedisPool, br);

        startCounterThread(symKey, settings,params);
        
        //Show the main menu
        Menu menu = new Menu();
        menu.setTitle("Disk Manager");

        menu.add("Manage the service");
        menu.add("Manage order");
        menu.add("Manage balance");
        menu.add("Manage the rewards");
        menu.add("Reset the price multipliers(nPrice)");
        menu.add("Manage ES indices");
        menu.add("Settings");

        while(true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> new DiskManager(service, settings.getApiAccount(APIP), br,symKey, DiskParams.class).menu();
                case 2 -> new OrderManager(service, counter, br, esClient, jedisPool).menu();
                case 3 -> new BalanceManager(service, br, esClient,jedisPool).menu();
                case 4 -> new RewardManager(sid,params.getDealer(),apipClient,esClient,null, jedisPool, br)
                        .menu(params.getConsumeViaShare(), params.getOrderViaShare());
                case 5 -> Order.resetNPrices(br, sid, jedisPool);
                case 6 -> manageIndices(br);//recreateAllIndices(esClient, br);
                case 7 -> settings.setting(symKey, br, serverType);
                case 0 -> {
                    if (counter != null) counter.close();
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
                case 4 -> EsTools.recreateIndex(Settings.addSidBriefToName(sid,BALANCE), BalanceInfo.MAPPINGS,esClient, br);
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

    private static void startCounterThread(byte[] symKey, Settings settings, Params params) {
        byte[] priKey = Settings.getMainFidPriKey(symKey, settings);
        counter = new DiskCounter(settings,priKey, params);
        Thread thread = new Thread(counter);
        thread.start();
    }
    private static void close() throws IOException {
        settings.close();
    }

    private static void recreateAllIndices(ElasticsearchClient esClient,BufferedReader br) {
        if(!Inputer.askIfYes(br,"Recreate the disk data, order, balance, reward indices?"))return;
        EsTools.recreateIndex(Settings.addSidBriefToName(sid,DATA), DiskItem.MAPPINGS,esClient, br);
        EsTools.recreateIndex(Settings.addSidBriefToName(sid,ORDER), Order.MAPPINGS,esClient, br);
        EsTools.recreateIndex(Settings.addSidBriefToName(sid,BALANCE), BalanceInfo.MAPPINGS,esClient, br);
        EsTools.recreateIndex(Settings.addSidBriefToName(sid,REWARD), RewardInfo.MAPPINGS,esClient, br);
    }

    private static void checkEsIndices(ElasticsearchClient esClient) {
        Map<String,String> nameMappingList = new HashMap<>();
        nameMappingList.put(Settings.addSidBriefToName(sid,DATA), DiskItem.MAPPINGS);
        nameMappingList.put(Settings.addSidBriefToName(sid,ORDER), Order.MAPPINGS);
        nameMappingList.put(Settings.addSidBriefToName(sid,BALANCE), BalanceInfo.MAPPINGS);
        nameMappingList.put(Settings.addSidBriefToName(sid,REWARD), RewardInfo.MAPPINGS);
        EsTools.checkEsIndices(esClient,nameMappingList);
    }

}
