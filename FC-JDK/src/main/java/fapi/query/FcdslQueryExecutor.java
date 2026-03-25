package fapi.query;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TrackHits;
import co.elastic.clients.json.JsonData;
import constants.Constants;
import data.apipData.*;
import utils.EsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executor for FCDSL queries against Elasticsearch.
 * Converts FCDSL query definitions to Elasticsearch queries and executes them.
 */
public class FcdslQueryExecutor {
    private static final Logger log = LoggerFactory.getLogger(FcdslQueryExecutor.class);
    
    private final ElasticsearchClient esClient;
    
    public FcdslQueryExecutor(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }
    
    /**
     * Execute an IDs-based query
     * @param index The index to query
     * @param clazz The entity class
     * @param ids List of document IDs
     * @return Map of matching entities keyed by their 'id' field value
     */
    public <T> Map<String, T> executeIdsQuery(String index, Class<T> clazz, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        
        if (ids.size() > Constants.MaxRequestSize) {
            throw new IllegalArgumentException(
                "Maximum IDs count exceeded: " + Constants.MaxRequestSize
            );
        }
        
        try {
            MgetResponse<T> result = esClient.mget(m -> m.index(index).ids(ids), clazz);
            List<MultiGetResponseItem<T>> items = result.docs();

            Map<String, T> resultMap = new HashMap<>();
            for (MultiGetResponseItem<T> item : items) {
                if (item.result().found()) {
                    T entity = item.result().source();
                    if (entity != null) {
                        String id = getIdFromEntity(entity);
                        if (id != null) {
                            resultMap.put(id, entity);
                        }
                    }
                }
            }
            
            return resultMap.isEmpty() ? null : resultMap;
        } catch (Exception e) {
            log.error("Failed to execute IDs query on index {}", index, e);
            throw new RuntimeException("Query execution failed", e);
        }
    }
    
    /**
     * Extract the 'id' field value from an entity using reflection
     */
    private <T> String getIdFromEntity(T entity) {
        if (entity == null) {
            return null;
        }
        
        try {
            // Try to call getId() method first
            Method getIdMethod = entity.getClass().getMethod("getId");
            Object idValue = getIdMethod.invoke(entity);
            return idValue != null ? idValue.toString() : null;
        } catch (NoSuchMethodException e) {
            // If getId() doesn't exist, try to access the 'id' field directly
            try {
                java.lang.reflect.Field idField = entity.getClass().getField("id");
                Object idValue = idField.get(entity);
                return idValue != null ? idValue.toString() : null;
            } catch (Exception ex) {
                log.warn("Failed to extract id from entity of type {}", entity.getClass().getName(), ex);
                return null;
            }
        } catch (Exception e) {
            log.warn("Failed to extract id from entity of type {}", entity.getClass().getName(), e);
            return null;
        }
    }
    
    /**
     * Execute a general FCDSL query
     * @param index The index to query
     * @param tClass The entity class
     * @param fcdsl The FCDSL query definition
     * @param defaultSortList Default sort order (can be overridden by fcdsl.sort)
     * @return QueryResult containing the results and pagination info
     */
    public <T> QueryResult<T> executeQuery(String index, Class<T> tClass, Fcdsl fcdsl, List<Sort> defaultSortList) {
        if (index == null || tClass == null) {
            return null;
        }
        
        SearchRequest.Builder searchBuilder = new SearchRequest.Builder();
        searchBuilder.index(index);
        
        // Build the query
        if (fcdsl.getQuery() == null && fcdsl.getExcept() == null && fcdsl.getFilter() == null) {
            // No query conditions - use matchAll
            MatchAllQuery matchAllQuery = getMatchAllQuery();
            searchBuilder.query(q -> q.matchAll(matchAllQuery));
        } else {
            // Build bool query from FCDSL components
            BoolQuery.Builder bBuilder = QueryBuilders.bool();
            
            if (fcdsl.getQuery() != null) {
                List<Query> queryList = getQueryList(fcdsl.getQuery());
                if (queryList != null && !queryList.isEmpty()) {
                    bBuilder.must(queryList);
                }
            }
            
            if (fcdsl.getFilter() != null) {
                List<Query> filterList = getQueryList(fcdsl.getFilter());
                if (filterList != null && !filterList.isEmpty()) {
                    bBuilder.filter(filterList);
                }
            }
            
            if (fcdsl.getExcept() != null) {
                List<Query> exceptList = getQueryList(fcdsl.getExcept());
                if (exceptList != null && !exceptList.isEmpty()) {
                    bBuilder.mustNot(exceptList);
                }
            }
            
            searchBuilder.query(q -> q.bool(bBuilder.build()));
        }
        
        // Handle size
        int size = parseSize(fcdsl.getSize());
        searchBuilder.size(size);
        
        // Handle sort
        if (fcdsl.getSort() != null) {
            defaultSortList = fcdsl.getSort();
        }
        if (defaultSortList != null && !defaultSortList.isEmpty()) {
            searchBuilder.sort(Sort.getSortList(defaultSortList));
        }
        
        // Handle after (pagination) - convert List<String> to List<FieldValue> for ES 8.8+
        if (fcdsl.getAfter() != null) {
            searchBuilder.searchAfter(EsUtils.toFieldValueList(fcdsl.getAfter()));
        }
        
        // Handle field filtering (fields and noFields)
        applyFieldFiltering(searchBuilder, fcdsl);
        
        // Enable total hits tracking
        TrackHits.Builder tb = new TrackHits.Builder();
        tb.enabled(true);
        searchBuilder.trackTotalHits(tb.build());
        
        SearchRequest searchRequest = searchBuilder.build();
        
        try {
            SearchResponse<T> result = esClient.search(searchRequest, tClass);
            return processSearchResponse(result);
        } catch (Exception e) {
            log.error("Failed to execute Elasticsearch query on index {}", index, e);
            return null;
        }
    }
    
