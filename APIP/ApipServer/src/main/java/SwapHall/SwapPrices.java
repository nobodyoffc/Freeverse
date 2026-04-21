package SwapHall;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import config.Settings;
import constants.ApipApiNames;
import constants.CodeMessage;
import constants.FieldNames;
import data.apipData.RequestBody;
import data.apipData.Sort;
import data.fcData.ReplyBody;
import data.feipData.ServiceType;
import feature.swap.SwapPriceData;
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

import static constants.FieldNames.*;
import static constants.IndicesNames.SWAP_PRICE;
import static constants.Values.ASC;

@WebServlet(ApipApiNames.SwapHallPath + ApipApiNames.SwapPrice)
public class SwapPrices extends HttpServlet {
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
        String gTick = null;
        String mTick = null;
        String lastStr = null;
        String startTime = null;
        String endTime = null;
        String size = null;

        RequestBody requestBody = replier.getRequestChecker().getRequestBody();
        if (requestBody != null && requestBody.getFcdsl() != null && requestBody.getFcdsl().getOther() != null) {
            if(requestBody!=null)replier.setNonce(requestBody.getNonce());
            try {
                Map<String, String> otherMap = ObjectUtils.objectToMap(requestBody.getFcdsl().getOther(), String.class, String.class);
                if (otherMap != null) {
                    sid = otherMap.get(SID);
                    gTick = otherMap.get(G_TICK);
                    mTick = otherMap.get(M_TICK);
                    lastStr = otherMap.get(FieldNames.LAST);
                    startTime = otherMap.get(START_TIME);
                    endTime = otherMap.get(END_TIME);
                    size = otherMap.get(SIZE);
                }
            } catch (Exception ignored) {}
        } else {
            if(requestBody!=null)replier.setNonce(requestBody.getNonce());
            sid = request.getParameter(SID);
            gTick = request.getParameter(G_TICK);
            mTick = request.getParameter(M_TICK);
            lastStr = request.getParameter(FieldNames.LAST);
            startTime = request.getParameter(START_TIME);
            endTime = request.getParameter(END_TIME);
            size = request.getParameter(SIZE);
        }

        SearchRequest.Builder searchBuilder = new SearchRequest.Builder();
        List<SortOptions> sortOptionsList = Sort.makeTwoFieldsSort(FieldNames.TIME, DESC, FieldNames.SID, ASC);

        searchBuilder.index(SWAP_PRICE);
        searchBuilder.sort(sortOptionsList);
        if (size != null) searchBuilder.size(Integer.valueOf(size));
        else searchBuilder.size(50);
        if (lastStr != null) {
            String[] last = lastStr.split(",");
            searchBuilder.searchAfter(EsUtils.toFieldValueList(Arrays.asList(last)));
        }

        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        List<Query> queryList = new ArrayList<>();
        if (sid != null) {
            Query query = EsUtils.getTermsQuery(SID, sid.toLowerCase());
            queryList.add(query);
        } else {
            if (gTick != null) {
                Query query = EsUtils.getTermsQuery(G_TICK, gTick.toLowerCase());
                queryList.add(query);
            }
            if (mTick != null) {
                Query query = EsUtils.getTermsQuery(M_TICK, mTick.toLowerCase());
                queryList.add(query);
            }
        }

        if (startTime != null || endTime != null) {
            RangeQuery.Builder rqb = new RangeQuery.Builder();
            rqb.field(TIME);
            if (startTime != null)
                rqb.gte(JsonData.of(Long.parseLong(startTime)));
            if (endTime != null)
                rqb.lt(JsonData.of(Long.parseLong(endTime)));
            Query query = new Query.Builder().range(rqb.build()).build();
            queryList.add(query);
        }

        BoolQuery boolQuery = boolBuilder.must(queryList).build();
        Query query = new Query(boolQuery);
        searchBuilder.query(query);

        try {
            SearchResponse<SwapPriceData> result = esClient.search(searchBuilder.build(), SwapPriceData.class);

            long total = 0;
            if (result != null && result.hits().total() != null)
                total = result.hits().total().value();
            if (total == 0) {
                replier.replyHttp(CodeMessage.Code1011DataNotFound, response);
                return;
            }

            String[] last = null;
            if (!result.hits().hits().isEmpty()) {
                last = EsUtils.toStringList(result.hits().hits().get(result.hits().hits().size() - 1).sort()).toArray(new String[0]);
            }

            List<Hit<SwapPriceData>> hitList = result.hits().hits();
            List<SwapPriceData> swapPriceList = new ArrayList<>();
            for (Hit<SwapPriceData> hit : hitList) {
                swapPriceList.add(hit.source());
            }

            if (swapPriceList.isEmpty()) {
                replier.replyHttp(CodeMessage.Code1011DataNotFound, response);
                return;
            }

            replier.setTotal(total);
            replier.setLast(last != null ? List.of(last) : null);
            replier.setGot((long) swapPriceList.size());
            replier.reply0SuccessHttp(swapPriceList, response);
        } catch (Exception e) {
            replier.replyOtherErrorHttp(e.getMessage(), response);
        }
    }
}
