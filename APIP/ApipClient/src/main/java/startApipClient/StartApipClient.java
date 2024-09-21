package startApipClient;


import apip.apipData.*;
import appTools.Inputer;
import appTools.Menu;
import appTools.Shower;
import clients.apipClient.*;
import configure.ServiceType;
import configure.Configure;
import constants.ApiNames;
import crypto.EncryptType;
import crypto.KeyTools;
import fcData.FcReplierHttp;
import fch.Wallet;
import fch.fchData.*;
import feip.feipData.*;
import feip.feipData.serviceParams.ApipParams;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.http.AuthType;
import javaTools.http.RequestMethod;
import org.bouncycastle.util.encoders.Base64;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static appTools.Inputer.inputString;
import static clients.FeipClient.setCid;
import static clients.FeipClient.setMaster;
import static constants.ApiNames.*;
import static fch.Inputer.inputGoodFid;

public class StartApipClient {
    public static final int DEFAULT_SIZE = 20;
    private static BufferedReader br;
    private static ApipClient apipClient;

    public static void main(String[] args) {
        br = new BufferedReader(new InputStreamReader(System.in));
        Menu.welcome("APIP");

        //Load config info from the file of config.json
        Configure.loadConfig(br);
        Configure configure = Configure.checkPassword(br);
        byte[] symKey = configure.getSymKey();

        String fid = configure.chooseMainFid(symKey);

        ApipClientSettings settings = ApipClientSettings.loadFromFile(fid, ApipClientSettings.class);
        if(settings ==null) settings = new ApipClientSettings(configure);
        settings.initiateClient(fid, symKey,configure,br);

        if(settings.getApipAccount()!=null)
            apipClient = (ApipClient) settings.getApipAccount().getClient();

        Long bestHeight = new Wallet(apipClient).getBestHeight();
        CidInfo fidInfo = settings.checkFidInfo(apipClient, br);
        String userPriKeyCipher = configure.getFidCipherMap().get(fid);

        if(fidInfo !=null && fidInfo.getCid()==null){
            if(fch.Inputer.askIfYes(br,"No CID yet. Set CID?")){
                setCid(fid, userPriKeyCipher, bestHeight, symKey,apipClient,br);
            }
        }

        if(fidInfo !=null && fidInfo.getMaster()==null){
            if(fch.Inputer.askIfYes(br,"No master yet. Set master for this FID?")){
                setMaster(fid, userPriKeyCipher, bestHeight, symKey,apipClient,br);
            }
        }

        Menu menu = new Menu();
        menu.setName("Apip Client");
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
                case 10 -> wallet();
                case 11 -> crypto();
                case 12 -> endpoint();
                case 13 -> settings.setting(symKey, br, null);
                case 0 -> {
                    BytesTools.clearByteArray(symKey);
                    BytesTools.clearByteArray(apipClient.getSessionKey());
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
            menu.setName("Free GET methods");
            menu.add(ApiNames.FreeAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> ping();
                case 2 -> chainInfo();
                case 3 -> getService();
                case 4 -> fidCidSeek();
                case 5 -> getFidCid();
                case 6 -> getAvatar();
                case 7 -> cashValid(DEFAULT_SIZE, "valid:desc->birthHeight:desc->cashId:asc");
                case 8 -> broadcastTx(RequestMethod.GET,AuthType.FREE);
                case 0 -> {
                    return;
                }
            }
        }
    }

    private static void ping() {
        boolean data = (boolean) apipClient.ping(Version1, RequestMethod.GET,AuthType.FREE, ServiceType.APIP);
        System.out.println(data);
    }

    public static void getService() {
        System.out.println("Getting the default service information...");
        FcReplierHttp replier = ApipClient.getService(apipClient.getUrlHead(), ApiNames.Version1, ApipParams.class);
        if(replier!=null)JsonTools.printJson(replier);
        else System.out.println("Failed to get service.");
        Menu.anyKeyToContinue(br);
    }
//
    public static void openAPI(byte[] symKey) {
        System.out.println("OpenAPI...");
        Menu menu = new Menu();

        ArrayList<String> menuItemList = new ArrayList<>();
        menu.setName("OpenAPI");
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
            System.out.println(JsonTools.toNiceJson(fcdsl));
            return;
        }
        System.out.println(JsonTools.toNiceJson(fcdsl));
        Menu.anyKeyToContinue(br);
        System.out.println("Requesting ...");
        FcReplierHttp replier = apipClient.general(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(replier);
        Menu.anyKeyToContinue(br);
    }

