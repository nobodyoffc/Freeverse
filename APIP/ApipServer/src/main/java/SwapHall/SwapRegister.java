package SwapHall;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import config.Settings;
import constants.ApipApiNames;
import constants.IndicesNames;
import data.feipData.Service;
import data.feipData.ServiceType;
import feature.swap.SwapParams;
import feature.swap.SwapRegisterInfo;
import initial.Initiator;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import utils.JsonUtils;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.*;

import static constants.Strings.DOT_JSON;

@WebServlet(ApipApiNames.SwapHallPath + ApipApiNames.SwapRegister)
public class SwapRegister extends HttpServlet {
    private final Settings settings = Initiator.settings;
    private static final Gson gson = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>(){}.getType();

    public static String APIP_SWAP_SID_ADDR_KEY;
    public static String REGISTERED_SWAP = "registeredSwap";

    public SwapRegister() {
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
        String addr = dataMap.get("registerer") != null ? String.valueOf(dataMap.get("registerer")) : null;

        JedisPool jedisPool = (JedisPool) settings.getClient(ServiceType.REDIS);
        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);

        String swapRegisterInfoJson;
        try (Jedis jedis = jedisPool.getResource()) {
            swapRegisterInfoJson = jedis.hget(APIP_SWAP_SID_ADDR_KEY, sid);
        }
        if (swapRegisterInfoJson != null) {
            writeSuccess(writer, sid + " had been registered.");
            return;
        }

        GetResponse<Service> result = esClient.get(g -> g.index(IndicesNames.SERVICE).id(sid), Service.class);

        Service swapService = result.source();
        if (swapService == null) {
            writeError(writer, response, "Failed to get the swap service: " + sid);
            return;
        }

        if (swapService.isClosed()) {
            writeError(writer, response, "The swap service " + sid + " is set to closed.");
            return;
        }

        if (!swapService.getActive()) {
            writeError(writer, response, "The swap service " + sid + " is no longer active.");
            return;
        }

        if (addr != null) {
            ArrayList<String> swapRelatedAddrList = new ArrayList<>();
            swapRelatedAddrList.add(swapService.getOwner());
            if (swapService.getWaiters() != null)
                swapRelatedAddrList.addAll(swapService.getWaiters());
            try {
                String swapParamsJson = gson.toJson(swapService.getParams());
                SwapParams swapParams = gson.fromJson(swapParamsJson, SwapParams.class);
                if (swapParams.getgAddr() != null)
                    swapRelatedAddrList.add(swapParams.getgAddr());
                if (swapParams.getmAddr() != null)
                    swapRelatedAddrList.add(swapParams.getmAddr());
            } catch (Exception ignore) {}

            boolean goodFid = swapRelatedAddrList.contains(addr);
            if (!goodFid) {
                writeError(writer, response, "Requester " + addr + " is not the owner, waiter, or dealer of the swap service. " + Arrays.toString(swapRelatedAddrList.toArray()));
                return;
            }
        }

        SwapRegisterInfo swapRegisterInfo = new SwapRegisterInfo();
        swapRegisterInfo.setSid(sid);
        swapRegisterInfo.setRegisterer(addr);
        swapRegisterInfo.setRegisterTime(System.currentTimeMillis());

        saveSwapIntoRedis(sid, swapRegisterInfo);
        if (!saveSwapIntoFile(swapRegisterInfo)) {
            writeError(writer, response, "Server failed to save information to file.");
            return;
        }

        writeSuccess(writer, sid + " registered by " + addr);
    }

    private void writeSuccess(PrintWriter writer, String data) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("message", "Success.");
        result.put("data", data);
        writer.write(gson.toJson(result));
    }

    private void writeError(PrintWriter writer, HttpServletResponse response, String message) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        Map<String, Object> err = new HashMap<>();
        err.put("code", 1020);
        err.put("message", message);
        writer.write(gson.toJson(err));
    }

    private static boolean saveSwapIntoFile(SwapRegisterInfo swapRegisterInfo) throws IOException {
        File file = new File(APIP_SWAP_SID_ADDR_KEY + DOT_JSON);
        if (!file.exists()) {
            if (!file.createNewFile()) {
                return false;
            }
        }
        List<SwapRegisterInfo> swapRegisterInfoList = JsonUtils.readJsonObjectListFromFile(APIP_SWAP_SID_ADDR_KEY + DOT_JSON, SwapRegisterInfo.class);
        if (swapRegisterInfoList == null) swapRegisterInfoList = new ArrayList<>();
        swapRegisterInfoList.add(swapRegisterInfo);
        JsonUtils.writeListToJsonFile(swapRegisterInfoList, APIP_SWAP_SID_ADDR_KEY + DOT_JSON, false);
        return true;
    }

    private void saveSwapIntoRedis(String sid, SwapRegisterInfo swapRegisterInfo) {
        JedisPool jedisPool = (JedisPool) settings.getClient(ServiceType.REDIS);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset(APIP_SWAP_SID_ADDR_KEY, sid, gson.toJson(swapRegisterInfo));
        }
    }
}

