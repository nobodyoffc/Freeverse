package config;

import core.crypto.*;
import data.fcData.CidInfo;
import clients.*;
import data.fcData.FcSession;
import data.apipData.RequestBody;
import handlers.AccountHandler;
import ui.Inputer;
import ui.Menu;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import data.fcData.ReplyBody;
import handlers.CashHandler;
import org.bitcoinj.fch.FchMainNetwork;
import utils.IdNameUtils;
import core.fch.TxCreator;
import data.fchData.Cash;
import data.fchData.SendTo;
import data.feipData.Service;
import data.feipData.serviceParams.ApipParams;
import data.feipData.serviceParams.Params;
import utils.*;
import utils.http.AuthType;
import utils.http.RequestMethod;
import clients.NaSaClient.NaSaRpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static ui.Inputer.*;
import static server.ApipApiNames.VERSION_1;
import static constants.Constants.APIP_Account_JSON;
import static constants.Constants.COIN_TO_SATOSHI;
import static core.crypto.KeyTools.prikeyToFid;
import static data.fcData.AlgorithmId.FC_AesCbc256_No1_NrC7;
import static data.fcData.AlgorithmId.FC_EccK1AesCbc256_No1_NrC7;

public class ApiAccount {
    private static final Logger log = LoggerFactory.getLogger(ApiAccount.class);
    public static long minRequestTimes = 100;
    public static long orderRequestTimes = 10000;
    private transient byte[] password;
    private String id;
    private String providerId;
    private String userId;
    private String userName;
    private String passwordCipher;
    private String userPubkey;
    private String userPrikeyCipher;
    private FcSession fcSession;
    private Double minPayment;
    private Map<String,Double> payments;
    private transient byte[] sessionKey;
    private transient Service service;
    private transient Params serviceParams;
    private String apiUrl;
    private String via;
    private long bestHeight;
    private String bestBlockId;
    private long balance;
    private transient Object client;
    private transient EsClientMaker esClientMaker;
    private transient ApipClient apipClient;

    public static void updateSession(ApiAccount apipAccount, byte[] symkey, FcSession fcSession, byte[] newSessionKey) {
        String newSessionKeyCipher = new Encryptor(FC_EccK1AesCbc256_No1_NrC7).encryptToJsonBySymkey(newSessionKey, symkey);
        if(newSessionKeyCipher.contains("Error"))return;
        fcSession.setKeyCipher(newSessionKeyCipher);
        apipAccount.fcSession.setKeyCipher(newSessionKeyCipher);
        String newSessionName = IdNameUtils.makeKeyName(newSessionKey);
        apipAccount.fcSession.setId(newSessionName);
        System.out.println("SessionName:" + newSessionName);
        System.out.println("SessionKeyCipher: " + fcSession.getKeyCipher());
    }

    public boolean isBalanceSufficient(){
        long price;
        if(serviceParams.getPricePerKBytes()!=null){
            price= (long) (NumberUtils.roundDouble8(Double.parseDouble(serviceParams.getPricePerKBytes()))*COIN_TO_SATOSHI);
        }else price= (long) (NumberUtils.roundDouble8(Double.parseDouble(serviceParams.getPricePerRequest()))*COIN_TO_SATOSHI);

        return balance < price * minRequestTimes;
    }

    public Object connectApi(ApiProvider apiProvider, byte[] symkey, BufferedReader br, @Nullable ApipClient apipClient, Map<String, CidInfo> fidCipherMap) {

        if (!checkApiGeneralParams(apiProvider, br)) return null;

        switch (apiProvider.getType()){
            case APIP -> {
                return connectApip(apiProvider,symkey,br);
            }
            case NASA_RPC -> {
                return connectNaSaRPC(symkey);
            }
            case ES -> {
                return connectEs(symkey);
            }
            case REDIS -> {
                return connectRedis();
            }
            case DISK -> {
                return connectDisk(apiProvider,symkey, apipClient, br, fidCipherMap);
            }
            case TALK -> {
                return connectTalk(apiProvider,symkey,apipClient,br,fidCipherMap);
            }
            case OTHER -> {
                return connectOtherApi(apiProvider, symkey);
            }
            default -> {
                System.out.println("The type of apiProvider is not supported:"+apiProvider.getType());
                return null;
            }
        }
    }


