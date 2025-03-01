package handlers;

import fch.fchData.Cid;
import apip.apipData.Fcdsl;
import apip.apipData.Sort;
import appTools.Inputer;
import appTools.Menu;
import appTools.Settings;
import appTools.Shower;
import clients.ApipClient;
import clients.Client;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;

import com.google.gson.Gson;
import constants.Constants;
import constants.FieldNames;
import constants.IndicesNames;
import constants.Values;
import crypto.KeyTools;
import fch.ParseTools;
import fch.TxCreator;
import fch.Wallet;
import fch.fchData.*;
import feip.feipData.Service;
import nasa.NaSaRpcClient;
import nasa.data.UTXO;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import fcData.ReplyBody;
import tools.*;
import tools.http.AuthType;
import tools.http.RequestMethod;

import javax.annotation.Nullable;

import static appTools.Inputer.askIfYes;
import static constants.Constants.*;
import static constants.FieldNames.*;
import static constants.IndicesNames.CASH;
import static constants.IndicesNames.CID;
import static constants.Strings.ASC;
import static constants.Strings.DESC;
import static constants.Values.*;
import static fch.TxCreator.DEFAULT_FEE_RATE;

public class CashHandler extends Handler<Cash> {
    private static final Logger log = LoggerFactory.getLogger(CashHandler.class);
    private static final int BATCH_SIZE = 50;
    private final Map<String, Integer> unsafeIdJumpNumMap;
    private final ApipClient apipClient;
    private final NaSaRpcClient nasaClient;
    private final ElasticsearchClient esClient;
    private final MempoolHandler mempoolHandler;

    private String myFid;
    private Cid cid;
    private final byte[] priKey;
    private final BufferedReader br;
    private long bestHeight;

    private final PersistentSequenceMap cashDB;
    private Long lastFee;

    public CashHandler(String myFid, String myPriKeyCipher, byte[] symKey,
                       ApipClient apipClient, NaSaRpcClient nasaClient, String blockDir, ElasticsearchClient esClient, String dbPath,
                       BufferedReader br) {
        this.apipClient = apipClient;
        this.nasaClient = nasaClient;
        this.esClient = esClient;
        if(nasaClient!=null)this.mempoolHandler = new MempoolHandler(nasaClient, apipClient, esClient, blockDir);
        else this.mempoolHandler = null;
        this.myFid = myFid;
        this.priKey = Client.decryptPriKey(myPriKeyCipher,symKey);
        this.br =br;
        unsafeIdJumpNumMap=new HashMap<>();
        this.cashDB = new PersistentSequenceMap(myFid,null, CASH, dbPath);
        freshCashDB();
    }

