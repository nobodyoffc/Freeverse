package api;

import server.ApipApiNames;
import initial.Initiator;
import server.DiskApiNames;
import tools.FileTools;
import tools.Hex;
import tools.http.AuthType;
import fcData.ReplyBody;
import redis.clients.jedis.Jedis;
import server.HttpRequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.FieldNames.DID;
import static tools.FileTools.getSubPathForDisk;
import static startManager.StartDiskManager.STORAGE_DIR;

@WebServlet(name = DiskApiNames.CHECK, value = "/"+ ApipApiNames.VERSION_1 +"/"+ DiskApiNames.CHECK)
public class Check extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier = new ReplyBody(Initiator.settings);

        AuthType authType = AuthType.FC_SIGN_URL;

        //Check authorization
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            HttpRequestChecker httpRequestChecker = new HttpRequestChecker(Initiator.settings, replier);
            httpRequestChecker.checkRequestHttp(request, response, authType);
            if (httpRequestChecker ==null){
                return;
            }

        //Do request
            doRequest(request, response, replier);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;

        ReplyBody replier = new ReplyBody(Initiator.settings);

        //Check authorization
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            HttpRequestChecker httpRequestChecker = new HttpRequestChecker(Initiator.settings, replier);
            httpRequestChecker.checkRequestHttp(request, response, authType);
            if (httpRequestChecker ==null){
                return;
            }
        //Do request
            doRequest(request, response, replier);
        }
    }

    private void doRequest(HttpServletRequest request, HttpServletResponse response, ReplyBody replier) {
        String did = request.getParameter(DID);
        if (did == null){
            replier.replyOtherErrorHttp("Failed to get "+DID+"From the URL.", response);
            return;
        }

        if(!Hex.isHex32(did)) {
            replier.replyOtherErrorHttp("It is not a hex string of a 32 byte array.", did, response);
            return;
        }
        String subDir = getSubPathForDisk(did);
        String path = STORAGE_DIR + subDir;
        Boolean isFileExists = Boolean.TRUE.equals(FileTools.checkFileOfFreeDisk(path, did));
        replier.reply0SuccessHttp(isFileExists,response);
    }

}
