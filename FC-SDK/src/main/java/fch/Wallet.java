package fch;

import apip.apipData.Fcdsl;
import apip.apipData.Sort;
import clients.apipClient.ApipClient;
import clients.esClient.EsTools;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import fch.fchData.*;
import fcData.FcReplier;
import javaTools.Hex;
import javaTools.ObjectTools;
import javaTools.http.AuthType;
import javaTools.http.HttpRequestMethod;
import nasa.NaSaRpcClient;
import nasa.data.TxInput;
import nasa.data.TxOutput;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.google.gson.Gson;

import constants.*;
import crypto.KeyTools;
import crypto.Hash;
import javaTools.JsonTools;
import nasa.data.UTXO;
import org.bitcoinj.core.ECKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tx.DogeTxMaker;
import tx.TxInputDoge;
import tx.TxOutputDoge;


import javaTools.BytesTools;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

import static constants.ApiNames.Version2;
import static constants.Constants.COINBASE;
import static constants.Constants.COIN_TO_SATOSHI;
import static constants.FieldNames.*;
import static constants.Strings.ASC;
import static constants.Strings.DESC;
import static constants.Values.TRUE;
import static fch.TxCreator.*;
import static fch.fchData.Cash.sumCashValue;
import static tx.DogeTxMaker.getPriKey32;

public class Wallet {
    private static final Logger log = LoggerFactory.getLogger(Wallet.class);
    private ApipClient apipClient;
    private ElasticsearchClient esClient;
    private NaSaRpcClient nasaClient;
    public static final double Million = 100000000d;

    public Wallet() {
    }

    public Wallet(ApipClient apipClient) {
        this.apipClient = apipClient;
    }

