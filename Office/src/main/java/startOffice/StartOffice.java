package startOffice;

import apip.apipData.CidInfo;
import apip.apipData.Fcdsl;
import apip.apipData.Sort;
import appTools.Menu;
import appTools.Shower;
import clients.FeipClient;
import clients.apipClient.ApipClient;
import clients.fcspClient.DiskClient;
import clients.esClient.EsTools;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import configure.Configure;
import constants.FieldNames;
import crypto.CryptoDataByte;
import crypto.Decryptor;
import fcData.FcReplierHttp;
import fch.Inputer;
import fch.ParseTools;
import fch.Wallet;
import fch.fchData.Cash;
import javaTools.Hex;
import javaTools.ObjectTools;
import javaTools.http.AuthType;
import javaTools.http.RequestMethod;
import nasa.NaSaRpcClient;
import redis.clients.jedis.JedisPool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

import static constants.FieldNames.*;
import static constants.Strings.ASC;
import static constants.Values.TRUE;
import static fch.Wallet.getCashListFromEs;

public class StartOffice {
    private static BufferedReader br;
    private static ApipClient apipClient;
    private static String fid;
    private static String userPriKeyCipher;
    private static CidInfo fidInfo;
    private static ElasticsearchClient esClient;
    private static JedisPool jedisPool;
    private static NaSaRpcClient fchClient;
    private static DiskClient diskClient;
    private static long bestHeight;
    private static OfficeSettings settings;

