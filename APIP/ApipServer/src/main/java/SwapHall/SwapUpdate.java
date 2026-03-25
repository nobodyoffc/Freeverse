package SwapHall;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import config.Settings;
import constants.ApipApiNames;
import data.feipData.ServiceType;
import feature.swap.*;
import initial.Initiator;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import utils.EsUtils;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static SwapHall.SwapRegister.REGISTERED_SWAP;
import static constants.IndicesNames.*;

@WebServlet(ApipApiNames.SwapHallPath + ApipApiNames.SwapUpdate)
public class SwapUpdate extends HttpServlet {
    private final Settings settings = Initiator.settings;
    private static final Gson gson = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>(){}.getType();

    public static String APIP_SWAP_SID_ADDR_KEY;

    public SwapUpdate() {
        APIP_SWAP_SID_ADDR_KEY = settings.getService().getStdName() + "_" + REGISTERED_SWAP;
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter writer = response.getWriter();

        byte[] bodyBytes = request.getInputStream().readAllBytes();
        if (bodyBytes == null || bodyBytes.length == 0) {
            writeError(writer, response, "Request body is empty.");
            return;
        }

        Map<String, Object> dataMap;
        try {
            dataMap = gson.fromJson(new String(bodyBytes), MAP_TYPE);
        } catch (Exception e) {
            writeError(writer, response, "Invalid JSON: " + e.getMessage());
            return;
        }

        if (dataMap == null || dataMap.get("sid") == null) {
            writeError(writer, response, "The 'sid' field is required.");
            return;
        }

        String sid = String.valueOf(dataMap.get("sid"));

        Object stateObj = dataMap.get("state");
        SwapStateData swapState = null;
        if (stateObj != null) {
            swapState = gson.fromJson(gson.toJson(stateObj), SwapStateData.class);
        }
        if (swapState == null) {
            writeError(writer, response, "The 'state' field is required and must be a valid SwapStateData object.");
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
            writeError(writer, response, "The swap '" + sid + "' is not registered in " + settings.getSid());
            return;
        }

        SwapRegisterInfo swapRegisterInfo = gson.fromJson(swapRegisterInfoJson, SwapRegisterInfo.class);
        if (swapRegisterInfo == null) {
            writeError(writer, response, "Failed to parse register info for swap " + sid);
            return;
        }

        List<String> resultList = new ArrayList<>();
        resultList.add(writeStateToEs(esClient, swapState));

        if (dataMap.get("lp") != null) {
            SwapLpData lpData = gson.fromJson(gson.toJson(dataMap.get("lp")), SwapLpData.class);
            resultList.add(writeLpToEs(esClient, lpData));
        }

        if (dataMap.get("pending") != null) {
            List<SwapAffair> pendingList = SwapDataGetter.getSwapAffairList(dataMap.get("pending"));
            resultList.add(writePendingToEs(esClient, pendingList, sid));
        }

        if (dataMap.get("finished") != null) {
            List<SwapAffair> finishedList = SwapDataGetter.getSwapAffairList(dataMap.get("finished"));
            resultList.add(writeFinishedToEs(esClient, finishedList, sid));
        }

        if (dataMap.get("price") != null) {
            List<SwapPriceData> priceList = SwapDataGetter.getSwapPriceList(dataMap.get("price"));
            resultList.add(writePriceToEs(esClient, priceList));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("message", "Success.");
        result.put("data", resultList);
        writer.write(gson.toJson(result));
    }

    private void writeError(PrintWriter writer, HttpServletResponse response, String message) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        Map<String, Object> err = new HashMap<>();
        err.put("code", 1020);
        err.put("message", message);
        writer.write(gson.toJson(err));
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