    public Wallet(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    public Wallet(NaSaRpcClient nasaClient) {
        this.nasaClient = nasaClient;
    }

    public Wallet(ApipClient apipClient, ElasticsearchClient esClient, NaSaRpcClient nasaClient) {
        this.apipClient = apipClient;
        this.esClient = esClient;
        this.nasaClient = nasaClient;
    }

    public static String sendTxByApip(byte[] priKey, List<SendTo> sendToList, String opReturnStr, long cd, int maxCashes, ApipClient apipClient) {
        String txSigned = makeTx(priKey, null, sendToList, opReturnStr, cd, maxCashes, apipClient, null);
        apipClient.broadcastTx(txSigned, HttpRequestMethod.POST, AuthType.FC_SIGN_BODY);
        Object data = apipClient.checkResult();
        return (String)data;
    }
    public static String makeTxByEs(byte[] priKey, List<SendTo> sendToList, String opReturnStr, long cd, int maxCashes, ElasticsearchClient esClient) {
        return makeTx(priKey,null , sendToList, opReturnStr, cd, maxCashes, null, esClient);
    }

    public static String makeTxForCs(String fid, List<SendTo> sendToList, String opReturnStr, long cd, int maxCashes, ApipClient apipClient) {
        return makeTx(null,fid , sendToList, opReturnStr, cd, maxCashes, apipClient,null);
    }
    public static String makeTx(byte[] priKey, String fidForOfflineSign, List<SendTo> sendToList, String opReturnStr, long cd, int maxCashes, ApipClient apipClient, ElasticsearchClient esClient) {
        String fid;
        if(priKey!=null)fid = KeyTools.priKeyToFid(priKey);
        else fid = fidForOfflineSign;
        long amount=0;
        if(sendToList==null)sendToList = new ArrayList<>();
        if(!sendToList.isEmpty()){
            for(SendTo sendTo : sendToList){
                amount += ParseTools.coinToSatoshi(sendTo.getAmount());
            }
        }
        List<Cash> cashList;
        long bestHeight = 0;

        if(apipClient!=null) {
            cashList = getCashListFromApip(fid, maxCashes, apipClient);
            bestHeight = apipClient.getFcClientEvent().getResponseBody().getBestHeight();
        }else if(esClient!=null){
            FcReplier replier = getCashListFromEs(fid, true, maxCashes, null, null, esClient);
            if(replier.getCode()!=0)return null;
            cashList = ObjectTools.objectToList(replier.getData(),Cash.class);//DataGetter.getCashList(replier.getData());
            bestHeight = getBestHeight(esClient);
        }else return null;

        Iterator<Cash> iter = cashList.iterator();
        List<Cash> spendCashList = new ArrayList<>();
        long valueSum = 0;
        long cdSum=0;
        while(iter.hasNext()){
            Cash cash = iter.next();
            if(cash.getIssuer()!=null && cash.getIssuer().equals(COINBASE) && (bestHeight-cash.getBirthHeight())< Constants.TenDayBlocks) {
                iter.remove();
                continue;
            }
            spendCashList.add(cash);
            long fee = calcTxSize(spendCashList.size(), sendToList.size(), opReturnStr.length());
            valueSum += cash.getValue();
            cdSum += cash.getCd();
            if(valueSum>=(amount +fee) && cdSum>= cd)break;
        }

        if(priKey!=null)
            return createTransactionSignFch(spendCashList, priKey, sendToList, opReturnStr);
        else return makeTxForOfflineSign(sendToList,opReturnStr,cashList);
    }

    public static String makeTxForOfflineSign(List<SendTo> sendToList,String opReturn,List<Cash> meetList){

        Gson gson = new Gson();
        StringBuilder RawTx = new StringBuilder("[");
        int i =0;
        for(Cash cash:meetList){
            if(i>0)RawTx.append(",");
            RawTxForCs rawTxForCs = new RawTxForCs();
            rawTxForCs.setAddress(cash.getOwner());
            rawTxForCs.setAmount((double) cash.getValue() / Constants.FchToSatoshi);
            rawTxForCs.setTxid(cash.getBirthTxId());
            rawTxForCs.setIndex(cash.getBirthIndex());
            rawTxForCs.setSeq(i);
            rawTxForCs.setDealType(1);
            RawTx.append(gson.toJson(rawTxForCs));
            i++;
        }
        int j = 0;
        if(sendToList!=null) {
            for (SendTo sendTo : sendToList) {
                RawTxForCs rawTxForCs = new RawTxForCs();
                rawTxForCs.setAddress(sendTo.getFid());
                rawTxForCs.setAmount(sendTo.getAmount());
                rawTxForCs.setSeq(j);
                rawTxForCs.setDealType(2);
                RawTx.append(",");
                RawTx.append(gson.toJson(rawTxForCs));
                j++;
            }
        }

        if(opReturn!=null) {
            RawOpReturnForCs rawOpReturnForCs = new RawOpReturnForCs();
            rawOpReturnForCs.setMsg(opReturn);
            rawOpReturnForCs.setMsgType(2);
            rawOpReturnForCs.setSeq(j);
            rawOpReturnForCs.setDealType(3);
            RawTx.append(",");
            RawTx.append(gson.toJson(rawOpReturnForCs));
        }
        RawTx.append("]");
        return RawTx.toString();
    }
    private static List<Cash> getCashListFromApip(String fid, int maxCashes, ApipClient apipClient) {
        List<Cash> cashList;
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(OWNER).addNewValues(fid);
        fcdsl.addNewFilter().addNewTerms().addNewFields(VALID).addNewValues(TRUE);
        fcdsl.addSize(maxCashes);
        fcdsl.addSort(CD, ASC).addSort(CASH_ID, ASC);
        return apipClient.cashSearch(fcdsl, HttpRequestMethod.POST, AuthType.FC_SIGN_BODY);//BlockchainAPIs.cashSearchPost(apipClient.getApiAccount().getApiUrl(), fcdsl, apipClient.getApiAccount().getVia(), apipClient.getSessionKey());
    }

    public static FcReplier sendTx(String txSigned, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient naSaRpcClient) {
        FcReplier fcReplier = new FcReplier();
        if(naSaRpcClient!=null){
            String txid = naSaRpcClient.sendRawTransaction(txSigned);
            long bestHeight = naSaRpcClient.getBestHeight();
            if(Hex.isHexString(txid)) {
                fcReplier.Set0Success();
                fcReplier.setData(txid);
                fcReplier.setBestHeight(bestHeight);
            }
        }else if(apipClient!=null){
            apipClient.broadcastTx(txSigned, HttpRequestMethod.POST, AuthType.FC_SIGN_BODY);
            return apipClient.getFcClientEvent().getResponseBody();
        }else fcReplier.setOtherError("No client to send tx.");
        return fcReplier;
    }

    public Double getFeeRate()  {
        if(apipClient!=null)return apipClient.feeRate(HttpRequestMethod.GET, AuthType.FREE);
        if(esClient!=null)return calcFeeRate(esClient);
        if(nasaClient!=null)return nasaClient.estimateFee(3);
        return 0.001D;
    }

    public static Double calcFeeRate(ElasticsearchClient esClient) {
        SearchResponse<Block> result = null;
        try {
            result = esClient.search(s -> s.index(IndicesNames.BLOCK).size(20).sort(sort -> sort.field(f -> f.field(HEIGHT).order(SortOrder.Desc))), Block.class);
        } catch (IOException e) {
            log.error("ElasticSearch Client wrong when calculating fee rate.");
            return null;
        }
        if(result==null || result.hits()==null)return null;
        List<Block> blockList = new ArrayList<>();
        Block expensiveBlock = new Block();
        expensiveBlock.setFee(0L);
        for(Hit<Block> hit :result.hits().hits()){
            Block block = hit.source();
            if(block==null || block.getTxCount()==0) continue;
            blockList.add(block);
            if (block.getFee()>expensiveBlock.getFee())
                expensiveBlock = block;
        }
        if(blockList.isEmpty())return 0D;
        blockList.remove(expensiveBlock);
        if(blockList.isEmpty())return 0d;
        double feeSum=0;
        double netBlockSizeSum = 0;
        for(Block block :blockList){
            feeSum += block.getFee();
            netBlockSizeSum += (block.getSize()-Constants.EMPTY_BLOCK_SIZE);
        }
        if(feeSum==0 || (double) (netBlockSizeSum /20) < 0.7*Constants.M_BYTES)return Constants.MIN_FEE_RATE;
        return  Math.max((double) (feeSum / netBlockSizeSum) /1000,Constants.MIN_FEE_RATE);
    }

    public void mergeCashList(List<Cash> cashList, byte[] priKey) {
        Iterator<Cash> iter = cashList.iterator();
        for(int i=0;i<=cashList.size()%100;i++){
            List<Cash> subCashList = new ArrayList<>();
            int j=0;
            while (iter.hasNext()){
                Cash cash = iter.next();
                subCashList.add(cash);
                iter.remove();
                j++;
                if(j==100)break;
            }
            mergeCashList(subCashList,0,priKey);
        }
    }

    public String mergeCashList(List<Cash> cashList, int issueNum, byte[] priKey) {
        if(cashList==null|| cashList.isEmpty())return null;
        String fid = KeyTools.priKeyToFid(priKey);

        Object data;
        long fee = calcTxSize(cashList.size(), issueNum, 0);

        long sumValue = sumCashValue(cashList)-fee;
        if(sumValue<0) {
            System.out.println("Cash value is too small:"+sumValue+fee+". Try again.");
            return null;
        }
        long valueForOne = sumValue/ issueNum;
        if(valueForOne < Constants.SatoshiDust){
            System.out.println("The sum of all cash values is too small to split. Try again.");
            return null;
        }
        List<SendTo> sendToList = new ArrayList<>();
        SendTo sendTo = new SendTo();
        sendTo.setFid(fid);
        sendTo.setAmount(ParseTools.satoshiToCoin(valueForOne));
        for(int i = 0; i< issueNum -1; i++)sendToList.add(sendTo);
        SendTo sendTo1 = new SendTo();
        sendTo1.setFid(fid);
        long lastCashValue = sumValue - (valueForOne * (issueNum - 1));
        sendTo1.setAmount(ParseTools.satoshiToCoin(lastCashValue));
        sendToList.add(sendTo1);

        String txSigned = createTransactionSignFch(cashList, priKey, sendToList, null);

        if(apipClient!=null) {
            apipClient.broadcastTx(txSigned, HttpRequestMethod.POST, AuthType.FC_SIGN_BODY );
            data = apipClient.checkResult();
        }else if(nasaClient!=null){
            data = nasaClient.sendRawTransaction(txSigned);
        }else return null;
        return (String)data;
    }

    public List<Cash> getAllCashList(String fid, boolean onlyValid, int size, ArrayList<Sort> sortList, List<String> last){
        List<Cash> cashList = new ArrayList<>();

        FcReplier fcReplier;
        if(this.apipClient!=null) {
            do {
                List<Cash> newCashList = getCashListFromApip(fid, onlyValid, size, sortList, last, apipClient);
                if(newCashList==null){
                    log.debug(this.apipClient.getFcClientEvent().getMessage());
                    return cashList;
                }
                if(newCashList.isEmpty())return cashList;
                cashList.addAll(newCashList);

                fcReplier= this.apipClient.getFcClientEvent().getResponseBody();
                last = fcReplier.getLast();
            }while(cashList.size()<fcReplier.getTotal());
        }
        else if (this.esClient!=null) {
            do {
                fcReplier = getCashListFromEs(fid, onlyValid, size, sortList, last, esClient);
                if(fcReplier.getCode()!=0){
                    log.debug(fcReplier.getMessage());
                    break;
                }
                if(fcReplier.getData()!=null){
                    cashList.addAll(ObjectTools.objectToList(fcReplier.getData(),Cash.class));//DataGetter.getCashList(fcReplier.getData()));
                }
                else return cashList;
                last = ObjectTools.objectToList(fcReplier.getData(),String.class);//DataGetter.getStringList(fcReplier.getLast());
            }while(cashList.size()<fcReplier.getTotal());
        }
        else if (this.nasaClient!=null){
            fcReplier =getCashListFromNasaNode(fid,null,true,nasaClient);
            if(fcReplier.getCode()!=0){
                log.debug(fcReplier.getMessage());
                return cashList;
            }
            if(fcReplier.getData()!=null)cashList.addAll(ObjectTools.objectToList(fcReplier.getData(),Cash.class));//DataGetter.getCashList(fcReplier.getData()));
            else return cashList;
        }
        return cashList;
    }

    public static List<Cash> getCashListFromApip(String fid, boolean onlyValid, int size, ArrayList<Sort> sortList, List<String> last, ApipClient apipClient) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addSize(size);
        if(sortList!=null && !sortList.isEmpty())
            fcdsl.setSort(sortList);
        fcdsl.addNewQuery().addNewTerms().addNewFields(OWNER).addNewValues(fid);
        if(onlyValid)
            fcdsl.addNewFilter().addNewTerms().addNewFields(VALID).addNewValues(TRUE);
        if(last!=null && !last.isEmpty())
            fcdsl.setAfter(last);
        return apipClient.cashSearch(fcdsl, HttpRequestMethod.POST, AuthType.FC_SIGN_BODY);//BlockchainAPIs.cashSearchPost(apipClient.getApiAccount().getApiUrl(), fcdsl, apipClient.getApiAccount().getVia(), apipClient.getSessionKey());
    }


