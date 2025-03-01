package APIP3V1_CidInfo;

import fch.fchData.Cid;
import appTools.Settings;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import constants.*;
import fcData.ReplyBody;
import feip.feipData.Service;
import initial.Initiator;
import server.ApipApiNames;
import tools.http.AuthType;
import server.HttpRequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

import static constants.FieldNames.ID;
import static constants.FieldNames.USED_CIDS;


@WebServlet(name = ApipApiNames.GET_FID_CID, value = "/"+ ApipApiNames.SN_3+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.GET_FID_CID)
public class GetFidCid extends HttpServlet {
    private final Settings settings;

    public GetFidCid() {
        this.settings = Initiator.settings;
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier = new ReplyBody(settings);
        replier.replyHttp(CodeMessage.Code1017MethodNotAvailable,null);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FREE;
        doRequest(request, response, authType, settings);
    }
    public static void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) throws IOException {
        ReplyBody replier = new ReplyBody(settings);
        //Check authorization
        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) {
            return;
        }

        String idRequested = request.getParameter(ID);
        Cid cid;

        SearchResponse<Cid> result = esClient.search(s -> s.index(IndicesNames.CID)
                        .query(q -> q
                                .bool(b -> b
                                        .should(sh -> sh
                                                .term(t -> t.field(ID).value(idRequested)))
                                        .should(sh -> sh
                                                .term(t -> t.field(USED_CIDS).value(idRequested)))
                                        .minimumShouldMatch("1")))
                , Cid.class);

        List<Hit<Cid>> hitList = result.hits().hits();
        if (hitList == null || hitList.size() == 0) {
            replier.replyHttp(CodeMessage.Code1011DataNotFound, response);
            return;
        }else if(hitList.size() !=1){
            replier.replyOtherErrorHttp("Got more than 1. Please search the full FID or CID.", response);
            return;
        }
        cid = hitList.get(0).source();
        replier.replySingleDataSuccessHttp(cid, response);
    }
}