    public Object connectApi(ApiProvider apiProvider, byte[] symkey, BufferedReader br){

        if (!checkApiGeneralParams(apiProvider)) return null;
        try {
            return switch (apiProvider.getType()) {
                case APIP -> connectApip(apiProvider, symkey, br);
                case NASA_RPC -> connectNaSaRPC(symkey);
                case ES -> connectEs(symkey);
                case REDIS -> connectRedis();
                case DISK -> connectDisk(apiProvider, symkey, apipClient, br, null);
                default -> connectOtherApi(apiProvider, symkey);
            };
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }


    public void showApipBalance(){
        System.out.println("APIP balance: "+(double) balance/ COIN_TO_SATOSHI + " F");
        System.out.println("Rest request: "+balance/(Double.parseDouble(serviceParams.getPricePerKBytes()) * COIN_TO_SATOSHI)+" times");
    }

    private byte[] connectOtherApi(ApiProvider apiProvider, byte[] symkey) {
        System.out.println("Connect to some other APIs. Undeveloped.");
        System.out.println("Client on "+apiUrl +" is created.\n");
        return null;
    }

    private boolean checkApiGeneralParams(ApiProvider apiProvider, BufferedReader br) {
        if(!providerId.equals(apiProvider.getId())){
            System.out.println("The SID of apiProvider "+ apiProvider.getId()+" is not the same as the SID "+ providerId +" in apiAccount.");
            if(Inputer.askIfYes(br,"Reset the SID of apiAccount to "+ apiProvider.getId()+"?")){
                providerId = apiProvider.getId();
            }else return false;
        }

        if (apiUrl==null && apiProvider.getApiUrl() == null) {
            System.out.println("The apiUrl is required.");
            if(Inputer.askIfYes(br,"Reset the apiUrl(urlHead)?")){
                inputApiUrl(br);
                apiProvider.setApiUrl(apiUrl);
            }
            return false;
        }

        if(! apiProvider.getApiUrl().equals(this.apiUrl)){
            this.apiUrl = apiProvider.getApiUrl();
        }
        return true;
    }


    private boolean checkApiGeneralParams(ApiProvider apiProvider){
        if(!providerId.equals(apiProvider.getId())){
            log.error("The SID of apiProvider "+ apiProvider.getId()+" is not the same as the SID "+ providerId +" in apiAccount.");
            return false;
        }

        if (apiUrl==null && apiProvider.getApiUrl() == null) {
            log.error("The apiUrl is required.");
            return false;
        }

        if(! apiProvider.getApiUrl().equals(this.apiUrl)){
            this.apiUrl = apiProvider.getApiUrl();
        }
        return true;
    }

    private NaSaRpcClient connectNaSaRPC(byte[] symkey) {
        if(apiUrl==null) {
            System.out.println("The URL of the API is necessary.");
            return null;
        }
        if(userName==null) {
            System.out.println("The username of the API is necessary.");
            return null;
        }
        if(passwordCipher==null) {
            System.out.println("The password of the API is necessary.");
            return null;
        }
        CryptoDataByte cryptoDataByte = new Decryptor().decryptJsonBySymkey(passwordCipher, symkey);
        password = cryptoDataByte.getData();
        if(password==null)return null;

        NaSaRpcClient naSaRpcClient = new NaSaRpcClient(apiUrl,userName,password);


        NaSaRpcClient.BlockchainInfo blockchainInfo = naSaRpcClient.getBlockchainInfo();

        if(blockchainInfo==null){
            System.out.println("Failed to connect NaSa RPC.");
            return null;
        }

        naSaRpcClient.setBestBlockId(blockchainInfo.getBestblockhash());
        naSaRpcClient.setBestHeight(blockchainInfo.getBlocks()-1);
;
        this.bestHeight = blockchainInfo.getBlocks()-1;
        this.bestBlockId = blockchainInfo.getBestblockhash();
        this.client = naSaRpcClient;


        log.info("The NaSa node on "+apiUrl+" is connected.");
        System.out.println("NaSa RPC client on "+apiUrl +" is created.\n");
        return naSaRpcClient;
    }

    private ElasticsearchClient connectEs(byte[] symkey) {
        if(apiUrl==null) {
            System.out.println("The URL of the API is necessary.");
            return null;
        }
        esClientMaker = new EsClientMaker();
        esClientMaker.getEsClientSilent(this,symkey);

        try {
            IndicesResponse result = esClientMaker.esClient.cat().indices();
            log.info("Got ES client. There are "+result.valueBody().size()+" indices in ES.");
            System.out.println("ES client on "+apiUrl +" is created.\n");
        } catch (IOException e) {
            log.debug("Failed to create ES client. Check ES.");
            System.exit(0);
        }
        this.client = esClientMaker.esClient;
        return esClientMaker.esClient;
    }

    private DiskClient connectDisk(ApiProvider apiProvider, byte[] symkey, ApipClient apipClient, BufferedReader br, Map<String, CidInfo> cidInfoMap) {
        if(apipClient==null){
            System.out.println("APIP client is required for the DISK service.");
            System.exit(-1);
            return null;
        }

        DiskClient diskClient;
        if(client==null){
            diskClient = new DiskClient(apiProvider,this,symkey,apipClient);
            client = diskClient;
        }
        else diskClient = (DiskClient) client;

        if(!apiProvider.getType().equals(Service.ServiceType.DISK)){
            System.out.println("It's not Disk provider.");
            if(br!=null)
                if(Inputer.askIfYes(br,"Reset the type of apiProvider to "+ Service.ServiceType.DISK +"?")){
                apiProvider.setType(Service.ServiceType.DISK);
                }else return null;
            return null;
        }

        if(checkFcApiProvider(apiProvider, Service.ServiceType.DISK, apipClient)==null){
            log.debug("Failed to check service with APIP service {}.", apipClient.getApiProvider().getApiUrl());
            if(br!=null)Menu.anyKeyToContinue(br);
            return null;
        }

        if (userPrikeyCipher == null) {
            System.out.println("Set requester prikey...");
            if(br!=null)inputPrikeyCipher(symkey,br,cidInfoMap);
            else return null;
        }

        byte[] sessionKey1 =
                checkSessionKey(symkey, apiProvider.getType(), br);

        if(sessionKey1==null) {
            System.out.println("Failed to connect Disk server.");
            return null;
        }

        sessionKey = sessionKey1;
        diskClient.setSessionKey(sessionKey);
        client = diskClient;

        log.debug("Connected to the Disk service: " + providerId + " on " + apiUrl);
        System.out.println("Disk client on "+apiUrl +" is created.\n");
        return diskClient;
    }

    private TalkClient connectTalk(ApiProvider apiProvider, byte[] symkey, ApipClient apipClient, BufferedReader br, Map<String, CidInfo> fidCipherMap) {
        if(apipClient==null){
            System.out.println("APIP client is required for the TALK service.");
            System.exit(-1);
            return null;
        }

        if(!apiProvider.getType().equals(Service.ServiceType.TALK)){
            System.out.println("It's not Talk provider.");
            if(br!=null)
                if(Inputer.askIfYes(br,"Reset the type of apiProvider to "+ Service.ServiceType.TALK +"?")){
                    apiProvider.setType(Service.ServiceType.TALK);
                }else return null;
            return null;
        }

        if(checkFcApiProvider(apiProvider, Service.ServiceType.TALK, apipClient)==null){
            log.debug("Failed to check service with APIP service {}.", apipClient.getApiProvider().getApiUrl());
            if(br!=null)Menu.anyKeyToContinue(br);
            return null;
        }
        String accountPubkey = apipClient.getPubkey(apiProvider.getApiParams().getDealer(), RequestMethod.POST, AuthType.FC_SIGN_BODY);

        if (accountPubkey != null) {
            apiProvider.setDealerPubkey(accountPubkey);
        }

        if(this.sessionKey==null && this.fcSession!=null && this.fcSession.getKeyCipher()!=null){
            this.sessionKey = decryptSessionKey(this.fcSession.getKeyCipher(),symkey);
        }

        if (userPrikeyCipher == null) {
            System.out.println("Set requester prikey...");
            if(br!=null)inputPrikeyCipher(symkey,br,fidCipherMap);
            else return null;
        }


        TalkClient talkClient;
        if(client==null){
            talkClient = new TalkClient(apiProvider,this,symkey,apipClient, br);
            client = talkClient;
        }
        else talkClient = (TalkClient) client;

        return talkClient;
    }

    public void closeEs(){
        if(client==null){
            System.out.println("No ES esClient running.");
            return;
        }
        try {
            esClientMaker.shutdownClient();
        } catch (IOException e) {
            log.debug("Failed to close the esClient.");
        }
    }

    public JedisPool connectRedis() {
        JedisPool jedisPool;
        if(apiUrl==null)jedisPool = new JedisPool();
        else jedisPool=new JedisPool(apiUrl);
        this.client = jedisPool;
        try(Jedis jedis = jedisPool.getResource()){
            if(!"pong".equalsIgnoreCase(jedis.ping())){
                System.out.println("Failed to connect Redis server.");
                return null;
            }
        }

        log.info("The JedisPool is ready.");
        System.out.println("Redis client on "+apiUrl +" is created.\n");
        return jedisPool;
    }

    public byte[] inputPrikeyCipher(BufferedReader br, byte[] symkey) {
        byte[] prikey32;
        while (true) {
            String input = core.fch.Inputer.inputString(br, "Generate a new private key? y/n");
            if ("y".equals(input)) {
                prikey32 = KeyTools.genNewFid(br).getPrivKeyBytes();
            } else {
                prikey32 = KeyTools.importOrCreatePrikey(br);
            }
            if (prikey32 == null) return null;
            this.userPubkey = Hex.toHex(KeyTools.prikeyToPubkey(prikey32));
            userId = prikeyToFid(prikey32);
            System.out.println("The FID is: \n" + userId);

            Encryptor encryptor = new Encryptor(FC_AesCbc256_No1_NrC7);
            CryptoDataByte cryptoDataByte = encryptor.encryptBySymkey(prikey32,symkey);//EccAes256K1P7.encryptWithSymkey(prikey32, symkey);
            if(cryptoDataByte.getCode() !=0){
                System.out.println(cryptoDataByte.getMessage());
                return null;
            }
            String buyerPrikeyCipher = cryptoDataByte.toJson();
            if (buyerPrikeyCipher.contains("Error")) continue;
            userPrikeyCipher = buyerPrikeyCipher;
            BytesUtils.clearByteArray(prikey32);
            return prikey32;
        }
    }

    public static ApiAccount checkApipAccount(BufferedReader br, byte[] symkey) {

        ApiAccount apiAccount = readApipAccountFromFile();

        byte[] sessionKey;

        if (apiAccount == null) {
            apiAccount = createApipAccount(br, symkey);
            if (apiAccount == null) return null;
        }

        boolean revised = false;

        if (apiAccount.getApiUrl() == null) {
            apiAccount.inputApiUrl(br);
            System.out.println("Request the service information...");
            Service service = getService(apiAccount.apiUrl);
            apiAccount.setProviderId(service.getId());

            revised = true;
        }
        if (apiAccount.getUserPrikeyCipher() == null) {
            apiAccount.inputPrikeyCipher(br, symkey);
            if (apiAccount.getUserPrikeyCipher() == null) return null;
            revised = true;
        }

        if (apiAccount.fcSession.getKeyCipher() == null) {
            sessionKey = apiAccount.freshSessionKey(symkey, Service.ServiceType.APIP, RequestBody.SignInMode.NORMAL, null);
            if (sessionKey == null) return null;
            revised = true;
        }

        if (revised) writeApipParamsToFile(apiAccount, APIP_Account_JSON);

        return apiAccount;
    }

    @Nullable
    public static ApiAccount createApipAccount(BufferedReader br, byte[] symkey) {
        byte[] sessionKey;
        ApiAccount apiAccount = new ApiAccount();

        System.out.println("Input the urlHead of the APIP service. Enter to set as 'https://cid.cash/APIP':");

        String urlHead = core.fch.Inputer.inputString(br);

        if ("".equals(urlHead)) {
            urlHead = "https://cid.cash/APIP";
        }
        apiAccount.setApiUrl(urlHead);

        System.out.println("Request the service information...");
        Service service = getService(urlHead);
        if (service == null) {
            System.out.println("Get APIP service wrong.");
            return null;
        }
        apiAccount.setProviderId(service.getId());
        apiAccount.setService(service);
        ApipParams serviceParams1 = ApipParams.fromObject(service.getParams());
        apiAccount.setApipParams(serviceParams1);
        try {
            apiAccount.setMinPayment(Double.valueOf(serviceParams1.getMinPayment()));
        }catch (Exception ignore){}

        apiAccount.inputVia(br);

        System.out.println();
        System.out.println("Set the APIP service buyer(requester)...");
        apiAccount.inputPrikeyCipher(br, symkey);
        sessionKey = apiAccount.freshSessionKey(symkey, Service.ServiceType.APIP, RequestBody.SignInMode.NORMAL, null);
        if (sessionKey == null) return null;

        writeApipParamsToFile(apiAccount, APIP_Account_JSON);
        return apiAccount;
    }

    static Service getService(String urlHead) {
        ReplyBody replier = ApipClient.getService(urlHead, VERSION_1, ApipParams.class);
        if(replier==null)return null;
        Service service = (Service) replier.getData();
        if(service!=null) {
            System.out.println("Got the service:");
            System.out.println(JsonUtils.toNiceJson(service));
        }
        return service;
    }

    public static byte[] decryptHexWithPrikey(String cipher, byte[] prikey) {

        CryptoDataByte cryptoDataBytes = new Decryptor().decryptJsonByAsyOneWay(cipher, prikey);
        if (cryptoDataBytes.getCode() != 0) {
            System.out.println("Failed to decrypt: " + cryptoDataBytes.getMessage());
            BytesUtils.clearByteArray(prikey);
            return null;
        }
        String sessionKeyHex = new String(cryptoDataBytes.getData(), StandardCharsets.UTF_8);
        return HexFormat.of().parseHex(sessionKeyHex);
    }

    public static void writeApipParamsToFile(ApiAccount apipParamsForClient, String fileName) {
        JsonUtils.writeObjectToJsonFile(apipParamsForClient, fileName, false);
    }

    public static ApiAccount readApipAccountFromFile() {
        File file = new File(APIP_Account_JSON);

        ApiAccount apipParamsForClient;
        try {
            if (!file.exists()) {
                boolean done = file.createNewFile();
                if (!done) {
                    System.out.println("Create " + APIP_Account_JSON + " wrong.");
                }
            }
            FileInputStream fis = new FileInputStream(file);
            apipParamsForClient = JsonUtils.readObjectFromJsonFile(fis, ApiAccount.class);
            if (apipParamsForClient != null) return apipParamsForClient;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }

    public String makeApiAccountId(String sid, String userName) {
        byte[] bundleBytes;
        if(userName!=null) {
            bundleBytes = BytesUtils.bytesMerger(sid.getBytes(), userName.getBytes());
        }else bundleBytes = sid.getBytes();
        return HexFormat.of().formatHex(Hash.sha256x2(bundleBytes));
    }

    public void inputAll(byte[] symkey, ApiProvider apiProvider, String userFid, Map<String, CidInfo> fidInfoMap, BufferedReader br) {
        try  {
            this.providerId =apiProvider.getId();
            this.apiUrl = apiProvider.getApiUrl();
            Service.ServiceType type = apiProvider.getType();
            if(type==null)type = chooseOne(Service.ServiceType.values(), null, "Choose the type:",br);
            this.userId= userFid;
            switch (type) {
                case ES -> {
                    if(askIfYes(br,"Set username and password for ES?")) {
                        inputUsername(br);
                        inputPasswordCipher(symkey, br);
                    }
                }
                case NASA_RPC -> {
                    inputUsername(br);
                    inputPasswordCipher(symkey, br);
                }
                case REDIS -> {
                    if(askIfYes(br,"Set password for REDIS?")) {
                        inputPasswordCipher(symkey, br);
                    }
                }
                case APIP, DISK,TALK -> {
                    if(providerId ==null)inputSid(br);

                    userPrikeyCipher = fidInfoMap.get(userFid).getPrikeyCipher();

                    while(userName==null) {
                        if(userId!=null)userName= userFid;
                        else{
                            userName = Inputer.inputString(br,"Input your userName");
                        }
                    }
                    inputVia(br);
                }
                default -> {
                    inputUsername(br);
                    inputUserId(br);
                    inputPasswordCipher(symkey, br);
                    inputPasswordCipher(symkey, br);
                    inputSessionName(br);
                    inputSessionKeyCipher(symkey, br);
                }
            }
            this.id = makeApiAccountId(providerId,this.userName);
        } catch (IOException e) {
            System.out.println("Error reading input");
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.out.println("Invalid number format");
            e.printStackTrace();
        }
    }

//    private void checkUserCipher(byte[] symkey, String userFid, Map<String, CidInfo> fidInfoMap, BufferedReader br) {
//        String cipher = null;
//        CidInfo cidInfo;
//        if(fidInfoMap !=null) {
//            cidInfo = fidInfoMap.get(userFid);
//            if (userFid != null) {
//                cipher = cidInfo.getPrikeyCipher();
//            }
//            if(cipher==null)
//                cipher = chooseUserCipher(symkey, fidInfoMap, br, cipher);
//        }
//
//        if(cipher==null) inputPrikeyCipher(symkey, br,fidInfoMap);
//    }

//    public String chooseUserCipher(byte[] symkey, Map<String, CidInfo> fidInfoMap, BufferedReader br, String cipher) {
//        String choice = chooseOneKeyFromMap(fidInfoMap, false, null, "Choose the account FID for this service:", br);
//        if(choice!=null) {
//            cipher = fidInfoMap.get(choice).getPrikeyCipher();
//            this.userPrikeyCipher = cipher;
//            this.userPubkey = makePubkey(this.userPrikeyCipher, symkey);
//        }
//        return cipher;
//    }

    private void inputSid(BufferedReader br) throws IOException {
        this.providerId = Inputer.promptAndSet(br, "sid", this.providerId);
    }
    private void inputUserId(BufferedReader br) throws IOException {
        this.userName = Inputer.promptAndSet(br, "userId", this.userId);
    }
    private void inputUsername(BufferedReader br) throws IOException {
        this.userName = Inputer.promptAndSet(br, "userName", this.userName);
    }

    public void updateAll(byte[]symkey, ApiProvider apiProvider,BufferedReader br) {
        try {
            this.providerId = promptAndUpdate(br, "sid", this.providerId);
            this.userName = promptAndUpdate(br, "userName", this.userName);
            this.passwordCipher = updateKeyCipher(br, "user's passwordCipher", this.passwordCipher,symkey);
            this.userPrikeyCipher = updateKeyCipher(br, "userPrikeyCipher", this.userPrikeyCipher,symkey);
            this.userPubkey = makePubkey(this.userPrikeyCipher,symkey);
            this.fcSession.setId(promptAndUpdate(br, "sessionName", this.fcSession.getId()));
            this.fcSession.setKeyCipher(updateKeyCipher(br, "sessionKeyCipher", this.fcSession.getKeyCipher(),symkey));

            this.via = promptAndUpdate(br, "via", this.via);
            this.id = makeApiAccountId(providerId,this.userName);
        } catch (IOException e) {
            System.out.println("Error reading input");
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.out.println("Invalid number format");
            e.printStackTrace();
        }
    }

    public static String makePubkey(String userPrikeyCipher, byte[] symkey) {
        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonBySymkey(userPrikeyCipher,symkey);
        if(cryptoDataByte.getCode()!=0)return null;
        byte[] pubkey = KeyTools.prikeyToPubkey(cryptoDataByte.getData());
        return Hex.toHex(pubkey);
    }

    public static CidInfo makeFidInfoByPrikeyCipher(String userPrikeyCipher, byte[] symkey) {
        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonBySymkey(userPrikeyCipher,symkey);
        if(cryptoDataByte.getCode()!=0)return null;
        byte[] prikey = cryptoDataByte.getData();
        return new CidInfo(prikey,symkey);
    }

    private String updateKeyCipher(BufferedReader reader, String fieldName, String currentValue, byte[] symkey) throws IOException {
        System.out.println(fieldName + " current value: " + currentValue);
        System.out.print("Do you want to update it? (y/n): ");

        if ("y".equalsIgnoreCase(reader.readLine())) {
            return inputKeyCipher(reader,fieldName,symkey);
        }
        return currentValue;
    }

    private void inputPasswordCipher(byte[] symkey, BufferedReader br) throws IOException {
        char[] password = Inputer.inputPassword(br, "Input the password. Enter to ignore:");
        if(password!=null)
            this.passwordCipher = new Encryptor(FC_AesCbc256_No1_NrC7).encryptToJsonBySymkey(BytesUtils.utf8CharArrayToByteArray(password), symkey);
        if(this.passwordCipher==null) {
           String input = inputKeyCipher(br, "user's passwordCipher", symkey);
           if (input == null) return;
           this.passwordCipher = input;
       }
    }

    public void inputPrikeyCipher(byte[] symkey, BufferedReader br, Map<String, CidInfo> cidInfoMap) {
        System.out.println();
        if(Inputer.askIfYes(br,"Set the API buyer prikey?"))
            while(true) {
                try {
                    String cipherJson = core.fch.Inputer.inputPrikeyCipher(br, symkey);
                    if(cipherJson==null){
                        System.out.println("Wrong input. Try again.");
                        continue;
                    }
                    this.userPrikeyCipher = cipherJson;

                    CidInfo cidInfo = makeFidInfoByPrikeyCipher(cipherJson,symkey);
                    if(cidInfo==null){
                        System.out.println("Failed to make CID info");
                        System.out.println("Try again.");
                        continue;
                    }
                    this.userPubkey = cidInfo.getPubkey();
                    String fid = KeyTools.pubkeyToFchAddr(Hex.fromHex(this.userPubkey));
                    cidInfoMap.put(fid,cidInfo);
                    break;
                }catch (Exception e){
                    System.out.println("Wrong input. Try again.");
                }
            }
    }

    private void inputSessionName(BufferedReader br) throws IOException {
        this.fcSession.setId(Inputer.promptAndSet(br, "sessionName", this.fcSession.getId()));
    }

    private void inputSessionKeyCipher(byte[] symkey, BufferedReader br) throws IOException {
        this.fcSession.setKeyCipher(inputKeyCipher(br, "sessionKeyCipher", symkey));
    }

    public static String inputKeyCipher(BufferedReader br, String keyName, byte[]symkey) {
        byte[] password;
        while(true) {
            System.out.println("Input the " + keyName + ", enter to exit:");
            String str = Inputer.inputString(br);
            if ("".equals(str)) {
                return null;
            }
            try {
                CryptoDataByte cryptoDataByte = new Decryptor().decryptJsonByPassword(str, BytesUtils.byteArrayToUtf8CharArray(Inputer.getPasswordBytes(br)));
                if(cryptoDataByte.getCode()!=0){
                    System.out.println("Something wrong. Try again.");
                    continue;
                }
                password = cryptoDataByte.getData();
                break;
            }catch (Exception e){
                System.out.println("Something wrong. Try again.");
            }
        }
        return new Encryptor(FC_EccK1AesCbc256_No1_NrC7).encryptToJsonBySymkey(password,symkey);
    }

    public static void waitConfirmation(String cashId, ApipClient apipClient) {
        while (true) {
            try {
                TimeUnit.SECONDS.sleep(60);
                Map<String, Cash> result = apipClient.cashByIds(RequestMethod.GET, AuthType.FREE, cashId);
                if(result!=null && result.get(cashId)!=null && !result.get(cashId).isValid()){
                    System.out.println("Confirmed!");
                    break;
                }
                System.out.println("Wait confirmation...");
            } catch (InterruptedException ignore) {
            }
        }
    }

    public boolean updateService(String sid, ApipClient apipClient) {
        Service service = apipClient.serviceById(sid);
        if(service == null)return false;
        Params params = Params.getParamsFromService(service,Params.class);
        if(params==null)return false;
        service.setParams(params);
        this.service= service;
        this.serviceParams=params;
        return true;
    }

    public Double buyApi(byte[] symkey, ApipClient apipClient, @Nullable BufferedReader br) {
        if(apipClient==null) apipClient = Settings.getFreeApipClient();

        byte[] prikey = decryptUserPrikey(userPrikeyCipher, symkey);
        Long minPay = utils.FchUtils.coinStrToSatoshi(serviceParams.getMinPayment());
        if(minPay==null)minPay= AccountHandler.DEFAULT_MIN_PAYMENT;

        Long price;
        price = FchUtils.coinStrToSatoshi(serviceParams.getPricePerKBytes());
        if(price==null) {
            System.out.println("The price of APIP service is 0.");
            price = 0L;
        }

        double payValue;

        payValue = (double) Math.max(price * orderRequestTimes, minPay) / COIN_TO_SATOSHI;

        List<SendTo> sendToList = new ArrayList<>();
        SendTo sendTo = new SendTo();

        sendTo.setFid(serviceParams.getDealer());
        sendTo.setAmount(payValue);
        sendToList.add(sendTo);

        List<Cash> cashList = apipClient.cashValid(this.userId,payValue,null,sendToList.size(),null, RequestMethod.GET,AuthType.FREE);//apipClient.getCashes(apiUrl,this.userId,payValue);
        if(cashList==null|| cashList.isEmpty())return null;
        String signedTx;

        if(prikey==null){
            signedTx = CashHandler.makeOffLineTx(this.userId, cashList,sendToList, 0L, TxCreator.DEFAULT_FEE_RATE,null, "2", br);
        }else {
            signedTx = TxCreator.createTxFch(cashList, prikey, sendToList, null, FchMainNetwork.MAINNETWORK);
        }
        if(signedTx==null)return null;

        String result = apipClient.broadcastTx(signedTx, RequestMethod.GET, AuthType.FREE);
        if(!Hex.isHex32(result)){
            log.error("Failed to buy APIP service. Failed to broadcast TX:"+result);
            return null;
        }
        log.debug("TxId: " + result);
        System.out.println("Paid for APIP service: " + payValue + "f to " + serviceParams.getDealer() + " by "+result+". \nWait for the confirmation for a few minutes...");

        waitConfirmation(cashList.get(0).getId(), apipClient);
        BytesUtils.clearByteArray(prikey);
        if(payments==null)payments=new HashMap<>();
        payments.put(result,payValue);
        return payValue;
    }


    public ApipClient connectApip(ApiProvider apiProvider, byte[] symkey, BufferedReader br){

        if(!apiProvider.getType().equals(Service.ServiceType.APIP)){
            System.out.println("It's not APIP provider.");
            if(br!=null)
                if(Inputer.askIfYes(br,"Reset the type of apiProvider to "+ Service.ServiceType.APIP+"?")){
                apiProvider.setType(Service.ServiceType.APIP);
                }else return null;
        }

        if(!checkApipProvider(apiProvider,apiUrl)){
            log.debug("Failed to get service from {}", apiProvider.getApiUrl());
            if(br!=null)Menu.anyKeyToContinue(br);
            return null;
        }

        ApipClient apipClient;

        if(client==null){
            apipClient = new ApipClient(apiProvider,this,symkey);
            client = apipClient;
        }else apipClient=(ApipClient) client;

        byte[] sessionKey1 = checkSessionKey(symkey, apiProvider.getType(), br);

        if(sessionKey1==null) {
            System.out.println("Failed to get the sessionKey of APIP service from "+apiUrl+". Only free APIs are available.");
            return null;
        }

        sessionKey = sessionKey1;
        System.out.println("Connected to the APIP service: " + providerId + " on " + apiUrl);

        apipClient.setUrlHead(apiUrl);
        apipClient.setVia(via);

        return apipClient;
    }

    public boolean checkApipProvider(ApiProvider apiProvider,String apiUrl) {
        ReplyBody replier = ApipClient.getService(apiUrl, VERSION_1, ApipParams.class);
        if(replier==null || replier.getData()==null) {
            return false;
        }
        service = (Service) replier.getData();
        serviceParams = (ApipParams) service.getParams();

        try {
            if (serviceParams != null) {
                this.minPayment = Double.valueOf(serviceParams.getMinPayment());
            }
        }catch (Exception ignore){}

        providerId = service.getId();
        if(service.getUrls()!=null && service.getUrls().length>0)
            apiProvider.setOrgUrl(service.getUrls()[0]);
        ApipParams apipParams = (ApipParams) service.getParams();
        if(apipParams!=null && apipParams.getUrlHead()!=null)
            this.apiUrl = apipParams.getUrlHead();
        apiProvider.setOwner(service.getOwner());
        apiProvider.setProtocols(service.getProtocols());
        apiProvider.setTicks(new String[]{"core/fch"});

        return true;
    }

    public ApiProvider checkFcApiProvider(ApiProvider apiProvider, Service.ServiceType type, ApipClient apipClient) {

        System.out.println("Update API provider from APIP service...");
        Map<String, Service> serviceMap = apipClient.serviceByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, apiProvider.getId());
        if(serviceMap==null || serviceMap.get(apiProvider.getId())==null)return null;

        this.service = serviceMap.get(apiProvider.getId());
        apiProvider.fromFcService(service, Params.getParamsClassByApiType(type) );

        this.serviceParams = (Params) service.getParams();
        try{
            this.minPayment = Double.valueOf(this.serviceParams.getMinPayment());
        }catch (Exception ignore){}
        this.providerId = service.getId();
        this.apiUrl = apiProvider.getApiUrl();
        if(client!=null){
            FcClient fcClient1 = (FcClient) client;
            fcClient1.setUrlHead(this.apiUrl);
        }
        return apiProvider;
    }

    private byte[] checkSessionKey(byte[] symkey, Service.ServiceType type, BufferedReader br) {
        if(this.fcSession ==null)this.fcSession = new FcSession();
        if (this.fcSession.getKeyCipher()== null) {
            this.sessionKey=freshSessionKey(symkey, type, RequestBody.SignInMode.NORMAL, br);
        } else {
            this.sessionKey =decryptSessionKey(fcSession.getKeyCipher(),symkey);
        }
        if (this.sessionKey==null || this.sessionKey.length==0) {
            this.sessionKey=freshSessionKey(symkey, type, RequestBody.SignInMode.NORMAL, br);
            if(this.sessionKey==null){
                log.debug("Failed to get sessionKey for "+type+" service.");
                System.out.println("Failed to get sessionKey for API account"+this.getId()+" of API provider "+service.getId()+".");
                return null;
            }
        }
        //test the client
        FcClient fcClient1 = (FcClient) client;

        fcClient1.setSessionKey(sessionKey);
        boolean allowFreeRequest = (boolean) fcClient1.ping(VERSION_1, RequestMethod.GET,AuthType.FREE, type);
        if(Settings.freeApiListMap!=null) ((FcClient) client).setAllowFreeRequest(allowFreeRequest);
        Object rest = fcClient1.ping(VERSION_1, RequestMethod.POST,AuthType.FC_SIGN_BODY, null);
        if(rest!=null){
            System.out.println((Long)rest+" KB/requests are available on "+ apiUrl);
            return sessionKey;
        }
        else return null;
    }

    public byte[] freshSessionKey(byte[] symkey, Service.ServiceType type, RequestBody.SignInMode mode, BufferedReader br) {
        System.out.println("Fresh the sessionKey of the "+type+" service...");

        FcSession fcSession;
        switch (type){
            case APIP -> {
                ApipClient apipClient = (ApipClient) client;
                fcSession = apipClient.signInEcc(this, mode,symkey, br);
            }
            case DISK -> {
                DiskClient diskClient = (DiskClient) client;
                fcSession = diskClient.signInEcc(this, mode,symkey, br);
            }
//            case TALK -> {
//                ClientTalk clientTalk = (ClientTalk) client;
//                apipSession = clientTalk.signInEcc(this, mode,symkey);
//            }
            default -> {
                byte[] prikey = decryptUserPrikey(userPrikeyCipher,symkey);
                if(prikey==null)return null;
                FcClient fcClient1 = (FcClient)client;
                fcSession = fcClient1.signInEcc(this, mode,symkey, null);
                BytesUtils.clearByteArray(prikey);
            }
        }

        if (fcSession == null) return null;
        this.sessionKey = Hex.fromHex(fcSession.getKey());

        fcSession.setKey(null);
        return sessionKey;
    }

    public void inputApiUrl(BufferedReader br) {
        System.out.println("Input the urlHead of the APIP service. Enter to set as 'https://cid.cash/APIP':");
        String input = core.fch.Inputer.inputString(br);
        if (input.endsWith("/")) input = input.substring(0, input.length() - 1);
        if ("".equals(input)) {
            this.apiUrl = "https://cid.cash/APIP";
        } else this.apiUrl = input;
    }

    public void inputVia(BufferedReader br) {
        String input;
        //String ask = "Input the FID by whom you knew this service. Default: 'FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7'";
        input = "";//fch.Inputer.inputString(br,ask);
        while(true) {
            if ("".equals(input)) {
                this.via = "FJYN3D7x4yiLF692WUAe7Vfo2nQpYDNrC7";
                break;
            } else {
                if (KeyTools.isGoodFid(input)) {
                    this.via = input;
                    break;
                }else System.out.println("It's not a valid FID. Try again.");
            }
        }
    }


    public byte[] decryptUserPrikey(String cipher, byte[] symkey) {
        if(cipher==null||"".equals(cipher)||symkey==null)return null;

        log.debug("Decrypt APIP buyer private key...");
        CryptoDataByte cryptoDataByte = new Decryptor().decryptJsonBySymkey(cipher, symkey);

        if (cryptoDataByte.getCode() != 0) {
            System.out.println("Error: " + cryptoDataByte.getMessage());
            return null;
        }
        return cryptoDataByte.getData();
    }

    public static byte[] decryptSessionKey(String sessionKeyCipher, byte[] symkey) {
        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonBySymkey(sessionKeyCipher,symkey);
        if(cryptoDataByte.getCode()==null || cryptoDataByte.getCode()!=0)return null;
        return cryptoDataByte.getData();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPasswordCipher() {
        return passwordCipher;
    }

    public void setPasswordCipher(String passwordCipher) {
        this.passwordCipher = passwordCipher;
    }

    public String getUserPrikeyCipher() {
        return userPrikeyCipher;
    }

    public void setUserPrikeyCipher(String userPrikeyCipher) {
        this.userPrikeyCipher = userPrikeyCipher;
    }

    public long getBestHeight() {
        return bestHeight;
    }

    public void setBestHeight(long bestHeight) {
        this.bestHeight = bestHeight;
    }

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getVia() {
        return via;
    }

    public void setVia(String via) {
        this.via = via;
    }

    public static long getMinRequestTimes() {
        return minRequestTimes;
    }

    public static void setMinRequestTimes(long minRequestTimes) {
        ApiAccount.minRequestTimes = minRequestTimes;
    }

    public static long getOrderRequestTimes() {
        return orderRequestTimes;
    }

    public static void setOrderRequestTimes(long orderRequestTimes) {
        ApiAccount.orderRequestTimes = orderRequestTimes;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public Params getApipParams() {
        return serviceParams;
    }

    public void setApipParams(ApipParams serviceParams) {
        this.serviceParams = serviceParams;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public byte[] getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(byte[] sessionKey) {
        this.sessionKey = sessionKey;
    }

    public byte[] getPassword() {
        return password;
    }

    public void setPassword(byte[] password) {
        this.password = password;
    }

    public String getBestBlockId() {
        return bestBlockId;
    }

    public void setBestBlockId(String bestBlockId) {
        this.bestBlockId = bestBlockId;
    }

    public Object getClient() {
        return client;
    }

    public void setClient(Object client) {
        this.client = client;
    }

    public Params getServiceParams() {
        return serviceParams;
    }

    public void setServiceParams(Params serviceParams) {
        this.serviceParams = serviceParams;
    }

    public ApipClient getApipClient() {
        return apipClient;
    }

    public void setApipClient(ApipClient apipClient) {
        this.apipClient = apipClient;
    }

    public FcSession getSession() {
        return fcSession;
    }

    public void setSession(FcSession fcSession) {
        this.fcSession = fcSession;
    }

    public String getUserPubkey() {
        return userPubkey;
    }

    public void setUserPubkey(String userPubkey) {
        this.userPubkey = userPubkey;
    }

    public Map<String, Double> getPayments() {
        return payments;
    }

    public void setPayments(Map<String, Double> payments) {
        this.payments = payments;
    }

    public Double getMinPayment() {
        return minPayment;
    }

    public void setMinPayment(Double minPayment) {
        this.minPayment = minPayment;
    }
}
