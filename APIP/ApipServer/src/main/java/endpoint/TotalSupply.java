package endpoint;

import clients.NaSaClient.NaSaRpcClient;
import constants.ApipApiNames;
import data.fcData.ReplyBody;
import data.fchData.FchChainInfo;
import data.feipData.ServiceType;
import initial.Initiator;
import utils.http.AuthType;
import server.HttpRequestChecker;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import config.Settings;

@WebServlet(name = ApipApiNames.TOTAL_SUPPLY, value = "/"+ ApipApiNames.TOTAL_SUPPLY)
public class TotalSupply extends HttpServlet {
    private final Settings settings = Initiator.settings;
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FREE;
        doRequest(request, response, authType,settings);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.ENCRYPTED;
        doRequest(request, response, authType,settings);
    }

    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) throws ServletException, IOException {
        NaSaRpcClient naSaRpcClient = (NaSaRpcClient) settings.getClient(ServiceType.NASA_RPC);

        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings);
        httpRequestChecker.checkRequestHttp(request, response, authType);
        ReplyBody replier = httpRequestChecker.getReplyBody();

        FchChainInfo freecashInfo = new FchChainInfo();
        freecashInfo.infoBest(naSaRpcClient);
        replier.replyHttp(freecashInfo.getTotalSupply(),response);
    }
}