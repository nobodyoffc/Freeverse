package startTalkServer;

import appTools.Inputer;
import appTools.Menu;
import clients.apipClient.ApipClient;
import clients.esClient.EsTools;
import fcData.TransferUnit;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import configure.Configure;
import configure.ServiceType;
import constants.ApiNames;
import crypto.CryptoDataByte;
import crypto.Decryptor;
import feip.feipData.Service;
import feip.feipData.serviceParams.Params;
import feip.feipData.serviceParams.TalkParams;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.JedisPool;
import server.Counter;
import settings.Settings;
import server.balance.BalanceInfo;
import server.order.Order;
import server.reward.RewardInfo;
import server.reward.RewardManager;
import server.reward.Rewarder;
import server.serviceManagers.TalkManager;
import settings.TalkServerSettings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import static constants.Strings.*;
import static feip.feipData.Service.makeServiceDataDir;

public class StartTalkServer {
    public static String STORAGE_DIR;
    private static TalkServerSettings settings;
    public static ElasticsearchClient esClient;
    public static ApipClient apipClient;
    public static Service service;
    public static TalkParams params;
    public static Counter counter;
    public static String sid;
    public static final ServiceType serviceType = ServiceType.TALK;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        //Load config info from the file
        Configure.loadConfig(br);
        Configure configure = Configure.checkPassword(br);
        byte[] symKey = configure.getSymKey();


        while(true) {
            sid = configure.chooseSid(serviceType);
            //Load the local settings from the file of localSettings.json
            settings = TalkServerSettings.loadFromFile(sid, TalkServerSettings.class);
            if(settings==null) settings = new TalkServerSettings(configure);

            service = settings.initiateServer(sid,symKey,configure, br);

            if(service!=null)break;
            System.out.println("Try again.");
        }

        sid = service.getSid();
        STORAGE_DIR = makeServiceDataDir(sid, ServiceType.TALK);
        params = (TalkParams) service.getParams();

        //Prepare API clients
        apipClient = (ApipClient) settings.getApipAccount().getClient();
        esClient = (ElasticsearchClient) settings.getEsAccount().getClient();
        JedisPool jedisPool = (JedisPool) settings.getRedisAccount().getClient();

//        Configure.checkWebConfig(configure.getPasswordName(), sid,configure, settings,symKey, serviceType, jedisPool, br);

        //Check indices in ES
        checkEsIndices(esClient);

        //Check API prices
        Order.setNPrices(sid, ApiNames.TalkApiList, jedisPool, br,false);

        //Check user balance
        Counter.checkUserBalance(sid, jedisPool, esClient, br);

        //Check webhooks for new orders.
        if(settings.getFromWebhook()!=null && settings.getFromWebhook().equals(Boolean.TRUE))
            if (!Order.checkWebhook(ApiNames.NewCashByFids, params, apipClient.getApiAccount(), br, jedisPool)){
                close();
                return;
            }

        Rewarder.checkRewarderParams(sid, params, jedisPool, br);

        startCounterThread(symKey, settings,params);

        byte[] waiterPriKey;
        waiterPriKey = getWaiterPriKey(configure, symKey);
        if (waiterPriKey == null) return;

        //Show the main menu
        Menu menu = new Menu();
        menu.setName("Talk Manager");
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
                    TalkUdpServer talkServer = new TalkUdpServer(settings, service, waiterPriKey, apipClient, jedisPool);
                    talkServer.start();
                }
                case 2 -> new TalkManager(service, settings.getApipAccount(), br,symKey, TalkParams.class).menu();
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
    private static byte[] getWaiterPriKey(Configure configure, byte[] symKey) {
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
        counter = new Counter(settings,priKey, params);
        Thread thread = new Thread(counter);
        thread.start();
    }
    private static void close() throws IOException {
        settings.close();
    }

    private static void recreateAllIndices(ElasticsearchClient esClient,BufferedReader br) {
        if(!Inputer.askIfYes(br,"Recreate the talk data, order, balance, reward indices?"))return;
        EsTools.recreateIndex(Settings.addSidBriefToName(sid,DATA), TransferUnit.MAPPINGS,esClient, br);
        EsTools.recreateIndex(Settings.addSidBriefToName(sid,ORDER), Order.MAPPINGS,esClient, br);
        EsTools.recreateIndex(Settings.addSidBriefToName(sid,BALANCE), BalanceInfo.MAPPINGS,esClient, br);
        EsTools.recreateIndex(Settings.addSidBriefToName(sid,REWARD), RewardInfo.MAPPINGS,esClient, br);
    }

    private static void checkEsIndices(ElasticsearchClient esClient) {
        Map<String,String> nameMappingList = new HashMap<>();
        nameMappingList.put(Settings.addSidBriefToName(sid,ORDER), Order.MAPPINGS);
        nameMappingList.put(Settings.addSidBriefToName(sid,BALANCE), BalanceInfo.MAPPINGS);
        nameMappingList.put(Settings.addSidBriefToName(sid,REWARD), RewardInfo.MAPPINGS);
        nameMappingList.put(Settings.addSidBriefToName(sid,DATA), TransferUnit.MAPPINGS);
        EsTools.checkEsIndices(esClient,nameMappingList);
    }

}
