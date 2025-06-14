package startApipClient;


import data.apipData.BlockInfo;
import data.apipData.Fcdsl;
import data.apipData.RequestBody;
import data.apipData.TxInfo;
import data.fcData.Module;
import data.fchData.*;
import config.Starter;
import ui.Inputer;
import ui.Menu;
import ui.Shower;
import clients.ApipClient;
import config.ApiAccount;
import data.feipData.*;
import data.feipData.Service.ServiceType;
import config.Configure;
import server.ApipApiNames;
import core.crypto.EncryptType;
import core.crypto.KeyTools;
import data.fcData.ReplyBody;
import data.fcData.FcSession;
import data.fcData.FidTxMask;

import data.feipData.serviceParams.ApipParams;
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
import static server.ApipApiNames.*;
import static core.fch.Inputer.inputGoodFid;

public class StartApipClient {
    public static final int DEFAULT_SIZE = 20;
    public static Configure configure;
    public static Settings settings;
    public static ApiAccount apipAccount;
    public static ApipClient apipClient;
    public static BufferedReader br ;
    public static String clientName= ServiceType.APIP.name();


    public static 	Map<String,Object> settingMap = new HashMap<>();

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

        Menu menu = new Menu();
        menu.setTitle("Apip Client");
        ArrayList<String> menuItemList = new ArrayList<>();
        menuItemList.add("Example");
        menuItemList.add("BasicAPIs");
        menuItemList.add("OpenAPI");
        menuItemList.add("Blockchain");
        menuItemList.add("Identity");
        menuItemList.add("Organize");
        menuItemList.add("Construct");
        menuItemList.add("Personal");
        menuItemList.add("Publish");
        menuItemList.add("Finance");
        menuItemList.add("Wallet");
        menuItemList.add("Crypto");
        menuItemList.add("Endpoint");
        menuItemList.add("Settings");

        menu.add(menuItemList);

        while (true) {
            System.out.println(" << APIP Client>>");
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> showExample();
                case 2 -> basicApi();
                case 3 -> openAPI(symKey);
                case 4 -> blockchain();
                case 5 -> identity();
                case 6 -> organize();
                case 7 -> construct();
                case 8 -> personal();
                case 9 -> publish();
                case 10 -> finance();
                case 11 -> wallet();
                case 12 -> crypto();
                case 13 -> endpoint();
                case 14 -> {
                    settings.setting(br, null);
                    symKey = settings.getSymkey();
                    apipClient = (ApipClient) settings.getClient(ServiceType.APIP);
                }
                case 0 -> {
                    BytesUtils.clearByteArray(symKey);
                    BytesUtils.clearByteArray(apipClient.getSessionKey());
                    return;
                }
            }
        }
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
        while (true) {
            Menu menu = new Menu();
            menu.setTitle("Free GET methods");
            menu.add(ApipApiNames.freeAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> ping();
                case 2 -> chainInfo();
                case 3 -> getService();
                case 4 -> fidCidSeek();
                case 5 -> getFidCid();
                case 6 -> getAvatar();
                case 7 -> cashValid(DEFAULT_SIZE, "valid:desc->birthHeight:desc->id:asc");
                case 8 -> broadcastTx(RequestMethod.GET,AuthType.FREE);
                case 0 -> {
                    return;
                }
            }
        }
    }

    private static void ping() {
        boolean data = (boolean) apipClient.ping(VERSION_1, RequestMethod.GET,AuthType.FREE, ServiceType.APIP);
        System.out.println(data);
    }

    public static void getService() {
        System.out.println("Getting the default service information...");
        ReplyBody replier = ApipClient.getService(apipClient.getUrlHead(), ApipApiNames.VERSION_1, ApipParams.class);
        if(replier!=null) JsonUtils.printJson(replier);
        else System.out.println("Failed to get service.");
        Menu.anyKeyToContinue(br);
    }
