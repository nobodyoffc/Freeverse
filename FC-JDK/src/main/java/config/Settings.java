package config;

import core.crypto.*;
import data.fcData.CidInfo;
import data.fcData.Module;
import data.feipData.ServiceType;
import fapi.client.FapiClient;
import fapi.service.FapiServer;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import ui.Inputer;
import ui.Menu;
import clients.ApipClient;
import clients.FcClient;
import clients.ClientGroup;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.Constants;
import constants.IndicesNames;
import constants.Strings;
import db.LocalDB;
import data.fcData.AlgorithmId;
import data.fcData.AutoTask;
import data.fchData.Block;
import data.fchData.Freer;
import data.feipData.Service;
import data.feipData.ServiceMask;
import managers.*;
import clients.NaSaClient.NaSaRpcClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.FreeApi;
import server.serviceManagers.ApipManager;
import server.serviceManagers.TalkManager;
import utils.*;
import utils.http.AuthType;
import utils.http.RequestMethod;

import data.apipData.Fcdsl;
import constants.FieldNames;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static ui.Inputer.askIfYes;
import static config.Configure.*;
import static constants.Constants.UserDir;
import static constants.Strings.*;
import static data.feipData.ServiceType.*;
import static server.ApipApi.VER_1;

public class Settings {
    public final static Logger log = LoggerFactory.getLogger(Settings.class);
    public static final String DEFAULT_AVATAR_BASE_PATH = System.getProperty("user.dir") + "/avatar/elements/";
    public static final String DEFAULT_AVATAR_FILE_PATH = System.getProperty("user.dir") + "/avatar/png/";

    public static final String LISTEN_PATH = "listenPath";
    public static final String CREDIT_LIMIT = "creditLimit";
    public static final String DB_DIR = "dbDir";
    public static final String OP_RETURN_PATH = "opReturnPath";
    public static final String FORBID_FREE_API = "forbidFreeApi";
    public static final String FROM_WEBHOOK = "fromWebhook";
    public static final String WINDOW_TIME = "windowTime";
    public static final String DEALER_MIN_BALANCE = "dealerMinBalance";
    public static final String MIN_DISTRIBUTE_BALANCE = "minDistributeBalance";
    public static final String AVATAR_ELEMENTS_PATH = "avatarElementsPath";
    public static final String AVATAR_PNG_PATH = "avatarPngPath";
    // 注意：FAPI 相关的配置键常量和默认值常量已移至各自的类中：
    // - BalanceVerifier 类：余额验证相关配置
    // - AutoRechargeManager 类：自动充值相关配置
    // - FapiBalanceManager 类：信用额度和充值保留天数等配置
    public static final Long DEFAULT_WINDOW_TIME = 300000L;
    public static final String DISK_DATA_PATH = System.getProperty(Constants.UserHome)+"/diskData";

    public static Map<ServiceType,List<FreeApi>> freeApiListMap;
    private static String fileName;
    private transient Configure config;
    private transient BufferedReader br;
    private transient String clientDataFileName;
    private transient JedisPool jedisPool;

    private transient List<ApiAccount> paidAccountList;
    private transient byte[] symkey;
    private transient Map<Manager.ManagerType, Manager<?>> managers;
    private transient Map<String, Object> nodes;
    private transient FudpNode fudpNode;

    private Map<ServiceType, ClientGroup> clientGroups;
    private ServiceType serverType;
    private Map<String,Long> bestHeightMap;
    private Service service;
    private String clientName;

    private boolean bootstrapping; // true while ServiceBootstrap is driving init (suppresses early loadMyService)
    private String sid; //For server
    private String mainFid; //For Client
//    private String master;
    private String myPubkey;
    private String myPrikeyCipher;
//    private String listenPath;

    //Settings
    private Map<String,Object> settingMap;

    private List<Module> modules;
//    private Object[] runningModules;
    private List<String> apiList;
    private String dbDir;
    private LocalDB.DbType localDBType;
    private List<AutoTask> autoTaskList;

    public Settings(Configure configure, String clientName, List<Module> modules, Map<String, Object> settingMap, List<AutoTask> autoTaskList) {
        if(configure!=null) {
            this.config = configure;
            this.br = configure.getBr();
            freeApiListMap = configure.getFreeApiListMap();
            clientGroups = new HashMap<>();
            checkDbDir(null);
            this.clientName = clientName;
            this.modules = modules;
            this.autoTaskList = autoTaskList;
            this.settingMap = settingMap;
            checkSetting(br);
            addShutdownHook();
        }
    }

    public Settings(Configure configure, String clientName, List<Module> modules, Map<String, Object> settingMap) {
        if(configure!=null) {
            this.config = configure;
            this.br = configure.getBr();
            freeApiListMap = configure.getFreeApiListMap();
            clientGroups = new HashMap<>();
            checkDbDir(null);
            this.clientName = clientName;
            this.modules = modules;
            this.settingMap = settingMap;
            addShutdownHook();
        }
    }
    public Settings(Configure configure, ServiceType serverType, Map<String,Object> settingMap, List<Module> modules, List<AutoTask> autoTaskList) {
        if(configure!=null) {
            this.config = configure;
            this.br =configure.getBr();
            freeApiListMap = configure.getFreeApiListMap();
            clientGroups = new HashMap<>();
            this.settingMap = settingMap;
            this.serverType = serverType;
            this.modules = modules;
            this.autoTaskList = autoTaskList;
            checkSetting(br);
            checkDbDir(settingMap);
            addShutdownHook();
        }
    }

    public Settings(Configure configure, ServiceType serverType, Map<String,Object> settingMap, List<Module> modules) {
        if(configure!=null) {
            this.config = configure;
            this.br =configure.getBr();
            freeApiListMap = configure.getFreeApiListMap();
            clientGroups = new HashMap<>();
            this.settingMap = settingMap;
            this.serverType = serverType;
            this.modules = modules;
            checkDbDir(settingMap);
            addShutdownHook();
        }
    }

    public Settings(Configure configure, Map<String,Object> settingMap, List<Module> modules) {
        if(configure!=null) {
            this.config = configure;
            this.br = configure.getBr();
            freeApiListMap = configure.getFreeApiListMap();
            clientGroups = new HashMap<>();
            this.settingMap = settingMap;
            this.modules = modules;
            checkDbDir(settingMap);
            addShutdownHook();
        }
    }

    @Nullable
    public static Settings loadSettingsForServer(String configFileName) {
        WebServerConfig webServerConfig;
        Configure configure;
        Settings settings;
        Map<String, Configure> configureMap;
        try {
            webServerConfig = JsonUtils.readJsonFromFile(configFileName,WebServerConfig.class);
            configureMap = JsonUtils.readMapFromJsonFile(null,webServerConfig.getConfigPath(),String.class,Configure.class);
            if(configureMap==null){
                log.error("Failed to read the config file of "+ configFileName +".");
                return null;
            }
            configure = configureMap.get(webServerConfig.getPasswordName());
            settings = JsonUtils.readJsonFromFile(webServerConfig.getSettingPath(), Settings.class);
            settings.setConfig(configure);

        } catch (IOException e) {
            log.error("Failed to read the config file of "+ configFileName +".");
            return null;
        }
        return settings;
    }

    @Nullable
    public static Settings loadSettings(String fid, String name) {
        Settings settings=null;
        String fileName = FileUtils.makeFileName(fid, name, SETTINGS, DOT_JSON);
        try {
            settings = JsonUtils.readObjectFromJsonFile(getConfDir(), fileName, Settings.class);
        }catch (IOException e){
            log.error("Failed to load settings json from file.");
        }
        return settings;
    }

    public Block getBestBlock(){
        ElasticsearchClient esClient = null;
        ApipClient apipClient = null;
        if(getClient(APIP)!=null)
            apipClient = (ApipClient)getClient(APIP);
        if(getClient(ES)!=null)
            esClient = (ElasticsearchClient)getClient(ES);
        return getBestBlock(null,apipClient,esClient,null);
    }


