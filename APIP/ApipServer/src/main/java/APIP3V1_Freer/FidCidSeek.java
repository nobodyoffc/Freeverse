package APIP3V1_Freer;

import config.Settings;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import constants.ApipApiNames;
import data.fchData.Freer;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static constants.IndicesNames.FREER;
import static data.apipData.FcQuery.PART;
import static constants.FieldNames.*;

@WebServlet(name = ApipApiNames.FID_CID_SEEK, value = "/"+ ApipApiNames.SN_3+"/"+ ApipApiNames.FID_CID_SEEK +"/"+ ApipApiNames.VER_1)
public class FidCidSeek extends HttpServlet {
    private final Settings settings;

    public FidCidSeek() {
        this.settings = Initiator.settings;
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.ENCRYPTED;
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
        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);
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

        if(value == null || value.isEmpty()){
            replier.setGot(0L);
            replier.setTotal(0L);
            replier.reply0SuccessHttp(addrCidsMap, response);
            return;
        }

        String finalValue = value;

        SearchResponse<Freer> result = esClient.search(s -> s
            .index(FREER)
            .size(20)
            .query(q -> q
                .bool(b -> b
                    .should(sh -> sh
                        .wildcard(w -> w
                            .field(ID)
                            .caseInsensitive(true)
                            .value("*" + finalValue + "*")))
                    .should(sh -> sh
                        .wildcard(w -> w
                            .field(USED_CIDS)
                            .caseInsensitive(true)
                            .value("*" + finalValue + "*")))
                )
            ), Freer.class);

        for (Hit<Freer> hit : result.hits().hits()) {
            Freer freer = hit.source();
            if (freer == null || freer.getId() == null) continue;
            List<String> usedCids = freer.getUsedCids();
            addrCidsMap.put(freer.getId(), usedCids != null ? usedCids : new ArrayList<>());
        }

        replier.setGot((long) addrCidsMap.size());
        replier.setTotal((long) addrCidsMap.size());
        replier.reply0SuccessHttp(addrCidsMap, response);
    }
}
