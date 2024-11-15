package clients;

import apip.apipData.CidInfo;
import apip.apipData.Fcdsl;
import apip.apipData.Sort;
import apip.apipData.TxInfo;
import appTools.Inputer;
import appTools.Menu;
import appTools.Shower;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;

import com.google.gson.Gson;
import constants.Constants;
import constants.FieldNames;
import constants.IndicesNames;
import constants.Values;
import fch.ParseTools;
import fch.TxCreator;
import fch.Wallet;
import fch.fchData.Cash;
import fch.fchData.SendTo;
import fch.fchData.TxHasInfo;
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
import fcData.FcReplierHttp;
import tools.*;
import tools.http.AuthType;
import tools.http.RequestMethod;

import javax.annotation.Nullable;

import static appTools.Inputer.askIfYes;
import static constants.Constants.*;
import static constants.FieldNames.*;
import static constants.Strings.ASC;
import static constants.Strings.DESC;
import static constants.Values.*;
import static fch.TxCreator.DEFAULT_FEE_RATE;
import static fch.Wallet.calcRestAmount;

public class CashClient {
    // Constants
    private static final Logger log = LoggerFactory.getLogger(CashClient.class);
    private static final int BATCH_SIZE = 50;

    // Instance Fields
    private Map<String, Cash> validCashMap;
    private final Map<String, Integer> unsafeIdJumpNumMap;
    private final ApipClient apipClient;
    private final NaSaRpcClient nasaClient;
    private final ElasticsearchClient esClient;
    private final JedisPool jedisPool;
    private String myFid;
    private CidInfo cidInfo;
    private String myPriKeyCipher;
    private byte[] symKey;
    private final BufferedReader br;
    private static long bestHeight;

    // Constructors
    public CashClient(String myFid, String myPriKeyCipher, byte[] symKey,
                    ApipClient apipClient, NaSaRpcClient nasaClient, ElasticsearchClient esClient,
                    JedisPool jedisPool, BufferedReader br) {
        this.apipClient = apipClient;
        this.nasaClient = nasaClient;
        this.esClient = esClient;
        this.jedisPool = jedisPool;
        this.myFid = myFid;
        this.myPriKeyCipher = myPriKeyCipher;
        this.symKey = symKey;

        this.validCashMap = new HashMap<>();
        this.br =br;
        unsafeIdJumpNumMap=new HashMap<>();
        checkValidCashes();
    }

    public CashClient(ApipClient apipClient, NaSaRpcClient nasaClient, ElasticsearchClient esClient,
                    JedisPool jedisPool, BufferedReader br) {
        this.apipClient = apipClient;
        this.nasaClient = nasaClient;
        this.esClient = esClient;
        this.jedisPool = jedisPool;
        this.br = br;
        unsafeIdJumpNumMap=new HashMap<>();
    }

    public void start() {
        List<Cash> validCashList;
        if(validCashMap.isEmpty()){
            validCashList = pullAllCashes(br,true);
            if(validCashList!=null && !validCashList.isEmpty()){
                for (Cash cash : validCashList) {
                    validCashMap.put(cash.getCashId(), cash);
                }
            }
        }else{
            validCashList = new ArrayList<>(validCashMap.values());
        }

        menu(validCashList,false);
    }

    public void menu(@Nullable List<Cash> validCashList, boolean isChosen) {

        if(validCashList==null || validCashList.isEmpty()){
            System.out.println("No cashes to manage.");
            return;
        }

        Menu menu = new Menu("Cash Management");
        if(!isChosen) menu.add("Valid", () -> Cash.showCashList(validCashList, "Your valid cashes", 0, myFid));
        if(!isChosen) menu.add("Cash Detail", () -> cashDetail());
        if(!isChosen) menu.add(INCOME, this::incomes);
        if(!isChosen) menu.add(EXPENSE, this::expense);
        if(!isChosen) menu.add("Load All", this::checkValidCashes);
        if(!isChosen) menu.add("Check New", () -> {
            long sumCd = Cash.sumCashCd(validCashList);
            if(br!=null && !askIfYes(br, "You are destroying "+sumCd+"cd. Continue?")) return;
            checkValidCashes();
        });
        menu.add("Rearrange", () -> rearrange(validCashList));
        if(isChosen)
            menu.add("Send", () -> sendTx(validCashList));
        else 
            menu.add("Send", () -> sendTx(null));
        menu.add("Carve", () -> carve(validCashList));
        menu.showAndSelect(br);
    }

