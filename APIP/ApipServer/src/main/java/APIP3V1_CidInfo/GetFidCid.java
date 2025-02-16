package APIP3V1_CidInfo;

import apip.apipData.CidInfo;
import appTools.Settings;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import constants.*;
import crypto.KeyTools;
import fcData.ReplyBody;
import fch.fchData.Address;
import feip.feipData.Cid;
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

import static apip.apipData.CidInfo.mergeCidInfo;

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

        String idRequested = request.getParameter("id");
        Cid cid = null;
        if (idRequested.contains("_")) {
            SearchResponse<Cid> result = esClient.search(s -> s.index(IndicesNames.CID)
                            .query(q -> q
                                    .term(t -> t.field("usedCids").value(idRequested)))
                    , Cid.class);

            List<Hit<Cid>> hitList = result.hits().hits();
            if (hitList == null || hitList.size() == 0) {
                replier.replyHttp(CodeMessage.Code1011DataNotFound, null);
                return;
            }

            cid = hitList.get(0).source();
        }
        String fid;
        if (cid != null) fid = cid.getFid();
        else {
            if (!KeyTools.isValidFchAddr(idRequested)) {
                replier.replyOtherErrorHttp("It's not a valid CID or FID.", response);
                return;
            }
            fid = idRequested;
        }
        GetResponse<Address> fidResult = esClient.get(g -> g.index(IndicesNames.ADDRESS).id(fid), Address.class);
        if (!fidResult.found()) {
            replier.replyHttp(CodeMessage.Code1011DataNotFound, null);
            return;
        }
        Address address = fidResult.source();

        if(cid==null){
            SearchResponse<Cid> result1 = esClient.search(s -> s
            .index(IndicesNames.CID)
            .query(q -> q.term(t -> t.field(FieldNames.FID).value(fid))), Cid.class);
            if(result1.hits().hits().size()>0){
                cid = result1.hits().hits().get(0).source();
            }
        }

        CidInfo cidInfo = mergeCidInfo(cid, address);
        replier.replySingleDataSuccessHttp(cidInfo, response);
    }
}