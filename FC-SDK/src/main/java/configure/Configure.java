package configure;

import appTools.Menu;
import feip.feipData.ServiceMask;
import feip.feipData.serviceParams.*;
import appTools.Inputer;
import clients.ApipClient;
import feip.feipData.Service;
import appTools.Shower;
import tools.*;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.google.gson.Gson;
import constants.FieldNames;
import constants.IndicesNames;
import crypto.*;
import fcData.AlgorithmId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.FreeApi;
import server.serviceManagers.ApipManager;
import appTools.Settings;
import server.serviceManagers.ChatManager;
import server.serviceManagers.DiskManager;
import server.serviceManagers.SwapHallManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static appTools.Inputer.askIfYes;
import static appTools.Inputer.confirmDefault;
import static constants.FieldNames.*;
import static constants.FieldNames.TYPES;
import static constants.Strings.*;
import static constants.Strings.SETTINGS;
import static fch.Inputer.inputPriKey;
import static fch.Inputer.makePriKeyCipher;
import static appTools.Settings.addSidBriefToName;

public class Configure {
    protected String nonce;
    protected String passwordName;
    protected List<String> ownerList;  //Owners for servers.
    protected Map<String,String> fidCipherMap; //Users for clients or accounts for servers.
    private String esAccountId;

    private Map<String, ServiceMask> myServiceMaskMap;
    private Map<String, ApiProvider> apiProviderMap;
    private Map<String, ApiAccount> apiAccountMap;
    private transient byte[] symKey;

    private transient ApipClient apipClient;
    final static Logger log = LoggerFactory.getLogger(Configure.class);
    public static String CONFIG_DOT_JSON = "config.json";

    private static BufferedReader br;
//    private List<FreeApi> freeApipUrlList;
    private Map<Service.ServiceType,List<FreeApi>> freeApiListMap;
    public static Map<String,Configure> configureMap;
//    public static List<String> freeApipUrls;

    public Configure(BufferedReader br) {
        Configure.br =br;
        initFreeApiListMap();
    }

    public Configure() {
    }

    public static void checkWebConfig(Settings settings) {
        String fileName = makeConfigFileName(settings.getServerType().name());
        if (!new File(fileName).exists()) {
            if(askIfYes(br,"\nCreate config file for the web server?")) {
                makeWebConfig(settings);
                Menu.anyKeyToContinue(br);
            }
        }else {
            WebServerConfig webServerConfig;
            try {
                webServerConfig = JsonTools.readJsonFromFile(fileName, WebServerConfig.class);
            } catch (IOException e) {
                System.out.println("Failed to read web config file.");
                return;
            }
            JedisPool jedisPool = (JedisPool) settings.getClient(Service.ServiceType.REDIS);
            byte[] symKeyForWebServer = getSymKeyFromRedis(webServerConfig.getSid(),settings.getConfig(),jedisPool);
            if(symKeyForWebServer== null||!Arrays.equals(symKeyForWebServer,settings.getSymKey())) {
                log.debug("Remake web config file...");
                makeWebConfig(settings);
                Menu.anyKeyToContinue(br);
            }
        }
    }



    @NotNull
    public static String makeConfigFileName(String type) {
        return type + "_" + CONFIG + DOT_JSON;
    }

    public static void makeWebConfig( Settings settings) {
        String fileName = makeConfigFileName(settings.getServerType().name());
        String sid = settings.getSid();
        WebServerConfig webServerConfig = new WebServerConfig();
        webServerConfig.setPasswordName(settings.getConfig().getPasswordName());
        webServerConfig.setSid(sid);
        String confDir = getConfDir();
        webServerConfig.setConfigPath(Path.of(confDir,CONFIG+DOT_JSON).toString());
        webServerConfig.setSettingPath(Path.of(confDir,addSidBriefToName(sid,SETTINGS+DOT_JSON)).toString());
        webServerConfig.setDataPath(Settings.getLocalDataDir(sid));
        webServerConfig.setDbPath(settings.getDbDir());
        CryptoDataByte result = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7).encryptByPassword(settings.getSymKey(), settings.getConfig().getNonce().toCharArray());
        if(result.getCode()!=0){
            System.out.println("Failed to encrypt symKey for web server.");
            return;
        }
        String symKeyCipher = result.toJson();
        JedisPool jedisPool = (JedisPool)settings.getClient(Service.ServiceType.REDIS);
        try(Jedis jedis = jedisPool.getResource()){
            jedis.hset(addSidBriefToName(sid,WEB_PARAMS),SYM_KEY_CIPHER,symKeyCipher);
            jedis.hset(addSidBriefToName(sid,WEB_PARAMS),FORBID_FREE_API, String.valueOf(settings.getSettingMap().get(FORBID_FREE_API)));
        }
        JsonTools.writeObjectToJsonFile(webServerConfig,fileName,false);

