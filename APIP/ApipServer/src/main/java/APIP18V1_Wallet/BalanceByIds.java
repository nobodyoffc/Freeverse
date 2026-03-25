package APIP18V1_Wallet;

import config.Settings;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.ApipApiNames;
import constants.CodeMessage;
import data.fcData.ReplyBody;
import data.feipData.ServiceType;
import initial.Initiator;
import utils.http.AuthType;
import server.FcHttpRequestHandler;
import server.HttpRequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;


@WebServlet(name = ApipApiNames.BALANCE_BY_IDS, value = "/"+ ApipApiNames.SN_18+"/"+ ApipApiNames.BALANCE_BY_IDS +"/"+ ApipApiNames.VER_1)
public class BalanceByIds extends HttpServlet {
    private final Settings settings = Initiator.settings;
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request, response, authType, settings);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.SYMKEY_ENCRYPT;
        doRequest(request, response, authType, settings);
    }


    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings)  {
        ReplyBody replier = new ReplyBody(settings);
        //Check authorization
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        httpRequestChecker.checkRequestHttp(request, response, authType);

        if(httpRequestChecker.getRequestBody()==null || httpRequestChecker.getRequestBody().getFcdsl()==null){
            replier.replyHttp(CodeMessage.Code1003BodyMissed,response);
            return;
        }

        if (httpRequestChecker.getRequestBody().getFcdsl().getIds()==null) {
            replier.replyHttp(CodeMessage.Code1015FidMissed,response);
            return;
        }

        List<String> fids = httpRequestChecker.getRequestBody().getFcdsl().getIds();
        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);

        FcHttpRequestHandler fcHttpRequestHandler = new FcHttpRequestHandler(settings);
        Map<String,Long> balanceMap = FcHttpRequestHandler.sumCashValueByOwners(fids, esClient);
        FcHttpRequestHandler.updateAddressBalances(balanceMap, esClient);
        replier.setGot((long) balanceMap.size());
        replier.setGot((long) balanceMap.size());
        replier.reply0SuccessHttp(balanceMap,response);
    }
}