    public CashHandler(Settings settings){
        this.apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);
        this.nasaClient = (NaSaRpcClient) settings.getClient(Service.ServiceType.NASA_RPC);
        this.esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        if(nasaClient!=null)this.mempoolHandler = new MempoolHandler(settings);
        else this.mempoolHandler = null;
        this.myFid = settings.getMainFid();
        this.priKey = Client.decryptPriKey(settings.getMyPriKeyCipher(),settings.getSymKey());
        this.br = settings.getBr(); 
        unsafeIdJumpNumMap=new HashMap<>();
        this.cashDB = new PersistentSequenceMap(settings.getMainFid(),null, CASH, settings.getDbDir());
        freshCashDB();
    }

    public void resetCashDB(){
        this.cashDB.clear();
        freshCashDB();
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


    public void menu() {
        Menu menu = new Menu("Cash Management", this::close);
        menu.add("View Valid", this::show);
        menu.add("Cash Detail", this::cashDetail);
        menu.add("Incomes", this::incomes);
        menu.add("Expense", this::expense);
        menu.add("Reload All", this::freshValidCashes);
        menu.add("Fresh", this::freshCashDB);
        menu.add("Rearrange", this::rearrange);
        menu.add("Send", this::sendTx);
        menu.add("Carve", this::carve);
        menu.showAndSelect(br);
    }

    public void show() {
        if(cashDB.isEmpty()){
            System.out.println("No cashes.");
            return;
        }
        byte[] start = null;
        int total = 0;
        while(true) {
            List<Cash> cashList = cashDB.getListFromEnd(start, BATCH_SIZE, Cash::fromBytes);
            if(cashList==null || cashList.isEmpty())return;
            total += cashList.size();
            Cash.showCashList(cashList, "Your cashes", total, myFid);
            if(cashList.size()<BATCH_SIZE)return;
            if(!askIfYes(br,"Continue?"))return;
            start = cashList.get(cashList.size()-1).getId().getBytes();
        }
    }

    private void cashDetail() {
        String cashId = Inputer.inputString(br, "Input the cash ID:");
        if(cashId == null)return;

        byte[] cashBytes = cashDB.get(Hex.fromHex(cashId));
        if(cashBytes==null){
            System.out.println("It's not cashId.");
            return;
        }
        Cash cash = Cash.fromBytes(cashBytes);
        if(cash==null){
            Map<String,Cash> cashMap = apipClient.cashByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY,cashId);
            if(cashMap==null || cashMap.isEmpty())return;
            cash = cashMap.get(cashId);
        }
        if(cash==null){
            System.out.println("Cash not found.");
            return;
        }
        String niceCashStr = JsonTools.toNiceText(JsonTools.toJson(cash),0);
        Shower.printUnderline(20);
        System.out.println(niceCashStr);
        Shower.printUnderline(20);
    }

    public void incomes() {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(OWNER).addNewValues(myFid);
        fcdsl.addNewExcept().addNewTerms().addNewFields(ISSUER).addNewValues(myFid,OP_RETURN);
        fcdsl.addSort(BIRTH_HEIGHT,DESC).addSort(CASH_ID,ASC);
        fcdsl.addSize(BATCH_SIZE);
        List<String> last = new ArrayList<>();
        int count = 0;
        while(true){
            if(!last.isEmpty()) fcdsl.addAfter(last);
            List<Cash> cashList = apipClient.cashSearch(fcdsl,RequestMethod.POST,AuthType.FC_SIGN_BODY);
            if(cashList==null || cashList.isEmpty())return;
            Cash.showCashList(cashList, "Your incomes:", count, myFid);
            if(cashList.size()<BATCH_SIZE)return;
            count += cashList.size();
            last.clear();
            last.addAll(apipClient.getFcClientEvent().getResponseBody().getLast());
            if(!askIfYes(br,"Continue?"))return;
        }
    }

    public void expense() {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(ISSUER).addNewValues(myFid);
        fcdsl.addNewExcept().addNewTerms().addNewFields(OWNER).addNewValues(myFid,OP_RETURN);
        fcdsl.addSort(SPEND_HEIGHT,DESC).addSort(CASH_ID,ASC);
        fcdsl.addSize(BATCH_SIZE);
        List<String> last = new ArrayList<>();
        int count = 0;
        while(true){
            if(!last.isEmpty()) fcdsl.addAfter(last);
            List<Cash> cashList = apipClient.cashSearch(fcdsl,RequestMethod.POST,AuthType.FC_SIGN_BODY);
            if(cashList==null || cashList.isEmpty())return;
            Cash.showCashList(cashList, "Your expense:", count, myFid);
            if(cashList.size()<BATCH_SIZE)return;
            count += cashList.size();
            last.clear();
            last.addAll(apipClient.getFcClientEvent().getResponseBody().getLast());
            if(!askIfYes(br,"Continue?"))return;
        }
    }
   public void rearrange() {
    List<Cash> validCashList = chooseCasheList(br);
    if(validCashList==null || validCashList.isEmpty())return;
    rearrange(validCashList);
   }
   public void rearrange(List<Cash> validCashList) {
    String tx = null;
    double sumCoin = ParseTools.satoshiToCoin(Cash.sumCashValue(validCashList));
    String choice = Inputer.chooseOne(
        new String[]{"All To One","Batch then Rest","Manual Input"}, 
        null, 
        "How to merge?", 
        br
    );
    
    if (choice != null) {
        switch (choice) {
            case "All To One" -> tx = swapAll(validCashList, null);
            case "Batch then Rest" -> tx = batchMerge(validCashList, sumCoin, br);
            case "Manual Input" -> tx = manualMerge(validCashList, br);
        }
        
        if (tx != null) {
            String result = apipClient.broadcastTx(tx, RequestMethod.POST, AuthType.FC_SIGN_BODY);
            System.out.println("Sent: " + result);
            Menu.anyKeyToContinue(br);
        }
    }
}

    public void carve() {
        List<Cash> chosenCashes = chooseCasheList(br);
        if(chosenCashes==null || chosenCashes.isEmpty())return;
        carve(chosenCashes);
    }

    public void carve(List<Cash> chosenCashes) {
        System.out.println("Input the message written on the blockchain. Enter to ignore:");
        String words = Inputer.inputStringMultiLine(br);
        if(words==null)return;
        if(!askIfYes(br, "Do you sure to carve the words on chain. It's irreversible. "))return;
        swapAll(chosenCashes, words);
    }

    

    public String swapAll(List<Cash> cashList, String words) {
            List<SendTo> sendToList = new ArrayList<>();
            int wordsLength = 0;
            if(words!=null)wordsLength = words.getBytes().length;
            long feeLong = TxCreator.calcFee(cashList.size(), 1, wordsLength,DEFAULT_FEE_RATE,false,null);
            double feeCoin = ParseTools.satoshiToCoin(feeLong);
            long sum = Cash.sumCashValue(cashList);
            double sumCoin = ParseTools.satoshiToCoin(sum);
            if(sumCoin<feeCoin){
            System.out.println("It's sufficient to pay the fee.");
            return null;
            }
            sendToList.add(new SendTo(myFid,sumCoin-feeCoin));

            return send(cashList, null, null, sendToList, words, br);
        }

    public String batchMerge(List<Cash> cashList, double sumCoin, BufferedReader br) {
        int mergeToCount = Inputer.inputInt(br, "Input the count of cashes you want to merge to:", 100);
        Double amount = Inputer.inputDouble(br, "Input the amount of the each cash:");
        if(amount==null)return null;
        double rest = NumberTools.roundDouble8(sumCoin-amount*(mergeToCount-1));
        System.out.println("You will get "+(mergeToCount-1)+" cashes with the amount of "+amount+"F, and the last one will have the rest "+rest+"F.");
        if(!askIfYes(br, "Do it?"))return null;

        List<SendTo> sendToList = new ArrayList<>();
        double paid = 0;
        for(int i=0;i<mergeToCount-1;i++){
            sendToList.add(new SendTo(myFid,amount));
            paid+=amount;
        }

        double cashAmountSum = Cash.sumCashAmount(cashList);

        double restAmount = calcRestAmount(cashList.size(), cashAmountSum, paid, sendToList.size(), 0, DEFAULT_FEE_RATE, false, null);

        if(restAmount<0){
            System.out.println("The rest is not enough to pay the fee.");
            return null;
        }
        sendToList.add(new SendTo(myFid,restAmount));

        for(SendTo sendTo :sendToList){
            System.out.println("To "+sendTo.getFid()+" "+ sendTo.getAmount());
        }

        if(askIfYes(br,"Send it?"))
            return send(cashList, null, null, sendToList, null, br);
        return null;
    }

    public String manualMerge(List<Cash> cashList, BufferedReader br) {
        if(cashList==null || cashList.isEmpty())return null;
        double cashSum = Cash.sumCashAmount(cashList);
        List<SendTo> sendToList = new ArrayList<>();
        double avaliable = cashSum;
        double rest = calcRestAmount(cashList.size(), avaliable, 0, 0, 0, DEFAULT_FEE_RATE, false, null);

        while (rest > 0) {
            System.out.printf("Remaining coins: %.8f FCH\n", rest);
            System.out.println("Enter amount the next cash:");
            String input = Inputer.inputString(br);

            if (input.isEmpty() && sendToList.size() > 0) {
                rest = calcRestAmount(cashList.size(), avaliable, 0, sendToList.size(), 0, DEFAULT_FEE_RATE, false, null);
                if(rest<0){
                    System.out.println("The rest is not enough to pay the fee.");
                    return null;
                }
                sendToList.add(new SendTo(myFid,rest));
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
                sendToList.add(new SendTo(myFid,amount));

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

        if(askIfYes(br,"Send it?"))
            return send(cashList, null, null, sendToList, null, br);
        return null;
    }

    public void sendTx() {
        sendTx(null,br);
    }  
    public void sendTx(@Nullable List<Cash> cashList, @Nullable BufferedReader br){
        String sender = myFid;

       System.out.println("Sender:  " + sender);

       System.out.println("Balance: "+ParseTools.satoshiToCoin(cid.getBalance())+".\nCashes:  "+ cid.getCash());

       List<SendTo> sendToList = SendTo.inputSendToList(br);

       double sum = 0;
       for (SendTo sendTo : sendToList) sum += sendTo.getAmount();

       System.out.println("Input the message written on the blockchain. Enter to ignore:");
       String msg =null;
       if(br!=null)msg = Inputer.inputString(br);

       Long cd = Inputer.inputLong(br, "CD", 0L);

       if(!sendToList.isEmpty()){
            System.out.println("\nSend to: ");
            for(SendTo sendTo:sendToList){
                System.out.println(sendTo.getFid()+" : "+sendTo.getAmount());
            }
            Wallet.checkNobodys(br,sendToList,apipClient,null);
       }else System.out.println("No receiver.  All the change will be sent back to the sender.");
       if(cd!=null)System.out.println("Required CD: "+cd);
       if(msg!=null && !msg.isEmpty())System.out.println("Message: "+msg);
       System.out.println();

       if(cashList==null)cashList = getCashesForSend(sum, cd, sendToList.size(), msg==null ? 0 : msg.getBytes().length);

       String result = send(cashList, sum, cd, sendToList, msg,br);
        if(result==null){
            System.out.println("Failed to send Tx. Check the log.");
            return;
        }
        String txid = null;
        if(Hex.isHexString(result)) {
            txid = result;
        }else if(StringTools.isBase64(result)){
            System.out.println("Unsigned TX: \n--------\n" + result + "\n--------");
            if(br!=null && !askIfYes(br,"Sign it?"))return;
            System.out.println("Please sign the TX and input the signed TX:");
            String txSigned;
            if(br!=null) txSigned = Inputer.inputString(br);
            else return;
            if(txSigned==null)return;
            if(StringTools.isBase64(txSigned)){
                txSigned = StringTools.base64ToHex(txSigned);
                txid = apipClient.broadcastTx(txSigned, RequestMethod.POST, AuthType.FC_SIGN_BODY);
            }else if(!Hex.isHexString(txSigned)){
                System.out.println("The input is not a signed TX.");
                return;
            }
        }

       System.out.println("Sent Tx:");
       Shower.printUnderline(10);
       System.out.println(txid);
       Shower.printUnderline(10);
       if(br!=null)Menu.anyKeyToContinue(br);
   }

   

    /**
     * @param cashList if null, choose cashes from cashFileMap.
     * @param amount if null, input amount.
     * @param cd if null, input cd.
     * @param sendToList if null, input sendToList.
     * @param opReturn if null, input opReturn.
     * @param br if null, no input and prompt.
     * @return the sent txid, unsigned rawTx or null if failed.
     */
    public String send(@Nullable List<Cash> cashList, @Nullable Double amount, @Nullable Long cd, @Nullable List<SendTo> sendToList, @Nullable String opReturn, @Nullable BufferedReader br){

        List<Cash> meetList;
        if(cashList!=null)meetList = cashList;
        else {
            if(amount == null && sendToList!=null)amount = sendToList.stream().mapToDouble(SendTo::getAmount).sum();
            meetList = getCashesForSend(amount, cd, sendToList==null ? 0 : sendToList.size(), opReturn==null ? 0 : opReturn.getBytes().length);
            if(meetList==null || meetList.isEmpty()){
                System.out.println("No cashes to spend.");
                return null;
            }
        }
        if(meetList.isEmpty())return null;

        boolean immatureRemoved = removeImmatureCashes(meetList, bestHeight);
        if(immatureRemoved){
            System.out.println("Some immature cashes have been removed.");
            if(br!=null && !askIfYes(br,"Continue?"))return null;
        }

        long destroyingCd = Cash.sumCashCd(meetList);
        if(cd!=null && destroyingCd < cd){
            System.out.println("The required CD is not enough:"+destroyingCd+" < "+cd);
            return null;
        }

        System.out.println("Destroying CD:"+destroyingCd);

        if(br!=null && !askIfYes(br,"\nAre you sure to send?"))return null;


        Integer maxJumpNum = getMaxJumpNum(meetList);
        if(maxJumpNum>=Constants.MAX_JUMP_NUM){
            System.out.println("The jump number is too large:"+ maxJumpNum);
            updateCashesIfOverJumped();
            return null;
        }

        byte[] unSignedTxBytes = TxCreator.createUnsignedTxFch(meetList, sendToList, opReturn==null ? null : opReturn.getBytes(), null, DEFAULT_FEE_RATE);
        if(unSignedTxBytes==null)return null;

        byte[] signedTxBytes = TxCreator.signRawTxFch(unSignedTxBytes, priKey);
        if(signedTxBytes==null){
            log.error("Failed to sign tx.");
            return null;
        }
        String signedTx = Hex.toHex(signedTxBytes);
        String result = null;
        if(nasaClient!=null)result = nasaClient.sendRawTransaction(signedTx);
        if(apipClient!=null)result = apipClient.broadcastTx(signedTx, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(!Hex.isHexString(result)){
            return null;
        }
        for(Cash cash : meetList){
            cashDB.remove(Hex.fromHex(cash.getId()));
        }

        Long balance = cid.getBalance();
        Long cashCount = cid.getCash();
        if(sendToList!=null){
            for(int i=0; i<sendToList.size();i++){
                SendTo sendTo = sendToList.get(i);
                if(sendTo.getFid().equals(myFid)){
                    addNewCash(result, i, sendTo.getAmount(), maxJumpNum);
                    cashCount++;
                }else{
                    balance -= ParseTools.coinToSatoshi(sendTo.getAmount());
                }
            }
        }

        double restAmount = Wallet.calcRestAmount(meetList, sendToList, opReturn==null ? 0 : opReturn.getBytes().length, null, false, null);

        if(restAmount > 0) {
            int index = sendToList == null ? 0 : sendToList.size();
            if(opReturn != null && !opReturn.isEmpty()) index++;
            balance += ParseTools.coinToSatoshi(restAmount);
            addNewCash(result, index, restAmount, maxJumpNum);
            cashCount++;
        }

        cid.setBalance(balance);
        cid.setCash(cashCount);

        maxJumpNum = getMaxJumpNum();
        if(maxJumpNum>=Constants.MAX_JUMP_NUM){
            updateCashesIfOverJumped();
        }
        return result;
    }

    public double calcRestAmount(List<Cash> cashList, List<SendTo> sendToList, int opReturnSize, Double feeRate, boolean isMultiSign, P2SH p2sh){
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

        return calcRestAmount(cashList.size(), cashAmountSum, sendToAmountSum, sendSize, opReturnSize, feeRate, isMultiSign, p2sh);
    }

    public double calcRestAmount(int cashListSize, double cashAmountSum, double outPutSum, int sendToListSize, int opReturnSize, Double feeRate, boolean isMultiSign, P2SH p2sh) {
        this.lastFee = TxCreator.calcFee(cashListSize, sendToListSize, opReturnSize, feeRate, isMultiSign, p2sh);
        double feeCoin = ParseTools.satoshiToCoin(lastFee);
        return cashAmountSum-feeCoin-outPutSum;
    }


    public long calcFee(List<Cash> cashList, List<SendTo> sendToList, int opReturnSize, Double feeRate, boolean isMultiSign, P2SH p2sh){
        if(feeRate==null)feeRate = DEFAULT_FEE_RATE;
        int sendSize=0;
        if(sendToList!=null)sendSize = sendToList.size();

        this.lastFee = TxCreator.calcFee(cashList.size(), sendSize, opReturnSize, feeRate, isMultiSign, p2sh);
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
    public List<Cash> chooseCasheList(BufferedReader br) {
        int totalDisplayed = 0;
        List<Cash> finalCashes = new ArrayList<>();
        byte[] start = null;
        while(true){
            List<Cash> currentList = cashDB.getListFromEnd(start, BATCH_SIZE, Cash::fromBytes);
            if(currentList==null || currentList.isEmpty())return null;
            totalDisplayed += currentList.size();
            List<Cash> chosenCashes = chooseCasheList(currentList, totalDisplayed, br);
            if(chosenCashes!=null && !chosenCashes.isEmpty()){
                finalCashes.addAll(chosenCashes);
                if(currentList.size()<BATCH_SIZE)break;
                start = currentList.get(currentList.size()-1).getId().getBytes();
            }else{
                return finalCashes;
            }
            if(!askIfYes(br,"Choose more?"))return finalCashes;    
        }
        return finalCashes;
    }
    public List<Cash> chooseCasheList(List<Cash> currentList, int totalDisplayed, BufferedReader br) {
        List<Cash> chosenCashes = new ArrayList<>();

        String title = "Choose Cashes";
        Cash.showCashList(currentList, title, totalDisplayed, myFid);

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
        List<Cash> cashList = cashDB.getListFromEnd( null, Constants.DEFAULT_CASH_LIST_SIZE, Cash::fromBytes);
        long spendValue = 0L;
        if (amount != null) spendValue = ParseTools.coinToSatoshi(amount);

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
            if (cashDB.size() > Constants.DEFAULT_CASH_LIST_SIZE) {
                System.out.println("You have more than " + Constants.DEFAULT_CASH_LIST_SIZE + " cashes. Please merge them first or choose cashes manually.");
            }
            System.out.println("No cashes can meet the amount and cd.");
            return null;
        }
    }

    private void addNewCash(String txId, int index, double amount, int maxJumpNum) {
        Cash cash = new Cash();
        cash.setOwner(myFid);
        cash.setBirthTxId(txId);
        cash.setBirthIndex(index);
        cash.setValue(ParseTools.coinToSatoshi(amount));
        cash.setId(ParseTools.calcTxoId(txId, index));

        cashDB.put(Hex.fromHex(cash.getId()), cash.toBytes());
        unsafeIdJumpNumMap.put(cash.getId(), maxJumpNum + 1);

    }

    public void freshCashDB(){
        long lastHeight = cashDB.getLastHeight();
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
                cid = apipClient.cidInfoById(myFid);
                if(cid ==null){
                    log.error("Failed to get cidInfo from apipClient when checkValidCashes.");
                }
                freshCashFileMapByApip(lastHeight);
            }else if(esClient!=null){
                try{    
                    Cid cid = EsTools.getById(esClient,CID,myFid, Cid.class);
                    if(cid!=null) this.cid = cid;
                    freshCashFileMapByEs(lastHeight);
                }catch (Exception e){
                    log.error("EsClient error:{}", e.getMessage());
                }
            }else {
                if (freshCashFileMapByNasaRpc()) return;
            }
        }
        freshUnconfirmed();
    }

    private boolean freshCashFileMapByNasaRpc() {
        cashDB.clear();
        ReplyBody replier = getCashListFromNasaNode(myFid, null, true, nasaClient);
        if(replier.getCode()!=0){
            log.error("Failed to get cash list from nasaClient:{}", replier.getMessage());
            return true;
        }
        List<Cash> cashList = (List<Cash>) replier.getData();
        for (Cash cash : cashList) {
            cashDB.put(Hex.fromHex(cash.getId()), cash.toBytes());
        }
        bestHeight = nasaClient.getBestHeight();
        cashDB.setLastHeight(bestHeight);
        System.out.println("Updated "+ cashList.size() + " cashes.");
        return false;
    }

    private void freshCashFileMapByEs(Long lastHeight) {
        int total = 0;
        List<Cash> cashList = new ArrayList<>();
        SearchRequest.Builder sb = new SearchRequest.Builder();
        List<String> last = null;
        sb.index(CASH);
        sb.trackTotalHits(t -> t.enabled(true));
        sb.size(DEFAULT_CASH_LIST_SIZE);
        List<SortOptions> sortOptionsList = Sort.makeTwoFieldsSort(FieldNames.LAST_TIME,DESC,FieldNames.CASH_ID,ASC);
        sb.sort(sortOptionsList);
        Query.Builder qb = new Query.Builder();
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        // Add the owner must clause
        boolBuilder.must(new Query(new TermQuery.Builder()
            .field(FieldNames.OWNER)
            .value(myFid)
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
                bestHeight = EsTools.getBestBlock(esClient).getHeight();
                List<String> newLast = null;
                for (Hit<Cash> hit : result.hits().hits()) {
                    cashList.add(hit.source());
                    newLast = hit.sort();
                }
                if (newLast != null)
                    last = newLast;
                if(cashList.size()<DEFAULT_CASH_LIST_SIZE)break;
            } catch (IOException e) {
                log.error("EsClient error:{}", e.getMessage());
            }
        }
        for (Cash cash : cashList) {
            total++;
            if(cash.isValid())
                cashDB.put(Hex.fromHex(cash.getId()), cash.toBytes());
            else
                cashDB.remove(Hex.fromHex(cash.getId()));
        }

        cashDB.setLastHeight(bestHeight);
        System.out.println("Updated "+ total + " cashes.");
    }

    private void freshCashFileMapByApip(Long lastHeight) {
        int total = 0;
        List<String> last = null;
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addSize(DEFAULT_CASH_LIST_SIZE);
        fcdsl.addNewQuery().addNewRange().addNewFields(FieldNames.BIRTH_HEIGHT, SPEND_HEIGHT).addGt(String.valueOf(lastHeight));
        fcdsl.getQuery().addNewTerms().addNewFields(OWNER).addNewValues(myFid);
        while(true){
            if(last!=null)fcdsl.setAfter(last);
            List<Cash> cashList = apipClient.cashSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
            if(cashList==null||cashList.isEmpty())break;
            bestHeight = apipClient.getBestHeight();
            for (Cash cash : cashList) {
                total++;
                if(cash.isValid()){
                    cashDB.put(Hex.fromHex(cash.getId()), cash.toBytes());
                }else{
                    cashDB.remove(Hex.fromHex(cash.getId()));
                }
            }
            if(cashList.size()<DEFAULT_CASH_LIST_SIZE)break;
            last = apipClient.getFcClientEvent().getResponseBody().getLast();
        }
        cashDB.setLastHeight(bestHeight);
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
                fcdsl.getQuery().addNewRange().addNewFields(FieldNames.BIRTH_HEIGHT).addGt(String.valueOf(sinceHeight));
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
        cashDB.clear();
        if(myFid==null){
            log.error("myFid is null.");
            return;
        }
        if(apipClient==null && esClient==null && nasaClient==null){
            log.error("apipClient, esClient and nasaClient are null.");
            return;
        }
        if(apipClient!=null){
            cid = apipClient.cidInfoById(myFid);
            if(cid ==null){
                log.error("Failed to get cidInfo from apipClient when checkValidCashes.");
            }

            List<Cash> cashList = pullAllValidFromApip(br,false, cashDB.getLastHeight());//getCashListFromApip(myFid, true, 200, null, null, bestHeight, apipClient);
            if(cashList.isEmpty()){
                log.error("Failed to get cash list from apipClient.");
                return;
            }
            for (Cash cash : cashList) {
                cashDB.put(Hex.fromHex(cash.getId()), cash.toBytes());
            }
            bestHeight = apipClient.getBestHeight();
            cashDB.setLastHeight(bestHeight);
        }else if(esClient!=null){
            ReplyBody replier = getAllCashListFromEs(new ArrayList<>(Arrays.asList(myFid)), true, null, DEFAULT_CASH_LIST_SIZE, null, null, esClient);
            if(replier.getCode()!=0){
                log.error("Failed to get cash list from esClient:{}", replier.getMessage());
                return;
            }
            List<Cash> cashList = (List<Cash>) replier.getData();
            if(cashList==null || cashList.isEmpty()){
                log.error("Failed to get cash list from esClient.");
                return;
            }
            for (Cash cash : cashList) {
                cashDB.put(Hex.fromHex(cash.getId()), cash.toBytes());
            }
            try {
                bestHeight = EsTools.getBestBlock(esClient).getHeight();
                cashDB.setLastHeight(bestHeight);
            } catch (IOException ignore) {}
            
        }else {
            ReplyBody replier = getCashListFromNasaNode(myFid, "1", true, nasaClient);
            if(replier.getCode()!=0){
                log.error("Failed to get cash list from nasaClient:{}", replier.getMessage());
                return;
            }
            List<Cash> cashList = (List<Cash>) replier.getData();
            if(cashList==null || cashList.isEmpty()){
                log.error("Failed to get cash list from nasaClient.");
                return;
            }
            for (Cash cash : cashList) {
                cashDB.put(Hex.fromHex(cash.getId()), cash.toBytes());
            }
            bestHeight = nasaClient.getBestHeight();
            cashDB.setLastHeight(bestHeight);
        }

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
            List<Cash> unconfirmedCashes = mempoolHandler.checkUnconfirmedCash(myFid);
            if(unconfirmedCashes!=null){
                for(Cash cash : unconfirmedCashes){
                    if(cash.isValid())
                        cashDB.put(Hex.fromHex(cash.getId()), cash.toBytes());
                    else
                        cashDB.remove(Hex.fromHex(cash.getId()));
                }
            }
        }else if(nasaClient!=null){
            Map<String, TxHasInfo> txInMempoolMap = Wallet.checkMempool(nasaClient, apipClient, esClient);
            for (Map.Entry<String, TxHasInfo> entry : txInMempoolMap.entrySet()) {
                TxHasInfo txHasInfo = entry.getValue();
                if(txHasInfo.getInCashList()!=null){
                    for (Cash cash : txHasInfo.getInCashList()) {
                        cashDB.remove(Hex.fromHex(cash.getId()));
                    }
                }
                if(txHasInfo.getOutCashList()!=null){
                    for (Cash cash : txHasInfo.getOutCashList()) {
                        if(myFid.equals(cash.getOwner())){
                            cashDB.put(Hex.fromHex(cash.getId()), cash.toBytes());
                        }
                    }
                }
            }
        }else if(apipClient!=null){
            Map<String,List<Cash>> result = apipClient.unconfirmedCaches(RequestMethod.POST, AuthType.FC_SIGN_BODY,myFid);
            if(result==null)return;
            List<Cash> unconfirmedCashes = result.get(myFid);
            if(unconfirmedCashes!=null){
                for (Cash cash : unconfirmedCashes) {
                    if(myFid.equals(cash.getOwner())){
                        if(cash.isValid())
                            cashDB.put(Hex.fromHex(cash.getId()), cash.toBytes());
                        else
                            cashDB.remove(Hex.fromHex(cash.getId()));
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
            List<Cash> cashes = getCashListFromApip(myFid, true, 200, null, null, cashDB.getLastHeight(), apipClient);
            if(cashes==null)return;
            for(Cash cash : cashes){
                cashDB.put(Hex.fromHex(cash.getId()), cash.toBytes());
                unsafeIdJumpNumMap.remove(cash.getId());
            }
            cid = apipClient.cidInfoById(myFid);
        }
    }

    public List<Cash> getAllCashList(String fid, Boolean valid, ArrayList<Sort> sortList, List<String> last, BufferedReader br) {
        List<Cash> cashList = new ArrayList<>();
        int chosenSize = 0;

        ReplyBody replyBody;
        if (this.apipClient != null) {
            do {
                List<Cash> newCashList = getCashListFromApip(fid, valid, DEFAULT_CASH_LIST_SIZE, sortList, last, null, apipClient);
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
                replyBody = getCashListFromEs(new ArrayList<>(Arrays.asList(fid)), valid, null, DEFAULT_CASH_LIST_SIZE, sortList, last, esClient);
                if (replyBody.getCode() != 0) {
                    log.debug(replyBody.getMessage());
                    break;
                }
                if (replyBody.getData() != null) {
                    List<Cash> newCashList = ObjectTools.objectToList(replyBody.getData(), Cash.class);

                    if(br!=null){
                        List<Cash> chosenCashList = chooseCasheList(newCashList, chosenSize, br);
                        cashList.addAll(chosenCashList);
                        chosenSize += chosenCashList.size();
                        if(!askIfYes(br,"Do you want to continue to get cashes?")){
                            break;
                        }
                    }else cashList.addAll(newCashList);

                } else return cashList;
                last = ObjectTools.objectToList(replyBody.getData(), String.class);//DataGetter.getStringList(fcReplier.getLast());
            } while (cashList.size() < replyBody.getTotal());
        } else if (this.nasaClient != null) {
            replyBody = getCashListFromNasaNode(fid, null, true, nasaClient);
            if (replyBody.getCode() != 0) {
                log.debug(replyBody.getMessage());
                return cashList;
            }
            if (replyBody.getData() != null){
                List<Cash> newCashList = ObjectTools.objectToList(replyBody.getData(), Cash.class);
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
            fcdsl.getQuery().addNewRange().addNewFields(BIRTH_HEIGHT).addGt(String.valueOf(sinceHeight));
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
        else sb.size(DEFAULT_CASH_LIST_SIZE);
        if (sortList != null && !sortList.isEmpty()) {
            List<SortOptions> sortOptionslist = Sort.getSortList(sortList);
            sb.sort(sortOptionslist);
        } else {
            List<SortOptions> sortOptionsList = Sort.makeTwoFieldsSort(BIRTH_HEIGHT, DESC, CASH_ID, ASC);
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
            cash.setValue(ParseTools.coinToSatoshi(utxo.getAmount()));
            cash.setLockScript(utxo.getRedeemScript());
            cashList.add(cash);
        }
        ReplyBody replyBody = new ReplyBody();
        replyBody.set0Success();
        replyBody.setData(cashList);
        return replyBody;
    }

    // Unconfirmed Transaction Methods
    public static void checkUnconfirmed(List<Cash> meetList, String fid, MempoolHandler mempoolHandler, ApipClient apipClient) {
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
        List<String> cashIdList = ObjectTools.objectToList(cashIdListStr,String.class);
        if(cashIdList==null|| cashIdList.isEmpty())return null;
        for(String cashId:cashIdList){
            String cashStr = jedis.hget(FieldNames.NEW_CASHES,cashId);
            Cash cash = gson.fromJson(cashStr, Cash.class);
            if(cash!=null)cashList.add(cash);
        }
        return cashList;
    }
    private List<Cash> pullAllValidFromApip() {
        return pullAllValidFromApip(null,false,null);
    }

    public List<Cash> chooseFromAllFromApip() {
        return pullAllValidFromApip(br,true,null);
    }

    private List<Cash> pullAllValidFromApip(@Nullable BufferedReader br, boolean choose, Long sinceHeight) {
         List<Cash> finalCashes = new ArrayList<>();
         List<Cash> subCashList;
         int totalDisplayed = 0;
         int got = 0;
         Fcdsl fcdsl = new Fcdsl();
         fcdsl.addIndex(CASH);
         fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.OWNER).addNewValues(myFid);
         fcdsl.addNewFilter().addNewTerms().addNewFields(FieldNames.VALID).addNewValues(Values.TRUE);
         if(sinceHeight!=null)fcdsl.getQuery().addNewRange().addNewFields(FieldNames.BIRTH_HEIGHT).addGt(String.valueOf(sinceHeight));
         fcdsl.addSort(FieldNames.BIRTH_HEIGHT, DESC).addSort(FieldNames.BIRTH_TX_INDEX, ASC).addSort(FieldNames.BIRTH_INDEX, DESC);
         fcdsl.addSize(BATCH_SIZE);
         List<String> last = null;

         while(true){
             if(last!=null){
                 fcdsl.setAfter(last);
             }
             ReplyBody replyBody = apipClient.general(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
             if(replyBody ==null)break;
             last = replyBody.getLast();
             subCashList = ObjectTools.objectToList(replyBody.getData(), Cash.class);
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
                     Cash.showCashList(finalCashes,"Chosen Cashes",0, myFid);
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

    public static Boolean isImmature(Cash cash, Long bestHeight) {
        if(bestHeight==null)return null;
        if(! COINBASE.equals(cash.getIssuer()))return false;

        if(cash.getOwner().equals(Constants.FUND_FID)){
            if( (bestHeight - cash.getBirthHeight()) < FUND_MATURE_DAYS_INT)return true;
        }

        return bestHeight - cash.getBirthHeight() < MINE_MATURE_DAYS_INT;
    }

    public static boolean removeImmatureCashes(List<Cash> cashList, Long bestHeight){
        return cashList.removeIf(cash -> isImmature(cash, bestHeight));
    }

    public static SearchResult<Cash> getValidCashes(String myFid, Long amount, Long cd, Long sinceHeight, int outputSize, int msgSize, ApipClient apipClient, ElasticsearchClient esClient, MempoolHandler mempoolHandler) {
        SearchResult<Cash> searchResult = new SearchResult<>();
        List<Cash> cashList = null;
        Long bestheight = null;
        if(apipClient!=null)bestheight = apipClient.getBestHeight();
        if(esClient!=null) {
            try {
                bestheight = EsTools.getBestBlock(esClient).getHeight();
            } catch (IOException ignore) {
            }
        }
        if(apipClient!=null){
            Fcdsl fcdsl = new Fcdsl();
            if(myFid!=null)fcdsl.addNewQuery().addNewTerms().addNewFields(OWNER).addNewValues(myFid);
            fcdsl.addNewFilter().addNewTerms().addNewFields(VALID).addNewValues(TRUE);
            if(sinceHeight!=null)fcdsl.getQuery().addNewRange().addNewFields(BIRTH_HEIGHT).addGt(String.valueOf(sinceHeight));
            fcdsl.addSize(MAX_CASH_SIZE);
            fcdsl.addSort(FieldNames.CD,ASC).addSort(CASH_ID, ASC);
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
                                .sort(s2 -> s2.field(f -> f.field(FieldNames.CASH_ID).order(SortOrder.Asc)))
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
                cdd = ParseTools.cdd(cash.getValue(), cash.getBirthTime(), System.currentTimeMillis()/1000);
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
        cash.setId(ParseTools.calcTxoId(cash.getBirthTxId(), cash.getBirthIndex()));
        cash.setBirthTime(System.currentTimeMillis() / 1000);  // Current Unix timestamp
        cash.setBirthHeight(100000L);  // Example block height
        cash.setValid(true);
        
        return cash;
    }

    public PersistentSequenceMap getCashDB() {
        return cashDB;
    }

    public String getMyFid() {
        return myFid;
    }

    public void setMyFid(String myFid) {
        this.myFid = myFid;
    }

    /**
     * Instance method that creates a transaction with an OP_RETURN message and optional CD requirement
     * @param opReturn The message to be written to the blockchain
     * @param cd The minimum CD requirement (can be null)
     * @return The transaction ID if successful, null otherwise
     */
    public String carve(String opReturn, Long cd) {
        return carve(opReturn, cd, this.priKey, this.apipClient);
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

        String fid = KeyTools.priKeyToFid(priKey);

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
        byte[] unSignedTxBytes = TxCreator.createUnsignedTxFch(
            cashList, 
            null, 
            opReturn.getBytes(), 
            null, 
            DEFAULT_FEE_RATE
        );

        if (unSignedTxBytes == null) {
            return null;
        }

        byte[] signedTxBytes = TxCreator.signRawTxFch(unSignedTxBytes, priKey);
        if (signedTxBytes == null) {
            return null;
        }

        // Broadcast the transaction
        String signedTx = Hex.toHex(signedTxBytes);
        return apipClient.broadcastTx(signedTx, RequestMethod.POST, AuthType.FC_SIGN_BODY);
    }

    /**
     * Static method to send FCH to one or more recipients
     * @param priKey The private key for signing the transaction
     * @param sendToList List of recipients and amounts
     * @param apipClient APIP client for blockchain interaction (required)
     * @param esClient Optional ES client for backup cash lookup
     * @return Transaction ID if successful, null if failed
     */
    public static String send(byte[] priKey, List<SendTo> sendToList, ApipClient apipClient, @Nullable ElasticsearchClient esClient) {
        if (priKey == null || sendToList == null || sendToList.isEmpty() || apipClient == null) {
            return null;
        }

        String fid = KeyTools.priKeyToFid(priKey);
        if (fid == null) {
            return null;
        }

        // Calculate total amount needed
        double totalAmount = sendToList.stream()
                .mapToDouble(SendTo::getAmount)
                .sum();

        // Get valid cashes that meet the amount requirement
        SearchResult<Cash> searchResult = getValidCashes(
            fid, 
            ParseTools.coinToSatoshi(totalAmount), 
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
        byte[] unSignedTxBytes = TxCreator.createUnsignedTxFch(
            cashList, 
            sendToList,
                (byte[]) null,
            null, 
            DEFAULT_FEE_RATE
        );

        if (unSignedTxBytes == null) {
            return null;
        }

        byte[] signedTxBytes = TxCreator.signRawTxFch(unSignedTxBytes, priKey);
        if (signedTxBytes == null) {
            return null;
        }

        // Broadcast the transaction
        String signedTx = Hex.toHex(signedTxBytes);
        return apipClient.broadcastTx(signedTx, RequestMethod.POST, AuthType.FC_SIGN_BODY);
    }
}
