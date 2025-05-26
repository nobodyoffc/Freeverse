package APIP3V1_Cid;

import config.Settings;
import data.fcData.ReplyBody;
import feature.avatar.AvatarMaker;
import initial.Initiator;
import server.ApipApiNames;
import server.HttpRequestChecker;
import utils.http.AuthType;

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

import static constants.Strings.FID;


@WebServlet(name = ApipApiNames.GET_AVATAR, value = "/"+ ApipApiNames.SN_3+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.GET_AVATAR)
public class GetAvatar extends HttpServlet {
    private final Settings settings = Initiator.settings;
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier =new ReplyBody(settings);
        doRequest(request, response, AuthType.FC_SIGN_BODY, replier);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FREE;
        ReplyBody replier = new ReplyBody(settings);
        doRequest(request, response, authType, replier);
    }

    private void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, ReplyBody replier) throws IOException {
        //Check authorization
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) {
            return;
        }

        String fid = request.getParameter(FID);
        if (fid == null) {
            replier.replyOtherErrorHttp("No qualified FID.", response);
            return;
        }

        String avatarPngPath;
        String avatarElementsPath;
        try {
            avatarPngPath = (String) settings.getSettingMap().get(Settings.AVATAR_PNG_PATH);
            avatarElementsPath = (String)settings.getSettingMap().get(Settings.AVATAR_ELEMENTS_PATH);
            AvatarMaker.getAvatars(new String[]{fid}, avatarElementsPath, avatarPngPath);
        }catch (Exception e){
            replier.replyOtherErrorHttp("Failed to get the png.",e.getMessage(), response);
            return;
        }

        response.reset();
        response.setContentType("image/png");
        if(!avatarPngPath.endsWith("/"))avatarPngPath=avatarPngPath+"/";
        File file = new File(avatarPngPath + fid + ".png");
        BufferedImage buffImg = ImageIO.read(new FileInputStream(file));
        ServletOutputStream servletOutputStream = response.getOutputStream();
        ImageIO.write(buffImg, "png", servletOutputStream);
        servletOutputStream.close();
        file.delete();
    }
}