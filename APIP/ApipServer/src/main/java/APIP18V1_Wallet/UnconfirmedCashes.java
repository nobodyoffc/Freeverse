package APIP18V1_Wallet;

import server.ApipApiNames;
import fcData.ReplyBody;
import fch.fchData.Cash;
import initial.Initiator;
import tools.http.AuthType;
import server.HttpRequestChecker;
import handlers.MempoolHandler;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import appTools.Settings;
import handlers.Handler.HandlerType;

@WebServlet(name = ApipApiNames.UNCONFIRMED_CASHES, value = "/"+ ApipApiNames.SN_18+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.UNCONFIRMED_CASHES)
public class UnconfirmedCashes extends HttpServlet {
    private final Settings settings = Initiator.settings;
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request, response, authType,settings);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request, response, authType,settings);
    }


    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings)  {
        ReplyBody replier = new ReplyBody(settings);

        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        httpRequestChecker.checkRequestHttp(request, response, authType);
        List<String> fidList = null;
        if(httpRequestChecker.getRequestBody()!=null && httpRequestChecker.getRequestBody().getFcdsl()!=null && httpRequestChecker.getRequestBody().getFcdsl().getIds()!=null){
            fidList = httpRequestChecker.getRequestBody().getFcdsl().getIds();
        }
        Map<String,List<Cash>> meetList = new HashMap<>();

        MempoolHandler mempoolHandler = (MempoolHandler) settings.getHandler(HandlerType.MEMPOOL);
        meetList = mempoolHandler.checkUnconfirmedCash(fidList);
        
        replier.replySingleDataSuccessHttp(meetList,response);
    }
}
