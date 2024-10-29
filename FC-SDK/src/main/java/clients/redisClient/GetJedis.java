package clients.redisClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;


public class GetJedis {
    private static final Logger log = LoggerFactory.getLogger(GetJedis.class);

    public static Jedis getJedis(String ip, int port) {

        if (port == 0 || ip == null) return null;

        Jedis jedis = new Jedis(ip, port);
        try {
            String ping = jedis.ping();
            if (ping.equals("PONG")) {
                return jedis;
            } else {
                log.error("Failed to startup redis.");
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to startup redis.");
            return null;
        }
    }

    public static JedisPool createJedisPool() {
        try {
            log.debug("Create jedis pool.......");
            JedisPoolConfig jedisConfig = new JedisPoolConfig();
//            jedisConfig.setMaxTotal(128);
//            jedisConfig.setMaxIdle(64);
//            jedisConfig.setMinIdle(32);
//            jedisConfig.setTestOnBorrow(true);
//            jedisConfig.setTestOnReturn(true);
//            jedisConfig.setTestWhileIdle(true);
            JedisPool jedisPool1 = new JedisPool(jedisConfig, "localhost", 6379, 10000);
            try (Jedis jedis = jedisPool1.getResource()) {
                jedis.set("test", "ok");
                if ("ok".equals(jedis.get("test"))) {
                    log.debug("Jedis pool created and tested.");
                    jedis.del("test");
                }
            }
            return jedisPool1;
        } catch (Exception e) {
            log.debug("Create jedisPool or jedis wrong. ", e);
        }
        return null;
    }
}