        System.out.println("Copy the file of '"+fileName+"' to the bin directory of Tomcat.");
    }

    public static byte[] getSymKeyFromRedis(String sid, Configure configure, JedisPool jedisPool) {
        byte[] symKey;
        String symKeyCipher;
        try(Jedis jedis = jedisPool.getResource()){
            symKeyCipher = jedis.hget(addSidBriefToName(sid, WEB_PARAMS),SYM_KEY_CIPHER);
        }
        CryptoDataByte result = new Decryptor().decryptJsonByPassword(symKeyCipher, configure.getNonce().toCharArray());
        if(result.getCode()!=0){
            log.debug("Failed to decrypt symKey for web server:"+result.getMessage());
            System.out.println("Check the config file for web server.");
            return null;
        }
        symKey = result.getData();
        return symKey;
    }

    public void initFreeApiListMap(){
        if (freeApiListMap == null) {
            freeApiListMap = new HashMap<>();
        }
        if(freeApiListMap.get(Service.ServiceType.APIP)==null){
            ArrayList<FreeApi> freeApipList = new ArrayList<>();
            FreeApi freeApiHelp = new FreeApi("https://help.cash/APIP",true, Service.ServiceType.APIP);
            FreeApi freeApiApip = new FreeApi("https://apip.cash/APIP",true, Service.ServiceType.APIP);
            FreeApi freeApiLocal8080 = new FreeApi("http://127.0.0.1:8080/APIP",true, Service.ServiceType.APIP);
            FreeApi freeApiLocal8081 = new FreeApi("http://127.0.0.1:8081/APIP",true, Service.ServiceType.APIP);
            freeApipList.add(freeApiApip);
            freeApipList.add(freeApiHelp);
            freeApipList.add(freeApiLocal8080);
            freeApipList.add(freeApiLocal8081);
            freeApiListMap.put(Service.ServiceType.APIP,freeApipList);
        }

        if(freeApiListMap.get(Service.ServiceType.DISK)==null){
            ArrayList<FreeApi> freeApipList = new ArrayList<>();
            FreeApi freeApiHelp = new FreeApi("https://help.cash/FCSK",true, Service.ServiceType.TALK);
            FreeApi freeApiApip = new FreeApi("https://apip.cash/FCSK",true, Service.ServiceType.TALK);
            FreeApi freeApiLocal8080 = new FreeApi("http://127.0.0.1:8080/FCSK",true, Service.ServiceType.TALK);
            FreeApi freeApiLocal8081 = new FreeApi("http://127.0.0.1:8081/FCSK",true, Service.ServiceType.TALK);
            freeApipList.add(freeApiHelp);
            freeApipList.add(freeApiApip);
            freeApipList.add(freeApiLocal8080);
            freeApipList.add(freeApiLocal8081);
            freeApiListMap.put(Service.ServiceType.TALK,freeApipList);
        }
        if(freeApiListMap.get(Service.ServiceType.ES)==null){
            ArrayList<FreeApi> freeApipList = new ArrayList<>();
            FreeApi freeApiLocal9200 = new FreeApi("http://127.0.0.1:9200",true, Service.ServiceType.ES);
            FreeApi freeApiLocal9201 = new FreeApi("http://127.0.0.1:9201",true, Service.ServiceType.ES);
            freeApipList.add(freeApiLocal9200);
            freeApipList.add(freeApiLocal9201);
            freeApiListMap.put(Service.ServiceType.ES,freeApipList);
        }
        if(freeApiListMap.get(Service.ServiceType.REDIS)==null){
            ArrayList<FreeApi> freeApipList = new ArrayList<>();
            FreeApi freeApiLocal1 = new FreeApi("http://127.0.0.1:6379",true, Service.ServiceType.REDIS);
            FreeApi freeApiLocal2 = new FreeApi("http://127.0.0.1:6380",true, Service.ServiceType.REDIS);
            freeApipList.add(freeApiLocal1);
            freeApipList.add(freeApiLocal2);
            freeApiListMap.put(Service.ServiceType.REDIS,freeApipList);
        }
    }

