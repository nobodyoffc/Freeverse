package APIP18V1_Wallet;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import nasa.NaSaRpcClient;
import server.ApipApiNames;
import fcData.ReplyBody;
import fch.Wallet;
import initial.Initiator;
import tools.NumberTools;
import tools.http.AuthType;
import server.HttpRequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import appTools.Settings;
import feip.feipData.Service;

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
        NaSaRpcClient naSaRpcClient = (NaSaRpcClient) settings.getClient(Service.ServiceType.NASA_RPC);
        ElasticsearchClient esClient = (ElasticsearchClient)settings.getClient(Service.ServiceType.ES);

        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        httpRequestChecker.checkRequestHttp(request, response, authType);
        Double feeRate = new Wallet(null,esClient,naSaRpcClient).getFeeRate();
        if(feeRate==null){
            replier.replyOtherErrorHttp("Calculating fee rate wrong.", response);
            return;
        }
        replier.replySingleDataSuccessHttp(NumberTools.roundDouble8(feeRate),response);
    }
}
