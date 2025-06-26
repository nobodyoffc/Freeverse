package APIP3V1_Cid;

import config.Settings;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import data.fchData.Cid;
import data.feipData.Service;
import server.ApipApiNames;
import data.fcData.ReplyBody;
import initial.Initiator;
import utils.http.AuthType;
import server.HttpRequestChecker;
import feature.avatar.AvatarMaker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import static constants.FieldNames.AVATAR;
import static constants.FieldNames.CID;

@WebServlet(name = ApipApiNames.CID_AVATAR_BY_IDS, value = "/"+ ApipApiNames.SN_3+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.CID_AVATAR_BY_IDS)
public class CidAvatarByIds extends HttpServlet {
    private final Settings settings;

    public CidAvatarByIds() {
        this.settings = Initiator.settings;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request, response, authType, settings);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request, response, authType, settings);
    }

    public static void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) throws IOException {
        ReplyBody replier = new ReplyBody(settings);
        //Check authorization
        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) {
            return;
        }

        List<String> idList = httpRequestChecker.getRequestBody().getFcdsl().getIds();
        if (idList == null || idList.isEmpty()) {
            replier.replyOtherErrorHttp("The parameter 'ids' is required.", response);
            return;
        }

        // Get CID info using bulk get
        Map<String, Cid> cidMap = new HashMap<>();
        try {
            MgetResponse<Cid> mgetResponse = esClient.mget(m -> m
                .index("cid")
                .ids(idList), Cid.class);
            
            mgetResponse.docs().forEach(doc -> {
                if (doc.result().found()) {
                    cidMap.put(doc.result().id(), doc.result().source());
                }
            });
        } catch (Exception ignore) {
        }

        Map<String,Map<String,String>> fidCidAvatarMap = new HashMap<>();

        // Get avatar paths
        String avatarPngPath;
        String avatarElementsPath;
        try {
            avatarPngPath = (String) settings.getSettingMap().get(Settings.AVATAR_PNG_PATH);
            avatarElementsPath = (String) settings.getSettingMap().get(Settings.AVATAR_ELEMENTS_PATH);
            AvatarMaker.getAvatars(idList.toArray(new String[0]), avatarElementsPath, avatarPngPath);
        } catch (Exception e) {
            replier.replyOtherErrorHttp("Failed to get the png.", e.getMessage(), response);
            return;
        }

        // Convert avatars to base64 and create final map
        Base64.Encoder encoder = Base64.getEncoder();
        if (!avatarPngPath.endsWith("/")) avatarPngPath = avatarPngPath + "/";

        for (String id : idList) {
            Map<String,String > cidAvatar = new HashMap<>();
            cidAvatar.put(CID,cidMap.get(id).getCid());
            File file = new File(avatarPngPath + id + ".png");
            if (file.exists()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    String pngStr = encoder.encodeToString(fis.readAllBytes());
                    cidAvatar.put(AVATAR,pngStr);
                    fidCidAvatarMap.put(id,cidAvatar);
                }
                file.delete();
            }
        }

        // Response
        replier.setGot((long) fidCidAvatarMap.size());
        replier.setTotal((long) fidCidAvatarMap.size());
        replier.reply0SuccessHttp(fidCidAvatarMap, response);
    }
} 