    public static CashListReturn getAllCashList(long value, long cd, int outputNum, int opReturnLength, String addrRequested, ElasticsearchClient esClient) {

        CashListReturn cashListReturn = new CashListReturn();

        String index = IndicesNames.CASH;

        SearchResponse<Cash> result;
        try {
            SearchRequest.Builder searchBuilder = new SearchRequest.Builder();
            searchBuilder.index(index);
            searchBuilder.trackTotalHits(tr->tr.enabled(true));
            searchBuilder.sort(s1->s1.field(f->f.field(FieldNames.CD).order(SortOrder.Asc)));
            searchBuilder.size(200);

            BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

            boolQueryBuilder.must(m->m.term(t -> t.field(FieldNames.OWNER).value(addrRequested)));
            boolQueryBuilder.must(m1->m1.term(t1->t1.field(FieldNames.VALID).value(true)));

            searchBuilder.query(q->q.bool(boolQueryBuilder.build()));

            result = esClient.search(searchBuilder.build(),Cash.class);

        } catch (IOException e) {
            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
            cashListReturn.setMsg("Can't get cashes. Check ES.");
            return cashListReturn;
        }

        if(result==null){
            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
            cashListReturn.setMsg("Can't get cashes.Check ES.");
            return cashListReturn;
        }

        assert result.hits().total() != null;
        long total = result.hits().total().value();

        long valueSum = 0;//(long)result.aggregations().get(FieldNames.SUM).valueSum().value();
        long cdSum = 0;
        long fee = 0;


        List<Cash> cashList = new ArrayList<>();
        List<Hit<Cash>> hitList = result.hits().hits();

        long bestHeight = getBestHeight(esClient);

        for(Hit<Cash> hit : hitList){
            Cash cash = hit.source();
            if(cash==null)continue;
            if(cash.getIssuer()!=null && cash.getIssuer().equals(COINBASE)&& cash.getBirthHeight()>(bestHeight-Constants.OneDayInterval *10))
                continue;
            cashList.add(cash);
        }

        List<Cash> issuingCashList = getIssuingCashList(addrRequested);
        if(issuingCashList!=null && issuingCashList.size()>0) {
            for (Cash cash : issuingCashList) {
                cashList.add(cash);
            }
        }

        String[] spendingCashIds = getSpendingCashId(addrRequested);
        if (spendingCashIds != null) {
            for (String id : spendingCashIds) {
                Iterator<Cash> iter = cashList.iterator();
                while (iter.hasNext()) {
                    Cash cash = iter.next();
                    if (id.equals(cash.getCashId())) {
                        iter.remove();
                        break;
                    }
                }
            }
        }

        List<Cash> meetList = new ArrayList<>();
        boolean done = false;
        for(Cash cash : cashList){
            meetList.add(cash);
            valueSum+=cash.getValue();
            cdSum += cash.getCd();
            fee = calcTxSize(cashList.size(),outputNum,opReturnLength);

            if(valueSum>(value+fee) && cdSum > cd) {
                done = true;
                break;
            }
        }

        if(!done){
            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
            cashListReturn.setMsg("Can't meet the conditions.");
            return cashListReturn;
        }
        cashListReturn.setTotal(total);
        cashListReturn.setCashList(meetList);

        return cashListReturn;
    }

