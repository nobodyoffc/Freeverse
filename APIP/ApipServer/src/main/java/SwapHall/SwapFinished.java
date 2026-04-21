package SwapHall;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import config.Settings;
import constants.ApipApiNames;
import constants.CodeMessage;
import constants.FieldNames;
import data.apipData.RequestBody;
import data.apipData.Sort;
import data.fcData.ReplyBody;
import data.feipData.ServiceType;
import feature.swap.SwapAffair;
import initial.Initiator;
import server.HttpRequestChecker;
import utils.EsUtils;
import utils.ObjectUtils;
import utils.http.AuthType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static constants.FieldNames.DESC;
import static constants.FieldNames.SID;
import static constants.IndicesNames.SWAP_FINISHED;
import static constants.Values.ASC;

@WebServlet(ApipApiNames.SwapHallPath + ApipApiNames.SwapFinished)
public class SwapFinished extends HttpServlet {
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
        String lastStr = null;

        RequestBody requestBody = replier.getRequestChecker().getRequestBody();
        if (requestBody != null && requestBody.getFcdsl() != null && requestBody.getFcdsl().getOther() != null) {
            if(requestBody!=null)replier.setNonce(requestBody.getNonce());
            try {
                Map<String, String> otherMap = ObjectUtils.objectToMap(requestBody.getFcdsl().getOther(), String.class, String.class);
                if (otherMap != null) {
                    sid = otherMap.get(SID);
                    lastStr = otherMap.get(FieldNames.LAST);
                }
            } catch (Exception ignored) {}
        } else {
            if(requestBody!=null)replier.setNonce(requestBody.getNonce());
            sid = request.getParameter(SID);
            lastStr = request.getParameter(FieldNames.LAST);
        }

        if (sid == null) {
            replier.replyOtherErrorHttp("SID is required.", response);
            return;
        }

        SearchRequest.Builder searchBuilder = new SearchRequest.Builder();
        List<SortOptions> sortOptionsList = Sort.makeTwoFieldsSort(FieldNames.GET_TIME, DESC, FieldNames.ID, ASC);

        searchBuilder.index(SWAP_FINISHED);
        searchBuilder.sort(sortOptionsList);
        searchBuilder.size(20);
        if (lastStr != null) {
            String[] last = lastStr.split(",");
            searchBuilder.searchAfter(EsUtils.toFieldValueList(Arrays.asList(last)));
        }

        Query query = EsUtils.getTermsQuery(SID, sid.toLowerCase());
        searchBuilder.query(query);
        try {
            SearchResponse<SwapAffair> result = esClient.search(searchBuilder.build(), SwapAffair.class);

            if (result == null || result.hits().total() == null) {
                replier.replyOtherErrorHttp("Searching ES wrong.", response);
                return;
            }
            if (result.hits().total().value() == 0) {
                replier.replyHttp(CodeMessage.Code1011DataNotFound, response);
                return;
            }
            String[] last = EsUtils.toStringList(result.hits().hits().get(result.hits().hits().size() - 1).sort()).toArray(new String[0]);
            long total = result.hits().total().value();
            List<Hit<SwapAffair>> hitList = result.hits().hits();
            List<SwapAffair> swapAffairList = new ArrayList<>();
            for (Hit<SwapAffair> hit : hitList) {
                swapAffairList.add(hit.source());
            }

            if (swapAffairList.isEmpty()) {
                replier.replyHttp(CodeMessage.Code1011DataNotFound, response);
                return;
            }

            replier.setTotal(total);
            replier.setLast(List.of(last));
            replier.setGot((long) swapAffairList.size());
            replier.reply0SuccessHttp(swapAffairList, response);
        } catch (Exception e) {
            replier.replyOtherErrorHttp(e.getMessage(), response);
        }
    }
}
