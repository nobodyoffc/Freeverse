package Disk;

import config.Settings;
import constants.ApipApiNames;
import data.apipData.Sort;
import data.fcData.DiskItem;
import data.fcData.ReplyBody;
import initial.Initiator;
import server.FcHttpRequestHandler;
import server.HttpRequestChecker;
import utils.http.AuthType;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;

import static constants.FieldNames.SINCE;

/**
 * DISK LIST: query stored {@link DiskItem} metadata with an FCDSL.
 *
 * <p>GET uses {@code FC_SIGN_URL}, POST uses {@code ENCRYPTED}. Defaults to sorting
 * by "since" (descending) when the request supplies no sort.
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

        FcHttpRequestHandler fcHttpRequestHandler = new FcHttpRequestHandler(replier, settings);

        ArrayList<Sort> defaultSortList = null;
        if (httpRequestChecker.getRequestBody() == null
                || httpRequestChecker.getRequestBody().getFcdsl() == null
                || httpRequestChecker.getRequestBody().getFcdsl().getSort() == null) {
            // Sort only by "since" (numeric) to avoid shard failures on a dynamically-mapped index.
            defaultSortList = Sort.makeSortList(SINCE, false, null, null, null, null);
        }

        java.util.List<DiskItem> meetList =
                fcHttpRequestHandler.doRequest(DiskStore.indexName(), defaultSortList, DiskItem.class);

        if (meetList == null) {
            replier.replyHttp(fcHttpRequestHandler.getFinalReplyJson(), response);
            return;
        }
        replier.reply0SuccessHttp(meetList, response);
    }
}
