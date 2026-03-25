package SwapHall;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import config.Settings;
import constants.ApipApiNames;
import constants.CodeMessage;
import constants.Strings;
import data.fcData.ReplyBody;
import data.feipData.ServiceType;
import feature.swap.SwapStateData;
import initial.Initiator;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import utils.Hex;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.IndicesNames.SWAP_STATE;

@WebServlet(ApipApiNames.SwapHallPath + ApipApiNames.SwapState)
public class SwapStatus extends HttpServlet {
    private final Settings settings = Initiator.settings;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ReplyBody replier = new ReplyBody();

        JedisPool jedisPool = (JedisPool) settings.getClient(ServiceType.REDIS);
        try(Jedis jedis = jedisPool.getResource()) {
            replier.setBestHeight(Long.parseLong(jedis.get(Strings.BEST_HEIGHT)));
        }

        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);

        String sid = request.getParameter(Strings.SID);
        if(sid==null){
            replier.replyOtherErrorHttp("SID is required.",response);
            return;
        }
        sid=sid.toLowerCase();

        if(!Hex.isHexString(sid)||sid.length()!=64){
            replier.replyOtherErrorHttp("It's not a SID.",response);
            return;
        }

        String finalSid = sid;

        try {
            GetResponse<SwapStateData> response1 = esClient.get(b -> b
                    .index(SWAP_STATE)
                    .id(finalSid)
            , SwapStateData.class);

            if (response1.found()) {
                SwapStateData swapState = response1.source();
                replier.setData(swapState);
                replier.setTotal(1L);
                replier.setGot(1L);
                replier.reply0SuccessHttp(swapState,response);
            } else {
                replier.replyHttp(CodeMessage.Code1011DataNotFound,response);
            }
        } catch (Exception e) {
            replier.replyOtherErrorHttp(e.getMessage(),response);
        }
    }
}