    public Block getBestBlock(JedisPool jedisPool,ApipClient apipClient,ElasticsearchClient esClient,NaSaRpcClient naSaRpcClient) {
        if(getClient(ServiceType.FAPI)!=null){
            return ((FapiClient)getClient(FAPI)).bestBlock();
        }
        if(getClient(ServiceType.FAPI_No1_NrC7)!=null){
            return ((FapiClient)getClient(FAPI_No1_NrC7)).bestBlock();
        }

        if(jedisPool!=null){
            try(Jedis jedis = jedisPool.getResource()) {
                Block block = new Block();
                String bestHeightStr = jedis.get(BEST_HEIGHT);
                long bestHeight = Long.parseLong(bestHeightStr);
                String bestBlockId = jedis.get(BEST_BLOCK_ID);
                block.setId(bestBlockId);
                block.setHeight(bestHeight);
                return block;
            }
        }
        if(naSaRpcClient!=null){
            Block block = new Block();
            naSaRpcClient.freshBestBlock();
            block.setHeight(naSaRpcClient.getBestHeight());
            block.setId(naSaRpcClient.getBestBlockId());
            return block;
        }

        if(apipClient!=null){
            return apipClient.bestBlock(RequestMethod.POST, AuthType.ENCRYPTED);
        }

        if(esClient!=null){
            try {
                return EsUtils.getBestBlock(esClient);
            } catch (IOException ignore) {
                return null;
            }
        }
        return null;
    }

    public Long getBestHeight() {
        if(getClient(FAPI)!=null){
            return ((FapiClient)getClient(FAPI)).bestHeight();
        }
        if(getClient(FAPI_No1_NrC7)!=null){
            return ((FapiClient)getClient(FAPI_No1_NrC7)).bestHeight();
        }

        if(getClient(APIP)!=null){
            return ((ApipClient)getClient(APIP)).getBestHeight();
        }

        if(getClient(NASA_RPC)!=null){
            NaSaRpcClient client = (NaSaRpcClient) getClient(NASA_RPC);
            client.freshBestBlock();
            return client.getBestHeight();
        }

        if(getClient(ES)!=null) {
            Block block = null;
            try {
                block = EsUtils.getBestBlock((ElasticsearchClient) getClient(ES));
            } catch (IOException ignored) {
            }
            if(block!= null) return block.getHeight();
        }
        return null;
    }

    public static Long getBestHeight(ApipClient apipClient,NaSaRpcClient naSaRpcClient, ElasticsearchClient esClient ,JedisPool jedisPool){

        if(naSaRpcClient!=null){
            naSaRpcClient.freshBestBlock();
            return naSaRpcClient.getBestHeight();
        }

        if(apipClient!=null){
            return apipClient.bestHeight();
        }

        if(esClient!=null) {
            Block block = null;
            try {
                block = EsUtils.getBestBlock(esClient);
            } catch (IOException ignored) {
            }
            if(block!= null) return block.getHeight();
        }
        if(jedisPool!=null){
            try(Jedis jedis = jedisPool.getResource()){
                String str =  jedis.get(BEST_HEIGHT);
                if(str!=null)return Long.parseLong(str);
            }
        }
        return null;
    }

    private void checkDbDir(Map<String, Object> settingMap) {
        if(settingMap==null || settingMap.isEmpty() || settingMap.get(DB_DIR)==null)
            this.dbDir = FileUtils.getUserDir()+"/db/";
        FileUtils.checkDirOrMakeIt(this.dbDir);
    }

    public static String makeSettingsFileName(@Nullable String fid,@Nullable String sid){
        return FileUtils.makeFileName(fid, sid, SETTINGS, DOT_JSON);
    }

    public void initiateServer(String sid, byte[] symkey, Configure config, List<String> apiList){
        if(clientGroups==null)clientGroups = new HashMap<>();
        if(this.config==null)this.config = config;
        this.apiList = apiList;
        this.symkey = symkey;

        System.out.println("Initiating server settings...");

        br = config.getBr();

//        if(sid==null)checkSetting(br);

        mainFid = config.getServiceDealer(sid,symkey);
        myPrikeyCipher = config.getMainCidInfoMap().get(mainFid).getPrikeyCipher();

        initModels();

        if(service==null){
            System.out.println("Failed to load service information");
            return;
        }
        Object client = getClient(REDIS);
        if(client!=null) jedisPool = (JedisPool) client;

        setMyKeys(symkey, config);

        saveServerSettings(service.getId());

        runAutoTasks();

        Configure.saveConfig();
    }

    private void setMyKeys(byte[] symkey, Configure config) {
        if(mainFid==null)return;

        Map<String, CidInfo> mainCidInfoMap = config.getMainCidInfoMap();
        if(mainCidInfoMap==null)mainCidInfoMap = new HashMap<>();

        CidInfo cidInfo = mainCidInfoMap.get(mainFid);

        if(cidInfo==null){
            System.out.println("The mainFid is new.");
            ApipClient apipClient = (ApipClient)getClient(ServiceType.APIP);
            if(apipClient!=null) {
                Freer cid = apipClient.freerById(mainFid);
                if(cid!=null){
                    if(askIfYes(br,"Import the prikey of "+mainFid+". Enter to set it watch-only:")){
                        byte[] prikey = core.fch.Inputer.importOrCreatePrikey(br);
                        if(prikey!=null)
                            myPrikeyCipher = Encryptor.encryptBySymkeyToJson(prikey,symkey);
                        this.myPubkey = cid.getPubkey();
                        cidInfo = CidInfo.fromCid(cid, myPrikeyCipher);
                        mainCidInfoMap.put(mainFid,cidInfo);
                        saveConfig();
                        return;
                    }
                }

                cidInfo = new CidInfo();
                cidInfo.setId(mainFid);
                cidInfo.setWatchOnly(true);
                if(askIfYes(br,"Input the pubkey?")){
                    String pubkey = KeyTools.inputPubkey(br);
                    cidInfo.setPubkey(pubkey);
                    myPubkey = pubkey;
                }
                mainCidInfoMap.put(mainFid, cidInfo);
                return;
            }
        }else {
            myPrikeyCipher = cidInfo.getPrikeyCipher();
            myPubkey = cidInfo.getPubkey();
        }
    }

    public void initiateMuteServer(String serverName, byte[] symkey, Configure config){
        if(clientGroups==null)clientGroups = new HashMap<>();
        if(this.config==null)this.config = config;
        this.symkey = symkey;

        System.out.println("Initiating mute server settings...");

        initModels();
        saveServerSettings(serverName);
        runAutoTasks();
        Configure.saveConfig();
    }

    public void initiateClient(String fid, String clientName, byte[] symkey, Configure config, BufferedReader br){
        System.out.println("Initiating Client settings...");
        this.br= br;
        this.symkey = symkey;
        this.mainFid = fid;
        this.myPrikeyCipher = config.getMainCidInfoMap().get(fid).getPrikeyCipher();
        if(this.config==null)this.config = config;
        this.myPubkey = config.getMainCidInfoMap().get(fid).getPubkey();
        if(this.myPubkey==null) this.myPubkey = ApiAccount.makePubkey(myPrikeyCipher,symkey);
        if(clientGroups==null)clientGroups = new HashMap<>();
        freeApiListMap = config.getFreeApiListMap();
        if(bestHeightMap==null)
            bestHeightMap=new HashMap<>();

        initModels();

        clientDataFileName = FileUtils.makeFileName(mainFid,clientName,DATA,DOT_JSON);

        setMyKeys(symkey, config);

        saveClientSettings(mainFid,clientName);
        runAutoTasks();
        Configure.saveConfig();
    }

    public void initiateTool(String toolName, byte[] symkey, Configure config, BufferedReader br){
        System.out.println("Initiating Client settings...");

        this.config = config;
        this.symkey = symkey;

        if(clientGroups==null) clientGroups = new HashMap<>();
        this.br= br;
        freeApiListMap = config.getFreeApiListMap();

        initModels();

        Configure.saveConfig();

        saveToolSettings(toolName);
    }

    public void closeMenu() {
        return;
    }

