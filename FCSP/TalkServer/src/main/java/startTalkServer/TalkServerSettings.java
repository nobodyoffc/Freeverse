package startTalkServer;

import appTools.Menu;
import clients.apipClient.ApipClient;
import clients.redisClient.RedisTools;
import configure.ServiceType;
import configure.Configure;
import constants.FieldNames;
import feip.feipData.Service;
import feip.feipData.serviceParams.TalkParams;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.Settings;
import server.serviceManagers.TalkManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import static constants.FieldNames.SETTINGS;

public class TalkServerSettings extends Settings {
    public static final int DEFAULT_WINDOW_TIME = 1000 * 60 * 5;

    private String localDataPath;

    public TalkServerSettings(Configure configure) {
        super(configure);
    }

    public Service initiateServer(String sid, byte[] symKey, Configure config, BufferedReader br) {
        System.out.println("Initiating service settings...");
        setInitForServer(sid, config, br);

        apipAccount = config.checkAPI(apipAccountId, mainFid, ServiceType.APIP,symKey);//checkApiAccount(apipAccountId,ApiType.APIP, config, symKey, null);
        if(apipAccount!=null)apipAccountId=apipAccount.getId();
        else System.out.println("No APIP service.");

        esAccount =  config.checkAPI(esAccountId, mainFid, ServiceType.ES,symKey);//checkApiAccount(esAccountId, ApiType.ES, config, symKey, null);
        if(esAccount!=null)esAccountId = esAccount.getId();
        else System.out.println("No ES service.");

        redisAccount =  config.checkAPI(redisAccountId, mainFid, ServiceType.REDIS,symKey);//checkApiAccount(redisAccountId,ApiType.REDIS,config,symKey,null);
        if(redisAccount!=null)redisAccountId = redisAccount.getId();
        else System.out.println("No Redis service.");

        ApipClient apipClient = (ApipClient) apipAccount.getClient();
        Service service = getMyService(sid, symKey, config, br, apipClient, TalkParams.class, ServiceType.TALK);

        if(service==null){
            new TalkManager(null,apipAccount,br,symKey, TalkParams.class).publishService();
            return null;
        }

        writeServiceToRedis(service,(JedisPool)redisAccount.getClient(), TalkParams.class);

        if(forbidFreeApi ==null)inputForbidFreeApi(br);
        if(windowTime==null)inputWindowTime(br);
        if(localDataPath==null)localDataPath=getLocalDataDir(this.sid);
        if(fromWebhook==null)inputFromWebhook(br);
        if(listenPath==null)checkListenPath(br);//listenPath=System.getProperty(UserDir) +"/"+ Settings.addSidBriefToName(service.getSid(),NewCashByFids)+"/";

        checkIfMainFidIsApiAccountUser(symKey,config,br,apipAccount, mainFid);

        apipAccountId = apipAccount.getId();
        config.saveConfig();
        saveSettings(service.getSid());
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
//        menu.add("Reset listenPath");
//        menu.add("Reset fromWebhook switch");
        menu.add("Reset forbidFreeApi switch");
        menu.add("Reset window time");
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> updateForbidFreeApi(br);
                case 2 -> updateWindowTime(br);
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
        menu.add("Reset DISK");
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> resetApi(symKey, apipClient, ServiceType.APIP);
                case 2 -> resetApi(symKey, apipClient, ServiceType.ES);
                case 3 -> resetApi(symKey, apipClient, ServiceType.REDIS);
                case 4 -> resetApi(symKey, apipClient, ServiceType.DISK);
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
                listenPath = makeWebhookNewCashListListenPath();
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
            JedisPool jedisPool = (JedisPool) redisAccount.getClient();
            try (Jedis jedis = jedisPool.getResource()) {
                RedisTools.writeToRedis(this, Settings.addSidBriefToName(sid,SETTINGS), jedis, TalkServerSettings.class);
            }
        }
    }

    public void saveSettings(JedisPool jedisPool){
        writeToFile(mainFid);
        try (Jedis jedis = jedisPool.getResource()) {
            RedisTools.writeToRedis(this, Settings.addSidBriefToName(sid,SETTINGS), jedis, TalkServerSettings.class);
        }
    }

    public void setWindowTime(long windowTime) {
        this.windowTime = windowTime;
    }

    public String getLocalDataPath() {
        return localDataPath;
    }

    public void setLocalDataPath(String localDataPath) {
        this.localDataPath = localDataPath;
    }
}
