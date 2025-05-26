package APIP18V1_Wallet;

import config.Settings;
import data.feipData.Service;
import clients.NaSaClient.NaSaRpcClient;
import server.ApipApiNames;
import data.fcData.ReplyBody;
import initial.Initiator;
import server.HttpRequestChecker;
import utils.Hex;
import utils.http.AuthType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

import static constants.FieldNames.RAW_TX;


@WebServlet(name = ApipApiNames.BROADCAST_TX, value = "/"+ ApipApiNames.SN_18+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.BROADCAST_TX)
public class BroadcastTx extends HttpServlet {
    private final Settings settings = Initiator.settings;
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FREE;
        doRequest(request, response, authType, settings);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request, response, authType, settings);
    }

    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) {
        ReplyBody replier = new ReplyBody(settings);
        //Do FCDSL other request
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        Map<String, String> other = httpRequestChecker.checkOtherRequestHttp(request, response, authType);
        if (other == null) return;
        //Do this request
        String rawTx = other.get(RAW_TX);
        NaSaRpcClient naSaRpcClient = (NaSaRpcClient) settings.getClient(Service.ServiceType.NASA_RPC);
        String result = naSaRpcClient.sendRawTransaction(rawTx);
        if(result.startsWith("\""))result=result.substring(1);
        if(result.endsWith("\""))result=result.substring(0,result.length()-1);

        if(!Hex.isHexString(result))
            replier.replyOtherErrorHttp(result, response);
        else replier.replySingleDataSuccessHttp(result,response);
    }
}
