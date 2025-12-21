package APIP3V1_Freer;

import config.Settings;
import constants.ApipApiNames;
import constants.CodeMessage;
import constants.IndicesNames;
import data.apipData.Fcdsl;
import data.apipData.RequestBody;
import data.fcData.ReplyBody;
import data.fchData.Nobody;
import initial.Initiator;
import server.FcHttpRequestHandler;
import server.HttpRequestChecker;
import utils.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static constants.FieldNames.ID;

@WebServlet(name = ApipApiNames.CHECK_NOBODIES, value = "/"+ ApipApiNames.SN_3+"/"+ ApipApiNames.CHECK_NOBODIES +"/"+ ApipApiNames.VER_1)
public class CheckNobodies extends HttpServlet {
    private final Settings settings;
    public CheckNobodies() {
        this.settings = Initiator.settings;
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.SYMKEY_ENCRYPT;
        doRequest(request, response, authType);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request, response, authType);
    }

    private void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType) {
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings);
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) {
            return;
        }
        ReplyBody replyBody = httpRequestChecker.getReplyBody();

        FcHttpRequestHandler fcHttpRequestHandler = new FcHttpRequestHandler(replyBody,settings);
        Map<String, Nobody> meetMap = fcHttpRequestHandler.doRequestForMap(IndicesNames.NOBODY, Nobody.class, ID, response);
        if (meetMap == null) {
            replyBody.replyOtherErrorHttp("Failed to get nobodies", response);
            return;
        }

        RequestBody requestBody = httpRequestChecker.getRequestBody();
        Fcdsl fcdsl = requestBody.getFcdsl();
        if(fcdsl==null || fcdsl.getIds()==null){
            replyBody.replyHttp(CodeMessage.Code1012BadQuery, response);
            return;
        }
        List<String> ids = fcdsl.getIds();

        Map<String,Boolean> fidMap = new HashMap<>();

        for(String id : ids){
            if(meetMap.get(id)!=null && meetMap.get(id).getPrikey()!=null)
                fidMap.put(id,Boolean.TRUE);
            else fidMap.put(id,Boolean.FALSE);
        }

        replyBody.reply0SuccessHttp(fidMap, response);
    }
}
