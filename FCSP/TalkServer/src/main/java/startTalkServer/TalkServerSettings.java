package startTalkServer;

import appTools.Menu;
import appTools.Shower;
import clients.apipClient.ApipClient;
import clients.redisClient.RedisTools;
import configure.Configure;
import configure.ServiceType;
import constants.ApiNames;
import constants.FieldNames;
import feip.feipData.Service;
import feip.feipData.serviceParams.TalkParams;
import javaTools.JsonTools;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.Settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import static configure.Configure.saveConfig;
import static constants.FieldNames.SETTINGS;

public class TalkServerSettings extends Settings {
    public static final int DEFAULT_WINDOW_TIME = 1000 * 60 * 5;
    public static final ServiceType serviceType = ServiceType.TALK;
    private String localDataPath;

    public TalkServerSettings(Configure configure) {
        super(configure);
    }

    public Service initiateServer(String sid, byte[] symKey, Configure config, BufferedReader br) {
        System.out.println("Initiating service settings...");
        setInitForServer(sid, config, br);

        mainFid = config.getServiceAccount(sid,symKey);

        if(shareApiAccount==null)inputShareApiAccount(br);

        apipAccount = config.checkAPI(apipAccountId, mainFid, ServiceType.APIP,symKey, shareApiAccount);//checkApiAccount(apipAccountId,ApiType.APIP, config, symKey, null);
        if(apipAccount!=null)apipAccountId=apipAccount.getId();
        else System.out.println("No APIP service.");

        esAccount =  config.checkAPI(esAccountId, mainFid, ServiceType.ES,symKey, shareApiAccount);//checkApiAccount(esAccountId, ApiType.ES, config, symKey, null);
        if(esAccount!=null)esAccountId = esAccount.getId();
        else System.out.println("No ES service.");

        redisAccount =  config.checkAPI(redisAccountId, mainFid, ServiceType.REDIS,symKey, shareApiAccount);//checkApiAccount(redisAccountId,ApiType.REDIS,config,symKey,null);
        if(redisAccount!=null)redisAccountId = redisAccount.getId();
        else System.out.println("No Redis service.");
        jedisPool = (JedisPool) redisAccount.getClient();

        ApipClient apipClient = (ApipClient) apipAccount.getClient();
        Service service = getMyService(sid, symKey, config, br, apipClient, TalkParams.class, ServiceType.TALK);
        if (isWrongServiceType(service, serviceType.name())) return null;

        writeServiceToRedis(service, TalkParams.class);

        if(forbidFreeApi ==null)inputForbidFreeApi(br);
        if(windowTime==null)inputWindowTime(br);
        if(localDataPath==null)localDataPath=getLocalDataDir(this.sid);
        if(fromWebhook==null)fromWebhook=true;//inputFromWebhook(br);
        if(listenPath==null)checkListenPath(br, ApiNames.NewCashByFids );//listenPath=System.getProperty(UserDir) +"/"+ Settings.addSidBriefToName(service.getSid(),NewCashByFids)+"/";

        checkIfMainFidIsApiAccountUser(symKey,config,br,apipAccount, mainFid);

        apipAccountId = apipAccount.getId();
        saveConfig();
        saveSettings(service.getSid());
        System.out.println("Check your service:");
        System.out.println(JsonTools.toNiceJson(service));
        Shower.printUnderline(20);
        System.out.println("Initiated.");
        return service;
    }

    @Override
    public String initiateClient(String fid, byte[] symKey, Configure config, BufferedReader br) {
        return null;
    }

    @Override
    public void resetLocalSettings(byte[] symKey) {
        Menu menu = new Menu();
        menu.setName("Settings of Talk Manager");
        menu.add("Reset listenPath");
        menu.add("Reset fromWebhook");
        menu.add("Reset forbidFreeApi");
        menu.add("Reset window time");
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> updateListenPath(br);
                case 2 -> updateFromWebhook(br);
                case 3 -> updateForbidFreeApi(br);
                case 4 -> updateWindowTime(br);
                case 0 -> {
                    System.out.println("Restart is necessary to active new settings.");
                    return;
                }
            }
        }
    }

    @Override
    public void resetApis(byte[] symKey,JedisPool jedisPool,ApipClient apipClient){
        Menu menu = new Menu();
        menu.setName("Reset APIs for Talk manager");
        menu.add("Reset APIP");
        menu.add("Reset ES");
        menu.add("Reset Redis");
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> resetApi(symKey, apipClient, ServiceType.APIP);
                case 2 -> resetApi(symKey, apipClient, ServiceType.ES);
                case 3 -> resetApi(symKey, apipClient, ServiceType.REDIS);
                default -> {
                    return;
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        if(redisAccount!=null){
            JedisPool jedisPool = (JedisPool)redisAccount.getClient();
            jedisPool.close();
        }
        if(esAccount!=null)esAccount.closeEs();
        br.close();
    }


    public void inputListenPath(BufferedReader br){
        try {
            while(true) {
                listenPath = appTools.Inputer.promptAndSet(br, FieldNames.LISTEN_PATH, this.listenPath);
                if (new File(listenPath).exists()) return;
                System.out.println("A listenPath is necessary to wake up the order scanning. \nGenerally it can be set to the blocks path.");
            }
        } catch (IOException e) {
            System.out.println("Failed to input listenPath.");
        }
    }

    @Override
    public void inputAll(BufferedReader br){
        try {
            fromWebhook = appTools.Inputer.promptAndSet(br, FieldNames.FROM_WEBHOOK,this.fromWebhook);
            if(fromWebhook){
                listenPath = makeWebhookListenPath(sid, ApiNames.NewCashByFids);
            }else listenPath = appTools.Inputer.promptAndSet(br, FieldNames.LISTEN_PATH,this.listenPath);
            inputForbidFreeApi(br);
            inputWindowTime(br);
            saveSettings(mainFid);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateAll(BufferedReader br){
        updateFromWebhook(br);
        if(!fromWebhook) updateListenPath(br);
        updateForbidFreeApi(br);
        updateWindowTime(br);
    }

    @Override
    public void saveSettings(String id){
        writeToFile(id);
        if(redisAccount!=null) {
            try (Jedis jedis = jedisPool.getResource()) {
                RedisTools.writeToRedis(this, Settings.addSidBriefToName(sid,SETTINGS), jedis, TalkServerSettings.class);
            }
        }
    }
}
