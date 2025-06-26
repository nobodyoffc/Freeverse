package APIP3V1_Cid;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import config.Settings;
import data.fcData.ReplyBody;
import data.fchData.Cid;
import data.feipData.Service;
import feature.avatar.AvatarMaker;
import initial.Initiator;
import server.ApipApiNames;
import server.FcHttpRequestHandler;
import server.HttpRequestChecker;
import utils.http.AuthType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static constants.FieldNames.AVATAR;
import static constants.FieldNames.CID;

@WebServlet(name = ApipApiNames.CID_BY_IDS, value = "/"+ ApipApiNames.SN_3+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.CID_BY_IDS)
public class CidByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;
    private final Settings settings;
    public CidByIds() {
       this.settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request, response, authType, settings);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
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

        List<String> idList = httpRequestChecker.getRequestBody().getFcdsl().getIds();
        if (idList == null || idList.isEmpty()) {
            replier.replyOtherErrorHttp("The parameter 'ids' is required.", response);
            return;
        }

        // Get CID info using bulk get
        Map<String, String> cidMap = new HashMap<>();
        try {
            MgetResponse<Cid> mgetResponse = esClient.mget(m -> m
                    .index("cid")
                    .ids(idList), Cid.class);

            mgetResponse.docs().forEach(doc -> {
                if (doc.result().found()) {
                    Cid cid = doc.result().source();
                    if(cid!=null && cid.getCid()!=null)
                        cidMap.put(doc.result().id(), cid.getCid());
                    else cidMap.put(doc.result().id(),"");
                }
            });
        } catch (Exception ignore) {
        }


        // Response
        replier.setGot((long) cidMap.size());
        replier.setTotal((long) cidMap.size());
        replier.reply0SuccessHttp(cidMap, response);
    }
}