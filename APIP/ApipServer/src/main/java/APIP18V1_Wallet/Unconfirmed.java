package APIP18V1_Wallet;

import data.apipData.UnconfirmedInfo;
import config.Settings;
import handlers.Manager.ManagerType;
import handlers.MempoolManager;
import server.ApipApiNames;
import constants.CodeMessage;
import data.fcData.ReplyBody;
import initial.Initiator;
import utils.http.AuthType;
import server.HttpRequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@WebServlet(name = ApipApiNames.UNCONFIRMED, value = "/"+ ApipApiNames.SN_18+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.UNCONFIRMED)
public class Unconfirmed extends HttpServlet {
    private final Settings settings = Initiator.settings;
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request, response, authType, settings);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request, response, authType, settings);
    }


    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings)  {
        ReplyBody replier = new ReplyBody(settings);
        //Check authorization
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        httpRequestChecker.checkRequestHttp(request, response, authType);
        if(httpRequestChecker.getRequestBody()==null || httpRequestChecker.getRequestBody().getFcdsl()==null){
            replier.replyHttp(CodeMessage.Code1003BodyMissed,null,response);
            return;
        }

        if (httpRequestChecker.getRequestBody().getFcdsl().getIds()==null) {
            replier.replyHttp(CodeMessage.Code1015FidMissed,null,response);
            return;
        }
        MempoolManager mempoolHandler = (MempoolManager) settings.getManager(ManagerType.MEMPOOL);

        Map<String, UnconfirmedInfo> resultMap = mempoolHandler.getUnconfirmedInfo(httpRequestChecker.getRequestBody().getFcdsl().getIds());
        replier.replySingleDataSuccessHttp(resultMap,response);
    }
}
