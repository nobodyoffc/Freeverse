package SwapHall;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import config.Settings;
import constants.ApipApiNames;
import constants.IndicesNames;
import data.apipData.RequestBody;
import data.fcData.ReplyBody;
import data.fchData.Freer;
import data.feipData.ServiceType;
import initial.Initiator;
import server.HttpRequestChecker;
import utils.ObjectUtils;
import utils.http.AuthType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

@WebServlet(ApipApiNames.SwapHallPath + ApipApiNames.SwapFidSearch)
public class SwapFidSearch extends HttpServlet {
    private final Settings settings = Initiator.settings;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        doRequest(request, response, AuthType.FREE, settings);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        doRequest(request, response, AuthType.ENCRYPTED, settings);
    }

    @SuppressWarnings("unchecked")
    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) {
        ReplyBody replier = new ReplyBody(settings);
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) return;

        List<String> idList = null;
        RequestBody requestBody = replier.getRequestChecker().getRequestBody();
        if (requestBody != null && requestBody.getFcdsl() != null && requestBody.getFcdsl().getIds() != null) {
            if(requestBody!=null)replier.setNonce(requestBody.getNonce());
            idList = requestBody.getFcdsl().getIds();
        } else if (requestBody != null && requestBody.getFcdsl() != null && requestBody.getFcdsl().getOther() != null) {
            if(requestBody!=null)replier.setNonce(requestBody.getNonce());
            try {
                Map<String, Object> otherMap = ObjectUtils.objectToMap(requestBody.getFcdsl().getOther(), String.class, Object.class);
                if (otherMap != null && otherMap.get("ids") != null) {
                    Gson gson = new Gson();
                    idList = gson.fromJson(gson.toJson(otherMap.get("ids")), new TypeToken<List<String>>(){}.getType());
                }
            } catch (Exception ignored) {}
        } else {
            if(requestBody!=null)replier.setNonce(requestBody.getNonce());
            String idsParam = request.getParameter("ids");
            if (idsParam != null) {
                idList = Arrays.asList(idsParam.split(","));
            }
        }

        if (idList == null || idList.isEmpty()) {
            replier.replyOtherErrorHttp("The 'ids' field is required.", response);
            return;
        }

        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);

        try {
            List<String> finalIdList = idList;
            MgetResponse<Freer> mgetResponse = esClient.mget(m -> m.index(IndicesNames.FREER).ids(finalIdList), Freer.class);

            List<Freer> resultList = new ArrayList<>();
            for (MultiGetResponseItem<Freer> item : mgetResponse.docs()) {
                if (item.result().found() && item.result().source() != null) {
                    resultList.add(item.result().source());
                }
            }

            replier.setTotal((long) resultList.size());
            replier.setGot((long) resultList.size());
            replier.reply0SuccessHttp(resultList, response);
        } catch (Exception e) {
            replier.replyOtherErrorHttp("ES query failed: " + e.getMessage(), response);
        }
    }
}
