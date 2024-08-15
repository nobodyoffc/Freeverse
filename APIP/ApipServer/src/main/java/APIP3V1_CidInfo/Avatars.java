package APIP3V1_CidInfo;

import avatar.AvatarMaker;
import constants.ApiNames;
import constants.ReplyCodeMessage;
import fcData.FcReplier;
import initial.Initiator;
import javaTools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.RequestCheckResult;
import server.RequestChecker;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static initial.Initiator.avatarElementsPath;
import static initial.Initiator.avatarPngPath;

@WebServlet(name = ApiNames.Avatars, value = "/"+ApiNames.SN_3+"/"+ApiNames.Version2 +"/"+ApiNames.Avatars)
public class Avatars extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid,request,response,authType,Initiator.jedisPool);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(Initiator.sid,request,response,authType,Initiator.jedisPool);
    }
    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, JedisPool jedisPool) throws ServletException, IOException {
        FcReplier replier = new FcReplier(sid,response);
        //Check authorization
        try (Jedis jedis = jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(sid, request, replier, authType, jedis, false);
            if (requestCheckResult == null) {
                return;
            }

            if (requestCheckResult.getRequestBody().getFcdsl().getIds() == null) {
                replier.reply(ReplyCodeMessage.Code1012BadQuery, null, jedis);
                return;
            }
            String[] addrs = requestCheckResult.getRequestBody().getFcdsl().getIds().toArray(new String[0]);
            if (addrs.length == 0) {
                replier.replyOtherError("No qualified FID.",null,jedis);
                return;
            }

            AvatarMaker.getAvatars(addrs, avatarElementsPath, avatarPngPath);

            Base64.Encoder encoder = Base64.getEncoder();
            Map<String, String> addrPngBase64Map = new HashMap<>();
            for (String addr1 : addrs) {
                File file = new File(avatarPngPath + addr1 + ".png");
                FileInputStream fis = new FileInputStream(file);
                String pngStr = encoder.encodeToString(fis.readAllBytes());
                addrPngBase64Map.put(addr1, pngStr);
                file.delete();
                fis.close();
            }
            //response
            replier.setData(addrPngBase64Map);
            replier.setGot((long) addrPngBase64Map.size());
            replier.setTotal((long) addrPngBase64Map.size());

            replier.reply0Success(addrPngBase64Map,jedis, null);
        }
    }
}