//    public String initiateClient(byte[] symKey) {
//        System.out.println("Initiating config...");
//        String fid;
//        if (apiProviderMap == null) apiProviderMap = new HashMap<>();
//        if (apiAccountMap == null) apiAccountMap = new HashMap<>();
//        if (fidCipherMap == null) {
//            fidCipherMap = new HashMap<>();
//            addUser(symKey);
//        }
//
//        if(fidCipherMap ==null || fidCipherMap.isEmpty())
//            return null;
//        fid = (String) Inputer.chooseOne(fidCipherMap.keySet().toArray(), null, "Choose a user:", br);
//        if(fid==null)fid = addUser(symKey);
//        saveConfig();
//        return fid;
//    }

    public String addUser(byte[] symKey) {
        return addUser(null,symKey);

    }

    public String addUser(String fid,byte[] symKey) {
        if(fidCipherMap.get(fid)!=null){
            if(!Inputer.askIfYes(br,fid +" exists. Replace it?"))return fid;
        }

        if(fid==null)System.out.println("Add new user...");
        else System.out.println("Add "+fid+" to users...");
        byte[] priKeyBytes;
        while(true) {
            String newFid;
            try {
                priKeyBytes = inputPriKey(br);
                newFid = KeyTools.priKeyToFid(priKeyBytes);
            }catch (Exception e){
                System.out.println("Something wrong. Try again.");
                continue;
            }
            if(fid==null) {
                    fid = newFid;
                    break;
            }
            if (newFid.equals(fid)) break;
            System.out.println("The cipher is of "+newFid+" instead of "+fid+". \nTry again.");
        }
        String cipher = makePriKeyCipher(priKeyBytes, symKey);
        fidCipherMap.put(fid, cipher);
        saveConfig();
        return fid;
    }

    @SuppressWarnings("unused")
    private Service chooseOwnerService(String owner, byte[] symKey, Service.ServiceType serviceType, ApipClient apipClient) {
        return chooseOwnerService(owner,symKey, serviceType,null, apipClient);
    }
    @SuppressWarnings("unused")
    private Service chooseOwnerService(String owner, byte[] symKey, Service.ServiceType serviceType, ElasticsearchClient esClient) {
        return chooseOwnerService(owner,symKey, serviceType,esClient,null);
    }

    public List<Service> getServiceListByOwnerAndTypeFromEs(String owner, @Nullable Service.ServiceType type, ElasticsearchClient esClient) {
        List<Service> serviceList;

        SearchRequest.Builder sb = new SearchRequest.Builder();
        sb.index(IndicesNames.SERVICE);

        BoolQuery.Builder bb = QueryBuilders.bool();
        bb.must(b->b.term(t->t.field(OWNER).value(owner)));
        bb.must(m->m.term(t1->t1.field(CLOSED).value(false)));
        if(type!=null)bb.must(m2->m2.match(m3->m3.field(TYPES).query(type.name())));
        BoolQuery boolQuery = bb.build();
        sb.query(q->q.bool(boolQuery));
        sb.size(EsTools.READ_MAX);
        SearchResponse<Service> result;
        try {
            result = esClient.search(sb.build(), Service.class);
        } catch (IOException e) {
            return null;
        }
        serviceList = new ArrayList<>();
        if(result==null || result.hits()==null)return null;
        for(Hit<Service> hit : result.hits().hits()){
            serviceList.add(hit.source());
        }
        return serviceList;
    }
    @Nullable
    public Service chooseOwnerService(String owner, byte[] symKey, Service.ServiceType type, ElasticsearchClient esClient, ApipClient apipClient) {
        List<Service> serviceList;

        if(esClient==null)
            serviceList = apipClient.getServiceListByOwnerAndType(owner,type);
        else serviceList = getServiceListByOwnerAndTypeFromEs(owner,type,esClient);

        if(serviceList==null || serviceList.isEmpty()){
            System.out.println("No any service on chain of the owner.");
            return null;
        }

        Service service;
        if(symKey!=null)service = selectService(serviceList, symKey, apipClient==null?null:apipClient.getApiAccount());
        else service = selectService(serviceList);
        if(service==null) System.out.println("Failed to get the service.");
//        else {
//            String sid = service.getSid();
//            Params params;
//            switch (type){
//                case APIP -> params = Params.getParamsFromService(service,ApipParams.class);
//                case DISK -> params = Params.getParamsFromService(service,DiskParams.class);
//                case SWAP_HALL -> params = Params.getParamsFromService(service,SwapParams.class);
//                case TALK -> params = Params.getParamsFromService(service, TalkParams.class);
//                default -> params=null;
//            }
//            if(params!=null) {
//                ServiceMask serviceMask = ServiceMask.ServiceToMask(service,params.getAccount());
//                myServiceMaskMap.put(sid, serviceMask);
//            }
//            saveConfig();
//        }
        return service;
    }

    public static Service selectService(List<Service> serviceList,byte[] symKey,ApiAccount apipAccount){
        if(serviceList==null||serviceList.isEmpty())return null;

        showServices(serviceList);

        int choice = Shower.choose(br,0,serviceList.size());
        if(choice==0){
            if(Inputer.askIfYes(br,"Publish a new service?")){
                Service.ServiceType type = Inputer.chooseOne(Service.ServiceType.values(), null, "Choose a type", br);
                switch (type){
                    case APIP -> new ApipManager(null,apipAccount,br,symKey,ApipParams.class).publishService();
                    case DISK -> new DiskManager(null,apipAccount,br,symKey,DiskParams.class).publishService();
                    case SWAP_HALL -> new SwapHallManager(null,apipAccount,br,symKey,SwapParams.class).publishService();
                    case TALK -> new ChatManager(null,apipAccount,br,symKey, TalkParams.class).publishService();
                    default -> {
                        System.out.println("The type of the service is not supported:"+type);
                        return null;
                    }
                }
                System.out.println("Wait for a few minutes and try to start again.");
                System.exit(0);
            }
        }
        return serviceList.get(choice-1);
    }

    public static Service selectService(List<Service> serviceList){
        if(serviceList==null||serviceList.isEmpty())return null;
        showServices(serviceList);
        int choice = Shower.choose(br,0,serviceList.size());
        if(choice==0)return null;
        return serviceList.get(choice-1);
    }
    public static void showServices(List<Service> serviceList) {
        String title = "Services";
        String[] fields = new String[]{FieldNames.STD_NAME, TYPES,FieldNames.ID};
        int[] widths = new int[]{24,24,64};
        List<List<Object>> valueListList = new ArrayList<>();
        for(Service service : serviceList){
            List<Object> valueList = new ArrayList<>();
            valueList.add(service.getStdName());
            StringBuilder sb = new StringBuilder();
            for(String type:service.getTypes()){
                sb.append(type);
                sb.append(",");
            }
            if(sb.length()>1)sb.deleteCharAt(sb.lastIndexOf(","));
            valueList.add(sb.toString());
            valueList.add(service.getId());
            valueListList.add(valueList);
        }
        Shower.showDataTable(title,fields,widths,valueListList, 0, true);
    }
