package startApipClient;


import data.apipData.*;
import data.fcData.*;
import data.fcData.Module;
import data.fchData.*;
import config.Starter;
import ui.Inputer;
import ui.Menu;
import ui.Shower;
import clients.ApipClient;
import config.ApiAccount;
import data.feipData.*;
import data.feipData.ServiceType;
import config.Configure;
import core.crypto.EncryptType;
import core.crypto.KeyTools;

import utils.BytesUtils;
import utils.Hex;
import utils.JsonUtils;
import utils.http.AuthType;
import utils.http.RequestMethod;
import org.bouncycastle.util.encoders.Base64;
import config.Settings;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ui.Inputer.inputString;
import static server.ApipApi.*;
import static core.fch.Inputer.inputGoodFid;

public class StartApipClient {
    public static final int DEFAULT_SIZE = 20;
    public static Configure configure;
    public static Settings settings;
    public static ApiAccount apipAccount;
    public static ApipClient apipClient;
    public static BufferedReader br ;
    public static String clientName= ServiceType.APIP.name();


    public static Map<String,Object> settingMap = new HashMap<>();

    public static void main(String[] args) {
        Menu.welcome(clientName);

        br = new BufferedReader(new InputStreamReader(System.in));
        List<Module> modules = new ArrayList<>();
        modules.add(new Module(Service.class.getSimpleName(), ServiceType.APIP.name()));

        settings = Starter.startClient(clientName, settingMap, br, modules, null);
        if(settings==null)return;
        byte[] symKey = settings.getSymkey();
        apipClient = (ApipClient) settings.getClient(ServiceType.APIP);
        configure = settings.getConfig();
        apipAccount = settings.getApiAccount(ServiceType.APIP);

        Menu menu = new Menu("Apip Client", () -> {
            BytesUtils.clearByteArray(settings.getSymkey());
            if(apipClient != null && apipClient.getSessionKey() != null) {
                BytesUtils.clearByteArray(apipClient.getSessionKey());
            }
        });
        menu.add("Example", () -> showExample());
        menu.add("BasicAPIs", () -> basicApi());
        menu.add("OpenAPI", () -> openAPI(settings.getSymkey()));
        menu.add("Blockchain", () -> blockchain());
        menu.add("Identity", () -> identity());
        menu.add("Organize", () -> organize());
        menu.add("Construct", () -> construct());
        menu.add("Personal", () -> personal());
        menu.add("Publish", () -> publish());
        menu.add("Finance", () -> finance());
        menu.add("Wallet", () -> wallet());
        menu.add("Crypto", () -> crypto());
        menu.add("Endpoint", () -> endpoint());
        menu.add("Settings", () -> settings.setting(br, null));

        System.out.println(" << APIP Client>>");
        menu.showAndSelect(br);
    }