    public void close() {
        try {
            // Close all handlers first to ensure LevelDB instances are properly closed
            if (managers != null) {
                log.info("Closing {} handlers...", managers.size());
                for (Manager<?> manager : managers.values()) {
                    if (manager != null) {
                        try {
                            log.debug("Closing handler: {}", manager.getHandlerType());
                            manager.close();
                        } catch (Exception e) {
                            log.error("Error closing handler {}: {}", manager.getHandlerType(), e.getMessage(), e);
                        }
                    }
                }
                managers.clear();
            }
            
            // Stop all auto tasks
            if (autoTaskList != null && !autoTaskList.isEmpty()) {
                log.info("Stopping {} auto tasks...", autoTaskList.size());
                AutoTask.stopAllTasks();
            }
            
            // Close all clients in all groups
            if (clientGroups != null) {
                log.info("Closing client groups...");
                for (ClientGroup group : clientGroups.values()) {
                    if(group.getClientMap()==null)continue;
                    for (Object client : group.getClientMap().values()) {
                        try {
                            switch (group.getGroupType()) {
                                case REDIS -> {
                                    if(client instanceof JedisPool jedisPool){
                                        log.debug("Closing Redis pool");
                                        jedisPool.close();
                                    }
                                }
                                case ES -> {
                                    ApiAccount account = getApiAccount(valueOf(group.getGroupType().name()));
                                    if (account != null) {
                                        log.debug("Closing ES client");
                                        account.closeEs();
                                    }
                                }
                                case NASA_RPC -> {
                                    // Nothing to close
                                }
                                default -> {
                                    if(client instanceof FcClient fcClient1){
                                        log.debug("Closing client: {}", fcClient1.getClass().getSimpleName());
                                        fcClient1.close();
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error("Error closing client in group {}: {}", group.getGroupType(), e.getMessage(), e);
                        }
                    }
                }
            }
            if (fudpNode != null) {
                try {
                    log.info("Stopping FUDP node...");
                    fudpNode.stop();
                } catch (Exception e) {
                    log.error("Error stopping FUDP node", e);
                }
            }
            
            // Clear sensitive data
            BytesUtils.clearByteArray(symkey);
            
            log.info("Settings closed successfully");
        } catch (Exception e) {
            log.error("Error during settings cleanup: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to close settings", e);
        }
    }

    @Nullable
    public Service loadMyService(String sid, byte[] symkey, Configure config) {
        ApipClient apipClient = (ApipClient) getClient(APIP);
        if(apipClient!=null) {
            service = getMyService(sid, symkey, config, br, apipClient, this.serverType);
        }else {
            ElasticsearchClient esClient = (ElasticsearchClient) getClient(ES);
            service = getMyService(sid, symkey, config, br, esClient, this.serverType);
        }

        if(service==null){
            System.out.println("Failed to get service.");
            return null;
        }

        System.out.println("\nYour service:\n"+ JsonUtils.toNiceJson(service));
        Menu.anyKeyToContinue(br);

        if(jedisPool!=null)writeServiceToRedis(service);
        this.sid = service.getId();
        return service;
    }

    public Object getClient(ServiceType serviceType) {
        ClientGroup group = clientGroups.get(ServiceType.valueOf(serviceType.name()));
        return group != null ? group.getClient() : null;
    }

    public ApiAccount getApiAccount(ServiceType serviceType) {
        ClientGroup group = clientGroups.get(ServiceType.valueOf(serviceType.name()));
        if (group != null && !group.getAccountIds().isEmpty()) {
            String accountId = group.getAccountIds().get(0);
            return config.getApiAccountMap().get(accountId);
        }
        return null;
    }

    public String getApiAccountId(ServiceType serviceType) {
        ClientGroup group = clientGroups.get(ServiceType.valueOf(serviceType.name()));
        if (group != null && !group.getAccountIds().isEmpty()) {
            return group.getAccountIds().get(0); // Returns first account ID by default
        }
        return null;
    }


    public static ApipClient getDefaultApipClient(){
        return getDefaultApipClient(null);
    }
    public static ApipClient getDefaultApipClient(BufferedReader br){
        ApipClient apipClient = new ApipClient();
        ApiAccount apipAccount = new ApiAccount();

        List<FreeApi> freeApiList = freeApiListMap.get(APIP);

        for(FreeApi freeApi : freeApiList){
            apipAccount.setApiUrl(freeApi.getUrlHead());
            apipClient.setApiAccount(apipAccount);
            apipClient.setUrlHead(freeApi.getUrlHead());
            try {
                if ((boolean) apipClient.ping(VER_1, RequestMethod.GET, AuthType.FREE, APIP))
                    return apipClient;
            }catch (Exception e){
                log.debug("Failed to get default apipClient: "+e.getMessage());
            }
        }
        if(br !=null) {
            if (askIfYes(br, "Failed to get free APIP service. Add new?")) {
                do {
                    String url = core.fch.Inputer.inputString(br, "Input the urlHead of the APIP service:");
                    apipAccount.setApiUrl(url);
                    apipClient.setApiAccount(apipAccount);
                    if ((boolean) apipClient.ping(VER_1, RequestMethod.GET,AuthType.FREE, APIP)) {
                        FreeApi freeApi = new FreeApi(url,true, APIP);
                        freeApiList.add(freeApi);
                        return apipClient;
                    }
                } while (askIfYes(br, "Failed to ping this APIP Service. Try more?"));
            }
        }
        return null;
    }

    public static FapiClient getDefaultFapiClient(Settings settings) {
        FudpNode fudpNode = settings.getFudpNode();
        if (fudpNode == null) {
            settings.initNode("FUDP");
        }
        if (fudpNode == null) return null;
        List<FapiClient.Endpoint> endpoints = FapiClient.loadDefaultEndpoints();
        for (FapiClient.Endpoint endpoint : endpoints) {
            try {
                FapiClient.DiscoveryResult discovery = FapiClient.discoverViaHelloAndPing(
                        fudpNode,
                        endpoint.host(),
                        endpoint.port(),
                        FapiClient.DEFAULT_HELLO_TIMEOUT_MS,
                        FapiClient.DEFAULT_PING_TIMEOUT_MS
                );
                if (discovery.getServices() == null || discovery.getServices().isEmpty()) {
                    continue;
                }
                Service target = discovery.getServices().stream()
                        .filter(s -> ServiceType.isFapi(s.fetchServiceType()))
                        .findFirst()
                        .orElse(discovery.getServices().get(0));
                return new FapiClient(fudpNode, discovery.getPeerId(), target.getId(), 30, settings);
            } catch (Throwable e) {
                log.warn("Failed to connect default FAPI_No1_NrC7 {}:{} ({})", endpoint.host(), endpoint.port(), e.getMessage());
            }
        }
        return null;
    }


    public static String getLocalDataDir(String sid){
        return System.getProperty("user.dir")+"/"+ addSidBriefToName(sid,DATA)+"/";
    }

    public static String addSidBriefToName(String sid, String name) {
        return IdNameUtils.makeKeyName(null,sid,name,true);
    }

    public static void setNPrices(String sid, String[] ApiNames, Jedis jedis, BufferedReader br) {
        for(String api : ApiNames){
            String ask = "Set the price multiplier for " + api + "? y/n. Enter to leave default 1:";
            int input = Inputer.inputInt(br, ask,0);
            if(input==0)input=1;
            jedis.hset(addSidBriefToName(sid,Strings.N_PRICE),api, String.valueOf(input));
        }
    }

    public static byte[] getMainFidPrikey(byte[] symkey, Settings settings) {
        Decryptor decryptor = new Decryptor();
        String mainFid = settings.getMainFid();
        String cipher = settings.getConfig().getMainCidInfoMap().get(mainFid).getPrikeyCipher();
        if(cipher==null)return null;
        CryptoDataByte result = decryptor.decryptJsonBySymkey(cipher, symkey);
        if(result.getCode()!=0){
            System.out.println("Failed to decrypt the private key of "+mainFid+".");
            return null;
        }
        return result.getData();
    }

    public Freer checkFidInfo(ApipClient apipClient, BufferedReader br) {
        Freer fidInfo = apipClient.freerById(mainFid);

        if(fidInfo!=null) {
            long bestHeight = apipClient.getFcClientEvent().getResponseBody().getBestHeight();

            bestHeightMap.put(IndicesNames.FREER,bestHeight);
            System.out.println("My information:\n" + JsonUtils.toNiceJson(fidInfo));
            Menu.anyKeyToContinue(br);
            if(fidInfo.getBalance()==0){
                System.out.println("No fch yet. Send some fch to "+mainFid);
                Menu.anyKeyToContinue(br);
            }
        }else{
            System.out.println("New FID. Send some fch to it: "+mainFid);
            Menu.anyKeyToContinue(br);
        }
        return fidInfo;
    }

    public void initClientGroup(ServiceType groupType) {
        System.out.println("Initiate "+ groupType +" accounts and clients...");

        // Preserve existing group if it exists
        ClientGroup group;
        if(clientGroups==null) clientGroups = new HashMap<>();
        group = clientGroups.get(groupType);
        if(group!=null){
            group.connectAllClients(config, this,symkey, br);
            return;
        }

        group = new ClientGroup(groupType);

        do {
            FapiClient fapiClient = null;
            ApipClient apipClient = null;

            switch (groupType) {
                case ES, REDIS, NASA_RPC,FAPI,FAPI_No1_NrC7 -> {
                }
                default -> {
                    apipClient = getDefaultApipClient();

                    if(apipClient ==null ){
                        System.out.println("Failed to get default FapiClient and ApipClient");
                        return;
                    }
                }
            }

            ApiAccount apiAccount = config.getApiAccount(symkey, mainFid, groupType, apipClient, fapiClient);
            if (apiAccount == null) {
                System.out.println(groupType + " module is ignored.");
                return;
            }
            group.addApiAccount(apiAccount);
            group.getAccountIds().add(apiAccount.getId());
            if (apiAccount.getClient() != null) group.getClientMap().put(apiAccount.getId(), apiAccount.getClient());
        } while (br == null || askIfYes(br, "\nAdd more " + groupType + " account?"));

        if(group.getAccountIds().size()>1){
            ClientGroup.GroupStrategy strategy = Inputer.chooseOne(ClientGroup.GroupStrategy.values(),null,"Chose the strategy",br);
            group.setStrategy(strategy);
        }
        clientGroups.put(groupType, group);
        if(groupType == REDIS)jedisPool = (JedisPool) group.getClient();
        if(sid==null && serverType!=null && !bootstrapping) {
            if(groupType == APIP || groupType == ES && (this.serverType == APIP || ServiceType.isFapi(this.serverType)))
                loadMyService(null, symkey, config);
        }
    }

    public Service getMyService(String sid, byte[] symkey, Configure config, BufferedReader br, ApipClient apipClient, ServiceType serviceType) {
        return getMyService(sid, symkey, config, br, apipClient,null, serviceType);
    }
    public Service getMyService(String sid, byte[] symkey, Configure config, BufferedReader br, ElasticsearchClient esClient, ServiceType serviceType) {
        return getMyService(sid, symkey, config, br, null,esClient, serviceType);
    }
    public Service getMyService(String sid, byte[] symkey, Configure config, BufferedReader br, ApipClient apipClient, ElasticsearchClient esClient, ServiceType serviceType) {
        System.out.println("Get my service...");
        Service service = null;
        if(sid ==null) {
            if(mainFid!=null)
                service = config.chooseDealerService(mainFid, symkey, serviceType, esClient, apipClient);
            else System.out.println("Dealer is not set. Please set mainFid first.");
        }else {
            service = getServiceBySid(sid, apipClient, esClient, service);
        }

        if(service==null){
            service = askIfPublishNewService(sid, symkey, br, serviceType, apipClient, esClient, this);
            if(service==null)return null;
        }

        this.sid = service.getId();

        if(config.getMainCidInfoMap().get(mainFid)==null) {
            System.out.println("The dealer of "+mainFid+" is new...");
            config.addUser(mainFid, symkey);
        }

        config.getMyServiceMaskMap().put(service.getId(),ServiceMask.ServiceToMask(service,this.mainFid));
        saveConfig();
        return service;
    }

    private static Service askIfPublishNewService(String sid, byte[] symkey, BufferedReader br, ServiceType serviceType, ApipClient apipClient, ElasticsearchClient esClient, Settings settings) {
        Service service = null;
        if(askIfYes(br,"Publish a new service?")) {
            switch (serviceType) {
                case APIP -> new ApipManager(null, null, br, symkey).publishService();
                case FAPI, FAPI_No1_NrC7 -> new FapiServer(null, br, symkey, settings).publishService();
                case DISK -> new server.serviceManagers.DiskManager(null, null, br, symkey).publishService();
                case TALK -> new TalkManager(null, null, br, symkey).publishService();
                default -> {
                    System.out.println("Unexpected service type: "+serviceType);
                    return null;
                }
            }
            while (true){
                sid = Inputer.inputString(br,"Input the SID of the service you published:");
                if(!Hex.isHex32(sid))continue;
                service = getServiceBySid(sid, apipClient, esClient, service);
                if(service!=null)return service;
                else System.out.println("Failed to get the service with SID: "+sid);
            }
        }
        return null;
    }

    @Nullable
    private static Service getServiceBySid(String sid, ApipClient apipClient, ElasticsearchClient esClient, Service service) {
        try {
            if(apipClient !=null){
                service = apipClient.serviceById(sid);
            } else if(esClient !=null)
                service = EsUtils.getById(esClient, IndicesNames.SERVICE, sid,Service.class);
        } catch (IOException e) {
            System.out.println("Failed to get service from ES.");
                    return null;
        }
        return service;
    }

    private void writeServiceToRedis(Service service) {
        try(Jedis jedis = jedisPool.getResource()) {
            String key = Settings.addSidBriefToName(service.getId(),SERVICE);
            RedisUtils.writeToRedis(service, key,jedis,service.getClass());
            if(service.getParams() != null) {
                String paramsKey = Settings.addSidBriefToName(sid,PARAMS);
                RedisUtils.writeToRedis(service.getParams(), paramsKey,jedis,service.getParams().getClass());
            }
        }
    }



    public void writeToFile(String fid, String oid){
        fileName = FileUtils.makeFileName(fid, oid, SETTINGS, DOT_JSON);
        JsonUtils.writeObjectToJsonFile(this,Configure.getConfDir(),fileName,false);
    }

    public void saveServerSettings(String sid){
        writeToFile(null, sid);
        if(jedisPool!=null) {
            try (Jedis jedis = jedisPool.getResource()) {
                RedisUtils.writeToRedis(this, Settings.addSidBriefToName(sid, Strings.SETTINGS), jedis,Settings.class);
            }
        }
    }

    public void saveToolSettings(String clientName){
        writeToFile(null, clientName);
    }

    public void saveClientSettings(String fid,String clientName){
        writeToFile(fid, clientName);
        if(jedisPool!=null) {
            try (Jedis jedis = jedisPool.getResource()) {
                RedisUtils.writeToRedis(this, Settings.addSidBriefToName(sid, Strings.SETTINGS), jedis,Settings.class);
            }
        }
    }

    public void saveSimpleSettings(String clientName){
        writeToFile(null, clientName);
    }
    
    private void saveSettings() {
        if (this.sid != null) {
            saveServerSettings(sid);
        } else if (this.mainFid != null) {
            saveClientSettings(mainFid, clientName);
        } else {
            saveSimpleSettings(clientName);
        }
    }

    public void setting(BufferedReader br, @Nullable ServiceType serviceTypeOnlyForServer) {
        System.out.println("Setting...");
        Menu menu = new Menu("Setting",this::closeMenu);
        menu.setTitle("Settings");
        
        boolean isServer = serviceTypeOnlyForServer != null;
        boolean isFapi = isServer && ServiceType.isFapi(serviceTypeOnlyForServer);
        
        ApipClient apipClient = (ApipClient) getClient(APIP);
        fapi.client.FapiClient fapiClient0 = (fapi.client.FapiClient)getClient(FAPI);
        fapi.client.FapiClient fapiClient = fapiClient0 != null ? fapiClient0 : (fapi.client.FapiClient)getClient(FAPI_No1_NrC7);
        
        menu.add("Reset password", () -> resetPassword(serviceTypeOnlyForServer, jedisPool));
        menu.add("Add API provider", () -> config.addApiProviderAndConnect(symkey, null, apipClient,fapiClient));
        menu.add("Add API account", () -> config.addApiAccount(null, symkey, apipClient,fapiClient));
        menu.add("Update API provider", () -> config.updateApiProvider(apipClient));
        menu.add("Update API account", () -> config.updateApiAccount(config.chooseApiProviderOrAdd(config.getApiProviderMap(), apipClient), symkey, apipClient,fapiClient));
        menu.add("Delete API provider", () -> config.deleteApiProvider(symkey, apipClient,fapiClient));
        menu.add("Delete API account", () -> config.deleteApiAccount(symkey, apipClient,fapiClient));
        menu.add("Reset default APIs", () -> resetApi(symkey, apipClient,fapiClient));
        menu.add("Check settings", () -> checkSetting(br));
        
        // FAPI 配置修改
        if (isFapi) {
            menu.add("Modify FAPI server settings", () -> modifyFapiServerSettings(br));
        } else if (fapiClient != null) {
            menu.add("Modify FAPI client settings", () -> modifyFapiClientSettings(br));
        }
        
        menu.add("Show my prikey", () -> dumpPrikey(br));
        menu.add("Remove me", () -> removeMe(br));

        menu.showAndSelect(br);
    }
    
    private void modifyFapiServerSettings(BufferedReader br) {
        if (settingMap == null) {
            settingMap = new HashMap<>();
        }
        
        modifySetting(br, fapi.FapiBalanceManager.CREDIT_LIMIT, fapi.FapiBalanceManager.DEFAULT_CREDIT_LIMIT);
        modifySetting(br, fapi.FapiBalanceManager.KEY_CREDIT_RETENTION_DAYS, fapi.FapiBalanceManager.DEFAULT_CREDIT_RETENTION_DAYS);
        modifySetting(br, "fapiDiskDataPath", System.getProperty("user.home") + "/diskData");
        
        saveSettings();
    }
    
    private void modifyFapiClientSettings(BufferedReader br) {
        if (settingMap == null) {
            settingMap = new HashMap<>();
        }
        
        modifySetting(br, "fapiClientPort", 8501);
        modifySetting(br, "fapiClientDataDir", "~/.fudp_client");
        
        // 余额验证配置
        modifySetting(br, fapi.client.BalanceVerifier.KEY_TOLERANCE_PCT, fapi.client.BalanceVerifier.DEFAULT_TOLERANCE_PCT);
        modifySetting(br, fapi.client.BalanceVerifier.KEY_TOLERANCE_SAT_MIN, fapi.client.BalanceVerifier.DEFAULT_TOLERANCE_SAT_MIN);
        modifySetting(br, fapi.client.BalanceVerifier.KEY_DRIFT_ACCUM_PCT, fapi.client.BalanceVerifier.DEFAULT_DRIFT_ACCUM_PCT);
        modifySetting(br, fapi.client.BalanceVerifier.KEY_DRIFT_ACCUM_SAT, fapi.client.BalanceVerifier.DEFAULT_DRIFT_ACCUM_SAT);
        modifySetting(br, fapi.client.BalanceVerifier.KEY_DRIFT_STOP_PCT, fapi.client.BalanceVerifier.DEFAULT_DRIFT_STOP_PCT);
        modifySetting(br, fapi.client.BalanceVerifier.KEY_DRIFT_STOP_SAT, fapi.client.BalanceVerifier.DEFAULT_DRIFT_STOP_SAT);
        modifySetting(br, fapi.client.BalanceVerifier.KEY_MAX_CONSECUTIVE_DRIFT, fapi.client.BalanceVerifier.DEFAULT_MAX_CONSECUTIVE_DRIFT);
        modifySetting(br, fapi.client.BalanceVerifier.KEY_DRIFT_ACTION, fapi.client.BalanceVerifier.DEFAULT_DRIFT_ACTION);
        
        // 自动充值配置
        modifySetting(br, fapi.client.AutoRechargeManager.KEY_ENABLED, fapi.client.AutoRechargeManager.DEFAULT_ENABLED);
        modifySetting(br, fapi.client.AutoRechargeManager.KEY_THRESHOLD, fapi.client.AutoRechargeManager.DEFAULT_THRESHOLD);
        modifySetting(br, fapi.client.AutoRechargeManager.KEY_PURCHASE_KB, fapi.client.AutoRechargeManager.DEFAULT_PURCHASE_KB);
        modifySetting(br, fapi.client.AutoRechargeManager.KEY_COOLDOWN_MS, fapi.client.AutoRechargeManager.DEFAULT_COOLDOWN_MS);
        modifySetting(br, fapi.client.AutoRechargeManager.KEY_MAX_RETRIES, fapi.client.AutoRechargeManager.DEFAULT_MAX_RETRIES);
        modifySetting(br, fapi.client.AutoRechargeManager.KEY_RETRY_DELAY_MS, fapi.client.AutoRechargeManager.DEFAULT_RETRY_DELAY_MS);
        modifySetting(br, fapi.client.AutoRechargeManager.KEY_MAX_PAYMENT, fapi.client.AutoRechargeManager.DEFAULT_MAX_PAYMENT);
        
        saveSettings();
    }
    
    private void modifySetting(BufferedReader br, String key, Object defaultValue) {
        if (settingMap == null) {
            settingMap = new HashMap<>();
        }
        
        Object currentValue = settingMap.getOrDefault(key, defaultValue);
        String prompt = key + " (current: " + currentValue + ", default: " + defaultValue + "):";
        
        if (defaultValue instanceof Long || currentValue instanceof Long) {
            Long defVal = defaultValue instanceof Long ? (Long) defaultValue : ((Number) defaultValue).longValue();
            Long currVal = currentValue instanceof Long ? (Long) currentValue : (currentValue instanceof Number ? ((Number) currentValue).longValue() : defVal);
            Long newValue = Inputer.inputLong(br, prompt, currVal);
            settingMap.put(key, newValue);
        } else if (defaultValue instanceof Double || currentValue instanceof Double) {
            Double defVal = defaultValue instanceof Double ? (Double) defaultValue : ((Number) defaultValue).doubleValue();
            Double currVal = currentValue instanceof Double ? (Double) currentValue : (currentValue instanceof Number ? ((Number) currentValue).doubleValue() : defVal);
            Double newValue = Inputer.inputDouble(br, prompt, currVal);
            settingMap.put(key, newValue);
        } else if (defaultValue instanceof Boolean || currentValue instanceof Boolean) {
            Boolean defVal = defaultValue instanceof Boolean ? (Boolean) defaultValue : Boolean.valueOf(defaultValue.toString());
            Boolean currVal = currentValue instanceof Boolean ? (Boolean) currentValue : (currentValue != null ? Boolean.valueOf(currentValue.toString()) : defVal);
            Boolean newValue = Inputer.inputBoolean(br, prompt, currVal);
            settingMap.put(key, newValue);
        } else if (defaultValue instanceof String || currentValue instanceof String) {
            String defVal = defaultValue != null ? defaultValue.toString() : "";
            String currVal = currentValue != null ? currentValue.toString() : defVal;
            String newValue = Inputer.inputString(br, prompt, currVal);
            settingMap.put(key, newValue);
        }
    }

    private void dumpPrikey(BufferedReader br) {
        if(askIfYes(br,"Never leak your prikey. Continue dumping prikey?")){
            if(askIfYes(br,"Do you ensure you circumstance is safe?")){
                byte[] prikey = Decryptor.decryptPrikey(getMyPrikeyCipher(), symkey);
                if (prikey == null) {
                    System.out.println("Failed to decrypt your prikey.");
                    return;
                }
                String hex = Hex.toHex(prikey);
                String base58 = KeyTools.prikey32To38WifCompressed(hex);
                if(!askIfYes(br,"Encrypt prikey with random password?")) {
                    System.out.println("Prikey cipher of "+mainFid+":");
                    System.out.println("Hex:\n" + hex);
                    QRCodeUtils.generateQRCode(hex);

                    System.out.println("Base58check:\n" + base58);
                    QRCodeUtils.generateQRCode(base58);
                    Menu.anyKeyToContinue(br);
                }else{
                    System.out.println("Prikey of "+mainFid+":");
                    byte[] random = BytesUtils.getRandomBytes(6);
                    CryptoDataByte cryptoDataByte = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7).encryptByPassword(prikey, Base58.encode(random).toCharArray());
                    if(cryptoDataByte.getCode()==0 && cryptoDataByte.getCipher()!=null) {
                        String cipher = cryptoDataByte.toNiceJson();
                        String password = Base58.encode(random);
                        System.out.println("Prikey Cipher:\n"+cipher);
                        QRCodeUtils.generateQRCode(cipher);
                        System.out.println("password: "+password);
                        QRCodeUtils.generateQRCode(password);
                        System.out.println("""
                                IMPORTANT:\s
                                \t1) Both cipher and password are required to recover your prikey!!!
                                \t2) The password is still weak for professional hacking.\s""");
                        Menu.anyKeyToContinue(br);
                    }
                }
            }
        }
    }


    public void resetPassword(@Nullable ServiceType serviceType, BufferedReader br) {
        byte[] newSymkey = resetPassword(serviceType, jedisPool);
        if (newSymkey != null) {
            this.symkey = newSymkey;
        }
    }

    public void removeMe(BufferedReader br) {
        System.out.println("Removing user...");

        // Get the password to confirm deletion
        if (!checkPassword(br, symkey, config)) {
            System.out.println("Wrong password. Removal cancelled.");
            return;
        }

        // Remove from configureMap
        if(!Inputer.askIfYes(br, "Are you sure you want to remove "+mainFid+"? \nThis action cannot be undone."))
            return;

        config.getMainCidInfoMap().remove(mainFid);

        if(Inputer.askIfYes(br, "Remove from the owner list?")) {
            config.getOwnerList().remove(mainFid);
        }

        // Collect API account IDs to remove first, then remove them
        List<String> apiAccountIdsToRemove = new ArrayList<>();
        for(ApiAccount apiAccount : config.getApiAccountMap().values()) {
            if(apiAccount.getUserId()!=null && apiAccount.getUserId().equals(mainFid)) {
                apiAccountIdsToRemove.add(apiAccount.getId());
            }
        }
        for(String accountId : apiAccountIdsToRemove) {
            config.getApiAccountMap().remove(accountId);
        }

        // Collect service mask IDs to remove first, then remove them
        List<String> serviceMaskIdsToRemove = new ArrayList<>();
        for(ServiceMask serviceMask : config.getMyServiceMaskMap().values()) {
            if(serviceMask.getDealer().equals(mainFid)) {
                serviceMaskIdsToRemove.add(serviceMask.getId());
            }
        }
        for(String serviceMaskId : serviceMaskIdsToRemove) {
            config.getMyServiceMaskMap().remove(serviceMaskId);
        }
        
        saveConfig();

        // Delete settings file
        String midName=null;
        if((clientName!=null) && (!clientName.isEmpty())) midName = clientName;
        else midName = sid;
        String fileName = FileUtils.makeFileName(mainFid,midName , SETTINGS, DOT_JSON);

        String settingsPath = Path.of(getConfDir(), fileName).toString();
        File settingsFile = new File(settingsPath);
        if (settingsFile.exists()) {
            if (settingsFile.delete()) {
                System.out.println("Settings file deleted successfully.");
            } else {
                System.out.println("Failed to delete settings file.");
            }
        }

        System.out.println("User is deleted successfully.");
    }


    /**
     * Adds a shutdown hook to ensure resources are cleaned up even in unexpected shutdowns.
     * This includes stopping auto tasks, closing clients, and clearing sensitive data.
     */
    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                // Stop all auto tasks
                if (this.autoTaskList != null && !this.autoTaskList.isEmpty()) {
                    AutoTask.stopAllTasks();
                }

                // Close all clients in all groups
                if (clientGroups != null) {
                    for (ClientGroup group : clientGroups.values()) {
                        for (Object client : group.getClientMap().values()) {
                            try {
                                switch (group.getGroupType()) {
                                    case REDIS -> {
                                        if (client instanceof JedisPool jedisPool) {
                                            jedisPool.close();
                                        }
                                    }
                                    case ES -> {
                                        ApiAccount account = getApiAccount(valueOf(group.getGroupType().name()));
                                        if (account != null) account.closeEs();
                                    }
                                    case NASA_RPC -> {
                                        // Nothing to close
                                    }
                                    default -> {
                                        if (client instanceof FcClient fcClient1) {
                                            fcClient1.close();
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.error("Error closing client in shutdown hook: {}", e.getMessage());
                            }
                        }
                    }
                }

                // Clear sensitive data
                if (symkey != null) {
                    BytesUtils.clearByteArray(symkey);
                }

                log.info("Shutdown hook completed");
            } catch (Exception e) {
                log.error("Error in shutdown hook: {}", e.getMessage());
            }
        }));
    }

