package endpoint;

import clients.NaSaClient.NaSaRpcClient;
import server.ApipApiNames;
import data.fchData.FchChainInfo;
import initial.Initiator;
import utils.http.AuthType;
import server.HttpRequestChecker;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import config.Settings;
import data.feipData.Service;

@WebServlet(name = ApipApiNames.TOTAL_SUPPLY, value = "/"+ ApipApiNames.TOTAL_SUPPLY)
public class TotalSupply extends HttpServlet {
    private final Settings settings = Initiator.settings;
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FREE;
        doRequest(request, response, authType,settings);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request, response, authType,settings);
    }

    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) throws ServletException, IOException {
        NaSaRpcClient naSaRpcClient = (NaSaRpcClient) settings.getClient(Service.ServiceType.NASA_RPC);

        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings);
        httpRequestChecker.checkRequestHttp(request, response, authType);
        FchChainInfo freecashInfo = new FchChainInfo();
        freecashInfo.infoBest(naSaRpcClient);
        response.getWriter().write(freecashInfo.getTotalSupply());
    }
}