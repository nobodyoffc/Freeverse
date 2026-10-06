package Disk;

import config.Settings;
import constants.ApipApiNames;
import constants.CodeMessage;
import data.apipData.Fcdsl;
import data.fcData.DiskItem;
import data.fcData.ReplyBody;
import db.fcdsl.FcdslException;
import db.fcdsl.FcdslProjection;
import db.fcdsl.FcdslQuery;
import db.fcdsl.FcdslResult;
import fapi.components.disk.DiskMetaStore;
import initial.Initiator;
import server.HttpRequestChecker;
import utils.http.AuthType;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * DISK LIST: query stored {@link DiskItem} metadata with an FCDSL.
 *
 * <p>GET uses {@code FC_SIGN_URL}, POST uses {@code ENCRYPTED}. Defaults to sorting
 * by "since" (descending) when the request supplies no sort; the id is always the last
 * sort key, so {@code last} pages without skipping items that share a since.
 */
@WebServlet(name = ApipApiNames.DISK_LIST + "_DISK",
        value = "/" + ApipApiNames.DISK_SN + "/" + ApipApiNames.DISK_LIST + "/" + ApipApiNames.VER_1)
public class List extends HttpServlet {
    private final Settings settings = Initiator.settings;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        doRequest(request, response, AuthType.FC_SIGN_URL);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        doRequest(request, response, AuthType.ENCRYPTED);
    }

    private void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType) {
        ReplyBody replier = new ReplyBody(settings);
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        if (!httpRequestChecker.checkRequestHttp(request, response, authType))
            return;

        Fcdsl fcdsl = httpRequestChecker.getRequestBody() == null
                ? null : httpRequestChecker.getRequestBody().getFcdsl();
        DiskMetaStore metaStore = DiskStore.metaStore();
        try {
            FcdslQuery<DiskItem> q = metaStore.compile(fcdsl);
            FcdslResult<DiskItem> result = metaStore.query(q);
            Object data = FcdslProjection.isNone(q) ? result.getItems() : FcdslProjection.project(q, result.getItems());
            replier.setGot((long) result.getItems().size());
            replier.setTotal(result.getTotal());
            replier.setLast(result.getLast());
            replier.reply0SuccessHttp(data, response);
        } catch (FcdslException e) {
            int code = e.getReason() == FcdslException.Reason.BAD_QUERY
                    ? CodeMessage.Code1012BadQuery : CodeMessage.Code1017MethodNotAvailable;
            replier.replyHttp(code, e.getMessage(), response);
        }
    }
}
