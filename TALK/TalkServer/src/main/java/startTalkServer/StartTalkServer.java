package startTalkServer;

import appTools.Inputer;
import appTools.Menu;
import appTools.Settings;
import appTools.Starter;
import clients.apipClient.ApipClient;
import clients.esClient.EsTools;
import fcData.TalkUnit;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import configure.Configure;
import configure.ServiceType;
import constants.ApiNames;
import crypto.CryptoDataByte;
import crypto.Decryptor;
import fch.ParseTools;
import feip.feipData.Service;
import feip.feipData.serviceParams.Params;
import feip.feipData.serviceParams.TalkParams;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.JedisPool;
import server.Counter;
import server.balance.BalanceInfo;
import server.order.Order;
import server.reward.RewardInfo;
import server.reward.RewardManager;
import server.reward.Rewarder;
import server.serviceManagers.TalkManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import static appTools.Settings.DEFAULT_WINDOW_TIME;
import static constants.Constants.UserHome;
import static constants.Strings.*;

public class StartTalkServer {
    public static String STORAGE_DIR;
    private static Settings settings;
    public static ElasticsearchClient esClient;
    public static ApipClient apipClient;
    public static Service service;
    public static TalkParams params;
    public static Counter counter;
    public static String sid;
    public static long price;
    public static final ServiceType serverType = ServiceType.TALK;

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
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        settings = Starter.startServer(serverType, serviceAliases,settingMap, br);
        if(settings==null)return;

        byte[] symKey = settings.getSymKey();
        Configure configure = settings.getConfig();

        service = settings.getService();
        sid = service.getSid();
        params = (TalkParams) service.getParams();
        try {
            price = ParseTools.coinToSatoshi(Double.parseDouble(params.getPricePerKBytes()));
        }catch (Exception e){
            price = 0;
        }


        //Prepare API clients
        apipClient = (ApipClient) settings.getClient(ServiceType.APIP);
        esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);
        JedisPool jedisPool = (JedisPool) settings.getClient(ServiceType.REDIS);

//        Configure.checkWebConfig(configure.getPasswordName(), sid,configure, settings,symKey, serviceType, jedisPool, br);

        //Check indices in ES
        checkEsIndices(esClient);

        //Check API prices
        Order.setNPrices(sid, ApiNames.TalkApiList, jedisPool, br,false);

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

        byte[] dealerPriKey;
        dealerPriKey = getDealerPriKey(configure, symKey);
        if (dealerPriKey == null) return;

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
                    TalkTcpServer talkServer = new TalkTcpServer(settings, service, price,dealerPriKey);
                    talkServer.start();
                }
                case 2 -> new TalkManager(service, settings.getApiAccount(ServiceType.APIP), br,symKey, TalkParams.class).menu();
                case 3 -> Order.resetNPrices(br, sid, jedisPool);
                case 4 -> recreateAllIndices(esClient, br);
                case 5 -> new RewardManager(sid,params.getDealer(),apipClient,esClient,null, jedisPool, br)
                        .menu(params.getConsumeViaShare(), params.getOrderViaShare());
                case 6 -> settings.setting(symKey, br, null);
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
        EsTools.recreateIndex(Settings.addSidBriefToName(sid,BALANCE), BalanceInfo.MAPPINGS,esClient, br);
        EsTools.recreateIndex(Settings.addSidBriefToName(sid,REWARD), RewardInfo.MAPPINGS,esClient, br);
    }

    private static void checkEsIndices(ElasticsearchClient esClient) {
        Map<String,String> nameMappingList = new HashMap<>();
        nameMappingList.put(Settings.addSidBriefToName(sid,ORDER), Order.MAPPINGS);
        nameMappingList.put(Settings.addSidBriefToName(sid,BALANCE), BalanceInfo.MAPPINGS);
        nameMappingList.put(Settings.addSidBriefToName(sid,REWARD), RewardInfo.MAPPINGS);
        nameMappingList.put(Settings.addSidBriefToName(sid,DATA), TalkUnit.MAPPINGS);
        EsTools.checkEsIndices(esClient,nameMappingList);
    }

}