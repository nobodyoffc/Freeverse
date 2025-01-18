package appTools;

import apip.apipData.CidInfo;
import clients.Client;
import clients.ApipClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import configure.*;
import constants.*;
import crypto.CryptoDataByte;
import crypto.KeyTools;
import feip.feipData.Service;
import crypto.Decryptor;
import crypto.Encryptor;
import fcData.AlgorithmId;
import fch.fchData.Address;
import feip.feipData.ServiceMask;
import feip.feipData.serviceParams.*;
import tools.*;
import tools.http.AuthType;
import tools.http.RequestMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.FreeApi;
import server.serviceManagers.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

import static appTools.Inputer.*;
import static configure.Configure.*;
import static configure.ServiceType.*;
import static constants.ApiNames.Version1;
import static constants.Constants.UserDir;
import static constants.Strings.*;

import clients.ClientGroup;

public class Settings {
    public final static Logger log = LoggerFactory.getLogger(Settings.class);

    public static final String DEFAULT_AVATAR_BASE_PATH = System.getProperty("user.dir") + "/avatar/elements/";
    public static final String DEFAULT_AVATAR_FILE_PATH = System.getProperty("user.dir") + "/avatar/png/";

    public static final String LISTEN_PATH = "listenPath";
    public static final String OP_RETURN_PATH = "opReturnPath";
    public static final String FORBID_FREE_API = "forbidFreeApi";
    public static final String FROM_WEBHOOK = "fromWebhook";
    public static final String WINDOW_TIME = "windowTime";
    public static final String  SHARE_API = "shareApi";
    public static final String  LOCAL_DATA_PATH = "localDataPath";
    public static final String SCAN_MEMPOOL = "scanMempool";
    public static final String AVATAR_ELEMENTS_PATH = "avatarElementsPath";
    public static final String AVATAR_PNG_PATH = "avatarPngPath";
    public static final String IGNORE_OP_RETURN = "ignoreOpReturn";
    public static final Long DEFAULT_WINDOW_TIME = 300000L;
    public static Map<ServiceType,List<FreeApi>> freeApiListMap;
    private String[] serviceAliases;

    private static String fileName;
    private transient Configure config;
    private transient BufferedReader br;
    private transient String clientDataFileName;
//    private transient String serverDataFileName;

    private  ServiceType serverType;
    private Map<String,Long> bestHeightMap;
    private Service service;

    private String sid; //For server
    private String mainFid; //For Client
    private String myPubKey;
    private String myPriKeyCipher;
    private Map<String,String>watchFidPubKeyMap;

    //Settings
    private Map<String,Object> settingMap;
    private List<ClientGroup.ClientGroupType> requiredGroupTypeList;


    //Services
    private Map<String,String> aliasAccountIdMap;
    private transient Map<ClientGroup.ClientGroupType, ClientGroup> clientGroups;
    private transient Map<String,ApiAccount> aliasAccountMap;
    private transient Map<String,Object> aliasClientMap;

    private transient JedisPool jedisPool;

    private  transient List<ApiAccount> paidAccountList;
    private  transient byte[] symKey;



    public Settings(Configure configure) {
        if(configure!=null) {
            this.config = configure;
            this.br =configure.getBr();
            freeApiListMap = configure.getFreeApiListMap();
            aliasAccountIdMap = new HashMap<>();
            aliasAccountMap = new HashMap<>();
            aliasClientMap = new HashMap<>();
            settingMap = new HashMap<>();
            this.clientGroups = new Map<ClientGroup.ClientGroupType, ClientGroup>() {
                @Override
                public int size() {
                    return 0;
                }

                @Override
                public boolean isEmpty() {
                    return false;
                }

                @Override
                public boolean containsKey(Object key) {
                    return false;
                }

                @Override
                public boolean containsValue(Object value) {
                    return false;
                }

                @Override
                public ClientGroup get(Object key) {
                    return null;
                }

                @Nullable
                @Override
                public ClientGroup put(ClientGroup.ClientGroupType key, ClientGroup value) {
                    return null;
                }

                @Override
                public ClientGroup remove(Object key) {
                    return null;
                }

                @Override
                public void putAll(@NotNull Map<? extends ClientGroup.ClientGroupType, ? extends ClientGroup> m) {

                }

                @Override
                public void clear() {

                }

                @NotNull
                @Override
                public Set<ClientGroup.ClientGroupType> keySet() {
                    return null;
                }

                @NotNull
                @Override
                public Collection<ClientGroup> values() {
                    return null;
                }

                @NotNull
                @Override
                public Set<Entry<ClientGroup.ClientGroupType, ClientGroup>> entrySet() {
                    return null;
                }
            };
            initializeRequiredGroups();
        }
    }

    public Settings(Configure configure,String[] serviceAliases,Map<String,Object> settingMap) {
        if(configure!=null) {
            this.config = configure;
            this.br =configure.getBr();
            freeApiListMap = configure.getFreeApiListMap();
            aliasAccountIdMap = new HashMap<>();
            aliasAccountMap = new HashMap<>();
            aliasClientMap = new HashMap<>();
            this.settingMap = settingMap;
            this.serviceAliases = serviceAliases;
        }
    }