    public byte[] resetPassword(@Nullable ServiceType serviceType, JedisPool jedisPoolOnlyForServer){
        System.out.println("Reset password...");
        byte[] oldSymkey;
        byte[] oldNonceBytes;
        byte[] oldPasswordBytes;
        String oldNonce;
        String oldPasswordName;

        while(true) {
            oldPasswordBytes = Inputer.getPasswordBytes(br);
            oldPasswordName = Configure.makePasswordHashName(oldPasswordBytes);
            if(oldPasswordName.equals(config.getPasswordName()))break;
            System.out.println("Password wrong. Try again.");
        }

        oldNonce =config.getNonce();
        oldNonceBytes = Hex.fromHex(oldNonce);
        oldSymkey = getSymkeyFromPasswordAndNonce(oldPasswordBytes, oldNonceBytes);//Hash.sha256x2(BytesTools.bytesMerger(oldPasswordBytes, oldNonceBytes));

        byte[] newPasswordBytes = Inputer.resetNewPassword(br);
        if(newPasswordBytes==null)return null;
        String newPasswordName = makePasswordHashName(newPasswordBytes);
        config.setPasswordName(newPasswordName);
        byte[] newNonce = BytesUtils.getRandomBytes(16);
        config.setNonce(Hex.toHex(newNonce));
        byte[] newSymkey =  getSymkeyFromPasswordAndNonce(newPasswordBytes, newNonce);


        // Re-encrypt prikeyCipher in mainCidInfoMap
        if(config.getMainCidInfoMap()!=null && !config.getMainCidInfoMap().isEmpty()){
            for(CidInfo cidInfo : config.getMainCidInfoMap().values()){
                if(cidInfo != null && cidInfo.getPrikeyCipher() != null && !cidInfo.getPrikeyCipher().isEmpty()){
                    String cipher = cidInfo.getPrikeyCipher();
                    String newCipher = replaceCipher(cipher, oldSymkey, newSymkey);
                    cidInfo.setPrikeyCipher(newCipher);
                }
            }
        }

        if(config.getApiAccountMap()==null||config.getApiAccountMap().isEmpty())return newSymkey;
        for(ApiAccount apiAccount : config.getApiAccountMap().values()){
            if(apiAccount.getPasswordCipher()!=null){
                String cipher = apiAccount.getPasswordCipher();
                String newCipher = replaceCipher(cipher,oldSymkey,newSymkey);
                apiAccount.setPasswordCipher(newCipher);
            }
            if(apiAccount.getUserPrikeyCipher()!=null){
                String cipher = apiAccount.getUserPrikeyCipher();
                String newCipher = replaceCipher(cipher,oldSymkey,newSymkey);
                apiAccount.setUserPrikeyCipher(newCipher);
                apiAccount.setUserPubkey(ApiAccount.makePubkey(newCipher,newSymkey));
            }
            if(apiAccount.getSession()!=null && apiAccount.getSession().getKeyCipher()!=null){
                String cipher = apiAccount.getSession().getKeyCipher();
                String newCipher = replaceCipher(cipher,oldSymkey,newSymkey);
                apiAccount.getSession().setKeyCipher(newCipher);
            }
        }

        if(jedisPoolOnlyForServer!=null){
            try(Jedis jedis = jedisPoolOnlyForServer.getResource()){
                String oldSymkeyCipher = jedis.hget(addSidBriefToName(sid,CONFIG),INIT_SYM_KEY_CIPHER);
                if(oldSymkeyCipher!=null) {
                    String newCipher = replaceCipher(oldSymkeyCipher, oldSymkey, newSymkey);
                    jedis.hset(addSidBriefToName(sid,CONFIG), INIT_SYM_KEY_CIPHER, newCipher);
                }
            }
        }

        configureMap.put(config.getPasswordName(),config);
        configureMap.remove(oldPasswordName);

        Configure.saveConfig();

        if(serviceType!=null)
            Configure.checkWebConfig(this);

        BytesUtils.clearByteArray(oldPasswordBytes);
        BytesUtils.clearByteArray(newPasswordBytes);
        BytesUtils.clearByteArray(oldSymkey);
        Menu.anyKeyToContinue(br);
        return newSymkey;
    }

