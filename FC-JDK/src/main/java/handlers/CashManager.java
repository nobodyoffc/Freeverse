package handlers;

import core.fch.RawTxInfo;
import data.apipData.Fcdsl;
import data.apipData.Sort;
import app.HomeApp;
import ui.Inputer;
import ui.Menu;
import config.Settings;
import ui.Shower;
import clients.ApipClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.google.gson.Gson;
import constants.*;
import core.crypto.KeyTools;
import data.fchData.*;
import db.LocalDB;
import data.fcData.ReplyBody;
import core.fch.RawTxParser;
import core.fch.TxCreator;
import data.feipData.Service;
import clients.NaSaClient.NaSaRpcClient;
import data.nasa.UTXO;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.fch.FchMainNetwork;
import org.bitcoinj.params.MainNetParams;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import utils.*;
import utils.http.AuthType;
import utils.http.RequestMethod;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static ui.Inputer.askIfYes;
import static constants.Constants.*;
import static constants.FieldNames.*;
import static constants.IndicesNames.CASH;
import static constants.IndicesNames.CID;
import static constants.Values.ASC;
import static constants.Values.DESC;
import static constants.Values.*;
import static core.fch.TxCreator.DEFAULT_FEE_RATE;
import static core.fch.Wallet.decodeTxFch;

public class CashManager extends Manager<Cash> {
    private static final Logger log = LoggerFactory.getLogger(CashManager.class);
    public static final String name = CASH;


    private static final int BATCH_SIZE = 50;
    public static final String APIP_CLIENT_IS_NULL = "Failed. ApipClient is absent.";
    public static final Long MAX_INPUT_SIZE = 200L;
    
    private final Map<String, Integer> unsafeIdJumpNumMap;
    private final ApipClient apipClient;
    private final NaSaRpcClient nasaClient;
    private final ElasticsearchClient esClient;
    private MempoolManager mempoolHandler;
    private ContactManager contactHandler;
    private final Settings settings;

    private Cid cid;
    private final BufferedReader br;
    private long bestHeight;
    private Long lastFee;

    public CashManager(Settings settings){
        super(settings, ManagerType.CASH, LocalDB.SortType.UPDATE_ORDER, Cash.class,true,true);
        this.apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);
        this.nasaClient = (NaSaRpcClient) settings.getClient(Service.ServiceType.NASA_RPC);
        this.esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        this.settings = settings;

        if(nasaClient!=null) {
            this.mempoolHandler = (MempoolManager) settings.getManager(ManagerType.MEMPOOL);
            if (this.mempoolHandler == null) {
                this.mempoolHandler = new MempoolManager(settings);
                settings.getManagers().put(ManagerType.MEMPOOL, this.mempoolHandler);
            }
        }
        
