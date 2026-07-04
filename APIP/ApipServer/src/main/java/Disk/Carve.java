package Disk;

import config.Settings;
import constants.ApipApiNames;
import constants.CodeMessage;
import data.fcData.ReplyBody;
import initial.Initiator;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * DISK CARVE: store a file permanently (no expiration), returning its DiskItem.
 *
 * <p>Shares the streaming/storage logic of {@link Put}; only the {@code permanent}
 * flag differs.
 */
@WebServlet(name = ApipApiNames.DISK_CARVE + "_DISK",
        value = "/" + ApipApiNames.DISK_SN + "/" + ApipApiNames.DISK_CARVE + "/" + ApipApiNames.VER_1)
public class Carve extends HttpServlet {
    private final Settings settings = Initiator.settings;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        new ReplyBody(settings).replyHttp(CodeMessage.Code1017MethodNotAvailable, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Put.store(request, response, true);
    }
}