    /**
     * Process search response into QueryResult
     */
    private <T> QueryResult<T> processSearchResponse(SearchResponse<T> result) {
        if (result == null) {
            return null;
        }
        
        QueryResult<T> queryResult = new QueryResult<>();
        
        // Set total
        var totalHits = result.hits().total();
        if (totalHits != null) {
            queryResult.setTotal(totalHits.value());
        }
        
        List<Hit<T>> hitList = result.hits().hits();
        if (hitList.isEmpty()) {
            return null;
        }
        
        // Extract data
        List<T> tList = new ArrayList<>();
        for (Hit<T> hit : hitList) {
            tList.add(hit.source());
        }
        queryResult.setData(tList);
        queryResult.setGot((long) tList.size());
        
        // Set last (pagination cursor) - convert List<FieldValue> to List<String> for backward compatibility
        List<FieldValue> sortFieldValues = hitList.get(hitList.size() - 1).sort();
        if (sortFieldValues != null && !sortFieldValues.isEmpty()) {
            queryResult.setLast(EsUtils.toStringList(sortFieldValues));
        }
        
        return queryResult;
    }
    
    /**
     * Parse size parameter with validation
     */
    private int parseSize(String sizeStr) {
        int size = 0;
        try {
            if (sizeStr != null) {
                size = Integer.parseInt(sizeStr);
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid size parameter: {}", sizeStr);
        }
        
        if (size <= 0 || size > Constants.MaxRequestSize) {
            size = Constants.DefaultSize;
        }
        return size;
    }
    
    /**
     * Apply field filtering to search builder
     */
    private void applyFieldFiltering(SearchRequest.Builder searchBuilder, Fcdsl fcdsl) {
        boolean hasFields = fcdsl.getFields() != null && !fcdsl.getFields().isEmpty();
        boolean hasNoFields = fcdsl.getNoFields() != null && !fcdsl.getNoFields().isEmpty();
        
        if (hasFields && hasNoFields) {
            searchBuilder.source(s -> s.filter(f -> f
                .includes(fcdsl.getFields())
                .excludes(fcdsl.getNoFields())
            ));
        } else if (hasFields) {
            searchBuilder.source(s -> s.filter(f -> f.includes(fcdsl.getFields())));
        } else if (hasNoFields) {
            searchBuilder.source(s -> s.filter(f -> f.excludes(fcdsl.getNoFields())));
        }
    }
    
    // ==================== Query Builders ====================
    
    /**
     * Convert FcQuery/Filter/Except to Query list
     */
    private List<Query> getQueryList(FcQuery query) {
        List<Query> queryList = new ArrayList<>();
        
        if (query.getTerms() != null) {
            BoolQuery termsQuery = getTermsQuery(query.getTerms());
            if (termsQuery != null) {
                queryList.add(new Query.Builder().bool(termsQuery).build());
            }
        }
        
        if (query.getPart() != null) {
            BoolQuery partQuery = getPartQuery(query.getPart());
            if (partQuery != null) {
                queryList.add(new Query.Builder().bool(partQuery).build());
            }
        }
        
        if (query.getMatch() != null) {
            BoolQuery matchQuery = getMatchQuery(query.getMatch());
            if (matchQuery != null) {
                queryList.add(new Query.Builder().bool(matchQuery).build());
            }
        }
        
        if (query.getExists() != null) {
            BoolQuery existsQuery = getExistsQuery(query.getExists());
            if (existsQuery != null) {
                queryList.add(new Query.Builder().bool(existsQuery).build());
            }
        }
        
        if (query.getUnexists() != null) {
            BoolQuery unexistsQuery = getUnexistQuery(query.getUnexists());
            if (unexistsQuery != null) {
                queryList.add(new Query.Builder().bool(unexistsQuery).build());
            }
        }
        
        if (query.getEquals() != null) {
            BoolQuery equalsQuery = getEqualQuery(query.getEquals());
            if (equalsQuery != null) {
                queryList.add(new Query.Builder().bool(equalsQuery).build());
            }
        }
        
        if (query.getUnequals() != null) {
            BoolQuery unequalsQuery = getUnequalQuery(query.getUnequals());
            if (unequalsQuery != null) {
                queryList.add(new Query.Builder().bool(unequalsQuery).build());
            }
        }
        
        if (query.getRange() != null) {
            BoolQuery rangeQuery = getRangeQuery(query.getRange());
            if (rangeQuery != null) {
                queryList.add(new Query.Builder().bool(rangeQuery).build());
            }
        }
        
        return queryList.isEmpty() ? null : queryList;
    }
    
    /**
     * Build Terms query
     */
    private BoolQuery getTermsQuery(Terms terms) {
        if (terms.getValues() == null || terms.getFields() == null) {
            return null;
        }
        
        List<FieldValue> valueList = new ArrayList<>();
        for (String value : terms.getValues()) {
            if (value == null || value.isBlank()) continue;
            String decodedValue = java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
            valueList.add(FieldValue.of(decodedValue));
        }
        
        BoolQuery.Builder termsBoolBuilder = makeBoolShouldTermsQuery(terms.getFields(), valueList);
        termsBoolBuilder.queryName("terms");
        return termsBoolBuilder.build();
    }
    
    /**
     * Build Part query (wildcard)
     */
    private BoolQuery getPartQuery(Part part) {
        if (part.getFields() == null || part.getValue() == null) {
            return null;
        }
        
        boolean isCaseInsensitive;
        try {
            isCaseInsensitive = Boolean.parseBoolean(part.getIsCaseInsensitive());
        } catch (Exception e) {
            log.warn("Invalid isCaseInsensitive parameter: {}", part.getIsCaseInsensitive());
            isCaseInsensitive = false;
        }
        
        List<Query> queryList = new ArrayList<>();
        String decodedValue = java.net.URLDecoder.decode(part.getValue(), StandardCharsets.UTF_8);
        
        for (String field : part.getFields()) {
            if (field == null || field.isBlank()) continue;
            final boolean caseInsensitive = isCaseInsensitive;
            WildcardQuery wQuery = WildcardQuery.of(w -> w
                .field(field)
                .caseInsensitive(caseInsensitive)
                .value("*" + decodedValue + "*"));
            queryList.add(new Query.Builder().wildcard(wQuery).build());
        }
        
        BoolQuery.Builder partBoolBuilder = new BoolQuery.Builder();
        partBoolBuilder.should(queryList);
        partBoolBuilder.queryName("part");
        return partBoolBuilder.build();
    }
    
    /**
     * Build Match query
     */
    private BoolQuery getMatchQuery(Match match) {
        if (match.getValue() == null || match.getFields() == null || match.getFields().length == 0) {
            return null;
        }
        
        String decodedValue = java.net.URLDecoder.decode(match.getValue(), StandardCharsets.UTF_8);
        List<Query> queryList = new ArrayList<>();
        
        for (String field : match.getFields()) {
            if (field == null || field.isBlank()) continue;
            MatchQuery.Builder mBuilder = new MatchQuery.Builder();
            mBuilder.field(field);
            mBuilder.query(decodedValue);
            queryList.add(new Query.Builder().match(mBuilder.build()).build());
        }
        
        BoolQuery.Builder bBuilder = new BoolQuery.Builder();
        bBuilder.should(queryList);
        return bBuilder.build();
    }
    
    /**
     * Build Range query
     */
    private BoolQuery getRangeQuery(Range range) {
        if (range.getFields() == null || range.getFields().length == 0) {
            return null;
        }
        
        List<Query> queryList = new ArrayList<>();
        
        for (String field : range.getFields()) {
            if (field == null || field.isBlank()) continue;
            
            RangeQuery.Builder rangeBuilder = new RangeQuery.Builder();
            rangeBuilder.field(field);
            
            int count = 0;
            if (range.getGt() != null) {
                rangeBuilder.gt(JsonData.of(range.getGt()));
                count++;
            }
            if (range.getGte() != null) {
                rangeBuilder.gte(JsonData.of(range.getGte()));
                count++;
            }
            if (range.getLt() != null) {
                rangeBuilder.lt(JsonData.of(range.getLt()));
                count++;
            }
            if (range.getLte() != null) {
                rangeBuilder.lte(JsonData.of(range.getLte()));
                count++;
            }
            
            if (count > 0) {
                queryList.add(new Query.Builder().range(rangeBuilder.build()).build());
            }
        }
        
        if (queryList.isEmpty()) {
            return null;
        }
        
        BoolQuery.Builder bBuilder = new BoolQuery.Builder();
        bBuilder.queryName("range");
        bBuilder.should(queryList);
        return bBuilder.build();
    }
    
    /**
     * Build Exists query
     */
    private BoolQuery getExistsQuery(String[] exists) {
        if (exists == null || exists.length == 0) {
            return null;
        }
        
        List<Query> eQueryList = new ArrayList<>();
        for (String e : exists) {
            if (e == null || e.isBlank()) continue;
            ExistsQuery.Builder eBuilder = new ExistsQuery.Builder();
            eBuilder.queryName("exists");
            eBuilder.field(e);
            eQueryList.add(new Query.Builder().exists(eBuilder.build()).build());
        }
        
        BoolQuery.Builder ebBuilder = new BoolQuery.Builder();
        return ebBuilder.must(eQueryList).build();
    }
    
    /**
     * Build Unexists query
     */
    private BoolQuery getUnexistQuery(String[] unexist) {
        if (unexist == null || unexist.length == 0) {
            return null;
        }
        
        List<Query> queryList = new ArrayList<>();
        for (String e : unexist) {
            if (e == null || e.isBlank()) continue;
            ExistsQuery.Builder eBuilder = new ExistsQuery.Builder();
            eBuilder.queryName("exist");
            eBuilder.field(e);
            queryList.add(new Query.Builder().exists(eBuilder.build()).build());
        }
        
        BoolQuery.Builder ueBuilder = new BoolQuery.Builder();
        return ueBuilder.mustNot(queryList).build();
    }
    
    /**
     * Build Equals query
     */
    private BoolQuery getEqualQuery(Equals equals) {
        if (equals.getValues() == null || equals.getFields() == null) {
            return null;
        }
        
        List<FieldValue> valueList = new ArrayList<>();
        for (String str : equals.getValues()) {
            if (str == null || str.isBlank()) continue;
            String decodedValue = java.net.URLDecoder.decode(str, StandardCharsets.UTF_8);
            valueList.add(FieldValue.of(decodedValue));
        }
        
        BoolQuery.Builder boolBuilder = makeBoolShouldTermsQuery(equals.getFields(), valueList);
        boolBuilder.queryName("equal");
        return boolBuilder.build();
    }
    
    /**
     * Build Unequals query
     */
    private BoolQuery getUnequalQuery(Equals unequals) {
        if (unequals.getValues() == null || unequals.getFields() == null) {
            return null;
        }
        
        List<FieldValue> valueList = new ArrayList<>();
        for (String str : unequals.getValues()) {
            if (str == null || str.isBlank()) continue;
            String decodedValue = java.net.URLDecoder.decode(str, StandardCharsets.UTF_8);
            valueList.add(FieldValue.of(decodedValue));
        }
        
        BoolQuery.Builder boolBuilder = makeBoolMustNotTermsQuery(unequals.getFields(), valueList);
        boolBuilder.queryName("unequal");
        return boolBuilder.build();
    }
    
    /**
     * Build MatchAll query
     */
    private MatchAllQuery getMatchAllQuery() {
        MatchAllQuery.Builder queryBuilder = new MatchAllQuery.Builder();
        queryBuilder.queryName("all");
        return queryBuilder.build();
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Build should Terms query (helper)
     */
    private BoolQuery.Builder makeBoolShouldTermsQuery(String[] fields, List<FieldValue> valueList) {
        BoolQuery.Builder termsBoolBuilder = new BoolQuery.Builder();
        List<Query> queryList = new ArrayList<>();
        
        for (String field : fields) {
            if (field == null || field.isBlank()) continue;
            TermsQuery tQuery = TermsQuery.of(t -> t
                .field(field)
                .terms(t1 -> t1.value(valueList))
            );
            queryList.add(new Query.Builder().terms(tQuery).build());
        }
        
        termsBoolBuilder.should(queryList);
        return termsBoolBuilder;
    }
    
    /**
     * Build mustNot Terms query (helper)
     */
    private BoolQuery.Builder makeBoolMustNotTermsQuery(String[] fields, List<FieldValue> valueList) {
        BoolQuery.Builder termsBoolBuilder = new BoolQuery.Builder();
        List<Query> queryList = new ArrayList<>();
        
        for (String field : fields) {
            if (field == null || field.isBlank()) continue;
            TermsQuery tQuery = TermsQuery.of(t -> t
                .field(field)
                .terms(t1 -> t1.value(valueList))
            );
            queryList.add(new Query.Builder().terms(tQuery).build());
        }
        
        termsBoolBuilder.mustNot(queryList);
        return termsBoolBuilder;
    }
    
    /**
     * Get the Elasticsearch client
     */
    public ElasticsearchClient getEsClient() {
        return esClient;
    }
}