    private String replaceCipher(String oldCipher, byte[] oldSymkey, byte[] newSymkey) {
        byte[] data = new Decryptor().decryptJsonBySymkey(oldCipher, oldSymkey).getData();
        return new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7).encryptBySymkey(data,newSymkey).toJson();
    }

    public void resetApi(byte[] symkey, ApipClient apipClient, FapiClient fapiClient) {
        System.out.println("Reset default API service...");
        List<ServiceType> requiredBasicServices = new ArrayList<>();
        for(Module module: modules){
            if(module.getType().equals(Module.ModuleType.SERVICE))
                requiredBasicServices.add(ServiceType.fromString(module.getName()));
        }
        ServiceType type = Inputer.chooseOne(requiredBasicServices.toArray(new ServiceType[0]),null,"Choose the Service:",br);

        ApiProvider apiProvider = config.chooseApiProviderOrAdd(config.getApiProviderMap(), type, apipClient);
        if(apiProvider==null)return;
        ApiAccount apiAccount = config.findAccountForTheProvider(apiProvider, mainFid, symkey, apipClient,fapiClient);
        if (apiAccount != null) {
            Object client = apiAccount.getClient();//connectApi(config.getApiProviderMap().get(apiAccount.getProviderId()), symkey, br, null, config.getMainCidInfoMap());
            if (client != null) {
                System.out.println("Done.");
            } else System.out.println("Failed to connect the apiAccount: " + apiAccount.getApiUrl());
        } else {
            System.out.println("Failed to get the apiAccount.");
            return;
        }
        addAccountToGroup(apiProvider.fetchServiceType(),apiAccount);

        saveConfig();
        saveSettings();
    }

    // saveSettings() 方法已在 resetApi 方法中使用，保留原有的实现

