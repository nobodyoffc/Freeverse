package SwapHall;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.google.gson.Gson;
import config.Settings;
import constants.ApipApiNames;
import data.apipData.RequestBody;
import data.fcData.ReplyBody;
import data.feipData.ServiceType;
import feature.swap.*;
import initial.Initiator;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.HttpRequestChecker;
import utils.EsUtils;
import utils.ObjectUtils;
import utils.http.AuthType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static SwapHall.SwapRegister.REGISTERED_SWAP;
import static constants.FieldNames.SID;
import static constants.IndicesNames.*;

@WebServlet(ApipApiNames.SwapHallPath + ApipApiNames.SwapUpdate)
public class SwapUpdate extends HttpServlet {
    private final Settings settings = Initiator.settings;
    private static final Gson gson = new Gson();

    public static String APIP_SWAP_SID_ADDR_KEY;

    public SwapUpdate() {
        APIP_SWAP_SID_ADDR_KEY = settings.getService().getStdName() + "_" + REGISTERED_SWAP;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        doRequest(request, response, AuthType.ENCRYPTED, settings);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        doRequest(request, response, AuthType.FREE, settings);
    }

    @SuppressWarnings("unchecked")
    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) {
        ReplyBody replier = new ReplyBody(settings);
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) return;

        RequestBody requestBody = replier.getRequestChecker().getRequestBody();
        if(requestBody!=null)replier.setNonce(requestBody.getNonce());

        String sid = null;
        Map<String, Object> dataMap = null;

        if (requestBody != null && requestBody.getFcdsl() != null && requestBody.getFcdsl().getOther() != null) {
            try {
                dataMap = ObjectUtils.objectToMap(requestBody.getFcdsl().getOther(), String.class, Object.class);
                if (dataMap != null) {
                    sid = dataMap.get(SID) != null ? String.valueOf(dataMap.get(SID)) : null;
                }
            } catch (Exception ignored) {}
        } else {
            sid = request.getParameter(SID);
        }

        if (sid == null) {
            replier.replyOtherErrorHttp("The 'sid' field is required.", response);
            return;
        }

        SwapStateData swapState = null;
        if (dataMap != null && dataMap.get("state") != null) {
            swapState = gson.fromJson(gson.toJson(dataMap.get("state")), SwapStateData.class);
        }
        if (swapState == null) {
            replier.replyOtherErrorHttp("The 'state' field is required and must be a valid SwapStateData object.", response);
            return;
        }
        if (swapState.getSid() == null) {
            swapState.setSid(sid);
        }

        JedisPool jedisPool = (JedisPool) settings.getClient(ServiceType.REDIS);
        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);

        String swapRegisterInfoJson;
        try (Jedis jedis = jedisPool.getResource()) {
            swapRegisterInfoJson = jedis.hget(APIP_SWAP_SID_ADDR_KEY, sid);
        }
        if (swapRegisterInfoJson == null) {
            replier.replyOtherErrorHttp("The swap '" + sid + "' is not registered in " + settings.getSid(), response);
            return;
        }

        SwapRegisterInfo swapRegisterInfo = gson.fromJson(swapRegisterInfoJson, SwapRegisterInfo.class);
        if (swapRegisterInfo == null) {
            replier.replyOtherErrorHttp("Failed to parse register info for swap " + sid, response);
            return;
        }

        List<String> resultList = new ArrayList<>();
        resultList.add(writeStateToEs(esClient, swapState));

        if (dataMap != null && dataMap.get("lp") != null) {
            SwapLpData lpData = gson.fromJson(gson.toJson(dataMap.get("lp")), SwapLpData.class);
            resultList.add(writeLpToEs(esClient, lpData));
        }

        if (dataMap != null && dataMap.get("pending") != null) {
            List<SwapAffair> pendingList = SwapDataGetter.getSwapAffairList(dataMap.get("pending"));
            resultList.add(writePendingToEs(esClient, pendingList, sid));
        }

        if (dataMap != null && dataMap.get("finished") != null) {
            List<SwapAffair> finishedList = SwapDataGetter.getSwapAffairList(dataMap.get("finished"));
            resultList.add(writeFinishedToEs(esClient, finishedList, sid));
        }

        if (dataMap != null && dataMap.get("price") != null) {
            List<SwapPriceData> priceList = SwapDataGetter.getSwapPriceList(dataMap.get("price"));
            resultList.add(writePriceToEs(esClient, priceList));
        }

        replier.reply0SuccessHttp(resultList, response);
    }

    private String writePriceToEs(ElasticsearchClient esClient, List<SwapPriceData> swapPriceList) {
        if (swapPriceList == null || swapPriceList.isEmpty()) return "No price data.";
        ArrayList<String> idList = new ArrayList<>();
        for (SwapPriceData p : swapPriceList) idList.add(p.getId());
        try {
            EsUtils.bulkWriteList(esClient, SWAP_PRICE, (ArrayList<SwapPriceData>) swapPriceList, idList, SwapPriceData.class);
        } catch (Exception e) {
            return "Failed to write swap price to ES: " + e.getMessage();
        }
        return "Saved swap price to ES.";
    }

    private String writePendingToEs(ElasticsearchClient esClient, List<SwapAffair> pendingList, String sid) {
        SwapPendingData swapPending = new SwapPendingData();
        swapPending.setSid(sid);
        swapPending.setPendingList(pendingList);
        try {
            esClient.index(i -> i.index(SWAP_PENDING).id(sid).document(swapPending));
        } catch (IOException e) {
            return "Failed to write swap pending to ES.";
        }
        return "Saved swap pending to ES.";
    }

    private String writeFinishedToEs(ElasticsearchClient esClient, List<SwapAffair> finishedList, String sid) {
        if (finishedList == null || finishedList.isEmpty()) return "No finished data.";
        ArrayList<String> idList = new ArrayList<>();
        for (SwapAffair a : finishedList) {
            a.setSid(sid);
            idList.add(a.getId());
        }
        try {
            EsUtils.bulkWriteList(esClient, SWAP_FINISHED, (ArrayList<SwapAffair>) finishedList, idList, SwapAffair.class);
        } catch (Exception e) {
            return "Failed to write swap finished to ES: " + e.getMessage();
        }
        return "Saved swap finished to ES.";
    }

    private String writeLpToEs(ElasticsearchClient esClient, SwapLpData lpData) {
        if (lpData == null || lpData.getSid() == null) return "No LP data.";
        try {
            esClient.index(i -> i.index(SWAP_LP).id(lpData.getSid()).document(lpData));
        } catch (IOException e) {
            return "Failed to write swap LP to ES.";
        }
        return "Saved swap LP to ES.";
    }

    private String writeStateToEs(ElasticsearchClient esClient, SwapStateData swapState) {
        if (swapState == null || swapState.getSid() == null) return "No state data.";
        try {
            esClient.index(i -> i.index(SWAP_STATE).id(swapState.getSid()).document(swapState));
        } catch (IOException e) {
            return "Failed to write swap state to ES.";
        }
        return "Saved swap state to ES.";
    }
}
