package APIP3V1_CidInfo;

import avatar.AvatarMaker;
import constants.ApiNames;
import constants.CodeMessage;
import fcData.FcReplierHttp;
import initial.Initiator;
import tools.http.AuthType;
import redis.clients.jedis.Jedis;
import server.RequestCheckResult;
import server.RequestChecker;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static constants.Strings.*;
import static initial.Initiator.*;


@WebServlet(name = ApiNames.GetAvatar, value = "/"+ApiNames.SN_3+"/"+ApiNames.Version1 +"/"+ApiNames.GetAvatar)
public class GetAvatar extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        FcReplierHttp replier =new FcReplierHttp(Initiator.sid,response);
        replier.replyHttp(CodeMessage.Code1017MethodNotAvailable,null,null);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FREE;
        FcReplierHttp replier = new FcReplierHttp(Initiator.sid, response);
        //Check authorization
        try (Jedis jedis = jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(Initiator.sid, request, replier, authType, jedis, false, Initiator.sessionHandler);
            if (requestCheckResult == null) {
                return;
            }

            String fid = request.getParameter(FID);
            if (fid == null) {
                replier.replyOtherErrorHttp("No qualified FID.", null, jedis);
                return;
            }

            AvatarMaker.getAvatars(new String[]{fid}, avatarElementsPath, avatarPngPath);

            response.reset();
            response.setContentType("image/png");
            File file = new File(avatarPngPath + fid + ".png");
            BufferedImage buffImg = ImageIO.read(new FileInputStream(file));
            ServletOutputStream servletOutputStream = response.getOutputStream();
            ImageIO.write(buffImg, "png", servletOutputStream);
            servletOutputStream.close();
            file.delete();
        }
    }
}