package config;

import core.crypto.*;
import data.fcData.CidInfo;
import ui.Menu;
import data.feipData.ServiceMask;
import data.feipData.serviceParams.ApipParams;
import data.feipData.serviceParams.DiskParams;
import data.feipData.serviceParams.SwapParams;
import data.feipData.serviceParams.TalkParams;
import ui.Inputer;
import clients.ApipClient;
import data.feipData.Service;
import ui.Shower;
import utils.*;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.google.gson.Gson;
import constants.FieldNames;
import constants.IndicesNames;
import data.fcData.AlgorithmId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.FreeApi;
import server.serviceManagers.ApipManager;
import server.serviceManagers.ChatManager;
import server.serviceManagers.DiskManager;
import server.serviceManagers.SwapHallManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static ui.Inputer.askIfYes;
import static ui.Inputer.confirmDefault;
import static constants.FieldNames.*;
import static constants.FieldNames.TYPES;
import static constants.Strings.*;
import static constants.Strings.SETTINGS;
import static core.fch.Inputer.importOrCreatePrikey;
import static config.Settings.addSidBriefToName;

public class Configure {
    protected String nonce;
    protected String passwordName;
    protected String passwordHash;
    protected List<String> ownerList;  //Owners for servers.
    protected Map<String, CidInfo> mainCidInfoMap; //Users for clients or accounts for servers.
    private String esAccountId;

