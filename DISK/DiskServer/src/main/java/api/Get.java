package api;


import apip.apipData.Fcdsl;
import server.ApipApiNames;
import constants.CodeMessage;
import initial.Initiator;
import server.DiskApiNames;
import utils.FileUtils;
import utils.ObjectUtils;
import utils.http.AuthType;
import fcData.ReplyBody;
import redis.clients.jedis.Jedis;
import server.HttpRequestChecker;

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

@WebServlet(name = DiskApiNames.GET, value = "/"+ ApipApiNames.VERSION_1 +"/"+ DiskApiNames.GET)
public class Get extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ReplyBody replier = new ReplyBody(Initiator.settings);

        AuthType authType = AuthType.FC_SIGN_URL;

        //Check authorization
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            HttpRequestChecker httpRequestChecker = new HttpRequestChecker(Initiator.settings, replier);
            httpRequestChecker.checkRequestHttp(request, response, authType);
            doGetRequest(request, response, replier, httpRequestChecker,jedis);
        }
    }

    private void doGetRequest(HttpServletRequest request, HttpServletResponse response, ReplyBody replier, HttpRequestChecker httpRequestChecker, Jedis jedis) throws IOException {
        String did = request.getParameter(DID);
        if(did==null) {
            replier.replyOtherErrorHttp("Failed to get "+DID+"From the URL.", response);
            return;
        }
        doPostRequest(response,replier,did,jedis);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier = new ReplyBody(Initiator.settings);
        HttpRequestChecker httpRequestChecker;
        AuthType authType = AuthType.FC_SIGN_BODY;
        String did;
        //Check authorization
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            httpRequestChecker = new HttpRequestChecker(Initiator.settings, replier);
            httpRequestChecker.checkRequestHttp(request, response, authType);
            if (httpRequestChecker ==null){
                return;
            }
            Fcdsl fcdsl = httpRequestChecker.getRequestBody().getFcdsl();
            try {
                Map<String, String> paramMap = ObjectUtils.objectToMap(fcdsl.getOther(), String.class, String.class);
                did = paramMap.get(DID);
                if (did == null) {
                    replier.replyHttp(CodeMessage.Code3009DidMissed, response);
                    return;
                }
            }catch (Exception e){
                replier.replyOtherErrorHttp("Failed to get DID.", response);
                return;
            }
//            did = replier.getStringFromBodyJsonData(requestCheckResult.getRequestBody(),DID,jedis);
            doPostRequest(response, replier, did,new Jedis());
        }
    }

    private void doPostRequest(HttpServletResponse response, ReplyBody replier, String did, Jedis jedis) throws IOException {
        if (did == null) return;
        String path = FileUtils.getSubPathForDisk(did);
        File file = new File(STORAGE_DIR+path, did);
        if (!file.exists()) {
            replier.replyHttp(CodeMessage.Code1020OtherError, response);
            return;
        }
        Long balance = replier.updateBalance(DiskApiNames.CHECK, file.length());
        if(balance!=null)
            response.setHeader(BALANCE, String.valueOf(balance));
        response.setContentType("application/octet-stream");
        response.setContentLengthLong(file.length());
        response.setHeader(CODE,"0");
        Files.copy(file.toPath(), response.getOutputStream());
    }

}
