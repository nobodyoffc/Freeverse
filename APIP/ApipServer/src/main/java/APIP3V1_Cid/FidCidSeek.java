package APIP3V1_Cid;

import appTools.Settings;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import fch.fchData.Cid;
import feip.feipData.Service;
import server.ApipApiNames;
import fcData.ReplyBody;
import initial.Initiator;
import utils.http.AuthType;
import server.HttpRequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static apip.apipData.FcQuery.PART;
import static constants.FieldNames.*;

@WebServlet(name = ApipApiNames.FID_CID_SEEK, value = "/"+ ApipApiNames.SN_3+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.FID_CID_SEEK)
public class FidCidSeek extends HttpServlet {
    private final Settings settings;

    public FidCidSeek() {
        this.settings = Initiator.settings;
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request, response, authType, settings);
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


        Map<String, List<String>> addrCidsMap = new HashMap<>();
        String value;
        try {
            value = httpRequestChecker.getRequestBody().getFcdsl().getQuery().getPart().getValue();
        }catch (Exception ignore){
            value = request.getParameter(PART);
        }

        String finalValue = value;
        SearchResponse<Cid> result = esClient.search(s -> s.index(CID).query(q -> q.wildcard(w -> w.field(ID)
                .caseInsensitive(true)
                .value("*" + finalValue + "*"))), Cid.class);

        if (result.hits().hits().size() > 0) {
            for (Hit<Cid> hit : result.hits().hits()) {
                Cid addr = hit.source();
                if(addr==null) continue;
                addrCidsMap.put(addr.getId(),new ArrayList<>());
            }
        }

        SearchResponse<Cid> result1 = esClient.search(s -> s
            .index(CID)
            .query(q -> q
                .bool(b -> b
                    .should(sh -> sh
                        .wildcard(w -> w
                            .field(USED_CIDS)
                            .caseInsensitive(true)
                            .value("*" + finalValue + "*")))
                    .should(sh -> sh
                        .wildcard(w -> w
                            .field(ID)
                            .caseInsensitive(true)
                            .value("*" + finalValue + "*")))
                )
            ), Cid.class);
        if (result1.hits().hits().size() > 0) {
            for (Hit<Cid> hit : result1.hits().hits()) {
                Cid cid = hit.source();
                if(cid==null) continue;
                addrCidsMap.put(cid.getId(), cid.getUsedCids());
            }
        }
        replier.setGot((long) addrCidsMap.size());
        replier.setTotal((long) addrCidsMap.size());
        replier.reply0SuccessHttp(addrCidsMap, response);
    }
}
