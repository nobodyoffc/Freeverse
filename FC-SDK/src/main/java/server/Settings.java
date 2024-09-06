package server;

import apip.apipData.CidInfo;
import apip.apipData.Fcdsl;
import clients.apipClient.ApipClient;
import clients.esClient.EsTools;
import clients.redisClient.RedisTools;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import configure.ApiProvider;
import configure.ServiceType;
import constants.*;
import crypto.CryptoDataByte;
import crypto.KeyTools;
import fch.FchMainNetwork;
import feip.feipData.Service;
import appTools.Inputer;
import appTools.Menu;
import appTools.Shower;
import configure.ApiAccount;
import configure.Configure;
import crypto.Decryptor;
import crypto.Encryptor;
import fcData.AlgorithmId;
import feip.feipData.ServiceMask;
import feip.feipData.serviceParams.*;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.StringTools;
import javaTools.http.AuthType;
import javaTools.http.HttpRequestMethod;
import org.bitcoinj.core.ECKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.serviceManagers.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static appTools.Inputer.*;
import static configure.Configure.*;
import static constants.ApiNames.Version1;
import static constants.Constants.UserDir;
import static constants.Strings.*;

public abstract class Settings {
    public static final long DEFAULT_WINDOW_TIME = 1000 * 60 * 5;
    final static Logger log = LoggerFactory.getLogger(Settings.class);
    protected String sid;
    protected String mainFid;
//    protected String mainFidPriKeyCipher;
    protected String listenPath;
    protected Long windowTime;
    protected Boolean fromWebhook;
    protected Boolean forbidFreeApi;
    protected Boolean shareApiAccount;
    protected String apipAccountId;
    protected String esAccountId;
    protected String redisAccountId;
    protected String nasaAccountId;
    protected Map<String,Long> bestHeightMap;
    protected transient Configure config;
    protected transient BufferedReader br;
    protected transient ApiAccount apipAccount;
    protected transient ApiAccount esAccount;
    protected transient ApiAccount redisAccount;
    protected transient ApiAccount nasaAccount;
    protected transient List<ApiAccount> paidAccountList;

    protected transient JedisPool jedisPool;
    private static String fileName;
    public static String SETTINGS_DOT_JSON = "settings.json";
    public static Map<ServiceType,List<FreeApi>> freeApiListMap;

    public Settings(Configure configure) {
        if(configure!=null) {
            this.config = configure;
            freeApiListMap = configure.getFreeApiListMap();
        }
    }

