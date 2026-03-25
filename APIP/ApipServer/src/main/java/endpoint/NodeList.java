package endpoint;

import clients.NaSaClient.NaSaRpcClient;
import config.Settings;
import constants.ApipApiNames;
import data.fcData.ReplyBody;
import data.feipData.ServiceType;
import initial.Initiator;
import utils.JsonUtils;
import utils.http.AuthType;
import server.HttpRequestChecker;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = ApipApiNames.NODE_LIST, value = "/" + ApipApiNames.NODE_LIST)
public class NodeList extends HttpServlet {
    private final Settings settings = Initiator.settings;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FREE;
        doRequest(request, response, authType, settings);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.SYMKEY_ENCRYPT;
        doRequest(request, response, authType, settings);
    }

    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) throws ServletException, IOException {
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings);
        httpRequestChecker.checkRequestHttp(request, response, authType);
        ReplyBody replier = httpRequestChecker.getReplyBody();

        NaSaRpcClient naSaRpcClient = (NaSaRpcClient) settings.getClient(ServiceType.NASA_RPC);
        NaSaRpcClient.PeerInfo[] peers = naSaRpcClient.getPeerInfo();

        if (peers == null) {
            replier.replyHttp("Failed to get peer info from node.", response);
            return;
        }

        replier.replyHttp(JsonUtils.toNiceJson(peers), response);
    }
}