//
    public static void openAPI(byte[] symKey) {
        System.out.println("OpenAPI...");
        Menu menu = new Menu();

        ArrayList<String> menuItemList = new ArrayList<>();
        menu.setTitle("OpenAPI");
        menuItemList.add("getService");
        menuItemList.add("SignInPost");
        menuItemList.add("SignInEccPost");
        menuItemList.add("TotalsGet");
        menuItemList.add("TotalsPost");
        menuItemList.add("generalPost");


        menu.add(menuItemList);

        while (true) {
            System.out.println(" << Maker manager>>");
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> getService();
                case 2 -> signInPost(symKey, RequestBody.SignInMode.NORMAL);
                case 3 -> signInEccPost(symKey, RequestBody.SignInMode.NORMAL);
                case 4 -> totalsGet();
                case 5 -> totalsPost();
                case 6 -> generalPost();
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void generalPost( ) {
        Fcdsl fcdsl = new Fcdsl();
        System.out.println("Input the index name. Enter to exit:");
        String input = Inputer.inputString(br);
        if ("".equals(input)) return;
        fcdsl.setIndex(input);

        fcdsl.promoteInput(br);

        if (fcdsl.isBadFcdsl()) {
            System.out.println("Fcdsl wrong:");
            System.out.println(JsonUtils.toNiceJson(fcdsl));
            return;
        }
        System.out.println(JsonUtils.toNiceJson(fcdsl));
        Menu.anyKeyToContinue(br);
        System.out.println("Requesting ...");
        ReplyBody replier = apipClient.general(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonUtils.printJson(replier);
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
        Map<String, String> result  = apipClient.totals(RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static byte[] signInEccPost(byte[] symKey, RequestBody.SignInMode mode) {
        System.out.println("Post request for signInEcc...");
        FcSession fcSession = apipClient.signInEcc(apipClient.getApiAccount(), mode, symKey, null);
        JsonUtils.printJson(fcSession);
        Menu.anyKeyToContinue(br);
        return Hex.fromHex(fcSession.getKey());
    }

    public static byte[] signInPost(byte[] symKey, RequestBody.SignInMode mode) {
        System.out.println("Post request for signIn...");
        FcSession fcSession = apipClient.signIn(apipClient.getApiAccount(), mode, symKey);
        JsonUtils.printJson(fcSession);
        Menu.anyKeyToContinue(br);
        return Hex.fromHex(fcSession.getKey());
    }

    public static void blockchain( ) {
        System.out.println("Blockchain...");
        while (true) {
            Menu menu = new Menu();
            menu.setTitle("Blockchain");
            menu.add(ApipApiNames.blockchainAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> blockSearch(DEFAULT_SIZE, "height:desc->id:asc");
                case 2 -> bestBlock();
                case 3 -> blockByIds();
                case 4 -> blockByHeights();
                case 5 -> cashSearch(DEFAULT_SIZE, "valid:desc->birthHeight:desc->id:asc");
                case 6 -> cashByIds();
                case 7 -> fidSearch(DEFAULT_SIZE, "lastHeight:desc->id:asc");
                case 8 -> fidByIds();
                case 9 -> opReturnSearch(DEFAULT_SIZE, "height:desc->txIndex:desc->id:asc");
                case 10 -> opReturnByIds();
                case 11 -> multisignSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc");
                case 12 -> multisignByIds();
                case 13 -> txSearch(DEFAULT_SIZE, "height:desc->id:asc");
                case 14 -> txByIds();
                case 15 -> txByFid();
                case 16 -> chainInfo();
                case 17 -> blockTimeHistory();
                case 18 -> difficultyHistory();
                case 19 -> hashRateHistory();
                case 0 -> {
                    return;
                }
            }
        }
    }
    public static void blockByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input blockIds:", 0);
        System.out.println("Requesting blockByIds...");
        Map<String, BlockInfo> result = apipClient.blockByIds(RequestMethod.POST,AuthType.FC_SIGN_BODY,ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void blockSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting blockSearch...");
        List<BlockInfo> result = apipClient.blockSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void blockByHeights( ) {
        String[] heights = Inputer.inputStringArray(br, "Input block heights:", 0);
        System.out.println("Requesting blockByHeights...");
        Map<String, BlockInfo> result = apipClient.blockByHeights(RequestMethod.POST,AuthType.FC_SIGN_BODY,heights);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void bestBlock() {
        System.out.println("Requesting bestBlock...");
        Block result = apipClient.bestBlock(RequestMethod.POST,AuthType.FC_SIGN_BODY);
        if(result==null)return;
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void cashByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input cashIds:", 0);
        System.out.println("Requesting cashByIds...");
        Map<String, Cash> result = apipClient.cashByIds(RequestMethod.POST,AuthType.FC_SIGN_BODY,ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void cashValid(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting cashValid...");
        List<Cash> result = apipClient.cashValid(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void cashSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting cashSearch...");
        List<Cash> result = apipClient.cashSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void fidByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input FIDs:", 0);
        System.out.println("Requesting fidByIds...");
        Map<String, Cid> result = apipClient.fidByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void fidSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting fidSearch...");
        List<Cid> result = apipClient.fidSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void opReturnByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input opReturnIds:", 0);
        System.out.println("Requesting opReturnByIds...");
        Map<String, OpReturn> result = apipClient.opReturnByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void opReturnSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting opReturnSearch...");
        List<OpReturn> result = apipClient.opReturnSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void multisignByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input p2shIds:", 0);
        System.out.println("Requesting p2shByIds...");
        Map<String, Multisign> result = apipClient.multisignByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void multisignSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting p2shSearch...");
        List<Multisign> result = apipClient.multisignSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void txByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input txIds:", 0);
        System.out.println("Requesting txByIds...");
        Map<String, TxInfo> result = apipClient.txByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void txSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting txSearch...");
        List<TxInfo> result = apipClient.txSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
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
        List<FidTxMask> result = apipClient.txByFid(fid, size, last, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void chainInfo() {
        Long height = Inputer.inputLongWithNull(br,"Input the height you want. Enter to query by the best height:");
        System.out.println("Requesting chainInfo...");
        FchChainInfo result = apipClient.chainInfo(height, RequestMethod.POST, AuthType.FC_SIGN_BODY);
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
        Map<Long, Long> result = apipClient.blockTimeHistory(startTime,endTime,count, RequestMethod.POST, AuthType.FC_SIGN_BODY);
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
        Map<Long, String> result = apipClient.difficultyHistory(startTime,endTime,count, RequestMethod.POST, AuthType.FC_SIGN_BODY);
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
        Map<Long, String> result = apipClient.hashRateHistory(startTime,endTime,count, RequestMethod.POST, AuthType.FC_SIGN_BODY);
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
        while (true) {
            Menu menu = new Menu();
            menu.setTitle("Identity");
            menu.add(ApipApiNames.identityAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> cidSearch(DEFAULT_SIZE, "nameTime:desc->id:asc");
                case 2 -> cidByIds();
                case 3 -> cidHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 4 -> fidCidSeek();
                case 5 -> getFidCid();
                case 6 -> nobodySearch(DEFAULT_SIZE, "deathHeight:desc->deathTxIndex:desc");
                case 7 -> nobodyByIds();
                case 8 -> homepageHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 9 -> noticeFeeHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 10 -> reputationHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 11 -> nidSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc");
                case 12 -> getAvatar();
                case 13 -> avatars();
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void cidByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input FIDs:", 0);
        System.out.println("Requesting cidByIds...");
        Map<String, Cid> result = apipClient.cidByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void cidSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting cidSearch...");
        List<Cid> result = apipClient.cidSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
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
        Cid result = apipClient.getFidCid(id);
        if(result==null)return;
        System.out.println("Got "+result.getCid()+".");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void nobodyByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input FIDs:", 0);
        System.out.println("Requesting nobodyByIds...");
        Map<String, Nobody> result = apipClient.nobodyByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void nobodySearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting nobodySearch...");
        List<Nobody> result = apipClient.nobodySearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void cidHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting cidHistory...");
        List<CidHist> result = apipClient.cidHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void homepageHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting homepageHistory...");
        List<CidHist> result = apipClient.homepageHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void noticeFeeHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting noticeFeeHistory...");
        List<CidHist> result = apipClient.noticeFeeHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void reputationHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting reputationHistory...");
        List<CidHist> result = apipClient.reputationHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void avatars( ) {
        String[] fids = core.fch.Inputer.inputFidArray(br, "Input FIDs:", 0);

        System.out.println("Requesting avatars...");
        Map<String, String> result = apipClient.avatars(fids, RequestMethod.POST, AuthType.FC_SIGN_BODY);
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
        while (true) {
            Menu menu = new Menu();
            menu.setTitle("Organize");
            menu.add(ApipApiNames.organizeAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> groupSearch(DEFAULT_SIZE, "tCdd:desc->id:asc");
                case 2 -> groupByIds();
                case 3 -> groupMembers();
                case 4 -> groupOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 5 -> myGroups(DEFAULT_SIZE);
                case 6 -> teamSearch(DEFAULT_SIZE, "active:desc->tRate:desc->id:asc");
                case 7 -> teamByIds();
                case 8 -> teamMembers();
                case 9 -> teamExMembers();
                case 10 -> teamOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 11 -> teamRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 12 -> teamOtherPersons();
                case 13 -> myTeams(DEFAULT_SIZE);
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void groupByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input GIDs:", 0);
        System.out.println("Requesting ...");
        apipClient.groupByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void groupSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        apipClient.groupSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void groupMembers( ) {
        String[] ids = Inputer.inputStringArray(br, "Input GIDs:", 0);
        System.out.println("Requesting ...");
        apipClient.groupMembers(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void groupOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        apipClient.groupOpHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void myGroups(Integer size) {
        System.out.println("Input the FID. Enter to exit:");
        String id = Inputer.inputString(br);
        if ("".equals(id)) return;
        List<String> last = Inputer.inputStringList(br, "Input the last:", 0);
        System.out.println("Requesting ...");
        apipClient.myGroups(id, null, size, last, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void teamByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input TIDs:", 0);
        apipClient.teamByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void teamSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        apipClient.teamSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void teamMembers( ) {
        String[] ids = Inputer.inputStringArray(br, "Input TIDs:", 0);
        System.out.println("Requesting ...");
        apipClient.teamMembers(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void teamExMembers( ) {
        String[] ids = Inputer.inputStringArray(br, "Input TIDs:", 0);
        System.out.println("Requesting ...");
        apipClient.teamExMembers(RequestMethod.POST, AuthType.FC_SIGN_BODY,ids);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void teamRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        apipClient.teamRateHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void teamOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        apipClient.teamOpHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void teamOtherPersons( ) {
        String[] ids = Inputer.inputStringArray(br, "Input TIDs:", 0);
        System.out.println("Requesting ...");
        apipClient.teamOtherPersons(RequestMethod.POST, AuthType.FC_SIGN_BODY,ids);
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
        apipClient.myTeams(id, sinceHeight,size, last, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void construct( ) {
        System.out.println("Construct...");
        while (true) {
            Menu menu = new Menu();
            menu.setTitle("Construct");
            menu.add(ApipApiNames.constructAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> protocolSearch(DEFAULT_SIZE, "active:desc->tRate:desc->id:asc");
                case 2 -> protocolByIds();
                case 3 -> protocolOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 4 -> protocolRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 5 -> codeSearch(DEFAULT_SIZE, "active:desc->tRate:desc->id:asc");
                case 6 -> codeByIds();
                case 7 -> codeOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 8 -> codeRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 9 -> serviceSearch(DEFAULT_SIZE, "active:desc->tRate:desc->id:asc");
                case 10 -> serviceByIds();
                case 11 -> serviceOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 12 -> serviceRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 13 -> appSearch(DEFAULT_SIZE, "active:desc->tRate:desc->id:asc");
                case 14 -> appByIds();
                case 15 -> appOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 16 -> appRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 17 -> remarkSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc");
                case 18 -> remarkByIds();
                case 19 -> remarkOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 20 -> remarkRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 21 -> paperSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc");
                case 22 -> paperByIds();
                case 23 -> paperOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 24 -> paperRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 25 -> bookSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc");
                case 26 -> bookByIds();
                case 27 -> bookOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 28 -> bookRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 29 -> artworkSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc");
                case 30 -> artworkByIds();
                case 31 -> artworkOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 32 -> artworkRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void protocolByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input PIDs:", 0);
        System.out.println("Requesting protocolByIds...");
        Map<String, Protocol> result = apipClient.protocolByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void protocolSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting protocolSearch...");
        List<Protocol> result = apipClient.protocolSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void protocolOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting protocolOpHistory...");
        List<ProtocolHistory> result = apipClient.protocolOpHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void protocolRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<ProtocolHistory> result = apipClient.protocolRateHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void codeByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input Code_IDs:", 0);
        System.out.println("Requesting codeByIds...");
        Map<String, Code> result = apipClient.codeByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void codeSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting protocolSearch...");
        List<Code> result = apipClient.codeSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void codeRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting odeRateHistory...");
        List<CodeHistory> result = apipClient.codeRateHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void codeOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting codeOpHistory...");
        List<CodeHistory> result = apipClient.codeOpHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void serviceByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input SIDs:", 0);
        System.out.println("Requesting ...");
        Map<String, Service> result = apipClient.serviceByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void serviceSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<Service> result = apipClient.serviceSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void serviceRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<ServiceHistory> result = apipClient.serviceRateHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void serviceOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<ServiceHistory> result = apipClient.serviceOpHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void appByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input AIDs:", 0);
        System.out.println("Requesting ...");
        Map<String, App> result = apipClient.appByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void appSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<App> result = apipClient.appSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void appRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<AppHistory> result = apipClient.appRateHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void appOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<AppHistory> result = apipClient.appOpHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }


    public static void personal( ) {
        System.out.println("Personal...");
        while (true) {
            Menu menu = new Menu();
            menu.setTitle("Personal");
            menu.add(ApipApiNames.personalAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> boxSearch(DEFAULT_SIZE, "lastHeight:desc->id:asc");
                case 2 -> boxByIds();
                case 3 -> boxHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 4 -> contactSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc");
                case 5 -> contactByIds();
                case 6 -> contactsDeleted(DEFAULT_SIZE, "lastHeight:desc->id:asc");
                case 7 -> secretSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc");
                case 8 -> secretByIds();
                case 9 -> secretsDeleted(DEFAULT_SIZE, "lastHeight:desc->id:asc");
                case 10 -> mailSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc");
                case 11 -> mailByIds();
                case 12 -> mailsDeleted(DEFAULT_SIZE, "lastHeight:desc->id:asc");
                case 13 -> mailThread(DEFAULT_SIZE, "birthHeight:desc->id:asc");
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void boxByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input BIDs:", 0);
        System.out.println("Requesting boxByIds...");
        Map<String, Box> result = apipClient.boxByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void boxSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting boxSearch...");
        List<Box> result = apipClient.boxSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void boxHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting boxHistory...");
        List<BoxHistory> result = apipClient.boxHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }


    public static void contactByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input Contact_Ids:", 0);
        System.out.println("Requesting contactByIds...");
        Map<String, Contact> result = apipClient.contactByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void contactSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting contactSearch...");
        List<Contact> result = apipClient.contactSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void contactsDeleted(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting contactsDeleted...");
        List<Contact> result = apipClient.contactDeleted(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }


    public static void secretByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input Secret_Ids:", 0);
        System.out.println("Requesting secretByIds...");
        Map<String, Secret> result = apipClient.secretByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void secretSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting secretSearch...");
        List<Secret> result = apipClient.secretSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void secretsDeleted(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting secretsDeleted...");
        List<Secret> result = apipClient.secretDeleted(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }


    public static void mailByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input Mail_Ids:", 0);
        System.out.println("Requesting mailByIds...");
        Map<String, Mail> result = apipClient.mailByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void mailSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting mailSearch...");
        List<Mail> result = apipClient.mailSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void mailsDeleted(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting mailsDeleted...");
        List<Mail> result = apipClient.mailDeleted(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
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
        List<Mail> result = apipClient.mailThread(fidA,fidB,startTime,endTime, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }


    public static void proofByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input Proof_Ids:", 0);
        System.out.println("Requesting proofByIds...");
        Map<String, Proof> result = apipClient.proofByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void proofSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting proofSearch...");
        List<Proof> result = apipClient.proofSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void proofHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting proofHistory...");
        List<ProofHistory> result = apipClient.proofHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void statementByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input Statement_Ids:", 0);
        System.out.println("Requesting statementByIds...");
        Map<String, Statement> result = apipClient.statementByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void statementSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting statementSearch...");
        List<Statement> result = apipClient.statementSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void nidSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting nidSearch...");
        List<Nid> result = apipClient.nidSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void tokenByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input Token_Ids:", 0);
        System.out.println("Requesting tokenByIds...");
        Map<String, Token> result = apipClient.tokenByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void tokenSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting tokenSearch...");
        List<Token> result = apipClient.tokenSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void tokenHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting tokenHistory...");
        List<TokenHistory> result = apipClient.tokenHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
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
        List<Group> result = apipClient.myTokens(id, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void tokenHoldersByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input Token_Ids:", 0);
        System.out.println("Requesting tokenHoldersByIds...");
        Map<String, TokenHolder> result = apipClient.tokenHoldersByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void tokenHolderSearch(int defaultSize, String defaultSort ) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting tokenHolderSearch...");
        List<TokenHolder> result = apipClient.tokenHolderSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void publish( ) {
        System.out.println("Publish...");
        while (true) {
            Menu menu = new Menu();
            menu.setTitle("Publish");
            menu.add(ApipApiNames.publishAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> statementSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc");
                case 2 -> statementByIds();
                case 3 -> essaySearch(DEFAULT_SIZE, "birthHeight:desc->id:asc");
                case 4 -> essayByIds();
                case 5 -> essayOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 6 -> essayRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 7 -> reportSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc");
                case 8 -> reportByIds();
                case 9 -> reportOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 10 -> reportRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 11 -> paperSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc");
                case 12 -> paperByIds();
                case 13 -> paperOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 14 -> paperRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 15 -> bookSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc");
                case 16 -> bookByIds();
                case 17 -> bookOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 18 -> bookRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 19 -> artworkSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc");
                case 20 -> artworkByIds();
                case 21 -> artworkOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 22 -> artworkRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 23 -> remarkSearch(DEFAULT_SIZE, "birthHeight:desc->id:asc");
                case 24 -> remarkByIds();
                case 25 -> remarkOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 26 -> remarkRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void essayByIds() {
        String[] ids = Inputer.inputStringArray(br, "Input Essay_Ids:", 0);
        System.out.println("Requesting essayByIds...");
        Map<String, Essay> result = apipClient.essayByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void essaySearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting essaySearch...");
        List<Essay> result = apipClient.essaySearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void essayOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting essayOpHistory...");
        List<EssayHistory> result = apipClient.essayOpHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void essayRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting essayRateHistory...");
        List<EssayHistory> result = apipClient.essayRateHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void reportByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input Report_Ids:", 0);
        System.out.println("Requesting reportByIds...");
        Map<String, Report> result = apipClient.reportByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void reportSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting reportSearch...");
        List<Report> result = apipClient.reportSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void reportOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting reportOpHistory...");
        List<ReportHistory> result = apipClient.reportOpHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void reportRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting reportRateHistory...");
        List<ReportHistory> result = apipClient.reportRateHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void paperByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input Paper_Ids:", 0);
        System.out.println("Requesting paperByIds...");
        Map<String, Paper> result = apipClient.paperByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void paperSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting paperSearch...");
        List<Paper> result = apipClient.paperSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void paperOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting paperOpHistory...");
        List<PaperHistory> result = apipClient.paperOpHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void paperRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting paperRateHistory...");
        List<PaperHistory> result = apipClient.paperRateHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void bookByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input Book_Ids:", 0);
        System.out.println("Requesting bookByIds...");
        Map<String, Book> result = apipClient.bookByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void bookSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting bookSearch...");
        List<Book> result = apipClient.bookSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void bookOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting bookOpHistory...");
        List<BookHistory> result = apipClient.bookOpHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void bookRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting bookRateHistory...");
        List<BookHistory> result = apipClient.bookRateHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void remarkByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input Remark_Ids:", 0);
        System.out.println("Requesting remarkByIds...");
        Map<String, Remark> result = apipClient.remarkByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void remarkSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting remarkSearch...");
        List<Remark> result = apipClient.remarkSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void remarkOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting remarkOpHistory...");
        List<RemarkHistory> result = apipClient.remarkOpHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void remarkRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting remarkRateHistory...");
        List<RemarkHistory> result = apipClient.remarkRateHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void artworkByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input Artwork_Ids:", 0);
        System.out.println("Requesting artworkByIds...");
        Map<String, Artwork> result = apipClient.artworkByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void artworkSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting artworkSearch...");
        List<Artwork> result = apipClient.artworkSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void artworkOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting artworkOpHistory...");
        List<ArtworkHistory> result = apipClient.artworkOpHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void artworkRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting artworkRateHistory...");
        List<ArtworkHistory> result = apipClient.artworkRateHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null)return;
        System.out.println("Got "+result.size()+" items.");
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void finance( ) {
        System.out.println("Finance...");
        while (true) {
            Menu menu = new Menu();
            menu.setTitle("Finance");
            menu.add(ApipApiNames.financeAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> proofSearch(DEFAULT_SIZE, "lastHeight:desc->id:asc");
                case 2 -> proofByIds();
                case 3 -> proofHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 4 -> tokenSearch(DEFAULT_SIZE, "lastHeight:desc->id:asc");
                case 5 -> tokenByIds();
                case 6 -> tokenHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 7 -> tokenHolderSearch(DEFAULT_SIZE, "lastHeight:desc->id:asc");
                case 8 -> tokenHoldersByIds();
                case 9 -> myTokens();
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void wallet( ) {
        System.out.println("Wallet...");
        while (true) {
            Menu menu = new Menu();
            menu.setTitle("Wallet");
            menu.add(
                "Broadcast Tx",
                "Decode Tx",
                "Get valid cashes",
                "Unconfirmed",
                "Fee Rate",
                "Get offLine Tx",
                "Balance by FIDs"
            );
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> broadcastTx(RequestMethod.POST, AuthType.FC_SIGN_BODY);
                case 2 -> decodeTx();
                case 3 -> cashValid();
                case 4 -> unconfirmed();
                case 5 -> feeRate();
                case 6 -> offLineTx();
                case 7 -> balanceByIds();
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void balanceByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input FIDs:", 0);
        System.out.println("Requesting fidByIds...");
        Map<String, Long> result = apipClient.balanceByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
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
        apipClient.decodeTx(txHex, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
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
        apipClient.cashValid(fid, amount, cd, outputSize, msgSize, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void unconfirmed( ) {
        String[] ids = Inputer.inputStringArray(br, "Input FIDs:", 0);
        System.out.println("Requesting ...");
        apipClient.unconfirmed(RequestMethod.POST,  AuthType.FC_SIGN_BODY,ids);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void feeRate( ) {
        System.out.println("Requesting ...");
        apipClient.feeRate(RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void crypto( ) {
        System.out.println("CryptoTools");
        while (true) {
            Menu menu = new Menu();
            menu.setTitle("CryptoTools");
            menu.add(ApipApiNames.cryptoAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> addresses();
                case 2 -> encrypt();
                case 3 -> verify();
                case 4 -> sha256();
                case 5 -> sha256x2();
                case 6 -> sha256Hex();
                case 7 -> sha256x2Hex();
                case 8 -> ripemd160Hex();
                case 9 -> keccakSha3Hex();
                case 10 -> checkSum4Hex();
                case 11 -> hexToBase58();
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void addresses( ) {
        System.out.println("Input the address or public key. Enter to exit:");
        String addrOrKey = Inputer.inputString(br);
        if ("".equals(addrOrKey)) return;
        System.out.println("Requesting ...");
        apipClient.addresses(addrOrKey, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
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

        apipClient.encrypt(type,msg,key,fid, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void verify( ) {
        System.out.println("Input the signature. Enter to exit:");
        String signature = Inputer.inputString(br);
        if ("".equals(signature)) return;
        System.out.println("Requesting ...");
        apipClient.verify(signature, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void sha256( ) {
        System.out.println("Input the text. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.sha256(text, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void sha256x2( ) {
        System.out.println("Input the text. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.sha256x2(text, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void sha256Hex( ) {
        System.out.println("Input the Hex. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.sha256Hex(text, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void sha256x2Hex( ) {
        System.out.println("Input the Hex. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.sha256x2Hex(text, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void ripemd160Hex( ) {
        System.out.println("Input the Hex. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.ripemd160Hex(text, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void keccakSha3Hex( ) {
        System.out.println("Input the Hex. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.KeccakSha3Hex(text, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void hexToBase58( ) {
        System.out.println("Input the Hex. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.hexToBase58(text, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void checkSum4Hex( ) {
        System.out.println("Input the Hex. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.checkSum4Hex(text, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void offLineTx( ) {

        String fid = inputGoodFid(br, "Input the sender's FID:");

        List<SendTo> sendToList = SendTo.inputSendToList(br);

        long cd = Inputer.inputInt(br, "Input the required CD:", 0);
        System.out.println("Input the text of OpReturn. Enter to skip:");
        String msg = Inputer.inputString(br);
        if ("".equals(msg)) msg = null;
        System.out.println("Requesting Post...");
        apipClient.offLineTx(fid,sendToList,msg,cd, "1", RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());

        System.out.println("Requesting Get...");
        apipClient.offLineTx(fid,sendToList,msg,cd, "2", RequestMethod.GET,  AuthType.FREE);
        JsonUtils.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void endpoint( ) {
        while (true) {
            Menu menu = new Menu();
            menu.setTitle("Endpoint");
            menu.add(endpointAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> System.out.println(apipClient.totalSupply());
                case 2 -> System.out.println(apipClient.circulating());
                case 3 -> System.out.println(apipClient.richlist());
                case 4 -> System.out.println(apipClient.freecashInfo());
                case 0 -> {
                    return;
                }
            }
        }
    }
//
//    public static void swapTools( ) {
//        System.out.println("SwapTools...");
//        while (true) {
//            Menu menu = new Menu();
//            menu.setName("SwapTools");
//            menu.add(ApiNames.SwapHallAPIs);
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
//        return signInEccPost(symKey, RequestBody.SignInMode.REFRESH);
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