    private void cashDetail() {
        String cashId = Inputer.inputString(br, "Input the cash ID:");
        Cash cash = validCashMap.get(cashId);
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
        System.out.println(niceCashStr);
    }

    public void incomes() {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(OWNER).addNewValues(myFid);
        fcdsl.addNewExcept().addNewTerms().addNewFields(ISSUER).addNewValues(myFid,OP_RETURN);
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
            last.addAll(apipClient.getFcClientEvent().responseBody.getLast());
            if(!askIfYes(br,"Continue?"))return;
        }
    }

    public void expense() {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(ISSUER).addNewValues(myFid);
        fcdsl.addNewExcept().addNewTerms().addNewFields(OWNER).addNewValues(myFid,OP_RETURN);
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
            last.addAll(apipClient.getFcClientEvent().responseBody.getLast());
            if(!askIfYes(br,"Continue?"))return;
        }
    }
        
public void chooseAndHandleCashes(){
        List<Cash> cashList = chooseCashesForSend(null, null, 0, 0, br);
        if(cashList==null || cashList.isEmpty())return;
        menu(cashList,true);
    }

    /**
     * @param cashList if null, choose cashes from validCashMap.
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
            meetList = chooseCashesForSend(amount, cd, sendToList==null ? 0 : sendToList.size(), opReturn==null ? 0 : opReturn.getBytes().length,br);
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
            updateCashes();
            return null;
        }

        byte[] unSignedTxBytes = TxCreator.createUnsignedTxFch(meetList, sendToList, opReturn==null ? null : opReturn.getBytes(), null, DEFAULT_FEE_RATE);
        if(unSignedTxBytes==null)return null;
        if(myPriKeyCipher==null)
            return Base64.getEncoder().encodeToString(unSignedTxBytes);

        byte[] priKeyBytes = Client.decryptPriKey(myPriKeyCipher, symKey);
        if(priKeyBytes==null){
            log.error("Failed to decrypt private key.");
            return null;
        }

        byte[] signedTxBytes = TxCreator.signRawTxFch(unSignedTxBytes, priKeyBytes);
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
            validCashMap.remove(cash.getCashId());
        }

        Long balance = cidInfo.getBalance();
        Long cashCount = cidInfo.getCash();
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

        cidInfo.setBalance(balance);
        cidInfo.setCash(cashCount);

        maxJumpNum = getMaxJumpNum();
        if(maxJumpNum>=Constants.MAX_JUMP_NUM){
            updateCashes();
        }
        return result;
    }


    /**
     * @param cashList if null, choose cashes from validCashMap.
     */
    public void sendTx(@Nullable List<Cash> cashList){
        String sender = myFid;

       System.out.println("Sender:  " + sender);

       System.out.println("Balance: "+ParseTools.satoshiToCoin(cidInfo.getBalance())+".\nCashes:  "+cidInfo.getCash());

       List<SendTo> sendToList = SendTo.inputSendToList(br);

       double sum = 0;
       for (SendTo sendTo : sendToList) sum += sendTo.getAmount();

       System.out.println("Input the message written on the blockchain. Enter to ignore:");
       String msg = Inputer.inputString(br);

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
            if(!askIfYes(br,"Sign it?"))return;
            System.out.println("Please sign the TX and input the signed TX:");
            String txSigned = Inputer.inputString(br);
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
       Menu.anyKeyToContinue(br);
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
    public String carve(List<Cash> chosenCashes) {
        System.out.println("Input the message written on the blockchain. Enter to ignore:");
         String words = Inputer.inputStringMultiLine(br);
         if(words==null)return null;
         if(!askIfYes(br, "Do you sure to carve the words on chain. It's irreversible. "))return null;
         return swapAll(chosenCashes, words);
     }

    public String swapAll(List<Cash> cashList, String words) {
            List<SendTo> sendToList = new ArrayList<>();
            int wordsLength = 0;
            if(words!=null)wordsLength = words.getBytes().length;
            //           long txSize = TxCreator.calcTxSize(cashList.size(), 1, wordsLength);
            //           long feeLong = TxCreator.calcFee(txSize,DEFAULT_FEE_RATE);
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
           double amount = Inputer.inputDouble(br, "Input the amount of the each cash:");
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
        double rest = calcRestAmount(cashList.size(), avaliable, 0, sendToList.size(), 0, DEFAULT_FEE_RATE, false, null);;

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
    // Core Cash Management Methods
    public SearchResult<Cash> getCashes(Long amount, Long cd, Long sinceHeight, int outputSize, int msgSize) {
        return getCashes(myFid,amount,cd,sinceHeight,outputSize,msgSize,apipClient,esClient,jedisPool);
    }

    private void addNewCash(String txId, int index, double amount, int maxJumpNum) {
        Cash cash = new Cash();
        cash.setOwner(myFid);
        cash.setBirthTxId(txId);
        cash.setBirthIndex(index);
        cash.setValue(ParseTools.coinToSatoshi(amount));
        cash.setCashId(ParseTools.calcTxoId(txId, index));

        validCashMap.put(cash.getCashId(), cash);
        unsafeIdJumpNumMap.put(cash.getCashId(), maxJumpNum + 1);

    }

    public void checkValidCashes(){
        if(myFid==null){
            log.error("myFid is null.");
            return;
        }
        if(apipClient==null && esClient==null && nasaClient==null){
            log.error("apipClient, esClient and nasaClient are null.");
            return;
        }
        if(apipClient!=null){

            cidInfo = apipClient.cidInfoById(myFid);
            if(cidInfo==null){
                log.error("Failed to get cidInfo from apipClient when checkValidCashes.");
            }

            List<Cash> cashList = pullAllCashes(br,false);//getCashListFromApip(myFid, true, 200, null, null, bestHeight, apipClient);
            if(cashList==null || cashList.isEmpty()){
                log.error("Failed to get cash list from apipClient.");
                return;
            }
            for (Cash cash : cashList) {
                validCashMap.put(cash.getCashId(), cash);
            }
        }else if(esClient!=null){
            FcReplierHttp replier = getCashListFromEs(myFid, true, 200, null, null, esClient);
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
                validCashMap.put(cash.getCashId(), cash);
            }
        }else if(nasaClient!=null){
            FcReplierHttp replier = getCashListFromNasaNode(myFid, "1", true, nasaClient);
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
                validCashMap.put(cash.getCashId(), cash);
            }
        }

        if(nasaClient!=null){
            Map<String, TxHasInfo> txInMempoolMap = Wallet.checkMempool(nasaClient, apipClient, esClient);
            for (Map.Entry<String, TxHasInfo> entry : txInMempoolMap.entrySet()) {
                TxHasInfo txHasInfo = entry.getValue();
                if(txHasInfo.getInCashList()!=null){
                    for (Cash cash : txHasInfo.getInCashList()) {
                        validCashMap.remove(cash.getCashId());
                    }
                }
                if(txHasInfo.getOutCashList()!=null){
                    for (Cash cash : txHasInfo.getOutCashList()) {
                        if(myFid.equals(cash.getOwner())){
                            validCashMap.put(cash.getCashId(), cash);
                        }
                    }
                }   
            }
        }else if(apipClient!=null){
            List<Cash> unconfirmedCashes = apipClient.unconfirmedCaches(RequestMethod.POST, AuthType.FC_SIGN_BODY);
            if(unconfirmedCashes!=null){
                for (Cash cash : unconfirmedCashes) {
                    if(myFid.equals(cash.getOwner())){
                        if(cash.isValid())
                            validCashMap.put(cash.getCashId(), cash);
                        else
                            validCashMap.remove(cash.getCashId());
                    }
                }
            }
        }
    }

    /**
     * the cashes are from validCashMap which was got by checkValidCashes() when CashClient was created.
     * if amount and cd are null: if br is null, return all cashes, else return cashes chosen by user.
     * if amount is null, return cashes that can meet the cd.
     * if cd is null, return cashes that can meet the amount.
     * if amount and cd are not null, return cashes that can meet the amount and cd.
     */
    public List<Cash> chooseCashesForSend(@Nullable Double amount, @Nullable Long cd, int outputNum, int opReturnLength,@Nullable BufferedReader br){
        List<Cash> cashList = new ArrayList<>(validCashMap.values());
        if(cashList.size()>1){
            cashList.sort((c1, c2) -> Long.compare(c1.getValue(), c2.getValue()));
        }
        if(amount==null && cd==null){
            if(br ==null) return cashList;
            else return chooseCasheList(cashList,0,br);
        }

        Long spendValue = 0L;
        if(amount!=null)spendValue = ParseTools.coinToSatoshi(amount);

        if(cd==null){
            cd = 0L;
        }

        long totalValue = 0L;
        long totalCd = 0L;

        List<Cash> meetList = new ArrayList<>();
        long fee = 0L;
        for (Cash cash : cashList) {
            totalValue += cash.getValue();
            if(cash.getCd()!=null) totalCd += cash.getCd();
            meetList.add(cash);

            long txSize = TxCreator.calcTxSize(meetList.size(), outputNum, opReturnLength);
            fee = TxCreator.calcFee(txSize, DEFAULT_FEE_RATE);
            if(totalValue>=(spendValue+fee) && totalCd>=cd){
                break;
            }
        }
        if(totalValue>=(spendValue+fee) && totalCd>=cd)return meetList;
        else return null;
    }
    /**
     * if cashList is null, get cashes from validCashMap.
     * sendToList can be null.
     */

    public void updateCashes(){
        Integer maxJumpNum = getMaxJumpNum();
        if(maxJumpNum.equals(MAX_JUMP_NUM)){
            List<Cash> cashes = getCashListFromApip(myFid, true, 200, null, null, bestHeight, apipClient);
            if(cashes==null)return;
            for(Cash cash : cashes){
                validCashMap.put(cash.getCashId(), cash);
                unsafeIdJumpNumMap.remove(cash.getCashId());
            }
            cidInfo = apipClient.cidInfoById(myFid);
        }
    }

    public List<Cash> getAllCashList(String fid, Boolean valid, ArrayList<Sort> sortList, List<String> last, BufferedReader br) {
        List<Cash> cashList = new ArrayList<>();
        int chosenSize = 0;

        FcReplierHttp fcReplierHttp;
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

                fcReplierHttp = this.apipClient.getFcClientEvent().getResponseBody();
                last = fcReplierHttp.getLast();
            } while (cashList.size() < fcReplierHttp.getTotal());
        } else if (this.esClient != null) {
            do {
                fcReplierHttp = getCashListFromEs(fid, valid, DEFAULT_CASH_LIST_SIZE, sortList, last, esClient);
                if (fcReplierHttp.getCode() != 0) {
                    log.debug(fcReplierHttp.getMessage());
                    break;
                }
                if (fcReplierHttp.getData() != null) {
                    List<Cash> newCashList = ObjectTools.objectToList(fcReplierHttp.getData(), Cash.class);

                    if(br!=null){
                        List<Cash> chosenCashList = chooseCasheList(newCashList, chosenSize, br);
                        cashList.addAll(chosenCashList);
                        chosenSize += chosenCashList.size();
                        if(!askIfYes(br,"Do you want to continue to get cashes?")){
                            break;
                        }
                    }else cashList.addAll(newCashList);

                } else return cashList;
                last = ObjectTools.objectToList(fcReplierHttp.getData(), String.class);//DataGetter.getStringList(fcReplier.getLast());
            } while (cashList.size() < fcReplierHttp.getTotal());
        } else if (this.nasaClient != null) {
            fcReplierHttp = getCashListFromNasaNode(fid, null, true, nasaClient);
            if (fcReplierHttp.getCode() != 0) {
                log.debug(fcReplierHttp.getMessage());
                return cashList;
            }
            if (fcReplierHttp.getData() != null){   
                List<Cash> newCashList = ObjectTools.objectToList(fcReplierHttp.getData(), Cash.class);
                if(br!=null){
                    List<Cash> chosenCashList = chooseCasheList(newCashList, chosenSize, br);
                    cashList.addAll(chosenCashList);
                    chosenSize += chosenCashList.size();
                }else cashList.addAll(newCashList);
            }else return cashList;
        }
        return cashList;
    }

    public List<Cash> getIssuingCashListFromJedis(String addr) {
        List<Cash> issuingCashList = new ArrayList<>();
        Gson gson = new Gson();
        try (Jedis jedis3Mempool = new Jedis()) {
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

    public String[] getSpendingCashIdFromJedis(String addr) {
        Gson gson = new Gson();
        try (Jedis jedis3Mempool = new Jedis()) {
            jedis3Mempool.select(Constants.RedisDb3Mempool);
            String spendCashIdStr = jedis3Mempool.hget(addr, FieldNames.SPEND_CASHES);
            if (spendCashIdStr != null) {
                return gson.fromJson(spendCashIdStr, String[].class);
            }
        }
        return null;
    }

    public static SearchResult<Cash> getCashes(String myFid,Long amount, Long cd, Long sinceHeight, int outputSize, int msgSize,ApipClient apipClient,ElasticsearchClient esClient, JedisPool jedisPool) {
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
            if(myFid!=null)fcdsl.addNewQuery().addNewEquals().addNewFields(OWNER).addNewValues(myFid);
            if(sinceHeight!=null)fcdsl.getQuery().addNewRange().addNewFields(BIRTH_HEIGHT).addGt(String.valueOf(sinceHeight));
            fcdsl.addSize(200);
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
                checkUnconfirmedSpentByJedisPool(cashList, jedisPool);
                searchResult.setData(cashList);
                searchResult.setGot((long) cashList.size());
                return searchResult;
            }
        }else if(esClient!=null){
            cashList = new ArrayList<>();
            SearchResponse<Cash> result;
            try {
                SearchRequest.Builder searchBuilder = new SearchRequest.Builder();
                searchBuilder.index(IndicesNames.CASH)
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
                checkUnconfirmedSpentByJedisPool(cashList, jedisPool);
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

        checkUnconfirmedSpentByJedisPool(cashList, jedisPool);

        amount = amount == null ? 0L : amount;
        cd = cd == null ? 0L : cd;
        long fchSum = 0;
        long cdSum = 0;
        long fee = 0;
        List<Cash> meetList = new ArrayList<>();

        for (Cash cash : cashList) {
            if(Boolean.TRUE.equals(isImmature(cash, bestheight)))continue;
            long cdd = ParseTools.cdd(cash.getValue(), cash.getBirthTime(), System.currentTimeMillis()/1000);
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

    // Cash List Retrieval Methods
    public List<Cash> getCashListFromApip(String fid, Boolean valid, int size,
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
            bestHeight = apipClient.getBestHeight();
            if(last!=null){
                last.clear();
                last.addAll(apipClient.getFcClientEvent().getResponseBody().getLast());
            }
        }
        return result;
    }

    @NotNull
    public FcReplierHttp getCashListFromEs(String fid, Boolean valid, int size,
                                           ArrayList<Sort> sortList, List<String> last, ElasticsearchClient esClient) {
        FcReplierHttp replier = new FcReplierHttp();
        SearchRequest.Builder sb = new SearchRequest.Builder();
        sb.index(IndicesNames.CASH);
        sb.trackTotalHits(t -> t.enabled(true));
        if (size > 0) sb.size(size);
        else sb.size(200);
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

        BoolQuery.Builder bb = new BoolQuery.Builder();
        List<Query> queryList = new ArrayList<>();
        TermQuery tq1 = new TermQuery.Builder().field(OWNER).value(fid).build();
        queryList.add(new Query(tq1));

        if (valid!=null ){
            if(valid)queryList.add(new Query(new TermQuery.Builder().field(VALID).value(TRUE).build()));
            else queryList.add(new Query(new TermQuery.Builder().field(VALID).value(FALSE).build()));
        }

        bb.must(queryList);

        qb.bool(bb.build());

        sb.query(qb.build());

        SearchRequest searchRequest = sb.build();
        SearchResponse<Cash> result;
        try {
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
                replier.setData(cashList);
                replier.setGot((long) cashList.size());
                replier.setTotal((long) result.hits().hits().size());
                if (newLast != null)
                    replier.setLast(newLast);
                replier.set0Success();
            }
        } catch (IOException e) {
            log.error("EsClient error:{}", e.getMessage());
            replier.setOtherError("EsClient error:" + e.getMessage());
        }
        return replier;
    }
    
    public FcReplierHttp getCashListFromNasaNode(String fid, String minConf,
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
        FcReplierHttp fcReplierHttp = new FcReplierHttp();
        fcReplierHttp.set0Success();
        fcReplierHttp.setData(cashList);
        return fcReplierHttp;
    }

    // Unconfirmed Transaction Methods
    public static void checkUnconfirmedSpentByJedisPool(List<Cash> meetList, JedisPool jedisPool) {
        if(jedisPool==null)return;
        try (Jedis jedis1 = jedisPool.getResource()) {
            checkUnconfirmedSpentByJedis(meetList, jedis1);
        }
    }

    public static void checkUnconfirmedSpentByJedis(List<Cash> cashList, Jedis jedis) {
        jedis.select(Constants.RedisDb3Mempool);
        cashList.removeIf(cash -> jedis.hget(FieldNames.SPEND_CASHES, cash.getCashId()) != null);
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

    public List<Cash> pullAllCashes(@Nullable BufferedReader br, boolean choose) {
         List<Cash> finalCashes = new ArrayList<>();
         List<Cash> subCashList;
         int totalDisplayed = 0;
         int got = 0;
         Fcdsl fcdsl = new Fcdsl();
         fcdsl.addIndex(IndicesNames.CASH);
         fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.OWNER).addNewValues(myFid);
         fcdsl.addNewFilter().addNewTerms().addNewFields(FieldNames.VALID).addNewValues(Values.TRUE);
         fcdsl.addSort(FieldNames.BIRTH_HEIGHT, DESC).addSort(FieldNames.BIRTH_TX_INDEX, ASC).addSort(FieldNames.BIRTH_INDEX, DESC);
         fcdsl.addSize(BATCH_SIZE);
         List<String> last = null;

         while(true){
             if(last!=null){
                 fcdsl.setAfter(last);
             }
             FcReplierHttp fcReplierHttp = apipClient.general(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
             if(fcReplierHttp==null)break;
             last = fcReplierHttp.getLast();
             subCashList = ObjectTools.objectToList(fcReplierHttp.getData(), Cash.class);
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
                 Cash.showCashList(subCashList, "Got Cashes", totalDisplayed, myFid);
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

    public List<Cash> chooseCasheList(List<Cash> currentList, int totalDisplayed, BufferedReader br) {
         List<Cash> chosenCashes = new ArrayList<>();

         String title = "Choose Cashes";
         Cash.showCashList(currentList, title, totalDisplayed, myFid);

         System.out.println("Enter cash numbers to select (comma-separated), 'a' to select all, 'q' to quit, or press Enter for more:");
         String input = Inputer.inputString(br);

         if ("".equals(input)) {
             return chosenCashes;
         }
         if (input.equals("a")) {
             chosenCashes.addAll(currentList);
             return chosenCashes;
         }

         if (input.equals("q")) {
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

    // Utility Methods

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
            Integer jumpNum = unsafeIdJumpNumMap.get(cash.getCashId());
            if (jumpNum == null) continue;
            else if (jumpNum > maxJumpNum) maxJumpNum = jumpNum;
        }
        return maxJumpNum;
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

    // Helper Classes
    public static class SearchResult<T> {
        private List<T> data;
        private String message;
        private Long got;
        private Long total;

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
    }

    // Sample Data Methods
    public static Cash createSampleCash() {
        Cash cash = new Cash();
        cash.setOwner("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");  // Example FID
        cash.setBirthTxId("6b86b273ff34fce19d6b804eff5a3f5747ada4eaa22f1d49c01e52ddb7875b4b");  // Example transaction ID
        cash.setBirthIndex(0);
        cash.setValue(100000000L);  // 1 FCH in satoshis
        cash.setCashId(ParseTools.calcTxoId(cash.getBirthTxId(), cash.getBirthIndex()));
        cash.setBirthTime(System.currentTimeMillis() / 1000);  // Current Unix timestamp
        cash.setBirthHeight(100000L);  // Example block height
        cash.setValid(true);
        
        return cash;
    }

    public Map<String, Cash> getValidCashMap() {
        return validCashMap;
    }

    public void setValidCashMap(Map<String, Cash> validCashMap) {
        this.validCashMap = validCashMap;
    }

    public String getMyFid() {
        return myFid;
    }

    public void setMyFid(String myFid) {
        this.myFid = myFid;
    }

    public String getMyPriKeyCipher() {
        return myPriKeyCipher;
    }

    public void setMyPriKeyCipher(String myPriKeyCipher) {
        this.myPriKeyCipher = myPriKeyCipher;
    }



}
