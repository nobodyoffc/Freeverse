package APIP16V1_Token;

import apip.apipData.Sort;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.ApiNames;
import constants.IndicesNames;
import fcData.FcReplier;
import feip.feipData.TokenHolder;
import initial.Initiator;
import javaTools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.FcdslRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static constants.FieldNames.ID;
import static constants.FieldNames.LAST_HEIGHT;


@WebServlet(name = ApiNames.TokenHoldersByIds, value = "/"+ApiNames.SN_16+"/"+ApiNames.Version2 +"/"+ApiNames.TokenHoldersByIds)
public class TokenHoldersByIds extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT, false, ID, true, null, null);
        doRequest(Initiator.sid, defaultSort, request, response, authType, Initiator.esClient, Initiator.jedisPool);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT, false, ID, true, null, null);
        doRequest(Initiator.sid, defaultSort, request, response, authType, Initiator.esClient, Initiator.jedisPool);
    }

    public static void doRequest(String sid, List<Sort> sort, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) {
        FcReplier replier = new FcReplier(sid, response);

        try (Jedis jedis = jedisPool.getResource()) {

            List<TokenHolder> meetList = FcdslRequestHandler.doRequestForList(sid, IndicesNames.TOKEN_HOLDER, TokenHolder.class, null, null, null, null, sort, request, response, authType, esClient, replier, jedis);
            if (meetList == null) return;

            Map<String, Map<String,Double>> meetMap = new HashMap<>();

            for (TokenHolder tokenHolder : meetList) {
                Map<String,Double> fidBalanceMap = meetMap.get(tokenHolder.getTokenId());
                if(fidBalanceMap==null)fidBalanceMap = new HashMap<>();
                fidBalanceMap.put(tokenHolder.getFid(),tokenHolder.getBalance());
                meetMap.put(tokenHolder.getTokenId(), fidBalanceMap);
            }
            replier.reply0Success(meetMap, jedis, null);
        }
    }
}