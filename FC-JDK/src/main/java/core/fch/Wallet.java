package core.fch;

import data.apipData.Sort;
import clients.ApipClient;
import data.fchData.Block;
import data.fchData.Cash;
import data.fchData.RawTxForCsV1;
import data.fchData.SendTo;
import handlers.CashHandler;
import data.fcData.ReplyBody;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.fch.FchMainNetwork;
import utils.*;
import utils.FchUtils;
import utils.http.AuthType;
import utils.http.RequestMethod;
import clients.NaSaClient.NaSaRpcClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.google.gson.Gson;

import constants.*;
import core.crypto.KeyTools;
import data.nasa.UTXO;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tx.DogeTxMaker;
import tx.TxInputDoge;
import tx.TxOutputDoge;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

import static handlers.CashHandler.*;
import static server.ApipApiNames.VERSION_1;
import static constants.Constants.*;
import static constants.FieldNames.*;
import static core.fch.TxCreator.*;
import static data.fchData.Cash.sumCashValue;
import static tx.DogeTxMaker.getPriKey32;

public class Wallet {
    private static final Logger log = LoggerFactory.getLogger(Wallet.class);
    private ApipClient apipClient;
    private ElasticsearchClient esClient;
    private NaSaRpcClient nasaClient;
    public static final int MAX_CASHE_SIZE = 100;

    public Wallet(ApipClient apipClient, ElasticsearchClient esClient, NaSaRpcClient nasaClient) {
        this.apipClient = apipClient;
        this.esClient = esClient;
        this.nasaClient = nasaClient;
    }

    public static void main(String[] args) {
        long num = 899996620;
        byte[] numBytes = BytesUtils.longToBytes(num);
        System.out.println(numBytes);

        long number = BytesUtils.bytes8ToLong(numBytes,false);
        System.out.println(number);


        String str = "/wABAAAAAAX14QACAAAAATBmNWOPwTCoczuJI1aWkKK8rP+qzzpnNd7b53YxHPgNAAAAAAD/////A4CWmAAAAAAAGXapFGHEKrtuNDXmO9iIYvN0aj+LhjVCiKwAAAAAAAAAAFZqTFN7InR5cGUiOiJGRUlQIiwic24iOiIzIiwidmVyIjoiNCIsIm5hbWUiOiJDSUQiLCJkYXRhIjp7Im9wIjoicmVnaXN0ZXIiLCJuYW1lIjoiYyJ9fUZJXQUAAAAAGXapFGHEKrtuNDXmO9iIYvN0aj+LhjVCiKwAAAAA";
        System.out.println(str);
        System.out.println("Base64 size:"+str.length());
        String hex = Hex.toHex(Base64.getDecoder().decode(str));
        System.out.println(hex);
        System.out.println("Hex size:"+hex.length());

        String strHex = "02000000010f7d38cd088bfd1432e2974668462ba6b042682c223c67e45529b17df97688e40200000000ffffffff0300e1f505000000001976a91461c42abb6e3435e63bd88862f3746a3f8b86354288ac0000000000000000566a4c537b2274797065223a2246454950222c22736e223a2233222c22766572223a2234222c226e616d65223a22434944222c2264617461223a7b226f70223a227265676973746572222c226e616d65223a2263227d7d92f9ae2f000000001976a91461c42abb6e3435e63bd88862f3746a3f8b86354288ac00000000";
        System.out.println("hex:"+strHex);
        System.out.println("Unsigned Tx:");
        System.out.println(TxCreator.decodeTxFch(strHex, FchMainNetwork.MAINNETWORK));

        System.out.println("Unsigned Tx from Base64:");
        System.out.println(TxCreator.decodeTxFch(str, FchMainNetwork.MAINNETWORK));
        byte[] priKey = Hex.fromHex("a048f6c843f92bfe036057f7fc2bf2c27353c624cf7ad97e98ed41432f700575");


        String signedTx = TxCreator.signRawTx(str, priKey, FchMainNetwork.MAINNETWORK);
        System.out.println(signedTx);
        System.out.println(StringUtils.base64ToHex(signedTx));

        System.out.println("Signed Tx:");

        System.out.println(TxCreator.decodeTxFch(signedTx, FchMainNetwork.MAINNETWORK));
    }

