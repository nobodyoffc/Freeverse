package api;


import apip.apipData.Fcdsl;
import constants.ApiNames;
import constants.CodeMessage;
import initial.Initiator;
import tools.FileTools;
import tools.ObjectTools;
import tools.http.AuthType;
import fcData.FcReplierHttp;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import server.RequestCheckResult;
import server.RequestChecker;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import static constants.FieldNames.DID;
import static constants.UpStrings.BALANCE;
import static constants.UpStrings.CODE;
import static startManager.StartDiskManager.STORAGE_DIR;

@WebServlet(name = ApiNames.Get, value = "/"+ApiNames.Version1 +"/"+ ApiNames.Get)
public class Get extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        FcReplierHttp replier = new FcReplierHttp(Initiator.sid,response);

        AuthType authType = AuthType.FC_SIGN_URL;

        //Check authorization
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(Initiator.sid, request, replier, authType, jedis, false, Initiator.sessionHandler);
            if (requestCheckResult==null){
                return;
            }
            doGetRequest(request, response, replier,requestCheckResult,jedis);
        }
    }

    @Nullable
    private void doGetRequest(HttpServletRequest request, HttpServletResponse response, FcReplierHttp replier, RequestCheckResult requestCheckResult, Jedis jedis) throws IOException {
        String did = replier.getStringFromUrl(request, DID, jedis);
        doPostRequest(response,replier,did,jedis);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        FcReplierHttp replier = new FcReplierHttp(Initiator.sid,response);
        RequestCheckResult requestCheckResult;
        AuthType authType = AuthType.FC_SIGN_BODY;
        String did;
        //Check authorization
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            requestCheckResult = RequestChecker.checkRequest(Initiator.sid, request, replier, authType, jedis, false, Initiator.sessionHandler);
            if (requestCheckResult==null){
                return;
            }
            Fcdsl fcdsl = requestCheckResult.getRequestBody().getFcdsl();
            try {
                Map<String, String> paramMap = ObjectTools.objectToMap(fcdsl.getOther(), String.class, String.class);
                did = paramMap.get(DID);
                if (did == null) {
                    replier.replyHttp(CodeMessage.Code3009DidMissed, null, jedis);
                    return;
                }
            }catch (Exception e){
                replier.replyOtherErrorHttp("Failed to get DID.",null,jedis);
                return;
            }
//            did = replier.getStringFromBodyJsonData(requestCheckResult.getRequestBody(),DID,jedis);
            doPostRequest(response, replier, did,new Jedis());
        }
    }

    private void doPostRequest(HttpServletResponse response, FcReplierHttp replier, String did, Jedis jedis) throws IOException {
        if (did == null) return;
        String path = FileTools.getSubPathForDisk(did);
        File file = new File(STORAGE_DIR+path, did);
        if (!file.exists()) {
            replier.replyHttp(CodeMessage.Code1020OtherError, null, jedis);
            return;
        }
        Long balance = replier.updateBalance(Initiator.sid, ApiNames.Check, file.length(),jedis, null);
        if(balance!=null)
            response.setHeader(BALANCE, String.valueOf(balance));
        response.setContentType("application/octet-stream");
        response.setContentLengthLong(file.length());
        response.setHeader(CODE,"0");
        Files.copy(file.toPath(), response.getOutputStream());
    }

}
