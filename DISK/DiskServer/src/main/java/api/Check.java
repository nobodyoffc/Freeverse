package api;

import appTools.Settings;
import feip.feipData.Service;
import redis.clients.jedis.JedisPool;
import server.ApipApiNames;
import initial.Initiator;
import server.DiskApiNames;
import utils.FileUtils;
import utils.Hex;
import utils.http.AuthType;
import fcData.ReplyBody;
import redis.clients.jedis.Jedis;
import server.HttpRequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.FieldNames.DID;
import static utils.FileUtils.getSubPathForDisk;
import static startManager.StartDiskManager.diskDir;

@WebServlet(name = DiskApiNames.CHECK, value = "/"+ ApipApiNames.VERSION_1 +"/"+ DiskApiNames.CHECK)
public class Check extends HttpServlet {

    private final Settings settings = Initiator.settings;
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier = new ReplyBody(settings);

        AuthType authType = AuthType.FC_SIGN_URL;
        //Check authorization
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        httpRequestChecker.checkRequestHttp(request, response, authType);

        //Do request
        doRequest(request, response, replier);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_URL;

        ReplyBody replier = new ReplyBody(settings);
        //Check authorization
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        httpRequestChecker.checkRequestHttp(request, response, authType);
        //Do request
        doRequest(request, response, replier);
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
        String path = diskDir + subDir;
        Boolean isFileExists = Boolean.TRUE.equals(FileUtils.checkFileOfDisk(path, did));
        replier.reply0SuccessHttp(isFileExists,response);
    }

}