    public static List<Cash> getIssuingCashList(String addr) {
        List<Cash> issuingCashList = new ArrayList<>();
        Gson gson = new Gson();
        try(Jedis jedis3Mempool = new Jedis()) {
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
        if(issuingCashList.size()==0)return null;
        return issuingCashList;
    }

    public static String[] getSpendingCashId(String addr) {
        Gson gson = new Gson();
        try(Jedis jedis3Mempool = new Jedis()) {
            jedis3Mempool.select(Constants.RedisDb3Mempool);
            String spendCashIdStr = jedis3Mempool.hget(addr, FieldNames.SPEND_CASHES);
            if (spendCashIdStr != null) {
                return gson.fromJson(spendCashIdStr, String[].class);
            }
        }
        return null;
    }

    public Long getBestHeight(){
        try {
            if(nasaClient!=null)return nasaClient.getBestHeight();
            if(esClient!=null)return getBestHeight(esClient);
            if (apipClient != null) {
                apipClient.ping(Version2,HttpRequestMethod.POST,AuthType.FC_SIGN_BODY, null);
                return apipClient.getFcClientEvent().getResponseBody().getBestHeight();
            }
        }catch (Exception ignore){}
        return null;
    }
    public static long getBestHeight(ElasticsearchClient esClient) {
        long bestHeight = 0;
        try {
            Block bestBlock = EsTools.getBestBlock(esClient);
            if(bestBlock!=null)
                bestHeight=bestBlock.getHeight();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return bestHeight;
    }
    public FcReplier getCashListFromNasaNode(String fid, String minConf, boolean includeUnsafe,NaSaRpcClient naSaRpcClient){
        UTXO[] utxos = new NaSaRpcClient(naSaRpcClient.getUrl(), naSaRpcClient.getUsername(), naSaRpcClient.getPassword()).listUnspent(fid, minConf,includeUnsafe);
        List<Cash> cashList = new ArrayList<>();
        for(UTXO utxo:utxos){
            Cash cash = new Cash();
            cash.setOwner(utxo.getAddress());
            cash.setBirthTxId(utxo.getTxid());
            cash.setBirthIndex(utxo.getVout());
            cash.setValue(ParseTools.coinToSatoshi(utxo.getAmount()));
            cash.setLockScript(utxo.getRedeemScript());
            cashList.add(cash);
        }
        FcReplier fcReplier = new FcReplier();
        fcReplier.set0Success();
        fcReplier.setData(cashList);
        return fcReplier;
    }
    @NotNull
    public static FcReplier getCashListFromEs(String fid, boolean onlyValid, int size, ArrayList<Sort> sortList, List<String> last,ElasticsearchClient esClient) {
        FcReplier replier = new FcReplier();
        SearchRequest.Builder sb = new SearchRequest.Builder();
        sb.index(IndicesNames.CASH);
        sb.trackTotalHits(t->t.enabled(true));
        if(size >0)sb.size(size);
        else sb.size(200);
        if(sortList!=null && !sortList.isEmpty()){
            List<SortOptions> sortOptionslist = Sort.getSortList( sortList);
            sb.sort(sortOptionslist);
        }else {
            List<SortOptions> sortOptionsList = Sort.makeTwoFieldsSort(BIRTH_HEIGHT,DESC,CASH_ID,ASC);
            sb.sort(sortOptionsList);
        }
        if(last !=null && !last.isEmpty())
            sb.searchAfter(last);

        Query.Builder qb = new Query.Builder();

        BoolQuery.Builder bb = new BoolQuery.Builder();
        List<Query> queryList = new ArrayList<>();
        TermQuery tq1 = new TermQuery.Builder().field(OWNER).value(fid).build();
        queryList.add(new Query(tq1));

        TermQuery tq2 = new TermQuery.Builder().field(VALID).value(TRUE).build();
        if(onlyValid)
            queryList.add(new Query(tq2));

        bb.must(queryList);

        qb.bool(bb.build());

        sb.query(qb.build());

        SearchRequest searchRequest = sb.build();
        SearchResponse<Cash> result;
        try {
            result = esClient.search(searchRequest, Cash.class);
            if(result.hits()==null|| result.hits().hits()==null){
                log.error("Failed to get Hits from esClient.");
                replier.setOtherError("Failed to get Hits from esClient.");
            }
            else if(result.hits().hits().size()==0){
                replier.set0Success();
                replier.setGot(0L);
                replier.setTotal(0L);
            }else {
                List<String> newLast = null;
                List<Cash> cashList = new ArrayList<>();
                for (Hit<Cash> hit : result.hits().hits()) {
                    cashList.add(hit.source());

                    newLast = hit.sort();
                }
                replier.setData(cashList);
                replier.setGot((long) cashList.size());
                if (result.hits().total() != null)
                    replier.setTotal(result.hits().total().value());
                if (newLast != null)
                    replier.setLast(newLast);
                replier.set0Success();
            }
        } catch (IOException e) {
            log.error("EsClient error:{}",e.getMessage());
            replier.setOtherError("EsClient error:"+e.getMessage());
        }
        return replier;
    }

    public String send(String fid,List<SendTo> sendToList, byte[] opReturn, ApipClient apipClient,ElasticsearchClient esClient){

        return null;
    }

    public boolean mergeCash(String fid, long maxCd, int maxCount, ApipClient apipClient,ElasticsearchClient esClient){

        return true;
    }

    public boolean splitCash(String fid, long maxCd,ApipClient apipClient,ElasticsearchClient esClient){

        return true;
    }

    public String sendFch(List<Cash> cashList, byte[] priKey, String toAddr, double toAmount, String msg, String urlHead) {
        SendTo sendTo = new SendTo();
        sendTo.setFid(toAddr);
        sendTo.setAmount(toAmount);
        List<SendTo> sendToList = new ArrayList<>();
        sendToList.add(sendTo);

        String txSigned = createTransactionSignFch(cashList, priKey, sendToList, msg);

        System.out.println("Broadcast with " + urlHead + " ...");
        return apipClient.broadcastTx(txSigned, HttpRequestMethod.POST, AuthType.FC_SIGN_BODY);
    }

    private static CashToInputsResult cashListToInputs(List<Cash> cashList) {
        if (cashList == null) return null;
        List<Map<String, Object>> inputs = new LinkedList<>();
        long inputSum = 0;
        long cdSum = 0;
        for (Cash cash : cashList) {
            Map<String, Object> transactionInput = new LinkedHashMap<>();
            transactionInput.put("txid", cash.getBirthTxId());
            transactionInput.put("vout", cash.getBirthIndex());
            inputs.add(transactionInput);
            inputSum = inputSum + cash.getValue();
            cdSum = cdSum + cash.getCd();
        }
        CashToInputsResult cashToInputsResult = new CashToInputsResult();
        cashToInputsResult.setInputs(inputs);
        cashToInputsResult.setValueSum(inputSum);
        cashToInputsResult.setCdSum(cdSum);

        return cashToInputsResult;
    }

    public static List<Cash> getCashListForCd(ElasticsearchClient esClient, String addr, long cd) throws IOException {

        List<Cash> cashList = new ArrayList<>();
        SearchResponse<Cash> result = esClient.search(s -> s.index("cash")
                .query(q -> q.bool(b -> b.must(m -> m
                                .term(t -> t.field("owner").value(addr))
                        ).must(m1 -> m1.term(t1 -> t1.field("valid").value(true))))
                )
                .trackTotalHits(tr -> tr.enabled(true))
                .aggregations("sum", a -> a.sum(s1 -> s1.field("cd")))
                .sort(s1 -> s1.field(f -> f.field("cd").order(SortOrder.Asc).field("value").order(SortOrder.Asc)))
                .size(100), Cash.class);

        if (result == null) return null;

        long sum = (long) result.aggregations().get("sum").sum().value();

        if (sum < cd) return cashList;

        List<Hit<Cash>> hitList = result.hits().hits();
        if (hitList.size() == 0) return cashList;

        for (Hit<Cash> hit : hitList) {
            Cash cash = hit.source();
            cashList.add(cash);
        }

        checkUnconfirmed(addr, cashList);

        List<Cash> meetList = new ArrayList<>();
        long adding = 0;
        for (Cash cash : cashList) {
            adding += cash.getCd();
            meetList.add(cash);
            if (adding > cd) break;
        }
        if (adding < cd) {
            System.out.println("More the 100 cashes was required. Merge small cashes first please.");
            return null;
        }
        return meetList;
    }

    public static void checkUnconfirmed(String addr, List<Cash> meetList) {
        Gson gson = new Gson();
        try (Jedis jedis3Mempool = new Jedis()) {
            jedis3Mempool.select(Constants.RedisDb3Mempool);
            String spendCashIdStr = jedis3Mempool.hget(addr, FieldNames.SPEND_CASHES);
            if (spendCashIdStr != null) {
                String[] spendCashIdList = gson.fromJson(spendCashIdStr, String[].class);
                Iterator<Cash> iter = meetList.iterator();
                while (iter.hasNext()) {
                    Cash cash = iter.next();
                    for (String id : spendCashIdList) {
                        if (id.equals(cash.getCashId())) iter.remove();
                        break;
                    }
                }
            }

            String newCashIdStr = jedis3Mempool.hget(addr, FieldNames.NEW_CASHES);
            if (newCashIdStr != null) {
                String[] newCashIdList = gson.fromJson(newCashIdStr, String[].class);
                for (String id : newCashIdList) {
                    Cash cash = gson.fromJson(jedis3Mempool.hget(FieldNames.NEW_CASHES, id), Cash.class);
                    if (cash != null) meetList.add(cash);
                }
            }
        }
    }

    private static Map<String, Object> splitToOutputs(String addr, int outCount, CashToInputsResult inputResult){
        if (outCount > 100) {
            System.out.println("Error, outCount > 100.");
        }
        long valueSum = inputResult.getValueSum();
        long valueEach = valueSum / outCount;
        if (valueEach < 100000) {
            outCount = (int) (valueSum / 100000);
            valueEach = 100000;
            if (outCount < 1) {
                System.out.println("Error, value of each output < 0.001.");
                return null;
            }
        }
        Map<String, Object> outputs = new LinkedHashMap<>();


        List<Map<String, Object>> inputs = inputResult.getInputs();
        long fee = calcTxSize(inputs.size(), outputs.size(), 0);
        outputs.put(addr, (valueSum - valueEach * (outCount - 1) - fee) / Million);

        return outputs;
    }
//
//    public static String splitCashes(ElasticsearchClient esClient, JsonRpcHttpClient fcClient, String addr, int inCount, int outCount) throws Throwable {
//
//        System.out.println("get cash list.");
//        SearchResponse<Cash> result = esClient.search(s -> s.index("cash")
//                .query(q ->q.bool(b->b.must(m->m
//                                .term(t -> t.field("owner").value(addr))
//                        ).must(m1->m1.term(t1->t1.field("valid").value(true))))
//                )
//                .trackTotalHits(tr->tr.enabled(true))
//                .aggregations("cdSum",a->a.sum(s1->s1.field("cd")))
//                .aggregations("valueSum",a->a.sum(s1->s1.field("value")))
//                .sort(s1->s1.field(f->f.field("cd").order(SortOrder.Asc).field("value").order(SortOrder.Asc)))
//                .size(inCount), Cash.class);
//
//        if(result==null) return addr;
//
//        long cdSum = (long)result.aggregations().get("cdSum").sum().value();
//        long valueSum = (long)result.aggregations().get("valueSum").sum().value();
//        System.out.println(cdSum + " "+ valueSum);
//
//        List<Hit<Cash>> hitList = result.hits().hits();
//        int size = hitList.size();
//        if(size==0) return addr;
//
//        System.out.println(size);
//
//        List<Cash> cashList = new ArrayList<>();
//        for(Hit<Cash> hit : hitList){
//            Cash cash = hit.source();
//            cashList.add(cash);
//        }
//
//        checkUnconfirmed(addr,cashList);
//
//        FCH.CashToInputsResult inputResult = cashListToInputs(cashList);
//
//        List<Map<String, Object>> inputs = inputResult.getInputs();
//
//        Map<String, Object> outputs = splitToOutputs(addr,outCount, inputResult);
//
//        return FcRpcMethods.createRawTx(fcClient, inputs, outputs);
//
//    }

    public static CashListReturn getCashForCd(String addrRequested, long cd, ElasticsearchClient esClient)  {
        CashListReturn cashListReturn = new CashListReturn();
        int code = 0;
        String msg = null;
        try {
            cashListReturn = getCdFromCashList(cd, addrRequested, esClient);
            if (cashListReturn.getCashList() != null) return cashListReturn;
            code = cashListReturn.getCode();
            msg = cashListReturn.getMsg();

            cashListReturn = getCdFromOneCash(addrRequested, cd, esClient);
            if (cashListReturn.getCashList() == null || cashListReturn.getCashList().isEmpty()) {
                cashListReturn.setCode(code);
                cashListReturn.setMsg(msg);
            }

            return cashListReturn;
        } catch (IOException e) {
            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
            cashListReturn.setMsg("Failed to get data from ES.");
            return cashListReturn;
        }
    }

    private static CashListReturn getCdFromOneCash(String addrRequested, long cd, ElasticsearchClient esClient) throws IOException {
        String index = IndicesNames.CASH;
        CashListReturn cashListReturn = new CashListReturn();
        SearchResponse<Cash> result = esClient.search(s -> s.index(index)
                .query(q -> q.bool(b -> b
                                .must(m -> m.term(t -> t.field(OWNER).value(addrRequested)))
                                .must(m1 -> m1.term(t1 -> t1.field(FieldNames.VALID).value(true)))
                                .must(m2 -> m2.range(r1 -> r1.field(FieldNames.CD).gte(JsonData.of(cd))))
                        )
                )
                .trackTotalHits(tr -> tr.enabled(true))
                .sort(s1 -> s1.field(f -> f.field(FieldNames.CD).order(SortOrder.Asc)))
                .size(1), Cash.class);

        List<Cash> cashList = new ArrayList<>();

        List<Hit<Cash>> hitList = result.hits().hits();

        if (hitList == null) {
            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
            cashListReturn.setMsg("Failed to get cash list from ES.");
            return cashListReturn;
        }

        if ( hitList.size() == 0) {
            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
            cashListReturn.setMsg("No cash match the condition.");
            return cashListReturn;
        }

        for (Hit<Cash> hit : hitList) {
            Cash cash = hit.source();
            cashList.add(cash);
        }

        checkUnconfirmed(addrRequested, cashList);

        if (result.hits().total() == null) return cashListReturn;
        cashListReturn.setTotal(result.hits().total().value());
        cashListReturn.setCashList(cashList);
        return cashListReturn;
    }

    private static CashListReturn getCdFromCashList(long cd, String fid, ElasticsearchClient esClient) throws IOException {
        String index = IndicesNames.CASH;
        CashListReturn cashListReturn = new CashListReturn();

        SearchResponse<Cash> result = esClient.search(s -> s.index(index)
                .query(q -> q.bool(b -> b
                                        .must(m -> m.term(t -> t.field(OWNER).value(fid)))
                                        .must(m1 -> m1.term(t1 -> t1.field(FieldNames.VALID).value(true)))
                        )
                )
                .trackTotalHits(tr -> tr.enabled(true))
                .aggregations("sum", a -> a.sum(s1 -> s1.field(FieldNames.CD)))
                .sort(s1 -> s1.field(f -> f.field(FieldNames.CD).order(SortOrder.Desc)))
                .size(100), Cash.class);

        if (result == null) {
            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
            cashListReturn.setMsg("Can't get cashes.");
            return cashListReturn;
        }

        long sum = (long) result.aggregations().get("sum").sum().value();

        if (cd !=0 && sum < cd) {
            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
            cashListReturn.setMsg("No enough cd balance: " + sum + " cd");
            return cashListReturn;
        }

        assert result.hits().total() != null;
        cashListReturn.setTotal(result.hits().total().value());

        List<Hit<Cash>> hitList = result.hits().hits();
        if (hitList.size() == 0) {
            cashListReturn.setCode(ReplyCodeMessage.Code2007CashNoFound);
            cashListReturn.setMsg(ReplyCodeMessage.Msg2007CashNoFound);
            return cashListReturn;
        }

        List<Cash> cashList = new ArrayList<>();

        for (Hit<Cash> hit : hitList) {
            Cash cash = hit.source();
            cashList.add(cash);
        }

        checkUnconfirmed(fid, cashList);

        List<Cash> meetList = new ArrayList<>();
        long adding = 0;
        for (Cash cash : cashList) {
            adding += cash.getCd();
            meetList.add(cash);
            if (adding >= cd) break;
        }

        if (adding < cd) {
            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
            cashListReturn.setMsg("Can not get enough cd from 100 cashes. Merge cashes with small cd first please.");
            return cashListReturn;
        }

        cashListReturn.setCashList(meetList);
        return cashListReturn;
    }

    public static CashListReturn getCashListForPayOld(long value, String addrRequested, ElasticsearchClient esClient) {

        CashListReturn cashListReturn = new CashListReturn();

        String index = IndicesNames.CASH;

        SearchResponse<Cash> result = null;
        try {
            result = esClient.search(s -> s.index(index)
                    .query(q -> q.bool(b -> b
                                    .must(m -> m.term(t -> t.field(OWNER).value(addrRequested)))
                                    .must(m1 -> m1.term(t1 -> t1.field(FieldNames.VALID).value(true)))
                            )
                    )
                    .trackTotalHits(tr -> tr.enabled(true))
                    .aggregations(FieldNames.SUM, a -> a.sum(s1 -> s1.field(FieldNames.VALUE)))
                    .sort(s1 -> s1.field(f -> f.field(FieldNames.CD).order(SortOrder.Asc)))
                    .size(100), Cash.class);
        } catch (IOException e) {
            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
            cashListReturn.setMsg("Can not get cashes. Check ES.");
            return cashListReturn;
        }

        if (result == null) {
            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
            cashListReturn.setMsg("Can not get cashes.Check ES.");
            return cashListReturn;
        }

        assert result.hits().total() != null;
        cashListReturn.setTotal(result.hits().total().value());

        long sum = (long) result.aggregations().get(FieldNames.SUM).sum().value();

        if (sum < value) {
            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
            cashListReturn.setMsg("No enough balance: " + sum / Constants.COIN_TO_SATOSHI + " fch");
            return cashListReturn;
        }

        List<Hit<Cash>> hitList = result.hits().hits();
        if (hitList.size() == 0) {
            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
            cashListReturn.setMsg("Get cashes failed.");
            return cashListReturn;
        }

        List<Cash> cashList = new ArrayList<>();

        for (Hit<Cash> hit : hitList) {
            Cash cash = hit.source();
            cashList.add(cash);
        }

        checkUnconfirmed(addrRequested, cashList);

        List<Cash> meetList = new ArrayList<>();
        long adding = 0;
        for (Cash cash : cashList) {
            adding += cash.getValue();
            meetList.add(cash);
            if (adding > value) break;
        }
        if (adding < value) {
            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
            cashListReturn.setMsg("Can't get enough amount from 100 cashes. Merge cashes with small cd first please. " + adding / Million + "f can be paid.");
            return cashListReturn;
        }

        cashListReturn.setCashList(meetList);
        return cashListReturn;
    }

    public static CashListReturn getCashListForPay(long value, String addrRequested, ElasticsearchClient esClient) {

        CashListReturn cashListReturn = new CashListReturn();

        String index = IndicesNames.CASH;

        SearchResponse<Cash> result;
        try {
            SearchRequest.Builder searchBuilder = new SearchRequest.Builder();
            searchBuilder.index(index);
            searchBuilder.trackTotalHits(tr -> tr.enabled(true));
            searchBuilder.aggregations(FieldNames.SUM, a -> a.sum(s1 -> s1.field(FieldNames.VALUE)));
            searchBuilder.sort(s1 -> s1.field(f -> f.field(FieldNames.CD).order(SortOrder.Asc)));
            searchBuilder.size(100);

            BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

            boolQueryBuilder.must(m -> m.term(t -> t.field(OWNER).value(addrRequested)));
            boolQueryBuilder.must(m1 -> m1.term(t1 -> t1.field(FieldNames.VALID).value(true)));

            searchBuilder.query(q -> q.bool(boolQueryBuilder.build()));

            result = esClient.search(searchBuilder.build(), Cash.class);

        } catch (IOException e) {
            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
            cashListReturn.setMsg("Can't get cashes. Check ES.");
            return cashListReturn;
        }

        if (result == null) {
            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
            cashListReturn.setMsg("Can't get cashes.Check ES.");
            return cashListReturn;
        }

        assert result.hits().total() != null;
        long total = result.hits().total().value();

        long sum = (long) result.aggregations().get(FieldNames.SUM).sum().value();

        if (sum < value) {
            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
            cashListReturn.setMsg("No enough balance: " + sum / Constants.COIN_TO_SATOSHI + " fch");
            return cashListReturn;
        }

        List<Hit<Cash>> hitList = result.hits().hits();
        if (hitList.size() == 0) {
            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
            cashListReturn.setMsg("Get cashes failed.");
            return cashListReturn;
        }

        List<Cash> cashList = new ArrayList<>();

        for (Hit<Cash> hit : hitList) {
            Cash cash = hit.source();
            cashList.add(cash);
        }

        checkUnconfirmed(addrRequested, cashList);

        List<Cash> meetList = new ArrayList<>();
        long adding = 0;
        long fee = 0;
        for (Cash cash : cashList) {
            adding += cash.getValue();
            meetList.add(cash);
            //Add tx fee
            fee = 10 + meetList.size() * 141L;
            if (adding > value + fee) break;
        }
        if (adding < value + fee) {
            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
            cashListReturn.setMsg("Can't get enough amount from 100 cashes. Merge cashes with small cd first. " + adding / Million + "f can be paid." + "Request " + value / Million + ". Fee " + fee / Million);
            return cashListReturn;
        }
        cashListReturn.setTotal(total);
        cashListReturn.setCashList(meetList);
        return cashListReturn;
    }

    private static void makeUnconfirmedFilter(List<String> spendCashList, BoolQuery.Builder boolQueryBuilder) {
        List<FieldValue> valueList = new ArrayList<>();
        for (String v : spendCashList) {
            if (v.isBlank()) continue;
            valueList.add(FieldValue.of(v));
        }

        TermsQuery tQuery = TermsQuery.of(t -> t
                .field(FieldNames.CASH_ID)
                .terms(t1 -> t1
                        .value(valueList)
                ));

        boolQueryBuilder.filter(new Query.Builder().terms(tQuery).build());
    }

    public static List<TxOutput> sendToToTxOutputList(List<SendTo> sendToList) {
        List<TxOutput> outputList = new ArrayList<>();
        for (SendTo sendTo : sendToList) {
            TxOutput txOutput = new TxOutput();
            txOutput.setAddress(sendTo.getFid());
            txOutput.setAmount((long) (sendTo.getAmount() * COIN_TO_SATOSHI));
            outputList.add(txOutput);
        }
        return outputList;
    }

    public static List<TxInput> cashToInputList(List<Cash> cashList, byte[] priKey) {
        List<TxInput> inputList = new ArrayList<>();
        JsonTools.printJson(cashList);
        for (Cash cash : cashList) {
            TxInput txInput = new TxInput();
            txInput.setIndex(cash.getBirthIndex());
            txInput.setAmount(cash.getValue());
            txInput.setTxId(cash.getBirthTxId());
            txInput.setPriKey32(priKey);
            inputList.add(txInput);
        }
        return inputList;
    }

    public static String schnorrMsgSign(String msg, byte[] priKey) {
        ECKey ecKey = ECKey.fromPrivate(priKey);
        BigInteger priKeyBigInteger = ecKey.getPrivKey();
        byte[] pubKey = ecKey.getPubKey();
        byte[] msgHash = Hash.sha256x2(msg.getBytes());
        byte[] sign = SchnorrSignature.schnorr_sign(msgHash, priKeyBigInteger);
        byte[] pkSign = BytesTools.bytesMerger(pubKey, sign);
        return Base64.getEncoder().encodeToString(pkSign);
    }

    public static boolean schnorrMsgVerify(String msg, String pubSign, String fid) throws IOException {
        byte[] msgHash = Hash.sha256x2(msg.getBytes());
        byte[] pubSignBytes = Base64.getDecoder().decode(pubSign);
        byte[] pubKey = Arrays.copyOf(pubSignBytes, 33);
        if (!fid.equals(KeyTools.pubKeyToFchAddr(HexFormat.of().formatHex(pubKey)))) return false;
        byte[] sign = Arrays.copyOfRange(pubSignBytes, 33, pubSignBytes.length);
        return SchnorrSignature.schnorr_verify(msgHash, pubKey, sign);
    }

    @Test
    public void sendDoge() {
        String url = "http://127.0.0.1:22555";
        String username = "username";
        String password = "password";
        String fromAddr = "DS8M937nHLtmeNef6hnu17ZXAwmVpM6TXY";
        String toAddr = "DK22fsq2qaH6aFDZqMUcEyC1JbULjHZqVo";
        String minConf = "1";
        tx.Utxo[] utxos = (new tx.ListUnspent()).listUnspent(fromAddr, minConf, url, username, password);
        if (utxos != null && utxos.length != 0) {
            List<TxInputDoge> txInputList = new ArrayList();
            tx.Utxo[] var8 = utxos;
            int var9 = utxos.length;

            String priKey = null;
            for (int var10 = 0; var10 < var9; ++var10) {
                tx.Utxo utxo1 = var8[var10];
                TxInputDoge txInput = new TxInputDoge();
                txInput.setTxId(utxo1.getTxid());
                txInput.setAmount((long) (utxo1.getAmount() * 1.0E8));
                txInput.setIndex(utxo1.getVout());
                priKey = "L2w6HHF352YhuLsX33YgGDL9r9Uv3auyHz5StzarvGasZWwsP83E";
                byte[] priKeyBytes = getPriKey32(priKey);
                shade.bitcoinj159.core.ECKey ecKey = shade.bitcoinj159.core.ECKey.fromPrivate(priKeyBytes);
                txInput.setPriKey32(ecKey.getPrivKeyBytes());
                txInputList.add(txInput);
            }

            TxOutputDoge txOutput = new TxOutputDoge();
            txOutput.setAddress(toAddr);
            txOutput.setAmount(63 * COIN_TO_SATOSHI);
            List<TxOutputDoge> txOutputs = new ArrayList();
            txOutputs.add(txOutput);
            tx.EstimateFee.ResultSmart fee = (new tx.EstimateFee()).estimatesmartfee(3, url, username, password);
            String opReturn = "a";
            String signedTx = DogeTxMaker.createTransactionSignDoge(txInputList, txOutputs, opReturn, fromAddr, fee.getFeerate());
            System.out.println(signedTx);
            String txId = new NaSaRpcClient(url, username, password).sendRawTransaction(signedTx);
            if (txId == null) {
                throw new RuntimeException("Failed to send tx.");
            }
            System.out.println(txId);
        } else {
            System.out.println("No UTXOs");
        }
    }

    public ApipClient getApipClient() {
        return apipClient;
    }

    public void setApipClient(ApipClient apipClient) {
        this.apipClient = apipClient;
    }

    public ElasticsearchClient getEsClient() {
        return esClient;
    }

    public void setEsClient(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    public NaSaRpcClient getNasaClient() {
        return nasaClient;
    }

    public void setNasaClient(NaSaRpcClient nasaClient) {
        this.nasaClient = nasaClient;
    }
}
