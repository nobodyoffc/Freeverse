package mempool;

import fch.fchData.Cash;
import fch.fchData.Tx;
import fch.fchData.TxHasInfo;
import nasa.NaSaRpcClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import utils.JsonUtils;
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

import static constants.FieldNames.*;
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
                                txInMempoolMap = parseMempoolTx(rawTxHex, txid, null, esClient);
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
        jedis.hset("tx",tx.getId(), JsonUtils.toNiceJson(tx));
    }

    private void addSpendCashesToRedis(List<Cash> inList, Jedis jedis) {
        for(Cash cash:inList){
            if(jedis.hget(SPEND_CASHES,cash.getId())==null) {
                jedis.hset(SPEND_CASHES, cash.getId(), JsonUtils.toNiceJson(cash));
            }
            else{
                log.debug("Double spend : "+ JsonUtils.toNiceJson(cash));
            }
        }
    }

    private void addNewCashesToRedis(List<Cash> outList, Jedis jedis) {
        for(Cash cash:outList){
            jedis.hset(NEW_CASHES,cash.getId(), JsonUtils.toNiceJson(cash));
        }
    }

    private void addAddressInfoToRedis(List<Cash> inList, List<Cash> outList, Jedis jedis) {

        for (Cash cash : inList) {
            String fid = cash.getOwner();
            String txId = cash.getSpendTxId();
            int spendCount = 0;
            long spendValue = 0;
            String[] spendCashes = new String[0];

            Map<String,Long> txValueMap;

            String netStr = jedis.hget(fid, NET);
            long net;
            if(netStr==null) net = 0;
            else net = Long.parseLong(netStr);
            net = net-cash.getValue();

            String spendCountStr = jedis.hget(fid, SPEND_COUNT);
            String spendValueStr = jedis.hget(fid, SPEND_VALUE);
            String txValueMapStr = jedis.hget(fid, TX_VALUE_MAP);

            //Load existed values from redis
            if ( spendCountStr!= null) {
                spendCount = Integer.parseInt(spendCountStr);
                spendValue = Long.parseLong(spendValueStr);
                spendCashes = gson.fromJson(jedis.hget(fid,SPEND_CASHES),String[].class);
                if(spendCashes==null)spendCashes = new String[0];
            }
            spendValue += cash.getValue();

            spendCount++;
            String[] newSpendCashes = new String[spendCashes.length+1];
            System.arraycopy(spendCashes,0,newSpendCashes,0,spendCashes.length);
            newSpendCashes[newSpendCashes.length-1]=cash.getId();

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

            jedis.hset(fid, SPEND_VALUE, String.valueOf(spendValue));
            jedis.hset(fid, SPEND_COUNT, String.valueOf(spendCount));
            jedis.hset(fid,SPEND_CASHES, JsonUtils.toNiceJson(newSpendCashes));
            jedis.hset(fid, NET, String.valueOf(net));
            jedis.hset(fid, TX_VALUE_MAP, JsonUtils.toNiceJson(txValueMap));
        }

        for (Cash cash : outList) {
            String fid = cash.getOwner();
            String txId = cash.getBirthTxId();
            //income数量，income金额，income数量，income金额，net净变化
            long net;
            String netStr = jedis.hget(fid, NET);
            if(netStr==null) net = 0;
            else net = Long.parseLong(netStr);
            net = net + cash.getValue();

            int incomeCount = 0;
            long incomeValue = 0;
            String[] newCashes = new String[0];
            Map<String,Long> txValueMap;

            if (jedis.hget(fid, INCOME_COUNT) != null) {
                incomeCount = Integer.parseInt(jedis.hget(fid, INCOME_COUNT));
                incomeValue = Long.parseLong(jedis.hget(fid, INCOME_VALUE));
                newCashes = gson.fromJson(jedis.hget(fid,NEW_CASHES),String[].class);
                if(newCashes==null)newCashes = new String[0];
            }

            incomeValue += cash.getValue();

            incomeCount++;

            String[] newIncomeCashes = new String[newCashes.length+1];
            System.arraycopy(newCashes,0,newIncomeCashes,0,newCashes.length);
            newIncomeCashes[newIncomeCashes.length-1]=cash.getId();

            String txValueMapStr = jedis.hget(fid, TX_VALUE_MAP);
            if(txValueMapStr!=null){
                Type mapType = new TypeToken<Map<String, Long>>(){}.getType();
                txValueMap = gson.fromJson(txValueMapStr,mapType);
            }else {
                txValueMap = new HashMap<>();
            }

            txValueMap.merge(txId, cash.getValue(), Long::sum);

            jedis.hset(fid, INCOME_VALUE, String.valueOf(incomeValue));
            jedis.hset(fid, INCOME_COUNT, String.valueOf(incomeCount));
            jedis.hset(fid,NEW_CASHES, JsonUtils.toNiceJson(newIncomeCashes));
            jedis.hset(fid, NET, String.valueOf(net));
            jedis.hset(fid, TX_VALUE_MAP, JsonUtils.toNiceJson(txValueMap));
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
