package api;


import data.apipData.Fcdsl;
import config.Settings;
import handlers.DiskManager;
import handlers.Manager;
import server.ApipApiNames;
import constants.CodeMessage;
import initial.Initiator;
import server.DiskApiNames;
import utils.ObjectUtils;
import utils.http.AuthType;
import data.fcData.ReplyBody;
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

@WebServlet(name = DiskApiNames.GET, value = "/"+ ApipApiNames.VERSION_1 +"/"+ DiskApiNames.GET)
public class Get extends HttpServlet {

    private final Settings settings = Initiator.settings;
    private final DiskManager diskHandler = (DiskManager) Initiator.settings.getManager(Manager.ManagerType.DISK);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ReplyBody replier = new ReplyBody(settings);

        AuthType authType = AuthType.FC_SIGN_URL;

        //Check authorization
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        httpRequestChecker.checkRequestHttp(request, response, authType);
        doGetRequest(request, response, replier);
    }

    private void doGetRequest(HttpServletRequest request, HttpServletResponse response, ReplyBody replier) throws IOException {
        String did = request.getParameter(DID);
        if(did==null) {
            replier.replyOtherErrorHttp("Failed to get "+DID+"From the URL.", response);
            return;
        }
        doPostRequest(response,replier,did);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier = new ReplyBody(settings);
        HttpRequestChecker httpRequestChecker;
        AuthType authType = AuthType.FC_SIGN_BODY;
        String did;
        //Check authorization
        httpRequestChecker = new HttpRequestChecker(settings, replier);
        httpRequestChecker.checkRequestHttp(request, response, authType);
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
        doPostRequest(response, replier, did);
    }

    private void doPostRequest(HttpServletResponse response, ReplyBody replier, String did) throws IOException {
        if (did == null) return;

        DiskManager diskManager = (DiskManager) settings.getManager(Manager.ManagerType.DISK);

        String path = diskManager.getDataPath(did);
        File file = new File(path);
        if (!file.exists()) {
            replier.replyHttp(CodeMessage.Code1011DataNotFound, response);
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
