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

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static constants.FieldNames.DID;
import static constants.UpStrings.BALANCE;
import static constants.UpStrings.CODE;

/**
 * DISK GET: stream the raw file content (application/octet-stream) for a did.
 *
 * <p>The file is streamed directly from disk (never fully loaded into memory).
 * Accessing a file extends its expiration, matching {@code DiskComponent}.
 */
@WebServlet(name = ApipApiNames.DISK_GET + "_DISK",
        value = "/" + ApipApiNames.DISK_SN + "/" + ApipApiNames.DISK_GET + "/" + ApipApiNames.VER_1)
public class Get extends HttpServlet {
    private final Settings settings = Initiator.settings;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier = new ReplyBody(settings);
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        if (!httpRequestChecker.checkRequestHttp(request, response, AuthType.FC_SIGN_URL))
            return;
        stream(request.getParameter(DID), response, replier);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ReplyBody replier = new ReplyBody(settings);
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        if (!httpRequestChecker.checkRequestHttp(request, response, AuthType.ENCRYPTED))
            return;
        stream(didFromBody(httpRequestChecker), response, replier);
    }

    private void stream(String did, HttpServletResponse response, ReplyBody replier) throws IOException {
        if (did == null || did.isEmpty()) {
            replier.replyHttp(CodeMessage.Code3009DidMissed, response);
            return;
        }

        FapiDiskHandler diskHandler = DiskStore.handler();
        Path filePath = diskHandler.getFilePath(did);
        if (filePath == null) {
            replier.replyHttp(CodeMessage.Code1011DataNotFound, response);
            return;
        }

        long fileSize = diskHandler.getFileSize(did);

        // Accessing the data keeps it alive.
        DiskItem metadata = diskHandler.getMetadata(did);
        if (metadata != null)
            diskHandler.extendExpire(metadata, false, DiskStore.defaultDataLifeDays());

        Long balance = replier.updateBalance(ApipApiNames.DISK_GET, fileSize);
        if (balance != null)
            response.setHeader(BALANCE, String.valueOf(balance));

        response.setContentType("application/octet-stream");
        response.setContentLengthLong(fileSize);
        response.setHeader(CODE, "0");
        // Name the download after the did (otherwise browsers fall back to the last URL segment).
        response.setHeader("Content-Disposition", "attachment; filename=\"" + did + "\"");
        Files.copy(filePath, response.getOutputStream());
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
