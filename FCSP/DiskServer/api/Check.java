package api;

import constants.ApiNames;
import initial.Initiator;
import javaTools.FileTools;
import javaTools.http.AuthType;
import fcData.FcReplier;
import redis.clients.jedis.Jedis;
import server.RequestCheckResult;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.FieldNames.DID;
import static javaTools.FileTools.getSubPathForDisk;
import static startManager.StartDiskManager.STORAGE_DIR;

@WebServlet(name = ApiNames.Check, value = "/"+ApiNames.Version1 +"/"+ApiNames.Check)
public class Check extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        FcReplier replier = new FcReplier(Initiator.sid,response);

        AuthType authType = AuthType.FC_SIGN_URL;

        //Check authorization
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(Initiator.sid, request, replier, authType, jedis, false);

            if (requestCheckResult==null){
                return;
            }

        //Do request
            doRequest(request, response, replier, requestCheckResult,jedis);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;

        FcReplier replier = new FcReplier(Initiator.sid,response);

        //Check authorization
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(Initiator.sid, request, replier, authType, jedis, false);
            if (requestCheckResult==null){

                return;
            }
        //Do request
            doRequest(request, response,  replier, requestCheckResult,jedis);
        }
    }

    private void doRequest(HttpServletRequest request, HttpServletResponse response, FcReplier replier, RequestCheckResult requestCheckResult, Jedis jedis) {
        String did = replier.getStringFromUrl(request, DID,jedis);
        if (did == null) return;
        String subDir = getSubPathForDisk(did);
        String path = STORAGE_DIR+subDir;
        Boolean isFileExists = Boolean.TRUE.equals(FileTools.checkFileOfFreeDisk(path, did));
        replier.reply0Success(isFileExists, jedis, null);
    }
}