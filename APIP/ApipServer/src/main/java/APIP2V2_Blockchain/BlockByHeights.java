package APIP2V2_Blockchain;


import constants.ApiNames;

import initial.Initiator;
import javaTools.http.AuthType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.Strings.HEIGHT;
import static server.FcdslRequestHandler.doBlockInfoRequest;

@WebServlet(name = ApiNames.BlockByHeights, value = "/"+ApiNames.SN_2+"/"+ApiNames.Version2 +"/"+ApiNames.BlockByHeights)
public class BlockByHeights extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doBlockInfoRequest(Initiator.sid,true,HEIGHT, request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doBlockInfoRequest(Initiator.sid,true,HEIGHT,request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }
//    private void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) {
//
//        FcReplier replier = new FcReplier(sid,response);
//        try (Jedis jedis = jedisPool.getResource()) {
//            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(sid, request, replier, authType, jedis);
//            if (requestCheckResult == null) {
//                return;
//            }
//
//            if (requestCheckResult.getRequestBody().getFcdsl().getIds() == null) {
//                replier.reply(ReplyCodeMessage.Code1012BadQuery, null, jedis);
//                return;
//            }
//            //Set default sort.
//            ArrayList<Sort> defaultSortList = Sort.makeSortList(HEIGHT, false, FieldNames.BLOCK_ID, true, null, null);
//
//            //Request
//            String index = IndicesNames.BLOCK_HAS;
//
//            FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(requestCheckResult.getRequestBody(), response, replier, esClient);
//            List<BlockHas> blockHasList = fcdslRequestHandler.doRequest(ADDRESS, defaultSortList, BlockHas.class, jedis);
//            if (blockHasList == null || blockHasList.size() == 0) {
//                return;
//            }
//
//            List<String> idList = new ArrayList<>();
//            for (BlockHas blockHas : blockHasList) {
//                idList.add(blockHas.getBlockId());
//            }
//
//            List<Block> blockList;
//
//            blockList = EsTools.getMultiByIdList(Initiator.esClient, IndicesNames.BLOCK, idList, Block.class).getResultList();
//            if (blockList == null ) {
//                replier.replyOtherError("Failed to get block info.", null, jedis);
//                return;
//            }
//            if (blockList.size()==0 ) {
//                replier.reply(ReplyCodeMessage.Code1011DataNotFound,null,jedis);
//                return;
//            }
//
//            List<BlockInfo> meetList = BlockInfo.mergeBlockAndBlockHas(blockList, blockHasList);
//
//            Map<String, BlockInfo> meetMap = new HashMap<>();
//            for (BlockInfo blockInfo : meetList) {
//                meetMap.put(String.valueOf(blockInfo.getHeight()), blockInfo);
//            }
//
//            //response
//            replier.setGot((long) meetMap.size());
//            replier.setTotal((long) meetMap.size());
//            replier.reply0Success(meetMap, jedis);
//        } catch (Exception e) {
//            replier.replyOtherError(e.getMessage(), null, null);
//        }
//    }
}