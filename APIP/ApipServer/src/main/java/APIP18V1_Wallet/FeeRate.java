package APIP18V1_Wallet;


import handlers.CashManager;
import handlers.Manager;
import server.ApipApiNames;
import data.fcData.ReplyBody;
import initial.Initiator;
import utils.NumberUtils;
import utils.http.AuthType;
import server.HttpRequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import config.Settings;

@WebServlet(name = ApipApiNames.FEE_RATE, value = "/"+ ApipApiNames.SN_18+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.FEE_RATE)
public class FeeRate extends HttpServlet {
    private final Settings settings = Initiator.settings;
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request, response, authType,settings);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request, response, authType,settings);
    }
    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) throws IOException {
        ReplyBody replier = new ReplyBody(settings);
        //Check authorization
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        httpRequestChecker.checkRequestHttp(request, response, authType);
        CashManager cashHandler = (CashManager)settings.getManager(Manager.ManagerType.CASH);
        Double feeRate = cashHandler.getFeeRate();
        if(feeRate==null){
            replier.replyOtherErrorHttp("Calculating fee rate wrong.", response);
            return;
        }
        replier.replySingleDataSuccessHttp(NumberUtils.roundDouble8(feeRate),response);
    }
}
