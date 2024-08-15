package APIP2V2_Blockchain;

import constants.ApiNames;
import initial.Initiator;
import javaTools.http.AuthType;
import server.FcdslRequestHandler;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.FieldNames.BLOCK_ID;

@WebServlet(name = ApiNames.BlockByIds, value = "/"+ApiNames.SN_2+"/"+ApiNames.Version2 +"/"+ApiNames.BlockByIds)
public class BlockByIds extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        FcdslRequestHandler.doBlockInfoRequest(Initiator.sid,true,BLOCK_ID, request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        FcdslRequestHandler.doBlockInfoRequest(Initiator.sid,true,BLOCK_ID,request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }
    //    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//        response.setContentType("application/json");
//        response.setCharacterEncoding("UTF-8");
//        Replier replier = new Replier();
//        PrintWriter writer = response.getWriter();
//
//        RequestChecker requestChecker = new RequestChecker(request,response, replier);
//
//        DataCheckResult dataCheckResult = requestChecker.checkDataRequest();
//
//        if(dataCheckResult==null)return;
//
//        String addr = dataCheckResult.getAddr();
//
//        RequestBody requestBody = dataCheckResult.getDataRequestBody();
//
//        //Check API
//        if(!isThisApiRequest(requestBody)){
//            response.setHeader(ReplyInfo.CodeInHeader,String.valueOf(ReplyInfo.Code1012BadQuery));
//            writer.write(replier.reply1012BadQuery(addr));
//            return;
//        }
//
//        //Request
//
//        DataRequestHandler esRequest = new DataRequestHandler(dataCheckResult.getAddr(),requestBody,response,replier);
//        List<BlockHas> blockHasList;
//        try {
//            blockHasList = esRequest.doRequest(IndicesNames.BLOCK_HAS,null, BlockHas.class);
//            if(blockHasList==null||blockHasList.size()==0){
//                return;
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            response.setHeader(ReplyInfo.CodeInHeader,String.valueOf(ReplyInfo.Code1012BadQuery));
//            writer.write(replier.reply1012BadQuery(addr));
//            return;
//        }
//
//        List<String> idList = new ArrayList<>();
//        for(BlockHas blockHas : blockHasList){
//            idList.add(blockHas.getBlockId());
//        }
//
//        List<Block> blockList = null;
//        try {
//            blockList = EsTools.getMultiByIdList(Initiator.esClient, IndicesNames.BLOCK, idList, Block.class).getResultList();
//            if(blockList==null||blockList.size()==0){
//                return;
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            response.setHeader(ReplyInfo.CodeInHeader,String.valueOf(ReplyInfo.Code1012BadQuery));
//            writer.write(replier.reply1012BadQuery(addr));
//        }
//
//        List<BlockInfo> meetList = BlockInfo.mergeBlockAndBlockHas(blockList, blockHasList);
//
//        Map<String,BlockInfo> meetMap = new HashMap<>();
//        for(BlockInfo blockInfo :meetList){
//            meetMap.put(blockInfo.getBlockId(),blockInfo);
//        }
//
//        //response
//
//        replier.setData(meetMap);
//        replier.setGot(meetMap.size());
//        replier.setTotal((long) meetMap.size());
//        esRequest.writeSuccess(dataCheckResult.getSessionKey());
//        return;
//    }
//
//    private boolean isThisApiRequest(RequestBody requestBody) {
//        if(requestBody.getFcdsl()==null)
//            return false;
//        if(requestBody.getFcdsl().getIds()==null)
//            return false;
//        return true;
//    }
}
