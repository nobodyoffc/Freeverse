package SwapHall;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.google.gson.Gson;
import config.Settings;
import constants.ApipApiNames;
import constants.IndicesNames;
import data.apipData.RequestBody;
import data.fcData.ReplyBody;
import data.feipData.Service;
import data.feipData.ServiceType;
import feature.swap.SwapParams;
import feature.swap.SwapRegisterInfo;
import initial.Initiator;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.HttpRequestChecker;
import utils.JsonUtils;
import utils.ObjectUtils;
import utils.http.AuthType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static constants.FieldNames.SID;
import static constants.Strings.DOT_JSON;

@WebServlet(ApipApiNames.SwapHallPath + ApipApiNames.SwapRegister)
public class SwapRegister extends HttpServlet {
    private final Settings settings = Initiator.settings;
    private static final Gson gson = new Gson();

    public static String APIP_SWAP_SID_ADDR_KEY;
    public static String REGISTERED_SWAP = "registeredSwap";

    public SwapRegister() {
        APIP_SWAP_SID_ADDR_KEY = settings.getService().getStdName() + "_" + REGISTERED_SWAP;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        doRequest(request, response, AuthType.FREE, settings);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        doRequest(request, response, AuthType.ENCRYPTED, settings);
    }

    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) {
        ReplyBody replier = new ReplyBody(settings);
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) return;

        String sid = null;
        String addr = null;

        RequestBody requestBody = replier.getRequestChecker().getRequestBody();
        if (requestBody != null && requestBody.getFcdsl() != null && requestBody.getFcdsl().getOther() != null) {
            if(requestBody!=null)replier.setNonce(requestBody.getNonce());
            try {
                Map<String, String> otherMap = ObjectUtils.objectToMap(requestBody.getFcdsl().getOther(), String.class, String.class);
                if (otherMap != null) {
                    sid = otherMap.get(SID);
                    addr = otherMap.get("registerer");
                }
            } catch (Exception ignored) {}
        } else {
            if(requestBody!=null)replier.setNonce(requestBody.getNonce());
            sid = request.getParameter(SID);
            addr = request.getParameter("registerer");
        }

        if (sid == null) {
            replier.replyOtherErrorHttp("The 'sid' field is required.", response);
            return;
        }

        JedisPool jedisPool = (JedisPool) settings.getClient(ServiceType.REDIS);
        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);

        String swapRegisterInfoJson;
        try (Jedis jedis = jedisPool.getResource()) {
            swapRegisterInfoJson = jedis.hget(APIP_SWAP_SID_ADDR_KEY, sid);
        }
        if (swapRegisterInfoJson != null) {
            replier.reply0SuccessHttp(sid + " had been registered.", response);
            return;
        }

        try {
            String finalSid = sid;
            GetResponse<Service> result = esClient.get(g -> g.index(IndicesNames.SERVICE).id(finalSid), Service.class);

            Service swapService = result.source();
            if (swapService == null) {
                replier.replyOtherErrorHttp("Failed to get the swap service: " + sid, response);
                return;
            }

            if (swapService.isClosed()) {
                replier.replyOtherErrorHttp("The swap service " + sid + " is set to closed.", response);
                return;
            }

            if (!swapService.getActive()) {
                replier.replyOtherErrorHttp("The swap service " + sid + " is no longer active.", response);
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
                    replier.replyOtherErrorHttp("Requester " + addr + " is not the owner, waiter, or dealer of the swap service. " + Arrays.toString(swapRelatedAddrList.toArray()), response);
                    return;
                }
            }

            SwapRegisterInfo swapRegisterInfo = new SwapRegisterInfo();
            swapRegisterInfo.setSid(sid);
            swapRegisterInfo.setRegisterer(addr);
            swapRegisterInfo.setRegisterTime(System.currentTimeMillis());

            saveSwapIntoRedis(sid, swapRegisterInfo);
            if (!saveSwapIntoFile(swapRegisterInfo)) {
                replier.replyOtherErrorHttp("Server failed to save information to file.", response);
                return;
            }

            replier.reply0SuccessHttp(sid + " registered by " + addr, response);
        } catch (Exception e) {
            replier.replyOtherErrorHttp(e.getMessage(), response);
        }
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