    public static void showExample() {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields("owner", "issuer").addNewValues("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        fcdsl.getQuery().addNewRange().addNewFields("cd").addGt("1").addLt("100");
        fcdsl.addNewFilter().addNewPart().addNewFields("issuer").addNewValue("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
        fcdsl.addNewExcept().addNewEquals().addNewFields("cd").addNewValues("1", "2");
        fcdsl.addSort("cd", "desc").addSize(2).addAfter("56");
        if (fcdsl.isBadFcdsl()) return;
        System.out.println("Java code:");
        Shower.printUnderline(20);
        String code = """
                public static void showCashList(ApipClient apipClient) {
                \tFcdsl fcdsl = new Fcdsl();
                \tfcdsl.addNewQuery().addNewTerms().addNewFields("owner", "issuer").addNewValues("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
                \tfcdsl.getQuery().addNewRange().addNewFields("cd").addGt("1").addLt("100");
                \tfcdsl.addNewFilter().addNewPart().addNewFields("issuer").addNewValue("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
                \tfcdsl.addNewExcept().addNewEquals().addNewFields("cd").addNewValues("1", "2");
                \tString[] last = new String(){"1723508228","588c6e3b8d4f1b55673f54d89a90ef9bf858bb0adf6ab84b2d2b4433de8caa09"};
                \tfcdsl.addSort("cd", "desc")
                \t\t.addSize(2)
                \t\t.addAfter(last);
                \tif (fcdsl.isBadFcdsl()) return;
                \tSystem.out.println("Requesting chainInfo...");
                \tList<Cash> cashList = apipClient.cashSearch(fcdsl, HttpRequestMethod.POST, AuthType.FC_SIGN_BODY);
                \tGson gson = new Gson();
                \tString cashListJson = gson.toJson(cashList);
                \tString responseBodyJson = gson.toJson(apipClient.getFcClientEvent().getResponseBody());
                \tSystem.out.println("Cash list:"+cashListJson);
                \tSystem.out.println("ResponseBody:"+responseBodyJson);
                }""";
        System.out.println(code);
        Shower.printUnderline(20);
        Menu.anyKeyToContinue(br);
    }

    public static void basicApi() {
        Menu menu = new Menu("Free GET methods");
        // Map initApiList items to their corresponding methods
        // initApiList contains: PING, GET_SERVICE, SIGN_IN, CHAIN_INFO, BROADCAST_TX, BEST_BLOCK, CASH_VALID, TX_BY_FID, TX_BY_IDS
        menu.add("ping", () -> ping());
        menu.add("getService", () -> getService());
//        menu.add("signIn", () -> signInPost(SignInMode.NORMAL));
//        menu.add("signInOffLine", () -> signInOffLine(SignInMode.NORMAL));
        menu.add("chainInfo", () -> chainInfo());
        menu.add("broadcastTx", () -> broadcastTx(RequestMethod.GET, AuthType.FREE));
        menu.add("bestBlock", () -> bestBlock());
        menu.add("cashValid", () -> cashValid());
        menu.add("txByFid", () -> txByFid());
        menu.add("txByIds", () -> txByIds());

        menu.showAndSelect(br);
    }

    private static void ping() {
//        boolean data = (boolean) apipClient.ping(VER_1, RequestMethod.GET,AuthType.FREE, ServiceType.APIP);
        boolean data = (boolean) apipClient.ping(VER_1, RequestMethod.POST,AuthType.ENCRYPTED, ServiceType.APIP);
        System.out.println(data);
    }

    public static void getService() {
        System.out.println("Getting the default service information...");
        ReplyBody replier = ApipClient.getService(apipClient.getUrlHead(), server.ApipApi.VER_1);
        if(replier!=null) JsonUtils.printJson(replier);
        else System.out.println("Failed to get service.");
        Menu.anyKeyToContinue(br);
    }
//
    public static void openAPI(byte[] symKey) {
        System.out.println("OpenAPI...");
        Menu menu = new Menu("OpenAPI");
        menu.add("getService", () -> getService());
//        menu.add("SignIn", () -> signInPost(SignInMode.NORMAL));
//        menu.add("SignIn without encrypting", () -> signInPost(SignInMode.NORMAL));
        menu.add("TotalsGet", () -> totalsGet());
        menu.add("TotalsPost", () -> totalsPost());
        menu.add("generalPost", () -> generalPost());
        menu.add("entityByIds", () -> entityByIds());
        menu.add("entitySearch", () -> entitySearch(DEFAULT_SIZE, "id:asc"));

        System.out.println(" << Maker manager>>");
        menu.showAndSelect(br);
    }

    public static void generalPost( ) {
        Fcdsl fcdsl = new Fcdsl();
        System.out.println("Input the index name. Enter to exit:");
        String input = Inputer.inputString(br);
        if ("".equals(input)) return;
        fcdsl.setEntity(input);

        fcdsl.promoteInput(br);

        if (fcdsl.isBadFcdsl()) {
            System.out.println("Fcdsl wrong:");
            System.out.println(JsonUtils.toNiceJson(fcdsl));
            return;
        }
        System.out.println(JsonUtils.toNiceJson(fcdsl));
        Menu.anyKeyToContinue(br);
        System.out.println("Requesting ...");
        ReplyBody replier = apipClient.general(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        JsonUtils.printJson(replier);
        Menu.anyKeyToContinue(br);
    }

    public static void entityByIds() {
        System.out.println("Input the entity name (e.g., cash, block, freer). Enter to exit:");
        String entityName = Inputer.inputString(br);
        if ("".equals(entityName)) return;

        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input IDs, separated by ',':", ",");
        if (ids == null || ids.length == 0) return;

        System.out.println("Requesting entityByIds for " + entityName + "...");
        Map<String, Object> result = apipClient.entityByIds(entityName, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if (result == null) {
            System.out.println("No results found.");
            return;
        }
        System.out.println("Got " + result.size() + " items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void entitySearch(int defaultSize, String defaultSort) {
        System.out.println("Input the entity name (e.g., cash, block, freer). Enter to exit:");
        String entityName = Inputer.inputString(br);
        if ("".equals(entityName)) return;

        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;

        System.out.println("Requesting entitySearch for " + entityName + "...");
        List<Object> result = apipClient.entitySearch(entityName, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if (result == null) {
            System.out.println("No results found.");
            return;
        }
        System.out.println("Got " + result.size() + " items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void totalsGet() {
        System.out.println("Get request for totals...");
        Map<String, String> result = apipClient.totals(RequestMethod.GET, AuthType.FREE);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void totalsPost( ) {
        System.out.println("Post request for totals...");
        Map<String, String> result  = apipClient.totals(RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }


//    public static byte[] signInPost(SignInMode mode) {
//        System.out.println("Post request for signIn...");
//        FcSession fcSession = apipClient.signIn(VER_1,mode);
//        JsonUtils.printJson(fcSession);
//        Menu.anyKeyToContinue(br);
//        if(fcSession==null)return null;
//        return Hex.fromHex(fcSession.getKey());
//    }

//    public static byte[] signInOffLine(SignInMode mode) {
//        System.out.println("Post request for signIn...");
//        FcSession fcSession = apipClient.signInOffLine(mode,br);
//        JsonUtils.printJson(fcSession);
//        Menu.anyKeyToContinue(br);
//        if(fcSession==null)return null;
//        return Hex.fromHex(fcSession.getKey());
//    }

    public static void blockchain( ) {
        System.out.println("Blockchain...");
        Menu menu = new Menu("Blockchain");
        // blockchainAPIs: BLOCK_SEARCH, BEST_BLOCK, BLOCK_BY_IDS, BLOCK_BY_HEIGHTS,
        // CASH_SEARCH, CASH_BY_IDS, OP_RETURN_SEARCH, OP_RETURN_BY_IDS,
        // MULTISIG_SEARCH, MULTISIG_BY_IDS, TX_SEARCH, TX_BY_IDS, TX_BY_FID,
        // CHAIN_INFO, BLOCK_TIME_HISTORY, DIFFICULTY_HISTORY, HASH_RATE_HISTORY
        menu.add("blockSearch", () -> blockSearch(DEFAULT_SIZE, "height:desc->id:asc"));
        menu.add("bestBlock", () -> bestBlock());
        menu.add("blockByIds", () -> blockByIds());
        menu.add("blockByHeights", () -> blockByHeights());
        menu.add("cashSearch", () -> cashSearch(DEFAULT_SIZE, "valid:desc->birthHeight:desc->id:asc"));
        menu.add("cashByIds", () -> cashByIds());
        menu.add("opReturnSearch", () -> opReturnSearch(DEFAULT_SIZE, "height:desc->txIndex:desc->id:asc"));
        menu.add("opReturnByIds", () -> opReturnByIds());
        menu.add("multisigSearch", () -> multisigSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc"));
        menu.add("multisigByIds", () -> multisigByIds());
        menu.add("p2shSearch", () -> p2shSearch(DEFAULT_SIZE, "id:asc"));
        menu.add("p2shByIds", () -> p2shByIds());
        menu.add("txSearch", () -> txSearch(DEFAULT_SIZE, "height:desc->id:asc"));
        menu.add("txByIds", () -> txByIds());
        menu.add("txByFid", () -> txByFid());
        menu.add("chainInfo", () -> chainInfo());
        menu.add("blockTimeHistory", () -> blockTimeHistory());
        menu.add("difficultyHistory", () -> difficultyHistory());
        menu.add("hashRateHistory", () -> hashRateHistory());

        menu.showAndSelect(br);
    }
    public static void blockByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input blockIds,separated by ',':", ",");
        System.out.println("Requesting blockByIds...");
        Map<String, Block> result = apipClient.entityByIds("block", Block.class, RequestMethod.POST,AuthType.ENCRYPTED,ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void blockSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting blockSearch...");
        List<Block> result = apipClient.entitySearch("block", Block.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void blockByHeights( ) {
        String[] heights = Inputer.inputStringArrayWithSeparator(br, "Input block heights,separated by ',':", ",");
        System.out.println("Requesting blockByHeights...");
        Map<String, Block> result = apipClient.blockByHeights(RequestMethod.POST,AuthType.ENCRYPTED,heights);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void bestBlock() {
        System.out.println("Requesting bestBlock...");
        Block result = apipClient.bestBlock(RequestMethod.POST,AuthType.ENCRYPTED);
        if(result==null)return;
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void cashByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input cashIds,separated by ',':", ",");
        System.out.println("Requesting cashByIds...");
        Map<String, Cash> result = apipClient.entityByIds("cash", Cash.class, RequestMethod.POST,AuthType.ENCRYPTED,ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void cashValid(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting cashValid...");
        List<Cash> result = apipClient.cashValid(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void cashSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting cashSearch...");
        List<Cash> result = apipClient.entitySearch("cash", Cash.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }


    public static void opReturnByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input opReturnIds,separated by ',':", ",");
        System.out.println("Requesting opReturnByIds...");
        Map<String, OpReturn> result = apipClient.entityByIds("opReturn", OpReturn.class, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void opReturnSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting opReturnSearch...");
        List<OpReturn> result = apipClient.entitySearch("opReturn", OpReturn.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void multisigByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input multisigIds,separated by ',':", ",");
        System.out.println("Requesting multisigByIds...");
        Map<String, Multisig> result = apipClient.entityByIds("multisig", Multisig.class, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void multisigSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting multisigSearch...");
        List<Multisig> result = apipClient.entitySearch("multisig", Multisig.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void p2shByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input p2shIds,separated by ',':", ",");
        System.out.println("Requesting p2shByIds...");
        Map<String, P2SH> result = apipClient.entityByIds("p2sh", P2SH.class, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void p2shSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting p2shSearch...");
        List<P2SH> result = apipClient.entitySearch("p2sh", P2SH.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void txByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input txIds,separated by ',':", ",");
        System.out.println("Requesting txByIds...");
        Map<String, Tx> result = apipClient.entityByIds("tx", Tx.class, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void txSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting txSearch...");
        List<Tx> result = apipClient.entitySearch("tx", Tx.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void txByFid( ) {
        String fid = Inputer.inputString(br, "Input FID");
        int size = core.fch.Inputer.inputInt(br,"Input the size:",0);
        String[] last=null;
        String lastStr = Inputer.inputString(br,"Input the last values spited with ','");
        if(!lastStr.isEmpty()) last = lastStr.split(",");
        System.out.println("Requesting txByFid...");
        List<FidTxMask> result = apipClient.txByFid(fid, size, last, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void chainInfo() {
        Long height = Inputer.inputLongWithNull(br,"Input the height you want. Enter to query by the best height:");
        System.out.println("Requesting chainInfo...");
        FchChainInfo result = apipClient.chainInfo(height, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.getHeight()+".");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void blockTimeHistory() {
        Long startTime = Inputer.inputLongWithNull(br,"Input the start timestamp:");
        Long endTime = Inputer.inputLongWithNull(br,"Input the end timestamp:");
        Integer count = Inputer.inputInt(br,"Input the data count:",0);
        System.out.println("Requesting blockTimeHistory...");
        Map<Long, Long> result = apipClient.blockTimeHistory(startTime,endTime,count, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void difficultyHistory() {
        Long startTime = Inputer.inputLongWithNull(br,"Input the start timestamp:");
        Long endTime = Inputer.inputLongWithNull(br,"Input the end timestamp:");
        Integer count = Inputer.inputInt(br,"Input the data count:",0);
        System.out.println("Requesting difficultyHistory...");
        Map<Long, String> result = apipClient.difficultyHistory(startTime,endTime,count, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void hashRateHistory() {
        Long startTime = Inputer.inputLongWithNull(br,"Input the start timestamp:");
        Long endTime = Inputer.inputLongWithNull(br,"Input the end timestamp:");
        Integer count = Inputer.inputInt(br,"Input the data count:",0);
        System.out.println("Requesting hashRateHistory...");
        Map<Long, String> result = apipClient.hashRateHistory(startTime,endTime,count, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static Fcdsl inputFcdsl(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = new Fcdsl();

        fcdsl.promoteSearch(defaultSize, defaultSort,br);

        if (fcdsl.isBadFcdsl()) {
            System.out.println("Fcdsl wrong:");
            System.out.println(JsonUtils.toNiceJson(fcdsl));
            return null;
        }
        System.out.println("fcdsl:\n" + JsonUtils.toNiceJson(fcdsl));

        Menu.anyKeyToContinue(br);
        return fcdsl;
    }

    public static void identity( ) {
        System.out.println("Identity...");
        Menu menu = new Menu("Identity");
        menu.add("cidSearch", () -> cidSearch(DEFAULT_SIZE, "nameTime:desc->id:asc"));
        menu.add("cidByIds", () -> cidByIds());
        menu.add("cidInfoByIds", () -> cidInfoByIds());
        menu.add("cidHistory", () -> cidHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("fidCidSeek", () -> fidCidSeek());
        menu.add("getFidCid", () -> getFidCid());
        menu.add("nobodySearch", () -> nobodySearch(DEFAULT_SIZE, "deathHeight:desc->deathTxIndex:desc"));
        menu.add("nobodyByIds", () -> nobodyByIds());
        menu.add("checkNobodies", () -> checkNobodies());
        menu.add("homepageHistory", () -> homeHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("noticeFeeHistory", () -> noticeFeeHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("reputationHistory", () -> reputationHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("nidSearch", () -> nidSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc"));
        menu.add("didByNids", () -> didByNids());
        menu.add("getAvatar", () -> getAvatar());
        menu.add("avatars", () -> avatars());
        menu.add("cidAvatarByIds", () -> cidAvatarByIds());

        menu.showAndSelect(br);
    }

    public static void cidInfoByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input FIDs,separated by ',':", ",");
        System.out.println("Requesting cidByIds...");
        Map<String, Freer> result = apipClient.entityByIds("freer", Freer.class, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void cidByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input FIDs,separated by ',':", ",");
        System.out.println("Requesting cidByIds...");
        Map<String, String> result = apipClient.cidByIds(RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void cidAvatarByIds() {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input FIDs,separated by ',':", ",");
        System.out.println("Requesting cidAvatarByIds...");
        Map<String, String> result = apipClient.cidAvatarByIds(RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if (result != null && !result.isEmpty()) {
            System.out.println("Found " + result.size() + " avatars:");
            for (Map.Entry<String, String> entry : result.entrySet()) {
                System.out.println("CID: " + entry.getKey());
                System.out.println("Avatar (base64): " + entry.getValue().substring(0, Math.min(50, entry.getValue().length())) + "...");
            }
        } else {
            System.out.println("No avatars found.");
        }
        Menu.anyKeyToContinue(br);
    }

    public static void cidSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting cidSearch...");
        List<Freer> result = apipClient.entitySearch("freer", Freer.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void fidCidSeek() {
        String searchStr = inputString(br,"Input the whole or part of the FID or CID you are looking for");
        System.out.println("Requesting fidCidSeek...");
        Map<String, String[]> result = apipClient.fidCidSeek(searchStr);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void getFidCid() {
        System.out.println("Input FID or CID:");
        String id = Inputer.inputString(br);
        System.out.println("Requesting getFidCid...");
        Freer result = apipClient.getFidCid(id);
        if(result==null)return;
        System.out.println("Got "+result.getCid()+".");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void nobodyByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input FIDs,separated by ',':", ",");
        System.out.println("Requesting nobodyByIds...");
        Map<String, Nobody> result = apipClient.entityByIds("nobody", Nobody.class, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void checkNobodies( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input FIDs,separated by ',':", ",");
        System.out.println("Requesting checkNobodies...");
        Map<String, Boolean> result = apipClient.checkNobodies(RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void nobodySearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting nobodySearch...");
        List<Nobody> result = apipClient.entitySearch("nobody", Nobody.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void cidHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting cidHistory...");
        List<FreerHist> result = apipClient.freerHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void homeHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting homeHistory...");
        List<FreerHist> result = apipClient.homeHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void noticeFeeHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting noticeFeeHistory...");
        List<FreerHist> result = apipClient.noticeFeeHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void reputationHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting reputationHistory...");
        List<FreerHist> result = apipClient.reputationHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void avatars( ) {
        String[] fids = core.fch.Inputer.inputFidArray(br, "Input FIDs:", 0);

        System.out.println("Requesting avatars...");
        Map<String, String> result = apipClient.avatars(fids, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        for(String key : result.keySet()){
            try (FileOutputStream fos = new FileOutputStream(key+".png")) {
                byte[] bytes = Base64.decode(result.get(key));
                fos.write(bytes);
                System.out.println("PNG file saved as '"+key+".png'.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Menu.anyKeyToContinue(br);
    }

    public static void getAvatar() {
        String fid = inputGoodFid(br, "Input FID:");
        System.out.println("Requesting getAvatar...");
        byte[] result = apipClient.getAvatar(fid);
        try (FileOutputStream fos = new FileOutputStream(fid+".png")) {
            fos.write(result);
            System.out.println("PNG file saved as '"+fid+".png'.");
        } catch (IOException e) {
            e.printStackTrace();
        }
        Menu.anyKeyToContinue(br);
    }

    public static void organize( ) {
        System.out.println("Organize...");
        Menu menu = new Menu("Organize");
        menu.add("squareSearch", () -> squareSearch(DEFAULT_SIZE, "tCdd:desc->id:asc"));
        menu.add("squareByIds", () -> squareByIds());
        menu.add("squareMembers", () -> squareMembers());
        menu.add("squareOpHistory", () -> squareOpHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("mySquares", () -> mySquares(DEFAULT_SIZE));
        menu.add("teamSearch", () -> teamSearch(DEFAULT_SIZE, "active:desc->tRate:desc->id:asc"));
        menu.add("teamByIds", () -> teamByIds());
        menu.add("teamMembers", () -> teamMembers());
        menu.add("teamExMembers", () -> teamExMembers());
        menu.add("teamOpHistory", () -> teamOpHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("teamRateHistory", () -> teamRateHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("teamOtherPersons", () -> teamOtherPersons());
        menu.add("myTeams", () -> myTeams(DEFAULT_SIZE));

        menu.showAndSelect(br);
    }

    public static void squareByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input GIDs,separated by ',':", ",");
        System.out.println("Requesting ...");
        Map<String, Square> result = apipClient.entityByIds("square", Square.class, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void squareSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<Square> result = apipClient.entitySearch("square", Square.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void squareMembers( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input GIDs,separated by ',':", ",");
        System.out.println("Requesting ...");
        apipClient.squareMembers(RequestMethod.POST, AuthType.ENCRYPTED, ids);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void squareOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        apipClient.squareOpHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void mySquares(Integer size) {
        System.out.println("Input the FID. Enter to exit:");
        String id = Inputer.inputString(br);
        if ("".equals(id)) return;
        List<String> last = Inputer.inputStringList(br, "Input the last:", 0);
        System.out.println("Requesting ...");
        apipClient.mySquares(id, null, size, last, RequestMethod.POST, AuthType.ENCRYPTED);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void teamByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input TIDs,separated by ',':", ",");
        System.out.println("Requesting ...");
        Map<String, Team> result = apipClient.entityByIds("team", Team.class, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void teamSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<Team> result = apipClient.entitySearch("team", Team.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void teamMembers( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input TIDs,separated by ',':", ",");
        System.out.println("Requesting ...");
        apipClient.teamMembers(RequestMethod.POST, AuthType.ENCRYPTED, ids);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void teamExMembers( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input TIDs,separated by ',':", ",");
        System.out.println("Requesting ...");
        apipClient.teamExMembers(RequestMethod.POST, AuthType.ENCRYPTED,ids);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void teamRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        apipClient.teamRateHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void teamOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        apipClient.teamOpHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void teamOtherPersons( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input TIDs,separated by ',':", ",");
        System.out.println("Requesting ...");
        apipClient.teamOtherPersons(RequestMethod.POST, AuthType.ENCRYPTED,ids);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void myTeams(Integer size) {
        System.out.println("Input the FID. Enter to exit:");
        String id = Inputer.inputString(br);
        if ("".equals(id)) return;  
        Long sinceHeight = Inputer.inputLong(br, "Input the sinceHeight:");
        List<String> last = Inputer.inputStringList(br, "Input the last:", 0);
        System.out.println("Requesting ...");
        apipClient.myTeams(id, sinceHeight,size, last, RequestMethod.POST, AuthType.ENCRYPTED);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void construct( ) {
        System.out.println("Construct...");
        Menu menu = new Menu("Construct");
        menu.add("protocolSearch", () -> protocolSearch(DEFAULT_SIZE, "active:desc->tRate:desc->id:asc"));
        menu.add("protocolByIds", () -> protocolByIds());
        menu.add("protocolOpHistory", () -> protocolOpHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("protocolRateHistory", () -> protocolRateHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("codeSearch", () -> codeSearch(DEFAULT_SIZE, "active:desc->tRate:desc->id:asc"));
        menu.add("codeByIds", () -> codeByIds());
        menu.add("codeOpHistory", () -> codeOpHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("codeRateHistory", () -> codeRateHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("serviceSearch", () -> serviceSearch(DEFAULT_SIZE, "active:desc->tRate:desc->id:asc"));
        menu.add("serviceByIds", () -> serviceByIds());
        menu.add("serviceOpHistory", () -> serviceOpHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("serviceRateHistory", () -> serviceRateHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("appSearch", () -> appSearch(DEFAULT_SIZE, "active:desc->tRate:desc->id:asc"));
        menu.add("appByIds", () -> appByIds());
        menu.add("appOpHistory", () -> appOpHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("appRateHistory", () -> appRateHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("remarkSearch", () -> remarkSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc"));
        menu.add("remarkByIds", () -> remarkByIds());
        menu.add("remarkOpHistory", () -> remarkOpHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("remarkRateHistory", () -> remarkRateHistory(DEFAULT_SIZE, "height:desc->index:desc"));

        menu.showAndSelect(br);
    }

    public static void protocolByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input PIDs,separated by ',':", ",");
        System.out.println("Requesting protocolByIds...");
        Map<String, Protocol> result = apipClient.entityByIds("protocol", Protocol.class, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void protocolSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting protocolSearch...");
        List<Protocol> result = apipClient.entitySearch("protocol", Protocol.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void protocolOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting protocolOpHistory...");
        List<ProtocolHistory> result = apipClient.protocolOpHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void protocolRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<ProtocolHistory> result = apipClient.protocolRateHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void codeByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input Code_IDs,separated by ',':", ",");
        System.out.println("Requesting codeByIds...");
        Map<String, Code> result = apipClient.entityByIds("code", Code.class, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void codeSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting codeSearch...");
        List<Code> result = apipClient.entitySearch("code", Code.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void codeRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting odeRateHistory...");
        List<CodeHistory> result = apipClient.codeRateHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void codeOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting codeOpHistory...");
        List<CodeHistory> result = apipClient.codeOpHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void serviceByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input SIDs,separated by ',':", ",");
        System.out.println("Requesting serviceByIds...");
        Map<String, Service> result = apipClient.entityByIds("service", Service.class, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void serviceSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting serviceSearch...");
        List<Service> result = apipClient.entitySearch("service", Service.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void serviceRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<ServiceHistory> result = apipClient.serviceRateHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void serviceOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<ServiceHistory> result = apipClient.serviceOpHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void appByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input AIDs,separated by ',':", ",");
        System.out.println("Requesting appByIds...");
        Map<String, App> result = apipClient.entityByIds("app", App.class, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void appSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting appSearch...");
        List<App> result = apipClient.entitySearch("app", App.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void appRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<AppHistory> result = apipClient.appRateHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void appOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<AppHistory> result = apipClient.appOpHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }


    public static void personal( ) {
        System.out.println("Personal...");
        Menu menu = new Menu("Personal");
        menu.add("boxSearch", () -> boxSearch(DEFAULT_SIZE, "lastHeight:desc->id:asc"));
        menu.add("boxByIds", () -> boxByIds());
        menu.add("boxHistory", () -> boxHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("contactSearch", () -> contactSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc"));
        menu.add("contactByIds", () -> contactByIds());
        menu.add("contactsDeleted", () -> contactsDeleted(DEFAULT_SIZE, "lastHeight:desc->id:asc"));
        menu.add("secretSearch", () -> secretSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc"));
        menu.add("secretByIds", () -> secretByIds());
        menu.add("secretsDeleted", () -> secretsDeleted(DEFAULT_SIZE, "lastHeight:desc->id:asc"));
        menu.add("mailSearch", () -> mailSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc"));
        menu.add("mailByIds", () -> mailByIds());
        menu.add("mailsDeleted", () -> mailsDeleted(DEFAULT_SIZE, "lastHeight:desc->id:asc"));
        menu.add("mailThread", () -> mailThread(DEFAULT_SIZE, "birthHeight:desc->id:asc"));

        menu.showAndSelect(br);
    }

    public static void boxByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input BIDs,separated by ',':", ",");
        System.out.println("Requesting boxByIds...");
        Map<String, Box> result = apipClient.entityByIds("box", Box.class, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void boxSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting boxSearch...");
        List<Box> result = apipClient.entitySearch("box", Box.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void boxHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting boxHistory...");
        List<BoxHistory> result = apipClient.boxHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }


    public static void contactByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input Contact_Ids,separated by ',':", ",");
        System.out.println("Requesting contactByIds...");
        Map<String, Contact> result = apipClient.entityByIds("contact", Contact.class, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void contactSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting contactSearch...");
        List<Contact> result = apipClient.entitySearch("contact", Contact.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void contactsDeleted(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting contactsDeleted...");
        List<Contact> result = apipClient.contactDeleted(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }


    public static void secretByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input Secret_Ids,separated by ',':", ",");
        System.out.println("Requesting secretByIds...");
        Map<String, Secret> result = apipClient.entityByIds("secret", Secret.class, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void secretSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting secretSearch...");
        List<Secret> result = apipClient.entitySearch("secret", Secret.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void secretsDeleted(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting secretsDeleted...");
        List<Secret> result = apipClient.secretDeleted(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }


    public static void mailByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input Mail_Ids,separated by ',':", ",");
        System.out.println("Requesting mailByIds...");
        Map<String, Mail> result = apipClient.entityByIds("mail", Mail.class, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void mailSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting mailSearch...");
        List<Mail> result = apipClient.entitySearch("mail", Mail.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void mailsDeleted(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting mailsDeleted...");
        List<Mail> result = apipClient.mailDeleted(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void mailThread(int defaultSize, String defaultSort) {
        String fidA = core.fch.Inputer.inputGoodFid(br,"Input the FID:");
        String fidB = core.fch.Inputer.inputGoodFid(br,"Input another FID:");
        if(fidA==null ||fidB==null)return;
        Long startTime = Inputer.inputDate(br,"yyyy-mm-dd","Input the start time. Enter to skip:");
        Long endTime = Inputer.inputDate(br,"yyyy-mm-dd","Input the end time. Enter to skip:");

        System.out.println("Requesting mailThread...");
        List<Mail> result = apipClient.mailThread(fidA,fidB,startTime,endTime, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }


    public static void proofByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input Proof_Ids,separated by ',':", ",");
        System.out.println("Requesting proofByIds...");
        Map<String, Proof> result = apipClient.entityByIds("proof", Proof.class, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void proofSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting proofSearch...");
        List<Proof> result = apipClient.entitySearch("proof", Proof.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void proofHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting proofHistory...");
        List<ProofHistory> result = apipClient.proofHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void statementByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input Statement_Ids,separated by ',':", ",");
        System.out.println("Requesting statementByIds...");
        Map<String, Statement> result = apipClient.entityByIds("statement", Statement.class, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void statementSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting statementSearch...");
        List<Statement> result = apipClient.entitySearch("statement", Statement.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void nidSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting nidSearch...");
        List<Nid> result = apipClient.entitySearch("nid", Nid.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void didByNids() {
        System.out.println("Input nids (JSON array of strings):");

        try {
            List<String>nids = Inputer.inputStringListWithSeparator(br, "Input nids,separated by ',':", ",");

            if (nids.isEmpty()) {
                System.out.println("Invalid nids format");
                return;
            }
            Map<String, String> nidDidMap = apipClient.oidByNids(nids, RequestMethod.POST, AuthType.FREE);
            if (nidDidMap == null || nidDidMap.isEmpty()) {
                System.out.println("No results found");
                return;
            }
            System.out.println("Results:");
            for (Map.Entry<String, String> entry : nidDidMap.entrySet()) {
                System.out.println(entry.getKey() + " -> " + entry.getValue());
            }
            Menu.anyKeyToContinue(br);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    public static void tokenByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input Token_Ids,separated by ',':", ",");
        System.out.println("Requesting tokenByIds...");
        Map<String, Token> result = apipClient.entityByIds("token", Token.class, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void tokenSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting tokenSearch...");
        List<Token> result = apipClient.entitySearch("token", Token.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void tokenHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting tokenHistory...");
        List<TokenHistory> result = apipClient.tokenHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void myTokens( ) {
        System.out.println("Input the FID. Enter to exit:");
        String id = Inputer.inputString(br);
        if ("".equals(id)) return;
        System.out.println("Requesting myTokens...");
        List<Token> result = apipClient.myTokens(id, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void tokenHoldersByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input Token_Ids,separated by ',':", ",");
        System.out.println("Requesting tokenHoldersByIds...");
        Map<String, TokenHolder> result = apipClient.tokenHoldersByIds(RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void tokenHolderSearch(int defaultSize, String defaultSort ) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting tokenHolderSearch...");
        List<TokenHolder> result = apipClient.entitySearch("tokenHolder", TokenHolder.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void publish( ) {
        System.out.println("Publish...");
        Menu menu = new Menu("Publish");
        menu.add("statementSearch", () -> statementSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc"));
        menu.add("statementByIds", () -> statementByIds());
        menu.add("remarkSearch", () -> remarkSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc"));
        menu.add("remarkByIds", () -> remarkByIds());
        menu.add("remarkOpHistory", () -> remarkOpHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("remarkRateHistory", () -> remarkRateHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("textSearch", () -> textSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc"));
        menu.add("textByIds", () -> textByIds());
        menu.add("textOpHistory", () -> textOpHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("textRateHistory", () -> textRateHistory(DEFAULT_SIZE, "height:desc->index:desc"));

        menu.showAndSelect(br);
    }


    public static void remarkByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input Remark_Ids,separated by ',':", ",");
        System.out.println("Requesting remarkByIds...");
        Map<String, Remark> result = apipClient.entityByIds("remark", Remark.class, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void remarkSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting remarkSearch...");
        List<Remark> result = apipClient.entitySearch("remark", Remark.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void remarkOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting remarkOpHistory...");
        List<RemarkHistory> result = apipClient.remarkOpHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void remarkRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting remarkRateHistory...");
        List<RemarkHistory> result = apipClient.remarkRateHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void textByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input Text_Ids,separated by ',':", ",");
        System.out.println("Requesting textByIds...");
        Map<String, Text> result = apipClient.entityByIds("text", Text.class, RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void textSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting textSearch...");
        List<Text> result = apipClient.entitySearch("text", Text.class, fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void textOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting textOpHistory...");
        List<TextHistory> result = apipClient.textOpHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void textRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting textRateHistory...");
        List<TextHistory> result = apipClient.textRateHistory(fcdsl, RequestMethod.POST, AuthType.ENCRYPTED);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void finance( ) {
        System.out.println("Finance...");
        Menu menu = new Menu("Finance");
        menu.add("proofSearch", () -> proofSearch(DEFAULT_SIZE, "lastHeight:desc->id:asc"));
        menu.add("proofByIds", () -> proofByIds());
        menu.add("proofHistory", () -> proofHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("tokenSearch", () -> tokenSearch(DEFAULT_SIZE, "lastHeight:desc->id:asc"));
        menu.add("tokenByIds", () -> tokenByIds());
        menu.add("tokenHistory", () -> tokenHistory(DEFAULT_SIZE, "height:desc->index:desc"));
        menu.add("tokenHolderSearch", () -> tokenHolderSearch(DEFAULT_SIZE, "lastHeight:desc->id:asc"));
        menu.add("tokenHoldersByIds", () -> tokenHoldersByIds());
        menu.add("myTokens", () -> myTokens());

        menu.showAndSelect(br);
    }

    public static void wallet( ) {
        System.out.println("Wallet...");
        Menu menu = new Menu("Wallet");
        menu.add("Broadcast Tx", () -> broadcastTx(RequestMethod.POST, AuthType.ENCRYPTED));
        menu.add("Decode Tx", () -> decodeTx());
        menu.add("Get valid cashes", () -> cashValid());
        menu.add("Unconfirmed", () -> unconfirmed());
        menu.add("Fee Rate", () -> feeRate());
        menu.add("Get offLine Tx", () -> offLineTx());
        menu.add("Balance by FIDs", () -> balanceByIds());

        menu.showAndSelect(br);
    }

    public static void balanceByIds( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input FIDs,separated by ',':", ",");
        System.out.println("Requesting fidByIds...");
        Map<String, Long> result = apipClient.balanceByIds(RequestMethod.POST, AuthType.ENCRYPTED, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void broadcastTx(RequestMethod requestMethod, AuthType authType) {
        String txHex;
        while (true) {
            System.out.println("Input the hex of the TX:");
            txHex = Inputer.inputString(br);
            if (Hex.isHexString(txHex)) break;
            System.out.println("It's not a hex. Try again.");
        }
        System.out.println("Requesting ...");
        apipClient.broadcastTx(txHex, requestMethod, authType);
        System.out.println(apipClient.getFcClientEvent().getRequestBody().toNiceJson());
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void decodeTx( ) {
        String txHex;
        while (true) {
            System.out.println("Input the hex of the raw TX:");
            txHex = Inputer.inputString(br);
            if (Hex.isHexString(txHex)) break;
            System.out.println("It's not a hex. Try again.");
        }
        System.out.println("Requesting ...");
        apipClient.decodeTx(txHex, RequestMethod.POST,  AuthType.ENCRYPTED);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void cashValid( ) {
        String fid;
        while (true) {
            System.out.println("Input the sender's FID:");
            fid = Inputer.inputString(br);
            if (KeyTools.isGoodFid(fid)) break;
            System.out.println("It's not a FID. Try again.");
        }
        Double amount = Inputer.inputDouble(br, "Input the amount:");
        if (amount == null) return;
        Long cd = Inputer.inputLong(br, "Input the required CD:");
        Integer outputSize = Inputer.inputInt(br, "Input the outputSize:",0);
        Integer msgSize = Inputer.inputInt(br, "Input the msgSize:",0);

        System.out.println("Requesting ...");
        apipClient.cashValid(fid, amount, cd, outputSize, msgSize, RequestMethod.POST, AuthType.ENCRYPTED);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void unconfirmed( ) {
        String[] ids = Inputer.inputStringArrayWithSeparator(br, "Input FIDs,separated by ',':", ",");
        System.out.println("Requesting ...");
        apipClient.unconfirmed(RequestMethod.POST,  AuthType.ENCRYPTED,ids);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void feeRate( ) {
        System.out.println("Requesting ...");
        apipClient.feeRate(RequestMethod.POST,  AuthType.ENCRYPTED);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void crypto( ) {
        System.out.println("CryptoTools");
        Menu menu = new Menu("CryptoTools");
        menu.add("addresses", () -> addresses());
        menu.add("encrypt", () -> encrypt());
        menu.add("verify", () -> verify());
        menu.add("sha256", () -> sha256());
        menu.add("sha256x2", () -> sha256x2());
        menu.add("sha256Hex", () -> sha256Hex());
        menu.add("sha256x2Hex", () -> sha256x2Hex());
        menu.add("ripemd160Hex", () -> ripemd160Hex());
        menu.add("keccakSha3Hex", () -> keccakSha3Hex());
        menu.add("checkSum4Hex", () -> checkSum4Hex());
        menu.add("hexToBase58", () -> hexToBase58());

        menu.showAndSelect(br);
    }

    public static void addresses( ) {
        System.out.println("Input the address or public key. Enter to exit:");
        String addrOrKey = Inputer.inputString(br);
        if ("".equals(addrOrKey)) return;
        System.out.println("Requesting ...");
        apipClient.addresses(addrOrKey, RequestMethod.POST,  AuthType.ENCRYPTED);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void encrypt( ) {
        System.out.println("Input the text. Enter to exit:");
        String msg = Inputer.inputString(br);
        if ("".equals(msg)) return;
        String key=null;
        String fid=null;
        EncryptType type = Inputer.chooseOne(EncryptType.values(), null, "Choose the type of encrypting:",br);
        switch (type){
            case Password -> {
                System.out.println("Input the password no more than 64 chars. Enter to exit:");
                key = Inputer.inputString(br);
            }
            case Symkey -> {
                System.out.println("Input the symKey. Enter to exit:");
                key = Inputer.inputString(br);
            }
            case AsyOneWay ->{
                System.out.println("Input the pubKey or FID. Enter to exit:");
                key = Inputer.inputString(br);
                if(KeyTools.isGoodFid(key)) {
                    fid = key;
                    key =null;
                }
            }
            default -> {
                System.out.println("Only for Password, Symkey, AsyOneWay.");
                return;
            }
        }

        if ("".equals(key)) return;
        System.out.println("Requesting ...");

        apipClient.encrypt(type,msg,key,fid, RequestMethod.POST,  AuthType.ENCRYPTED);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void verify( ) {
        System.out.println("Input the signature. Enter to exit:");
        String signature = Inputer.inputString(br);
        if ("".equals(signature)) return;
        System.out.println("Requesting ...");
        apipClient.verify(signature, RequestMethod.POST,  AuthType.ENCRYPTED);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void sha256( ) {
        System.out.println("Input the text. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.sha256(text, RequestMethod.POST,  AuthType.ENCRYPTED);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void sha256x2( ) {
        System.out.println("Input the text. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.sha256x2(text, RequestMethod.POST,  AuthType.ENCRYPTED);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void sha256Hex( ) {
        System.out.println("Input the Hex. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.sha256Hex(text, RequestMethod.POST,  AuthType.ENCRYPTED);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void sha256x2Hex( ) {
        System.out.println("Input the Hex. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.sha256x2Hex(text, RequestMethod.POST,  AuthType.ENCRYPTED);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void ripemd160Hex( ) {
        System.out.println("Input the Hex. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.ripemd160Hex(text, RequestMethod.POST,  AuthType.ENCRYPTED);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void keccakSha3Hex( ) {
        System.out.println("Input the Hex. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.KeccakSha3Hex(text, RequestMethod.POST,  AuthType.ENCRYPTED);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void hexToBase58( ) {
        System.out.println("Input the Hex. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.hexToBase58(text, RequestMethod.POST,  AuthType.ENCRYPTED);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void checkSum4Hex( ) {
        System.out.println("Input the Hex. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.checkSum4Hex(text, RequestMethod.POST,  AuthType.ENCRYPTED);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void offLineTx( ) {

        String fid = inputGoodFid(br, "Input the sender's FID:");

        List<Cash> sendToList = Cash.inputSendToList(br);

        long cd = Inputer.inputInt(br, "Input the required CD:", 0);
        System.out.println("Input the text of OpReturn. Enter to skip:");
        String msg = Inputer.inputString(br);
        if ("".equals(msg)) msg = null;
        System.out.println("Requesting Post...");
        apipClient.offLineTx(fid,sendToList,msg,cd, "1", RequestMethod.POST,  AuthType.ENCRYPTED);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());

        System.out.println("Requesting Get...");
        apipClient.offLineTx(fid,sendToList,msg,cd, "2", RequestMethod.GET,  AuthType.FREE);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void endpoint( ) {
        Menu menu = new Menu("Endpoint");
        menu.add("totalSupply", () -> System.out.println(apipClient.totalSupply()));
        menu.add("circulating", () -> System.out.println(apipClient.circulating()));
        menu.add("richlist", () -> System.out.println(apipClient.richlist()));
        menu.add("freecashInfo", () -> System.out.println(apipClient.freecashInfo()));

        menu.showAndSelect(br);
    }
//
//    public static void swapTools( ) {
//        System.out.println("SwapTools...");
//        while (true) {
//            Menu menu = new Menu();
//            menu.setName("SwapTools");
//            menu.add(ApipApiNames.SwapHallAPIs);
//            menu.show();
//            int choice = menu.choose(br);
//
//            switch (choice) {
//                case 1 -> swapRegister();
//                case 2 -> swapUpdate();
//                case 3 -> swapInfo();
//                case 4 -> swapState();
//                case 5 -> swapLp();
//                case 6 -> swapPending();
//                case 7 -> swapFinished();
//                case 8 -> swapPrice();
//                case 0 -> {
//                    return;
//                }
//            }
//        }
//    }
//
//    private static void swapRegister( ) {
//        System.out.println("Input the sid. Enter to exit:");
//        String sid = Inputer.inputString(br);
//        if ("".equals(sid)) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = SwapHallAPIs.swapRegisterPost( sid);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        Menu.anyKeyToContinue(br);
//    }
//
//    private static void swapUpdate( ) {
//        System.out.println("In developing...");
//    }
//
//    private static void swapInfo() {
//        System.out.println("Input the sid. Enter to exit:");
//        String sid = Inputer.inputString(br);
//        if ("".equals(sid)) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = SwapHallAPIs.getSwapInfo( new String[]{sid}, null);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//
//        List<String> last = diskClientData.getResponseBody().getLast();
//        if (last != null&& !last.isEmpty()) {
//            diskClientData = SwapHallAPIs.getSwapInfo( new String[]{sid}, last);
//            System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        }
//        Menu.anyKeyToContinue(br);
//    }
//
//    private static void swapState() {
//        System.out.println("Input the sid. Enter to exit:");
//        String sid = Inputer.inputString(br);
//        if ("".equals(sid)) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = SwapHallAPIs.getSwapState( sid);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//    }
//
//    private static void swapLp() {
//        System.out.println("Input the sid. Enter to exit:");
//        String sid = Inputer.inputString(br);
//        if ("".equals(sid)) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = SwapHallAPIs.getSwapLp( sid);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//    }
//
//    private static void swapPending() {
//        System.out.println("Input the sid. Enter to exit:");
//        String sid = Inputer.inputString(br);
//        if ("".equals(sid)) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = SwapHallAPIs.getSwapPending( sid);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//    }
//
//    private static void swapFinished() {
//        System.out.println("Input the sid. Enter to exit:");
//        String sid = Inputer.inputString(br);
//        if ("".equals(sid)) return;
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = SwapHallAPIs.getSwapFinished( sid, null);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//
//        List<String> last = diskClientData.getResponseBody().getLast();
//        if (last != null && !last.isEmpty()) {
//            diskClientData = SwapHallAPIs.getSwapInfo( new String[]{sid}, last);
//            System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        }
//        Menu.anyKeyToContinue(br);
//    }
//
//    private static void swapPrice() {
//        System.out.println("Input the sid. Enter to ignore:");
//        String sid = Inputer.inputString(br);
//        if ("".equals(sid)) sid = null;
//
//        System.out.println("Input the sid. Enter to exit:");
//        String gTick = Inputer.inputString(br);
//        if ("".equals(gTick)) gTick = null;
//
//        System.out.println("Input the sid. Enter to exit:");
//        String mTick = Inputer.inputString(br);
//        if ("".equals(mTick)) mTick = null;
//
//        System.out.println("Requesting ...");
//        ApipClientEvent diskClientData = SwapHallAPIs.getSwapPrice( sid, gTick, mTick, null);
//        System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//
//        List<String> last = diskClientData.getResponseBody().getLast();
//        if (last != null && !last.isEmpty()) {
//            diskClientData = SwapHallAPIs.getSwapPrice( sid, gTick, mTick, last);
//            System.out.println("Result:\n" + diskClientData.getResponseBodyStr());
//        }
//        Menu.anyKeyToContinue(br);
//    }
//
//    public static void setting( byte[] symKey) {
//        System.out.println("Setting...");
//        while (true) {
//            Menu menu = new Menu();
//            menu.setName("Settings");
//            menu.add("Check APIP", "Reset APIP", "Refresh SessionKey", "Change password");
//            menu.show();
//            int choice = menu.choose(br);
//
//            switch (choice) {
//                case 1 -> checkApip();
//                case 2 -> resetApip(apipAccount, br);
//                case 3 -> sessionKey = refreshSessionKey(symKey);
//                case 4 -> {
//                    byte[] symKeyNew = resetPassword();
//                    if (symKeyNew == null) break;
//                    symKey = symKeyNew;
//                }
//                case 0 -> {
//                    return;
//                }
//            }
//        }
//    }
//
//    public static byte[] resetPassword() {
//
//        byte[] passwordBytesOld;
//        while (true) {
//            System.out.print("Check password. ");
//
//            passwordBytesOld = Inputer.getPasswordBytes();
//            byte[] sessionKey = decryptSessionKey(apipAccount.getSession().getSessionKeyCipher(), Hash.sha256x2(passwordBytesOld));
//            if (sessionKey != null) break;
//            System.out.println("Wrong password. Try again.");
//        }
//
//        byte[] passwordBytesNew;
//        passwordBytesNew = Inputer.inputAndCheckNewPassword();
//
//        byte[] symKeyOld = Hash.sha256x2(passwordBytesOld);
//
//        byte[] sessionKey = decryptSessionKey(apipAccount.getSession().getSessionKeyCipher(), symKeyOld);
//        byte[] priKey = EccAes256K1P7.decryptJsonBytes(userPriKeyCipher, symKeyOld);
//
//        byte[] symKeyNew = Hash.sha256x2(passwordBytesNew);
//        String buyerPriKeyCipherNew = EccAes256K1P7.encryptWithSymkey(priKey, symKeyNew);
//        if(buyerPriKeyCipherNew.contains("Error"))return null;
//
//        String sessionKeyCipherNew = EccAes256K1P7.encryptWithSymkey(sessionKey, symKeyNew);
//        if (sessionKeyCipherNew.contains("Error")) {
//            System.out.println("Get sessionKey wrong:" + sessionKeyCipherNew);
//        }
//        apipAccount.getSession().setSessionKeyCipher(sessionKeyCipherNew);
//        apipAccount.setUserPriKeyCipher(buyerPriKeyCipherNew);
//
//        ApiAccount.writeApipParamsToFile(apipAccount, APIP_Account_JSON);
//        return symKeyNew;
//    }
//
//    public static byte[] refreshSessionKey(byte[] symKey) {
//        System.out.println("Refreshing ...");
//        return signInEccPost(symKey, SignInMode.REFRESH);
//    }
//
//    public static void checkApip(ApiAccount initApiAccount) {
//        Shower.printUnderline(20);
//        System.out.println("Apip Service:");
//        String urlHead = initApiAccount.getApiUrl();
//        String[] ids = new String[]{initApiAccount.getProviderId()};
//        String via = initApiAccount.getVia();
//
//        System.out.println("Requesting ...");
//        ApipClientEvent apipClientEvent = apipClient.serviceByIds(HttpRequestMethod.POST,AuthType.FC_SIGN_BODY,ids);//ConstructAPIs.serviceByIdsPost(urlHead, ids, via, sessionKey);
//        System.out.println(apipClientEvent.getResponseBodyStr());
//
//        Shower.printUnderline(20);
//        System.out.println("User Params:");
//        System.out.println(JsonTools.toNiceJson(initApiAccount));
//        Shower.printUnderline(20);
//        Menu.anyKeyToContinue(br);
//    }
//
//    private static void resetApip(ApiAccount initApiAccount)) {
//        byte[] passwordBytes = Inputer.getPasswordBytes();
//        initApiAccount.updateApipAccount(br, passwordBytes);
//    }
}
