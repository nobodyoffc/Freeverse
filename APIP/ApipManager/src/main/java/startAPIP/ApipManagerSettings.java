package startAPIP;

import appTools.Menu;
import clients.apipClient.ApipClient;
import clients.redisClient.RedisTools;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import configure.ServiceType;
import configure.Configure;
import constants.ApiNames;
import constants.FieldNames;
import feip.feipData.Service;
import feip.feipData.serviceParams.ApipParams;
import javaTools.JsonTools;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import settings.Settings;
import server.order.Order;

import java.io.BufferedReader;
import java.io.IOException;

import static configure.Configure.saveConfig;
import static constants.Constants.UserDir;
import static constants.FieldNames.SETTINGS;

public class ApipManagerSettings extends Settings {
    public static final ServiceType serviceType = ServiceType.APIP;
    protected Boolean scanMempool = true;
    private String avatarElementsPath = System.getProperty(UserDir)+"/avatar/elements";
    private String avatarPngPath = System.getProperty(UserDir)+"/avatar/png";
    private Boolean ignoreOpReturn;

    public ApipManagerSettings(Configure configure) {
        super(configure);
    }

    public Service initiateServer(String sid, byte[] symKey, Configure config, BufferedReader br) {
        System.out.println("Initiating service settings...");

        setInitForServer(sid, config, br);

        mainFid = config.getServiceDealer(sid,symKey);

        if(shareApiAccount==null)inputShareApiAccount(br);

        esAccount = config.checkAPI(esAccountId, mainFid, ServiceType.ES,symKey, shareApiAccount);//checkApiAccount(esAccountId, ApiType.ES, config, symKey, null);
        if(esAccount.getClient()!=null)esAccountId = esAccount.getId();
        else System.out.println("No ES service.");

        nasaAccount = config.checkAPI(nasaAccountId, mainFid, ServiceType.NASA_RPC,symKey, shareApiAccount);//checkApiAccount(nasaAccountId, ApiType.REDIS, config, symKey, null);
        if(nasaAccount.getClient()!=null)nasaAccountId=nasaAccount.getId();
        else System.out.println("No Nasa node RPC service.");

        redisAccount = config.checkAPI(redisAccountId, mainFid, ServiceType.REDIS,symKey, shareApiAccount);//checkApiAccount(redisAccountId,ApiType.REDIS,config,symKey,null);
        if(redisAccount.getClient()!=null){
            redisAccountId = redisAccount.getId();
            this.jedisPool=(JedisPool) redisAccount.getClient();
        }
        else System.out.println("No Redis service.");

        ElasticsearchClient esClient = (ElasticsearchClient) esAccount.getClient();
        Service service = getMyService(sid, symKey, config, br, esClient, ApipParams.class, ServiceType.APIP);
        if (isWrongServiceType(service, serviceType.name())) return null;

        System.out.println("\nYour service:\n"+ JsonTools.toNiceJson(service));
        Menu.anyKeyToContinue(br);

        writeServiceToRedis(service, ApipParams.class);

        if(listenPath==null)inputListenPath(br);
        fromWebhook=false;
        if(forbidFreeApi==null)inputForbidFreeApi(br);
        if(windowTime==null)inputWindowTime(br);

        saveConfig();
        saveSettings(service.getSid());
        System.out.println("Initiated.\n");
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
        menu.add("Reset fromWebhook switch");
        menu.add("Reset forbidFreeApi switch");
        menu.add("Reset window time");
        menu.add("Reset API prices");
        menu.add("Make web server config file");
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> updateListenPath(br);
                case 2 -> updateFromWebhook(br);
                case 3 -> updateForbidFreeApi(br);
                case 4 -> updateWindowTime(br);
                case 5 -> Order.setNPrices(sid, ApiNames.ApipApiList, (JedisPool) redisAccount.getClient(), br, true);
                case 6 -> Configure.makeWebConfig(sid, config, this,symKey, serviceType, jedisPool);
                case 0 -> {
                    System.out.println("Restart is necessary to active new settings.");
                    return;
                }
            }
            saveSettings(sid);
        }
    }

    @Override
    public void resetApis(byte[] symKey,JedisPool jedisPool,ApipClient apipClient){
        Menu menu = new Menu();
        menu.setName("Reset APIs for APIP manager");
        menu.add("Reset ES");
        menu.add("Reset Redis");
        menu.add("Reset NaSaRPC");
        while (true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> resetApi(symKey, apipClient, ServiceType.ES);
                case 2 -> resetApi(symKey, apipClient, ServiceType.REDIS);
                case 3 -> resetApi(symKey, apipClient, ServiceType.NASA_RPC);
                default -> {
                    return;
                }
            }
        }
    }

    @Override
    public void close() {
        JedisPool jedisPool = (JedisPool)redisAccount.getClient();
        jedisPool.close();
        esAccount.closeEs();
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
//    public static ApipManagerSettings loadMySettings(String sid, BufferedReader br, JedisPool jedisPool){
//        ApipManagerSettings apipManagerSettings = ApipManagerSettings.loadFromFile(sid,ApipManagerSettings.class);
//        if(apipManagerSettings ==null){
//            apipManagerSettings = new ApipManagerSettings();
//            apipManagerSettings.setSid(sid);
//            apipManagerSettings.br = br;
//            apipManagerSettings.inputAll(br);
//            if(jedisPool!=null)apipManagerSettings.saveSettings(sid);
//            return apipManagerSettings;
//        }else {
//            apipManagerSettings.br = br;
//            if (apipManagerSettings.getWindowTime() == 0)
//                apipManagerSettings.setWindowTime(DEFAULT_WINDOW_TIME);
//        }
//        if(jedisPool!=null)apipManagerSettings.saveSettings(jedisPool);
//        apipManagerSettings.setSid(sid);
//        return apipManagerSettings;
//    }
//    public static ApipManagerSettings loadMySettings(String sid,BufferedReader br){
//        System.out.println("Load local settings...");
//        return loadMySettings(sid,br,null);
//    }

    @Override
    public void inputAll(BufferedReader br){
        try {
            listenPath = appTools.Inputer.promptAndSet(br, FieldNames.LISTEN_PATH,this.listenPath);
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
                RedisTools.writeToRedis(this, Settings.addSidBriefToName(sid,SETTINGS), jedis, ApipManagerSettings.class);
            }
        }
    }

    public void saveSettings(JedisPool jedisPool){
        writeToFile(mainFid);
        try (Jedis jedis = jedisPool.getResource()) {
            RedisTools.writeToRedis(this, Settings.addSidBriefToName(sid,SETTINGS), jedis, ApipManagerSettings.class);
        }
    }


    public String getAvatarElementsPath() {
        return avatarElementsPath;
    }

    public void setAvatarElementsPath(String avatarElementsPath) {
        this.avatarElementsPath = avatarElementsPath;
    }

    public String getAvatarPngPath() {
        return avatarPngPath;
    }

    public void setAvatarPngPath(String avatarPngPath) {
        this.avatarPngPath = avatarPngPath;
    }

    public Boolean getForbidFreeApi() {
        return isForbidFreeApi();
    }

    public Boolean getScanMempool() {
        return scanMempool;
    }

    public void setScanMempool(Boolean scanMempool) {
        this.scanMempool = scanMempool;
    }

    public Boolean getIgnoreOpReturn() {
        return ignoreOpReturn;
    }

    public void setIgnoreOpReturn(Boolean ignoreOpReturn) {
        this.ignoreOpReturn = ignoreOpReturn;
    }

}
