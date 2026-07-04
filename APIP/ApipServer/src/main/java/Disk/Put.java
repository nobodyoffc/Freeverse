package Disk;

import config.Settings;
import constants.ApipApiNames;
import constants.CodeMessage;
import data.fcData.DiskItem;
import data.fcData.ReplyBody;
import fapi.components.disk.FapiDiskHandler;
import initial.Initiator;
import server.HttpRequestChecker;
import utils.http.AuthType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;

/**
 * DISK PUT: store a file with an expiration (dataLifeDays), returning its DiskItem.
 *
 * <p>Uses {@code FC_SIGN_URL} auth so the raw octet-stream body is left intact for
 * the servlet to stream to disk. Storage is handled by {@link FapiDiskHandler} via
 * {@link DiskStore} (no {@code DiskManager}).
 */
@WebServlet(name = ApipApiNames.DISK_PUT + "_DISK",
        value = "/" + ApipApiNames.DISK_SN + "/" + ApipApiNames.DISK_PUT + "/" + ApipApiNames.VER_1)
public class Put extends HttpServlet {
    private final Settings settings = Initiator.settings;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        new ReplyBody(settings).replyHttp(CodeMessage.Code1017MethodNotAvailable, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        store(request, response, false);
    }

    static void store(HttpServletRequest request, HttpServletResponse response, boolean permanent) throws IOException {
        Settings settings = Initiator.settings;
        ReplyBody replier = new ReplyBody(settings);

        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        if (!httpRequestChecker.checkRequestHttp(request, response, AuthType.FC_SIGN_URL))
            return;

        FapiDiskHandler diskHandler = DiskStore.handler();
        long dataLifeDays = DiskStore.defaultDataLifeDays();

        DiskItem diskItem;
        try (InputStream inputStream = request.getInputStream()) {
            diskItem = diskHandler.storeFromStream(inputStream, request.getContentLengthLong(), permanent, dataLifeDays);
        } catch (IllegalArgumentException e) {
            replier.replyOtherErrorHttp("File content is required.", response);
            return;
        } catch (Exception e) {
            replier.replyOtherErrorHttp("Failed to store the data: " + e.getMessage(), response);
            return;
        }

        replier.reply0SuccessHttp(diskItem, response);
    }
}
