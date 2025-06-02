package startManager;

import config.Starter;
import clients.ApipClient;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import constants.FieldNames;
import data.fcData.AutoTask;
import data.fcData.Module;
import data.feipData.Service;
import data.feipData.serviceParams.DiskParams;
import handlers.AccountManager;
import handlers.Manager;
import ui.Inputer;
import ui.Menu;
import data.fcData.DiskItem;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import config.Configure;
import server.DiskApiNames;
import utils.EsUtils;
import utils.JsonUtils;
import redis.clients.jedis.JedisPool;
import server.balance.BalanceInfo;
import server.order.Order;
import server.reward.RewardInfo;
import server.reward.RewardManager;
import server.serviceManagers.DiskManager;
import config.Settings;
import utils.ObjectUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

import static config.Settings.*;
import static constants.Constants.SEC_PER_DAY;
import static data.feipData.Service.ServiceType.*;
import static constants.IndicesNames.ORDER;
import static constants.Strings.*;
import static data.feipData.Service.ServiceType.APIP;

public class StartDiskManager {
    public static  String diskDir;
    private static Settings settings;
    public static ElasticsearchClient esClient;
    public static ApipClient apipClient;
    public static Service service;
    public static DiskParams params;

    public static String sid;
    public static final Service.ServiceType serverType = Service.ServiceType.DISK;




    public static void main(String[] args) throws IOException {
         List<Module> modules = new ArrayList<>();
         modules.add(new Module(Service.class.getSimpleName(),Service.ServiceType.REDIS.name()));
         modules.add(new Module(Service.class.getSimpleName(),Service.ServiceType.ES.name()));
         modules.add(new Module(Service.class.getSimpleName(),Service.ServiceType.APIP.name()));
         modules.add(new Module(Manager.class.getSimpleName(), Manager.ManagerType.CASH.name()));
         modules.add(new Module(Manager.class.getSimpleName(), Manager.ManagerType.SESSION.name()));
         modules.add(new Module(Manager.class.getSimpleName(), Manager.ManagerType.NONCE.name()));
         modules.add(new Module(Manager.class.getSimpleName(), Manager.ManagerType.ACCOUNT.name()));
         modules.add(new Module(Manager.class.getSimpleName(), Manager.ManagerType.DISK.name()));

        Map<String,Object>  settingMap = new HashMap<> ();
        settingMap.put(Settings.FORBID_FREE_API, false);
        settingMap.put(Settings.WINDOW_TIME, DEFAULT_WINDOW_TIME);
        settingMap.put(FieldNames.DISK_DIR, DISK_DATA_PATH);
        settingMap.put(AccountManager.DISTRIBUTE_DAYS, AccountManager.DEFAULT_DISTRIBUTE_DAYS);
        settingMap.put(AccountManager.MIN_DISTRIBUTE_BALANCE, AccountManager.DEFAULT_MIN_DISTRIBUTE_BALANCE);
        settingMap.put(AccountManager.DEALER_MIN_BALANCE, AccountManager.DEFAULT_DEALER_MIN_BALANCE);

        List<AutoTask> autoTaskList = new ArrayList<>();
        autoTaskList.add(new AutoTask(Manager.ManagerType.NONCE, "removeTimeOutNonce", SEC_PER_DAY));
        autoTaskList.add(new AutoTask(Manager.ManagerType.ACCOUNT, "distribute", 10*SEC_PER_DAY));
        autoTaskList.add(new AutoTask(Manager.ManagerType.ACCOUNT, "saveMapsToLocalDB", SEC_PER_DAY));
        autoTaskList.add(new AutoTask(Manager.ManagerType.DISK,"deleteExpiredFiles",SEC_PER_DAY));

        Menu.welcome("DISK Manager");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        settings = Starter.startServer(serverType, settingMap, DiskApiNames.diskApiList, modules, br, autoTaskList);
        if(settings==null)return;

        diskDir = (String) settings.getSettingMap().get(FieldNames.DISK_DIR);

        byte[] symkey = settings.getSymkey();
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

        AccountManager accountHandler = (AccountManager) settings.getManager(Manager.ManagerType.ACCOUNT);

        //Show the main menu
        Menu menu = new Menu();
        menu.setTitle("Disk Manager");

        menu.add("Manage the service");
        menu.add("Manage account");
        menu.add("Manage Rewards");
        menu.add("Reset the price multipliers(nPrice)");
        menu.add("Manage ES indices");
        menu.add("Settings");

        while(true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> new DiskManager(service, settings.getApiAccount(APIP), br,symkey, DiskParams.class).menu();
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
