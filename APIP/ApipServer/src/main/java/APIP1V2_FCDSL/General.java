package APIP1V2_FCDSL;

import apip.apipData.Sort;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.ApiNames;
import fcData.FcReplier;
import initial.Initiator;
import javaTools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.FcdslRequestHandler;
import server.RequestCheckResult;
import server.RequestChecker;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@WebServlet(name = ApiNames.General, value = "/"+ApiNames.SN_1+"/"+ApiNames.Version2 +"/"+ApiNames.General)
public class General extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid, request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(Initiator.sid,request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }

    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) {
        FcReplier replier = new FcReplier(sid,response);

        try (Jedis jedis = jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(sid, request, replier, authType, jedis, false);
            if (requestCheckResult == null) {
                return;
            }
            List<Object> meetList;
            FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(requestCheckResult.getRequestBody(), response, replier, esClient);
            ArrayList<Sort> defaultSortList = null;
            String index = requestCheckResult.getRequestBody().getFcdsl().getIndex();
            meetList = fcdslRequestHandler.doRequest(index, defaultSortList, Object.class, jedis);

            if ( meetList== null) return;
            replier.setGot((long) meetList.size());
            replier.reply0Success(meetList, jedis, null);
        }

    }
}

