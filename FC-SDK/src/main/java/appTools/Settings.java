package appTools;

import apip.apipData.CidInfo;
import clients.ApipClient;
import clients.Client;
import clients.ClientGroup;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import configure.ApiAccount;
import configure.ApiProvider;
import configure.Configure;
import configure.WebServerConfig;
import constants.FieldNames;
import constants.IndicesNames;
import constants.Strings;
import crypto.CryptoDataByte;
import crypto.Decryptor;
import crypto.Encryptor;
import crypto.KeyTools;
import fcData.AlgorithmId;
import fcData.IdNameTools;
import fch.ParseTools;
import fch.fchData.Address;
import fch.fchData.Block;
import feip.feipData.Service;
import feip.feipData.ServiceMask;
import feip.feipData.serviceParams.*;
import handlers.*;
import nasa.NaSaRpcClient;

import org.bouncycastle.asn1.dvcs.ServiceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.FreeApi;
import server.serviceManagers.ApipManager;
import server.serviceManagers.DiskManager;
import server.serviceManagers.SwapHallManager;
import server.serviceManagers.TalkManager;
import handlers.NonceHandler;
import tools.*;
import tools.http.AuthType;
import tools.http.RequestMethod;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static appTools.Inputer.askIfYes;
import static appTools.Inputer.chooseOneFromList;
import static configure.Configure.*;
import static feip.feipData.Service.ServiceType.*;
import static handlers.Handler.HandlerType.MEMPOOL;
import static server.ApipApiNames.VERSION_1;
import static constants.Constants.UserDir;
import static constants.Strings.*;

public class Settings {
    public final static Logger log = LoggerFactory.getLogger(Settings.class);
    public static final String DEFAULT_AVATAR_BASE_PATH = System.getProperty("user.dir") + "/avatar/elements/";
    public static final String DEFAULT_AVATAR_FILE_PATH = System.getProperty("user.dir") + "/avatar/png/";

    public static final String LISTEN_PATH = "listenPath";
    public static final String DB_DIR = "dbDir";
    public static final String OP_RETURN_PATH = "opReturnPath";
    public static final String FORBID_FREE_API = "forbidFreeApi";
    public static final String FROM_WEBHOOK = "fromWebhook";
    public static final String WINDOW_TIME = "windowTime";
    public static final String DEALER_MIN_BALANCE = "dealerMinBalance";
    public static final String MIN_DISTRIBUTE_BALANCE = "minDistributeBalance";
    public static final String  SHARE_API = "shareApi";
    public static final String  LOCAL_DATA_PATH = "localDataPath";
    public static final String SCAN_MEMPOOL = "scanMempool";
    public static final String AVATAR_ELEMENTS_PATH = "avatarElementsPath";
    public static final String AVATAR_PNG_PATH = "avatarPngPath";
    public static final Long DEFAULT_WINDOW_TIME = 300000L;

    public static Map<Service.ServiceType,List<FreeApi>> freeApiListMap;
    private static String fileName;
    private transient Configure config;
    private transient BufferedReader br;
    private transient String clientDataFileName;
    private transient JedisPool jedisPool;

    private transient List<ApiAccount> paidAccountList;
    private transient byte[] symKey;
    private transient Map<Handler.HandlerType, Handler<?>> handlers;
    private transient AtomicBoolean isRunning = new AtomicBoolean(false);

    private Map<Service.ServiceType, ClientGroup> clientGroups;
    private Service.ServiceType serverType;
    private Map<String,Long> bestHeightMap;
    private Service service;

    private String sid; //For server
    private String mainFid; //For Client
    private String myPubKey;
    private String myPriKeyCipher;
    private Map<String,String>watchFidPubKeyMap;
    private String listenPath;

    //Settings
    private Map<String,Object> settingMap;

    private Object[] modules;
    private Object[] runningModules;
    private List<String> apiList;
    private String dbDir;

    public Settings(Configure configure) {
        if(configure!=null) {
            this.config = configure;
            this.br = configure.getBr();
            freeApiListMap = configure.getFreeApiListMap();
            clientGroups = new HashMap<>();
            checkDbDir(null);
        }
    }

    public Settings(Configure configure, Map<String,Object> settingMap, Object[] modules) {
        if(configure!=null) {
            this.config = configure;
            this.br = configure.getBr();
            freeApiListMap = configure.getFreeApiListMap();
            clientGroups = new HashMap<>();
            this.settingMap = settingMap;
            if(settingMap.get(LISTEN_PATH)!=null)
                this.listenPath = (String) settingMap.get(LISTEN_PATH);
            this.modules = modules;
            checkDbDir(settingMap);
        }
    }

    public Settings(Configure configure, Service.ServiceType serverType, Map<String,Object> settingMap, Object[] modules, Handler.HandlerType[] runningModules) {
        if(configure!=null) {
            this.config = configure;
            this.br =configure.getBr();
            freeApiListMap = configure.getFreeApiListMap();
            clientGroups = new HashMap<>();
            this.settingMap = settingMap;
            this.serverType = serverType;
            this.modules = modules;
            this.runningModules = runningModules;
            if(settingMap.get(LISTEN_PATH)!=null)
                this.listenPath = (String) settingMap.get(LISTEN_PATH);
            checkDbDir(settingMap);
        }
    }

    @Nullable
    public static Settings loadSettingsForServer(String configFileName) {
        WebServerConfig webServerConfig;
        Configure configure;
        Settings settings;
        Map<String, Configure> configureMap;
        try {
            webServerConfig = JsonTools.readJsonFromFile(configFileName,WebServerConfig.class);
            configureMap = JsonTools.readMapFromJsonFile(null,webServerConfig.getConfigPath(),String.class,Configure.class);
            if(configureMap==null){
                log.error("Failed to read the config file of "+ configFileName +".");
                return null;
            }
            configure = configureMap.get(webServerConfig.getPasswordName());
            settings = JsonTools.readJsonFromFile(webServerConfig.getSettingPath(), Settings.class);
            settings.setConfig(configure);

        } catch (IOException e) {
            log.error("Failed to read the config file of "+ configFileName +".");
            return null;
        }
        return settings;
    }
    public Block getBestBlock(){
        ElasticsearchClient esClient = null;
        ApipClient apipClient = null;
        NaSaRpcClient naSaRpcClient = null;
        if(getClient(NASA_RPC)!=null)
            naSaRpcClient = (NaSaRpcClient)getClient(NASA_RPC);
        if(getClient(APIP)!=null)
            apipClient = (ApipClient)getClient(APIP);
        if(getClient(ES)!=null)
            esClient = (ElasticsearchClient)getClient(ES);
        return getBestBlock(jedisPool,esClient,apipClient,naSaRpcClient);
    }


