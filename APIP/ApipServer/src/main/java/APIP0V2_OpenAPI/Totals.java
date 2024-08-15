package APIP0V2_OpenAPI;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import constants.ApiNames;
import fcData.FcReplier;
import initial.Initiator;
import javaTools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.RequestCheckResult;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = ApiNames.Totals, value = "/"+ApiNames.Version2 +"/"+ApiNames.Totals)
public class Totals extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid, request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(Initiator.sid,request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }
    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) throws IOException {
        FcReplier replier = new FcReplier(sid,response);
        //Check authorization
        try (Jedis jedis = jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(sid, request, replier, authType, jedis, false);
            if (requestCheckResult == null) {
                return;
            }

            IndicesResponse result = esClient.cat().indices();
            List<IndicesRecord> indicesRecordList = result.valueBody();

            Map<String, String> allSumMap = new HashMap<>();
            for (IndicesRecord record : indicesRecordList) {
                allSumMap.put(record.index(), record.docsCount());
            }
            replier.setGot((long) allSumMap.size());
            replier.setTotal((long) allSumMap.size());
            replier.reply0Success(allSumMap, jedis, null);
        }
    }
}