    private Map<String, ServiceMask> myServiceMaskMap;
    private Map<String, ApiProvider> apiProviderMap;
    private Map<String, ApiAccount> apiAccountMap;
    private transient byte[] symkey;

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
        makeWebConfig(settings);
        Menu.anyKeyToContinue(br);

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
        String settingFileName = FileUtils.makeFileName(null, settings.getSid(), SETTINGS, DOT_JSON);
        webServerConfig.setSettingPath(Path.of(confDir,settingFileName).toString());
        webServerConfig.setDataPath(Settings.getLocalDataDir(sid));
        webServerConfig.setDbPath(settings.getDbDir());
        CryptoDataByte result = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7).encryptByPassword(settings.getSymkey(), settings.getConfig().getNonce().toCharArray());
        if(result.getCode()!=0){
            System.out.println("Failed to encrypt symkey for web server.");
            return;
        }
        String symkeyCipher = result.toJson();
        JedisPool jedisPool = (JedisPool)settings.getClient(Service.ServiceType.REDIS);
        try(Jedis jedis = jedisPool.getResource()){
            jedis.hset(addSidBriefToName(sid,WEB_PARAMS),SYM_KEY_CIPHER,symkeyCipher);
            jedis.hset(addSidBriefToName(sid,WEB_PARAMS),FORBID_FREE_API, String.valueOf(settings.getSettingMap().get(FORBID_FREE_API)));
        }
        JsonUtils.writeObjectToJsonFile(webServerConfig,fileName,false);

        System.out.println("\nCopy the file of '"+fileName+"' to the bin directory of Tomcat.");
    }

    public static byte[] getSymkeyFromRedis(String sid, Configure configure, JedisPool jedisPool) {
        byte[] symkey;
        String symkeyCipher;
        try(Jedis jedis = jedisPool.getResource()){
            symkeyCipher = jedis.hget(addSidBriefToName(sid, WEB_PARAMS),SYM_KEY_CIPHER);
        }
        CryptoDataByte result = new Decryptor().decryptJsonByPassword(symkeyCipher, configure.getNonce().toCharArray());
        if(result.getCode()!=0){
            log.debug("Failed to decrypt symkey for web server:"+result.getMessage());
            System.out.println("Check the config file for web server.");
            return null;
        }
        symkey = result.getData();
        return symkey;
    }

    public void initFreeApiListMap(){
        if (freeApiListMap == null) {
            freeApiListMap = new HashMap<>();
        }
        if(freeApiListMap.get(Service.ServiceType.APIP)==null){
            ArrayList<FreeApi> freeApipList = new ArrayList<>();
            for(String url:ApipClient.freeAPIs){
                freeApipList.add(new FreeApi(url,true, Service.ServiceType.APIP));
            }
            freeApiListMap.put(Service.ServiceType.APIP,freeApipList);
        }

        if(freeApiListMap.get(Service.ServiceType.DISK)==null){
            ArrayList<FreeApi> freeApipList = new ArrayList<>();
            for(String url:ApipClient.freeAPIs){
                freeApipList.add(new FreeApi(url,true, Service.ServiceType.DISK));
            }
            freeApiListMap.put(Service.ServiceType.APIP,freeApipList);
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

//    public String initiateClient(byte[] symkey) {
//        System.out.println("Initiating config...");
//        String fid;
//        if (apiProviderMap == null) apiProviderMap = new HashMap<>();
//        if (apiAccountMap == null) apiAccountMap = new HashMap<>();
//        if (fidCipherMap == null) {
//            fidCipherMap = new HashMap<>();
//            addUser(symkey);
//        }
//
//        if(fidCipherMap ==null || fidCipherMap.isEmpty())
//            return null;
//        fid = (String) Inputer.chooseOne(fidCipherMap.keySet().toArray(), null, "Choose a user:", br);
//        if(fid==null)fid = addUser(symkey);
//        saveConfig();
//        return fid;
//    }

    public String addUser(byte[] symkey) {
        return addUser(null,symkey);

    }

    public String addUser(String fid,byte[] symkey) {
        CidInfo cidInfo;
        if(mainCidInfoMap.get(fid)!=null){
            if(!Inputer.askIfYes(br,fid +" exists. Replace it?"))
                return fid;
        }

        if(fid==null)System.out.println("Add new user...");
        else System.out.println("Add "+fid+" to users...");
        byte[] prikeyBytes;
        String pubkey=null;

        while(true) {
            try {
                prikeyBytes = importOrCreatePrikey(br);
                if(prikeyBytes==null){
                    if(askIfYes(br, "Add a watch-only FID?")) {
                        if(askIfYes(br,"Input the pubkey? ")){
                            pubkey = KeyTools.inputPubkey(br);
                            cidInfo = new CidInfo(null,pubkey);
                        }else {
                            cidInfo = new CidInfo();
                            String newFid = Inputer.inputFid(br, "Input the watch-only FID:");
                            if(newFid!=null){
                                cidInfo.setId(newFid);
                                cidInfo.setWatchOnly(true);
                            }
                            else return null;
                        }
                    } else continue;
                }else{
                    cidInfo = new CidInfo(prikeyBytes,symkey);
                }
            }catch (Exception e){
                System.out.println("Something wrong. Try again.");
                continue;
            }
            if(fid == null) {
                    fid = cidInfo.getId();
                    break;
            }
            if(fid.equals(cidInfo.getId()))break;

            System.out.println("The cipher is of " + cidInfo.getId() + " instead of " + fid + ". \nTry again.");
        }

        mainCidInfoMap.put(fid, cidInfo);
        saveConfig();
        return fid;
    }

    @SuppressWarnings("unused")
    private Service chooseOwnerService(String owner, byte[] symkey, Service.ServiceType serviceType, ApipClient apipClient) {
        return chooseOwnerService(owner,symkey, serviceType,null, apipClient);
    }
    @SuppressWarnings("unused")
    private Service chooseOwnerService(String owner, byte[] symkey, Service.ServiceType serviceType, ElasticsearchClient esClient) {
        return chooseOwnerService(owner,symkey, serviceType,esClient,null);
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
        sb.size(EsUtils.READ_MAX);
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
    public Service chooseOwnerService(String owner, byte[] symkey, Service.ServiceType type, ElasticsearchClient esClient, ApipClient apipClient) {
        List<Service> serviceList;

        if(esClient==null)
            serviceList = apipClient.getServiceListByOwnerAndType(owner,type);
        else serviceList = getServiceListByOwnerAndTypeFromEs(owner,type,esClient);

        if(serviceList==null || serviceList.isEmpty()){
            System.out.println("No any service on chain of the owner.");
            return null;
        }

        Service service;
        if(symkey!=null)service = selectService(serviceList, symkey, apipClient==null?null:apipClient.getApiAccount());
        else service = selectService(serviceList);
        if(service==null) System.out.println("Failed to get the service.");

        return service;
    }

    public static Service selectService(List<Service> serviceList,byte[] symkey,ApiAccount apipAccount){
        if(serviceList==null||serviceList.isEmpty())return null;

        showServices(serviceList);

        int choice = Shower.choose(br,0,serviceList.size());
        if(choice==0){
            if(Inputer.askIfYes(br,"Publish a new service?")){
                Service.ServiceType type = Inputer.chooseOne(Service.ServiceType.values(), null, "Choose a type", br);
                switch (type){
                    case APIP -> new ApipManager(null,apipAccount,br,symkey, ApipParams.class).publishService();
                    case DISK -> new DiskManager(null,apipAccount,br,symkey, DiskParams.class).publishService();
                    case SWAP_HALL -> new SwapHallManager(null,apipAccount,br,symkey, SwapParams.class).publishService();
                    case TALK -> new ChatManager(null,apipAccount,br,symkey, TalkParams.class).publishService();
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
            if(service.getTypes()!=null)
                for(String type:service.getTypes()){
                    sb.append(type);
                    sb.append(",");
                }
            if(sb.length()>1)sb.deleteCharAt(sb.lastIndexOf(","));
            valueList.add(sb.toString());
            valueList.add(service.getId());
            valueListList.add(valueList);
        }
        Shower.showOrChooseList(title,fields,widths,valueListList, null);
    }
//    private ApiAccount chooseApi(byte[] symkey, ServiceType type, ApipClient apipClient) {
//        System.out.println("The " + type.name() + " is not ready. Set it...");
//        ApiProvider apiProvider = chooseApiProviderOrAdd(apiProviderMap,type,apipClient);
//        ApiAccount apiAccount = findAccountForTheProvider(apiProvider, null, symkey,apipClient);
//        if(apiAccount.getClient()==null) {
//            System.err.println("Failed to create " + type.name() + ".");
//            return null;
//        }
//        return apiAccount;
//    }
    public ApiAccount addApiAccount(@NotNull ApiProvider apiProvider, String userFid, byte[] symkey, ApipClient initApipClient) {
        System.out.println("Adding new "+apiProvider.getType()+" account...");
//        if(askIfYes(br,"Stop adding API account for provider "+ apiProvider.getId()+"?"))return null;
        if(apiAccountMap==null)apiAccountMap = new HashMap<>();
        ApiAccount apiAccount;
        while(true) {
            apiAccount = new ApiAccount();
            apiAccount.inputAll(symkey,apiProvider, userFid, mainCidInfoMap, br);
            saveConfig();
            try {
                Object client = apiAccount.connectApi(apiProvider, symkey, br, initApipClient, mainCidInfoMap);
                if(client==null) {
                    if(askIfYes(br,"Failed to connect "+apiAccount.getApiUrl()+". Reset API provider?")){
                        apiProvider = changeApiProvider(apiProvider);
                        if(apiProvider==null)return null;
                        continue;
                    }
                    else return null;
                }
            }catch (Exception e){
//                e.printStackTrace();
                System.out.println("Can't connect the API provider of "+apiProvider.getId());
                if(Inputer.askIfYes(br,"Do you want to revise the API provider?")){
                    apiProvider = changeApiProvider(apiProvider);
                    if(apiProvider==null)return null;
                    continue;
                }else return null;
            }
            apiAccountMap.put(apiAccount.getId(), apiAccount);
            saveConfig();
            break;
        }
        return apiAccount;
    }

    private ApiProvider changeApiProvider(@NotNull ApiProvider apiProvider) {
        ApiProvider newApiProvider = new ApiProvider();
        newApiProvider.setType(apiProvider.getType());
        newApiProvider.updateAll(br);
        if(newApiProvider.getId() == null)return null;
        apiProviderMap.put(newApiProvider.getId(),newApiProvider);
        saveConfig();
        return newApiProvider;
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
        Shower.showOrChooseList("API providers", fields, widths, valueListList, null);
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
        Shower.showOrChooseList("API accounts", fields, widths, valueListList, null);
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
        JsonUtils.writeObjectToJsonFile(configureMap, Configure.getConfDir()+ Configure.CONFIG_DOT_JSON,false);
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

    public void addApiAccount(String userFid, byte[] symkey, ApipClient initApipClient){
        System.out.println("Add API accounts...");
        ApiProvider apiProvider = chooseApiProviderOrAdd(apiProviderMap,  initApipClient);
        if(apiProvider!=null) {
            ApiAccount apiAccount = addApiAccount(apiProvider, userFid, symkey, initApipClient);
            saveConfig();
            if(apiAccount==null) System.out.println("Failed to add API account for "+apiProvider.getApiUrl());
            else System.out.println("Add API account "+apiAccount.getId()+" is added.");
        } else System.out.println("Failed to add API account.");

        Menu.anyKeyToContinue(br);
    }
    public void addApiProviderAndConnect(byte[] symkey, Service.ServiceType serviceType, ApipClient initApipClient){
        System.out.println("Add API providers...");
        ApiProvider apiProvider = addApiProvider(serviceType,initApipClient);
        if(apiProvider!=null) {
            ApiAccount apiAccount = addApiAccount(apiProvider, null, symkey, initApipClient);
            if(apiAccount!=null) {
                apiAccount.connectApi(apiProvider, symkey, br, null, mainCidInfoMap);
                saveConfig();
            }else return;
        }
       if(apiProvider!=null)System.out.println("Add API provider "+apiProvider.getId()+" is added.");
       else System.out.println("Failed to add API provider.");
       Menu.anyKeyToContinue(br);
    }

    public ApiProvider addApiProvider(Service.ServiceType serviceType, ApipClient apipClient) {
        String ask;
        System.out.println("Adding a new API provider...");
//        if(serviceType==null)
//            ask = "Stop adding new provider?";
//        else ask = "Stop adding new "+ serviceType +" provider?";

//        if(askIfYes(br,ask))return null;

        ApiProvider apiProvider = new ApiProvider();
        if(!apiProvider.makeApiProvider(br, serviceType,apipClient))return null;

        if(apiProviderMap==null)apiProviderMap= new HashMap<>();
        apiProviderMap.put(apiProvider.getId(),apiProvider);
        System.out.println(apiProvider.getId()+" on "+apiProvider.getApiUrl() + " added.");
        saveConfig();
        return apiProvider;
    }


    public void updateApiAccount(ApiProvider apiProvider, byte[] symkey, ApipClient initApipClient){
        System.out.println("Update API accounts...");
        ApiAccount apiAccount;
        if(apiProvider==null)apiAccount = chooseApiAccount(symkey,initApipClient);
        else apiAccount = findAccountForTheProvider(apiProvider, null, symkey,initApipClient);
        if(apiAccount!=null) {
            System.out.println("Update API account: "+apiAccount.getProviderId()+"...");
            apiAccount.updateAll(symkey, apiProvider,br);
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

    public void deleteApiProvider(byte[] symkey,ApipClient apipClient){
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

    public void deleteApiAccount(byte[] symkey,ApipClient initApipClient){
        System.out.println("Deleting API Account...");
        ApiAccount apiAccount = chooseApiAccount(symkey,initApipClient);
        if(apiAccount==null) return;
        if(Inputer.askIfYes(br,"Delete API account "+apiAccount.getId()+"?")) {
            getApiAccountMap().remove(apiAccount.getId());
            System.out.println("Api account " + apiAccount.getId() + " is deleted.");
            saveConfig();
        }
        Menu.anyKeyToContinue(br);
    }

    public ApiAccount chooseApiAccount(byte[] symkey,ApipClient initApipClient){
        ApiAccount apiAccount = null;
        showAccounts(getApiAccountMap());
        int input = Inputer.inputInt(br, "Input the number of the account you want. Enter to add a new one:", getApiAccountMap().size());
        if (input == 0) {
            if(Inputer.askIfYes(br,"Add a new API account?")) {
                ApiProvider apiProvider = chooseApiProviderOrAdd(apiProviderMap, initApipClient);
                if(apiProvider==null)return null;
                apiAccount = addApiAccount(apiProvider, null, symkey, initApipClient);
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
            serviceType = core.fch.Inputer.chooseOne(Service.ServiceType.values(), null, "Choose the API type:",br);
        ApiProvider apiProvider = chooseApiProvider(apiProviderMap, serviceType);
        if(apiProvider==null){
            if(askIfYes(br,"Add new provider?"))
                apiProvider = addApiProvider(serviceType,apipClient);
            else return null;
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


    public ApiAccount getAccountForTheProvider(ApiProvider apiProvider, String userFid, byte[] symkey, ApipClient initApipClient) {
        if(apiProvider==null || apiProvider.getId()==null){
            System.out.println("The API provider or its ID is null.");
            return null;
        }
        System.out.println("Get account for "+apiProvider.getName()+"...");
        ApiAccount apiAccount;
        if (apiAccountMap == null) setApiAccountMap(new HashMap<>());

        if(apiAccountMap.size()!=0) {
            for (ApiAccount apiAccount1 : getApiAccountMap().values()) {
                if (apiAccount1.getProviderId().equals(apiProvider.getId())) {
                    String account1UserId = apiAccount1.getUserId();
                    if (account1UserId != null && account1UserId.equals(userFid) && KeyTools.isGoodFid(account1UserId)) {
                        apiAccount1.setApipClient(initApipClient);
                        if (apiAccount1.getClient() == null) apiAccount1.connectApi(apiProvider, symkey, br);
                        return apiAccount1;
                    }
                }
            }
        }
        apiAccount = addApiAccount(apiProvider, userFid, symkey,initApipClient );
        if(apiAccount==null)return null;
        apiAccount.setApipClient(initApipClient);
        if(apiAccount.getClient()==null)apiAccount.connectApi(apiProvider,symkey, br);
        return apiAccount;
    }

    public ApiAccount findAccountForTheProvider(ApiProvider apiProvider, String userFid, byte[] symkey, ApipClient initApipClient) {
        System.out.println("Get account for "+apiProvider.getName()+"...");
        ApiAccount apiAccount;
        Map<String, ApiAccount> hitApiAccountMap = new HashMap<>();
        if (apiAccountMap == null) setApiAccountMap(new HashMap<>());

        if(apiAccountMap.size()!=0){
            for (ApiAccount apiAccount1 : getApiAccountMap().values()) {
                if (apiAccount1.getProviderId().equals(apiProvider.getId())) {
                    String account1UserId = apiAccount1.getUserId();
                    if(account1UserId !=null && account1UserId.equals(userFid) && KeyTools.isGoodFid(account1UserId)) {
                        apiAccount1.setApipClient(initApipClient);
                        if (apiAccount1.getClient() == null) apiAccount1.connectApi(apiProvider, symkey, br);
                        return apiAccount1;
                    }else hitApiAccountMap.put(apiAccount1.getId(), apiAccount1);
                }
            }

            if(hitApiAccountMap.size()==0) {
                apiAccount = addApiAccount(apiProvider, userFid, symkey,initApipClient );
            }else if(hitApiAccountMap.size()==1){
                String key = (String)hitApiAccountMap.keySet().toArray()[0];
                apiAccount = hitApiAccountMap.get(key);
                if(confirmDefault(br,apiAccount.getUserName())) {
                    apiAccount.setApipClient(initApipClient);
                    if(apiAccount.getClient()==null)apiAccount.connectApi(apiProvider,symkey, br);
                    return apiAccount;
                } else apiAccount = addApiAccount(apiProvider, userFid, symkey,initApipClient );
            }else {
                showAccounts(hitApiAccountMap);

                int input = Inputer.inputInt(br, "Input the number of the account you want. Enter to add new one:", hitApiAccountMap.size());
                if (input == 0) {
                    apiAccount = addApiAccount(apiProvider, userFid, symkey, initApipClient);
                } else {
                    apiAccount = (ApiAccount) hitApiAccountMap.values().toArray()[input - 1];
                }
            }
        }else apiAccount = addApiAccount(apiProvider, userFid, symkey,initApipClient );

        apiAccount.setApipClient(initApipClient);
        if(apiAccount.getClient()==null)apiAccount.connectApi(apiProvider,symkey, br);
        return apiAccount;
    }

//        System.out.println("No API accounts yet. Add new one...");
//        return addApiAccount(apiProvider, userFid, symkey, initApipClient);


//        if (apiAccountMap.size() == 0) {
//            System.out.println("No API accounts yet. Add new one...");
//            apiAccount = addApiAccount(apiProvider, userFid, symkey, initApipClient);
//        } else {
//            for (ApiAccount apiAccount1 : getApiAccountMap().values()) {
//                if (apiAccount1.getProviderId().equals(apiProvider.getId())) {
//                    hitApiAccountMap.put(apiAccount1.getId(), apiAccount1);
//                }
//            }
//            if (hitApiAccountMap.size() == 0) {
//                apiAccount = addApiAccount(apiProvider, userFid, symkey, initApipClient);
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
//                    apiAccount = addApiAccount(apiProvider, userFid, symkey,initApipClient );
//                } else {
//                    apiAccount = (ApiAccount) hitApiAccountMap.values().toArray()[input - 1];
//                    apiAccount.setApipClient(initApipClient);
//                    if(apiAccount.getClient()==null)apiAccount.connectApi(apiProvider,symkey);
//                }
//            }
//        }
//        return apiAccount;

    public ApiAccount checkAPI(@Nullable String apiAccountId, String userFid, Service.ServiceType serviceType, byte[] symkey) {
        if(serviceType !=null) System.out.println("\nCheck "+ serviceType +" API...");
        apiAccountMap.remove("null");
        ApiAccount apiAccount = null;
        while (true) {
            if (apiAccountId == null) {
                System.out.println("No " + serviceType + " account set yet. ");
                    apiAccount = getApiAccount(symkey, userFid, serviceType, apipClient);
            }else {
                apiAccount = apiAccountMap.get(apiAccountId);
                if(apiAccount ==null || askIfYes(br,"Current API is from "+apiAccount.getApiUrl()+" Change it?"))
                    apiAccount = getApiAccount(symkey, userFid, serviceType, apipClient);
            }

            if (apiAccount == null) {
                if(askIfYes(br,"Failed to get API account. Try again?")) continue;
                return null;
            }

            if (apiAccount.getClient() == null) {
                Object apiClient;
                try {
                    apiClient = apiAccount.connectApi(getApiProviderMap().get(apiAccount.getProviderId()), symkey, br, apipClient, mainCidInfoMap);
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

//    public ApiAccount setApiService(byte[] symkey,ApipClient apipClient) {
//        ApiProvider apiProvider = chooseApiProviderOrAdd(apiProviderMap, apipClient);
//        ApiAccount apiAccount = chooseApiProvidersAccount(apiProvider,symkey,apipClient);
//        if(apiAccount.getClient()!=null) saveConfig();
//        return apiAccount;
//    }

    public ApiAccount getApiAccount(byte[] symkey, String userFid, Service.ServiceType serviceType, ApipClient apipClient) {
        ApiProvider apiProvider = chooseApiProvider(apiProviderMap, serviceType);

        while(apiProvider==null) {
            apiProvider = addApiProvider(serviceType,apipClient);
            if(apiProvider!=null)break;

            if(!Inputer.askIfYes(br,"Failed to add API provider. Try again?"))return null;
        }

        if(apiProvider.getId()==null){
            System.out.println("The ID of the API provider is null. Update it...");
            apiProvider.updateAll(br);
            saveConfig();
        }

        ApiAccount apiAccount;
//        if(shareApiAccount)apiAccount = findAccountForTheProvider(apiProvider, userFid, symkey,apipClient);
//        else
        apiAccount = getAccountForTheProvider(apiProvider, userFid, symkey,apipClient);

        if(apiAccount!=null && apiAccount.getClient()!=null) saveConfig();
        return apiAccount;
    }



    public static Configure checkPassword(BufferedReader br){
        Configure configure;
        byte[] passwordBytes;
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
            Map<String, ApiProvider> providerMap = configure.getApiProviderMap();
            if(providerMap !=null && !providerMap.isEmpty()){
                providerMap.entrySet().removeIf(entry -> entry.getKey() == null);
            }

            Map<String, ApiAccount> accountMap = configure.getApiAccountMap();
            if(accountMap !=null && !accountMap.isEmpty()){
                accountMap.entrySet().removeIf(entry -> entry.getKey() == null);
            }
        }
        initConfigure(br, configure);
        return configure;
    }

    public static Configure verifyPassword(BufferedReader br) {
        byte[] symkey;
        byte[] nonceBytes;
        byte[] passwordBytes;
        String passwordName;
        Configure configure = null;
        char[] password = Inputer.inputPassword(br, "Input your password:");
        while (true) {
            if(password==null)continue;
            passwordBytes = BytesUtils.utf8CharArrayToByteArray(password);
            passwordName = makePasswordHashName(passwordBytes);
            configure = configureMap.get(passwordName);
            if (configure != null) {
                nonceBytes = Hex.fromHex(configure.getNonce());
                symkey = getSymkeyFromPasswordAndNonce(passwordBytes, nonceBytes);
                configure.setSymkey(symkey);
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

    public static boolean checkPassword(BufferedReader br, byte[] symkey,Configure configure) {
        while(true){    
            char[] password = Inputer.inputPassword(br, "Input your password:");
            if(password==null)return false;
            byte[] passwordBytes = BytesUtils.utf8CharArrayToByteArray(password);
            byte[] nonceBytes = Hex.fromHex(configure.getNonce());
            byte[] symkey1 = getSymkeyFromPasswordAndNonce(passwordBytes, nonceBytes);
            if(Arrays.equals(symkey, symkey1))return true;
            System.out.println("Wrong password. Try again.");
        }
    }


    public boolean checkSimplePassword(){
        String passwordHash;
        byte[] passwordBytes;

        if(getPasswordHash()==null){
            while(true) {
                passwordBytes = Inputer.resetNewPassword(br);
                if(passwordBytes!=null) {
                    passwordHash = Hex.toHex(passwordBytes);
                    setPasswordHash(passwordHash);
                    saveConfig();
                    this.symkey = Hash.sha256(passwordBytes);
                    return true;
                }
                System.out.println("A password is required. Try again.");
            }
        }else return verifyPassword(getPasswordHash(), br);
    }

    public boolean verifyPassword(String passwordHash,BufferedReader br) {
        byte[] passwordBytes;
        char[] password = Inputer.inputPassword(br, "Input your password:");
        while (true) {
            if(password==null)continue;
            passwordBytes = BytesUtils.utf8CharArrayToByteArray(password);
            byte[] newHash = Hash.sha256x2(passwordBytes);
            String newHashHex = Hex.toHex(newHash);
            if(passwordHash.equals(newHashHex))return true;

            String input = Inputer.inputString(br, "Password wrong. Try again. 'q' to quit:");
            if (input.equals("q")) {
                System.exit(0);
                return false;
            }
        }
    }

    @NotNull
    public static String makePasswordHashName(byte[] passwordBytes) {
        return Hex.toHex(Hash.sha256x2(passwordBytes)).substring(0, 6);
    }

    private static Configure creatNewConfigure(byte[] passwordBytes) {
        Configure configure = new Configure();
        byte[] symkey;
        byte[] nonceBytes;
        nonceBytes = BytesUtils.getRandomBytes(16);
        symkey = getSymkeyFromPasswordAndNonce(passwordBytes, nonceBytes);
        configure.nonce = Hex.toHex(nonceBytes);
        configure.setSymkey(symkey);
        String name = makePasswordHashName(passwordBytes);
        configure.setPasswordName(name);
        configureMap.put(name,configure);
        saveConfig();
        BytesUtils.clearByteArray(passwordBytes);
        return configure;
    }

    private static void initConfigure(BufferedReader br, Configure configure) {
        configure.initFreeApiListMap();
        configure.setBr(br);
        if(configure.getApiProviderMap()==null)
                configure.setApiProviderMap(new HashMap<>());
        if(configure.getApiAccountMap() == null)
                configure.setApiAccountMap(new HashMap<>());
        if(configure.getMainCidInfoMap()==null)
                configure.setMainCidInfoMap(new HashMap<>());
        if(configure.getMyServiceMaskMap()==null)
                configure.setMyServiceMaskMap(new HashMap<>());
        if(configure.getOwnerList()==null)
                configure.setOwnerList(new ArrayList<>());
        if(configure.mainCidInfoMap ==null)
            configure.initFreeApiListMap();
    }

    public static byte[] getSymkeyFromPasswordAndNonce(byte[] passwordBytes, byte[] nonce) {
        return Hash.sha256(BytesUtils.bytesMerger(passwordBytes, nonce));
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
            configureMap = JsonUtils.readMapFromJsonFile(path, CONFIG_DOT_JSON, String.class,Configure.class);
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

    public Map<String, CidInfo> getMainCidInfoMap() {
        return mainCidInfoMap;
    }

    public void setMainCidInfoMap(Map<String, CidInfo> mainCidInfoMap) {
        this.mainCidInfoMap = mainCidInfoMap;
    }

    public String addOwner(BufferedReader br) {
        String owner = core.fch.Inputer.inputGoodFid(br,"Input the FID or pubkey of the owner:");

        if(ownerList ==null) ownerList = new ArrayList<>();

        ownerList.add(owner);

        saveConfig();

        return owner;
    }


    public String chooseMainFid(byte[] symkey) {
        while(true) {
            String fid = Inputer.chooseOne(mainCidInfoMap.keySet().toArray(new String[0]), null, "Choose the FID", br);
            if (fid == null) {
                System.out.println("No FID chosen.");
                if (askIfYes(br, "Add a new FID?"))
                    return addUser(symkey);
                else continue;
            }
            String fidCipher = mainCidInfoMap.get(fid).getPrikeyCipher();
            if(fidCipher==null || fidCipher.equals("")){
                System.out.println("This is a watch-only FID.");
                Menu.anyKeyToContinue(br);
                return fid;
            }     
            return fid;
        }
    }

    public String getServiceDealer(String sid, byte[] symkey) {

        ServiceMask serviceMask = myServiceMaskMap.get(sid);
        if(serviceMask!=null && serviceMask.getDealer()!=null)
            return serviceMask.getDealer();

        System.out.println("Set the dealer of your service which was published on-chain...");
        return chooseMainFid(symkey);
    }

    public String chooseSid(Service.ServiceType serviceType) {
        Map<String, ServiceMask> map = new HashMap<>();
        if(serviceType!=null){
            for(String key: myServiceMaskMap.keySet()){
                ServiceMask serviceSummary = myServiceMaskMap.get(key);
                if(StringUtils.isContainCaseInsensitive(serviceSummary.getTypes(),serviceType.name()))
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

    public byte[] getSymkey() {
        return symkey;
    }

    public void setSymkey(byte[] symkey) {
        this.symkey = symkey;
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
