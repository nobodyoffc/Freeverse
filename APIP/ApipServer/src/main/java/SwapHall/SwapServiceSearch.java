package SwapHall;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TrackHits;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.SortOptions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import config.Settings;
import constants.ApipApiNames;
import constants.IndicesNames;
import data.feipData.Service;
import data.feipData.ServiceType;
import initial.Initiator;
import utils.EsUtils;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.*;

@WebServlet(ApipApiNames.SwapHallPath + ApipApiNames.SwapServiceSearch)
public class SwapServiceSearch extends HttpServlet {
    private final Settings settings = Initiator.settings;
    private static final Gson gson = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>(){}.getType();
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter writer = response.getWriter();

        byte[] bodyBytes = request.getInputStream().readAllBytes();
        if (bodyBytes == null || bodyBytes.length == 0) {
            writeError(writer, response, "Request body is empty.");
            return;
        }

        Map<String, Object> fcdslMap;
        try {
            fcdslMap = gson.fromJson(new String(bodyBytes), MAP_TYPE);
        } catch (Exception e) {
            writeError(writer, response, "Invalid JSON: " + e.getMessage());
            return;
        }

        if (fcdslMap == null) {
            writeError(writer, response, "Request body is null.");
            return;
        }

        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);

        try {
            SearchRequest.Builder searchBuilder = new SearchRequest.Builder();
            searchBuilder.index(IndicesNames.SERVICE);

            BoolQuery.Builder boolBuilder = QueryBuilders.bool();
            boolean hasClauses = false;

            if (fcdslMap.get("query") != null) {
                List<Query> mustQueries = buildQueryList(fcdslMap.get("query"));
                if (mustQueries != null && !mustQueries.isEmpty()) {
                    boolBuilder.must(mustQueries);
                    hasClauses = true;
                }
            }

            if (fcdslMap.get("filter") != null) {
                List<Query> filterQueries = buildQueryList(fcdslMap.get("filter"));
                if (filterQueries != null && !filterQueries.isEmpty()) {
                    boolBuilder.filter(filterQueries);
                    hasClauses = true;
                }
            }

            if (fcdslMap.get("except") != null) {
                List<Query> exceptQueries = buildQueryList(fcdslMap.get("except"));
                if (exceptQueries != null && !exceptQueries.isEmpty()) {
                    boolBuilder.mustNot(exceptQueries);
                    hasClauses = true;
                }
            }

            if (hasClauses) {
                searchBuilder.query(q -> q.bool(boolBuilder.build()));
            } else {
                searchBuilder.query(q -> q.matchAll(new MatchAllQuery.Builder().build()));
            }

            int size = DEFAULT_SIZE;
            if (fcdslMap.get("size") != null) {
                try {
                    size = Integer.parseInt(String.valueOf(fcdslMap.get("size")));
                    if (size <= 0 || size > MAX_SIZE) size = DEFAULT_SIZE;
                } catch (NumberFormatException ignore) {}
            }
            searchBuilder.size(size);

            if (fcdslMap.get("sort") != null) {
                List<SortOptions> sortOptions = buildSortList(fcdslMap.get("sort"));
                if (sortOptions != null && !sortOptions.isEmpty()) {
                    searchBuilder.sort(sortOptions);
                }
            }

            if (fcdslMap.get("after") != null) {
                List<String> afterList = gson.fromJson(gson.toJson(fcdslMap.get("after")), new TypeToken<List<String>>(){}.getType());
                if (afterList != null && !afterList.isEmpty()) {
                    searchBuilder.searchAfter(EsUtils.toFieldValueList(afterList));
                }
            }

            TrackHits.Builder tb = new TrackHits.Builder();
            tb.enabled(true);
            searchBuilder.trackTotalHits(tb.build());

            SearchResponse<Service> result = esClient.search(searchBuilder.build(), Service.class);

            List<Hit<Service>> hitList = result.hits().hits();

            List<Service> serviceList = new ArrayList<>();
            for (Hit<Service> hit : hitList) {
                serviceList.add(hit.source());
            }

            long total = 0;
            if (result.hits().total() != null) {
                total = result.hits().total().value();
            }

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("code", 0);
            resultMap.put("message", "Success.");
            resultMap.put("data", serviceList);
            resultMap.put("got", serviceList.size());
            resultMap.put("total", total);
            if (!hitList.isEmpty()) {
                List<String> lastSort = EsUtils.toStringList(hitList.get(hitList.size() - 1).sort());
                if (lastSort != null && !lastSort.isEmpty()) {
                    resultMap.put("last", lastSort);
                }
            }
            writer.write(gson.toJson(resultMap));

        } catch (Exception e) {
            writeError(writer, response, "ES query failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Query> buildQueryList(Object queryObj) {
        Map<String, Object> queryMap = gson.fromJson(gson.toJson(queryObj), MAP_TYPE);
        if (queryMap == null) return null;

        List<Query> queryList = new ArrayList<>();

        if (queryMap.get("terms") != null) {
            Query termsQuery = buildTermsQuery(queryMap.get("terms"));
            if (termsQuery != null) queryList.add(termsQuery);
        }

        return queryList;
    }

    @SuppressWarnings("unchecked")
    private Query buildTermsQuery(Object termsObj) {
        Map<String, Object> termsMap = gson.fromJson(gson.toJson(termsObj), MAP_TYPE);
        if (termsMap == null) return null;

        List<String> fieldsList = gson.fromJson(gson.toJson(termsMap.get("fields")), new TypeToken<List<String>>(){}.getType());
        List<String> valuesList = gson.fromJson(gson.toJson(termsMap.get("values")), new TypeToken<List<String>>(){}.getType());

        if (fieldsList == null || fieldsList.isEmpty() || valuesList == null || valuesList.isEmpty()) return null;

        List<FieldValue> fieldValues = new ArrayList<>();
        for (String value : valuesList) {
            if (value != null && !value.isBlank()) {
                fieldValues.add(FieldValue.of(value));
            }
        }

        BoolQuery.Builder termsBoolBuilder = new BoolQuery.Builder();
        List<Query> shouldQueries = new ArrayList<>();
        for (String field : fieldsList) {
            if (field == null || field.isBlank()) continue;
            TermsQuery tQuery = TermsQuery.of(t -> t
                    .field(field)
                    .terms(t1 -> t1.value(fieldValues))
            );
            shouldQueries.add(new Query.Builder().terms(tQuery).build());
        }
        termsBoolBuilder.should(shouldQueries);

        return new Query.Builder().bool(termsBoolBuilder.build()).build();
    }

    @SuppressWarnings("unchecked")
    private List<SortOptions> buildSortList(Object sortObj) {
        List<Map<String, Object>> sortList = gson.fromJson(gson.toJson(sortObj), new TypeToken<List<Map<String, Object>>>(){}.getType());
        if (sortList == null || sortList.isEmpty()) return null;

        List<SortOptions> sortOptions = new ArrayList<>();
        for (Map<String, Object> sortItem : sortList) {
            String field = (String) sortItem.get("field");
            String order = (String) sortItem.get("order");
            if (field == null || field.isBlank()) continue;

            SortOrder sortOrder = "asc".equalsIgnoreCase(order) ? SortOrder.Asc : SortOrder.Desc;
            FieldSort fs = FieldSort.of(f -> f.field(field).order(sortOrder));
            sortOptions.add(SortOptions.of(s -> s.field(fs)));
        }
        return sortOptions;
    }

    private void writeError(PrintWriter writer, HttpServletResponse response, String message) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        Map<String, Object> err = new HashMap<>();
        err.put("code", 1020);
        err.put("message", message);
        writer.write(gson.toJson(err));
    }
}
