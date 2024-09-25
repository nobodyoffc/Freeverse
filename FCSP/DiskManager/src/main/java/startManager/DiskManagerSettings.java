package startManager;

import appTools.Menu;
import appTools.Shower;
import clients.apipClient.ApipClient;
import clients.redisClient.RedisTools;
import configure.ServiceType;
import configure.Configure;
import constants.ApiNames;
import constants.FieldNames;
import feip.feipData.Service;
import feip.feipData.serviceParams.DiskParams;
import javaTools.JsonTools;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import settings.Settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import static configure.Configure.saveConfig;
import static constants.FieldNames.SETTINGS;

public class DiskManagerSettings extends Settings {
    public static final int DEFAULT_WINDOW_TIME = 1000 * 60 * 5;
    public static final ServiceType serviceType = ServiceType.DISK;
    private String localDataPath;

    public DiskManagerSettings(Configure configure) {
        super(configure);
    }

    public Service initiateServer(String sid, byte[] symKey, Configure config, BufferedReader br) {
        System.out.println("Initiating service settings...");
        setInitForServer(sid, config, br);

        mainFid = config.getServiceDealer(sid,symKey);

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
        Service service = getMyService(sid, symKey, config, br, apipClient, DiskParams.class, ServiceType.DISK);
        if (isWrongServiceType(service, serviceType.name())) return null;

        writeServiceToRedis(service, DiskParams.class);

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
        menu.setName("Settings of Disk Manager");
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
        menu.setName("Reset APIs for Disk manager");
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
//    public Object resetDefaultApi(byte[] symKey, ApiType apiType) {
//        System.out.println("Reset API service...");
//        ApiProvider apiProvider = config.chooseApiProviderOrAdd();
//        ApiAccount apiAccount = config.chooseApiProvidersAccount(apiProvider, symKey);
//        Object client = null;
//        if (apiAccount != null) {
//            client = apiAccount.connectApi(apiProvider, symKey, br, null);
//            if (client != null) {
//                switch (apiType) {
//                    case APIP -> apipAccountId=apiAccount.getId();
//                    case ES -> esAccountId=apiAccount.getId();
//                    case REDIS -> redisAccountId=apiAccount.getId();
//                    default -> {
//                        return client;
//                    }
//                }
//                System.out.println("Done.");
//            } else System.out.println("Failed to connect the apiAccount: " + apiAccount.getApiUrl());
//        } else System.out.println("Failed to get the apiAccount.");
//        return client;
//    }
//
//    public static DiskManagerSettings loadMySettings(String sid, BufferedReader br, JedisPool jedisPool){
//        DiskManagerSettings diskManagerSettings = DiskManagerSettings.loadFromFile(sid,DiskManagerSettings.class);
//        if(diskManagerSettings ==null){
//            diskManagerSettings = new DiskManagerSettings(null);
//            diskManagerSettings.setSid(sid);
//            diskManagerSettings.br = br;
//            diskManagerSettings.inputAll(br);
//            if(jedisPool!=null)diskManagerSettings.saveSettings(sid);
//            return diskManagerSettings;
//        }else {
//            diskManagerSettings.br = br;
//            diskManagerSettings.updateFromWebhook(br);
//            if (!diskManagerSettings.fromWebhook && diskManagerSettings.getListenPath() == null)
//                diskManagerSettings.inputListenPath(br);
//            if (diskManagerSettings.getWindowTime() == 0) diskManagerSettings.setWindowTime(DEFAULT_WINDOW_TIME);
//        }
//        if(jedisPool!=null)diskManagerSettings.saveSettings(jedisPool);
//        diskManagerSettings.setSid(sid);
//        return diskManagerSettings;
//    }
//    public static DiskManagerSettings loadMySettings(String sid,BufferedReader br){
//        System.out.println("Load local settings...");
//        return loadMySettings(sid,br,null);
//    }

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

    public void inputLocalDataPath(BufferedReader br){
        while(true) {
            try {
                localDataPath = appTools.Inputer.promptAndSet(br, FieldNames.LOCAL_DATA_PATH, this.localDataPath);
                if(localDataPath!=null){
                    if(new File(localDataPath).exists())return;
                }
                System.out.println("A local data path is necessary to wake up the order scanning.");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
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
                RedisTools.writeToRedis(this, Settings.addSidBriefToName(sid,SETTINGS), jedis, DiskManagerSettings.class);
            }
        }
    }

    public void saveSettings(JedisPool jedisPool){
        writeToFile(mainFid);
        try (Jedis jedis = jedisPool.getResource()) {
            RedisTools.writeToRedis(this, Settings.addSidBriefToName(sid,SETTINGS), jedis, DiskManagerSettings.class);
        }
    }
//    private void inputForbidFreeApi(BufferedReader br) {
//        try {
//            setForbidFreeApi(appTools.Inputer.promptAndSet(br, FieldNames.FORBID_FREE_API, this.isForbidFreeApi()));
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }

//    private void updateForbidFreeApi(BufferedReader br) {
//        try {
//            setForbidFreeApi(appTools.Inputer.promptAndSet(br, FieldNames.FORBID_FREE_API, this.isForbidFreeApi()));
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//        saveSettings(mainFid);
//        System.out.println("It's '"+ isForbidFreeApi() +"' now.");
//        Menu.anyKeyToContinue(br);
//    }

    public String getRedisAccountId() {
        return redisAccountId;
    }

    public void setRedisAccountId(String redisAccountId) {
        this.redisAccountId = redisAccountId;
    }

    public String getEsAccountId() {
        return esAccountId;
    }

    public void setEsAccountId(String esAccountId) {
        this.esAccountId = esAccountId;
    }

    public String getApipAccountId() {
        return apipAccountId;
    }

    public void setApipAccountId(String apipAccountId) {
        this.apipAccountId = apipAccountId;
    }

    public Long getWindowTime() {
        return windowTime;
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
