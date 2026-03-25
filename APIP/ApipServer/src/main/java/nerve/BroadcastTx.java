package nerve;

import constants.ApiNames;
import constants.CodeMessage;
import data.fcData.ReplyBody;
import config.Settings;
import data.feipData.ServiceType;
import initial.Initiator;
import server.HttpRequestChecker;
import utils.Hex;
import utils.http.AuthType;
import clients.NaSaClient.NaSaRpcClient;


import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;



@WebServlet(ApiNames.NERVE + ApiNames.BroadcastTx)
public class BroadcastTx extends HttpServlet {
    private final Settings settings = Initiator.settings;
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FREE;
        String rawTx = request.getParameter("rawTx");
        doRequest(rawTx,request, response, authType, settings);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.SYMKEY_ENCRYPT;
        String rawTx = getRawTx(request);
        doRequest(rawTx, request, response, authType, settings);
    }

    protected void doRequest(String rawTx, HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) throws IOException {
        ReplyBody replier = new ReplyBody(settings);
        //Do FCDSL other request
        NaSaRpcClient naSaRpcClient = (NaSaRpcClient) settings.getClient(ServiceType.NASA_RPC);
        String result = naSaRpcClient.sendRawTransaction(rawTx);
        if(result.startsWith("\""))result=result.substring(1);
        if(result.endsWith("\""))result=result.substring(0,result.length()-1);

        if(Hex.isHexString(result)) {
            replier.setData(result);
            replier.setCodeMessage(CodeMessage.Code0Success);
            response.getWriter().write(replier.toJson());
        }
        else {
            replier.setCodeMessage(CodeMessage.Code1020OtherError);
            replier.setMessage(result);
            response.getWriter().write(replier.toJson());
        }
    }

    private String getRawTx(HttpServletRequest request) {
        try {
            byte[] requestBodyBytes = request.getInputStream().readAllBytes();
            String requestBodyJson = new String(requestBodyBytes);
            Gson gson = new Gson();
            Type mapType = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> dataMap = gson.fromJson(requestBodyJson, mapType);
            return dataMap != null ? dataMap.get("rawTx") : null;
        } catch (IOException e) {
            return null;
        }
    }
}

