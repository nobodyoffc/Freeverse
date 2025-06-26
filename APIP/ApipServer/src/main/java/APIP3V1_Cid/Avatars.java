package APIP3V1_Cid;

import config.Settings;
import feature.avatar.AvatarMaker;
import server.ApipApiNames;
import constants.CodeMessage;
import data.fcData.ReplyBody;
import initial.Initiator;
import utils.http.AuthType;
import server.HttpRequestChecker;

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

@WebServlet(name = ApipApiNames.AVATARS, value = "/"+ ApipApiNames.SN_3+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.AVATARS)
public class Avatars extends HttpServlet {
    private final Settings settings;

    public Avatars() {
        this.settings = Initiator.settings;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request,response,authType, settings);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request,response,authType, settings);
    }
    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) throws ServletException, IOException {
        ReplyBody replier = new ReplyBody(settings);
        //Check authorization
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) {
            return;
        }

        if (httpRequestChecker.getRequestBody().getFcdsl().getIds() == null) {
            replier.replyHttp(CodeMessage.Code1012BadQuery, response);
            return;
        }
        String[] addrs = httpRequestChecker.getRequestBody().getFcdsl().getIds().toArray(new String[0]);
        if (addrs.length == 0) {
            replier.replyOtherErrorHttp("No qualified FID.", response);
            return;
        }

        String avatarPngPath;
        String avatarElementsPath;
        try {
            avatarPngPath = (String) settings.getSettingMap().get(Settings.AVATAR_PNG_PATH);
            avatarElementsPath = (String)settings.getSettingMap().get(Settings.AVATAR_ELEMENTS_PATH);
            AvatarMaker.getAvatars(addrs, avatarElementsPath, avatarPngPath);
        }catch (Exception e){
            replier.replyOtherErrorHttp("Failed to get the png.",e.getMessage(), response);
            return;
        }
        Base64.Encoder encoder = Base64.getEncoder();
        Map<String, String> addrPngBase64Map = new HashMap<>();
        if(!avatarPngPath.endsWith("/"))avatarPngPath=avatarPngPath+"/";

        for (String addr1 : addrs) {
            if(addr1.length()!=34)continue;
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
        replier.reply0SuccessHttp(addrPngBase64Map, response);

    }
}