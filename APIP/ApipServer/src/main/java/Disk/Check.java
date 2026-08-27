package Disk;

import config.Settings;
import constants.ApipApiNames;
import constants.CodeMessage;
import data.apipData.Fcdsl;
import data.fcData.DiskItem;
import data.fcData.ReplyBody;
import fapi.components.disk.FapiDiskHandler;
import initial.Initiator;
import server.HttpRequestChecker;
import utils.ObjectUtils;
import utils.http.AuthType;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

import static constants.FieldNames.DID;

/**
 * DISK CHECK: return the {@link DiskItem} metadata for a stored file id (did).
 *
 * <p>GET uses {@code FC_SIGN_URL} (did as a URL param); POST uses {@code ENCRYPTED}
 * (did carried in the decrypted FCDSL "other").
 */
@WebServlet(name = ApipApiNames.DISK_CHECK + "_DISK",
        value = "/" + ApipApiNames.DISK_SN + "/" + ApipApiNames.DISK_CHECK + "/" + ApipApiNames.VER_1)
public class Check extends HttpServlet {
    private final Settings settings = Initiator.settings;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        ReplyBody replier = new ReplyBody(settings);
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        if (!httpRequestChecker.checkRequestHttp(request, response, AuthType.FC_SIGN_URL))
            return;
        doRequest(request.getParameter(DID), response, replier);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        ReplyBody replier = new ReplyBody(settings);
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        if (!httpRequestChecker.checkRequestHttp(request, response, AuthType.ENCRYPTED))
            return;
        doRequest(didFromBody(httpRequestChecker), response, replier);
    }

    private void doRequest(String did, HttpServletResponse response, ReplyBody replier) {
        if (did == null || did.isEmpty()) {
            replier.replyHttp(CodeMessage.Code3009DidMissed, response);
            return;
        }

        FapiDiskHandler diskHandler = DiskStore.handler();
        FapiDiskHandler.DiskCheckResult result = diskHandler.check(did);

        if (!result.exists() || result.metadata() == null) {
            replier.replyHttp(CodeMessage.Code1011DataNotFound, response);
            return;
        }
        replier.reply0SuccessHttp(result.metadata(), response);
    }

    private static String didFromBody(HttpRequestChecker httpRequestChecker) {
        try {
            Fcdsl fcdsl = httpRequestChecker.getRequestBody().getFcdsl();
            Map<String, String> paramMap = ObjectUtils.objectToMap(fcdsl.getOther(), String.class, String.class);
            return paramMap == null ? null : paramMap.get(DID);
        } catch (Exception e) {
            return null;
        }
    }
}
