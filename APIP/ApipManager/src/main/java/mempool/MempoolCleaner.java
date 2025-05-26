package mempool;

import data.fchData.Block;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.Constants;
import constants.Strings;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import utils.FchUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static core.fch.TxCreator.getBestBlock;


public class MempoolCleaner implements Runnable {
    private volatile AtomicBoolean running = new AtomicBoolean(true);
    private final String blockFilePath;
    private final ElasticsearchClient esClient;
    private final JedisPool jedisPool;
    public MempoolCleaner(String blockFilePath, ElasticsearchClient esClient, JedisPool jedisPool) {
        this.blockFilePath =blockFilePath;
        this.esClient = esClient;
        this.jedisPool= jedisPool;
    }

    public void run() {
        System.out.println("MempoolCleaner running...");
        try {
            while (running.get()) {
                FchUtils.waitForChangeInDirectory(blockFilePath,running);
                try(Jedis jedis1 = jedisPool.getResource()) {
                    jedis1.select(Constants.RedisDb3Mempool);
                    jedis1.flushDB();

                    TimeUnit.SECONDS.sleep(2);
                    jedis1.select(Constants.RedisDb0Common);
                    Block block = getBestBlock(esClient);

                    jedis1.set(Strings.BEST_HEIGHT,String.valueOf(block.getHeight()));
                    jedis1.set(Strings.BEST_BLOCK_ID,block.getId());
                }
            }
        }catch (Exception e){
            e.printStackTrace();
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
