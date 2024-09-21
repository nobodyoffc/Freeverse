package APIP3V1_CidInfo;

import apip.apipData.CidInfo;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import constants.*;
import crypto.KeyTools;
import fcData.FcReplierHttp;
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
import java.util.*;

import static apip.apipData.CidInfo.mergeCidInfo;

@WebServlet(name = ApiNames.GetFidCid, value = "/"+ApiNames.SN_3+"/"+ApiNames.Version1 +"/"+ApiNames.GetFidCid)
public class GetFidCid extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        FcReplierHttp replier = new FcReplierHttp(Initiator.sid, response);
        replier.replyHttp(ReplyCodeMessage.Code1017MethodNotAvailable,null,null);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FREE;
        doRequest(Initiator.sid,request, response, authType,Initiator.esClient, Initiator.jedisPool);
    }
    public static void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) throws IOException {
        FcReplierHttp replier = new FcReplierHttp(sid, response);
        //Check authorization
        try (Jedis jedis = jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(sid, request, replier, authType, jedis, false);
            if (requestCheckResult == null) {
                return;
            }

            String idRequested = request.getParameter("id");
            Cid cid = null;
            if (idRequested.contains("_")) {
                SearchResponse<Cid> result = esClient.search(s -> s.index(IndicesNames.CID)
                                .query(q -> q
                                        .term(t -> t.field("usedCids").value(idRequested)))
                        , Cid.class);

                List<Hit<Cid>> hitList = result.hits().hits();
                if (hitList == null || hitList.size() == 0) {
                    replier.replyHttp(ReplyCodeMessage.Code1011DataNotFound, null, jedis);
                    return;
                }

                cid = hitList.get(0).source();
            }
            String fid;
            if (cid != null) fid = cid.getFid();
            else {
                if (!KeyTools.isValidFchAddr(idRequested)) {
                    replier.replyOtherErrorHttp("It's not a valid CID or FID.", null, jedis);
                    return;
                }
                fid = idRequested;
            }
            GetResponse<Address> fidResult = esClient.get(g -> g.index(IndicesNames.ADDRESS).id(fid), Address.class);
            if (!fidResult.found()) {
                replier.replyHttp(ReplyCodeMessage.Code1011DataNotFound, null, jedis);
                return;
            }
            Address address = fidResult.source();

            CidInfo cidInfo = mergeCidInfo(cid, address);
            replier.replySingleDataSuccess(cidInfo, jedis);
        }
    }
}