        settings.saveClientSettings(mainFid,name);
        this.br = settings.getBr();
        unsafeIdJumpNumMap=new HashMap<>();
        freshCashDB();
    }


    public void menu(BufferedReader br, boolean isRootMenu) {
        showSummary();
        if(contactHandler==null)contactHandler = (ContactManager)settings.getManager(ManagerType.CONTACT);
        Menu menu = newMenu("Cash",isRootMenu);

        menu.add("Wallet", this::show);
        menu.add("Contact",()->contactHandler.menu(this.br,false));
        menu.add("Send", this::simpleSend);
        menu.add("Carve", this::carve);
        menu.add("Move All", this::moveAll);
        menu.add("Merge or Split", this::rearrange);
        menu.add("Advanced Send", this::sendTx);
        menu.add("Sign TX", this::signTx);
        menu.add("Decode and Broadcast", this::broadcast);
        menu.add("Incomes", this::incomes);
        menu.add("Expense", this::expense);
        menu.add("MultiSign",()-> HomeApp.multiSign(settings));
        menu.add("Detail by ID", this::cashDetail);
        menu.add("Reload All", this::freshValidCashes);
        menu.add("Fresh", this::freshCashDB);
        if(isRootMenu)
            menu.add("Settings", () -> settings.setting(this.br, null));
        menu.showAndSelect(this.br);
    }

    public static String makeOffLineTx(String sender, List<Cash> cashList, List<SendTo> sendToList, Long cd, Double feeRate, Multisign multisign, String ver, @org.jetbrains.annotations.Nullable BufferedReader br) {
        String signedTx;
        if(cashList!=null) cashList = Cash.makeCashListForPay(cashList);

        RawTxInfo txInfo = new RawTxInfo(sender, cashList, sendToList,null,cd, feeRate, multisign,ver);
        String txJson = txInfo.toNiceJson();
        System.out.println("Unsigned TX: \n--------\n" + txJson + "\n--------");
        QRCodeUtils.generateQRCode(txJson);
        if(br!=null) {
            if(askIfYes(br,"Show unsigned TX for CryptoSign V3?")){
                List<RawTxForCsV1> rawTxForCsV1List = RawTxForCsV1.fromV2(txInfo);
                String json = JsonUtils.toJson(rawTxForCsV1List);
                System.out.println("For CryptoSign v1-v3.");
                System.out.println("Unsigned TX: \n--------\n" + json + "\n--------");
                if(askIfYes(br,"Show QR codes?"))
                    QRCodeUtils.generateQRCode(json);
            }

            while (true) {
                System.out.println("Please sign the TX and input the signed Tx. 'q' to quit:");
                signedTx = Inputer.inputStringMultiLine(br);
                if ("q".equals(signedTx)) System.exit(0);
                if (Hex.isHexString(signedTx)) break;
                if (StringUtils.isBase64(signedTx)) {
                    signedTx = StringUtils.base64ToHex(signedTx);
                    break;
                }
                System.out.println("Failed to get signed Tx. Try again.");
            }
        } else  return null;
        return signedTx;
    }

    private ContactManager getContactHandler() {
        if (contactHandler == null && settings != null) {
            contactHandler = (ContactManager)settings.getManager(ManagerType.CONTACT);
        }
        return contactHandler;
    }

    public static boolean checkNobodys(BufferedReader br, List<SendTo> sendToList, ApipClient apipClient, ElasticsearchClient esClient) {
        System.out.println("Check nobodys...");
        List<String> recipientList = new ArrayList<>();
        if (sendToList != null && !sendToList.isEmpty()) {
            for (SendTo sendTo : sendToList) {
                recipientList.add(sendTo.getFid());
            }
        }
        List<Nobody> nobodyList = checkNobodys(recipientList, apipClient, esClient);
        if(!nobodyList.isEmpty()){
            System.out.println("Nobodys: "+nobodyList.toString());
            return askIfYes(br, "Cancel sending?");
        }
        return false;
    }

    public static Map<String, TxHasInfo> checkMempool(NaSaRpcClient nasaClient, ApipClient apipClient, ElasticsearchClient esClient){
        String[] txIds = nasaClient.getRawMempoolIds();
        if(txIds==null)return new HashMap<>();
        Map<String, TxHasInfo> txInMempoolMap = new HashMap<>();
        for (String txid : txIds) {
            String rawTxHex = nasaClient.getRawTx(txid);
            TxHasInfo txInMempool = null;
            try {
                txInMempool = RawTxParser.parseMempoolTx(rawTxHex, txid, apipClient, esClient);
            } catch (Exception e) {
                log.error("Parse tx of "+txid+" wrong:"+e.getMessage());
            }
            if(txInMempool!=null)txInMempoolMap.put(txid, txInMempool);
        }
        return txInMempoolMap;
    }

    public static List<Nobody> checkNobodys(List<String> recipientList, ApipClient apipClient, ElasticsearchClient esClient) {
        List<Nobody> nobodyList = new ArrayList<>();
        if (apipClient != null) {
            Map<String, Nobody> nobodyMap = apipClient.nobodyByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, recipientList.toArray(new String[0]));
            if (nobodyMap != null && !nobodyMap.isEmpty()) nobodyList.addAll(nobodyMap.values());
        } else if (esClient != null) {
            List<FieldValue> valueList = recipientList.stream().map(FieldValue::of).collect(Collectors.toList());
            SearchResponse<Nobody> result = null;
            try {
                result = esClient.search(s -> s.index(IndicesNames.NOBODY).query(q -> q.terms(t -> t.field(FieldNames.ID).terms(t1 -> t1.value(valueList)))), Nobody.class);
            } catch (IOException e) {
                log.error("ElasticSearch Client wrong when checking recipients.");
            }
            if (result != null && result.hits() != null && !result.hits().hits().isEmpty()) {
                nobodyList.addAll(result.hits().hits().stream().map(Hit::source).toList());
            }
        } else {
            System.out.println("No client to check nobodys.");
        }

        if (!nobodyList.isEmpty()) {
            System.out.println("The following recipients has lost their private keys:");
            for (Nobody nobody : nobodyList)
                System.out.println(nobody.getId());
        }
        return nobodyList;
    }


    public void resetCashDB(){
        clearDB();
        freshCashDB();
    }

    public Double getFeeRate() {
        if (apipClient != null) return apipClient.feeRate(RequestMethod.GET, AuthType.FREE);
        if (esClient != null) return calcFeeRate(esClient);
        if (nasaClient != null) return nasaClient.estimateFee(3);
        return DEFAULT_FEE_RATE;
    }

    public static Double calcFeeRate(ElasticsearchClient esClient) {
        SearchResponse<Block> result;
        try {
            result = esClient.search(s -> s.index(IndicesNames.BLOCK).size(20).sort(sort -> sort.field(f -> f.field(HEIGHT).order(SortOrder.Desc))), Block.class);
        } catch (IOException e) {
            log.error("ElasticSearch Client wrong when calculating fee rate.");
            return null;
        }
        if (result == null || result.hits() == null) return null;
        List<Block> blockList = new ArrayList<>();
        Block expensiveBlock = new Block();
        expensiveBlock.setFee(0L);
        for (Hit<Block> hit : result.hits().hits()) {
            Block block = hit.source();
            if (block == null || block.getTxCount() == 0) continue;
            blockList.add(block);
            if (block.getFee() > expensiveBlock.getFee())
                expensiveBlock = block;
        }
        if (blockList.isEmpty()) return 0D;
        blockList.remove(expensiveBlock);
        if (blockList.isEmpty()) return 0d;
        double feeSum = 0;
        double netBlockSizeSum = 0;
        for (Block block : blockList) {
            feeSum += block.getFee();
            netBlockSizeSum += (block.getSize() - Constants.EMPTY_BLOCK_SIZE);
        }
        if (feeSum == 0 || (netBlockSizeSum / 20) < 0.7 * Constants.M_BYTES) return Constants.MIN_FEE_RATE;
        return Math.max(feeSum / netBlockSizeSum / 1000, Constants.MIN_FEE_RATE);
    }

    public long  getBestHeight() {
        return bestHeight;
    }

    public Cid getCidInfo() {
        return cid;
    }

    public Long getLastFee() {
        return lastFee;
    }

    private void signTx() {
        RawTxInfo rawTxInfo;
        try {
            if(askIfYes(br,"Input the unsigned TX json:")) {
                String unsignedTxJson = Inputer.inputStringMultiLine(br);
                rawTxInfo = RawTxInfo.fromJson(unsignedTxJson, RawTxInfo.class);
                if (askIfYes(br, "Modify it?")) {
                    rawTxInfo = Inputer.updateFromUserInput(br, rawTxInfo, null, null, null);
                }
            }else {
                rawTxInfo = Inputer.createFromInput(br, RawTxInfo.class);
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return;
        }
        if(rawTxInfo ==null)return;
        System.out.println("Confirm your TX carefully:");
        System.out.println(rawTxInfo.toNiceJson());
        if(askIfYes(br,"Give up it?"))return;
        String signedTx = TxCreator.signOffLineTx(prikey, rawTxInfo, core.fch.FchMainNetwork.MAINNETWORK);
        Shower.showTextAndQR(signedTx,"The signed TX:");
    }

    private void broadcast() {
        String rawTx = Inputer.inputString(br, "Input the raw tx:");
        if(rawTx==null || rawTx.equals(""))return;
        String decodedTx = decodeTxFch(rawTx);
        if(decodedTx==null || decodedTx.equals(""))return;
        System.out.println(decodedTx);
        if(askIfYes(br, "Do you want to broadcast the tx?")){
            if(apipClient!=null){
                String result = apipClient.broadcastTx(rawTx, RequestMethod.POST, AuthType.FC_SIGN_BODY);
                System.out.println("Broadcasted tx: "+result);
            }else if(nasaClient!=null){
                String result = nasaClient.broadcast(decodedTx);
                System.out.println("Broadcasted tx: "+result);
            }else{
                System.out.println("No apip client or nasa client to broadcast.");
            }
        }
    }

    private void simpleSend() {
        String fid = Inputer.inputFid(br, "Input the fid of the receiver. Enter to send to yourself:");
        if(fid==null || fid.equals(""))fid = mainFid;
        Double amount = Inputer.inputDouble(br, "Input the amount of the each cash. Enter to stop:");
        if(amount==null || amount==0)return;
        sendAndUpdate(null, amount, 0L, new ArrayList<>(List.of(new SendTo(fid, amount))), null, DEFAULT_FEE_RATE, br);
        Menu.anyKeyToContinue(br);
    }

    public void moveAll() {
        if(localDB.getSize()==0){
            System.out.println("No valid cashes.");
            return;
        }

        if (tooManyInputs(localDB.getSize())) {
            System.out.println("Please merge your cashes first.");
            return;
        }

        List<Cash> cashList = localDB.getAll().values().stream().toList();
        if(exitWithoutValidCash(cashList))return;
        String fid =null;
        if(br!=null)fid = Inputer.inputFid(br, "Input the fid of the receiver. Enter to send to yourself:");
        if(fid==null)fid = mainFid;
        String words = null;
        if(br!=null)
            words = Inputer.inputString(br,"Input the message written on the blockchain. Enter to ignore:");
        sendAllTo(cashList, fid, words, br);
    }

    private boolean exitWithoutValidCash(List<Cash> cashList) {
        return (cashList==null || cashList.isEmpty()) && !askIfYes(br, "Failed to get valid cash list. Continue?");
    }

    private boolean tooManyInputs(long size) {
        if(size>MAX_INPUT_SIZE){
            System.out.println("There are "+size+" valid cashes.");
            System.out.println("We can't deal with more than "+MAX_INPUT_SIZE+" cashes at once.");
            return true;
        }
        return false;
    }

    public void show() {
        List<Cash> chosenItems = showOrChooseItemList("Chose your cashes",null,Shower.DEFAULT_PAGE_SIZE,br,true,true);

        if(chosenItems==null || chosenItems.isEmpty()){
            return;
        }
        opItems(chosenItems,"What you want to do with them?",br);
    }

    @Override
    public void opItems(List<Cash> items, String ask, BufferedReader br) {

        String op = Inputer.chooseOne(
            new String[]{"Show details","Send","Carve","Merge or Split"}, 
            null, 
            "What you want to do with them?", 
            br
        );
        if(op==null)return;
        switch (op) {
            case "Show details" -> showItemDetails(items, br);
            case "Send" -> sendTx(items);
            case "Carve" -> carve(items);
            case "Merge or Split" -> rearrange(items);
        }
    }

    public void moveAll(List<Cash> items, BufferedReader br) {
        String fid =null;
        String words = null;
        if(br!=null)fid = Inputer.inputFid(br, "Input the fid of the receiver. Enter to send to yourself:");
        if(fid==null)fid = mainFid;
        if(br!=null)
            words = Inputer.inputString(br,"Input the message written on the blockchain. Enter to ignore:");
        sendAllTo(items, fid, words, br);
    }

    private void cashDetail() {
        String cashId = Inputer.inputString(br, "Input the cash ID:");
        if(cashId == null)return;
        cashId = cashId.trim();
        if(!Hex.isHex32(cashId)){
            log.error("The input is not a cashId.");
            return;
        }
        Cash cash = localDB.get(cashId);
        if(cash==null){
            Map<String,Cash> cashMap = apipClient.cashByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY,cashId);
            if(cashMap==null || cashMap.isEmpty())return;
            cash = cashMap.get(cashId);
        }
        if(cash==null){
            System.out.println("Cash not found.");
            return;
        }
        String niceCashStr = JsonUtils.toNiceText(JsonUtils.toJson(cash),0);
        Shower.printUnderline(20);
        System.out.println(niceCashStr);
        Shower.printUnderline(20);
    }

    public void incomes() {
        String title = "Your incomes";
        if(apipClient==null){
            System.out.println(APIP_CLIENT_IS_NULL);
            return;
        }
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(OWNER).addNewValues(mainFid);
        fcdsl.addNewExcept().addNewTerms().addNewFields(ISSUER).addNewValues(mainFid,OP_RETURN);
        fcdsl.addSort(BIRTH_HEIGHT,DESC).addSort(ID,ASC);
        fcdsl.addSize(Shower.DEFAULT_PAGE_SIZE);
        requestAndShowCashList(title, fcdsl);
    }

    private void requestAndShowCashList(String title, Fcdsl fcdsl) {
        List<String> last = new ArrayList<>();
        Long totalPages = 0L;
        int currentPage = 1;
        while(true){
            if(!last.isEmpty()) fcdsl.addAfter(last);
            List<Cash> cashList = apipClient.cashSearch(fcdsl,RequestMethod.POST,AuthType.FC_SIGN_BODY);

            if(totalPages==0) {
                Long total = apipClient.getFcClientEvent().getResponseBody().getTotal();
                System.out.println("\nTotal: "+total+" items.");
                totalPages = total/Shower.DEFAULT_PAGE_SIZE +1 ;
            }
            if(exitWithoutValidCash(cashList)) return;
            Cash.showOrChooseCashList(cashList, title +" (page "+currentPage+"/"+totalPages+") :", mainFid, false, br);

            if(cashList.size()<Shower.DEFAULT_PAGE_SIZE){
                System.out.println("No more items.");
                Menu.anyKeyToContinue(br);
                return;
            }
            last.clear();
            last.addAll(apipClient.getFcClientEvent().getResponseBody().getLast());
            long left = totalPages-currentPage;
            if(askIfYes(br,"There are "+ left +" pages left. Stop?")) return;
            currentPage++;
        }
    }

    public void expense() {
        if(apipClient==null){
            System.out.println(APIP_CLIENT_IS_NULL);
            return;
        }
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(ISSUER).addNewValues(mainFid);
        fcdsl.addNewExcept().addNewTerms().addNewFields(OWNER).addNewValues(mainFid,OP_RETURN);
        fcdsl.addSort(SPEND_HEIGHT,DESC).addSort(ID,ASC);
        fcdsl.addSize(Shower.DEFAULT_PAGE_SIZE);
        requestAndShowCashList("Your expenses",fcdsl);
//        List<String> last = new ArrayList<>();
//        while(true){
//            if(!last.isEmpty()) fcdsl.addAfter(last);
//            List<Cash> cashList = apipClient.cashSearch(fcdsl,RequestMethod.POST,AuthType.FC_SIGN_BODY);
//            if(exitWithoutValidCash(cashList))return;
//            Cash.showCashList(cashList, "Your expense:", mainFid);
//            if(cashList.size()<Shower.DEFAULT_PAGE_SIZE)return;
//            last.clear();
//            last.addAll(apipClient.getFcClientEvent().getResponseBody().getLast());
//            long left = (apipClient.getFcClientEvent().getResponseBody().getTotal()-cashList.size())/Shower.DEFAULT_PAGE_SIZE;
//            if(askIfYes(br,"There are "+ left +" pages left. Stop?"))return;
//        }
    }
    public void rearrange() {
        System.out.println("You have " + cid.getCash() + " cashes.");
        List<Cash> validCashList ;
        if(askIfYes(br, "Do you want to rearrange all cashes?")){
            if(tooManyInputs(cid.getCash())){
                Menu.anyKeyToContinue(br);
                validCashList = showOrChooseItemList("Chose to rearrange...", null, Shower.DEFAULT_PAGE_SIZE, br, true, true);
            }else{
                validCashList = new ArrayList<>(localDB.getAll().values());
            }
    }else{
        validCashList = showOrChooseItemList("Chose to rearrange...",null,Shower.DEFAULT_PAGE_SIZE,br,true,true);
    }
    if(exitWithoutValidCash(validCashList))return;
    rearrange(validCashList);
   }

   public void rearrange(List<Cash> validCashList) {
    String choice = Inputer.chooseOne(
        new String[]{"All To One","Simple Split","Manual Arrange"},
        null, 
        "How to rearrange?", 
        br
    );
    
    if (choice != null) {
        switch (choice) {
            case "All To One" -> sendAllTo(validCashList, mainFid, null, br);
            case "Simple Split" -> simpleSplit(validCashList, br);
            case "Manual Arrange" -> manualMerge(validCashList, br);
        }
    }
}

    public void carve() {
        List<Cash> chosenCashes = showOrChooseItemList("Chose to carve...",null,Shower.DEFAULT_PAGE_SIZE,br,true,true);
        carve(chosenCashes);
    }

    public void carve(List<Cash> chosenCashes) {
        if(exitWithoutValidCash(chosenCashes))return;

        System.out.println("Input the message written on the blockchain. Enter to ignore:");
        String words = Inputer.inputStringMultiLine(br);
        if(words==null || words.equals(""))return;
        if(!askIfYes(br, "Do you sure to carve the words on chain. It's irreversible. "))return;
        String fid;
        fid = Inputer.inputFid(br, "Input the fid of the receiver. Enter to send to yourself:");

        if(fid==null||fid.equals(""))fid = mainFid;

        sendAllTo(chosenCashes,fid, words, br);
    }

    public void sendAllTo(List<Cash> cashList, String fid, String words, BufferedReader br) {
        List<SendTo> sendToList = new ArrayList<>();
        int wordsLength = 0;
        if(words!=null&&!"".equals(words))wordsLength = words.getBytes().length;
        long feeLong = TxCreator.calcFee(cashList==null?0:cashList.size(), 1, wordsLength,DEFAULT_FEE_RATE,false,null);
//        double feeCoin = utils.FchUtils.satoshiToCoin(feeLong);
        long sum = Cash.sumCashValue(cashList);
//        double sumCoin = utils.FchUtils.satoshiToCoin(sum);
        if(sum>=feeLong) {
            if (fid == null || fid.equals("")) fid = mainFid;
            sendToList.add(new SendTo(fid, utils.FchUtils.satoshiToCoin(sum-feeLong)));
        }
        sendAndUpdate(cashList, null, null, sendToList, words, DEFAULT_FEE_RATE, br);
        if(br!=null)Menu.anyKeyToContinue(br);
    }

    public void simpleSplit(List<Cash> cashList, BufferedReader br) {
        double cashAmountSum = Cash.sumCashAmount(cashList);
        System.out.println("The sum is "+cashAmountSum);
        String choice = Inputer.chooseOne(new String[]{"Count of cashes","Amount of each cash"},null,"Split by:",br);
        int count=0;
        Double amount = 0D;
        double rest = 0;
        if(choice.equals("Count of cashes")) {
            count = Inputer.inputInt(br, "Input the count of cashes you want:", MAX_CASH_SIZE);
            if(count==0)return;
            amount = NumberUtils.roundDouble8(cashAmountSum/count);

        }else {
            amount = Inputer.inputDouble(br, "Input the amount of the each cash:");
            if(amount==null)return;
            count = (int)(cashAmountSum/amount)+1;
        }

        List<SendTo> sendToList = new ArrayList<>();
        double paid = 0;
        for(int i=0;i<count-1;i++){
            sendToList.add(new SendTo(mainFid,amount));
            paid+=amount;
        }

        double restAmount = calcRestAmount(cashList.size(), cashAmountSum, paid, sendToList.size(), 0, DEFAULT_FEE_RATE, false, null);

        if(restAmount<0){
            System.out.println("The rest is not enough to pay the fee.");
            return;
        }
        sendToList.add(new SendTo(mainFid,restAmount));
        System.out.println("You are spending "+cashList.size()+" cashes and sending:");
        for(SendTo sendTo :sendToList){
            System.out.println("to "+sendTo.getFid()+" "+ sendTo.getAmount()+"f");
        }
        System.out.println("with the fee: "+ FchUtils.satoshiToCash(lastFee)+"c (1c = 100 sats; 1f = 1,000,000c)");
        if(askIfYes(br,"Send it?")) {
            sendAndUpdate(cashList, null, null, sendToList, null, DEFAULT_FEE_RATE, br);
            Menu.anyKeyToContinue(br);
        }
    }

    public void manualMerge(List<Cash> cashList, BufferedReader br) {
        if(exitWithoutValidCash(cashList))return;
        double cashSum = Cash.sumCashAmount(cashList);
        List<SendTo> sendToList = new ArrayList<>();
        double avaliable = cashSum;
        double rest = calcRestAmount(cashList.size(), avaliable, 0, 0, 0, DEFAULT_FEE_RATE, false, null);

        while (rest > 0) {
            System.out.printf("Remaining coins: %.8f FCH\n", rest);
            System.out.println("Enter the amount of the next cash. Enter to end:");
            String input = Inputer.inputString(br);

            if (input.isEmpty() && sendToList.size() > 0) {
                rest = calcRestAmount(cashList.size(), avaliable, 0, sendToList.size(), 0, DEFAULT_FEE_RATE, false, null);
                if(rest<0){
                    System.out.println("The rest is not enough to pay the fee.");
                    return;
                }
                sendToList.add(new SendTo(mainFid,rest));
                break;
            }

            try {
                double amount = Double.parseDouble(input);
                if (amount <= 0) {
                    System.out.println("Amount must be greater than 0");
                    continue;
                }
                if (amount > rest) {
                    System.out.println("Amount cannot be greater than remaining coins");
                    continue;
                }
                sendToList.add(new SendTo(mainFid,amount));

                avaliable -= amount;

                rest = calcRestAmount(cashList.size(),avaliable, 0, sendToList.size(), 0, DEFAULT_FEE_RATE, false, null);
                if (rest < 0.00000001) { // Handle potential floating point precision issues
                    break;
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid amount format. Please enter a valid number.");
            }
        }

        for(SendTo sendTo :sendToList){
            System.out.println("To "+sendTo.getFid()+" "+ sendTo.getAmount());
        }

        if(askIfYes(br,"Send it?")) {
            sendAndUpdate(cashList, null, null, sendToList, null, DEFAULT_FEE_RATE, br);
            Menu.anyKeyToContinue(br);
        }
    }

    public void sendTx() {
        sendTx(null);
    }  
    public void sendTx(@Nullable List<Cash> cashList){
        String sender = mainFid;

       System.out.println("Sender:  " + sender);
       
       if(cashList==null||cashList.isEmpty()) {
           cashList = showOrChooseItemList("Choose the cashes to spend",null,Shower.DEFAULT_PAGE_SIZE,br,true,true);
       }
       if(cashList==null || cashList.isEmpty())return;

       System.out.println("Available sum:  "+ utils.FchUtils.satoshiToCoin(cashList.stream().mapToLong(Cash::getValue).sum()));

       List<SendTo> sendToList=new ArrayList<>();
        List<String> fidList = getContactHandler().inputOrSearchFidList(br);
        if (fidList == null || fidList.isEmpty()) {
            System.out.println("No receiver. Cancel.");
            return;
        }
        for (String fid : fidList) {
            Double amount = Inputer.inputDouble(br, "Input the amount of the each cash. Enter to stop:");
            if (amount == null || amount == 0) continue;
            sendToList.add(new SendTo(fid, amount));
        }

       double sum = 0;
       if(!sendToList.isEmpty()){
        for (SendTo sendTo : sendToList) sum += sendTo.getAmount();
       }

       System.out.println("Input the message written on the blockchain. Enter to ignore:");
       String msg = Inputer.inputString(br);
        if(!sendToList.isEmpty()){
            System.out.println("\nSend to: ");
            for(SendTo sendTo:sendToList){
                System.out.println(sendTo.getFid()+" : "+sendTo.getAmount());
            }
            checkNobodys(br,sendToList,apipClient,null);
       }else System.out.println("No receiver.  All the change will be sent back to the sender.");
       if(msg!=null && !msg.isEmpty())System.out.println("Message: "+msg);
       System.out.println();

       sendAndUpdate(cashList, sum, 0L, sendToList, msg, DEFAULT_FEE_RATE, br);

        Menu.anyKeyToContinue(br);
   }

    private void sendAndUpdate(List<Cash> cashList, Double sum, Long cd, List<SendTo> sendToList, String msg, Double feeRate, BufferedReader br) {
        String result = send(cashList, sum, cd, sendToList, msg, null, feeRate, null, core.fch.FchMainNetwork.MAINNETWORK, br);
        sendResult(br, result);
    }

    public String sendResult(@Nullable BufferedReader br, String result) {
        if(result==null){
            System.out.println("Failed to send TX.");
            return null;
        }
        String typeName = result.substring(0,result.indexOf(":"));

        TxResultType resultType = TxResultType.fromString(typeName);
        switch (resultType) {
            case TX_ID -> showSendResult(result, "Sent Tx:", br);
            case UNSIGNED_JSON -> showSendResult(result, "Unsigned TX json. Import it into CryptoSign to sign it:", br);
            case TX_WITHOUT_INPUTS -> showSendResult(result, "Unsigned TX json without valid cashes. Complete it:", br);
            case SINGED_HEX -> showSendResult(result, "Signed TX in hex. Broadcast it:", br);
            case SIGNED_BASE64 -> showSendResult(result, "Signed TX in base64. Broadcast it:", br);
            case ERROR_STRING -> showSendResult(result, "Error:", br);
            case NULL -> System.out.println("Failed to send TX.");
        }
        return result;
    }

    private static void showSendResult(String result, String promote, @Nullable BufferedReader br) {
        System.out.println(promote);
        Shower.printUnderline(10);
        System.out.println(result);
        Shower.printUnderline(10);
        if(br!=null && askIfYes(br,"Show in QR codes?"))
            QRCodeUtils.generateQRCode(result);
    }


    /**
     * @param cashList      if null, choose cashes from cashFileMap.
     * @param amount        if null, input amount.
     * @param cd            if null, input cd.
     * @param sendToList    if null, input sendToList.
     * @param opReturn      if null, input opReturn.
     * @param br            if null, no input and prompt.
     * @return the sent txid, unsigned rawTx or null if failed.
     */
    public String send(@Nullable List<Cash> cashList, @Nullable Double amount, @Nullable Long cd, @Nullable List<SendTo> sendToList, @Nullable String opReturn, Multisign multisign, Double feeRate, String changeTo, MainNetParams mainNetParams, @Nullable BufferedReader br){
        TxResultType resultType = TxResultType.NULL;
        if(feeRate==null)feeRate=DEFAULT_FEE_RATE;
        List<Cash> meetList;
        int opReturnSize = opReturn == null ? 0 : opReturn.getBytes().length;
        if(cashList!=null)meetList = cashList;
        else {
            if(amount == null && sendToList!=null)amount = sendToList.stream().mapToDouble(SendTo::getAmount).sum();
            meetList = getCashesForSend(amount, cd, sendToList==null ? 0 : sendToList.size(), opReturnSize);
            if(meetList==null || meetList.isEmpty()){
                System.out.println("Can't get the valid cash list to make the TX");
                RawTxInfo rawTxInfo = new RawTxInfo(mainFid, null, sendToList, opReturn, cd, feeRate, multisign, "2");
                String result = rawTxInfo.toNiceJson();
                resultType= TxResultType.TX_WITHOUT_INPUTS;
                return resultType.addTypeAheadResult(result);
            }
        }

        boolean immatureRemoved = removeImmatureCashes(meetList, bestHeight);
        if(immatureRemoved){
            System.out.println("Some immature cashes have been removed.");
            if(br!=null && !askIfYes(br,"Continue?"))return resultType+"";
        }

        long destroyingCd = Cash.sumCashCd(meetList);
        if(cd!=null && destroyingCd < cd){
            String error = "The required CD is not enough:"+destroyingCd+" < "+cd;
            resultType = TxResultType.ERROR_STRING;
            return resultType.addTypeAheadResult(error);
        }

        System.out.println("Destroying CD:"+destroyingCd);

        if(br!=null && !askIfYes(br,"\nAre you sure to send?"))return resultType+"";


        Integer maxJumpNum = getMaxJumpNum(meetList);
        if(maxJumpNum>=Constants.MAX_JUMP_NUM){
            updateCashesIfOverJumped();
            String error = "The jump number is too large:"+ maxJumpNum;
            resultType = TxResultType.ERROR_STRING;
            return resultType.addTypeAheadResult(error);
        }
        String result;
        
        if(prikey ==null){
            RawTxInfo rawTxInfo = new RawTxInfo(mainFid, meetList, sendToList,opReturn,cd,feeRate, multisign,"2");
            resultType = TxResultType.UNSIGNED_JSON;
            result = rawTxInfo.toNiceJson();
        }else {
            String signedTx = signTx(meetList, sendToList, opReturn, multisign, feeRate, changeTo, mainNetParams);

            if (nasaClient != null) {
                result = nasaClient.sendRawTransaction(signedTx);
                if(Hex.isHexString(result))resultType = TxResultType.TX_ID;
                else resultType = TxResultType.ERROR_STRING;
            }
            else if (apipClient != null) {
                result = apipClient.broadcastTx(signedTx, RequestMethod.POST, AuthType.FC_SIGN_BODY);
                if(Hex.isHexString(result))resultType = TxResultType.TX_ID;
                else resultType = TxResultType.ERROR_STRING;
            }
            else {
                System.out.println("No way to broadcast the TX:");
                result = signedTx;
                resultType = TxResultType.SINGED_HEX;
                System.out.println(result);
                QRCodeUtils.generateQRCode(result);
            }
        }

        double restAmount = calcRestAmount(meetList, sendToList, opReturnSize, null, false, null);

        if(cid!=null) {
            updateLocalInfo(meetList,sendToList, maxJumpNum, result, restAmount);
        }
        maxJumpNum = getMaxJumpNum();
        if(maxJumpNum>=Constants.MAX_JUMP_NUM){
            updateCashesIfOverJumped();
        }
        Iterator<Cash> iterator = meetList.iterator();
        while(iterator.hasNext()){
            Cash cash = iterator.next();
            remove(cash.getId());
            iterator.remove();
        }

        return resultType.addTypeAheadResult(result);
    }

    private void updateLocalInfo(List<Cash> meetList, @org.jetbrains.annotations.Nullable List<SendTo> sendToList, Integer maxJumpNum, String result, double restAmount) {
        Long balance = cid.getBalance();
        if (balance == null) balance = localDB.getAll().values().stream().mapToLong(Cash::getValue).sum();
        Long cashCount = cid.getCash();
        if (cashCount == null) cashCount = localDB.getAll().values().stream().count();

        if (sendToList != null && Hex.isHex32(result)) {
            for (int i = 0; i < sendToList.size(); i++) {
                SendTo sendTo = sendToList.get(i);
                if (sendTo.getFid().equals(mainFid)) {
                    addNewSelfCash(result, i, sendTo.getAmount(), maxJumpNum);
                    cashCount++;
                } else {
                    balance -= FchUtils.coinToSatoshi(sendTo.getAmount());
                }
            }
        }

        if (restAmount > 0 && Hex.isHex32(result)) {
            int index = sendToList == null ? 0 : sendToList.size();
            balance += FchUtils.coinToSatoshi(restAmount);
            addNewSelfCash(result, index, restAmount, maxJumpNum);
            cashCount++;
        }
        if (cid == null) {
            cid = new Cid();
            cid.setId(mainFid);
        }
        cid.setBalance(balance);
        cid.setCash(cashCount-meetList.size());
    }

    public String signTx(@org.jetbrains.annotations.Nullable List<Cash> cashList, @org.jetbrains.annotations.Nullable List<SendTo> sendToList, @org.jetbrains.annotations.Nullable String opReturn, Multisign multisign, Double feeRate, String changeToFid, MainNetParams mainNetParams) {
        Transaction transaction = TxCreator.createUnsignedTx(cashList, sendToList, opReturn, multisign, feeRate, changeToFid, mainNetParams);
        if(transaction == null)return null;
        return TxCreator.signTx(prikey, transaction);
    }

    public double calcRestAmount(List<Cash> cashList, List<SendTo> sendToList, int opReturnSize, Double feeRate, boolean isMultiSign, Multisign multisign){
        if(feeRate==null)feeRate = DEFAULT_FEE_RATE;
        int sendSize=0;
        if(sendToList!=null)sendSize = sendToList.size();

        double cashAmountSum = Cash.sumCashAmount(cashList);
        double sendToAmountSum = 0;
        if(sendToList!=null){
            for(SendTo sendTo : sendToList){
                sendToAmountSum += sendTo.getAmount();
            }
        }

        return calcRestAmount(cashList.size(), cashAmountSum, sendToAmountSum, sendSize, opReturnSize, feeRate, isMultiSign, multisign);
    }

    public double calcRestAmount(int cashListSize, double cashAmountSum, double outPutSum, int sendToListSize, int opReturnSize, Double feeRate, boolean isMultiSign, Multisign multisign) {
        this.lastFee = TxCreator.calcFee(cashListSize, sendToListSize, opReturnSize, feeRate, isMultiSign, multisign);
        double feeCoin = utils.FchUtils.satoshiToCoin(lastFee);
        return NumberUtils.roundDouble8(cashAmountSum-feeCoin-outPutSum);
    }

//    public static double calcRestAmount(List<Cash> cashList, List<SendTo> sendToList, int opReturnSize, Double feeRate, boolean isMultiSign, P2SH p2sh) {
//        double cashAmountSum = Cash.sumCashAmount(cashList);
//
//        double sendToAmountSum = 0;
//        if(sendToList!=null){
//            for(SendTo sendTo : sendToList){
//                sendToAmountSum += sendTo.getAmount();
//            }
//        }
//
//        if(feeRate==null)feeRate = DEFAULT_FEE_RATE;
//        int sendSize=0;
//        if(sendToList!=null)sendSize = sendToList.size();
//
//        long feeLong = TxCreator.calcFee(cashList.size(), sendSize, opReturnSize, feeRate, isMultiSign, p2sh);
//
//        double feeCoin = FchUtils.satoshiToCoin(feeLong);
//        return NumberUtils.roundDouble8(cashAmountSum-feeCoin-sendToAmountSum);
//    }

    public long calcFee(List<Cash> cashList, List<SendTo> sendToList, int opReturnSize, Double feeRate, boolean isMultiSign, Multisign multisign){
        if(feeRate==null)feeRate = DEFAULT_FEE_RATE;
        int sendSize=0;
        if(sendToList!=null)sendSize = sendToList.size();

        this.lastFee = TxCreator.calcFee(cashList.size(), sendSize, opReturnSize, feeRate, isMultiSign, multisign);
        return this.lastFee;
    }

    @NotNull
    public Integer getMaxJumpNum() {
        int maxJumpNum = 0;
        for (Integer jumpNum : unsafeIdJumpNumMap.values()) {
            if (jumpNum != null && jumpNum > maxJumpNum) {
                maxJumpNum = jumpNum;
            }
        }
        return maxJumpNum;
    }
    public Integer getMaxJumpNum(List<Cash> cashList) {
        int maxJumpNum = 0;
        for (Cash cash : cashList) {
            Integer jumpNum = unsafeIdJumpNumMap.get(cash.getId());
            if (jumpNum!=null && jumpNum > maxJumpNum) maxJumpNum = jumpNum;
        }
        return maxJumpNum;
    }
    // public List<Cash> chooseCasheList(BufferedReader br) {
    //     int totalDisplayed = 0;
    //     List<Cash> finalCashes = new ArrayList<>();
    //     byte[] start = null;
    //     while(true){
    //         List<Cash> currentList = getListFromEnd(start, BATCH_SIZE);
    //         if(currentList==null || currentList.isEmpty())return null;
    //         totalDisplayed += currentList.size();
    //         List<Cash> chosenCashes = chooseCasheList(currentList, totalDisplayed, br);
    //         if(chosenCashes!=null && !chosenCashes.isEmpty()){
    //             finalCashes.addAll(chosenCashes);
    //             if(currentList.size()<BATCH_SIZE)break;
    //             start = currentList.get(currentList.size()-1).getId().getBytes();
    //         }else{
    //             return finalCashes;
    //         }
    //         if(!askIfYes(br,"Choose more?"))return finalCashes;    
    //     }
    //     return finalCashes;
    // }
    public List<Cash> chooseCasheList(List<Cash> currentList, int totalDisplayed, BufferedReader br) {
        List<Cash> chosenCashes = new ArrayList<>();

        String title = "Choose Cashes";
        Cash.showOrChooseCashList(currentList, title, mainFid, false, br);

        System.out.println("Enter cash numbers to select (comma-separated). 'a' to select all. Enter to ignore:");
        String input = Inputer.inputString(br);

        if ("".equals(input)) {
            return chosenCashes;
        }
        if (input.equals("a")) {
            chosenCashes.addAll(currentList);
            return chosenCashes;
        }

        String[] selections = input.split(",");
        for (String selection : selections) {
            try {
                int index = Integer.parseInt(selection.trim()) - 1;
                if (index >= 0 && index < totalDisplayed + currentList.size()) {
                    int listIndex = index - totalDisplayed;
                    chosenCashes.add(currentList.get(listIndex));
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input: " + selection);
            }
        }

        return chosenCashes;
    }

    /**
     * if amount and cd are null,return cashes for the tx fee and opreturn.
     * if amount is null, return cashes that can meet the cd.
     * if cd is null, return cashes that can meet the amount.
     * if amount and cd are not null, return cashes that can meet the amount and cd.
     */
    public List<Cash> getCashesForSend(@Nullable Double amount, @Nullable Long cd, int outputNum, int opReturnLength){
        List<Cash> meetList = new ArrayList<>();
        List<Cash> cashList = getListFromEnd( null, Constants.DEFAULT_DISPLAY_LIST_SIZE);
        long spendValue = 0L;
        if (amount != null) spendValue = utils.FchUtils.coinToSatoshi(amount);

        if (cd == null) {
            cd = 0L;
        }

        long totalValue = 0L;
        long totalCd = 0L;

        long fee = 0L;
        for (Cash cash : cashList) {
            totalValue += cash.getValue();
            if (cash.getCd() != null) totalCd += cash.getCd();
            meetList.add(cash);

            long txSize = TxCreator.calcTxSize(meetList.size(), outputNum, opReturnLength);
            fee = TxCreator.calcFee(txSize, DEFAULT_FEE_RATE);
            if (totalValue >= (spendValue + fee) && totalCd >= cd) {
                break;
            }
        }
        if (totalValue >= (spendValue + fee) && totalCd >= cd) {
            return meetList;
        } else {
            if (localDB.getSize() > Constants.DEFAULT_DISPLAY_LIST_SIZE) {
                System.out.println("You have more than " + Constants.DEFAULT_DISPLAY_LIST_SIZE + " cashes. Please merge them first or choose cashes manually.");
            }
            System.out.println("No cashes can meet the amount and cd.");
            return null;
        }
    }

    private Cash addNewSelfCash(String txId, int index, double amount, int maxJumpNum) {
        Cash cash = new Cash();
        cash.setBirthTxId(txId);
        cash.setBirthIndex(index);
        cash.setIssuer(mainFid);
        cash.setOwner(mainFid);
        cash.setValue(utils.FchUtils.coinToSatoshi(amount));
        cash.setValid(true);
        cash.makeId(txId, index);
        unsafeIdJumpNumMap.put(cash.getId(), maxJumpNum+1);
        localDB.put(cash.getId(), cash);
        return cash;
    }

    public void freshCashDB(){
        long lastHeight = localDB.getSize() == 0 ? 0 : getLongState(LAST_HEIGHT);
        if(lastHeight==0){
            freshValidCashes();
            return;
        }
        if(apipClient==null && nasaClient==null && esClient==null){
            log.error("Failed to fresh bestHeight due to the absence of apipClient, nasaClient, and esClient.");
            return;
        }
        bestHeight = Settings.getBestHeight(apipClient,nasaClient,esClient,null);

        if(bestHeight>lastHeight){
            if(apipClient!=null){
                cid = apipClient.cidInfoById(mainFid);
                if(cid ==null){
                    log.error("Failed to get cidInfo from apipClient when checkValidCashes.");
                }
                freshCashDBByApip(lastHeight);
            }else if(esClient!=null){
                try{    
                    Cid cid = EsUtils.getById(esClient,CID, mainFid, Cid.class);
                    if(cid!=null) this.cid = cid;
                    freshCashDBByEs(lastHeight);
                }catch (Exception e){
                    log.error("EsClient error:{}", e.getMessage());
                }
            }else {
                if (freshCashDBByNasaRpc()) return;
            }
        }
        freshCd();
        freshUnconfirmed();
//        if(br!=null)Menu.anyKeyToContinue(br);
    }

    public void freshCd() {
        if(dbEmpty())return;
        long sumCd=0;
        for(Cash cash:localDB.getAll().values()){
            cash.makeCd();
            if(cash.getCd()!=null)sumCd+=cash.getCd();
        }
        if(cid!=null)cid.setCd(sumCd);
    }

    private void showSummary() {
        if(cid==null)return;
        Shower.printUnderline(20);
        if(cid.getId()!=null)System.out.println("FID: "+cid.getId());
        if(cid.getCid()!=null)System.out.println("CID: "+cid.getCid());
        if(cid.getCash()!=null)System.out.println("Cashes: "+cid.getCash());
        if(cid.getBalance()!=null)System.out.println("Balance: "+FchUtils.satoshiToCoin(cid.getBalance())+" f");
        if(cid.getCd()!=null)System.out.println("CD: "+cid.getCd()+" cd");
        if(bestHeight!=0) System.out.println("FV date: "+ FchUtils.heightToMinuteDate(bestHeight));

        Shower.printUnderline(20);
    }

    private boolean freshCashDBByNasaRpc() {
        clearDB();
        ReplyBody replier = getCashListFromNasaNode(mainFid, null, true, nasaClient);
        if(replier.getCode()!=0){
            log.error("Failed to get cash list from nasaClient:{}", replier.getMessage());
            return true;
        }
        List<Cash> cashList = (List<Cash>) replier.getData();
        for (Cash cash : cashList) {
            localDB.put(cash.getId(), cash);
        }
        bestHeight = nasaClient.getBestHeight();
        localDB.putState(LAST_HEIGHT, bestHeight);
        System.out.println("Updated "+ cashList.size() + " cashes.");
        return false;
    }

    private void freshCashDBByEs(Long lastHeight) {
        int total = 0;
        SearchRequest.Builder sb = new SearchRequest.Builder();
        List<String> last = null;
        sb.index(CASH);
        sb.trackTotalHits(t -> t.enabled(true));
        sb.size(DEFAULT_DISPLAY_LIST_SIZE);
        List<SortOptions> sortOptionsList = Sort.makeTwoFieldsSort(LAST_HEIGHT,DESC,FieldNames.ID,ASC);
        sb.sort(sortOptionsList);
        Query.Builder qb = new Query.Builder();
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        // Add the owner must clause
        boolBuilder.must(new Query(new TermQuery.Builder()
            .field(FieldNames.OWNER)
            .value(mainFid)
            .build()));

        // Add height conditions if lastHeight is provided
        if (lastHeight != null) {
            RangeQuery.Builder lastHeightRb = new RangeQuery.Builder()
                .field(FieldNames.LAST_HEIGHT)
                .gt(JsonData.of(lastHeight));

            boolBuilder.must(
                new Query(lastHeightRb.build())
            );
        }

        qb.bool(boolBuilder.build());
        sb.query(qb.build());
        Long newLastHeight=null;
        while(true){
            if (last != null && !last.isEmpty())
                sb.searchAfter(last);

            SearchRequest searchRequest = sb.build();
            SearchResponse<Cash> result;
            try {
                result = esClient.search(searchRequest, Cash.class);
                if (result.hits() == null || result.hits().hits() == null || result.hits().hits().size() == 0) {
                    break;
                }
                List<String> newLast = null;
                List<Hit<Cash>> hits = result.hits().hits();
                for (Hit<Cash> hit : hits) {
                    Cash cash = hit.source();
                    if(cash==null)continue;;
                    newLastHeight = cash.getLastHeight();
                    newLast = hit.sort();
                    localDB.put(cash.getId(), cash);
                    total++;
                }
                if (newLast != null)
                    last = newLast;
                if(hits.size()< DEFAULT_DISPLAY_LIST_SIZE)break;
            } catch (IOException e) {
                log.error("EsClient error:{}", e.getMessage());
            }
        }
        if(newLastHeight!=null) {
            localDB.putState(LAST_HEIGHT, newLastHeight);
            bestHeight = newLastHeight;
        }
        System.out.println("Updated "+ total + " cashes.");
    }

    private void freshCashDBByApip(Long lastHeight) {
        int total = 0;
        List<String> last = (List<String>) localDB.getState(LAST);

        Fcdsl fcdsl = createFcdslForValidCashes(null, mainFid);

        if(last==null)fcdsl.getQuery().addNewRange().addNewFields(FieldNames.LAST_HEIGHT).addGt(String.valueOf(lastHeight));

        ReplyBody result;
        while(true){
            if(last!=null)fcdsl.setAfter(last);
            result = apipClient.general(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
            if(result==null || result.getCode()!=0){
                if(result==null)log.error("Failed to fresh cashes.");
                else {
                    if(result.getCode()== CodeMessage.Code1011DataNotFound){
                        System.out.println("No new items.");
                    }else log.error("Failed to fresh cashes:"+result.getMessage());
                }
                return;
            }
            List<Cash> cashList = ObjectUtils.objectToList(result.getData(),Cash.class);
            if(exitWithoutValidCash(cashList))break;
            for (Cash cash : cashList) {
                localDB.put(cash.getId(), cash);
                total++;
            }
            if(result.getLast()!=null)
                last = result.getLast();

            if(cashList.size()< DEFAULT_DISPLAY_LIST_SIZE)break;
        }
        if(result.getBestHeight()!=null){
            bestHeight =  result.getBestHeight();
            localDB.putState(LAST_HEIGHT, result.getBestHeight());
        }
        if(last!=null)localDB.putState(LAST,last);
        System.out.println("Updated "+ total + " cashes.");
    }

    public static List<Cash> getAllCashListByFids(List<String> fids, Boolean valid, Long sinceHeight, int size, ArrayList<Sort> sortList, List<String> last, ApipClient apipClient, NaSaRpcClient nasaClient, ElasticsearchClient esClient) {
        if (fids == null || fids.isEmpty()) {
            return new ArrayList<>();
        }

        // Try APIP client first
        if (apipClient != null) {
            Fcdsl fcdsl = new Fcdsl();
            fcdsl.addSize(size);
            fcdsl.addNewQuery().addNewTerms().addNewFields(OWNER).addNewValues(fids.toArray(new String[0]));
            if (valid != null) {
                fcdsl.getQuery().addNewTerms().addNewFields(VALID).addNewValues(valid.toString());
            }
            if (sinceHeight != null) {
                fcdsl.getQuery().addNewRange().addNewFields(FieldNames.LAST_HEIGHT).addGt(String.valueOf(sinceHeight));
            }
            if (sortList != null) {
                fcdsl.setSort(sortList);
            }
            if (last != null) {
                fcdsl.setAfter(last);
            }   
            List<Cash> cashList = apipClient.cashSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
            if (cashList != null && !cashList.isEmpty()) {
                return cashList;
            }
        }

        // Try Elasticsearch client second
        if (esClient != null) {
            try {
                ReplyBody replier = getAllCashListFromEs(fids, valid, sinceHeight, size, sortList, last, esClient);
                if (replier.getCode() == 0 && replier.getData() != null) {
                    return (List<Cash>) replier.getData();
                }
            } catch (Exception e) {
                log.error("Error getting cash list from ES: {}", e.getMessage());
            }
        }

        // Try NASA client last
        if (nasaClient != null) {
            ReplyBody replier = getCashListFromNasaNode(String.join(",", fids), "1", true, nasaClient);
            if (replier.getCode() == 0 && replier.getData() != null) {
                return (List<Cash>) replier.getData();
            }
        }

        // Return empty list if all attempts fail
        return new ArrayList<>();
    }

    public void freshValidCashes(){
        localDB.clear();
        if(mainFid ==null){
            log.error("myFid is null.");
            return;
        }
        if(apipClient==null && esClient==null && nasaClient==null){
            log.info("Failed to freshValidCashes because apipClient, esClient and nasaClient are null.");
            return;
        }
        long sumCd=0;
        List<Cash> cashList;
        if(apipClient!=null){
            cid = apipClient.cidInfoById(mainFid);
            if(cid ==null){
                log.error("Failed to get cidInfo from apipClient when checkValidCashes.");
            }

            cashList = pullAllValidFromApip(br,false);//getCashListFromApip(myFid, true, 200, null, null, bestHeight, apipClient);
            if(cashList.isEmpty()){
                log.error("Failed to get cash list from apipClient.");
                return;
            }

            for (Cash cash : cashList) {
                String id = cash.getId();  // Use the ID directly, don't convert with Hex.fromHex
                localDB.put(id, cash);
                if(cash.getCd()!=null)
                    sumCd+=cash.getCd();
                bestHeight = cash.getLastHeight();
            }
            localDB.putState(LAST_HEIGHT, bestHeight);
        }else if(esClient!=null){
            ReplyBody replier = getAllCashListFromEs(new ArrayList<>(Arrays.asList(mainFid)), true, null, DEFAULT_DISPLAY_LIST_SIZE, null, null, esClient);
            if(replier.getCode()!=0){
                log.error("Failed to get cash list from esClient:{}", replier.getMessage());
                return;
            }

            cashList = (List<Cash>) replier.getData();
            if(cashList==null || cashList.isEmpty()){
                log.error("Failed to get cash list from esClient.");
                return;
            }

            for (Cash cash : cashList) {
                String id = cash.getId();  // Use the ID directly, don't convert with Hex.fromHex
                localDB.put(id, cash);
                if(cash.getCd()!=null)
                    sumCd+=cash.getCd();
                bestHeight = cash.getLastHeight();
            }
            this.localDB.putState(LAST_HEIGHT, bestHeight);
        }else {
            ReplyBody replier = getCashListFromNasaNode(mainFid, "1", true, nasaClient);
            if(replier.getCode()!=0){
                log.error("Failed to get cash list from nasaClient:{}", replier.getMessage());
                return;
            }
            cashList = (List<Cash>) replier.getData();
            if(cashList==null || cashList.isEmpty()){
                log.error("Failed to get cash list from nasaClient.");
                return;
            }
            for (Cash cash : cashList) {
                String id = cash.getId();  // Use the ID directly, don't convert with Hex.fromHex
                localDB.put(id, cash);
            }
            bestHeight = nasaClient.getBestHeight();
            localDB.putState(LAST_HEIGHT, bestHeight);
        }
        if(!cashList.isEmpty())
            System.out.println("Got "+cashList.size() +" new cashes.");
        if(cid!=null)cid.setCd(sumCd);
        freshUnconfirmed();
    }

    @NotNull
    public static ReplyBody getAllCashListFromEs(List<String> fids, Boolean valid, Long afterHeight, int size,
                                                 ArrayList<Sort> sortList, List<String> last, ElasticsearchClient esClient) {
    ReplyBody finalReplier = new ReplyBody();
    List<Cash> allCashes = new ArrayList<>();
    long totalGot = 0;

    while (true) {
        ReplyBody replier = getCashListFromEs(fids, valid, afterHeight, size, sortList, last, esClient);
        
        if (replier.getCode() != 0) {
            // If there's an error, return the error replier
            return replier;
        }

        List<Cash> cashList = (List<Cash>)replier.getData();
        if (cashList == null || cashList.isEmpty()) {
            break;
        }

        allCashes.addAll(cashList);
        totalGot += cashList.size();

        // If we got less than requested size, we've reached the end
        if (cashList.size() < size) {
            break;
        }

        // Update last for pagination
        last = replier.getLast();
        if (last == null || last.isEmpty()) {
            break;
        }
    }

    // Set the final results
    finalReplier.set0Success(allCashes);
    finalReplier.setGot(totalGot);
    finalReplier.setTotal(totalGot);
    
    return finalReplier;
    }

    private void freshUnconfirmed() {
        if(mempoolHandler!=null){
            List<Cash> unconfirmedCashes = mempoolHandler.checkUnconfirmedCash(mainFid);
            if(unconfirmedCashes!=null){
                for(Cash cash : unconfirmedCashes){
                    if(cash.isValid())
                        localDB.put(cash.getId(), cash);
                }
            }
        }else if(nasaClient!=null){
            Map<String, TxHasInfo> txInMempoolMap = checkMempool(nasaClient, apipClient, esClient);
            for (Map.Entry<String, TxHasInfo> entry : txInMempoolMap.entrySet()) {
                TxHasInfo txHasInfo = entry.getValue();
                if(txHasInfo.getInCashList()!=null){
                    for (Cash cash : txHasInfo.getInCashList()) {
                        remove(cash.getId());
                    }
                }
                if(txHasInfo.getOutCashList()!=null){
                    for (Cash cash : txHasInfo.getOutCashList()) {
                        if(mainFid.equals(cash.getOwner())){
                            localDB.put(cash.getId(), cash);
                        }
                    }
                }
            }
        }else if(apipClient!=null){
            Map<String,List<Cash>> result = apipClient.unconfirmedCaches(RequestMethod.POST, AuthType.FC_SIGN_BODY, mainFid);
            if(result==null)return;
            List<Cash> unconfirmedCashes = result.get(mainFid);
            if(unconfirmedCashes!=null){
                for (Cash cash : unconfirmedCashes) {
                    if(mainFid.equals(cash.getOwner())){
                        if(cash.isValid())
                            localDB.put(cash.getId(), cash);
                        else
                            remove(cash.getId());
                    }
                }
            }
        }
    }

    /**
     * if cashList is null, get cashes from cashFileMap.
     * sendToList can be null.
     */
    public void updateCashesIfOverJumped(){
        Integer maxJumpNum = getMaxJumpNum();
        if(maxJumpNum.equals(MAX_JUMP_NUM)){
            List<Cash> cashes = getCashListFromApip(mainFid, true, 200, null, null, getLongState(LAST_HEIGHT), apipClient);
            if(cashes==null)return;
            for(Cash cash : cashes){
                String id = cash.getId();  // Use the ID directly, don't convert with Hex.fromHex
                localDB.put(id, cash);
                unsafeIdJumpNumMap.remove(id);
            }
            cid = apipClient.cidInfoById(mainFid);
        }
    }

    public List<Cash> getAllCashList(String fid, Boolean valid, ArrayList<Sort> sortList, List<String> last, BufferedReader br) {
        List<Cash> cashList = new ArrayList<>();
        int chosenSize = 0;

        ReplyBody replyBody;
        if (this.apipClient != null) {
            do {
                List<Cash> newCashList = getCashListFromApip(fid, valid, DEFAULT_DISPLAY_LIST_SIZE, sortList, last, null, apipClient);
                if (newCashList == null) {
                    log.debug(this.apipClient.getFcClientEvent().getMessage());
                    return cashList;
                }
                if (newCashList.isEmpty()) return cashList;

                if(br!=null){
                    List<Cash> chosenCashList = chooseCasheList(newCashList, chosenSize, br);
                    cashList.addAll(chosenCashList);
                    chosenSize += chosenCashList.size();
                    if(!askIfYes(br,"Do you want to continue to get cashes?")){
                        break;
                    }
                }else cashList.addAll(newCashList);

                replyBody = this.apipClient.getFcClientEvent().getResponseBody();
                last = replyBody.getLast();
            } while (cashList.size() < replyBody.getTotal());
        } else if (this.esClient != null) {
            do {
                replyBody = getCashListFromEs(new ArrayList<>(Arrays.asList(fid)), valid, null, DEFAULT_DISPLAY_LIST_SIZE, sortList, last, esClient);
                if (replyBody.getCode() != 0) {
                    log.debug(replyBody.getMessage());
                    break;
                }
                if (replyBody.getData() != null) {
                    List<Cash> newCashList = ObjectUtils.objectToList(replyBody.getData(), Cash.class);

                    if(br!=null){
                        List<Cash> chosenCashList = chooseCasheList(newCashList, chosenSize, br);
                        cashList.addAll(chosenCashList);
                        chosenSize += chosenCashList.size();
                        if(!askIfYes(br,"Do you want to continue to get cashes?")){
                            break;
                        }
                    }else cashList.addAll(newCashList);

                } else return cashList;
                last = ObjectUtils.objectToList(replyBody.getData(), String.class);//DataGetter.getStringList(fcReplier.getLast());
            } while (cashList.size() < replyBody.getTotal());
        } else if (this.nasaClient != null) {
            replyBody = getCashListFromNasaNode(fid, null, true, nasaClient);
            if (replyBody.getCode() != 0) {
                log.debug(replyBody.getMessage());
                return cashList;
            }
            if (replyBody.getData() != null){
                List<Cash> newCashList = ObjectUtils.objectToList(replyBody.getData(), Cash.class);
                if(br!=null){
                    List<Cash> chosenCashList = chooseCasheList(newCashList, chosenSize, br);
                    cashList.addAll(chosenCashList);
                    chosenSize += chosenCashList.size();
                }else cashList.addAll(newCashList);
            }else return cashList;
        }
        return cashList;
    }

    public List<Cash> getIssuingCashListFromJedis(String addr, JedisPool jedisPool) {
        List<Cash> issuingCashList = new ArrayList<>();
        Gson gson = new Gson();
        try (Jedis jedis3Mempool = jedisPool.getResource()) {
            jedis3Mempool.select(Constants.RedisDb3Mempool);
            String newCashIdStr = jedis3Mempool.hget(addr, FieldNames.NEW_CASHES);
            if (newCashIdStr != null) {
                String[] newCashIdList = gson.fromJson(newCashIdStr, String[].class);
                for (String cashId : newCashIdList) {
                    Cash cash = gson.fromJson(jedis3Mempool.hget(FieldNames.NEW_CASHES, cashId), Cash.class);
                    if (cash != null) issuingCashList.add(cash);
                }
            }
        }
        if (issuingCashList.size() == 0) return null;
        return issuingCashList;
    }

    public String[] getSpendingCashIdFromJedis(String addr, JedisPool jedisPool) {
        Gson gson = new Gson();
        try (Jedis jedis3Mempool = jedisPool.getResource()) {
            jedis3Mempool.select(Constants.RedisDb3Mempool);
            String spendCashIdStr = jedis3Mempool.hget(addr, FieldNames.SPEND_CASHES);
            if (spendCashIdStr != null) {
                return gson.fromJson(spendCashIdStr, String[].class);
            }
        }
        return null;
    }


    // Cash List Retrieval Methods
    public static List<Cash> getCashListFromApip(String fid, Boolean valid, int size,
                                          ArrayList<Sort> sortList, final List<String> last, Long sinceHeight, ApipClient apipClient) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addSize(size);
        if (sortList != null && !sortList.isEmpty())
            fcdsl.setSort(sortList);
        fcdsl.addNewQuery().addNewTerms().addNewFields(OWNER).addNewValues(fid);
        if(sinceHeight!=null)
            fcdsl.getQuery().addNewRange().addNewFields(LAST_HEIGHT).addGt(String.valueOf(sinceHeight));
        if (last != null && !last.isEmpty())
            fcdsl.setAfter(last);

        List<Cash> result;
        if(valid!=null ){
            if(valid)result = apipClient.cashValid(fcdsl,RequestMethod.POST,AuthType.FC_SIGN_BODY);
            else {
                fcdsl.addNewFilter().addNewTerms().addNewFields(FieldNames.VALID).addNewValues(Values.FALSE);
                result = apipClient.cashSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
            }
        }else {
            result = apipClient.cashSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        }
        if(result!=null && !result.isEmpty()){
            if(last!=null){
                last.clear();
                last.addAll(apipClient.getFcClientEvent().getResponseBody().getLast());
            }
        }
        return result;
    }

    @NotNull
    public static ReplyBody getCashListFromEs(List<String> fids, Boolean valid, Long afterHeight, int size,
                                              ArrayList<Sort> sortList, List<String> last, ElasticsearchClient esClient) {
        ReplyBody replier = new ReplyBody();
        SearchRequest.Builder sb = new SearchRequest.Builder();
        sb.index(CASH);
        sb.trackTotalHits(t -> t.enabled(true));
        if (size > 0) sb.size(size);
        else sb.size(DEFAULT_DISPLAY_LIST_SIZE);
        if (sortList != null && !sortList.isEmpty()) {
            List<SortOptions> sortOptionslist = Sort.getSortList(sortList);
            sb.sort(sortOptionslist);
        } else {
            List<SortOptions> sortOptionsList = Sort.makeTwoFieldsSort(LAST_HEIGHT, ASC, ID, ASC);
            sb.sort(sortOptionsList);
        }
        if (last != null && !last.isEmpty())
            sb.searchAfter(last);

        Query.Builder qb = new Query.Builder();
        if (afterHeight != null) {
            RangeQuery.Builder rb = new RangeQuery.Builder();
            if (valid != null && !valid) {
                rb.field(FieldNames.SPEND_HEIGHT);
            } else {
                rb.field(FieldNames.BIRTH_HEIGHT); 
            }
            rb.gt(JsonData.of(afterHeight));
            qb.range(rb.build());
        }
        BoolQuery.Builder bb = new BoolQuery.Builder();
        List<Query> queryList = new ArrayList<>();
        
        // Replace multiple Term queries with a single Terms query
        List<FieldValue> fieldValues= new ArrayList<>();
        for (String fid : fids) {
            fieldValues.add(FieldValue.of(fid));
        }
        TermsQuery tq = new TermsQuery.Builder()
            .field(OWNER)
            .terms(t -> t.value(fieldValues))
            .build();
        queryList.add(new Query(tq));

        if (valid!=null ){
            if(valid)queryList.add(new Query(new TermQuery.Builder().field(VALID).value(TRUE).build()));
            else queryList.add(new Query(new TermQuery.Builder().field(VALID).value(FALSE).build()));
        }

        bb.must(queryList);

        qb.bool(bb.build());
        sb.query(qb.build());

        SearchRequest searchRequest = sb.build();
        try {

            SearchResponse<Cash> result;

            result = esClient.search(searchRequest, Cash.class);

            if (result.hits() == null || result.hits().hits() == null) {
                log.error("Failed to get Hits from esClient.");
                replier.setOtherError("Failed to get Hits from esClient.");
            } else if (result.hits().hits().size() == 0) {
                replier.set0Success();
                replier.setGot(0L);
                replier.setTotal(0L);
            } else {
                List<String> newLast = null;
                List<Cash> cashList = new ArrayList<>();
                for (Hit<Cash> hit : result.hits().hits()) {
                    cashList.add(hit.source());

                    newLast = hit.sort();
                }
                replier.set0Success(cashList);
                replier.setGot((long) cashList.size());
                replier.setTotal((long) result.hits().hits().size());
                if (newLast != null)
                    replier.setLast(newLast);
            }
        } catch (IOException e) {
            log.error("EsClient error:{}", e.getMessage());
            replier.setOtherError("EsClient error:" + e.getMessage());
        }
        return replier;
    }
    
    public static ReplyBody getCashListFromNasaNode(String fid, String minConf,
                                                    boolean includeUnsafe, NaSaRpcClient naSaRpcClient) {
        UTXO[] utxos = new NaSaRpcClient(naSaRpcClient.getUrl(), naSaRpcClient.getUsername(), naSaRpcClient.getPassword()).listUnspent(fid, minConf, includeUnsafe);
        List<Cash> cashList = new ArrayList<>();
        for (UTXO utxo : utxos) {
            Cash cash = new Cash();
            cash.setOwner(utxo.getAddress());
            cash.setBirthTxId(utxo.getTxid());
            cash.setBirthIndex(utxo.getVout());
            cash.setValue(utils.FchUtils.coinToSatoshi(utxo.getAmount()));
            cash.setLockScript(utxo.getRedeemScript());
            cashList.add(cash);
        }
        ReplyBody replyBody = new ReplyBody();
        replyBody.set0Success();
        replyBody.setData(cashList);
        return replyBody;
    }

    // Unconfirmed Transaction Methods
    public static void checkUnconfirmed(List<Cash> meetList, String fid, MempoolManager mempoolHandler, ApipClient apipClient) {
        if(mempoolHandler!=null)mempoolHandler.updateUnconfirmedValidCash(meetList,fid);
        if(apipClient!=null)apipClient.updateUnconfirmedValidCash(meetList,fid);
    }

    public static void checkUnconfirmedSpentByJedis(List<Cash> cashList, Jedis jedis) {
        jedis.select(Constants.RedisDb3Mempool);
        cashList.removeIf(cash -> jedis.hget(FieldNames.SPEND_CASHES, cash.getId()) != null);
        jedis.select(0);
    }

    public static List<Cash> checkUnconfirmedNewCashesByJedis(String fid,Jedis jedis){
        Gson gson = new Gson();
        List<Cash> cashList = new ArrayList<>();
        String cashIdListStr = jedis.hget(fid, NEW_CASHES);
        if(cashIdListStr==null)return null;
        List<String> cashIdList = ObjectUtils.objectToList(cashIdListStr,String.class);
        if(cashIdList==null|| cashIdList.isEmpty())return null;
        for(String cashId:cashIdList){
            String cashStr = jedis.hget(FieldNames.NEW_CASHES,cashId);
            Cash cash = gson.fromJson(cashStr, Cash.class);
            if(cash!=null)cashList.add(cash);
        }
        return cashList;
    }
    private List<Cash> pullAllValidFromApip() {
        return pullAllValidFromApip(null,false);
    }

    public List<Cash> chooseFromAllFromApip() {
        return pullAllValidFromApip(br,true);
    }

    private List<Cash> pullAllValidFromApip(@Nullable BufferedReader br, boolean choose) {
         List<Cash> finalCashes = new ArrayList<>();
         List<Cash> subCashList;
         int totalDisplayed = 0;
         int got = 0;

        Fcdsl fcdsl = createFcdslForValidCashes(null, mainFid);

        List<String> last = null;

         while(true){
             if(last!=null){
                 fcdsl.setAfter(last);
             }
             ReplyBody replyBody = apipClient.general(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
             if(replyBody ==null)break;
             last = replyBody.getLast();
             subCashList = ObjectUtils.objectToList(replyBody.getData(), Cash.class);
             if(subCashList == null)break;
             got += subCashList.size();

             if(choose && br!=null){
                 List<Cash> result = chooseCasheList(subCashList, totalDisplayed, br);
                 if(result.size()==0)break;
                 finalCashes.addAll(result);
                 if(askIfYes(br, "Continue to pull more cashes?")){
                     totalDisplayed += subCashList.size();
                     if(got<BATCH_SIZE)break;
                     if(finalCashes.size()>MAX_CASH_SIZE){
                         System.out.println("Warning: You have pulled more than "+MAX_CASH_SIZE+" cashes. It's hard to deal with so many cashes.");
                         if(askIfYes(br, "Continue?"))return finalCashes;
                     }
                     continue;
                 }else{
                     Cash.showOrChooseCashList(finalCashes,"Chosen Cashes", mainFid, false, br);
                     return finalCashes;
                 }
             }else{
                 System.out.println("Got "+subCashList.size()+" cashes.");
                 finalCashes.addAll(subCashList);
                 totalDisplayed += subCashList.size();
             }

             if(got<BATCH_SIZE)break;
             if(finalCashes.size()>MAX_CASH_SIZE){
                 System.out.println("Warning: You have pulled "+finalCashes.size()+" cashes. ");
                 if(br!=null && !askIfYes(br, "Continue?"))return finalCashes;
             }
         }
         return finalCashes;
     }

    @NotNull
    private static Fcdsl createFcdslForValidCashes(Long sinceHeight, String fid) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addIndex(CASH);
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.OWNER).addNewValues(fid);
        fcdsl.addNewFilter().addNewTerms().addNewFields(FieldNames.VALID).addNewValues(Values.TRUE);
        if(sinceHeight !=null)fcdsl.getQuery().addNewRange().addNewFields(LAST_HEIGHT).addGt(String.valueOf((Long) null));
        fcdsl.addSort(LAST_HEIGHT, ASC).addSort(FieldNames.ID, ASC);
        fcdsl.addSize(BATCH_SIZE);
        return fcdsl;
    }

    public static Boolean isImmature(Cash cash, Long bestHeight) {
        if(bestHeight==null)return null;
        if(! COINBASE.equals(cash.getIssuer()))return false;

        if(cash.getOwner().equals(Constants.FUND_FID)){
            if( (bestHeight - cash.getBirthHeight()) < FUND_MATURE_DAYS_INT)return true;
        }

        return bestHeight - cash.getBirthHeight() < MINE_MATURE_DAYS_INT;
    }

    public static boolean removeImmatureCashes(List<Cash> cashList, Long bestHeight){
        boolean removed = false;
        Iterator<Cash> iterator = cashList.iterator();
        while (iterator.hasNext()) {
            Cash cash = iterator.next();
            if (isImmature(cash, bestHeight)) {
                iterator.remove();
                removed = true;
            }
        }
        return removed;
    }

    public static SearchResult<Cash> getValidCashes(String myFid, Long amount, Long cd, Long sinceHeight, int outputSize, int msgSize, ApipClient apipClient, ElasticsearchClient esClient, MempoolManager mempoolHandler) {
        SearchResult<Cash> searchResult = new SearchResult<>();
        List<Cash> cashList = null;
        Long bestheight = null;
        if(apipClient!=null)bestheight = apipClient.getBestHeight();
        if(esClient!=null) {
            try {
                bestheight = EsUtils.getBestBlock(esClient).getHeight();
            } catch (IOException ignore) {
            }
        }
        if(apipClient!=null){
            Fcdsl fcdsl = createFcdslForValidCashes(sinceHeight, myFid);
            cashList = apipClient.cashSearch(fcdsl, RequestMethod.POST,AuthType.FC_SIGN_BODY);
            if(cashList==null || cashList.isEmpty()){
                searchResult.setMessage("Can't get cashes. Check APIP.");
                return searchResult;
            }

            try{
                searchResult.setTotal(apipClient.getFcClientEvent().getResponseBody().getTotal());
            }catch (Exception ignore){}

            if (amount == null && cd == null) {
                checkUnconfirmed(cashList, myFid, mempoolHandler, apipClient);
                searchResult.setData(cashList);
                searchResult.setGot((long) cashList.size());
                return searchResult;
            }
        }else if(esClient!=null){
            cashList = new ArrayList<>();
            SearchResponse<Cash> result;
            try {
                SearchRequest.Builder searchBuilder = new SearchRequest.Builder();
                searchBuilder.index(CASH)
                        .trackTotalHits(tr -> tr.enabled(true))
                        .sort(s1 -> s1.field(f -> f.field(FieldNames.CD).order(SortOrder.Asc)))
                        .sort(s2 -> s2.field(f -> f.field(FieldNames.ID).order(SortOrder.Asc)))
                        .size(200);

                BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
                TermQuery ownerQuery = new TermQuery.Builder().field(OWNER).value(myFid).build();
                TermQuery validQuery = new TermQuery.Builder().field(FieldNames.VALID).value(true).build();
                if (sinceHeight != null) {
                    RangeQuery heightQuery = new RangeQuery.Builder()
                            .field(BIRTH_HEIGHT)
                            .gt(JsonData.of(sinceHeight))
                            .build();
                    boolQueryBuilder.must(new Query(heightQuery));
                }
                boolQueryBuilder.must(new Query(ownerQuery))
                        .must(new Query(validQuery));

                searchBuilder.query(q -> q.bool(boolQueryBuilder.build()));
                result = esClient.search(searchBuilder.build(), Cash.class);
            } catch (IOException e) {
                searchResult.setMessage("Can't get cashes. Check ES.");
                return searchResult;
            }

            if (result == null) {
                searchResult.setMessage("Can't get cashes. Check ES.");
                return searchResult;
            }


            List<Hit<Cash>> hitList = result.hits().hits();
            if (hitList.size() == 0) {
                searchResult.setMessage("No cashes found.");
                return searchResult;
            }

            for (Hit<Cash> hit : hitList) {
                cashList.add(hit.source());
            }
            if (amount == null && cd == null) {
                checkUnconfirmed(cashList, myFid, mempoolHandler, apipClient);
                searchResult.setData(cashList);
                searchResult.setGot((long) cashList.size());
                try{
                    searchResult.setTotal(result.hits().total().value());
                }catch (Exception ignore){}
                return searchResult;
            }
        }

        if(cashList==null || cashList.isEmpty()){
            searchResult.setMessage("Can't get cashes. Check APIP.");
            return searchResult;
        }

        checkUnconfirmed(cashList,myFid, mempoolHandler, apipClient);

        amount = amount == null ? 0L : amount;
        cd = cd == null ? 0L : cd;
        long fchSum = 0;
        long cdSum = 0;
        long fee = 0;
        List<Cash> meetList = new ArrayList<>();

        for (Cash cash : cashList) {
            if(Boolean.TRUE.equals(isImmature(cash, bestheight)))continue;
            long cdd=0;
            if(cash.getBirthTime()!=null) {
                cdd = utils.FchUtils.cdd(cash.getValue(), cash.getBirthTime(), System.currentTimeMillis()/1000);
            }
            fchSum += cash.getValue();
            cdSum += cdd;
            meetList.add(cash);
            long txSize = TxCreator.calcTxSize(meetList.size(),outputSize,msgSize);
            fee = TxCreator.calcFee(txSize,DEFAULT_FEE_RATE);
            if (fchSum >= (amount+fee) && cdSum >= cd) {
                break;
            }
        }

        if (fchSum < (amount+fee) || cdSum < cd) {
            searchResult.setMessage("Can't get enough amount or cd within 200 cashes. You may need to merge cashes.");
            return searchResult;
        }

        searchResult.setData(meetList);
        searchResult.setGot((long) meetList.size());
        return searchResult;
    }

    // Helper Classes
    public static class SearchResult<T> {
        private List<T> data;
        private String message;
        private Long got;
        private Long total;
        private Long lastHeight;
        private Long lastTime;

        public SearchResult() {
            this.data = new ArrayList<>();
        }

        // Getters and setters
        public List<T> getData() { return data; }
        public void setData(List<T> data) { this.data = data; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Long getGot() { return got; }
        public void setGot(Long got) { this.got = got; }
        public Long getTotal() { return total; }
        public void setTotal(Long total) { this.total = total; }

        public boolean hasError() {
            return message != null && !message.isEmpty();
        }

        public Long getLastHeight() {
            return lastHeight;
        }

        public void setLastHeight(Long lastHeight) {
            this.lastHeight = lastHeight;
        }

        public Long getLastTime() {
            return lastTime;
        }

        public void setLastTime(Long lastTime) {
            this.lastTime = lastTime;
        }
    }

    // Sample Data Methods
    public static Cash createSampleCash() {
        Cash cash = new Cash();
        cash.setOwner("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");  // Example FID
        cash.setBirthTxId("6b86b273ff34fce19d6b804eff5a3f5747ada4eaa22f1d49c01e52ddb7875b4b");  // Example transaction ID
        cash.setBirthIndex(0);
        cash.setValue(100000000L);  // 1 FCH in satoshis
        cash.setId(Cash.makeCashId(cash.getBirthTxId(), cash.getBirthIndex()));
        cash.setBirthTime(System.currentTimeMillis() / 1000);  // Current Unix timestamp
        cash.setBirthHeight(100000L);  // Example block height
        cash.setValid(true);
        
        return cash;
    }

    public String getMainFid() {
        return mainFid;
    }

    public void setMainFid(String mainFid) {
        this.mainFid = mainFid;
    }

    /**
     * Instance method that creates a transaction with an OP_RETURN message and optional CD requirement
     * @param opReturn The message to be written to the blockchain
     * @param cd The minimum CD requirement (can be null)
     * @return The transaction ID if successful, null otherwise
     */
    public String carve(String opReturn, Long cd) {
        return carve(opReturn, cd, this.prikey, this.apipClient);
    }
    /**
     * Creates a transaction with an OP_RETURN message and optional CD requirement
     * @param opReturn The message to be written to the blockchain
     * @param cd The minimum CD requirement (can be null)
     * @param priKey The private key for signing the transaction
     * @param apipClient The APIP client for broadcasting the transaction
     * @return The transaction ID if successful, null otherwise
     */
    public static String carve(String opReturn, Long cd, byte[] priKey, ApipClient apipClient) {
        if (opReturn == null || opReturn.isEmpty()) {
            return null;
        }

        String fid = KeyTools.prikeyToFid(priKey);

        // Get valid cashes that meet the CD requirement
        SearchResult<Cash> searchResult = getValidCashes(
            fid, 
            null, 
            cd, 
            null, 
            1, 
            opReturn.getBytes().length, 
            apipClient, 
            null, null);

        if (searchResult.hasError() || searchResult.getData().isEmpty()) {
            return null;
        }

        List<Cash> cashList = searchResult.getData();
        
        // Create and sign the transaction
        Transaction unSignedTxBytes = TxCreator.createUnsignedTx(
            cashList, 
            null, 
            opReturn,
            null, 
            DEFAULT_FEE_RATE, null, FchMainNetwork.MAINNETWORK
        );

        if (unSignedTxBytes == null) {
            return null;
        }

        String signedTx = TxCreator.signTx(priKey, unSignedTxBytes);

        // Broadcast the transaction
        return apipClient.broadcastTx(signedTx, RequestMethod.POST, AuthType.FC_SIGN_BODY);
    }

    /**
     * Static method to send FCH to one or more recipients
     *
     * @param priKey        The private key for signing the transaction
     * @param sendToList    List of recipients and amounts
     * @param apipClient    APIP client for blockchain interaction (required)
     * @param esClient      Optional ES client for backup cash lookup
     * @return Transaction ID if successful, null if failed
     */
    public static String send(byte[] priKey, List<SendTo> sendToList, ApipClient apipClient, @Nullable ElasticsearchClient esClient, NaSaRpcClient naSaRpcClient) {
        if (priKey == null || sendToList == null || sendToList.isEmpty()) {
            return null;
        }

        String fid = KeyTools.prikeyToFid(priKey);

        // Calculate total amount needed
        double totalAmount = sendToList.stream()
                .mapToDouble(SendTo::getAmount)
                .sum();

        // Get valid cashes that meet the amount requirement
        SearchResult<Cash> searchResult = getValidCashes(
            fid, 
            utils.FchUtils.coinToSatoshi(totalAmount),
            null, 
            null, 
            sendToList.size(), 
            0, 
            apipClient, 
            esClient, null);

        if (searchResult.hasError() || searchResult.getData().isEmpty()) {
            return null;
        }

        List<Cash> cashList = searchResult.getData();

        // Create and sign the transaction
        Transaction transaction = TxCreator.createUnsignedTx(
            cashList, 
            sendToList,
                null,
            null, 
            DEFAULT_FEE_RATE, null, FchMainNetwork.MAINNETWORK
        );

        if (transaction == null) {
            return null;
        }

        String signedTx = TxCreator.signTx( priKey,transaction);

        // Broadcast the transaction
        if(apipClient!=null)
            return apipClient.broadcastTx(signedTx, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        else if(naSaRpcClient!=null) {
            return naSaRpcClient.sendRawTransaction(signedTx);
        } else{
            return signedTx;
        }
    }


    /**
     * Gets a list of Cash objects from the end of the database
     * @param start starting point
     * @param batchSize number of items to retrieve
     * @return list of Cash objects
     */
    public List<Cash> getListFromEnd(byte[] start, int batchSize) {
        if (start == null) {
            return localDB.getList(batchSize, null, null, false, null, null, true, true);
        } else {
            String startId = Hex.toHex(start);
            return localDB.getList(batchSize, startId, null, false, null, null, true, true);
        }
    }

//    @Override
//    public List<Cash> showOrChooseItemList(String promote, @Nullable List<Cash> itemList, Integer sizeInPage, @Nullable BufferedReader br, boolean isFromEnd, boolean choose) {
//        if (itemList != null) {
//            return Cash.showAndChooseCashList(itemList, promote, 0, mainFid, br);
//        }
//
//        List<Cash> chosenItems = new ArrayList<>();
//        Long fromIndex = null;
//        int totalDisplayed = 0;
//
//        while (true) {
//            List<Cash> batchItemList = getItemList(sizeInPage, fromIndex, null, false, null, null, true, isFromEnd);
//
//            if (batchItemList.isEmpty()) break;
//
//            try {
//                fromIndex = localDB.getTempIndex();
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
//
//            List<Cash> result = Cash.showAndChooseCashList(batchItemList, promote, totalDisplayed, mainFid, br);
//
//            chosenItems.addAll(result);
//
//            if(batchItemList.size()<sizeInPage)break;
//            totalDisplayed += batchItemList.size();
//            if(br!=null && !Inputer.askIfYes(br,"List more?"))break;
//        }
//
//        return chosenItems;
//    }
}
