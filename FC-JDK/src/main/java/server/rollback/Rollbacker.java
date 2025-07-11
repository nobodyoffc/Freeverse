package server.rollback;

import clients.ApipClient;
import data.apipData.BlockInfo;
import constants.FieldNames;
import data.fchData.Block;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.Strings;
import utils.EsUtils;
import utils.RedisUtils;
import utils.http.AuthType;
import utils.http.RequestMethod;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.order.Order;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import static core.fch.BlockFileUtils.getBlockByHeight;
import static constants.IndicesNames.BLOCK;
import static constants.IndicesNames.ORDER;
import static constants.Strings.ORDER_LAST_HEIGHT;
import static config.Settings.addSidBriefToName;

public class Rollbacker {
    /**
     * 检查上一个orderHeight与orderBlockId是否一致
     * 不一致则orderHeight减去30
     * 对回滚区块的es的order做减值处理。
    * */
    public static boolean isRolledBack(long lastHeight, String lastBlockId, ApipClient apipClient) throws IOException {

        if (lastHeight==0 || lastBlockId ==null)return false;

        Map<String, BlockInfo> heightBlockInfoMap = apipClient.blockByHeights(RequestMethod.POST, AuthType.FC_SIGN_BODY, String.valueOf(lastHeight));
        if(heightBlockInfoMap==null)heightBlockInfoMap = apipClient.blockByHeights(RequestMethod.POST, AuthType.FC_SIGN_BODY, String.valueOf(lastHeight));
        if(heightBlockInfoMap==null)throw new RuntimeException("Failed to get last block info. Check APIP service.");

        BlockInfo blockInfo = heightBlockInfoMap.get(String.valueOf(lastHeight));
        return !blockInfo.getId().equals(lastBlockId);
    }

    public static boolean isRolledBack(ElasticsearchClient esClient,long lastHeight,String lastBlockId) throws IOException {
        if(esClient==null) {
            System.out.println("Failed to check rollback. Start a ES client first.");
            return false;
        }

        if (lastHeight==0 || lastBlockId ==null)return false;
        Block block =null;
        try {
            block = EsUtils.getById(esClient, BLOCK, lastBlockId, Block.class);
        }catch (Exception e){
            e.printStackTrace();
        }
        return block == null;
    }

    public static void rollback(String sid,long height, ElasticsearchClient esClient, JedisPool jedisPool)  {
        ArrayList<Order> orderList = null;
        try (Jedis jedis0Common = jedisPool.getResource()){
            String index = addSidBriefToName(sid,ORDER);
            orderList= EsUtils.getListSinceHeight(esClient, index,"height",height,Order.class);

            if(orderList==null || orderList.size()==0)return;
            minusFromBalance(sid,orderList, esClient, jedisPool);

            jedis0Common.set(addSidBriefToName(sid,ORDER_LAST_HEIGHT), String.valueOf(height));
            Block block = getBlockByHeight(esClient, height);
            jedis0Common.set(Strings.ORDER_LAST_BLOCK_ID, block.getId());
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private static void minusFromBalance(String sid,ArrayList<Order> orderList, ElasticsearchClient esClient, JedisPool jedisPool) throws Exception {
        ArrayList<String> idList= new ArrayList<>();
        try(Jedis jedis = jedisPool.getResource()) {
            for (Order order : orderList) {
                String addr = order.getFromFid();
                long balance = RedisUtils.readHashLong(jedis, addSidBriefToName(sid, FieldNames.BALANCE), addr);
                jedis.hset(addSidBriefToName(sid, FieldNames.BALANCE), addr, String.valueOf(balance - order.getAmount()));

                idList.add(order.getOrderId());
            }
        }
        String index = addSidBriefToName(sid,ORDER);
        EsUtils.bulkDeleteList(esClient, index, idList);
    }
}
