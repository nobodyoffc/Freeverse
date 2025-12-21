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
import feature.swap.SwapParams;
import feature.swap.SwapRegisterInfo;
import initial.Initiator;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.HttpRequestChecker;
import utils.JsonUtils;
import utils.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static constants.FieldNames.SID;
import static constants.Strings.DOT_JSON;

@WebServlet(ApipApiNames.SwapHallPath + ApipApiNames.SwapRegister)
public class SwapRegister extends HttpServlet {
    private final Settings settings = Initiator.settings;
    public static String APIP_SWAP_SID_ADDR_KEY;
    public static String REGISTERED_SWAP = "registeredSwap";

    public SwapRegister() {
        APIP_SWAP_SID_ADDR_KEY = settings.getService().getStdName() + "_" + REGISTERED_SWAP;
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ReplyBody replier = new ReplyBody();

        JedisPool jedisPool = (JedisPool) settings.getClient(Service.ServiceType.REDIS);
        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);

        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings);

        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, AuthType.SYMKEY_ENCRYPT);
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

        Object swapRegisterRequestData = requestBody.getFcdsl().getOther();

        Gson gson = new Gson();
        Map<String,String> dataMap = JsonUtils.getStringStringMap(gson.toJson(swapRegisterRequestData));

        if(dataMap == null || dataMap.get(SID)==null){
            replier.replyOtherErrorHttp("The key of sid is no found in fcdsl.other.",response);
            return;
        }

        String sid = dataMap.get(SID);
        String swapRegisterInfoJson;
        try(Jedis jedis = jedisPool.getResource()){
            swapRegisterInfoJson = jedis.hget(APIP_SWAP_SID_ADDR_KEY, sid);
        }
        if(swapRegisterInfoJson!=null) {
            replier.setData(sid + " had been registered" + " by " + addr);
            replier.reply0SuccessHttp(sid + " had been registered" + " by " + addr,response);
            return;
        }

        String index = IndicesNames.SERVICE;

        GetResponse<Service> result = esClient.get(g -> g.index(index).id(sid), Service.class);

        Service swapService = result.source();
        if(swapService==null){
            replier.replyOtherErrorHttp("Failed to get the swap service: "+sid,response);
            return;
        }

        if(swapService.isClosed()){
            replier.replyOtherErrorHttp("The swap service "+sid+" is set to closed.",response);
            return;
        }

        if(!swapService.isActive()){
            replier.replyOtherErrorHttp("The swap service "+sid+" is no longer active.",response);
            return;
        }

        ArrayList<String> swapRelatedAddrList = new ArrayList<>();
        swapRelatedAddrList.add(swapService.getOwner());
        if(swapService.getWaiters()!=null)
            swapRelatedAddrList.addAll(Arrays.asList(swapService.getWaiters()));
        try {
            String swapParamsJson = gson.toJson(swapService.getParams());
            SwapParams swapParams = gson.fromJson(swapParamsJson, SwapParams.class);
            if(swapParams.getgAddr()!=null)
                swapRelatedAddrList.add(swapParams.getgAddr());
            if(swapParams.getmAddr()!=null)
                swapRelatedAddrList.add(swapParams.getmAddr());
        }catch (Exception ignore){}
        boolean goodFid = false;
        for(String fid: swapRelatedAddrList){
            if(fid.equals(addr)){
                goodFid=true;
                break;
            }
        }
        if (!goodFid){
            replier.replyOtherErrorHttp("Requester "+addr+" is not the owner, waiter, or dealer of the swap service."+Arrays.toString(swapRelatedAddrList.toArray()),response);
            return;
        }

        SwapRegisterInfo swapRegisterInfo = new SwapRegisterInfo();
        swapRegisterInfo.setSid(sid);
        swapRegisterInfo.setRegisterer(addr);
        swapRegisterInfo.setRegisterTime(System.currentTimeMillis());

        saveSwapIntoRedis(gson, sid, swapRegisterInfo);
        if(!saveSwapIntoFile(swapRegisterInfo)){
            replier.replyOtherErrorHttp("Server failed to save information to file.",response);
            return;
        }

        replier.setData(sid+" registered"+" by "+addr);
        replier.reply0SuccessHttp(sid+" registered"+" by "+addr,response);
    }

    private static boolean saveSwapIntoFile(SwapRegisterInfo swapRegisterInfo) throws IOException {
        File file = new File(APIP_SWAP_SID_ADDR_KEY + DOT_JSON);
        if(!file.exists()){
            if(!file.createNewFile()){
                return false;
            }
        }
        List<SwapRegisterInfo> swapRegisterInfoList = JsonUtils.readJsonObjectListFromFile(APIP_SWAP_SID_ADDR_KEY + DOT_JSON, SwapRegisterInfo.class);
        if(swapRegisterInfoList==null)swapRegisterInfoList=new ArrayList<>();
        swapRegisterInfoList.add(swapRegisterInfo);
        JsonUtils.writeListToJsonFile(swapRegisterInfoList,APIP_SWAP_SID_ADDR_KEY + DOT_JSON,false);
        return true;
    }

    private void saveSwapIntoRedis(Gson gson, String sid, SwapRegisterInfo swapRegisterInfo) {
        JedisPool jedisPool = (JedisPool) settings.getClient(Service.ServiceType.REDIS);
        try(Jedis jedis = jedisPool.getResource()){
            jedis.hset(APIP_SWAP_SID_ADDR_KEY, sid, gson.toJson(swapRegisterInfo));
        }
    }

    private boolean isThisApiRequest(RequestBody requestBody) {
        if(requestBody.getFcdsl()==null)
            return false;
        return requestBody.getFcdsl().getOther() != null;
    }


}

