package SwapHall;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import config.Settings;
import constants.ApipApiNames;
import constants.CodeMessage;
import constants.IndicesNames;
import data.apipData.RequestBody;
import data.fcData.ReplyBody;
import data.feipData.ServiceType;
import feature.swap.SwapAffair;
import feature.swap.SwapPendingData;
import initial.Initiator;
import server.HttpRequestChecker;
import utils.Hex;
import utils.ObjectUtils;
import utils.http.AuthType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

import static constants.FieldNames.SID;

@WebServlet(ApipApiNames.SwapHallPath + ApipApiNames.SwapPending)
public class SwapPending extends HttpServlet {
    private final Settings settings = Initiator.settings;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        doRequest(request, response, AuthType.FREE, settings);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        doRequest(request, response, AuthType.ENCRYPTED, settings);
    }

    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) {
        ReplyBody replier = new ReplyBody(settings);
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) return;

        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);

        String sid = null;
        RequestBody requestBody = replier.getRequestChecker().getRequestBody();
        if (requestBody != null && requestBody.getFcdsl() != null && requestBody.getFcdsl().getOther() != null) {
            if(requestBody!=null)replier.setNonce(requestBody.getNonce());
            try {
                Map<String, String> otherMap = ObjectUtils.objectToMap(requestBody.getFcdsl().getOther(), String.class, String.class);
                if (otherMap != null) {
                    sid = otherMap.get(SID);
                }
            } catch (Exception ignored) {}
        } else {
            if(requestBody!=null)replier.setNonce(requestBody.getNonce());
            sid = request.getParameter(SID);
        }

        if (sid == null) {
            replier.replyOtherErrorHttp("SID is required.", response);
            return;
        }
        sid = sid.toLowerCase();

        if (!Hex.isHexString(sid) || sid.length() != 64) {
            replier.replyOtherErrorHttp("It's not a SID.", response);
            return;
        }

        String finalSid = sid;

        try {
            GetResponse<SwapPendingData> response1 = esClient.get(b -> b
                    .index(IndicesNames.SWAP_PENDING)
                    .id(finalSid)
            , SwapPendingData.class);

            if (response1.found()) {
                SwapPendingData swapPending = response1.source();
                if (swapPending == null) {
                    replier.replyHttp(CodeMessage.Code1011DataNotFound, response);
                    return;
                }
                List<SwapAffair> swapPendingList = swapPending.getPendingList();
                if (swapPendingList == null || swapPendingList.isEmpty()) {
                    replier.replyHttp(CodeMessage.Code1011DataNotFound, response);
                    return;
                }
                replier.setTotal((long) swapPendingList.size());
                replier.setGot((long) swapPendingList.size());
                replier.reply0SuccessHttp(swapPendingList, response);
            } else {
                replier.replyHttp(CodeMessage.Code1011DataNotFound, response);
            }
        } catch (Exception e) {
            replier.replyOtherErrorHttp(e.getMessage(), response);
        }
    }
}