    public static Block getBestBlock(JedisPool jedisPool,ElasticsearchClient esClient,ApipClient apipClient,NaSaRpcClient naSaRpcClient) {

        if(jedisPool!=null){
            try(Jedis jedis = jedisPool.getResource()) {
                Block block = new Block();
                String bestHeightStr = jedis.get(BEST_HEIGHT);
                long bestHeight = Long.parseLong(bestHeightStr);
                String bestBlockId = jedis.get(BEST_BLOCK_ID);
                block.setBlockId(bestBlockId);
                block.setHeight(bestHeight);
                return block;
            }
        }
        if(naSaRpcClient!=null){
            Block block = new Block();
            naSaRpcClient.freshBestBlock();
            block.setHeight(naSaRpcClient.getBestHeight());
            block.setBlockId(naSaRpcClient.getBestBlockId());
            return block;
        }

        if(apipClient!=null){
            return apipClient.bestBlock(RequestMethod.POST, AuthType.FC_SIGN_BODY);
        }

        if(esClient!=null){
            try {
                return EsTools.getBestBlock(esClient);
            } catch (IOException ignore) {
                return null;
            }
        }
        return null;
    }

    public Long getBestHeight() {
        if(getClient(NASA_RPC)!=null){
            NaSaRpcClient client = (NaSaRpcClient) getClient(NASA_RPC);
            client.freshBestBlock();
            return client.getBestHeight();
        }

        if(getClient(APIP)!=null){
            return ((ApipClient)getClient(APIP)).getBestHeight();
        }

        if(getClient(ES)!=null) {
            Block block = null;
            try {
                block = EsTools.getBestBlock((ElasticsearchClient) getClient(ES));
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
            return apipClient.getBestHeight();
        }

        if(esClient!=null) {
            Block block = null;
            try {
                block = EsTools.getBestBlock(esClient);
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
            this.dbDir = FileTools.getUserDir()+"/db/";
        FileTools.checkDirOrMakeIt(this.dbDir);
    }

    public static String makeSettingsFileName(@Nullable String fid,@Nullable String sid){
        return FileTools.makeFileName(fid, sid, SETTINGS, DOT_JSON);
    }

    public void initiateServer(String sid, byte[] symKey, Configure config, List<String> apiList){
        if(clientGroups==null)clientGroups = new HashMap<>();
        if(this.config==null)this.config = config;
        this.apiList = apiList;
        this.symKey = symKey;

        System.out.println("Initiating server settings...");

        br = config.getBr();

        if(sid==null)checkSetting(br);

        mainFid = config.getServiceDealer(sid,symKey);
        myPriKeyCipher = config.getFidCipherMap().get(mainFid);

        initModels();

        if(service==null){
            System.out.println("Failed to load service information");
            return;
        }

        Object client = getClient(REDIS);
        if(client!=null) jedisPool = (JedisPool) client;

        setMyKeys(symKey, config);

        saveServerSettings(service.getSid());

        Configure.saveConfig();
    }

    private void setMyKeys(byte[] symKey, Configure config) {
        if(mainFid!=null && config.getFidCipherMap()!=null && config.getFidCipherMap().get(mainFid)!=null){
            myPriKeyCipher = config.getFidCipherMap().get(mainFid);
            byte[] priKey = Client.decryptPriKey(myPriKeyCipher, symKey);
            if(priKey==null){
                System.out.println("Failed to decrypt the priKey of "+mainFid);
            }else {
                byte[] pubKey = KeyTools.priKeyToPubKey(priKey);
                if(pubKey!=null) myPubKey = Hex.toHex(pubKey);
            }
        }else if(mainFid!=null){
            ApipClient apipClient = null;
            ApiAccount apipAccount = getApiAccount(APIP);
            if(apipAccount!=null && apipAccount.getClient()!=null)
                apipClient = (ApipClient)apipAccount.getClient();
            if(apipClient!=null)
                myPubKey = apipClient.getPubKey(mainFid,RequestMethod.POST,AuthType.FC_SIGN_BODY);
        }
    }

    public void initiateMuteServer(String serverName, byte[] symKey, Configure config){
        if(clientGroups==null)clientGroups = new HashMap<>();
        if(this.config==null)this.config = config;
        this.symKey = symKey;

        System.out.println("Initiating mute server settings...");

        br = config.getBr();

        checkSetting(br);

//        initBasicServices(requiredBasicServices, symKey, config);
        initModels();
        saveServerSettings(serverName);

        Configure.saveConfig();
    }

    public void initiateClient(String fid, String clientName, byte[] symKey, Configure config, BufferedReader br){
        System.out.println("Initiating Client settings...");

        this.config = config;
        this.symKey = symKey;
        this.br = config.getBr();
        this.mainFid = fid;
        this.myPriKeyCipher = config.getFidCipherMap().get(fid);

        if(clientGroups==null)clientGroups = new HashMap<>();
        if(this.config==null)this.config = config;

        setInitForClient(fid, config, br);

        checkSetting(br);

//        initBasicServices(requiredBasicServices, symKey, config);
        initModels();
        clientDataFileName = FileTools.makeFileName(mainFid,clientName,DATA,DOT_JSON);

        setMyKeys(symKey, config);

        saveClientSettings(mainFid,clientName);
        Configure.saveConfig();
    }

    public void close() {
        try {
            if(isRunning !=null) isRunning.set(false);
            br.close();
            BytesTools.clearByteArray(symKey);
            
            // Close all clients in all groups
            for (ClientGroup group : clientGroups.values()) {
                for (Object client : group.getClientMap().values()) {
                    switch (group.getGroupType()) {
                        case REDIS -> {
                            if(client instanceof JedisPool jedisPool){
                                jedisPool.close();
                            }
                        }
                        case ES -> {
                            ApiAccount account = getApiAccount(valueOf(group.getGroupType().name()));
                            if (account != null) account.closeEs();
                        }
                        case NASA_RPC -> {
                        }
                        default -> {
                            if(client instanceof Client client1){
                                client1.close();
                            }
//                            throw new IllegalStateException("Unexpected value: " + group.getGroupType());
                        }
                    }
                }
            }

            for(Object module: runningModules){
                if(module instanceof Handler<?> handler){
                    handler.close();
                }else if(module instanceof ServiceType serviceType){
                    // ServiceType is an enum, no need to switch on it
                    // Just close any resources if needed
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    public Service loadMyService(String sid, byte[] symKey, Configure config) {
        Class<? extends Params> paramClass =
                switch (serverType){
                    case APIP -> ApipParams.class;
                    case DISK -> DiskParams.class;
                    case TALK -> TalkParams.class;
                    case OTHER, MAP, SWAP_HALL -> Params.class;
                    default -> null;
                };

        ApipClient apipClient = (ApipClient) getClient(APIP);
        if(apipClient!=null) {
            service = getMyService(sid, symKey, config, br, apipClient, paramClass, this.serverType);
        }else {
            ElasticsearchClient esClient = (ElasticsearchClient) getClient(ES);
            service = getMyService(sid, symKey, config, br, esClient, paramClass, this.serverType);
        }

        if(service==null){
            System.out.println("Failed to get service.");
            return null;
        }

        if (isWrongServiceType(service, serverType.name())) return null;

        System.out.println("\nYour service:\n"+ JsonTools.toNiceJson(service));
        Menu.anyKeyToContinue(br);

        if(jedisPool!=null)writeServiceToRedis(service, ApipParams.class);
        this.sid = service.getSid();
        return service;
    }

    public Object getClient(Service.ServiceType serviceType) {
        ClientGroup group = clientGroups.get(Service.ServiceType.valueOf(serviceType.name()));
        return group != null ? group.getClient() : null;
    }

    public ApiAccount getApiAccount(Service.ServiceType serviceType) {
        ClientGroup group = clientGroups.get(Service.ServiceType.valueOf(serviceType.name()));
        if (group != null && !group.getAccountIds().isEmpty()) {
            String accountId = group.getAccountIds().get(0);
            return config.getApiAccountMap().get(accountId);
        }
        return null;
    }

    public String getApiAccountId(Service.ServiceType serviceType) {
        ClientGroup group = clientGroups.get(Service.ServiceType.valueOf(serviceType.name()));
        if (group != null && !group.getAccountIds().isEmpty()) {
            return group.getAccountIds().get(0); // Returns first account ID by default
        }
        return null;
    }


    public static ApipClient getFreeApipClient(){
        return getFreeApipClient(null);
    }
    public static ApipClient getFreeApipClient(BufferedReader br){
        ApipClient apipClient = new ApipClient();
        ApiAccount apipAccount = new ApiAccount();

        List<FreeApi> freeApiList = freeApiListMap.get(APIP);

        for(FreeApi freeApi : freeApiList){
            apipAccount.setApiUrl(freeApi.getUrlHead());
            apipClient.setApiAccount(apipAccount);
            apipClient.setUrlHead(freeApi.getUrlHead());
            try {
                if ((boolean) apipClient.ping(VERSION_1, RequestMethod.GET, AuthType.FREE, APIP))
                    return apipClient;
            }catch (Exception ignore){};
        }
        if(br !=null) {
            if (askIfYes(br, "Failed to get free APIP service. Add new?")) {
                do {
                    String url = fch.Inputer.inputString(br, "Input the urlHead of the APIP service:");
                    apipAccount.setApiUrl(url);
                    apipClient.setApiAccount(apipAccount);
                    if ((boolean) apipClient.ping(VERSION_1, RequestMethod.GET,AuthType.FREE, APIP)) {
                        FreeApi freeApi = new FreeApi(url,true, APIP);
                        freeApiList.add(freeApi);
                        return apipClient;
                    }
                } while (askIfYes(br, "Failed to ping this APIP Service. Try more?"));
            }
        }
        return null;
    }


    public static String getLocalDataDir(String sid){
        return System.getProperty("user.dir")+"/"+ addSidBriefToName(sid,DATA)+"/";
    }

    public static String addSidBriefToName(String sid, String name) {
//        String finalName;
//        finalName = (sid.substring(0,6) + "_" + name);
        return IdNameTools.makeKeyName(null,sid,name,null);
    }

    public static void setNPrices(String sid, String[] ApiNames, Jedis jedis, BufferedReader br) {
        for(String api : ApiNames){
            String ask = "Set the price multiplier for " + api + "? y/n. Enter to leave default 1:";
            int input = Inputer.inputInt(br, ask,0);
            if(input==0)input=1;
            jedis.hset(addSidBriefToName(sid,Strings.N_PRICE),api, String.valueOf(input));
        }
    }

    public static byte[] getMainFidPriKey(byte[] symKey, Settings settings) {
        Decryptor decryptor = new Decryptor();
        String mainFid = settings.getMainFid();
        String cipher = settings.getConfig().getFidCipherMap().get(mainFid);
        if(cipher==null)return null;
        CryptoDataByte result = decryptor.decryptJsonBySymKey(cipher, symKey);
        if(result.getCode()!=0){
            System.out.println("Failed to decrypt the private key of "+mainFid+".");
            return null;
        }
        return result.getData();
    }

    public CidInfo checkFidInfo(ApipClient apipClient, BufferedReader br) {
        CidInfo fidInfo = apipClient.cidInfoById(mainFid);

        if(fidInfo!=null) {
            long bestHeight = apipClient.getFcClientEvent().getResponseBody().getBestHeight();

            bestHeightMap.put(IndicesNames.CID,bestHeight);
            System.out.println("My information:\n" + JsonTools.toNiceJson(fidInfo));
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

    private  void setInitForClient(String fid, Configure config, BufferedReader br) {
        this.config= config;
        this.br= br;
        freeApiListMap = config.getFreeApiListMap();
        this.mainFid = fid;
        if(bestHeightMap==null)bestHeightMap=new HashMap<>();
    }
//    public void initBasicServices(){
//        initBasicServices(requiredBasicServices, symKey, config);
//    }
//
//    public void initBasicServices(Service.ServiceType[] requiredServices, byte[] symKey, Configure config) {
//        if (clientGroups == null) {
//            clientGroups = new HashMap<>();
//        }
//        boolean jedisDone = false;
//        if(symKey==null && Arrays.asList(requiredServices).contains(Service.ServiceType.REDIS)){
//            initClientGroup(Service.ServiceType.REDIS);
//            try{
//                symKey = Configure.getSymKeyFromRedis(sid, config, jedisPool);
//            }catch(Exception e){
//                System.out.println("Failed to get symKey from Redis.");
//            }
//            jedisDone = true;
//        }
//
//        for(Service.ServiceType alias : requiredServices) {
//            if(jedisDone && alias== Service.ServiceType.REDIS)continue;
//            initClientGroup(alias);
//        }
//    }

    public void initClientGroup(Service.ServiceType groupType) {
        System.out.println("Initiate "+ groupType +" accounts and clients...");

        // Preserve existing group if it exists
        ClientGroup group;
        if(clientGroups==null) clientGroups = new HashMap<>();
        group = clientGroups.get(groupType);
        if(group!=null){
            group.connectAllClients(config, this,symKey);
            return;
        }

        group = new ClientGroup(groupType);
        while(true){
            ApipClient freeApipClient;
            switch (groupType){
                case ES,REDIS,NASA_RPC ->freeApipClient = null;
                default -> freeApipClient = getFreeApipClient();
            }
            ApiAccount apiAccount = config.getApiAccount(symKey, mainFid, groupType, freeApipClient);
            if(apiAccount==null)break;
            group.addApiAccount(apiAccount);
            group.getAccountIds().add(apiAccount.getId());
            if(apiAccount.getClient()!=null)group.getClientMap().put(apiAccount.getId(),apiAccount.getClient());
            if(br !=null && !askIfYes(br,"Add more "+groupType + " account?"))break;
        }
        if(group.getAccountIds().size()>1){
            ClientGroup.GroupStrategy strategy = Inputer.chooseOne(ClientGroup.GroupStrategy.values(),null,"Chose the strategy",br);
            group.setStrategy(strategy);
        }
        clientGroups.put(groupType, group);
        if(groupType == REDIS)jedisPool = (JedisPool) group.getClient();
        if(sid==null && serverType!=null) {
            if(groupType==APIP
                    || (groupType == ES && this.serverType == APIP))
                loadMyService(null, symKey, config);
        }
    }

    public Service getMyService(String sid, byte[] symKey, Configure config, BufferedReader br, ApipClient apipClient, Class<?> paramsClass, Service.ServiceType serviceType) {
        return getMyService(sid, symKey, config, br, apipClient,null,paramsClass, serviceType);
    }
    public Service getMyService(String sid, byte[] symKey, Configure config, BufferedReader br, ElasticsearchClient esClient,  Class<?> paramsClass, Service.ServiceType serviceType) {
        return getMyService(sid, symKey, config, br, null,esClient,paramsClass, serviceType);
    }
    public Service getMyService(String sid, byte[] symKey, Configure config, BufferedReader br, ApipClient apipClient, ElasticsearchClient esClient, Class<?> paramsClass, Service.ServiceType serviceType) {
        System.out.println("Get my service...");
        Service service = null;
        if(sid ==null) {
            service = askIfPublishNewService(sid, symKey, br, serviceType, apipClient, esClient);
            if(service==null) {
                String owner = chooseOneFromList(config.getOwnerList(), null, "Choose the owner:", br);
                if (owner == null)
                    owner = config.addOwner(br);
                service = config.chooseOwnerService(owner, symKey, serviceType, esClient, apipClient);
            }
        }else {
            service = getServiceBySid(sid, apipClient, esClient, service);
        }

        if(service==null){
            service = askIfPublishNewService(sid, symKey, br, serviceType, apipClient, esClient);
            if(service==null)return null;
        }

        Params params;
        switch (serviceType) {
            case APIP -> params = (ApipParams) Params.getParamsFromService(service, paramsClass);
            case DISK -> params = (DiskParams) Params.getParamsFromService(service, paramsClass);
            case TALK -> params = (TalkParams) Params.getParamsFromService(service, paramsClass);
            case SWAP_HALL -> params = (SwapHallParams) Params.getParamsFromService(service, paramsClass);
            default -> params = (Params) Params.getParamsFromService(service, paramsClass);
        }
        if (params == null) return service;
        service.setParams(params);
        this.sid = service.getSid();
        this.mainFid = params.getDealer();
        if(config.getFidCipherMap().get(mainFid)==null) {
            System.out.println("The dealer of "+mainFid+" is new...");
            config.addUser(mainFid, symKey);
        }

        config.getMyServiceMaskMap().put(service.getSid(),ServiceMask.ServiceToMask(service,this.mainFid));
        saveConfig();
        return service;
    }

    private static Service askIfPublishNewService(String sid, byte[] symKey, BufferedReader br, Service.ServiceType serviceType, ApipClient apipClient, ElasticsearchClient esClient) {
        Service service = null;
        if(askIfYes(br,"Publish a new service?")) {
            switch (serviceType) {
                case APIP -> new ApipManager(null, null, br, symKey, ApipParams.class).publishService();
                case DISK -> new DiskManager(null, null, br, symKey, DiskParams.class).publishService();
                case TALK -> new TalkManager(null, null, br, symKey, TalkParams.class).publishService();
                case SWAP_HALL -> new SwapHallManager(null, null, br, symKey, SwapHallParams.class).publishService();
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
                service = EsTools.getById(esClient, IndicesNames.SERVICE, sid,Service.class);
        } catch (IOException e) {
            System.out.println("Failed to get service from ES.");
                    return null;
        }
        return service;
    }

    private  void setInitForServer(String sid, Configure config, BufferedReader br) {
        this.config= config;
        this.br = br;
        if(sid==null) {
            System.out.println("No service yet. We will set it later.");
            Menu.anyKeyToContinue(br);
        }
        else this.sid = sid;
        freeApiListMap = config.getFreeApiListMap();
    }

    private  void writeServiceToRedis(Service service, Class<? extends Params> paramsClass) {
        try(Jedis jedis = jedisPool.getResource()) {
            String key = Settings.addSidBriefToName(service.getSid(),SERVICE);
            RedisTools.writeToRedis(service, key,jedis,service.getClass());
            String paramsKey = Settings.addSidBriefToName(sid,PARAMS);
            RedisTools.writeToRedis(service.getParams(), paramsKey,jedis,paramsClass);
        }
    }

    public static boolean isWrongServiceType(Service service, String type) {
        if(!StringTools.isContainCaseInsensitive(service.getTypes(), type)) {
            System.out.println("\nWrong service type:"+ Arrays.toString(service.getTypes()));
            return true;
        }
        return false;
    }

    public static void showServiceList(List<Service> serviceList) {
        String title = "Services";
        String[] fields = new String[]{"",FieldNames.STD_NAME,FieldNames.SID};
        int[] widths = new int[]{2,24,64};
        List<List<Object>> valueListList = new ArrayList<>();
        int i=1;
        for(Service service : serviceList){
            List<Object> valueList = new ArrayList<>();
            valueList.add(i);
            valueList.add(service.getStdName());
            valueList.add(service.getSid());
            valueListList.add(valueList);
            i++;
        }
        Shower.showDataTable(title,fields,widths,valueListList, 0);
    }

    public String chooseFid(Configure config, BufferedReader br, byte[] symKey) {
        String fid = fch.Inputer.chooseOne(config.getFidCipherMap().keySet().toArray(new String[0]), null, "Choose fid:",br);
        if(fid==null)fid =config.addUser(symKey);
        return fid;
    }


    private  void initService(String accountAlias, Service.ServiceType type, String apipAccountId, byte[] symKey, Configure config) {
        ApiAccount apiAccount = config.checkAPI(apipAccountId, mainFid, type,symKey);
        switch (type){
            case APIP,TALK,SWAP_HALL,MAP -> apiAccount = checkIfMainFidIsApiAccountUser(symKey,config,br,apiAccount, mainFid);
        }
        if(apiAccount.getClient()!=null){
            apipAccountId=apiAccount.getId();
            ClientGroup clientGroup = clientGroups.get(accountAlias);
            clientGroup.addClient(apipAccountId,apiAccount.getClient());
//            aliasAccountMap.put(accountAlias,apiAccount);
//            aliasAccountIdMap.put(accountAlias,apipAccountId);
//            aliasClientMap.put(accountAlias,apiAccount.getClient());
            if(type.equals(APIP))config.setApipClient((ApipClient) apiAccount.getClient());
        }
        else System.out.println("Failed to connect service:"+accountAlias);
    }

    public void writeToFile(String fid, String oid){
        fileName = FileTools.makeFileName(fid, oid, SETTINGS, DOT_JSON);
        JsonTools.writeObjectToJsonFile(this,Configure.getConfDir(),fileName,false);
    }

    public void saveServerSettings(String sid){
        writeToFile(null, sid);
        if(jedisPool!=null) {
            try (Jedis jedis = jedisPool.getResource()) {
                RedisTools.writeToRedis(this, Settings.addSidBriefToName(sid, Strings.SETTINGS), jedis,Settings.class);
            }
        }
    }

    public void saveClientSettings(String fid,String clientName){
        writeToFile(fid, clientName);
        if(jedisPool!=null) {
            try (Jedis jedis = jedisPool.getResource()) {
                RedisTools.writeToRedis(this, Settings.addSidBriefToName(sid, Strings.SETTINGS), jedis,Settings.class);
            }
        }
    }

    public void setting(byte[] symKey, BufferedReader br, @Nullable Service.ServiceType serviceTypeOnlyForServer) {
        System.out.println("Setting...");
        while (true) {
            Menu menu = new Menu();
            menu.setTitle("Settings");
            menu.add("Reset password",
                    "Add API provider",
                    "Add API account",
                    "Update API provider",
                    "Update API account",
                    "Delete API provider",
                    "Delete API account",
                    "Reset Default APIs",
                    "Watching FIDs",
                    "Check settings"
            );
            menu.show();
            int choice = menu.choose(br);
            ApipClient apipClient = (ApipClient) getClient(APIP);
            switch (choice) {
                case 1 -> {
                    byte[] newSymKey=resetPassword(serviceTypeOnlyForServer, jedisPool);
                    if(newSymKey==null)break;
                    this.symKey = newSymKey;
                }
                case 2 -> config.addApiProviderAndConnect(symKey,null,apipClient);
                case 3 -> config.addApiAccount(null, symKey, apipClient);
                case 4 -> config.updateApiProvider(apipClient);
                case 5 -> config.updateApiAccount(config.chooseApiProviderOrAdd(config.getApiProviderMap(), apipClient),symKey,apipClient);
                case 6 -> config.deleteApiProvider(symKey,apipClient);
                case 7 -> config.deleteApiAccount(symKey,apipClient);
                case 8 -> resetApi(symKey,apipClient);
                case 9 -> watchingFids(apipClient, br);
                case 10 -> checkSetting(br);
                case 0 -> {
                    return;
                }
            }
        }
    }

    public void watchingFids(ApipClient apipClient, BufferedReader br) {
        while(true){
            String op = Inputer.chooseOne(new String[]{"List", "Add", "Delete", "Delete All"}, null, "Operation:", br);
            if(op == null) return;
            switch(op) {
            case "List" -> {
                List<List<Object>> valueListList = new ArrayList<>();
                for(String fid : this.getWatchFidPubKeyMap().keySet()) {
                    valueListList.add(List.of(fid));
                }
                Shower.showDataTable("Watching FIDs", new String[]{"FID"}, new int[]{24}, valueListList, 0);
                Menu.anyKeyToContinue(br);
            }
            case "Add" -> {
                String[] fids = Inputer.inputFidArray(br, "Input the FIDs you want to watch, enter to exit:", 0);
                Map<String,Address> fidMap = apipClient.fidByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, fids);
                if(fidMap == null || fidMap.isEmpty()) {
                    System.out.println("No FIDs found.");
                    return;
                }
                for(String fid : fidMap.keySet()) {
                    this.getWatchFidPubKeyMap().put(fid, fidMap.get(fid).getPubKey());
                    System.out.print("Watching FID: "+fid+" is added.");
                    if(this.getWatchFidPubKeyMap().get(fid) == null) {
                        System.out.println(" Without public key.");
                    }else System.out.println();
                }
                Menu.anyKeyToContinue(br);
            }
            case "Delete" -> {
                String fid = Inputer.chooseOneKeyFromMap(this.getWatchFidPubKeyMap(), false, null, "Choose one to delete:", br);
                if(fid != null && askIfYes(br, "Delete the watching FID: "+fid+"?")) {
                    this.getWatchFidPubKeyMap().remove(fid);
                    System.out.println(fid+" is deleted.");
                    Menu.anyKeyToContinue(br);
                }
            }
            case "Delete All" -> {
                    if(askIfYes(br, "Delete all watching FIDs?")) { 
                        this.getWatchFidPubKeyMap().clear();
                        System.out.println("All watching FIDs are deleted.");
                        Menu.anyKeyToContinue(br);
                    }
                }
            }
        }
    }

    public byte[] resetPassword(@Nullable Service.ServiceType serviceType, JedisPool jedisPoolOnlyForServer){
        System.out.println("Reset password...");
        byte[] oldSymKey;
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
        oldSymKey = getSymKeyFromPasswordAndNonce(oldPasswordBytes, oldNonceBytes);//Hash.sha256x2(BytesTools.bytesMerger(oldPasswordBytes, oldNonceBytes));

        byte[] newPasswordBytes = Inputer.resetNewPassword(br);
        if(newPasswordBytes==null)return null;
        String newPasswordName = makePasswordHashName(newPasswordBytes);
        config.setPasswordName(newPasswordName);
        byte[] newNonce = BytesTools.getRandomBytes(16);
        config.setNonce(Hex.toHex(newNonce));
        byte[] newSymKey =  getSymKeyFromPasswordAndNonce(newPasswordBytes, newNonce);


        if(config.getApiAccountMap()==null||config.getApiAccountMap().isEmpty())return newSymKey;
        for(ApiAccount apiAccount : config.getApiAccountMap().values()){
            if(apiAccount.getPasswordCipher()!=null){
                String cipher = apiAccount.getPasswordCipher();
                String newCipher = replaceCipher(cipher,oldSymKey,newSymKey);
                apiAccount.setPasswordCipher(newCipher);
            }
            if(apiAccount.getUserPriKeyCipher()!=null){
                String cipher = apiAccount.getUserPriKeyCipher();
                String newCipher = replaceCipher(cipher,oldSymKey,newSymKey);
                apiAccount.setUserPriKeyCipher(newCipher);
                apiAccount.setUserPubKey(ApiAccount.makePubKey(newCipher,newSymKey));
            }
            if(apiAccount.getSession()!=null && apiAccount.getSession().getKeyCipher()!=null){
                String cipher = apiAccount.getSession().getKeyCipher();
                String newCipher = replaceCipher(cipher,oldSymKey,newSymKey);
                apiAccount.getSession().setKeyCipher(newCipher);
            }
        }

        if(jedisPoolOnlyForServer!=null){
            try(Jedis jedis = jedisPoolOnlyForServer.getResource()){
                String oldSymKeyCipher = jedis.hget(addSidBriefToName(sid,CONFIG),INIT_SYM_KEY_CIPHER);
                if(oldSymKeyCipher!=null) {
                    String newCipher = replaceCipher(oldSymKeyCipher, oldSymKey, newSymKey);
                    jedis.hset(addSidBriefToName(sid,CONFIG), INIT_SYM_KEY_CIPHER, newCipher);
                }
            }
        }

        configureMap.put(config.getPasswordName(),config);
        configureMap.remove(oldPasswordName);

        Configure.saveConfig();

        if(serviceType!=null)
            Configure.checkWebConfig(this);

        BytesTools.clearByteArray(oldPasswordBytes);
        BytesTools.clearByteArray(newPasswordBytes);
        BytesTools.clearByteArray(oldSymKey);
        Menu.anyKeyToContinue(br);
        return newSymKey;
    }

    private String replaceCipher(String oldCipher, byte[] oldSymKey, byte[] newSymKey) {
        byte[] data = new Decryptor().decryptJsonBySymKey(oldCipher, oldSymKey).getData();
        return new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7).encryptBySymKey(data,newSymKey).toJson();
    }
    public ApiAccount checkIfMainFidIsApiAccountUser(byte[] symKey, Configure config, BufferedReader br, ApiAccount apiAccount, String userFid) {
        if(mainFid==null||apiAccount==null)return apiAccount;

        String mainFidPriKeyCipher;
        if(mainFid.equals(apiAccount.getUserId())) {
            mainFidPriKeyCipher = apiAccount.getUserPriKeyCipher();
            config.getFidCipherMap().put(mainFid,mainFidPriKeyCipher);
            if(paidAccountList ==null) paidAccountList = new ArrayList<>();
            paidAccountList.add(apiAccount);
        }else{
            if(askIfYes(br,"Your service dealer "+mainFid+" is not the user of the API account "+apiAccount.getUserId()+". \nReset API account?")){
                while(true) {
                    apiAccount = config.getApiAccount(symKey, userFid, APIP,null);
                    if(mainFid.equals(apiAccount.getUserId())){
                        if(paidAccountList ==null) paidAccountList = new ArrayList<>();
                        paidAccountList.add(apiAccount);
                        String apiProvideType = config.getApiProviderMap().get(apiAccount.getProviderId()).getType().name();
//                        aliasAccountIdMap.put(apiProvideType,apiAccount.getId());
                        ClientGroup clientGroup = clientGroups.get(Service.ServiceType.valueOf(apiProvideType));
                        clientGroup.addClient(apiAccount.getId(),apiAccount.getClient());
                        break;
                    }
                    System.out.println("The API user is still not your service account. Reset API account again.");
                }
            }else {
                config.addUser(mainFid, symKey);
            }
        }

        return apiAccount;
    }

    public void resetApi(byte[] symKey, ApipClient apipClient) {
        System.out.println("Reset default API service...");
        List<Service.ServiceType> requiredBasicServices = new ArrayList<>();
        for(Object model: modules){
            if(model instanceof Service.ServiceType type){
                requiredBasicServices.add(type);
            }
        }
        Service.ServiceType type = Inputer.chooseOne(requiredBasicServices.toArray(new Service.ServiceType[0]),null,"Choose the Service:",br);

        ApiProvider apiProvider = config.chooseApiProviderOrAdd(config.getApiProviderMap(), type, apipClient);
        ApiAccount apiAccount = config.findAccountForTheProvider(apiProvider, mainFid, symKey, apipClient);
        if (apiAccount != null) {
            Object client = apiAccount.connectApi(config.getApiProviderMap().get(apiAccount.getProviderId()), symKey, br, null, config.getFidCipherMap());
            if (client != null) {
                Configure.saveConfig();
                System.out.println("Done.");
            } else System.out.println("Failed to connect the apiAccount: " + apiAccount.getApiUrl());
        } else {
            System.out.println("Failed to get the apiAccount.");
            return;
        }
        addAccountToGroup(type,apiAccount);
//        freshAliasMaps(type, apiAccount);
    }

//    private void freshAliasMaps(String alias, ApiAccount apiAccount) {
//        aliasAccountMap.put(alias, apiAccount);
//        aliasAccountIdMap.put(alias, apiAccount.getId());
//        aliasClientMap.put(alias, apiAccount.getClient());
//    }

    private void addAccountToGroup(Service.ServiceType type, ApiAccount apiAccount){
        ClientGroup clientGroup = clientGroups.get(type);
        clientGroup.addClient(apiAccount.getId(),apiAccount.getClient());
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

    private  void checkSetting(BufferedReader br) {
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
            } else {
                throw new IllegalStateException("Unexpected value class: " + valueClass);
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



    public byte[] getSymKey() {
        return symKey;
    }

    public void setSymKey(byte[] symKey) {
        this.symKey = symKey;
    }

//    public Service.ServiceType[] getRequiredBasicServices() {
//        return requiredBasicServices;
//    }
//
//    public void setRequiredBasicServices(Service.ServiceType[] requiredBasicServices) {
//        this.requiredBasicServices = requiredBasicServices;
//    }


    public Service.ServiceType getServerType() {
        return serverType;
    }

    public void setServerType(Service.ServiceType serverType) {
        this.serverType = serverType;
    }

    public static Map<Service.ServiceType, List<FreeApi>> getFreeApiListMap() {
        return freeApiListMap;
    }

    public static void setFreeApiListMap(Map<Service.ServiceType, List<FreeApi>> freeApiListMap) {
        Settings.freeApiListMap = freeApiListMap;
    }
//
//    public Map<String, String> getAliasAccountIdMap() {
//        return aliasAccountIdMap;
//    }
//
//    public void setAliasAccountIdMap(Map<String, String> aliasAccountIdMap) {
//        this.aliasAccountIdMap = aliasAccountIdMap;
//    }
//
//    public Map<String, ApiAccount> getAliasAccountMap() {
//        return aliasAccountMap;
//    }
//
//    public void setAliasAccountMap(Map<String, ApiAccount> aliasAccountMap) {
//        this.aliasAccountMap = aliasAccountMap;
//    }
//
//    public Map<String, Object> getAliasClientMap() {
//        return aliasClientMap;
//    }
//
//    public void setAliasClientMap(Map<String, Object> aliasClientMap) {
//        this.aliasClientMap = aliasClientMap;
//    }

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

    public String getMyPubKey() {
        return myPubKey;
    }

    public void setMyPubKey(String myPubKey) {
        this.myPubKey = myPubKey;
    }

    public String getMyPriKeyCipher() {
        return myPriKeyCipher;
    }

    public void setMyPriKeyCipher(String myPriKeyCipher) {
        this.myPriKeyCipher = myPriKeyCipher;
    }

    public Map<String, String> getWatchFidPubKeyMap() {
        return watchFidPubKeyMap;
    }

    public void setWatchFidPubKeyMap(Map<String, String> watchFidPubKeyMap) {
        this.watchFidPubKeyMap = watchFidPubKeyMap;
    }

    public String addWatchingFids(BufferedReader br2, ApipClient apipClient, String clientName) {
        if(watchFidPubKeyMap == null) watchFidPubKeyMap = new HashMap<>();
        String fid = Inputer.inputFid(br2, "Input the watching FID:");
        if(fid == null) return null;
        String pubKey = null;
        if(apipClient != null) {
            pubKey = apipClient.getPubKey(fid, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        }
        if(pubKey == null) pubKey = Inputer.inputString(br2, "Input the watching FID's public key:", null);
        watchFidPubKeyMap.put(fid, pubKey);
        saveClientSettings(this.mainFid,clientName);
        return fid;
    }

//    private void initializeClientGroups() {
//        if (serviceTypeList != null) {
//            for (Service.ServiceType groupType : serviceTypeList) {
//                clientGroups.put(groupType, new ClientGroup(groupType));
//            }
//        }
//    }
//
//    public void setClientGroupTypeList(List<Service.ServiceType> types) {
//        serviceTypeList = types;
//    }

    public ClientGroup getClientGroup(Service.ServiceType type) {
        return clientGroups.get(type);
    }

    public void addClientToGroup(Service.ServiceType type, String apiAccountId, Object client) {
        ClientGroup group = clientGroups.get(type);
        if (group != null) {
            group.addClient(apiAccountId, client);
        }
    }


    public void initModulesMute(Object[] modules) {
        if(clientGroups==null)clientGroups = new HashMap<>();
        if(handlers==null)handlers = new HashMap<>();
        for (Object model : modules) {
            if (model instanceof String strModel) {
                try {
                    // Try to parse as ServiceType
                    Service.ServiceType serviceType = Service.ServiceType.valueOf(strModel);
                    initClientGroupMute(serviceType);
                } catch (IllegalArgumentException e) {
                    // Not a ServiceType, try HandlerType
                    Handler.HandlerType handlerType = Handler.HandlerType.valueOf(strModel);
                    initHandler(handlerType);
                }
            } else if (model instanceof Service.ServiceType type) {
                initClientGroupMute(type);
            } else if (model instanceof Handler.HandlerType type) {
                initHandler(type);
            }
        }
    }

    public void initModels() {
        if(clientGroups==null)clientGroups = new HashMap<>();
        handlers = new HashMap<>();
        for (Object model : modules) {
            if (model instanceof String strModel) {
                try {
                    // Try to parse as ServiceType
                    try {
                        Service.ServiceType type = Service.ServiceType.valueOf(strModel);
                        initClientGroup(type);
                        continue;
                    } catch (IllegalArgumentException ignored) {
                        // Not a ServiceType, try HandlerType
                    }
                    
                    // Try to parse as HandlerType
                    Handler.HandlerType type = Handler.HandlerType.valueOf(strModel);
                    initHandler(type);
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown module type: " + strModel);
                }
            } else if (model instanceof Service.ServiceType type) {
                initClientGroup(type);
            } else if (model instanceof Handler.HandlerType type) {
                initHandler(type);
            }
            if(isRunning ==null) isRunning = new AtomicBoolean(true);
        }
    }
//
//    public void initiateHandlers() {
//        if (requiredHandlers == null || requiredHandlers.length == 0) {
//            return;
//        }
//
//        handlers = new HashMap<>();
//
//        for (HandlerType type : requiredHandlers) {
//            switch (type) {
//                case CID -> handlers.put(type, new CidHandler(this));
//                case CASH -> handlers.put(type, new CashHandler(this));
//                case SESSION -> handlers.put(type, new SessionHandler(this));
//                case MAIL -> handlers.put(type, new MailHandler(this));
//                case CONTACT -> handlers.put(type, new ContactHandler(this));
//                case GROUP -> handlers.put(type, new GroupHandler(this));
//                case TEAM -> handlers.put(type, new TeamHandler(this));
//                case HAT -> handlers.put(type, new HatHandler(this));
//                case DISK -> handlers.put(type, new DiskHandler(this));
//                case TALK_ID -> handlers.put(type, new TalkIdHandler(this));
//                case TALK_UNIT -> handlers.put(type, new TalkUnitHandler(this));
//                case ACCOUNT -> handlers.put(type, new AccountHandler(this));
//                default -> throw new IllegalArgumentException("Unexpected handler type: " + type);
//            }
//        }
//    }

    public Handler<?> initHandler(Handler.HandlerType type) {
        if(handlers==null)handlers = new HashMap<>();
        Handler<?> handler = handlers.get(type);
        if(handler!=null)return handler;
        switch (type) {
            case CID -> handlers.put(type, new CidHandler(this));
            case CASH -> handlers.put(type, new CashHandler(this));
            case SESSION -> handlers.put(type, new SessionHandler(this));
            case NONCE -> handlers.put(type, new NonceHandler(this));
            case MAIL -> handlers.put(type, new MailHandler(this));
            case CONTACT -> handlers.put(type, new ContactHandler(this));
            case GROUP -> handlers.put(type, new GroupHandler(this));
            case TEAM -> handlers.put(type, new TeamHandler(this));
            case HAT -> handlers.put(type, new HatHandler(this));
            case DISK -> handlers.put(type, new DiskHandler(this)); 
            case TALK_ID -> handlers.put(type, new TalkIdHandler(this));
            case TALK_UNIT -> handlers.put(type, new TalkUnitHandler(this));
            case ACCOUNT -> handlers.put(type, new AccountHandler(this));
            case SECRETE -> handlers.put(type,new SecretHandler(this));
            case MEMPOOL -> handlers.put(type,new MempoolHandler(this));
            case WEBHOOK -> handlers.put(type,new WebhookHandler(this));
            default -> throw new IllegalArgumentException("Unexpected handler type: " + type);
        }
        System.out.println(type + " handler initiated.\n");
        return handlers.get(type);
    }

    public Handler<?> getHandler(Handler.HandlerType type) {
        return handlers != null ? handlers.get(type) : null;
    }


    public Map<Service.ServiceType, ClientGroup> getClientGroups() {
        return clientGroups;
    }

    public void setClientGroups(Map<Service.ServiceType, ClientGroup> clientGroups) {
        this.clientGroups = clientGroups;
    }

    public Map<Handler.HandlerType, Handler<?>> getHandlers() {
        return handlers;
    }

    public void addHandler(Handler<?> handler){
        if(this.getHandlers()==null)this.handlers = new HashMap<>();
        this.handlers.put(handler.getHandlerType(),handler);
    }

    public void setHandlers(Map<Handler.HandlerType, Handler<?>> handlers) {
        this.handlers = handlers;
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

    public Object[] getModules() {
        return modules;
    }

    public void setModules(Object[] modules) {
        this.modules = modules;
    }

    public String getListenPath() {
        return listenPath;
    }

    public void setListenPath(String listenPath) {
        this.listenPath = listenPath;
    }

    public void runAutoTasks() {
        runAutoTasks(this.runningModules);
    }

    public void runAutoTasks(Object[] runningModules) {
        if(isRunning!=null && isRunning.get()){
            System.out.println("Auto tasks have been running.");
            return;
        }

        System.out.println("Start auto tasks...");

        Thread apipManagerThread = new Thread(() -> {
            if (getListenPath() == null || getListenPath().isEmpty()) {
                log.warn("Cannot start APIP manager thread: listenPath is not set");
                return;
            }
            isRunning = new AtomicBoolean(true);
            log.info("Started auto tasks thread by watching path: {}", getListenPath());
            System.out.println("Started auto tasks thread by watching path: "+getListenPath());
            System.out.println();

            AccountHandler accountHandler = null;
            WebhookHandler webhookHandler = null;
            MempoolHandler mempoolHandler = null;
            NonceHandler nonceHandler = null;

            for(Object module : runningModules){
                if(module instanceof Handler.HandlerType handlerType){
                    Handler<?> handler = getHandler(handlerType);
                    handler.getIsRunning().set(true);
                    switch(handlerType){
                        case ACCOUNT -> accountHandler = (AccountHandler) handler;
                        case WEBHOOK -> webhookHandler = (WebhookHandler) handler;
                        case MEMPOOL -> mempoolHandler = (MempoolHandler) handler;
                        case NONCE -> nonceHandler = (NonceHandler) handler;
                        default -> throw new IllegalArgumentException("Unexpected value: " + handlerType);
                    }
                    System.out.println(handlerType + " handler is running.");
                }else if(module instanceof Service.ServiceType serviceType){
                    switch(serviceType){
                        default -> throw new IllegalArgumentException("Unexpected value: " + serviceType);
                    }
                }
            }

            while(true) {
                try{
                    ParseTools.waitForChangeInDirectory(getListenPath(), isRunning);

                    for (Object module : runningModules) {
                        if (module instanceof Handler.HandlerType handlerType) {
                            switch (handlerType) {
                                case ACCOUNT -> {if(accountHandler!=null) accountHandler.updateAll();}
                                case WEBHOOK -> {if(webhookHandler!=null) webhookHandler.pushWebhookData();}
                                case MEMPOOL -> {if(mempoolHandler!=null) mempoolHandler.checkMempool();}
                                case NONCE -> {if(nonceHandler!=null) nonceHandler.removeTimeOutNonce();}
                                default -> {
                                    log.warn("Cannot start thread: invalid handler type:{}",handlerType);
                                    return;
                                }
                            }
                        } else if (module instanceof Service.ServiceType serviceType) {
                            switch (serviceType) {
                                default -> {
                                    log.warn("Cannot start thread: invalid handler type:{}",serviceType);
                                    return;
                                }
                            }
                        }
                    }
                }catch(Exception e){
                    log.error("Error in auto tasks thread: {}", e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        apipManagerThread.setDaemon(true);
        apipManagerThread.start();
        System.out.println("Auto tasks thread is created.\n");
    }

    /**
     * Initialize client group for web server without user interaction
     */
    public Object initClientGroupMute(Service.ServiceType type) {
        System.out.println("Initiate "+ type +" accounts and clients for server...");

        // Preserve existing group if it exists
        ClientGroup group;
        if(clientGroups==null) {
            log.warn("Client groups are not initialized");
            return null;
        };
        group = clientGroups.get(type);
        if(group==null){
            log.warn("Client group for "+ type +" is not initialized");
            return null;
        }

        group.connectAllClients(config, this, symKey);

        if(type == REDIS) {
            jedisPool = (JedisPool) group.getClient();
            try {
                symKey = Configure.getSymKeyFromRedis(sid, config, jedisPool);
            }catch (Exception e){e.printStackTrace();}
        }
        if(sid==null && serverType!=null) {
            if(type==APIP || (type == ES && this.serverType == APIP)) {
                loadMyService(null, symKey, config);
            }
        }
        return getClient(type);
    }
}
