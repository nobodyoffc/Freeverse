package mempool;

import fch.fchData.Cash;
import fch.fchData.Tx;
import fch.fchData.TxHasInfo;
import nasa.NaSaRpcClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javaTools.JsonTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static fch.RawTxParser.parseMempoolTx;


public class MempoolScanner implements Runnable {
    private volatile AtomicBoolean running = new AtomicBoolean(true);
    private static final Logger log = LoggerFactory.getLogger(MempoolScanner.class);

    private final long IntervalSeconds = 5;
    private final ElasticsearchClient esClient;
    private JedisPool jedisPool;
    private NaSaRpcClient naSaRpcClient;

    private final Gson gson = new Gson();

//    public static JsonRpcHttpClient fcClient;

    public MempoolScanner(NaSaRpcClient naSaRpcClient,ElasticsearchClient esClient, JedisPool jedisPool) {
        this.esClient = esClient;
        this.naSaRpcClient = naSaRpcClient;
        this.jedisPool = jedisPool;
    }

    public void run() {
        try(Jedis jedis = jedisPool.getResource()) {
            jedis.select(3);
            log.debug("Scanning mempool...");
            while (running.get()) {
                String[] txIds;

                txIds = naSaRpcClient.getRawMempoolIds();
                if(txIds==null||txIds.length==0){
                    try {
                        TimeUnit.SECONDS.sleep(IntervalSeconds);
                    } catch (InterruptedException e) {
                        continue;
                    }
                } else {
                    for (String txid : txIds) {
                        if (jedis.hget("tx", txid) == null) {
                            log.debug("Got unconfirmed TX : " + txid);
                            String rawTxHex = null;
                            try {
                                rawTxHex = naSaRpcClient.getRawTx(txid);
                            } catch (Throwable e) {
                                e.printStackTrace();
                                log.error("Get raw tx of " + txid + " wrong.");
                            }
                            TxHasInfo txInMempoolMap = null;
                            try {
                                txInMempoolMap = parseMempoolTx(esClient, rawTxHex, txid);
                            } catch (Exception e) {
                                e.printStackTrace();
                                log.error("Parse tx of " + txid + " wrong.");
                            }
                            if (txInMempoolMap != null)
                                addMempoolTxToRedis(txInMempoolMap,jedis);
                        }
                    }
                }
                try {
                    TimeUnit.SECONDS.sleep(IntervalSeconds);
                } catch (InterruptedException ignored) {}
            }
        }
    }

    private void addMempoolTxToRedis(TxHasInfo txInMempoolMap, Jedis jedis) {
        Tx tx = txInMempoolMap.getTx();
        List<Cash> inList = txInMempoolMap.getInCashList();
        List<Cash> outList = txInMempoolMap.getOutCashList();

        addTxToRedis(tx,jedis);
        addSpendCashesToRedis(inList, jedis);
        addNewCashesToRedis(outList,jedis);

        //收入，支出，数量，笔数，净值
        addAddressInfoToRedis(inList,outList,jedis);
    }

    private void addTxToRedis(Tx tx,Jedis jedis) {
        jedis.hset("tx",tx.getTxId(), JsonTools.toNiceJson(tx));
    }

    private void addSpendCashesToRedis(List<Cash> inList, Jedis jedis) {
        for(Cash cash:inList){
            if(jedis.hget("spendCashes",cash.getCashId())==null) {
                jedis.hset("spendCashes", cash.getCashId(), JsonTools.toNiceJson(cash));
            }
            else{
                log.debug("Double spend : "+ JsonTools.toNiceJson(cash));
            }
        }
    }

    private void addNewCashesToRedis(List<Cash> outList, Jedis jedis) {
        for(Cash cash:outList){
            jedis.hset("newCashes",cash.getCashId(), JsonTools.toNiceJson(cash));
        }
    }