//    private ApiAccount chooseApi(byte[] symKey, ServiceType type, ApipClient apipClient) {
//        System.out.println("The " + type.name() + " is not ready. Set it...");
//        ApiProvider apiProvider = chooseApiProviderOrAdd(apiProviderMap,type,apipClient);
//        ApiAccount apiAccount = findAccountForTheProvider(apiProvider, null, symKey,apipClient);
//        if(apiAccount.getClient()==null) {
//            System.err.println("Failed to create " + type.name() + ".");
//            return null;
//        }
//        return apiAccount;
//    }
    public ApiAccount addApiAccount(@NotNull ApiProvider apiProvider, String userFid, byte[] symKey, ApipClient initApipClient) {
        System.out.println("Add API account for provider "+ apiProvider.getId()+"...");
        if(apiAccountMap==null)apiAccountMap = new HashMap<>();
        ApiAccount apiAccount;
        while(true) {
            apiAccount = new ApiAccount();
            apiAccount.inputAll(symKey,apiProvider, userFid, fidCipherMap, br);
//            if (apiAccountMap.get(apiAccount.getId()) != null) {
//                ApiAccount apiAccount1 = apiAccountMap.get(apiAccount.getId());
//                if (!Inputer.askIfYes(br, "There has an account for user " + apiAccount1.getUserName() + " on SID " + apiAccount1.getProviderId() + ".\n Cover it?")) {
//                    System.out.println("Add again.");
//                    continue;
//                }
//            }
            saveConfig();
            try {
                Object client = apiAccount.connectApi(apiProvider, symKey, br, initApipClient,fidCipherMap);
                if(client==null) {
                    if(askIfYes(br,"This account can't connect withe the API. Reset again?")) continue;
                    else return null;
                }
            }catch (Exception e){
                System.out.println("Can't connect the API provider of "+apiProvider.getId());
                if(Inputer.askIfYes(br,"Do you want to revise the API provider?")){
                    apiProvider.updateAll(br);
                    saveConfig();
                    continue;
                }else return null;
            }
            apiAccountMap.put(apiAccount.getId(), apiAccount);
            saveConfig();
            break;
        }
        return apiAccount;
    }

    public void showApiProviders(Map<String, ApiProvider> apiProviderMap) {
        if(apiProviderMap==null || apiProviderMap.size()==0)return;
        String[] fields = {"sid", "type","url", "ticks"};
        int[] widths = {16,10, 32, 24};
        List<List<Object>> valueListList = new ArrayList<>();
        for (ApiProvider apiProvider : apiProviderMap.values()) {
            List<Object> valueList = new ArrayList<>();
            valueList.add(apiProvider.getId());
            valueList.add(apiProvider.getType());
            valueList.add(apiProvider.getApiUrl());
            valueList.add(Arrays.toString(apiProvider.getTicks()));
            valueListList.add(valueList);
        }
        Shower.showDataTable("API providers", fields, widths, valueListList, 0, true);
    }

    public void showAccounts(Map<String, ApiAccount> apiAccountMap) {
        if(apiAccountMap==null || apiAccountMap.size()==0)return;
        String[] fields = {"id","userName","userId", "url", "sid"};
        int[] widths = {16,16,16, 32, 16};
        List<List<Object>> valueListList = new ArrayList<>();
        for (ApiAccount apiAccount : apiAccountMap.values()) {
            List<Object> valueList = new ArrayList<>();
            valueList.add(apiAccount.getId());
            valueList.add(apiAccount.getUserName());
            valueList.add(apiAccount.getUserId());
            valueList.add(apiAccount.getApiUrl());
            valueList.add(apiAccount.getProviderId());
            valueListList.add(valueList);
        }
        Shower.showDataTable("API accounts", fields, widths, valueListList, 0, true);
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public Map<String, ApiProvider> getApiProviderMap() {
        return apiProviderMap;
    }

    public void setApiProviderMap(Map<String, ApiProvider> apiProviderMap) {
        this.apiProviderMap = apiProviderMap;
    }

    public Map<String, ApiAccount> getApiAccountMap() {
        return apiAccountMap;
    }

    public void setApiAccountMap(Map<String, ApiAccount> apiAccountMap) {
        this.apiAccountMap = apiAccountMap;
    }

    public static void saveConfig() {
        tools.JsonTools.writeObjectToJsonFile(configureMap, Configure.getConfDir()+ Configure.CONFIG_DOT_JSON,false);
    }


    public List<String> getOwnerList() {
        return ownerList;
    }

    public void setOwnerList(List<String> ownerList) {
        this.ownerList = ownerList;
    }

//    public String getApipAccountId() {
//        return apipAccountId;
//    }

//    public void setApipAccountId(String apipAccountId) {
//        this.apipAccountId = apipAccountId;
//    }

    public void addApiAccount(String userFid, byte[] symKey, ApipClient initApipClient){
        System.out.println("Add API accounts...");
        ApiProvider apiProvider = chooseApiProviderOrAdd(apiProviderMap,  initApipClient);
        if(apiProvider!=null) {
            ApiAccount apiAccount = addApiAccount(apiProvider, userFid, symKey, initApipClient);
            saveConfig();
            if(apiAccount==null) System.out.println("Failed to add API account for "+apiProvider.getApiUrl());
            else System.out.println("Add API account "+apiAccount.getId()+" is added.");
        } else System.out.println("Failed to add API account.");

        Menu.anyKeyToContinue(br);
    }
    public void addApiProviderAndConnect(byte[] symKey, Service.ServiceType serviceType, ApipClient initApipClient){
        System.out.println("Add API providers...");
        ApiProvider apiProvider = addApiProvider(serviceType,initApipClient);
        if(apiProvider!=null) {
            ApiAccount apiAccount = addApiAccount(apiProvider, null, symKey, initApipClient);
            if(apiAccount!=null) {
                apiAccount.connectApi(apiProvider, symKey, br, null, fidCipherMap);
                saveConfig();
            }else return;
        }
       if(apiProvider!=null)System.out.println("Add API provider "+apiProvider.getId()+" is added.");
       else System.out.println("Failed to add API provider.");
       Menu.anyKeyToContinue(br);
    }

    public ApiProvider addApiProvider(Service.ServiceType serviceType, ApipClient apipClient) {
        if(serviceType==null)System.out.println("Add new provider...");
        else System.out.println("Add new "+ serviceType +" provider...");

        ApiProvider apiProvider = new ApiProvider();
        if(!apiProvider.makeApiProvider(br, serviceType,apipClient))return null;

        if(apiProviderMap==null)apiProviderMap= new HashMap<>();
        apiProviderMap.put(apiProvider.getId(),apiProvider);
        System.out.println(apiProvider.getId()+" on "+apiProvider.getApiUrl() + " added.");
        saveConfig();
        return apiProvider;
    }


    public void updateApiAccount(ApiProvider apiProvider, byte[] symKey, ApipClient initApipClient){
        System.out.println("Update API accounts...");
        ApiAccount apiAccount;
        if(apiProvider==null)apiAccount = chooseApiAccount(symKey,initApipClient);
        else apiAccount = findAccountForTheProvider(apiProvider, null, symKey,initApipClient);
        if(apiAccount!=null) {
            System.out.println("Update API account: "+apiAccount.getProviderId()+"...");
            apiAccount.updateAll(symKey, apiProvider,br);
            getApiAccountMap().put(apiAccount.getId(), apiAccount);
            saveConfig();
        }
        if(apiAccount!=null) System.out.println("Api account "+apiAccount.getId()+" is updated.");
        else System.out.println("Failed to update API account.");
        Menu.anyKeyToContinue(br);
    }

    public void updateApiProvider(ApipClient apipClient){
        System.out.println("Update API providers...");
        ApiProvider apiProvider = chooseApiProviderOrAdd(apiProviderMap, apipClient);
        if(apiProvider!=null) {
            apiProvider.updateAll(br);
            getApiProviderMap().put(apiProvider.getId(), apiProvider);
            saveConfig();
            System.out.println("Api provider "+apiProvider.getId()+" is updated.");
        }
        System.out.println("Failed to update API provider.");
        Menu.anyKeyToContinue(br);
    }

    public void deleteApiProvider(byte[] symKey,ApipClient apipClient){
        System.out.println("Deleting API provider...");
        ApiProvider apiProvider = chooseApiProvider(apiProviderMap);
        if(apiProvider==null) return;
        for(ApiAccount apiAccount: getApiAccountMap().values()){
            if(apiAccount.getProviderId().equals(apiProvider.getId())){
                if(Inputer.askIfYes(br,"There is the API account "+apiAccount.getId()+" of "+apiProvider.getId()+". \nDelete it?")){
                    getApiAccountMap().remove(apiAccount.getId());
                    System.out.println("Api account "+apiAccount.getId()+" is deleted.");
                    saveConfig();
                }
            }
        }
        if(Inputer.askIfYes(br,"Delete API provider "+apiProvider.getId()+"?")){
            getApiProviderMap().remove(apiProvider.getId());
            System.out.println("Api provider " + apiProvider.getId() + " is deleted.");
            saveConfig();
        }
        Menu.anyKeyToContinue(br);
    }

    public void deleteApiAccount(byte[] symKey,ApipClient initApipClient){
        System.out.println("Deleting API Account...");
        ApiAccount apiAccount = chooseApiAccount(symKey,initApipClient);
        if(apiAccount==null) return;
        if(Inputer.askIfYes(br,"Delete API account "+apiAccount.getId()+"?")) {
            getApiAccountMap().remove(apiAccount.getId());
            System.out.println("Api account " + apiAccount.getId() + " is deleted.");
            saveConfig();
        }
        Menu.anyKeyToContinue(br);
    }

    public ApiAccount chooseApiAccount(byte[] symKey,ApipClient initApipClient){
        ApiAccount apiAccount = null;
        showAccounts(getApiAccountMap());
        int input = Inputer.inputInt(br, "Input the number of the account you want. Enter to add a new one:", getApiAccountMap().size());
        if (input == 0) {
            if(Inputer.askIfYes(br,"Add a new API account?")) {
                ApiProvider apiProvider = chooseApiProviderOrAdd(apiProviderMap, initApipClient);
                apiAccount = addApiAccount(apiProvider, null, symKey, initApipClient);
            }
        } else {
            apiAccount = (ApiAccount) getApiAccountMap().values().toArray()[input - 1];
        }
        return apiAccount;
    }

public ApiProvider chooseApiProviderOrAdd(Map<String, ApiProvider> apiProviderMap, ApipClient apipClient){
        return chooseApiProviderOrAdd(apiProviderMap,null,apipClient);
}
    public ApiProvider chooseApiProviderOrAdd(Map<String, ApiProvider> apiProviderMap, Service.ServiceType serviceType, ApipClient apipClient){
        if(serviceType ==null)
            serviceType = fch.Inputer.chooseOne(Service.ServiceType.values(), null, "Choose the API type:",br);
        ApiProvider apiProvider = chooseApiProvider(apiProviderMap, serviceType);
        if(apiProvider==null){
            apiProvider = addApiProvider(serviceType,apipClient);
        }
        return apiProvider;
    }
    public ApiProvider chooseApiProvider(Map<String, ApiProvider> apiProviderMap, Service.ServiceType serviceType){
        Map<String, ApiProvider> map = new HashMap<>();
        for(String id : apiProviderMap.keySet()){
            ApiProvider apiProvider = apiProviderMap.get(id);
            if(apiProvider.getType().equals(serviceType))
                map.put(id,apiProvider);
        }
        return chooseApiProvider(map);
    }

    public ApiProvider chooseApiProvider(Map<String, ApiProvider> apiProviderMap){
        System.out.println("Choose API provider...");
        ApiProvider apiProvider;
        if (apiProviderMap == null) {
            apiProviderMap = new HashMap<>();
            setApiProviderMap(apiProviderMap);
        }
        if (apiProviderMap.size() == 0) {
            System.out.println("No API provider yet.");
            return null;
        } else {
            if(apiProviderMap.size()==1){
                String key = (String)apiProviderMap.keySet().toArray()[0];
                ApiProvider apiProvider1 = apiProviderMap.get(key);
                if(confirmDefault(br,apiProvider1.getName())) {
                    return apiProvider1;
                } else return null;
            }

            showApiProviders(apiProviderMap);
            int input = Inputer.inputInt( br,"Input the number of the API provider you want. Enter to add new one:", apiProviderMap.size());
            if (input == 0) {
                return null;
            } else apiProvider = (ApiProvider) apiProviderMap.values().toArray()[input - 1];
        }
        return apiProvider;
    }

    public ApiProvider selectFcApiProvider(ApipClient initApipClient, Service.ServiceType serviceType) {
        ApiProvider apiProvider = chooseApiProvider(apiProviderMap, serviceType);
        if(apiProvider==null) apiProvider= ApiProvider.searchFcApiProvider(initApipClient, serviceType);
        return apiProvider;
    }


    public ApiAccount getAccountForTheProvider(ApiProvider apiProvider, String userFid, byte[] symKey, ApipClient initApipClient) {
        System.out.println("Get account for "+apiProvider.getName()+"...");
        ApiAccount apiAccount;
        if (apiAccountMap == null) setApiAccountMap(new HashMap<>());

        if(apiAccountMap.size()!=0) {
            for (ApiAccount apiAccount1 : getApiAccountMap().values()) {
                if (apiAccount1.getProviderId().equals(apiProvider.getId())) {
                    String account1UserId = apiAccount1.getUserId();
                    if (account1UserId != null && account1UserId.equals(userFid) && KeyTools.isValidFchAddr(account1UserId)) {
                        apiAccount1.setApipClient(initApipClient);
                        if (apiAccount1.getClient() == null) apiAccount1.connectApi(apiProvider, symKey);
                        return apiAccount1;
                    }
                }
            }
        }
        apiAccount = addApiAccount(apiProvider, userFid, symKey,initApipClient );
        if(apiAccount==null)return null;
        apiAccount.setApipClient(initApipClient);
        if(apiAccount.getClient()==null)apiAccount.connectApi(apiProvider,symKey);
        return apiAccount;
    }

    public ApiAccount findAccountForTheProvider(ApiProvider apiProvider, String userFid, byte[] symKey, ApipClient initApipClient) {
        System.out.println("Get account for "+apiProvider.getName()+"...");
        ApiAccount apiAccount;
        Map<String, ApiAccount> hitApiAccountMap = new HashMap<>();
        if (apiAccountMap == null) setApiAccountMap(new HashMap<>());

        if(apiAccountMap.size()!=0){
            for (ApiAccount apiAccount1 : getApiAccountMap().values()) {
                if (apiAccount1.getProviderId().equals(apiProvider.getId())) {
                    String account1UserId = apiAccount1.getUserId();
                    if(account1UserId !=null && account1UserId.equals(userFid) && KeyTools.isValidFchAddr(account1UserId)) {
                        apiAccount1.setApipClient(initApipClient);
                        if (apiAccount1.getClient() == null) apiAccount1.connectApi(apiProvider, symKey);
                        return apiAccount1;
                    }else hitApiAccountMap.put(apiAccount1.getId(), apiAccount1);
                }
            }

            if(hitApiAccountMap.size()==0) {
                apiAccount = addApiAccount(apiProvider, userFid, symKey,initApipClient );
            }else if(hitApiAccountMap.size()==1){
                String key = (String)hitApiAccountMap.keySet().toArray()[0];
                apiAccount = hitApiAccountMap.get(key);
                if(confirmDefault(br,apiAccount.getUserName())) {
                    apiAccount.setApipClient(initApipClient);
                    if(apiAccount.getClient()==null)apiAccount.connectApi(apiProvider,symKey);
                    return apiAccount;
                } else apiAccount = addApiAccount(apiProvider, userFid, symKey,initApipClient );
            }else {
                showAccounts(hitApiAccountMap);

                int input = Inputer.inputInt(br, "Input the number of the account you want. Enter to add new one:", hitApiAccountMap.size());
                if (input == 0) {
                    apiAccount = addApiAccount(apiProvider, userFid, symKey, initApipClient);
                } else {
                    apiAccount = (ApiAccount) hitApiAccountMap.values().toArray()[input - 1];
                }
            }
        }else apiAccount = addApiAccount(apiProvider, userFid, symKey,initApipClient );

        apiAccount.setApipClient(initApipClient);
        if(apiAccount.getClient()==null)apiAccount.connectApi(apiProvider,symKey);
        return apiAccount;
    }

//        System.out.println("No API accounts yet. Add new one...");
//        return addApiAccount(apiProvider, userFid, symKey, initApipClient);


//        if (apiAccountMap.size() == 0) {
//            System.out.println("No API accounts yet. Add new one...");
//            apiAccount = addApiAccount(apiProvider, userFid, symKey, initApipClient);
//        } else {
//            for (ApiAccount apiAccount1 : getApiAccountMap().values()) {
//                if (apiAccount1.getProviderId().equals(apiProvider.getId())) {
//                    hitApiAccountMap.put(apiAccount1.getId(), apiAccount1);
//                }
//            }
//            if (hitApiAccountMap.size() == 0) {
//                apiAccount = addApiAccount(apiProvider, userFid, symKey, initApipClient);
//            }
//            else {
//
//                if(hitApiAccountMap.size()==1){
//                    String key = (String)hitApiAccountMap.keySet().toArray()[0];
//                    ApiAccount apiAccount1 = hitApiAccountMap.get(key);
//                    if(confirmDefault(br,apiAccount1.getUserName())) {
//                        return apiAccount1;
//                    } else return null;
//                }
//
//                showAccounts(hitApiAccountMap);
//                int input = Inputer.inputInteger( br,"Input the number of the account you want. Enter to add new one:", hitApiAccountMap.size());
//                if (input == 0) {
//                    apiAccount = addApiAccount(apiProvider, userFid, symKey,initApipClient );
//                } else {
//                    apiAccount = (ApiAccount) hitApiAccountMap.values().toArray()[input - 1];
//                    apiAccount.setApipClient(initApipClient);
//                    if(apiAccount.getClient()==null)apiAccount.connectApi(apiProvider,symKey);
//                }
//            }
//        }
//        return apiAccount;

    public ApiAccount checkAPI(@Nullable String apiAccountId, String userFid, Service.ServiceType serviceType, byte[] symKey) {
        if(serviceType !=null) System.out.println("\nCheck "+ serviceType +" API...");
        apiAccountMap.remove("null");
        ApiAccount apiAccount = null;
        while (true) {
            if (apiAccountId == null) {
                System.out.println("No " + serviceType + " account set yet. ");
                    apiAccount = getApiAccount(symKey, userFid, serviceType, apipClient);
            }else {
                apiAccount = apiAccountMap.get(apiAccountId);
                if(apiAccount ==null || askIfYes(br,"Current API is from "+apiAccount.getApiUrl()+" Change it?"))
                    apiAccount = getApiAccount(symKey, userFid, serviceType, apipClient);
            }

            if (apiAccount == null) {
                if(askIfYes(br,"Failed to get API account. Try again?")) continue;
                return null;
            }

            if (apiAccount.getClient() == null) {
                Object apiClient;
                try {
                    apiClient = apiAccount.connectApi(getApiProviderMap().get(apiAccount.getProviderId()), symKey, br, apipClient, fidCipherMap);
                }catch (Exception e){
                    System.out.println("Failed to connect " + apiAccount.getApiUrl() + ". Try again.");
                    continue;
                }
                if (apiClient == null) {
                    System.out.println("Failed to connect " + apiAccount.getApiUrl() + ". Try again.");
                    continue;
                }
            }
            return apiAccount;
        }
    }

//    public ApiAccount setApiService(byte[] symKey,ApipClient apipClient) {
//        ApiProvider apiProvider = chooseApiProviderOrAdd(apiProviderMap, apipClient);
//        ApiAccount apiAccount = chooseApiProvidersAccount(apiProvider,symKey,apipClient);
//        if(apiAccount.getClient()!=null) saveConfig();
//        return apiAccount;
//    }

    public ApiAccount getApiAccount(byte[] symKey, String userFid, Service.ServiceType serviceType, ApipClient apipClient) {
        ApiProvider apiProvider = chooseApiProvider(apiProviderMap, serviceType);

        while(apiProvider==null) {
            apiProvider = addApiProvider(serviceType,apipClient);
            if(apiProvider!=null)break;
            System.out.println("Failed to add API provider.");
        }

        if(apiProvider.getId()==null){
            System.out.println("The ID of the API provider is null. Update it...");
            apiProvider.updateAll(br);
            saveConfig();
        }

        ApiAccount apiAccount;
//        if(shareApiAccount)apiAccount = findAccountForTheProvider(apiProvider, userFid, symKey,apipClient);
//        else
        apiAccount = getAccountForTheProvider(apiProvider, userFid, symKey,apipClient);

        if(apiAccount==null) apiAccount = addApiAccount(apiProvider, userFid, symKey,apipClient );
        if(apiAccount!=null && apiAccount.getClient()!=null) saveConfig();
        return apiAccount;
    }



    public static Configure checkPassword(BufferedReader br){
        byte[] passwordBytes;
        Configure configure;
        if(configureMap.isEmpty()){
            while(true) {
                passwordBytes = Inputer.resetNewPassword(br);
                if(passwordBytes!=null) break;
                System.out.println("A password is required. Try again.");
            }
            configure = creatNewConfigure(passwordBytes);
        }else {
            configure = verifyPassword(br);
            if(configure==null)return null;
        }
        initConfigure(br, configure);
        return configure;
    }

    public static Configure verifyPassword(BufferedReader br) {
        byte[] symKey;
        byte[] nonceBytes;
        byte[] passwordBytes;
        String passwordName;
        Configure configure = null;
        char[] password = Inputer.inputPassword(br, "Input your password:");
        while (true) {
            if(password==null)continue;
            passwordBytes = BytesTools.utf8CharArrayToByteArray(password);
            passwordName = makePasswordHashName(passwordBytes);
            configure = configureMap.get(passwordName);
            if (configure != null) {
                nonceBytes = Hex.fromHex(configure.getNonce());
                symKey = getSymKeyFromPasswordAndNonce(passwordBytes, nonceBytes);
                configure.setSymKey(symKey);
                configure.setPasswordName(passwordName);
                break;
            }
            String input = Inputer.inputString(br, "Password wrong. Try again. 'c' to create new one. 'q' to quit:");
            if (input.equals("c")) {
                passwordBytes = Inputer.resetNewPassword(br);
                configure = creatNewConfigure(passwordBytes);
                break;
            } else if (input.equals("q")) {
                System.exit(0);
                return null;
            }else {
                password = input.toCharArray();
            }
        }
        return configure;
    }

    public static boolean checkPassword(BufferedReader br, byte[] symKey,Configure configure) {
        while(true){    
            char[] password = Inputer.inputPassword(br, "Input your password:");
            if(password==null)return false;
            byte[] passwordBytes = BytesTools.utf8CharArrayToByteArray(password);
            byte[] nonceBytes = Hex.fromHex(configure.getNonce());
            byte[] symKey1 = getSymKeyFromPasswordAndNonce(passwordBytes, nonceBytes);
            if(Arrays.equals(symKey, symKey1))return true;
            System.out.println("Wrong password. Try again.");
        }
    }

    @NotNull
    public static String makePasswordHashName(byte[] passwordBytes) {
        return Hex.toHex(Hash.sha256(passwordBytes)).substring(0, 6);
    }

    private static Configure creatNewConfigure(byte[] passwordBytes) {
        Configure configure = new Configure();
        byte[] symKey;
        byte[] nonceBytes;
        nonceBytes = BytesTools.getRandomBytes(16);
        symKey = getSymKeyFromPasswordAndNonce(passwordBytes, nonceBytes);
        configure.nonce = Hex.toHex(nonceBytes);
        configure.setSymKey(symKey);
        String name = makePasswordHashName(passwordBytes);
        configure.setPasswordName(name);
        configureMap.put(name,configure);
        saveConfig();
        BytesTools.clearByteArray(passwordBytes);
        return configure;
    }

    private static void initConfigure(BufferedReader br, Configure configure) {
        configure.initFreeApiListMap();
        configure.setBr(br);
        if(configure.getApiProviderMap()==null)
                configure.setApiProviderMap(new HashMap<>());
        if(configure.getApiAccountMap() == null)
                configure.setApiAccountMap(new HashMap<>());
        if(configure.getFidCipherMap()==null)
                configure.setFidCipherMap(new HashMap<>());
        if(configure.getMyServiceMaskMap()==null)
                configure.setMyServiceMaskMap(new HashMap<>());
        if(configure.getOwnerList()==null)
                configure.setOwnerList(new ArrayList<>());
        if(configure.fidCipherMap==null)
            configure.initFreeApiListMap();
    }

    public static byte[] getSymKeyFromPasswordAndNonce(byte[] passwordBytes, byte[] nonce) {
        return Hash.sha256x2(BytesTools.bytesMerger(passwordBytes, nonce));
    }

    public static  <T> T parseMyServiceParams(Service myService, Class<T> tClass){
        Gson gson = new Gson();
        T params = gson.fromJson(gson.toJson(myService.getParams()), tClass);
        myService.setParams(params);
        return params;
    }

    public static String getConfDir(){
        return System.getProperty("user.dir")+"/"+ CONFIG +"/";
    }

    public static void loadConfig(String path, BufferedReader br){
        if(path==null)path = getConfDir();
        try {
            configureMap = JsonTools.readMapFromJsonFile(path, CONFIG_DOT_JSON, String.class,Configure.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if(configureMap==null){
            log.debug("Failed to load configMap from "+ CONFIG_DOT_JSON+". It will be create.");
            configureMap = new HashMap<>();
        }
    }

    public static void loadConfig(BufferedReader br){
        loadConfig(null, br);
    }


    public BufferedReader getBr() {
        return br;
    }

    public void setBr(BufferedReader br1) {
        br = br1;
    }


    public Map<String, ServiceMask> getMyServiceMaskMap() {
        return myServiceMaskMap;
    }

    public void setMyServiceMaskMap(Map<String, ServiceMask> myServiceMaskMap) {
        this.myServiceMaskMap = myServiceMaskMap;
    }

    public static String getConfigDotJson() {
        return CONFIG_DOT_JSON;
    }

    public static void setConfigDotJson(String configDotJson) {
        CONFIG_DOT_JSON = configDotJson;
    }
    public String getEsAccountId() {
        return esAccountId;
    }

    public void setEsAccountId(String esAccountId) {
        this.esAccountId = esAccountId;
    }

    public Map<String, String> getFidCipherMap() {
        return fidCipherMap;
    }

    public void setFidCipherMap(Map<String, String> fidCipherMap) {
        this.fidCipherMap = fidCipherMap;
    }

    public String addOwner(BufferedReader br) {
        String owner = fch.Inputer.inputGoodFid(br,"Input the owner FID:");
        if(ownerList==null)ownerList = new ArrayList<>();
        ownerList.add(owner);
        saveConfig();
        return owner;
    }

    public String chooseMainFid(byte[] symKey) {
        while(true) {
            String fid = Inputer.chooseOne(fidCipherMap.keySet().toArray(new String[0]), null, "Choose the FID", br);
            if (fid == null) {
                System.out.println("No FID chosen.");
                if (askIfYes(br, "Add a new FID?"))
                    fid = addUser(symKey);
                else continue;
            }
            return fid;
        }
    }

    public String getServiceDealer(String sid, byte[] symKey) {

        ServiceMask serviceMask = myServiceMaskMap.get(sid);
        if(serviceMask!=null && serviceMask.getDealer()!=null)
            return serviceMask.getDealer();

        System.out.println("Set the dealer of your service which was published on-chain...");
        return chooseMainFid(symKey);
    }

    public String chooseSid(Service.ServiceType serviceType) {
        Map<String, ServiceMask> map = new HashMap<>();
        if(serviceType!=null){
            for(String key: myServiceMaskMap.keySet()){
                ServiceMask serviceSummary = myServiceMaskMap.get(key);
                if(StringTools.isContainCaseInsensitive(serviceSummary.getTypes(),serviceType.name()))
                    map.put(key,serviceSummary);
            }
        }else map= myServiceMaskMap;
        return Inputer.chooseOneKeyFromMap(map, true, STD_NAME, "Choose your service:", br);
    }

    public static boolean containsIgnoreCase(String str, String searchStr) {
        if (str == null || searchStr == null) {
            return false;
        }
        return str.toLowerCase().contains(searchStr.toLowerCase());
    }

    public Map<Service.ServiceType, List<FreeApi>> getFreeApiListMap() {
        return freeApiListMap;
    }

    public void setFreeApiListMap(Map<Service.ServiceType, List<FreeApi>> freeApiListMap) {
        this.freeApiListMap = freeApiListMap;
    }

    public byte[] getSymKey() {
        return symKey;
    }

    public void setSymKey(byte[] symKey) {
        this.symKey = symKey;
    }

    public String getPasswordName() {
        return passwordName;
    }

    public void setPasswordName(String passwordName) {
        this.passwordName = passwordName;
    }

    public static Map<String, Configure> getConfigureMap() {
        return configureMap;
    }

    public static void setConfigureMap(Map<String, Configure> configureMap) {
        Configure.configureMap = configureMap;
    }

    public ApipClient getApipClient() {
        return apipClient;
    }

    public void setApipClient(ApipClient apipClient) {
        this.apipClient = apipClient;
    }

    // Add method to get all account IDs for a service type
    public List<String> getAccountIdsForServiceType(Service.ServiceType type) {
        List<String> accountIds = new ArrayList<>();
        if (apiAccountMap != null) {
            for (Map.Entry<String, ApiAccount> entry : apiAccountMap.entrySet()) {
                ApiProvider provider = apiProviderMap.get(entry.getValue().getProviderId());
                if (provider != null && provider.getType() == type) {
                    accountIds.add(entry.getKey());
                }
            }
        }
        return accountIds;
    }
}
