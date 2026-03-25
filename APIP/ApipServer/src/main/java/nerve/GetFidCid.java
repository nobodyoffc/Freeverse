package nerve;

import data.fchData.Freer;
import config.Settings;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import constants.*;
import data.fcData.ReplyBody;
import data.feipData.ServiceType;
import initial.Initiator;
import utils.http.AuthType;
import server.HttpRequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

import static constants.FieldNames.ID;
import static constants.FieldNames.USED_CIDS;

@WebServlet(ApiNames.NERVE + ApiNames.GetFidCid)
public class GetFidCid extends HttpServlet {
    private final Settings settings;

    public GetFidCid() {
        this.settings = Initiator.settings;
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier = new ReplyBody(settings);
        replier.replyHttp(CodeMessage.Code1017MethodNotAvailable,response);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FREE;
        doRequest(request, response, authType, settings);
    }
    public static void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) throws IOException {
        ReplyBody replier = new ReplyBody(settings);
        //Check authorization
        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) {
            return;
        }

        String idRequested = request.getParameter(ID);
        Freer cid;

        SearchResponse<Freer> result = esClient.search(s -> s.index(IndicesNames.FREER)
                        .query(q -> q
                                .bool(b -> b
                                        .should(sh -> sh
                                                .term(t -> t.field(ID).value(idRequested)))
                                        .should(sh -> sh
                                                .term(t -> t.field(USED_CIDS).value(idRequested)))
                                        .minimumShouldMatch("1")))
                , Freer.class);

        List<Hit<Freer>> hitList = result.hits().hits();
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