    public static ApipClient getFreeApipClient(){
        return getFreeApipClient(null);
    }
    public static ApipClient getFreeApipClient(BufferedReader br){
        ApipClient apipClient = new ApipClient();
        ApiAccount apipAccount = new ApiAccount();

        List<FreeApi> freeApiList = freeApiListMap.get(ServiceType.APIP);

        for(FreeApi freeApi : freeApiList){
            apipAccount.setApiUrl(freeApi.getUrlHead());
            apipClient.setApiAccount(apipAccount);
            apipClient.setUrlHead(freeApi.getUrlHead());
            if((boolean) apipClient.ping(Version1,HttpRequestMethod.GET,AuthType.FREE, ServiceType.APIP))
                return apipClient;
        }
        if(br !=null) {
            if (askIfYes(br, "Failed to get free APIP service. Add new?")) {
                do {
                    String url = fch.Inputer.inputString(br, "Input the urlHead of the APIP service:");
                    apipAccount.setApiUrl(url);
                    apipClient.setApiAccount(apipAccount);
                    if ((boolean) apipClient.ping(Version1,HttpRequestMethod.GET,AuthType.FREE, ServiceType.APIP)) {
                        FreeApi freeApi = new FreeApi(url,true, ServiceType.APIP);
                        freeApiList.add(freeApi);
                        return apipClient;
                    }
                } while (askIfYes(br, "Failed to ping this APIP Service. Try more?"));
            }
        }
        return null;
    }
    public static String makeFileName(String id){
        if(id==null)return SETTINGS_DOT_JSON;
        if(Hex.isHexString(id)) return id.substring(0,6)+"_" +SETTINGS_DOT_JSON;
        else return id.substring(id.length()-6)+"_" +SETTINGS_DOT_JSON;
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
            int input = Inputer.inputInteger(br, ask,0);
            if(input==0)input=1;
            jedis.hset(addSidBriefToName(sid,Strings.N_PRICE),api, String.valueOf(input));
        }
    }

    public static byte[] getMainFidPriKey(byte[] symKey, Settings settings) {
        Decryptor decryptor = new Decryptor();
        String mainFid = settings.getMainFid();
        String cipher = settings.getConfig().getFidCipherMap().get(mainFid);
        CryptoDataByte result = decryptor.decryptJsonBySymKey(cipher, symKey);
        if(result.getCode()!=0){
            System.out.println("Failed to decrypt the private key of "+mainFid+".");
//            return;
        }
        byte[] priKey= result.getData();
        return priKey;
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

    protected void setInitForClient(String fid, Configure config, BufferedReader br) {
        this.config= config;
        this.br= br;
        freeApiListMap = config.getFreeApiListMap();
        this.mainFid = fid;
        if(bestHeightMap==null)bestHeightMap=new HashMap<>();
    }

    public abstract Service initiateServer(String sid, byte[] symKey, Configure config, BufferedReader br);
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
                String owner = chooseOne(config.getOwnerList(), null, "Choose the owner:", br);
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
        this.mainFid = params.getAccount();
        if(config.getFidCipherMap().get(mainFid)==null)
            config.addUser(mainFid, symKey);

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

    protected void setInitForServer(String sid, Configure config, BufferedReader br) {
        this.config= config;
        this.br = br;
        if(sid==null) {
            System.out.println("No service yet. We will set it later.");
            Menu.anyKeyToContinue(br);
        }
        else this.sid = sid;
        freeApiListMap = config.getFreeApiListMap();
    }

    protected void writeServiceToRedis(Service service, Class<? extends Params> paramsClass) {
        try(Jedis jedis = jedisPool.getResource()) {
            String key = Settings.addSidBriefToName(service.getSid(),SERVICE);
            RedisTools.writeToRedis(service, key,jedis,service.getClass());
            String paramsKey = Settings.addSidBriefToName(sid,PARAMS);
            RedisTools.writeToRedis(service.getParams(), paramsKey,jedis,paramsClass);
        }
    }

//    protected void writeWebParamsToRedis(String sid,Map<String,Object> webParamsMap, JedisPool jedisPool) {
//        try(Jedis jedis = jedisPool.getResource()) {
//
//            symKeyCipher = jedis.hset(addSidBriefToName(sid, WEB_PARAMS),SYM_KEY_CIPHER,);
//
//            String key = Settings.addSidBriefToName(sid,PARAMS);
//            RedisTools.writeToRedis(params, key,jedis,tClass);
//        }
//    }

    public static boolean isWrongServiceType(Service service, String type) {
        if(!StringTools.isContainCaseInsensitive(service.getTypes(), type)) {
            System.out.println("\nWrong service type:"+ Arrays.toString(service.getTypes()));
            return true;
        }
        return false;
    }

    public abstract String initiateClient(String fid, byte[] symKey, Configure config, BufferedReader br);

    public Service loadMyService(String owner, String[] types){
        System.out.println("Load my services from APIP...");

        List<Service> serviceList = getMyServiceList(owner,true, types,(ApipClient)apipAccount.getClient());

        if (serviceList == null) {
            System.out.println("Load swap services wrong.");
            return null;
        }
        return selectMyService(serviceList);
    }

    private List<Service> getMyServiceList(String owner, boolean onlyActive, String[] types, ApipClient apipClient) {

        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.TYPES).setValues(types);
        fcdsl.addNewFilter().addNewTerms().addNewFields(FieldNames.OWNER).setValues(owner);
        if(onlyActive)fcdsl.addNewExcept().addNewTerms().addNewFields(Strings.ACTIVE).addNewValues(Values.FALSE);
        fcdsl.addSize(100);

        fcdsl.addSort(FieldNames.LAST_HEIGHT,Strings.DESC);
        return apipClient.serviceSearch(fcdsl, HttpRequestMethod.POST, AuthType.FC_SIGN_BODY);
    }

    public Service selectMyService(List<Service> serviceList){
        if(serviceList==null||serviceList.isEmpty())return null;

        showServiceList(serviceList);

        int choice = Shower.choose(br,0,serviceList.size());
        if(choice==0){
            return null;
        }
        return serviceList.get(choice-1);
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
        Shower.showDataTable(title,fields,widths,valueListList);
    }

    private static String inputOwner(BufferedReader br) {
        String input = fch.Inputer.inputGoodFid(br, "Set the owner FID. Enter to create a new one:");
        if ("".equals(input)) {
            ECKey ecKey = KeyTools.genNewFid(br);
            input = ecKey.toAddress(FchMainNetwork.MAINNETWORK).toBase58();
        }
        return input;
    }

    public static <T> T loadFromFile(String id,Class<T> tClass){
        if(id==null)return null;
        try {
            fileName = makeFileName(id);
            return JsonTools.readObjectFromJsonFile(Configure.getConfDir(), fileName, tClass);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    public ApiAccount checkFcAccount(String accountId, ServiceType serviceType, Configure config, byte[] symKey, ApipClient apipClient) {
        ApiAccount fcApiAccount;
        if(accountId ==null) {
            ApiProvider apiProvider = config.selectFcApiProvider((ApipClient)apipAccount.getClient(), serviceType);
            if(apiProvider==null)return null;
            config.getApiProviderMap().put(apiProvider.getId(),apiProvider);
            apiProvider.setType(serviceType);
            fcApiAccount = config.findAccountForTheProvider(apiProvider, mainFid, symKey,apipClient);
        }else {
            fcApiAccount = config.getApiAccountMap().get(accountId);
            fcApiAccount.setApipClient((ApipClient) apipAccount.getClient());
            Object result = fcApiAccount.connectApi(config.getApiProviderMap().get(fcApiAccount.getProviderId()), symKey);
            if(result==null) {
                if( Inputer.askIfYes(this.br,"Failed to connected the "+ serviceType +" server. Continue?")){
                    return null;
                }else {
                    System.exit(0);
                }
            }
            fcApiAccount.setClient(result);
        }
        return checkIfMainFidIsApiAccountUser(symKey,config,br,fcApiAccount, mainFid);
    }

    public String chooseFid(Configure config, BufferedReader br, byte[] symKey) {
        String fid = fch.Inputer.chooseOne(config.getFidCipherMap().keySet().toArray(new String[0]), null, "Choose fid:",br);
        if(fid==null)fid =config.addUser(symKey);
        return fid;
    }

    public void inputListenPath(BufferedReader br){
        while(true) {
            try {
                listenPath = promptAndSet(br, FieldNames.LISTEN_PATH, this.listenPath);
                if(listenPath!=null){
                    if(new File(listenPath).exists())return;
                }
                System.out.println("The directory '"+listenPath+"' does not exist.");
                System.out.println("A listenPath is necessary to wake up the order scanning. \nGenerally it can be set to the blocks path.");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public abstract void inputAll(BufferedReader br);

    public abstract void updateAll(BufferedReader br);

    public void writeToFile(String id){
        fileName = makeFileName(id);
        JsonTools.writeObjectToJsonFile(this,Configure.getConfDir(),fileName,false);
    }

    public abstract void saveSettings(String id);

    public void setting(byte[] symKey, BufferedReader br, @Nullable ServiceType serviceTypeOnlyForServer) {
        System.out.println("Setting...");
        while (true) {
            Menu menu = new Menu();
            menu.setName("Settings");
            menu.add("Reset password",
                    "Add API provider",
                    "Add API account",
                    "Update API provider",
                    "Update API account",
                    "Delete API provider",
                    "Delete API account",
                    "Reset Default APIs",
                    "Reset sharing API account switch",
                    "local settings"
            );
            menu.show();
            int choice = menu.choose(br);
            ApipClient apipClient = null;
            if(apipAccount!=null)apipClient = (ApipClient) apipAccount.getClient();
            switch (choice) {
                case 1 -> {
                    byte[] newSymKey=resetPassword(serviceTypeOnlyForServer, jedisPool);
                    if(newSymKey==null)break;
                    symKey = newSymKey;
                }
                case 2 -> config.addApiProviderAndConnect(symKey,null,apipClient);
                case 3 -> config.addApiAccount(null, symKey, apipClient);
                case 4 -> config.updateApiProvider(symKey,apipClient);
                case 5 -> config.updateApiAccount(config.chooseApiProviderOrAdd(config.getApiProviderMap(), apipClient),symKey,apipClient, shareApiAccount);
                case 6 -> config.deleteApiProvider(symKey,apipClient);
                case 7 -> config.deleteApiAccount(symKey,apipClient);
                case 8 -> resetApis(symKey,jedisPool,apipClient);
                case 9 -> updateShareApiAccount(br);
                case 10 -> resetLocalSettings(symKey);
                case 0 -> {
                    return;
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

//            CryptoDataByte cryptoDataByte = new Decryptor().decryptJsonBySymKey(config.getNonceCipher(), oldSymKey);
//            if(cryptoDataByte.getCode()!=0){
//                System.out.println("Wrong password. Try again.");
//                continue;
//            }
//            byte[] oldNonce = cryptoDataByte.getData();
//            if (oldNonce==null || ! config.getNonce().equals(Hex.toHex(oldNonce))) {
//                System.out.println("Password wrong. Reset it.");
//                config.setNonce(null);
//                continue;
//            }

            byte[] newPasswordBytes = Inputer.resetNewPassword(br);
            if(newPasswordBytes==null)return null;
            String newPasswordName = makePasswordHashName(newPasswordBytes);
            config.setPasswordName(newPasswordName);
            byte[] newNonce = BytesTools.getRandomBytes(16);
            config.setNonce(Hex.toHex(newNonce));
            byte[] newSymKey =  getSymKeyFromPasswordAndNonce(newPasswordBytes, newNonce);

//            CryptoDataByte cryptoDataByte1 = new Encryptor(AlgorithmId.FC_Aes256Cbc_No1_NrC7).encryptBySymKey(newNonce, newSymKey);
//            if(cryptoDataByte1.getCode()!=0){
//                System.out.println("Failed to encrypt with new symKey.");
//                continue;
//            }
//            String newNonceCipher = cryptoDataByte1.toJson();

//            config.setNonceCipher(newNonceCipher);

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
                if(apiAccount.getSession()!=null && apiAccount.getSession().getSessionKeyCipher()!=null){
                    String cipher = apiAccount.getSession().getSessionKeyCipher();
                    String newCipher = replaceCipher(cipher,oldSymKey,newSymKey);
                    apiAccount.getSession().setSessionKeyCipher(newCipher);
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
                Configure.checkWebConfig(config.getPasswordName(),sid,config, this,newSymKey, serviceType,jedisPool,br);

            BytesTools.clearByteArray(oldPasswordBytes);
            BytesTools.clearByteArray(newPasswordBytes);
            BytesTools.clearByteArray(oldSymKey);
            Menu.anyKeyToContinue(br);
            return newSymKey;
        }

    private String replaceCipher(String oldCipher, byte[] oldSymKey, byte[] newSymKey) {
        byte[] data = new Decryptor().decryptJsonBySymKey(oldCipher, oldSymKey).getData();
        return new Encryptor(AlgorithmId.FC_Aes256Cbc_No1_NrC7).encryptBySymKey(data,newSymKey).toJson();
    }

//    public void inputCounterPriKeyCipher(String account, byte[] symKey, BufferedReader br) {
//        while(true) {
//            System.out.println("Input the priKey cipher of your account");
//            byte[] priKey32 = KeyTools.inputCipherGetPriKey(br);
//            String fid = KeyTools.priKeyToFid(priKey32);
//            if (fid.equals(account)){
//                accountPriKeyCipher = fch.Inputer.makePriKeyCipher(priKey32, symKey);
//                break;
//            }
//            System.out.println("You inputted the priKey of fid. But your service account is:"+ account +". Try again.");
//        }
//    }

    public ApiAccount checkIfMainFidIsApiAccountUser(byte[] symKey, Configure config, BufferedReader br, ApiAccount apiAccount, String userFid) {
        if(mainFid==null||apiAccount==null)return apiAccount;

        String mainFidPriKeyCipher;
        if(mainFid.equals(apiAccount.getUserId())) {
            mainFidPriKeyCipher = apiAccount.getUserPriKeyCipher();
            config.getFidCipherMap().put(mainFid,mainFidPriKeyCipher);
            if(paidAccountList ==null) paidAccountList = new ArrayList<>();
            paidAccountList.add(apipAccount);
        }else{
            if(askIfYes(br,"Your service account "+mainFid+" is not the user of the API account "+apiAccount.getUserId()+". \nReset API account?")){
                while(true) {
                    apiAccount = config.getApiAccount(symKey, userFid, ServiceType.APIP,null, false);
                    if(mainFid.equals(apiAccount.getUserId())){
                        if(paidAccountList ==null) paidAccountList = new ArrayList<>();
                        this.apipAccount = apiAccount;
                        paidAccountList.add(apipAccount);
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

    public abstract void resetLocalSettings(byte[] symKey);

//    public abstract Object resetDefaultApi(byte[] symKey, ApiType apiType);
    public abstract void resetApis(byte[] symKey,JedisPool jedisPool,ApipClient apipClient);

    public void resetApi(byte[] symKey, ApipClient apipClient, ServiceType type) {
        System.out.println("Reset default API service...");
        ApiProvider apiProvider = config.chooseApiProviderOrAdd(config.getApiProviderMap(), type, apipClient);
        ApiAccount apiAccount = config.findAccountForTheProvider(apiProvider, mainFid, symKey, apipClient);
        if (apiAccount != null) {
            Object client = apiAccount.connectApi(config.getApiProviderMap().get(apiAccount.getProviderId()), symKey, br, null, config.getFidCipherMap());
            if (client != null) {
                config.saveConfig();
                System.out.println("Done.");
            } else System.out.println("Failed to connect the apiAccount: " + apiAccount.getApiUrl());
        } else System.out.println("Failed to get the apiAccount.");
        switch (type){
            case APIP -> apipAccountId=apiAccount.getId();
            case ES -> esAccountId=apiAccount.getId();
            case REDIS -> redisAccountId=apiAccount.getId();
        }
    }

    public abstract void close() throws IOException;

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

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getListenPath() {
        return listenPath;
    }

    public void setListenPath(String listenPath) {
        this.listenPath = listenPath;
    }

    public ApiAccount getApipAccount() {
        return apipAccount;
    }

    public void setApipAccount(ApiAccount apipAccount) {
        this.apipAccount = apipAccount;
    }

    public ApiAccount getEsAccount() {
        return esAccount;
    }

    public void setEsAccount(ApiAccount esAccount) {
        this.esAccount = esAccount;
    }

    public ApiAccount getRedisAccount() {
        return redisAccount;
    }

    public void setRedisAccount(ApiAccount redisAccount) {
        this.redisAccount = redisAccount;
    }

    public String getNasaAccountId() {
        return nasaAccountId;
    }

    public void setNasaAccountId(String nasaAccountId) {
        this.nasaAccountId = nasaAccountId;
    }

    public ApiAccount getNasaAccount() {
        return nasaAccount;
    }

    public void setNasaAccount(ApiAccount nasaAccount) {
        this.nasaAccount = nasaAccount;
    }

    public String getMainFid() {
        return mainFid;
    }

    public void setMainFid(String mainFid) {
        this.mainFid = mainFid;
    }


    public String getApipAccountId() {
        return apipAccountId;
    }

    public void setApipAccountId(String apipAccountId) {
        this.apipAccountId = apipAccountId;
    }

    public String getEsAccountId() {
        return esAccountId;
    }

    public void setEsAccountId(String esAccountId) {
        this.esAccountId = esAccountId;
    }

    @NotNull
    public static String makeWebhookListenPath(String sid, String methodName) {
        return System.getProperty(UserDir) + "/" + Settings.addSidBriefToName(sid, methodName);
    }

    protected void inputFromWebhook(BufferedReader br) {
        try {
            fromWebhook = promptAndSet(br, FieldNames.FROM_WEBHOOK,this.fromWebhook);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void inputForbidFreeApi(BufferedReader br) {
        try {
            setForbidFreeApi(promptAndSet(br, FieldNames.FORBID_FREE_API, this.isForbidFreeApi()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void inputShareApiAccount(BufferedReader br) {
        try {
            setShareApiAccount(promptAndSet(br, FieldNames.SHARE_API_ACCOUNT, this.isForbidFreeApi()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void updateForbidFreeApi(BufferedReader br) {
        try {
            setForbidFreeApi(promptAndSet(br, FieldNames.FORBID_FREE_API, this.isForbidFreeApi()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        saveSettings(mainFid);
        System.out.println("It's '"+ isForbidFreeApi() +"' now.");
        Menu.anyKeyToContinue(br);
    }

    protected void updateShareApiAccount(BufferedReader br) {
        try {
            setShareApiAccount(promptAndSet(br, FieldNames.SHARE_API_ACCOUNT, this.getShareApiAccount()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        saveSettings(mainFid);
        System.out.println("It's '"+ isForbidFreeApi() +"' now.");
        Menu.anyKeyToContinue(br);
    }


    protected void checkListenPath(BufferedReader br, String methodName)  {
        if(fromWebhook)listenPath = makeWebhookListenPath(sid, methodName);
        else inputListenPath(br);
    }

    protected void inputWindowTime(BufferedReader br) {
        try {
            if(windowTime==null || windowTime==0)windowTime = DEFAULT_WINDOW_TIME;
            windowTime = promptAndUpdate(br, WINDOW_TIME,this.windowTime);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void updateWindowTime(BufferedReader br) {
        try {
            if(windowTime==0)windowTime = DEFAULT_WINDOW_TIME;
            windowTime = promptAndUpdate(br, WINDOW_TIME,this.windowTime);
            saveSettings(sid);
            System.out.println("It's '"+windowTime+"' now.");
            Menu.anyKeyToContinue(br);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void updateListenPath(BufferedReader br) {
        try {
            listenPath = promptAndUpdate(br, FieldNames.LISTEN_PATH,this.listenPath);
            saveSettings(mainFid);
            System.out.println("It's '"+listenPath+"' now.");
            Menu.anyKeyToContinue(br);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Boolean isForbidFreeApi() {
        return forbidFreeApi;
    }

    public void setForbidFreeApi(boolean forbidFreeApi) {
        this.forbidFreeApi = forbidFreeApi;
    }

    protected void updateFromWebhook(BufferedReader br)  {
        try {
            fromWebhook = promptAndSet(br, FieldNames.FROM_WEBHOOK,this.fromWebhook);
            if(Boolean.TRUE.equals(fromWebhook))listenPath = makeWebhookListenPath(sid, ApiNames.NewCashByFids);
            saveSettings(mainFid);
            System.out.println("It's '"+fromWebhook +"' now.");
            Menu.anyKeyToContinue(br);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getRedisAccountId() {
        return redisAccountId;
    }

    public void setRedisAccountId(String redisAccountId) {
        this.redisAccountId = redisAccountId;
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

    public Long getWindowTime() {
        return windowTime;
    }

    public void setWindowTime(Long windowTime) {
        this.windowTime = windowTime;
    }

    public Boolean getFromWebhook() {
        return fromWebhook;
    }

    public void setFromWebhook(Boolean fromWebhook) {
        this.fromWebhook = fromWebhook;
    }

    public Boolean getForbidFreeApi() {
        return forbidFreeApi;
    }

    public void setForbidFreeApi(Boolean forbidFreeApi) {
        this.forbidFreeApi = forbidFreeApi;
    }

    public Boolean getShareApiAccount() {
        return shareApiAccount;
    }

    public void setShareApiAccount(Boolean shareApiAccount) {
        this.shareApiAccount = shareApiAccount;
    }
}