    public static void totalsGet() {
        System.out.println("Get request for totals...");
        Map<String, String> result = apipClient.totals(RequestMethod.GET, AuthType.FREE);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void totalsPost( ) {
        System.out.println("Post request for totals...");
        Map<String, String> result  = apipClient.totals(RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static byte[] signInEccPost(byte[] symKey, RequestBody.SignInMode mode) {
        System.out.println("Post request for signInEcc...");
        Session session = apipClient.signInEcc(apipClient.getApiAccount(), RequestBody.SignInMode.NORMAL, symKey);
        JsonTools.printJson(session);
        Menu.anyKeyToContinue(br);
        return Hex.fromHex(session.getSessionKey());
    }

    public static byte[] signInPost(byte[] symKey, RequestBody.SignInMode mode) {
        System.out.println("Post request for signIn...");
        Session session = apipClient.signIn(apipClient.getApiAccount(), RequestBody.SignInMode.NORMAL, symKey);
        JsonTools.printJson(session);
        Menu.anyKeyToContinue(br);
        return Hex.fromHex(session.getSessionKey());
    }

    public static void blockchain( ) {
        System.out.println("Blockchain...");
        while (true) {
            Menu menu = new Menu();
            menu.setName("Blockchain");
            menu.add(ApiNames.BlockchainAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> blockSearch(DEFAULT_SIZE, "height:desc->blockId:asc");
                case 2 -> blockByIds();
                case 3 -> blockByHeights();
                case 4 -> cashSearch(DEFAULT_SIZE, "valid:desc->birthHeight:desc->cashId:asc");
                case 5 -> cashByIds();
                case 6 -> fidSearch(DEFAULT_SIZE, "lastHeight:desc->fid:asc");
                case 7 -> fidByIds();
                case 8 -> opReturnSearch(DEFAULT_SIZE, "height:desc->txIndex:desc->txId:asc");
                case 9 -> opReturnByIds();
                case 10 -> p2shSearch(DEFAULT_SIZE, "birthHeight:desc->fid:asc");
                case 11 -> p2shByIds();
                case 12 -> txSearch(DEFAULT_SIZE, "height:desc->txId:asc");
                case 13 -> txByIds();
                case 14 -> txByFid();
                case 15 -> chainInfo();
                case 16 -> blockTimeHistory();
                case 17 -> difficultyHistory();
                case 18 -> hashRateHistory();
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
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void blockSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting blockSearch...");
        List<BlockInfo> result = apipClient.blockSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void blockByHeights( ) {
        String[] heights = Inputer.inputStringArray(br, "Input block heights:", 0);
        System.out.println("Requesting blockByHeights...");
        Map<String, BlockInfo> result = apipClient.blockByHeights(RequestMethod.POST,AuthType.FC_SIGN_BODY,heights);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void cashByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input cashIds:", 0);
        System.out.println("Requesting cashByIds...");
        Map<String, Cash> result = apipClient.cashByIds(RequestMethod.POST,AuthType.FC_SIGN_BODY,ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void cashValid(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting cashValid...");
        List<Cash> result = apipClient.cashValid(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void cashSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting cashSearch...");
        List<Cash> result = apipClient.cashSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void fidByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input FIDs:", 0);
        System.out.println("Requesting fidByIds...");
        Map<String, Address> result = apipClient.fidByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void fidSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting fidSearch...");
        List<Address> result = apipClient.fidSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void opReturnByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input opReturnIds:", 0);
        System.out.println("Requesting opReturnByIds...");
        Map<String, OpReturn> result = apipClient.opReturnByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void opReturnSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting opReturnSearch...");
        List<OpReturn> result = apipClient.opReturnSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void p2shByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input p2shIds:", 0);
        System.out.println("Requesting p2shByIds...");
        Map<String, P2SH> result = apipClient.p2shByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void p2shSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting p2shSearch...");
        List<P2SH> result = apipClient.p2shSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void txByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input txIds:", 0);
        System.out.println("Requesting txByIds...");
        Map<String, TxInfo> result = apipClient.txByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void txSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting txSearch...");
        List<TxInfo> result = apipClient.txSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void txByFid( ) {
        String fid = Inputer.inputString(br, "Input FID");
        int size = fch.Inputer.inputInteger(br,"Input the size:",0);
        String[] last=null;
        String lastStr = Inputer.inputString(br,"Input the last values spited with ','");
        if(!lastStr.isEmpty()) last = lastStr.split(",");
        System.out.println("Requesting txByIds...");
        List<TxInfo> result = apipClient.txByFid(fid, size, last, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void chainInfo() {
        Long height = Inputer.inputLongWithNull(br,"Input the height you want. Enter to query by the best height:");
        System.out.println("Requesting chainInfo...");
        FchChainInfo result = apipClient.chainInfo(height, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void blockTimeHistory() {
        Long startTime = Inputer.inputLongWithNull(br,"Input the start timestamp:");
        Long endTime = Inputer.inputLongWithNull(br,"Input the end timestamp:");
        Integer count = Inputer.inputInteger(br,"Input the data count:",0);
        System.out.println("Requesting chainInfo...");
        Map<Long, Long> result = apipClient.blockTimeHistory(startTime,endTime,count, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void difficultyHistory() {
        Long startTime = Inputer.inputLongWithNull(br,"Input the start timestamp:");
        Long endTime = Inputer.inputLongWithNull(br,"Input the end timestamp:");
        Integer count = Inputer.inputInteger(br,"Input the data count:",0);
        System.out.println("Requesting chainInfo...");
        Map<Long, String> result = apipClient.difficultyHistory(startTime,endTime,count, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void hashRateHistory() {
        Long startTime = Inputer.inputLongWithNull(br,"Input the start timestamp:");
        Long endTime = Inputer.inputLongWithNull(br,"Input the end timestamp:");
        Integer count = Inputer.inputInteger(br,"Input the data count:",0);
        System.out.println("Requesting chainInfo...");
        Map<Long, String> result = apipClient.hashRateHistory(startTime,endTime,count, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static Fcdsl inputFcdsl(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = new Fcdsl();

        fcdsl.promoteSearch(defaultSize, defaultSort,br);

        if (fcdsl.isBadFcdsl()) {
            System.out.println("Fcdsl wrong:");
            System.out.println(JsonTools.toNiceJson(fcdsl));
            return null;
        }
        System.out.println("fcdsl:\n" + JsonTools.toNiceJson(fcdsl));

        Menu.anyKeyToContinue(br);
        return fcdsl;
    }

    public static void identity( ) {
        System.out.println("Identity...");
        while (true) {
            Menu menu = new Menu();
            menu.setName("Identity");
            menu.add(ApiNames.IdentityAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> cidInfoSearch(DEFAULT_SIZE, "nameTime:desc->fid:asc");
                case 2 -> cidInfoByIds();
                case 3 -> cidHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 4 -> fidCidSeek();
                case 5 -> getFidCid();
                case 6 -> nobodySearch(DEFAULT_SIZE, "deathHeight:desc->deathTxIndex:desc");
                case 7 -> nobodyByIds();
                case 8 -> homepageHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 9 -> noticeFeeHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 10 -> reputationHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 11 -> getAvatar();
                case 12 -> avatars();
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void cidInfoByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input FIDs:", 0);
        System.out.println("Requesting cidInfoByIds...");
        Map<String, CidInfo> result = apipClient.cidInfoByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void cidInfoSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<CidInfo> result = apipClient.cidInfoSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void fidCidSeek() {
        String searchStr = inputString(br,"Input the whole or part of the FID or CID you are looking for");
        System.out.println("Requesting ...");
        Map<String, String[]> result = apipClient.fidCidSeek(searchStr);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void getFidCid() {
        System.out.println("Input FID or CID:");
        String id = Inputer.inputString(br);
        System.out.println("Requesting ...");
        CidInfo result = apipClient.getFidCid(id);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void nobodyByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input FIDs:", 0);
        System.out.println("Requesting nobodyByIds...");
        Map<String, feip.feipData.Nobody> result = apipClient.nobodyByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void nobodySearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<feip.feipData.Nobody> result = apipClient.nobodySearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void cidHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<CidHist> result = apipClient.cidHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void homepageHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<CidHist> result = apipClient.homepageHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void noticeFeeHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<CidHist> result = apipClient.noticeFeeHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void reputationHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<CidHist> result = apipClient.reputationHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void avatars( ) {
        String[] fids = fch.Inputer.inputFidArray(br, "Input FIDs:", 0);

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
        System.out.println("Requesting ...");
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
            menu.setName("Organize");
            menu.add(ApiNames.OrganizeAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> groupSearch(DEFAULT_SIZE, "tCdd:desc->gid:asc");
                case 2 -> groupByIds();
                case 3 -> groupMembers();
                case 4 -> groupOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 5 -> myGroups();
                case 6 -> teamSearch(DEFAULT_SIZE, "active:desc->tRate:desc->tid:asc");
                case 7 -> teamByIds();
                case 8 -> teamMembers();
                case 9 -> teamExMembers();
                case 10 -> teamOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 11 -> teamRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 12 -> teamOtherPersons();
                case 13 -> myTeams();
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
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void groupSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        apipClient.groupSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void groupMembers( ) {
        String[] ids = Inputer.inputStringArray(br, "Input GIDs:", 0);
        System.out.println("Requesting ...");
        apipClient.groupMembers(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void groupOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        apipClient.groupOpHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void myGroups( ) {
        System.out.println("Input the FID. Enter to exit:");
        String id = Inputer.inputString(br);
        if ("".equals(id)) return;
        System.out.println("Requesting ...");
        apipClient.myGroups(id, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void teamByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input TIDs:", 0);
        apipClient.teamByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void teamSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        apipClient.teamSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void teamMembers( ) {
        String[] ids = Inputer.inputStringArray(br, "Input TIDs:", 0);
        System.out.println("Requesting ...");
        apipClient.teamMembers(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void teamExMembers( ) {
        String[] ids = Inputer.inputStringArray(br, "Input TIDs:", 0);
        System.out.println("Requesting ...");
        apipClient.teamExMembers(RequestMethod.POST, AuthType.FC_SIGN_BODY,ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void teamRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        apipClient.teamRateHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void teamOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        apipClient.teamOpHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void teamOtherPersons( ) {
        String[] ids = Inputer.inputStringArray(br, "Input TIDs:", 0);
        System.out.println("Requesting ...");
        apipClient.teamOtherPersons(RequestMethod.POST, AuthType.FC_SIGN_BODY,ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void myTeams( ) {
        System.out.println("Input the FID. Enter to exit:");
        String id = Inputer.inputString(br);
        if ("".equals(id)) return;
        System.out.println("Requesting ...");
        apipClient.myTeams(id, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void construct( ) {
        System.out.println("Construct...");
        while (true) {
            Menu menu = new Menu();
            menu.setName("Construct");
            menu.add(ApiNames.ConstructAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> protocolSearch(DEFAULT_SIZE, "active:desc->tRate:desc->pid:asc");
                case 2 -> protocolByIds();
                case 3 -> protocolOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 4 -> protocolRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 5 -> codeSearch(DEFAULT_SIZE, "active:desc->tRate:desc->codeId:asc");
                case 6 -> codeByIds();
                case 7 -> codeOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 8 -> codeRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 9 -> serviceSearch(DEFAULT_SIZE, "active:desc->tRate:desc->sid:asc");
                case 10 -> serviceByIds();
                case 11 -> serviceOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 12 -> serviceRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 13 -> appSearch(DEFAULT_SIZE, "active:desc->tRate:desc->aid:asc");
                case 14 -> appByIds();
                case 15 -> appOpHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 16 -> appRateHistory(DEFAULT_SIZE, "height:desc->index:desc");
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
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void protocolSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting protocolSearch...");
        List<Protocol> result = apipClient.protocolSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void protocolOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting protocolOpHistory...");
        List<ProtocolHistory> result = apipClient.protocolOpHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void protocolRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<ProtocolHistory> result = apipClient.protocolRateHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void codeByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input Code_IDs:", 0);
        System.out.println("Requesting codeByIds...");
        Map<String, Code> result = apipClient.codeByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void codeSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting protocolSearch...");
        List<Code> result = apipClient.codeSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void codeRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting odeRateHistory...");
        List<CodeHistory> result = apipClient.codeRateHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void codeOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting codeOpHistory...");
        List<CodeHistory> result = apipClient.codeOpHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void serviceByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input SIDs:", 0);
        System.out.println("Requesting ...");
        Map<String, Service> result = apipClient.serviceByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void serviceSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<Service> result = apipClient.serviceSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void serviceRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<ServiceHistory> result = apipClient.serviceRateHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void serviceOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<ServiceHistory> result = apipClient.serviceOpHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void appByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input AIDs:", 0);
        System.out.println("Requesting ...");
        Map<String, App> result = apipClient.appByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void appSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<App> result = apipClient.appSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void appRateHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<AppHistory> result = apipClient.appRateHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void appOpHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<AppHistory> result = apipClient.appOpHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }


    public static void personal( ) {
        System.out.println("Personal...");
        while (true) {
            Menu menu = new Menu();
            menu.setName("Personal");
            menu.add(ApiNames.PersonalAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> boxSearch(DEFAULT_SIZE, "lastHeight:desc->bid:asc");
                case 2 -> boxByIds();
                case 3 -> boxHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 4 -> contactSearch(DEFAULT_SIZE, "birthHeight:desc->contactId:asc");
                case 5 -> contactByIds();
                case 6 -> contactsDeleted(DEFAULT_SIZE, "lastHeight:desc->contactId:asc");
                case 7 -> secretSearch(DEFAULT_SIZE, "birthHeight:desc->secretId:asc");
                case 8 -> secretByIds();
                case 9 -> secretsDeleted(DEFAULT_SIZE, "lastHeight:desc->secretId:asc");
                case 10 -> mailSearch(DEFAULT_SIZE, "birthHeight:desc->mailId:asc");
                case 11 -> mailByIds();
                case 12 -> mailsDeleted(DEFAULT_SIZE, "lastHeight:desc->mailId:asc");
                case 13 -> mailThread(DEFAULT_SIZE, "birthHeight:desc->mailId:asc");
                case 0 -> {
                    return;
                }
            }
        }
    }

    public static void boxByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input BIDs:", 0);
        System.out.println("Requesting ...");
        Map<String, Box> result = apipClient.boxByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void boxSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<Box> result = apipClient.boxSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void boxHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<BoxHistory> result = apipClient.boxHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }


    public static void contactByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input Contact_Ids:", 0);
        System.out.println("Requesting ...");
        Map<String, Contact> result = apipClient.contactByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void contactSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<Contact> result = apipClient.contactSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void contactsDeleted(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<Contact> result = apipClient.contactDeleted(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }


    public static void secretByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input Secret_Ids:", 0);
        System.out.println("Requesting ...");
        Map<String, Secret> result = apipClient.secretByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void secretSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<Secret> result = apipClient.secretSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void secretsDeleted(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<Secret> result = apipClient.secretDeleted(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }


    public static void mailByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input Mail_Ids:", 0);
        System.out.println("Requesting ...");
        Map<String, Mail> result = apipClient.mailByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void mailSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<Mail> result = apipClient.mailSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void mailsDeleted(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<Mail> result = apipClient.mailDeleted(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void mailThread(int defaultSize, String defaultSort) {
        String fidA = fch.Inputer.inputGoodFid(br,"Input the FID:");
        String fidB = fch.Inputer.inputGoodFid(br,"Input another FID:");
        if(fidA==null ||fidB==null)return;
        Long startTime = Inputer.inputDate(br,"yyyy-mm-dd","Input the start time. Enter to skip:");
        Long endTime = Inputer.inputDate(br,"yyyy-mm-dd","Input the end time. Enter to skip:");

        System.out.println("Requesting ...");
        List<Mail> result = apipClient.mailThread(fidA,fidB,startTime,endTime, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }


    public static void proofByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input Proof_Ids:", 0);
        System.out.println("Requesting ...");
        Map<String, Proof> result = apipClient.proofByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void proofSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<Proof> result = apipClient.proofSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void proofHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<ProofHistory> result = apipClient.proofHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void statementByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input Statement_Ids:", 0);
        System.out.println("Requesting ...");
        Map<String, Statement> result = apipClient.statementByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void statementSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<Statement> result = apipClient.statementSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void nidSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<Nid> result = apipClient.nidSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void tokenByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input Token_Ids:", 0);
        System.out.println("Requesting ...");
        Map<String, Token> result = apipClient.tokenByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void tokenSearch(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<Token> result = apipClient.tokenSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void tokenHistory(int defaultSize, String defaultSort) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<TokenHistory> result = apipClient.tokenHistory(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void myTokens( ) {
        System.out.println("Input the FID. Enter to exit:");
        String id = Inputer.inputString(br);
        if ("".equals(id)) return;
        System.out.println("Requesting ...");
        apipClient.myTokens(id, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }
    public static void tokenHoldersByIds( ) {
        String[] ids = Inputer.inputStringArray(br, "Input Token_Ids:", 0);
        System.out.println("Requesting ...");
        Map<String, TokenHolder> result = apipClient.tokenHoldersByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void tokenHolderSearch(int defaultSize, String defaultSort ) {
        Fcdsl fcdsl = inputFcdsl(defaultSize, defaultSort);
        if (fcdsl == null) return;
        System.out.println("Requesting ...");
        List<TokenHolder> result = apipClient.tokenHolderSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void publish( ) {
        System.out.println("Publish...");
        while (true) {
            Menu menu = new Menu();
            menu.setName("Publish");
            menu.add(ApiNames.PublishAPIs);
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> proofSearch(DEFAULT_SIZE, "lastHeight:desc->bid:asc");
                case 2 -> proofByIds();
                case 3 -> proofHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 4 -> statementSearch(DEFAULT_SIZE, "birthHeight:desc->contactId:asc");
                case 5 -> statementByIds();
                case 6 -> nidSearch(DEFAULT_SIZE, "birthHeight:desc->nameId:asc");
                case 7 -> tokenSearch(DEFAULT_SIZE, "lastHeight:desc->tokenId:asc");
                case 8 -> tokenByIds();
                case 9 -> tokenHistory(DEFAULT_SIZE, "height:desc->index:desc");
                case 10 -> tokenHolderSearch(DEFAULT_SIZE, "lastHeight:desc->id:asc");
                case 11 -> tokenHoldersByIds();
                case 12 -> myTokens();
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
            menu.setName("Wallet");
            menu.add(BroadcastTx,
                    DecodeTx,
                    "cashValidForPay",
                    "cashValidForCd",
                    Unconfirmed,
                    FeeRate,
                    "offLineTxForPay",
                    "offLineTxByCd");
            menu.show();
            int choice = menu.choose(br);

            switch (choice) {
                case 1 -> broadcastTx(RequestMethod.POST, AuthType.FC_SIGN_BODY);
                case 2 -> decodeTx();
                case 3 -> cashValidForPay();
                case 4 -> cashValidForCd();
                case 5 -> unconfirmed();
                case 6 -> feeRate();
                case 7 -> offLineTxForPay();
                case 8 -> offLineTxByCd();
                case 0 -> {
                    return;
                }
            }
        }
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
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
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
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void cashValidForPay( ) {
        String fid;

        while (true) {
            System.out.println("Input the sender's FID:");
            fid = Inputer.inputString(br);
            if (KeyTools.isValidFchAddr(fid)) break;
            System.out.println("It's not a FID. Try again.");
        }
        Double amount = Inputer.inputDouble(br, "Input the amount:");
        if (amount == null) return;
        System.out.println("Requesting ...");
        apipClient.cashValid(fid, amount, null, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void cashValidForCd( ) {
        String fid;

        while (true) {
            System.out.println("Input the sender's FID:");
            fid = Inputer.inputString(br);
            if (KeyTools.isValidFchAddr(fid)) break;
            System.out.println("It's not a FID. Try again.");
        }
        long cd = Inputer.inputLong(br, "Input the required CD:");
        System.out.println("Requesting ...");
        apipClient.cashValid(fid, null,cd, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void unconfirmed( ) {
        String[] ids = Inputer.inputStringArray(br, "Input FIDs:", 0);
        System.out.println("Requesting ...");
        apipClient.unconfirmed(RequestMethod.POST,  AuthType.FC_SIGN_BODY,ids);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void feeRate( ) {
        System.out.println("Requesting ...");
        apipClient.feeRate(RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void crypto( ) {
        System.out.println("CryptoTools");
        while (true) {
            Menu menu = new Menu();
            menu.setName("CryptoTools");
            menu.add(ApiNames.CryptoAPIs);
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
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
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
            case SymKey -> {
                System.out.println("Input the symKey. Enter to exit:");
                key = Inputer.inputString(br);
            }
            case AsyOneWay ->{
                System.out.println("Input the pubKey or FID. Enter to exit:");
                key = Inputer.inputString(br);
                if(KeyTools.isValidFchAddr(key)) {
                    fid = key;
                    key =null;
                }
            }
            default -> {
                System.out.println("Only for Password, SymKey, AsyOneWay.");
                return;
            }
        }

        if ("".equals(key)) return;
        System.out.println("Requesting ...");

        apipClient.encrypt(type,msg,key,fid, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void verify( ) {
        System.out.println("Input the signature. Enter to exit:");
        String signature = Inputer.inputString(br);
        if ("".equals(signature)) return;
        System.out.println("Requesting ...");
        apipClient.verify(signature, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void sha256( ) {
        System.out.println("Input the text. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.sha256(text, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void sha256x2( ) {
        System.out.println("Input the text. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.sha256x2(text, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void sha256Hex( ) {
        System.out.println("Input the Hex. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.sha256Hex(text, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void sha256x2Hex( ) {
        System.out.println("Input the Hex. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.sha256x2Hex(text, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void ripemd160Hex( ) {
        System.out.println("Input the Hex. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.ripemd160Hex(text, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void keccakSha3Hex( ) {
        System.out.println("Input the Hex. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.KeccakSha3Hex(text, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void hexToBase58( ) {
        System.out.println("Input the Hex. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.hexToBase58(text, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void checkSum4Hex( ) {
        System.out.println("Input the Hex. Enter to exit:");
        String text = Inputer.inputString(br);
        if ("".equals(text)) return;
        System.out.println("Requesting ...");
        apipClient.checkSum4Hex(text, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void offLineTxForPay( ) {

        String fid = inputGoodFid(br, "Input the sender's FID:");

        List<SendTo> sendToList = SendTo.inputSendToList(br);

        System.out.println("Input the text of OpReturn. Enter to skip:");
        String msg = Inputer.inputString(br);
        if ("".equals(msg)) msg = null;
        System.out.println("Requesting ...");
        apipClient.offLineTx(fid,sendToList,msg,null, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void offLineTxByCd( ) {

        String fid = inputGoodFid(br, "Input the FID:");
        long cd = Inputer.inputInteger(br, "Input the required CD:", 0);
        List<SendTo> sendToList = SendTo.inputSendToList(br);
        System.out.println("Input the text of OpReturn. Enter to skip:");
        String msg = Inputer.inputString(br);
        if ("".equals(msg)) msg = null;
        System.out.println("Requesting ...");
        apipClient.offLineTx(fid,sendToList,msg,cd, RequestMethod.POST,  AuthType.FC_SIGN_BODY);
        JsonTools.printJson(apipClient.getFcClientEvent().getResponseBody());
        Menu.anyKeyToContinue(br);
    }

    public static void endpoint( ) {
        while (true) {
            Menu menu = new Menu();
            menu.setName("Endpoint");
            menu.add(EndpointAPIs);
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
//        String buyerPriKeyCipherNew = EccAes256K1P7.encryptWithSymKey(priKey, symKeyNew);
//        if(buyerPriKeyCipherNew.contains("Error"))return null;
//
//        String sessionKeyCipherNew = EccAes256K1P7.encryptWithSymKey(sessionKey, symKeyNew);
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
