package endpoint;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import constants.ApiNames;
import constants.IndicesNames;
import fcData.FcReplier;
import fch.ParseTools;
import fch.fchData.Address;
import initial.Initiator;
import javaTools.JsonTools;
import javaTools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.RequestCheckResult;
import server.RequestChecker;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static constants.Strings.*;


@WebServlet(name = ApiNames.Richlist, value = "/"+ApiNames.Richlist)
public class Richlist extends HttpServlet {
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FREE;
        doRequest(Initiator.sid,request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid,request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }

    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) throws ServletException, IOException {
        FcReplier replier = new FcReplier(sid,response);
        //Check authorization
        try (Jedis jedis = jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(sid, request, replier, authType, jedis, false);
            if (requestCheckResult == null) {
                return;
            }
            SearchResponse<Address> result = Initiator.esClient.search(s -> s.index(IndicesNames.ADDRESS).size(100).sort(so -> so.field(f -> f.field(BALANCE).order(SortOrder.Desc))), Address.class);
            if(result==null||result.hits()==null||result.hits().hits()==null){
                response.getWriter().write("Failed to get data.");
                return;
            }
            Map<String,Double> richMap = new LinkedHashMap<>();
            for(Hit<Address> hit : result.hits().hits()){
                Address address = hit.source();
                if(address==null)continue;
                richMap.put(address.getFid(), ParseTools.satoshiToCoin(address.getBalance()));
            }
            if(richMap.isEmpty()) {
                response.getWriter().write("Failed to get data.");
                return;
            }
            response.getWriter().write(JsonTools.toNiceJson(richMap));
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}