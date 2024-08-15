package APIP3V1_CidInfo;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import constants.ApiNames;
import fcData.FcReplier;
import fch.fchData.Address;
import feip.feipData.Cid;
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
import java.util.Map;

import static apip.apipData.FcQuery.PART;
import static constants.FieldNames.*;
import static constants.IndicesNames.ADDRESS;

@WebServlet(name = ApiNames.FidCidSeek, value = "/"+ApiNames.SN_3+"/"+ApiNames.Version2 +"/"+ApiNames.FidCidSeek)
public class FidCidSeek extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid,false,request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FREE;
        doRequest(Initiator.sid,false,request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }
    public static void doRequest(String sid, boolean isForMap, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) throws IOException {
        FcReplier replier = new FcReplier(sid,response);
        //Check authorization
        try (Jedis jedis = jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(sid, request, replier, authType, jedis, false);
            if (requestCheckResult == null) {
                return;
            }


            Map<String, String[]> addrCidsMap = new HashMap<>();
            String value;
            try {
                value = requestCheckResult.getRequestBody().getFcdsl().getQuery().getPart().getValue();
            }catch (Exception ignore){
                value = request.getParameter(PART);
            }

            String finalValue = value;
            SearchResponse<Address> result = esClient.search(s -> s.index(ADDRESS).query(q -> q.wildcard(w -> w.field(FID)
                    .caseInsensitive(true)
                    .value("*" + finalValue + "*"))), Address.class);

            if (result.hits().hits().size() > 0) {
                for (Hit<Address> hit : result.hits().hits()) {
                    Address addr = hit.source();
                    addrCidsMap.put(addr.getFid(),new String[0]);
                }
            }

            SearchResponse<Cid> result1 = esClient.search(s -> s.index(CID).query(q -> q.wildcard(w -> w.field(USED_CIDS)
                    .caseInsensitive(true)
                    .value("*" + finalValue + "*"))), Cid.class);
            if (result1.hits().hits().size() > 0) {
                for (Hit<Cid> hit : result1.hits().hits()) {
                    Cid cid = hit.source();
                    addrCidsMap.put(cid.getFid(), cid.getUsedCids());
                }
            }
            replier.setGot((long) addrCidsMap.size());
            replier.setTotal((long) addrCidsMap.size());
            replier.reply0Success(addrCidsMap, jedis, null);
        }
    }
}
