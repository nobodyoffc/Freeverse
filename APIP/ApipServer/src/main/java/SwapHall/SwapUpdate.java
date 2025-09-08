package SwapHall;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import com.google.gson.Gson;
import config.Settings;
import constants.ApiNames;
import data.apipData.RequestBody;
import data.fcData.ReplyBody;
import data.feipData.Service;
import feature.swap.*;
import initial.Initiator;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.HttpRequestChecker;
import utils.EsUtils;
import utils.ObjectUtils;
import utils.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static constants.ApiNames.REGISTERED_SWAP;
import static constants.FieldNames.SID;
import static constants.IndicesNames.*;
import static constants.Strings.*;

@WebServlet(ApiNames.SwapHallPath + ApiNames.SwapUpdate)
public class SwapUpdate extends HttpServlet {
    private final Settings settings = Initiator.settings;

    public static String APIP_SWAP_SID_ADDR_KEY;

    public SwapUpdate() {
        APIP_SWAP_SID_ADDR_KEY = settings.getService().getStdName() + "_" + REGISTERED_SWAP;
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ReplyBody replier = new ReplyBody();

        JedisPool jedisPool = (JedisPool) settings.getClient(Service.ServiceType.REDIS);
        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);

        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings);

        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, AuthType.FC_SIGN_BODY);
        if (!isOk) {
            return;
        }

        String addr = httpRequestChecker.getFid();

        RequestBody requestBody = httpRequestChecker.getRequestBody();

        replier.setNonce(requestBody.getNonce());
        //Check API
        if(!isThisApiRequest(requestBody)){
            replier.replyOtherErrorHttp("Bad query: other field is required",response);
            return;
        }

        Object uploadedMapObj = requestBody.getFcdsl().getOther();

        Gson gson = new Gson();
        Map<String,Object> dataMap = ObjectUtils.objectToMap(uploadedMapObj,String.class,Object.class);


        if(dataMap.get(SID) == null){
            replier.replyOtherErrorHttp("The key of sid is no found in fcdsl.other.",response);
            return;
        }

        SwapStateData swapState = gson.fromJson(gson.toJson(dataMap.get(STATE)), SwapStateData.class);

        if(swapState==null){
            replier.replyOtherErrorHttp("No swapState was found in the other filed of the FCDSL of the request body.",response);
            return;
        }

        String sid = swapState.getSid();
        String swapRegisterInfoJson;
        try(Jedis jedis = jedisPool.getResource()){
            swapRegisterInfoJson = jedis.hget(APIP_SWAP_SID_ADDR_KEY, sid);
        }
        if(swapRegisterInfoJson==null){
            replier.replyOtherErrorHttp("The swap is not registered in "+ settings.getSid(),response);
            return;
        }

        SwapRegisterInfo swapRegisterInfo = gson.fromJson(swapRegisterInfoJson,SwapRegisterInfo.class);
        if(swapRegisterInfo==null){
            replier.replyOtherErrorHttp("Failed to get the swap register info of "+ swapState.getSid(),response);
            return;
        }

        if(!swapRegisterInfo.getRegisterer().equals(addr)){
            replier.replyOtherErrorHttp("The uploader has to be the swap registerer "+ swapRegisterInfo.getRegisterer(),response);
            return;
        }

        List<String> resultList = new ArrayList<>();
        String stateResult = writeStateToEs(esClient,swapState);
        resultList.add(stateResult);

        if(dataMap.get(LP)!=null){
            SwapLpData lpMaps = gson.fromJson(gson.toJson(dataMap.get(LP)), SwapLpData.class);
            String result = writeLpToEs(esClient,lpMaps);
            resultList.add(result);
        }

        if(dataMap.get(PENDING)!=null){
            List<SwapAffair> pendingList = SwapDataGetter.getSwapAffairList(dataMap.get(PENDING));
            String result = writePendingToEs(esClient,pendingList,swapState.getSid());
            resultList.add(result);
        }

        if(dataMap.get(FINISHED)!=null){
            List<SwapAffair> finishedList = SwapDataGetter.getSwapAffairList(dataMap.get(FINISHED));
            String result = writeFinishedToEs(esClient,finishedList,swapState.getSid());
            resultList.add(result);
        }

        if(dataMap.get(PRICE)!=null){
            List<SwapPriceData> swapPriceList = SwapDataGetter.getSwapPriceList(dataMap.get(PRICE));
            String result = writePriceToEs(esClient,swapPriceList);
            resultList.add(result);
        }

        replier.setData(resultList);
        replier.reply0SuccessHttp(resultList,response);
    }

    private String writePriceToEs(ElasticsearchClient esClient, List<SwapPriceData> swapPriceList) {
        if(swapPriceList==null||swapPriceList.isEmpty())return "No data.";
        ArrayList<String>idList =new ArrayList<>();
        for(SwapPriceData swapPriceData : swapPriceList){
            idList.add(swapPriceData.getId());
        }
        try {
            EsUtils.bulkWriteList(esClient,SWAP_PRICE,(ArrayList<SwapPriceData>) swapPriceList,idList,SwapPriceData.class);
        } catch (Exception e) {
            return "Failed to write into ES: "+e.getMessage();
        }
        return "Saved swap price to ES.";
    }

    private String writePendingToEs(ElasticsearchClient esClient, List<SwapAffair> pendingList, String sid) {

        SwapPendingData swapPending = new SwapPendingData();
        swapPending.setSid(sid);
        swapPending.setPendingList(pendingList);
        try {
            IndexResponse result = esClient.index(i -> i.index(SWAP_PENDING).id(sid).document(swapPending));
        } catch (IOException e) {
            return "Failed to write swap pending into ES.";
        }
        return "Swap pending was updated to ES.";
    }

    private String writeFinishedToEs(ElasticsearchClient esClient, List<SwapAffair> finishedList, String sid) {
        if(finishedList==null||finishedList.isEmpty())return "No data.";
        ArrayList<String>idList =new ArrayList<>();
        for(SwapAffair swapAffair : finishedList){
            swapAffair.setSid(sid);
            idList.add(swapAffair.getId());
        }
        try {
            EsUtils.bulkWriteList(esClient,SWAP_FINISHED,(ArrayList<SwapAffair>) finishedList,idList,SwapAffair.class);
        } catch (Exception e) {
            return "Failed to write into ES: "+e.getMessage();
        }
        return "Saved swap finished to ES.";
    }

    private String writeLpToEs(ElasticsearchClient esClient, SwapLpData lpMaps) {
        if(lpMaps==null||lpMaps.getSid()==null)return "No data.";
        try {
            esClient.index(i -> i.index(SWAP_LP).id(lpMaps.getSid()).document(lpMaps));
        } catch (IOException e) {
            return "Failed to write swap lpMaps into ES.";
        }
        return "Saved swap lpMaps to ES.";
    }

    private String writeStateToEs(ElasticsearchClient esClient, SwapStateData swapState) {
        if(swapState==null||swapState.getSid()==null)return "No data.";
        try {
            esClient.index(i -> i.index(SWAP_STATE).id(swapState.getSid()).document(swapState));
        } catch (IOException e) {
            return "Failed to write swap state into ES.";
        }
        return "Saved swap state to ES.";
    }

    private boolean isThisApiRequest(RequestBody requestBody) {
        if(requestBody.getFcdsl()==null)
            return false;
        return requestBody.getFcdsl().getOther() != null;
    }
}