    public static String carve(String myFid, byte[] priKey, String opReturnStr, long cd, ApipClient apipClient, CashHandler cashHandler, BufferedReader br) {
        String txSigned;
        String sendResult;
        if(cashHandler !=null) sendResult = cashHandler.send(null,null, CD_REQUIRED,null, opReturnStr, null, null, null, core.fch.FchMainNetwork.MAINNETWORK, br);
        else {
            txSigned = makeTx(br, priKey, myFid, null, opReturnStr, cd, MAX_CASH_SIZE, apipClient, null);
            if (txSigned == null) {
                System.out.println("Failed to make tx.");
                return null;
            }
            sendResult = apipClient.broadcastTx(txSigned, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        }
        return sendResult;
    }

    public String send(List<Cash> cashList, byte[] priKey, String toAddr, double toAmount, String msg) {
        SendTo sendTo = new SendTo();
        sendTo.setFid(toAddr);
        sendTo.setAmount(toAmount);
        List<SendTo> sendToList = new ArrayList<>();
        sendToList.add(sendTo);
        String txSigned = createTxFch(cashList, priKey, sendToList, msg, FchMainNetwork.MAINNETWORK);
        return apipClient.broadcastTx(txSigned, RequestMethod.POST, AuthType.FC_SIGN_BODY);
    }

    public static String signRawTx(String rawTxHex, byte[] priKey) {
        return TxCreator.signRawTx(rawTxHex, priKey, FchMainNetwork.MAINNETWORK);
    }

    public static String sendTxByApip(BufferedReader br, byte[] priKey, List<SendTo> sendToList, String opReturnStr, long cd, int maxCashes, ApipClient apipClient) {
        String txSigned = makeTx(br, priKey, null, sendToList, opReturnStr, cd, maxCashes, apipClient, null);
        apipClient.broadcastTx(txSigned, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        Object data = apipClient.checkResult();
        return (String) data;
    }

    public static String decodeTxFch(String rawTxHex) {
        return TxCreator.decodeTxFch(rawTxHex, FchMainNetwork.MAINNETWORK);
    }

    public static String decodeTxFch(byte[] rawTxBytes) {
        return TxCreator.decodeTxFch(rawTxBytes, FchMainNetwork.MAINNETWORK);
    }

    public static String makeTxByEs(BufferedReader br,byte[] priKey, List<SendTo> sendToList, String opReturnStr, long cd, int maxCashes, ElasticsearchClient esClient) {
        return makeTx(br, priKey, null, sendToList, opReturnStr, cd, maxCashes, null, esClient);
    }

    public static String makeTxForCs(BufferedReader br, String fid, List<SendTo> sendToList, String opReturnStr, long cd, int maxCashes, ApipClient apipClient) {
        return makeTx(br, null, fid, sendToList, opReturnStr, cd, maxCashes, apipClient, null);
    }

    public static String makeTx(BufferedReader br,byte[] priKey, String fidForOfflineSign, List<SendTo> sendToList, String opReturnStr, long cd, int maxCashes, ApipClient apipClient, ElasticsearchClient esClient){
        if(sendToList!=null && !sendToList.isEmpty())if (CashHandler.checkNobodys(br, sendToList, apipClient, esClient)) return null;
        return makeTx(priKey,fidForOfflineSign,sendToList,opReturnStr,cd,maxCashes,apipClient,esClient, null);
    }

    public static String makeTx(byte[] priKey, String fidForOfflineSign, List<SendTo> sendToList, String opReturnStr, long cd, int maxCashes, ApipClient apipClient, ElasticsearchClient esClient, NaSaRpcClient nasaClient) {
        String fid;
        if (priKey != null) fid = KeyTools.prikeyToFid(priKey);
        else fid = fidForOfflineSign;
        long amount = 0;
        double sum = 0;
        if (sendToList == null) sendToList = new ArrayList<>();
        else {
            for(SendTo sendTo :sendToList)sum += sendTo.getAmount();
        }

        List<Cash> cashList;
        long bestHeight;
        int opReturnSize = 0;
        byte[] opReturnBytes = null;
        if(opReturnStr!=null) {
            opReturnBytes= opReturnStr.getBytes();
            opReturnSize = opReturnBytes.length;
        }

        if (apipClient != null) {
            cashList = apipClient.cashValid(fid,sum,cd,sendToList.size(),opReturnSize,RequestMethod.POST,AuthType.FC_SIGN_BODY);
            bestHeight = apipClient.getFcClientEvent().getResponseBody().getBestHeight();
        } else if (esClient != null) {
            ReplyBody replier = getCashListFromEs(new ArrayList<>(Arrays.asList(fid)), true, null, maxCashes, null, null, esClient);
            if (replier.getCode() != 0) return replier.getMessage();
            cashList = ObjectUtils.objectToList(replier.getData(), Cash.class);//DataGetter.getCashList(replier.getData());
            bestHeight = getBestHeight(esClient);
        } else if(nasaClient !=null){
            UTXO[] utxos = nasaClient.listUnspent(fid, "0", true);
            cashList = Cash.fromUtxoList(Arrays.asList(utxos));
            bestHeight = nasaClient.getBestHeight();
        }else
            return "No APIP client or ES client to make tx.";

        if (cashList == null) return "No qualified cash found.";
        Iterator<Cash> iter = cashList.iterator();
        List<Cash> spendCashList = new ArrayList<>();
        long valueSum = 0;
        long cdSum = 0;
        long fee =0;
        while (iter.hasNext()) {
            Cash cash = iter.next();

            if(!cash.isValid()){
                iter.remove();
                continue;
            }
            if (cash.getIssuer() != null && cash.getIssuer().equals(COINBASE) && (bestHeight - cash.getBirthHeight()) < Constants.TenDayBlocks) {
                iter.remove();
                continue;
            }
            spendCashList.add(cash);
            fee = calcTxSize(spendCashList.size(), sendToList.size(), opReturnSize);
            valueSum += cash.getValue();
            Long cd1 = utils.FchUtils.cdd(cash.getValue(), cash.getBirthTime(), System.currentTimeMillis()/1000);
            cdSum += cd1;
            if (valueSum >= (amount + fee) && cdSum >= cd) break;
        }

        if (!(valueSum >= (amount + fee) && cdSum >= cd)) return null;

        if (priKey != null) {
                return createTxFch(spendCashList, priKey, sendToList, opReturnStr, FchMainNetwork.MAINNETWORK);
            }
        else {
//            return makeTxForOfflineSign(sendToList, opReturnStr, cashList);
            Transaction transaction = TxCreator.createUnsignedTx(spendCashList,sendToList,opReturnStr,null,Constants.MIN_FEE_RATE, null, FchMainNetwork.MAINNETWORK);
            if(transaction==null)return null;
            return Hex.toHex(transaction.bitcoinSerialize());
        }
    }

    public static String makeTxForOfflineSign(List<SendTo> sendToList, String opReturn, List<Cash> meetList) {

        Gson gson = new Gson();
        StringBuilder RawTx = new StringBuilder("[");
        int i = 0;
        for (Cash cash : meetList) {
            if (i > 0) RawTx.append(",");
            RawTxForCsV1 rawTxForCsV1 = new RawTxForCsV1();
            rawTxForCsV1.setAddress(cash.getOwner());
            rawTxForCsV1.setAmount(utils.FchUtils.satoshiToCoin(cash.getValue()));
            rawTxForCsV1.setTxid(cash.getBirthTxId());
            rawTxForCsV1.setIndex(cash.getBirthIndex());
            rawTxForCsV1.setSeq(i);
            rawTxForCsV1.setDealType(RawTxForCsV1.DealType.INPUT);
            RawTx.append(gson.toJson(rawTxForCsV1));
            i++;
        }
        int j = 0;
        if (sendToList != null) {
            for (SendTo sendTo : sendToList) {
                RawTxForCsV1 rawTxForCsV1 = new RawTxForCsV1();
                rawTxForCsV1.setAddress(sendTo.getFid());
                rawTxForCsV1.setAmount(sendTo.getAmount());
                rawTxForCsV1.setSeq(j);
                rawTxForCsV1.setDealType(RawTxForCsV1.DealType.OUTPUT);
                RawTx.append(",");
                RawTx.append(gson.toJson(rawTxForCsV1));
                j++;
            }
        }

        if (opReturn != null) {
            RawTxForCsV1 rawOpReturnForCs = new RawTxForCsV1();
            rawOpReturnForCs.setMsg(opReturn);
            rawOpReturnForCs.setSeq(j);
            rawOpReturnForCs.setDealType(RawTxForCsV1.DealType.OP_RETURN);
            RawTx.append(",");
            RawTx.append(gson.toJson(rawOpReturnForCs));
        }
        RawTx.append("]");
        return RawTx.toString();
    }

    public static ReplyBody sendTx(String txSigned, @Nullable ApipClient apipClient, @Nullable NaSaRpcClient naSaRpcClient) {
        ReplyBody replyBody = new ReplyBody();
        if(txSigned==null)return null;
        if (naSaRpcClient != null) {
            String txid = naSaRpcClient.sendRawTransaction(txSigned);
            long bestHeight = naSaRpcClient.getBestHeight();
            if (Hex.isHexString(txid)) {
                replyBody.Set0Success();
                replyBody.setData(txid);
                replyBody.setBestHeight(bestHeight);
            }
        } else if (apipClient != null) {
            apipClient.broadcastTx(txSigned, RequestMethod.POST, AuthType.FC_SIGN_BODY);
            return apipClient.getFcClientEvent().getResponseBody();
        } else replyBody.setOtherError("No client to send tx.");
        return replyBody;
    }

    public Double getFeeRate() {
        if (apipClient != null) return apipClient.feeRate(RequestMethod.GET, AuthType.FREE);
        if (esClient != null) return calcFeeRate(esClient);
        if (nasaClient != null) return nasaClient.estimateFee(3);
        return 0.001D;
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

    public static void mergeCashList(List<Cash> cashList, byte[] priKey, ApipClient apipClient, NaSaRpcClient nasaClient) {
        Iterator<Cash> iter = cashList.iterator();
        for (int i = 0; i <= cashList.size() % 100; i++) {
            List<Cash> subCashList = new ArrayList<>();
            int j = 0;
            while (iter.hasNext()) {
                Cash cash = iter.next();
                subCashList.add(cash);
                iter.remove();
                j++;
                if (j == 100) break;
            }
            mergeCashList(subCashList, 0, priKey, apipClient, nasaClient);
        }
    }

    public static String mergeCashList(List<Cash> cashList, int issueNum, byte[] priKey, ApipClient apipClient, NaSaRpcClient nasaClient) {
        if (cashList == null || cashList.isEmpty()) return null;
        String fid = KeyTools.prikeyToFid(priKey);

        Object data;
        long fee = calcTxSize(cashList.size(), issueNum, 0);

        long sumValue = sumCashValue(cashList) - fee;
        if (sumValue < 0) {
            System.out.println("Cash value is too small:" + sumValue + fee + ". Try again.");
            return null;
        }
        long valueForOne = sumValue / issueNum;
        if (valueForOne < Constants.SatoshiDust) {
            System.out.println("The sum of all cash values is too small to split. Try again.");
            return null;
        }
        List<SendTo> sendToList = new ArrayList<>();
        SendTo sendTo = new SendTo();
        sendTo.setFid(fid);
        sendTo.setAmount(utils.FchUtils.satoshiToCoin(valueForOne));
        for (int i = 0; i < issueNum - 1; i++) sendToList.add(sendTo);
        SendTo sendTo1 = new SendTo();
        sendTo1.setFid(fid);
        long lastCashValue = sumValue - (valueForOne * (issueNum - 1));
        sendTo1.setAmount(FchUtils.satoshiToCoin(lastCashValue));
        sendToList.add(sendTo1);

        String txSigned = createTxFch(cashList, priKey, sendToList, null, FchMainNetwork.MAINNETWORK);

        if (apipClient != null) {
            apipClient.broadcastTx(txSigned, RequestMethod.POST, AuthType.FC_SIGN_BODY);
            data = apipClient.checkResult();
        } else if (nasaClient != null) {
            data = nasaClient.sendRawTransaction(txSigned);
        } else return null;
        return (String) data;
    }

    public List<Cash> getAllCashList(String fid, boolean onlyValid, int size, ArrayList<Sort> sortList, List<String> last) {
        List<Cash> cashList = new ArrayList<>();

        ReplyBody replyBody;
        if (this.apipClient != null) {
            do {
                List<Cash> newCashList = getCashListFromApip(fid, onlyValid, size, sortList, last, null, apipClient);
                if (newCashList == null) {
                    log.debug(this.apipClient.getFcClientEvent().getMessage());
                    return cashList;
                }
                if (newCashList.isEmpty()) return cashList;
                cashList.addAll(newCashList);

                replyBody = this.apipClient.getFcClientEvent().getResponseBody();
                last = replyBody.getLast();
            } while (cashList.size() < replyBody.getTotal());
        } else if (this.esClient != null) {
            do {
                replyBody = getCashListFromEs(new ArrayList<>(Arrays.asList(fid)), onlyValid, null, size, sortList, last, esClient);
                if (replyBody.getCode() != 0) {
                    log.debug(replyBody.getMessage());
                    break;
                }
                if (replyBody.getData() != null) {
                    cashList.addAll(ObjectUtils.objectToList(replyBody.getData(), Cash.class));//DataGetter.getCashList(fcReplier.getData()));
                } else return cashList;
                last = ObjectUtils.objectToList(replyBody.getData(), String.class);//DataGetter.getStringList(fcReplier.getLast());
            } while (cashList.size() < replyBody.getTotal());
        } else if (this.nasaClient != null) {
            replyBody = getCashListFromNasaNode(fid, null, true, nasaClient);
            if (replyBody.getCode() != 0) {
                log.debug(replyBody.getMessage());
                return cashList;
            }
            if (replyBody.getData() != null)
                cashList.addAll(ObjectUtils.objectToList(replyBody.getData(), Cash.class));//DataGetter.getCashList(fcReplier.getData()));
            else return cashList;
        }
        return cashList;
    }

//
//    public CashListReturn getAllCashList(long value, long cd, int outputNum, int opReturnLength, String addrRequested, ElasticsearchClient esClient) {
//
//        CashListReturn cashListReturn = new CashListReturn();
//
//        String index = IndicesNames.CASH;
//
//        SearchResponse<Cash> result;
//        try {
//            SearchRequest.Builder searchBuilder = new SearchRequest.Builder();
//            searchBuilder.index(index);
//            searchBuilder.trackTotalHits(tr -> tr.enabled(true));
//            searchBuilder.sort(s1 -> s1.field(f -> f.field(FieldNames.CD).order(SortOrder.Asc)));
//            searchBuilder.size(200);
//
//            BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
//
//            boolQueryBuilder.must(m -> m.term(t -> t.field(FieldNames.OWNER).value(addrRequested)));
//            boolQueryBuilder.must(m1 -> m1.term(t1 -> t1.field(FieldNames.VALID).value(true)));
//
//            searchBuilder.query(q -> q.bool(boolQueryBuilder.build()));
//
//            result = esClient.search(searchBuilder.build(), Cash.class);
//
//        } catch (IOException e) {
//            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
//            cashListReturn.setMsg("Can't get cashes. Check ES.");
//            return cashListReturn;
//        }
//
//        if (result == null) {
//            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
//            cashListReturn.setMsg("Can't get cashes.Check ES.");
//            return cashListReturn;
//        }
//
//        long total = result.hits().hits().size();
//
//        long valueSum = 0;//(long)result.aggregations().get(FieldNames.SUM).valueSum().value();
//        long cdSum = 0;
//        long fee = 0;
//
//
//        List<Cash> cashList = new ArrayList<>();
//        List<Hit<Cash>> hitList = result.hits().hits();
//
//        long bestHeight = getBestHeight(esClient);
//
//        for (Hit<Cash> hit : hitList) {
//            Cash cash = hit.source();
//            if (cash == null) continue;
//            if (cash.getIssuer() != null && cash.getIssuer().equals(COINBASE) && cash.getBirthHeight() > (bestHeight - Constants.OneDayInterval * 10))
//                continue;
//            cashList.add(cash);
//        }
//        List<Cash> issuingCashList = cashClient.getIssuingCashListFromJedis(addrRequested);
//        if (issuingCashList != null && issuingCashList.size() > 0) {
//            for (Cash cash : issuingCashList) {
//                cashList.add(cash);
//            }
//        }
//
//        String[] spendingCashIds = cashClient.getSpendingCashIdFromJedis(addrRequested);
//        if (spendingCashIds != null) {
//            for (String id : spendingCashIds) {
//                Iterator<Cash> iter = cashList.iterator();
//                while (iter.hasNext()) {
//                    Cash cash = iter.next();
//                    if (id.equals(cash.getCashId())) {
//                        iter.remove();
//                        break;
//                    }
//                }
//            }
//        }
//
//        List<Cash> meetList = new ArrayList<>();
//        boolean done = false;
//        for (Cash cash : cashList) {
//            meetList.add(cash);
//            valueSum += cash.getValue();
//            cdSum += cash.getCd();
//            fee = calcTxSize(cashList.size(), outputNum, opReturnLength);
//
//            if (valueSum > (value + fee) && cdSum > cd) {
//                done = true;
//                break;
//            }
//        }
//
//        if (!done) {
//            cashListReturn.setCode(ReplyCodeMessage.Code1020OtherError);
//            cashListReturn.setMsg("Can't meet the conditions.");
//            return cashListReturn;
//        }
//        cashListReturn.setTotal(total);
//        cashListReturn.setCashList(meetList);
//
//        return cashListReturn;
//    }

    public Long getBestHeight() {
        try {
            if (nasaClient != null) return nasaClient.getBestHeight();
            if (esClient != null) return getBestHeight(esClient);
            if (apipClient != null) {
                apipClient.ping(VERSION_1, RequestMethod.POST, AuthType.FC_SIGN_BODY, null);
                return apipClient.getFcClientEvent().getResponseBody().getBestHeight();
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    public static long getBestHeight(ElasticsearchClient esClient) {
        long bestHeight = 0;
        try {
            Block bestBlock = EsUtils.getBestBlock(esClient);
            if (bestBlock != null)
                bestHeight = bestBlock.getHeight();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return bestHeight;
    }

    @SuppressWarnings("unused")
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
//
//    public static List<TxOutput> sendToToTxOutputList(List<SendTo> sendToList) {
//        List<TxOutput> outputList = new ArrayList<>();
//        for (SendTo sendTo : sendToList) {
//            TxOutput txOutput = new TxOutput();
//            txOutput.setAddress(sendTo.getFid());
//            txOutput.setAmount((long) (sendTo.getAmount() * COIN_TO_SATOSHI));
//            outputList.add(txOutput);
//        }
//        return outputList;
//    }
//
//    public static List<TxInput> cashToInputList(List<Cash> cashList, byte[] priKey) {
//        List<TxInput> inputList = new ArrayList<>();
//        JsonTools.printJson(cashList);
//        for (Cash cash : cashList) {
//            TxInput txInput = new TxInput();
//            txInput.setIndex(cash.getBirthIndex());
//            txInput.setAmount(cash.getValue());
//            txInput.setTxId(cash.getBirthTxId());
//            txInput.setPriKey32(priKey);
//            inputList.add(txInput);
//        }
//        return inputList;
//    }

    public void sendDogeTest() {
        String url = "http://127.0.0.1:22555";
        String username = "username";
        String password = "password";
        String fromAddr = "DS8M937nHLtmeNef6hnu17ZXAwmVpM6TXY";
        String toAddr = "DK22fsq2qaH6aFDZqMUcEyC1JbULjHZqVo";
        String minConf = "1";
        tx.Utxo[] utxos = (new tx.ListUnspent()).listUnspent(fromAddr, minConf, url, username, password);
        if (utxos != null && utxos.length != 0) {
            List<TxInputDoge> txInputList = new ArrayList<>();
            int var9 = utxos.length;

            String priKey = null;
            for (tx.Utxo utxo1 : utxos) {
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
            List<TxOutputDoge> txOutputs = new ArrayList<>();
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
}
