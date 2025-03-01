package talkServer;

import appTools.Inputer;
import appTools.Menu;
import appTools.Settings;
import appTools.Starter;
import clients.ApipClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import configure.Configure;
import constants.FieldNames;
import crypto.CryptoDataByte;
import crypto.Decryptor;
import fcData.TalkUnit;
import feip.feipData.Service;
import feip.feipData.serviceParams.Params;
import feip.feipData.serviceParams.TalkParams;

import handlers.Handler;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.JedisPool;
import server.Counter;
import server.TalkServer;
import server.balance.BalanceInfo;
import server.order.Order;
import server.reward.RewardInfo;
import server.reward.RewardManager;
import server.serviceManagers.TalkManager;
import tools.EsTools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static appTools.Settings.DEFAULT_WINDOW_TIME;
import static constants.Strings.*;
import static handlers.AccountHandler.DEFAULT_DEALER_MIN_BALANCE;

public class StartTalkServer {
    private static Settings settings;
    public static ElasticsearchClient esClient;
    public static ApipClient apipClient;
    public static Service service;
    public static TalkParams params;
    public static Counter counter;
    public static String sid;
    public static double price;
    public static final Service.ServiceType serverType = Service.ServiceType.TALK;

    public static final Object[] modules = new Object[]{
            Service.ServiceType.APIP,
            Service.ServiceType.DISK,
            Service.ServiceType.ES,
            Service.ServiceType.REDIS,
            Handler.HandlerType.SESSION,
            Handler.HandlerType.CASH,
            Handler.HandlerType.ACCOUNT,
            Handler.HandlerType.HAT,
            Handler.HandlerType.DISK,
            Handler.HandlerType.TALK_UNIT,
            Handler.HandlerType.TEAM,
            Handler.HandlerType.GROUP
    };

    public static Map<String,Object> settingMap = new HashMap<>();
    
    static {
//        settingMap.put(Settings.FORBID_FREE_API, false);
        settingMap.put(Settings.WINDOW_TIME, DEFAULT_WINDOW_TIME);
        settingMap.put(Settings.DEALER_MIN_BALANCE,DEFAULT_DEALER_MIN_BALANCE);
//        settingMap.put(Settings.LISTEN_PATH, System.getProperty(UserHome) + "/fc_data/blocks");
//        settingMap.put(Settings.FROM_WEBHOOK, true);
    }

    

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        settings = Starter.startServer(serverType, settingMap, Arrays.stream(TalkServer.chargeType).toList(), modules, null, br);
        if(settings==null)return;

        byte[] symKey = settings.getSymKey();
//        Configure configure = settings.getConfig();

        service = settings.getService();
        sid = service.getId();
        params = (TalkParams) service.getParams();
        try {
            price = Double.parseDouble(params.getPricePerKBytes());
        }catch (Exception e){
            price = 0;
        }


        //Prepare API clients
        apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);
        esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        JedisPool jedisPool = (JedisPool) settings.getClient(Service.ServiceType.REDIS);

//        Configure.checkWebConfig(configure.getPasswordName(), sid,configure, settings,symKey, serviceType, jedisPool, br);

        //Check indices in ES
        checkEsIndices(esClient);

        //Check API prices
//        Order.setNPrices(sid, ApiNames.TalkApiList, jedisPool, br,false);

        //Check user balance
//        Counter.checkUserBalance(sid, jedisPool, esClient, br);

        //Check webhooks for new orders.
//        if(settings.getSettingMap().get(Settings.FROM_WEBHOOK).equals(Boolean.TRUE))
//            if (!Order.checkWebhook(ApiNames.NewCashByFids, params, apipClient.getApiAccount(), br, jedisPool)){
//                close();
//                return;
//            }

//        Rewarder.checkRewarderParams(sid, params, jedisPool, br);

//        startCounterThread(symKey, settings,params);

        //Show the main menu
        Menu menu = new Menu();
        menu.setTitle("Talk Manager");
        menu.add("Start server");
        menu.add("Manage the service");
        menu.add("Reset the price multipliers(nPrice)");
        menu.add("Recreate all indices");
        menu.add("Manage the rewards");
        menu.add("Settings");

        while(true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> {
                    TalkServer talkServer = new TalkServer(settings, price);
                    talkServer.start();
                }
                case 2 -> new TalkManager(service, settings.getApiAccount(Service.ServiceType.APIP), br,symKey, TalkParams.class).menu();
                case 3 -> Order.resetNPrices(br, sid, jedisPool);
                case 4 -> recreateAllIndices(esClient, br);
                case 5 -> new RewardManager(sid,params.getDealer(),apipClient,esClient,null, jedisPool, br)
                        .menu(params.getConsumeViaShare(), params.getOrderViaShare());
                case 6 -> settings.setting(br, null);
                case 0 -> {
                    if (counter != null) counter.close();
                    close();
                    System.out.println("Exited, see you again.");
                    System.exit(0);
                }
            }
        }
    }

    @Nullable
    private static byte[] getDealerPriKey(Configure configure, byte[] symKey) {
        byte[] waiterPriKey;
        CryptoDataByte result = new Decryptor().decryptJsonBySymKey(configure.getFidCipherMap().get(settings.getMainFid()), symKey);
        if(result.getCode()!=0 || result.getData()==null){
            System.out.println("Failed to decrypt the waiter's priKey.");
            return null;
        }
        waiterPriKey = result.getData();
        return waiterPriKey;
    }

    private static void startCounterThread(byte[] symKey, Settings settings, Params params) {
        byte[] priKey = Settings.getMainFidPriKey(symKey, settings);
        if(priKey==null){
            System.out.println("Failed to get the priKey of the dealer.");
            return;
        }
        counter = new Counter(settings,priKey, params);
        Thread thread = new Thread(counter);
        thread.start();
    }
    private static void close() throws IOException {
        settings.close();
    }

    private static void recreateAllIndices(ElasticsearchClient esClient,BufferedReader br) {
        if(!Inputer.askIfYes(br,"Recreate the talk data, order, balance, reward indices?"))return;
        EsTools.recreateIndex(Settings.addSidBriefToName(sid,DATA), TalkUnit.MAPPINGS,esClient, br);
        EsTools.recreateIndex(Settings.addSidBriefToName(sid,ORDER), Order.MAPPINGS,esClient, br);
        EsTools.recreateIndex(Settings.addSidBriefToName(sid, FieldNames.BALANCE), BalanceInfo.MAPPINGS,esClient, br);
        EsTools.recreateIndex(Settings.addSidBriefToName(sid,REWARD), RewardInfo.MAPPINGS,esClient, br);
    }

    private static void checkEsIndices(ElasticsearchClient esClient) {
        Map<String,String> nameMappingList = new HashMap<>();
        nameMappingList.put(Settings.addSidBriefToName(sid,DATA), TalkUnit.MAPPINGS);
        EsTools.checkEsIndices(esClient,nameMappingList);
    }

}