    private void addAddressInfoToRedis(List<Cash> inList, List<Cash> outList, Jedis jedis) {
        String txValueMapKey = "txValueMap";
        String netKey = "net";
        String spendCountKey = "spendCount";
        String spendValueKey = "spendValue";
        String spendCashesKey = "spendCashes";
        String incomeCountKey = "incomeCount";
        String incomeValueKey = "incomeValue";
        String newCashesKey = "newCashes";

        Map<String, Long> fidNetMap = new HashMap<>();

        for (Cash cash : inList) {
            String fid = cash.getOwner();
            String txId = cash.getSpendTxId();
            //income数量，income金额，spend数量，spend金额，fid的net净变化, 交易中净变化
            int spendCount = 0;
            long spendValue = 0;
            String[] spendCashes = new String[0];

            Map<String,Long> txValueMap;

            String netStr = jedis.hget(fid, netKey);
            long net;
            if(netStr==null) net = 0;
            else net = Long.parseLong(netStr);
            net = net-cash.getValue();

            String spendCountStr = jedis.hget(fid, spendCountKey);
            String spendValueStr = jedis.hget(fid, spendValueKey);
            String txValueMapStr = jedis.hget(fid, txValueMapKey);

            //Load existed values from redis
            if ( spendCountStr!= null) {
                spendCount = Integer.parseInt(spendCountStr);
                spendValue = Long.parseLong(spendValueStr);
                spendCashes = gson.fromJson(jedis.hget(fid,spendCashesKey),String[].class);
                if(spendCashes==null)spendCashes = new String[0];
            }
            spendValue += cash.getValue();

            spendCount++;
            String[] newSpendCashes = new String[spendCashes.length+1];
            System.arraycopy(spendCashes,0,newSpendCashes,0,spendCashes.length);
            newSpendCashes[newSpendCashes.length-1]=cash.getCashId();

            if(txValueMapStr!=null){
                Type mapType = new TypeToken<Map<String, Long>>(){}.getType();

                txValueMap = gson.fromJson(txValueMapStr,mapType);
            }else {
                txValueMap = new HashMap<>();
            }
            if(txValueMap.get(txId)!=null){
                txValueMap.put(txId,txValueMap.get(txId)-cash.getValue());
            }else{
                txValueMap.put(txId, -cash.getValue());
            }

            jedis.hset(fid, spendValueKey, String.valueOf(spendValue));
            jedis.hset(fid, spendCountKey, String.valueOf(spendCount));
            jedis.hset(fid,spendCashesKey, JsonTools.toNiceJson(newSpendCashes));
            jedis.hset(fid, netKey, String.valueOf(net));
            jedis.hset(fid, txValueMapKey, JsonTools.toNiceJson(txValueMap));
        }

        for (Cash cash : outList) {
            String fid = cash.getOwner();
            String txId = cash.getBirthTxId();
            //income数量，income金额，income数量，income金额，net净变化
            long net;
            String netStr = jedis.hget(fid, netKey);
            if(netStr==null) net = 0;
            else net = Long.parseLong(netStr);
            net = net + cash.getValue();

            int incomeCount = 0;
            long incomeValue = 0;
            String[] newCashes = new String[0];
            Map<String,Long> txValueMap;

            if (jedis.hget(fid, incomeCountKey) != null) {
                incomeCount = Integer.parseInt(jedis.hget(fid, incomeCountKey));
                incomeValue = Long.parseLong(jedis.hget(fid, incomeValueKey));
                newCashes = gson.fromJson(jedis.hget(fid,newCashesKey),String[].class);
                if(newCashes==null)newCashes = new String[0];
            }

            incomeValue += cash.getValue();

            incomeCount++;

            String[] newIncomeCashes = new String[newCashes.length+1];
            System.arraycopy(newCashes,0,newIncomeCashes,0,newCashes.length);
            newIncomeCashes[newIncomeCashes.length-1]=cash.getCashId();

            String txValueMapStr = jedis.hget(fid, txValueMapKey);
            if(txValueMapStr!=null){
                Type mapType = new TypeToken<Map<String, Long>>(){}.getType();
                txValueMap = gson.fromJson(txValueMapStr,mapType);
            }else {
                txValueMap = new HashMap<>();
            }

            txValueMap.merge(txId, cash.getValue(), Long::sum);

            jedis.hset(fid, incomeValueKey, String.valueOf(incomeValue));
            jedis.hset(fid, incomeCountKey, String.valueOf(incomeCount));
            jedis.hset(fid,newCashesKey, JsonTools.toNiceJson(newIncomeCashes));
            jedis.hset(fid, netKey, String.valueOf(net));
            jedis.hset(fid, txValueMapKey, JsonTools.toNiceJson(txValueMap));
        }

    }
    public void shutdown() {
        running.set(false);
    }
    public void restart(){
        running.set(true);
    }

    public AtomicBoolean getRunning() {
        return running;
    }
}