    public Settings(Configure configure, ServiceType serverType, String[] serviceAliases, Map<String,Object> settingMap) {
        if(configure!=null) {
            this.config = configure;
            this.br =configure.getBr();
            freeApiListMap = configure.getFreeApiListMap();
            aliasAccountIdMap = new HashMap<>();
            aliasAccountMap = new HashMap<>();
            aliasClientMap = new HashMap<>();
            this.settingMap = settingMap;
            this.serverType = serverType;
            this.serviceAliases = serviceAliases;
        }
    }

    public void initiateServer(String sid, byte[] symKey, Configure config){
        if(aliasAccountMap==null)aliasAccountMap = new HashMap<>();
        if(aliasClientMap==null)aliasClientMap = new HashMap<>();
        if(this.config==null)this.config = config;
        this.symKey = symKey;

        System.out.println("Initiating server settings...");

        br = config.getBr();

        if(sid==null)checkSetting(br);

        mainFid = config.getServiceDealer(sid,symKey);

        initServices(serviceAliases, symKey, config);

        Object client = getClient(REDIS);

        if(client!=null) jedisPool = (JedisPool) client;

        service = loadMyService(sid, symKey, config);
        if(service==null){
            System.out.println("Failed to load service information");
            return;
        }
//        sid = service.getSid();
//        serverDataFileName = makeServerDataDir(sid, serverType);

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
        if(aliasAccountMap==null)aliasAccountMap = new HashMap<>();
        if(aliasClientMap==null)aliasClientMap = new HashMap<>();
        if(this.config==null)this.config = config;
        this.symKey = symKey;

        System.out.println("Initiating mute server settings...");

        br = config.getBr();

        checkSetting(br);

        initServices(serviceAliases, symKey, config);

        saveServerSettings(serverName);

        Configure.saveConfig();
    }

    public String initiateClient(String fid, String clientName, byte[] symKey, Configure config, BufferedReader br){
        System.out.println("Initiating Client settings...");

        this.config = config;
        this.symKey = symKey;
        this.br = config.getBr();

        if(aliasAccountMap==null)aliasAccountMap = new HashMap<>();
        if(aliasClientMap==null)aliasClientMap = new HashMap<>();
        if(this.config==null)this.config = config;

        setInitForClient(fid, config, br);

        checkSetting(br);

        initServices(serviceAliases, symKey, config);

        clientDataFileName = FileTools.makeFileName(mainFid,clientName,DATA,DOT_JSON);

        setMyKeys(symKey, config);

        saveClientSettings(mainFid,clientName);
        Configure.saveConfig();
        return mainFid;
    }

