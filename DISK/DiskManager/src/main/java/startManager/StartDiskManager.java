package startManager;

import appTools.Starter;
import clients.ApipClient;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import constants.FieldNames;
import fcData.AutoTask;
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
import utils.EsUtils;
import utils.JsonUtils;
import redis.clients.jedis.JedisPool;
import server.balance.BalanceInfo;
import server.order.Order;
import server.reward.RewardInfo;
import server.reward.RewardManager;
import server.serviceManagers.DiskManager;
import appTools.Settings;
import utils.ObjectUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

import static appTools.Settings.*;
import static constants.Constants.SEC_PER_DAY;
import static feip.feipData.Service.ServiceType.*;
import static constants.IndicesNames.ORDER;
import static constants.Strings.*;
import static feip.feipData.Service.ServiceType.APIP;

public class StartDiskManager {
    public static  String diskDir;
    private static Settings settings;
    public static ElasticsearchClient esClient;
    public static ApipClient apipClient;
    public static Service service;
    public static DiskParams params;

    public static String sid;
    public static final Service.ServiceType serverType = Service.ServiceType.DISK;

    public static final Object[] modules = new Object[]{
        Service.ServiceType.REDIS,
        Service.ServiceType.ES,
        Service.ServiceType.APIP,
        Handler.HandlerType.CASH,
        Handler.HandlerType.SESSION,
        Handler.HandlerType.ACCOUNT
    };


    public static void main(String[] args) throws IOException {
        Map<String,Object>  settingMap = new HashMap<> ();
        settingMap.put(Settings.FORBID_FREE_API, false);
        settingMap.put(Settings.WINDOW_TIME, DEFAULT_WINDOW_TIME);
        settingMap.put(FieldNames.DISK_DIR, DISK_DATA_PATH);
        settingMap.put(AccountHandler.DISTRIBUTE_DAYS,AccountHandler.DEFAULT_DISTRIBUTE_DAYS);
        settingMap.put(AccountHandler.MIN_DISTRIBUTE_BALANCE,AccountHandler.DEFAULT_MIN_DISTRIBUTE_BALANCE);
        settingMap.put(AccountHandler.DEALER_MIN_BALANCE,AccountHandler.DEFAULT_DEALER_MIN_BALANCE);

        List<AutoTask> autoTaskList = new ArrayList<>();
        autoTaskList.add(new AutoTask(Handler.HandlerType.NONCE, "removeTimeOutNonce", SEC_PER_DAY));
        autoTaskList.add(new AutoTask(Handler.HandlerType.ACCOUNT, "distribute", 10*SEC_PER_DAY));
        autoTaskList.add(new AutoTask(Handler.HandlerType.ACCOUNT, "saveMapsToLocalDB", SEC_PER_DAY));

        Menu.welcome("DISK Manager");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        settings = Starter.startServer(serverType, settingMap, DiskApiNames.diskApiList, modules, br, autoTaskList);
        if(settings==null)return;

        diskDir = (String) settings.getSettingMap().get(FieldNames.DISK_DIR);

        byte[] symKey = settings.getSymKey();
        service =settings.getService();
        sid = service.getId();
        params = ObjectUtils.objectToClass( service.getParams(),DiskParams.class);

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
        menu.add("Manage account");
        menu.add("Reset the price multipliers(nPrice)");
        menu.add("Manage ES indices");
        menu.add("Settings");

        while(true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> new DiskManager(service, settings.getApiAccount(APIP), br,symKey, DiskParams.class).menu();
                case 2 -> accountHandler.menu(br, false);
                case 3 -> new RewardManager(sid,params.getDealer(),apipClient,esClient,null, jedisPool, br)
                        .menu(params.getConsumeViaShare(), params.getOrderViaShare());
                case 4 -> Order.resetNPrices(br, sid, jedisPool);
                case 5 -> manageIndices(br);
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
                case 2 -> EsUtils.recreateIndex(Settings.addSidBriefToName(sid,DATA), DiskItem.MAPPINGS,esClient,br);
                case 3 -> EsUtils.recreateIndex(Settings.addSidBriefToName(sid,ORDER), Order.MAPPINGS,esClient, br);
                case 4 -> EsUtils.recreateIndex(Settings.addSidBriefToName(sid, FieldNames.BALANCE), BalanceInfo.MAPPINGS,esClient, br);
                case 5 -> EsUtils.recreateIndex(Settings.addSidBriefToName(sid,REWARD), RewardInfo.MAPPINGS,esClient, br);
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
        System.out.println(JsonUtils.toNiceJson(allSumMap));
    }

    private static void close() throws IOException {
        settings.close();
    }

    private static void recreateAllIndices(ElasticsearchClient esClient,BufferedReader br) {
        if(!Inputer.askIfYes(br,"Recreate the disk data, order, balance, reward indices?"))return;
        EsUtils.recreateIndex(Settings.addSidBriefToName(sid,DATA), DiskItem.MAPPINGS,esClient, br);
        EsUtils.recreateIndex(Settings.addSidBriefToName(sid,ORDER), Order.MAPPINGS,esClient, br);
        EsUtils.recreateIndex(Settings.addSidBriefToName(sid, FieldNames.BALANCE), BalanceInfo.MAPPINGS,esClient, br);
        EsUtils.recreateIndex(Settings.addSidBriefToName(sid,REWARD), RewardInfo.MAPPINGS,esClient, br);
    }

    private static void checkEsIndices(ElasticsearchClient esClient) {
        Map<String,String> nameMappingList = new HashMap<>();
        nameMappingList.put(Settings.addSidBriefToName(sid,DATA), DiskItem.MAPPINGS);
        nameMappingList.put(Settings.addSidBriefToName(sid,ORDER), Order.MAPPINGS);
        nameMappingList.put(Settings.addSidBriefToName(sid, FieldNames.BALANCE), BalanceInfo.MAPPINGS);
        nameMappingList.put(Settings.addSidBriefToName(sid,REWARD), RewardInfo.MAPPINGS);
        EsUtils.checkEsIndices(esClient,nameMappingList);
    }
}
