package SwapHall;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import config.Settings;
import constants.ApiNames;
import constants.CodeMessage;
import constants.IndicesNames;
import constants.Strings;
import data.fcData.ReplyBody;
import data.feipData.Service;
import feature.swap.SwapAffair;
import feature.swap.SwapPendingData;
import initial.Initiator;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import utils.BytesUtils;
import utils.Hex;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@WebServlet(ApiNames.SwapHallPath + ApiNames.SwapPending)
public class SwapPending extends HttpServlet {
    private final Settings settings = Initiator.settings;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ReplyBody replier = new ReplyBody();

        JedisPool jedisPool = (JedisPool) settings.getClient(Service.ServiceType.REDIS);
        try(Jedis jedis = jedisPool.getResource()) {
            replier.setBestHeight(Long.parseLong(jedis.get(Strings.BEST_HEIGHT)));
        }

        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);

        String sid = request.getParameter(Strings.SID);
        if(sid==null){
            replier.replyOtherErrorHttp("SID is required.",response);
            return;
        }
        sid = sid.toLowerCase();

        if(!Hex.isHexString(sid)||sid.length()!=64){
            replier.replyOtherErrorHttp("It's not a SID.",response);
            return;
        }

        String finalSid = sid;

        try {
            GetResponse<SwapPendingData> response1 = esClient.get(b -> b
                    .index(IndicesNames.SWAP_PENDING)
                    .id(finalSid)
            , SwapPendingData.class);

            if (response1.found()) {
                SwapPendingData swapPending = response1.source();
                if(swapPending==null){
                    replier.replyHttp(CodeMessage.Code1011DataNotFound,response);
                    return;
                }
                List<SwapAffair> swapPendingList = swapPending.getPendingList();
                if(swapPendingList==null||swapPendingList.isEmpty()){
                    replier.replyHttp(CodeMessage.Code1011DataNotFound,response);
                    return;
                }
                replier.setData(swapPendingList);
                replier.setTotal((long) swapPendingList.size());
                replier.setGot((long) swapPendingList.size());
                replier.reply0SuccessHttp(swapPendingList,response);
            } else {
                replier.replyHttp(CodeMessage.Code1011DataNotFound,response);
            }
        } catch (Exception e) {
            replier.replyOtherErrorHttp(e.getMessage(),response);
        }
    }
}