//    private void freshAliasMaps(String alias, ApiAccount apiAccount) {
//        aliasAccountMap.put(alias, apiAccount);
//        aliasAccountIdMap.put(alias, apiAccount.getId());
//        aliasClientMap.put(alias, apiAccount.getClient());
//    }

    private void addAccountToGroup(ServiceType type, ApiAccount apiAccount){
        ClientGroup clientGroup = clientGroups.get(type);
        if(clientGroup==null)clientGroup= new ClientGroup(type);
        ClientGroup.GroupStrategy strategy = clientGroup.getStrategy();
        switch (strategy) {
            case USE_FIRST:
                clientGroup.addToFirstClient(apiAccount.getId(),apiAccount.getClient());
                break;
            case USE_ANY_VALID:

            case USE_ALL:

            case USE_ONE_RANDOM:

            case USE_ONE_ROUND_ROBIN:

            default:
                clientGroup.addClient(apiAccount.getId(),apiAccount.getClient());
                break;
        }

    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public Configure getConfig() {
        return config;
    }

    public void setConfig(Configure config) {
        this.config = config;
    }

    public BufferedReader getBr() {
        return br;
    }

    public void setBr(BufferedReader br) {
        this.br = br;
    }

    public void setJedisPool(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName1) {
        fileName = fileName1;
    }


    public String getMainFid() {
        return mainFid;
    }

    public void setMainFid(String mainFid) {
        this.mainFid = mainFid;
    }


    @NotNull
    public static String makeWebhookListenPath(String sid, String methodName) {
        return System.getProperty(UserDir) + "/" + Settings.addSidBriefToName(sid, methodName);
    }

    public void checkSetting(BufferedReader br) {
        if(settingMap==null){
            settingMap = new HashMap<>();
        }

        for(String key: settingMap.keySet()) {
            Class<?> valueClass = settingMap.get(key).getClass();
            if (valueClass.equals(String.class)) {
                settingMap.put(key,Inputer.inputString(br, key,(String) settingMap.get(key)));
            } else if (valueClass.equals(Long.class)||valueClass.equals(long.class)) {
                settingMap.put(key,Inputer.inputLong(br, key,(Long) settingMap.get(key)));
            } else if(valueClass.equals(Double.class)||valueClass.equals(double.class)){
                settingMap.put(key,Inputer.inputDouble(br, key,(Double) settingMap.get(key)));
            } else if(valueClass.equals(float.class)||valueClass.equals(Float.class)){
                settingMap.put(key,Inputer.inputFloat(br, key,(Float) settingMap.get(key)));
            }else if(valueClass.equals(boolean.class)||valueClass.equals(Boolean.class)){
                settingMap.put(key,Inputer.inputBoolean(br, key,(Boolean) settingMap.get(key)));
            }
        }

        if(autoTaskList!=null){
            for(AutoTask autoTask : autoTaskList){
                autoTask.checkTrigger(br);
            }
        }
    }

    public Map<String, Long> getBestHeightMap() {
        return bestHeightMap;
    }

    public void setBestHeightMap(Map<String, Long> bestHeightMap) {
        this.bestHeightMap = bestHeightMap;
    }

    public List<ApiAccount> getPaidAccountList() {
        return paidAccountList;
    }

    public void setPaidAccountList(List<ApiAccount> paidAccountList) {
        this.paidAccountList = paidAccountList;
    }



    public byte[] getSymkey() {
        return symkey;
    }

    public void setSymkey(byte[] symkey) {
        this.symkey = symkey;
    }

//    public Service.ServiceType[] getRequiredBasicServices() {
//        return requiredBasicServices;
//    }
//
//    public void setRequiredBasicServices(Service.ServiceType[] requiredBasicServices) {
//        this.requiredBasicServices = requiredBasicServices;
//    }


    public ServiceType getServerType() {
        return serverType;
    }

    public void setServerType(ServiceType serverType) {
        this.serverType = serverType;
    }

    public void setBootstrapping(boolean bootstrapping) {
        this.bootstrapping = bootstrapping;
    }

    public static Map<ServiceType, List<FreeApi>> getFreeApiListMap() {
        return freeApiListMap;
    }

    public static void setFreeApiListMap(Map<ServiceType, List<FreeApi>> freeApiListMap) {
        Settings.freeApiListMap = freeApiListMap;
    }

    public Map<String, Object> getSettingMap() {
        return settingMap;
    }

    public void setSettingMap(Map<String, Object> settingMap) {
        this.settingMap = settingMap;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public String getClientDataFileName() {
        return clientDataFileName;
    }

    public void setClientDataFileName(String clientDataFileName) {
        this.clientDataFileName = clientDataFileName;
    }

    public String getMyPubkey() {
        return myPubkey;
    }

    public void setMyPubkey(String myPubkey) {
        this.myPubkey = myPubkey;
    }

    public String getMyPrikeyCipher() {
        return myPrikeyCipher;
    }

    public byte[] decryptPrikey(){
        return Decryptor.decryptPrikey(getMyPrikeyCipher(),getSymkey());
    }

    public void setMyPrikeyCipher(String myPrikeyCipher) {
        this.myPrikeyCipher = myPrikeyCipher;
    }

    public ClientGroup getClientGroup(ServiceType type) {
        return clientGroups.get(type);
    }

    public void addClientToGroup(ServiceType type, String apiAccountId, Object client) {
        ClientGroup group = clientGroups.get(type);
        if (group != null) {
            group.addClient(apiAccountId, client);
        }
    }


    public void initModulesMute() {
        if(clientGroups==null)clientGroups = new HashMap<>();
        if(managers ==null) managers = new HashMap<>();
        int total = modules.size();
        System.out.println("\nThere are "+ total +" modules to be loaded.");
        int count = 1;

        for (Module module : modules) {
            System.out.println("Load module "+(count++) + "/"+ total +"...");

            switch (module.getType()) {
                case SERVICE -> initClientGroupMute(ServiceType.valueOf(module.getName()));
                case MANAGER -> initManager(Manager.ManagerType.valueOf(module.getName()));
                default -> log.warn("Unknown module type: " + module.getName());
            }

        }
        System.out.println("Nice! All the "+ total +" modules are loaded.\n");
    }

    public void initModels() {
        if(modules==null)return;
        if(clientGroups==null)clientGroups = new HashMap<>();
        managers = new HashMap<>();
        int total = modules.size();
        System.out.println("\nThere are "+ total +" modules to be loaded.");
        int count = 1;
        for (Module module : modules) {
            System.out.println("Load module "+(count++) + "/"+ total +"...");

            switch (module.getType()) {
                case SERVICE -> initClientGroup(ServiceType.valueOf(module.getName()));
                case MANAGER -> initManager(Manager.ManagerType.valueOf(module.getName()));
                case NODE -> initNode(module.getName());
                default -> log.warn("Unknown module type: " + module.getName());
            }

        }
        System.out.println("All the "+ total +" modules are loaded.\n");
    }


    public Manager<?> initManager(Manager.ManagerType type) {
        if(managers ==null) managers = new HashMap<>();
        Manager<?> manager = managers.get(type);
        if(manager !=null)return manager;

        switch (type) {
            case CID -> managers.put(type, new CidManager(this));
            case CASH -> managers.put(type, new CashManager(this));
            case SESSION -> managers.put(type, new SessionManager(this));
            case NONCE -> managers.put(type, new NonceManager(this));
            case MAIL -> managers.put(type, new MailManager(this));
            case CONTACT -> managers.put(type, new ContactManager(this));
            case SQUARE -> managers.put(type, new SquareManager(this));
            case TEAM -> managers.put(type, new TeamManager(this));
            case HAT -> managers.put(type, new HatManager(this));
            case DISK -> managers.put(type, new DiskManager(this));
            case ACCOUNT -> managers.put(type, new AccountManager(this));
            case SECRET -> managers.put(type,new SecretManager(this));
            case MEMPOOL -> managers.put(type,new MempoolManager(this));
            case WEBHOOK -> managers.put(type,new WebhookManager(this));
//            case BALANCE -> managers.put(type, new BalanceManager(this));
            default -> throw new IllegalArgumentException("Unexpected handler type: " + type);
        }
        System.out.println(type + " handler initiated.\n");
        return managers.get(type);
    }

    public Object initNode(String name) {
        if (nodes == null) nodes = new HashMap<>();
        if (nodes.containsKey(name)) {
            return nodes.get(name);
        }
        if ("FUDP".equalsIgnoreCase(name) || "FUDP_NODE".equalsIgnoreCase(name) || FudpNode.class.getSimpleName().equalsIgnoreCase(name)) {
            try {
                Map<String, Object> currentSettingMap = getSettingMap();
                if (currentSettingMap == null) {
                    currentSettingMap = new HashMap<>();
                    setSettingMap(currentSettingMap);
                }
                applyFudpDefaults(currentSettingMap);
                byte[] privateKey = decryptPrikey();

                NodeConfig config = new NodeConfig();
                int port = ((Number) currentSettingMap.getOrDefault(NodeConfig.FUDP_PORT, 8501)).intValue();
                String dataDir = String.valueOf(currentSettingMap.getOrDefault(NodeConfig.FUDP_DATA_DIR, "~/.fudp_client"));
                boolean enableBalanceVerification = ((boolean) currentSettingMap.getOrDefault(NodeConfig.BALANCE_VERIFICATION, true));

                config.setPort(port);
                config.setDataDir(dataDir);
                config.setEnableBalanceVerification(enableBalanceVerification);
                fudpNode = new FudpNode(privateKey, config);
                fudpNode.start();
                nodes.put(name, fudpNode);
                System.out.println("FUDP Node started. Local FID: " + fudpNode.getLocalFid());
                System.out.println("Listening on port " + port + ", dataDir=" + dataDir + ", balanceVerification =" + enableBalanceVerification);
                return fudpNode;
            } catch (Exception e) {
                log.error("Failed to start FUDP Node", e);
                return null;
            }
        }
        log.warn("Unknown node type: {}", name);
        return null;
    }

    private void applyFudpDefaults(Map<String, Object> settingMap) {
        if (settingMap == null) return;
        settingMap.putIfAbsent(NodeConfig.FUDP_PORT, 8501L);
        settingMap.putIfAbsent(NodeConfig.FUDP_DATA_DIR, "~/.fudp_client");
        settingMap.putIfAbsent(NodeConfig.BALANCE_VERIFICATION,true);
    }


    private Service resolveFapiService(Service selected, FapiClient client) {
        Service service = null;
        if (client != null && selected != null) {
            try {
                Map<String, Service> map = client.entityByIds(IndicesNames.SERVICE, Service.class, selected.getId());
                if (map != null) {
                    service = map.get(selected.getId());
                }
                if (service == null) {
                    Fcdsl fcdsl = new Fcdsl();
                    fcdsl.setEntity(IndicesNames.SERVICE);
                    fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.TYPE).addNewValues(FAPI_No1_NrC7.name(), FAPI.name());
                    List<Service> list = client.entitySearch(IndicesNames.SERVICE, fcdsl, Service.class);
                    if (list != null && !list.isEmpty()) {
                        service = list.stream().filter(s -> Objects.equals(s.getId(), selected.getId())).findFirst().orElse(list.get(0));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get service via fapi client: {}", e.getMessage());
            }
        }
        if (service == null && selected != null) {
            ApipClient apipClient = (ApipClient) getClient(APIP);
            if (apipClient == null) {
                apipClient = getDefaultApipClient();
            }
            if (apipClient != null) {
                service = apipClient.serviceById(selected.getId());
            }
        }
        return service == null ? selected : service;
    }

    private void persistFapiConfiguration(String host, int port, Service selected, ClientGroup group, FapiClient client) {
        Configure cfg = getConfig();
        if (cfg == null) return;
        if (cfg.getApiProviderMap() == null) cfg.setApiProviderMap(new HashMap<>());
        if (cfg.getApiAccountMap() == null) cfg.setApiAccountMap(new HashMap<>());

        ApiProvider provider = buildFapiProvider(host, port, selected);
        cfg.getApiProviderMap().put(provider.getId(), provider);

        ApiAccount account = buildFapiAccount(selected, provider, client);
        cfg.getApiAccountMap().entrySet().removeIf(entry ->
                entry.getValue() != null
                        && provider.getId().equals(entry.getValue().getProviderId())
                        && !entry.getKey().equals(account.getId()));
        cfg.getApiAccountMap().put(account.getId(), account);

        group.addApiAccount(account);
        group.addClient(account.getId(), client);
        clientGroups.put(ServiceType.FAPI_No1_NrC7, group);

        Configure.saveConfig();
        if (mainFid != null) {
            saveClientSettings(mainFid, clientName);
        }
    }

    private ApiProvider buildFapiProvider(String host, int port, Service selected) {
        ApiProvider provider = new ApiProvider();
        provider.fromFcService(selected);
        provider.makeServiceType(ServiceType.FAPI_No1_NrC7);
        provider.setApiUrl(host + ":" + port);
        return provider;
    }

    private ApiAccount buildFapiAccount(Service selected, ApiProvider provider, FapiClient client) {
        ApiAccount account = new ApiAccount();
        account.setUserName(mainFid);
        String newId = account.makeApiAccountId(provider.getId(), account.getUserName());
        account.setId(newId);
        account.setProviderId(provider.getId());
        account.setApiUrl(provider.getApiUrl());
        account.setService(selected);
        account.setUserId(mainFid);
        account.setUserPubkey(myPubkey);
        account.setUserPrikeyCipher(myPrikeyCipher);
        account.setClient(client);
        return account;
    }

    private String findExistingFapiAccountId(String providerId) {
        if (config == null || config.getApiAccountMap() == null) return null;
        for (Map.Entry<String, ApiAccount> entry : config.getApiAccountMap().entrySet()) {
            ApiAccount account = entry.getValue();
            if (account != null && providerId.equals(account.getProviderId())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public Manager<?> getManager(Manager.ManagerType type) {
        return managers != null ? managers.get(type) : null;
    }


    public Map<ServiceType, ClientGroup> getClientGroups() {
        return clientGroups;
    }

    public void setClientGroups(Map<ServiceType, ClientGroup> clientGroups) {
        this.clientGroups = clientGroups;
    }

    public Map<Manager.ManagerType, Manager<?>> getManagers() {
        return managers;
    }

    public Map<String, Object> getNodes() {
        return nodes;
    }

    public FudpNode getFudpNode() {
        return fudpNode;
    }

    public void setFudpNode(FudpNode fudpNode) {
        this.fudpNode = fudpNode;
    }

    public void addManager(Manager<?> manager){
        if(this.getManagers()==null)this.managers = new HashMap<>();
        this.managers.put(manager.getHandlerType(), manager);
    }

    public void setManager(Map<Manager.ManagerType, Manager<?>> handlers) {
        this.managers = handlers;
    }

    public List<String> getApiList() {
        return apiList;
    }

    public void setApiList(List<String> apiList) {
        this.apiList = apiList;
    }

    public String getDbDir() {
        return dbDir;
    }

    public void setDbDir(String dbDir) {
        this.dbDir = dbDir;
    }

    public List<Module> getModules() {
        return modules;
    }

    public void setModules(List<Module> modules) {
        this.modules = modules;
    }

    public void runAutoTasks() {
        if(autoTaskList==null)return;
        AutoTask.runAutoTask(autoTaskList, this);
    }

    /**
     * Initialize client group for web server without user interaction
     */
    public void initClientGroupMute(ServiceType type) {
        System.out.println("Initiate "+ type +" accounts and clients for server...");

        // Preserve existing group if it exists
        ClientGroup group;
        if(clientGroups==null) {
            log.warn("Client groups are not initialized");
            return;
        };
        group = clientGroups.get(type);
        if(group==null){
            log.warn("Client group for "+ type +" is not initialized");
            return;
        }

        group.connectAllClients(config, this, symkey, br);

        if(type == REDIS) {
            jedisPool = (JedisPool) group.getClient();
            try {
                symkey = Configure.getSymkeyFromRedis(sid, config, jedisPool);
            }catch (Exception e){e.printStackTrace();}
        }
        if(sid==null && serverType!=null) {
            if(type==APIP || (type == ES && this.serverType == APIP)) {
                loadMyService(null, symkey, config);
            }
        }
    }

    public LocalDB.DbType getLocalDBType() {
        return localDBType;
    }

    public void setLocalDBType(LocalDB.DbType localDBType) {
        this.localDBType = localDBType;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public List<AutoTask> getAutoTaskList() {
        return autoTaskList;
    }

    public void setAutoTaskList(List<AutoTask> autoTaskList) {
        this.autoTaskList = autoTaskList;
    }
}