    public void close() {
        try {
            br.close();
            BytesTools.clearByteArray(symKey);
            for(String alias :aliasClientMap.keySet()){
                switch (ServiceType.typeInString(alias)){
                    case REDIS -> ((JedisPool)aliasClientMap.get(alias)).close();
                    case ES -> aliasAccountMap.get(alias).closeEs();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    public Service loadMyService(String sid, byte[] symKey, Configure config) {
        Service service;
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

        writeServiceToRedis(service, ApipParams.class);
        return service;
    }

    public Object getClient(ServiceType serviceType) {
        Object client=null;
        for(String alias:aliasClientMap.keySet()){
            if(alias.toLowerCase().contains(serviceType.name().toLowerCase())) {
                client =  aliasClientMap.get(alias);
                if(client!=null)break;
            }
        }
        return client;
    }

    public ApiAccount getApiAccount(ServiceType serviceType) {
        ApiAccount apiAccount=null;
        for(String alias:aliasAccountMap.keySet()){
            if(alias.toLowerCase().contains(serviceType.name().toLowerCase())) {
                apiAccount =  aliasAccountMap.get(alias);
                if(apiAccount!=null)break;
            }
        }
        return apiAccount;
    }

    public String getApiAccountId(ServiceType serviceType) {
        for(String alias:aliasAccountIdMap.keySet()){
            if(alias.toLowerCase().contains(serviceType.name().toLowerCase())) {
                return aliasAccountIdMap.get(alias);
            }
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
            if((boolean) apipClient.ping(Version1, RequestMethod.GET,AuthType.FREE, APIP))
                return apipClient;
        }
        if(br !=null) {
            if (askIfYes(br, "Failed to get free APIP service. Add new?")) {
                do {
                    String url = fch.Inputer.inputString(br, "Input the urlHead of the APIP service:");
                    apipAccount.setApiUrl(url);
                    apipClient.setApiAccount(apipAccount);
                    if ((boolean) apipClient.ping(Version1, RequestMethod.GET,AuthType.FREE, APIP)) {
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
        String finalName;
        finalName = (sid.substring(0,6) + "_" + name);
        return finalName;
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

    private void initServices(String[] serviceAliases, byte[] symKey, Configure config) {
        for(String accountAlias : serviceAliases){
            String apiAccountId = aliasAccountIdMap.get(accountAlias);
            ServiceType type = ServiceType.typeInString(accountAlias);
            initService(accountAlias,type,apiAccountId,symKey, config);
        }
    }

    public Service getMyService(String sid, byte[] symKey, Configure config, BufferedReader br, ApipClient apipClient, Class<?> paramsClass, ServiceType serviceType) {
        return getMyService(sid, symKey, config, br, apipClient,null,paramsClass, serviceType);
    }
    public Service getMyService(String sid, byte[] symKey, Configure config, BufferedReader br, ElasticsearchClient esClient,  Class<?> paramsClass, ServiceType serviceType) {
        return getMyService(sid, symKey, config, br, null,esClient,paramsClass, serviceType);
    }
    public Service getMyService(String sid, byte[] symKey, Configure config, BufferedReader br, ApipClient apipClient, ElasticsearchClient esClient, Class<?> paramsClass, ServiceType serviceType) {
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

    private static Service askIfPublishNewService(String sid, byte[] symKey, BufferedReader br, ServiceType serviceType, ApipClient apipClient, ElasticsearchClient esClient) {
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


    private  void initService(String accountAlias, ServiceType type, String apipAccountId, byte[] symKey, Configure config) {
        ApiAccount apiAccount = config.checkAPI(apipAccountId, mainFid, type,symKey);
        switch (type){
            case APIP,TALK,SWAP_HALL,MAP -> apiAccount = checkIfMainFidIsApiAccountUser(symKey,config,br,apiAccount, mainFid);
        }
        if(apiAccount.getClient()!=null){
            apipAccountId=apiAccount.getId();
            aliasAccountMap.put(accountAlias,apiAccount);
            aliasAccountIdMap.put(accountAlias,apipAccountId);
            aliasClientMap.put(accountAlias,apiAccount.getClient());
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

    public void setting(byte[] symKey, BufferedReader br, @Nullable ServiceType serviceTypeOnlyForServer) {
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

    public byte[] resetPassword(@Nullable ServiceType serviceType, JedisPool jedisPoolOnlyForServer){
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
            Configure.checkWebConfig(sid,config, this,newSymKey, serviceType,jedisPool,br);

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
                    apiAccount = config.getApiAccount(symKey, userFid, APIP,null, false);
                    if(mainFid.equals(apiAccount.getUserId())){
                        if(paidAccountList ==null) paidAccountList = new ArrayList<>();
                        paidAccountList.add(apiAccount);
                        String apiProvideType = config.getApiProviderMap().get(apiAccount.getProviderId()).getType().name();
                        aliasAccountIdMap.put(apiProvideType,apiAccount.getId());
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
        String alias = Inputer.chooseOne(this.serviceAliases,null,"Choose the Service:",br);

        ServiceType type = ServiceType.typeInString(alias);

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

        freshAliasMaps(alias, apiAccount);
    }

    private void freshAliasMaps(String alias, ApiAccount apiAccount) {
        aliasAccountMap.put(alias, apiAccount);
        aliasAccountIdMap.put(alias, apiAccount.getId());
        aliasClientMap.put(alias, apiAccount.getClient());
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

    public JedisPool getJedisPool() {
        return jedisPool;
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

    public String[] getServiceAliases() {
        return serviceAliases;
    }

    public void setServiceAliases(String[] serviceAliases) {
        this.serviceAliases = serviceAliases;
    }


    public ServiceType getServerType() {
        return serverType;
    }

    public void setServerType(ServiceType serverType) {
        this.serverType = serverType;
    }

    public static Map<ServiceType, List<FreeApi>> getFreeApiListMap() {
        return freeApiListMap;
    }

    public static void setFreeApiListMap(Map<ServiceType, List<FreeApi>> freeApiListMap) {
        Settings.freeApiListMap = freeApiListMap;
    }

    public Map<String, String> getAliasAccountIdMap() {
        return aliasAccountIdMap;
    }

    public void setAliasAccountIdMap(Map<String, String> aliasAccountIdMap) {
        this.aliasAccountIdMap = aliasAccountIdMap;
    }

    public Map<String, ApiAccount> getAliasAccountMap() {
        return aliasAccountMap;
    }

    public void setAliasAccountMap(Map<String, ApiAccount> aliasAccountMap) {
        this.aliasAccountMap = aliasAccountMap;
    }

    public Map<String, Object> getAliasClientMap() {
        return aliasClientMap;
    }

    public void setAliasClientMap(Map<String, Object> aliasClientMap) {
        this.aliasClientMap = aliasClientMap;
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

    private void initializeRequiredGroups() {
        if (requiredGroupTypeList != null) {
            for (ClientGroup.ClientGroupType groupType : requiredGroupTypeList) {
                clientGroups.put(groupType, new ClientGroup(groupType));
            }
        }
    }

    public void setRequiredGroupTypeList(List<ClientGroup.ClientGroupType> types) {
        requiredGroupTypeList = types;
    }

    public ClientGroup getClientGroup(ClientGroup.ClientGroupType type) {
        return clientGroups.get(type);
    }

    public void addClientToGroup(ClientGroup.ClientGroupType type, String apiAccountId, Client client) {
        ClientGroup group = clientGroups.get(type);
        if (group != null) {
            group.addClient(apiAccountId, client);
        }
    }
}