    public static void main(String[] args) throws IOException {
        br = new BufferedReader(new InputStreamReader(System.in));
        Menu.welcome("Office");

        //Load config info from the file of config.json
        Configure.loadConfig(br);
        Configure configure = Configure.checkPassword(br);
        byte[] symKey = configure.getSymKey();

        fid = configure.chooseMainFid(symKey);
        settings = OfficeSettings.loadFromFile(fid,OfficeSettings.class);//new ApipClientSettings(configure,br);
        if(settings==null) settings = new OfficeSettings(configure);
        settings.initiateClient(fid, symKey, configure, br);

        if(settings.getApipAccount()!=null)
            apipClient = (ApipClient) settings.getApipAccount().getClient();
        if(settings.getEsAccount()!=null)
            esClient = (ElasticsearchClient) settings.getEsAccount().getClient();
        if(settings.getRedisAccount()!=null)
            jedisPool = (JedisPool) settings.getRedisAccount().getClient();
        if(settings.getNasaAccount()!=null)
            fchClient = (NaSaRpcClient) settings.getNasaAccount().getClient();
//        if(settings.getDiskAccount()!=null)
//            diskClient = (DiskClient) settings.getDiskAccount().getClient();

        bestHeight = new Wallet(apipClient,esClient,fchClient).getBestHeight();
        fidInfo = settings.checkFidInfo(apipClient,br);
        userPriKeyCipher = configure.getFidCipherMap().get(fid);

        if(fidInfo!=null && fidInfo.getCid()==null){
            if(Inputer.askIfYes(br,"No CID yet. Set CID?")){
                FeipClient.setCid(fid,userPriKeyCipher,bestHeight,symKey,apipClient,br);
                return;
            }
        }

        if(fidInfo!=null &&fidInfo.getMaster()==null){
            if(Inputer.askIfYes(br,"No master yet. Set master for this FID?")){
                FeipClient.setMaster(fid,userPriKeyCipher,bestHeight,symKey,apipClient,br);
                return;
            }
        }

        Menu menu = new Menu();
        menu.setName("Office");
        menu.add("Cash");
        menu.add("Relative");
        while(true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> cashManager(br, userPriKeyCipher,symKey);
//                case 2 -> relative(br);
                case 3 -> sendFch(br);
                case 0 -> {
                    settings.close();
                    return;
                }
            }
        }
    }

    private static void sendFch(BufferedReader br) {
        System.out.println("Send fch...");
        Menu.anyKeyToContinue(br);
    }

    private static void cashManager(BufferedReader br, String priKeyCipher, byte[] symKey) {
        Menu menu = new Menu();
        menu.setName("Cash Manager");
        menu.add("List by Apip",
                "List by ES",
                "List by Node",
                "Merge/split Cashes");
        while(true) {
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> listCashByApip(br);
                case 2 -> listCashByEs(br);
                case 3 -> listCashByNasaNode(fid,fchClient,br);
                case 4 -> mergeCashes(br,priKeyCipher,symKey);
                case 0-> {
                    return;
                }
            }
        }
    }

    private static void mergeCashes(BufferedReader br, String priKeyCipher, byte[] symKey) {
        if (fidInfo == null) return;
        if(Inputer.askIfYes(br,"There are "+fidInfo.getCash()+" cashes with "+ fidInfo.getCd()+" cd in total. \nMerge/Split them?")){
            int maxCd;
            while(true) {
                maxCd = Inputer.inputInteger(br, "Input the maximum destroyable CD for every cash.", 0);
                Fcdsl fcdsl = new Fcdsl();

                fcdsl.addNewQuery().addNewRange().addNewFields(CD).addLte(String.valueOf(maxCd));
                fcdsl.getQuery().addNewTerms().addNewFields(OWNER).addNewValues(fid);
                fcdsl.addNewFilter().addNewTerms().addNewFields(VALID).addNewValues(TRUE);

                fcdsl.addSize(EsTools.READ_MAX);
                fcdsl.addSort(CD, ASC);
                fcdsl.addSort(CASH_ID, ASC);


                List<Cash>cashList = apipClient.cashSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);

                showCashList(cashList);
                if(appTools.Inputer.askIfYes(br,"Merge/split them?")){
                    int issueNum = appTools.Inputer.inputInteger(br,"Input the number of the new cashes you want:",100);
                    CryptoDataByte cryptoResult = new Decryptor().decryptJsonBySymKey(priKeyCipher, symKey);
                    if(cryptoResult.getCode()!=0){
                        cryptoResult.printCodeMessage();
                        return;
                    }
                    byte[]priKey = cryptoResult.getData();
                    String result = new Wallet(apipClient).mergeCashList(cashList, issueNum, priKey);
                    if(Hex.isHexString(result)) System.out.println("Done:");
                    else continue;
                    System.out.println(result);
                    if(Inputer.askIfYes(br,"Continue?"))continue;
                }else if(appTools.Inputer.askIfYes(br,"Try again?"))continue;
                return;
            }


        }


    }


    private static void listCashByApip(BufferedReader br) {
        List<Cash> cashList;
        Wallet wallet = new Wallet(apipClient);
        System.out.println("Input the sort:");
        ArrayList<Sort> sorts = null;
        List<String> lastList = null;
        if (Inputer.askIfYes(br, "Input sorts?")) {
            sorts = Sort.inputSortList(br);
            if (Inputer.askIfYes(br, "Input the last?")) {
                String[] last = Inputer.inputStringArray(br, "Input after strings:", sorts.size());
                lastList = Arrays.asList(last);
            }
        }
        boolean onlyValid = false;
        if(!Inputer.askIfYes(br,"Including spent?"))
            onlyValid = true;

        cashList = Wallet.getCashListFromApip(fid, onlyValid,40, sorts, lastList,apipClient);
        FcReplierHttp fcReplierHttp = apipClient.getFcClientEvent().getResponseBody();
        if (fcReplierHttp.getCode() != 0) {
            fcReplierHttp.printCodeMessage();
        }else {
            showCashList(cashList);
        }
        Menu.anyKeyToContinue(br);
    }
    private static void listCashByEs(BufferedReader br) {
        List<Cash> cashList;
        Wallet wallet = new Wallet(esClient);
        ArrayList<Sort> sorts = null;
        List<String> lastList = null;
        if (Inputer.askIfYes(br, "Input sorts?")) {
            sorts = Sort.inputSortList(br);
            if (Inputer.askIfYes(br, "Input the last?")) {
                String[] last = Inputer.inputStringArray(br, "Input after strings:", sorts.size());
                lastList = Arrays.asList(last);
            }
        }
        boolean onlyValid = false;
        if(!Inputer.askIfYes(br,"Including spent?"))
            onlyValid = true;
        FcReplierHttp fcReplierHttp = getCashListFromEs(fid, onlyValid,40, sorts, lastList,esClient);
        if (fcReplierHttp.getCode() != 0) {
            fcReplierHttp.printCodeMessage();
        }else {
            cashList = ObjectTools.objectToList(fcReplierHttp.getData(),Cash.class);//DataGetter.getCashList(fcReplier.getData());
            showCashList(cashList);
        }
        Menu.anyKeyToContinue(br);
    }
    private static void listCashByNasaNode(String fid, NaSaRpcClient naSaClient, BufferedReader br) {
        List<Cash> cashList;
        Wallet wallet = new Wallet(naSaClient);
        FcReplierHttp fcReplierHttp = wallet.getCashListFromNasaNode(fid, String.valueOf(1), true, naSaClient);
        if (fcReplierHttp.getCode() != 0) {
            fcReplierHttp.printCodeMessage();
        }else {
            cashList = ObjectTools.objectToList(fcReplierHttp.getData(),Cash.class);
            showCashList(cashList);
        }
        Menu.anyKeyToContinue(br);
    }

    private static void showCashList(List<Cash> cashList) {
        String title = "Cash List";
        String[] fields = new String[]{FieldNames.CASH_ID, VALID, VALUE, CD,CDD};
        int[] widths = new int[]{12, 6, 16, 16,16};
        List<List<Object>> valueListList = new ArrayList<>();
        for (Cash cash : cashList) {
            List<Object> list = new ArrayList<>();
            list.add(cash.getCashId());
            list.add(cash.isValid());
            list.add(ParseTools.satoshiToCoin(cash.getValue()));
            list.add(cash.getCd());
            list.add(cash.getCdd());
            valueListList.add(list);
        }
        Shower.showDataTable(title, fields, widths, valueListList);
    }

}
