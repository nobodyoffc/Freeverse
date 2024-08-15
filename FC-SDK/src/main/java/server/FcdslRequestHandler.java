package server;

import apip.apipData.*;
import clients.esClient.EsTools;
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
import constants.ApiNames;
import constants.Constants;
import constants.IndicesNames;
import constants.ReplyCodeMessage;
import fcData.FcReplier;
import fch.fchData.*;
import feip.feipData.Cid;
import javaTools.ObjectTools;
import javaTools.http.AuthType;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

import static constants.FieldNames.*;
import static constants.IndicesNames.ADDRESS;
import static constants.Strings.HEIGHT;

public class FcdslRequestHandler {
    private final FcReplier replier;
    private final HttpServletResponse response;
    private final ElasticsearchClient esClient;
    private final RequestBody dataRequestBody;

    public FcdslRequestHandler(RequestBody dataRequestBody, HttpServletResponse response, FcReplier replier, ElasticsearchClient esClient) {
        if(dataRequestBody==null)
            this.dataRequestBody= new RequestBody();
        else this.dataRequestBody = dataRequestBody;
        if(this.dataRequestBody.getFcdsl()==null)
            this.dataRequestBody.setFcdsl(new Fcdsl());
        this.esClient = esClient;
        this.response = response;
        this.replier = replier;
    }

    public static <T> void doIdsRequest(String indexName, Class<T>  tClass, String keyFieldName, String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) {
        FcReplier replier = new FcReplier(sid,response);
        //Check authorization
        try (Jedis jedis = jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(sid, request, replier, authType, jedis, false);
            if (requestCheckResult == null) {
                return;
            }

            if (requestCheckResult.getRequestBody().getFcdsl().getIds() == null) {
                replier.replyOtherError("The parameter 'ids' is required.", null, jedis);
                return;
            }

            List<T> meetList;
            FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(requestCheckResult.getRequestBody(), response, replier, esClient);
            ArrayList<Sort> defaultSortList = null;
            meetList = fcdslRequestHandler.doRequest(indexName, defaultSortList, tClass, jedis);

            if (meetList == null || meetList.size() == 0) return;

            Map<String, T> meetMap = ObjectTools.listToMap(meetList,keyFieldName);

            replier.setGot((long) meetMap.size());
            replier.setTotal((long) meetMap.size());
            replier.reply0Success(meetMap, jedis, null);
        }
    }
    public static <T> void doSearchRequest(String sid, String indexName, Class<T> tClass, List<Sort> sort, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) {
        doSearchRequest(sid,indexName,tClass,null,null, null, null, sort,request,response,authType,esClient,jedisPool);
    }
    public static <T> void doSearchRequest(String sid, String indexName, Class<T> tClass, String filterField, String filterValue, String exceptField, String exceptValue, List<Sort> sort, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) {
        FcReplier replier = new FcReplier(sid,response);

        try (Jedis jedis = jedisPool.getResource()) {

            List<T> meetList = doRequestForList(sid, indexName, tClass, filterField, filterValue, exceptField, exceptValue, sort, request, response, authType, esClient, replier, jedis);
            if (meetList == null) return;

            replier.reply0Success(meetList, jedis, null);
        }
    }

    @Nullable
    public static <T> List<T> doRequestForList(String sid, String indexName, Class<T> tClass, String filterField, String filterValue, String exceptField, String exceptValue, List<Sort> sort, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, FcReplier replier, Jedis jedis) {
        RequestCheckResult requestCheckResult = RequestChecker.checkRequest(sid, request, replier, authType, jedis, false);
        if (requestCheckResult == null) {
            return null;
        }

        RequestBody requestBody = requestCheckResult.getRequestBody();
        if(requestBody==null){
            replier.reply(ReplyCodeMessage.Code1013BadRequest, null, jedis);
            return null;
        }
        Fcdsl fcdsl = requestBody.getFcdsl();
        if (fcdsl == null) fcdsl = new Fcdsl();

        if(filterField !=null)requestBody.getFcdsl().setFilterTerms(filterField, filterValue);
        if(exceptField !=null)requestBody.getFcdsl().setExceptTerms(exceptField, exceptValue);

        if(fcdsl.getSort()==null){
            fcdsl.setSort(sort);
        }
        //Request
        FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(requestBody, response, replier, esClient);
        List<T> meetList = fcdslRequestHandler.doRequest(indexName, fcdsl.getSort(), tClass, jedis);
        if (meetList == null || meetList.isEmpty()) return null;
        return meetList;
    }

    public static void doBlockInfoRequest(String sid, boolean isForMap,String idFieldName, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) {

        FcReplier replier = new FcReplier(sid,response);
        try (Jedis jedis = jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(sid, request, replier, authType, jedis, false);
            if (requestCheckResult == null) {
                return;
            }

            if (requestCheckResult.getApiName().equals(ApiNames.BlockByIds) && requestCheckResult.getRequestBody().getFcdsl().getIds() == null) {
                replier.replyOtherError("The parameter 'ids' is required.", null, jedis);
                return;
            }

            if (requestCheckResult.getApiName().equals(ApiNames.BlockByHeights)) {
                FcQuery query = requestCheckResult.getRequestBody().getFcdsl().getQuery();
                if(query == null||query.getTerms()==null) {
                    replier.replyOtherError("The terms query on the height field is required.", null, jedis);
                    return;
                }
            }

            //Set default sort.
            ArrayList<Sort> defaultSortList = Sort.makeSortList(HEIGHT, false, BLOCK_ID, true, null, null);

            //Request
            String index = IndicesNames.BLOCK_HAS;

            FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(requestCheckResult.getRequestBody(), response, replier, esClient);
            List<BlockHas> blockHasList = fcdslRequestHandler.doRequest(index, defaultSortList, BlockHas.class, jedis);
            if (blockHasList == null || blockHasList.size() == 0) {
                return;
            }

            List<String> idList = new ArrayList<>();
            for (BlockHas blockHas : blockHasList) {
                idList.add(blockHas.getBlockId());
            }

            List<Block> blockList;

            blockList = EsTools.getMultiByIdList(esClient, IndicesNames.BLOCK, idList, Block.class).getResultList();
            if (blockList == null ) {
                replier.replyOtherError("Failed to get block info.", null, jedis);
                return;
            }
            if (blockList.size()==0 ) {
                replier.reply(ReplyCodeMessage.Code1011DataNotFound,null,jedis);
                return;
            }

            List<BlockInfo> meetList = BlockInfo.mergeBlockAndBlockHas(blockList, blockHasList);


            Map<String, BlockInfo> meetMap = null;
            if(isForMap)meetMap= ObjectTools.listToMap(meetList,idFieldName);

            //response
            replier.setGot((long) meetList.size());
            replier.setTotal((long) meetList.size());
            if(isForMap)replier.reply0Success(meetMap, jedis, null);
            else replier.reply0Success(meetList,jedis, null);

        } catch (Exception e) {
            replier.replyOtherError(e.getMessage(), null, null);
        }
    }

    public static void doCidInfoRequest(String sid, ArrayList<Sort> sort, boolean isForMap, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) {
        FcReplier replier = new FcReplier(sid,response);
        //Check authorization
        try (Jedis jedis = jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(sid, request, replier, authType, jedis, false);
            if (requestCheckResult == null) {
                return;
            }
            if(isForMap)
                if (requestCheckResult.getRequestBody().getFcdsl().getIds() == null) {
                    replier.replyOtherError("The parameter 'ids' is required.", null, jedis);
                    return;
                }

            List<Cid> meetCidList;
            List<Address> meetAddrList;
            FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(requestCheckResult.getRequestBody(), response, replier, esClient);
            meetCidList = fcdslRequestHandler.doRequest(CID, sort, Cid.class, jedis);

            if (meetCidList == null) return;
            List<String> idList = new ArrayList<>();
            for(Cid cid : meetCidList){
                idList.add(cid.getFid());
            }

            EsTools.MgetResult<Address> multiResult;
            try {
                multiResult = EsTools.getMultiByIdList(esClient, ADDRESS, idList, Address.class);
            } catch (Exception e) {
                replier.replyOtherError("Reading ES wrong.",null,jedis);
                return;
            }
            meetAddrList = multiResult.getResultList();

            List<CidInfo> cidInfoList = CidInfo.mergeCidInfoList(meetCidList, meetAddrList);

            if (cidInfoList == null || cidInfoList.size() == 0) {
                replier.reply(ReplyCodeMessage.Code1011DataNotFound, null, jedis);
                return;
            }
            for (CidInfo cidInfo : cidInfoList) {
                cidInfo.reCalcWeight();
            }

            Map<String, CidInfo> meetMap=null;
            if(isForMap)meetMap= ObjectTools.listToMap(cidInfoList,FID);
            replier.setGot((long) cidInfoList.size());
            replier.setTotal((long) cidInfoList.size());
            if(isForMap)replier.reply0Success(meetMap, jedis, null);
            else replier.reply0Success(cidInfoList, jedis, null);
        }
    }

    public static void doTxInfoRequest(String sid, boolean isForMap, String idFieldName, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) throws ServletException, IOException {

        FcReplier replier = new FcReplier(sid,response);
        try (Jedis jedis = jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(sid, request, replier, authType, jedis, false);
            if (requestCheckResult == null) {
                return;
            }
            if(isForMap)
                if (requestCheckResult.getRequestBody().getFcdsl().getIds() == null) {
                replier.replyOtherError("The parameter 'ids' is required.", null, jedis);
                return;
                }
            //Set default sort.
            ArrayList<Sort> defaultSortList = Sort.makeSortList(HEIGHT, false, TX_ID, true, null, null);

            //Request
            String index = IndicesNames.TX_HAS;

            FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(requestCheckResult.getRequestBody(), response, replier, esClient);
            List<TxHas> txHasList = fcdslRequestHandler.doRequest(index, defaultSortList, TxHas.class, jedis);
            if (txHasList == null || txHasList.size() == 0) {
                return;
            }

            List<String> idList = new ArrayList<>();
            for (TxHas txHas : txHasList) {
                idList.add(txHas.getTxId());
            }

            List<Tx> txList;

            txList = EsTools.getMultiByIdList(esClient, IndicesNames.TX, idList, Tx.class).getResultList();
            if (txList == null ) {
                replier.replyOtherError("Failed to get TX info.", null, jedis);
                return;
            }
            if (txList.size()==0 ) {
                replier.reply(ReplyCodeMessage.Code1011DataNotFound,null,jedis);
                return;
            }

            List<TxInfo> meetList = TxInfo.mergeTxAndTxHas(txList, txHasList);


            Map<String, TxInfo> meetMap = null;
            if(isForMap)meetMap= ObjectTools.listToMap(meetList,idFieldName);

            //response
            replier.setGot((long) meetList.size());
            replier.setTotal((long) meetList.size());
            if(isForMap)replier.reply0Success(meetMap, jedis, null);
            else replier.reply0Success(meetList,jedis, null);

        } catch (Exception e) {
            replier.replyOtherError(e.getMessage(), null, null);
        }
    }

    public <T> List<T> doRequest(String index, List<Sort> defaultSortList, Class<T> tClass, Jedis jedis) {
        if(index==null||tClass==null)return null;

        SearchRequest.Builder searchBuilder = new SearchRequest.Builder();
        SearchRequest searchRequest;
        searchBuilder.index(index);

        Fcdsl fcdsl;

        if(dataRequestBody.getFcdsl()==null){
            MatchAllQuery matchAllQuery = getMatchAllQuery();
            searchBuilder.query(q->q.matchAll(matchAllQuery));
        }else{
            fcdsl = dataRequestBody.getFcdsl();

            if(fcdsl.getIds()!=null)
                return doIdsRequest(index,tClass, jedis);

            if(fcdsl.getQuery() == null && fcdsl.getExcept()==null && fcdsl.getFilter()==null){
                MatchAllQuery matchAllQuery = getMatchAllQuery();
                searchBuilder.query(q->q.matchAll(matchAllQuery));
            }else {
                List<Query> queryList = null;
                if(fcdsl.getQuery()!=null) {
                    FcQuery fcQuery = fcdsl.getQuery();
                    queryList = getQueryList(fcQuery,jedis);
                }
                
                List<Query> filterList = null;
                if(fcdsl.getFilter()!=null) {
                    Filter fcFilter = fcdsl.getFilter();
                    filterList = getQueryList(fcFilter, jedis);
                }
                
                List<Query> exceptList = null;
                if(fcdsl.getExcept()!=null) {
                    Except fcExcept = fcdsl.getExcept();
                    exceptList = getQueryList(fcExcept, jedis);
                }

                BoolQuery.Builder bBuilder = QueryBuilders.bool();
                if(queryList!=null && queryList.size()>0)
                    bBuilder.must(queryList);
                if(filterList!=null && filterList.size()>0)
                    bBuilder.filter(filterList);
                if(exceptList!=null && exceptList.size()>0)
                    bBuilder.mustNot(exceptList);

                searchBuilder.query(q -> q.bool(bBuilder.build()));
            }

            int size=0;
            try {
                if(fcdsl.getSize()!= null) {
                    size = Integer.parseInt(fcdsl.getSize());
                }
            }catch(Exception e){
                e.printStackTrace();
                replier.reply(ReplyCodeMessage.Code1012BadQuery, e.getMessage(), jedis);
                return null;
            }
            if(size==0 || size> Constants.MaxRequestSize) size= Constants.DefaultSize;
            searchBuilder.size(size);

            if(fcdsl.getSort()!=null) {
                defaultSortList = fcdsl.getSort();
            }
            if(defaultSortList!=null) {
                if (defaultSortList.size() > 0) {
                    searchBuilder.sort(Sort.getSortList(defaultSortList));
                }
            }
            if(fcdsl.getAfter()!=null){
                List<String>  after = fcdsl.getAfter();
                searchBuilder.searchAfter(after);
            }
        }

        TrackHits.Builder tb = new TrackHits.Builder();
        tb.enabled(true);
        searchBuilder.trackTotalHits(tb.build());
        searchRequest = searchBuilder.build();

        SearchResponse<T> result;
        try {
            result = esClient.search(searchRequest, tClass);
        }catch(Exception e){
            e.printStackTrace();
            replier.reply(ReplyCodeMessage.Code1012BadQuery, e.getMessage(),jedis);
            return null;
        }

        if(result==null){
            replier.reply(ReplyCodeMessage.Code1012BadQuery, null, jedis);
            return null;
        }

        assert result.hits().total() != null;
        replier.setTotal(result.hits().total().value());

        List<Hit<T>> hitList = result.hits().hits();
        if(hitList.size()==0){
            replier.reply(ReplyCodeMessage.Code1011DataNotFound, null,jedis);
            return null;
        }

        List<T> tList = new ArrayList<>();
        for(Hit<T> hit : hitList){
            tList.add(hit.source());
        }

        List<String> sortList = hitList.get(hitList.size()-1).sort();

        if(sortList.size()>0)
            replier.setLast(sortList);
        return tList;
    }

    private List<Query> getQueryList(FcQuery query, Jedis jedis) {
        BoolQuery termsQuery;
        BoolQuery partQuery;
        BoolQuery matchQuery;
        BoolQuery rangeQuery;
        BoolQuery existsQuery;
        BoolQuery unexistsQuery;
        BoolQuery equalsQuery;

        List<Query> queryList = new ArrayList<>();
        if(query.getTerms()!=null){
            termsQuery = getTermsQuery(query.getTerms());
            Query q = new Query.Builder().bool(termsQuery).build();
            if(q!=null)queryList.add(q);
        }

        if(query.getPart()!=null){
            partQuery = getPartQuery(query.getPart(), jedis);
            Query q = new Query.Builder().bool(partQuery).build();
            if(q!=null)queryList.add(q);
        }

        if(query.getMatch()!=null){
            matchQuery = getMatchQuery(query.getMatch());
            Query q = new Query.Builder().bool(matchQuery).build();
            if(q!=null)queryList.add(q);
        }

        if(query.getExists()!=null){
            existsQuery = getExistsQuery(query.getExists());
            Query q = new Query.Builder().bool(existsQuery).build();
            if(q!=null)queryList.add(q);
        }

        if(query.getUnexists()!=null){
            unexistsQuery = getUnexistQuery(query.getUnexists());
            Query q = new Query.Builder().bool(unexistsQuery).build();
            if(q!=null)queryList.add(q);
        }

        if(query.getEquals()!=null){
            equalsQuery = getEqualQuery(query.getEquals(),jedis);
            Query q = new Query.Builder().bool(equalsQuery).build();
            if(q!=null)queryList.add(q);
        }

        if(query.getRange()!=null){
            rangeQuery = getRangeQuery(query.getRange());
            Query q = new Query.Builder().bool(rangeQuery).build();
            if(q!=null)queryList.add(q);
        }

        if(queryList.size()==0){
            return null;
        }

        return queryList;
    }

    private <T> List<T> doIdsRequest(String index, Class<T> clazz, Jedis jedis) {

        List<String> idList = dataRequestBody.getFcdsl().getIds();
        if(idList.size()> Constants.MaxRequestSize) {
            Integer data = new HashMap<String, Integer>().put("maxSize", Constants.MaxRequestSize);
            replier.reply(ReplyCodeMessage.Code1010TooMuchData, data,jedis);
            return null;
        }

        MgetResponse<T> result;
        try {
            result = esClient.mget(m -> m.index(index).ids(idList), clazz);
        }catch(Exception e){
            replier.reply(ReplyCodeMessage.Code1012BadQuery, null, jedis);
            return null;
        }
        List<MultiGetResponseItem<T>> items = result.docs();

        ListIterator<MultiGetResponseItem<T>> iter = items.listIterator();
        List<T> meetList = new ArrayList<>();
        while(iter.hasNext()) {
            MultiGetResponseItem<T> item = iter.next();
            if(item.result().found()) {
                meetList.add(item.result().source());
            }
        }

        if(meetList.size()==0) {
            replier.reply(ReplyCodeMessage.Code1011DataNotFound, null, jedis);
            return null;
        }else return meetList;
    }

    private BoolQuery getMatchQuery(Match match) {
        BoolQuery.Builder bBuilder = new BoolQuery.Builder();

        if(match.getValue()==null)return null;
        if(match.getFields()==null || match.getFields().length==0)return null;

        List<Query> queryList = new ArrayList<>();

        for(String field: match.getFields()){
            if(field.isBlank())continue;
            MatchQuery.Builder mBuilder = new MatchQuery.Builder();
            mBuilder.field(field);
            mBuilder.query(match.getValue());

            queryList.add(new Query.Builder().match(mBuilder.build()).build());
        }
        bBuilder.should(queryList);
        return bBuilder.build();
    }

    private BoolQuery getExistsQuery(String[] exists) {
        BoolQuery.Builder ebBuilder = new BoolQuery.Builder();
        List<Query> eQueryList = new ArrayList<>();
        for(String e: exists) {
            if(e.isBlank())continue;
            ExistsQuery.Builder eBuilder = new ExistsQuery.Builder();
            eBuilder.queryName("exists");
            eBuilder.field(e);
            eQueryList.add(new Query.Builder().exists(eBuilder.build()).build());
        }
        return ebBuilder.must(eQueryList).build();
    }

    private BoolQuery getUnexistQuery(String[] unexist) {
        BoolQuery.Builder ueBuilder = new BoolQuery.Builder();
        List<Query> queryList = new ArrayList<>();
        for(String e: unexist) {
            if(e.isBlank())continue;
            ExistsQuery.Builder eBuilder = new ExistsQuery.Builder();
            eBuilder.queryName("exist");
            eBuilder.field(e);
            queryList.add(new Query.Builder().exists(eBuilder.build()).build());
        }
        return ueBuilder.mustNot(queryList).build();
    }

    private BoolQuery getEqualQuery(Equals equals, Jedis jedis) {
        if(equals.getValues()==null|| equals.getFields()==null)return null;

        BoolQuery.Builder boolBuilder;

        List<FieldValue> valueList = new ArrayList<>();
        for(String str: equals.getValues()){
            if(str.isBlank())continue;
            if(str.contains(".")){
                try {
                    valueList.add(FieldValue.of(Double.parseDouble(str)));
                }catch(Exception e){
                    replier.reply(ReplyCodeMessage.Code1012BadQuery, null,jedis);
                    return null;
                }
            }else{
                try {
                    valueList.add(FieldValue.of(Long.parseLong(str)));
                }catch(Exception e){
                    replier.reply(ReplyCodeMessage.Code1012BadQuery, null, jedis);
                    return null;
                }
            }
        }

        boolBuilder = makeBoolShouldTermsQuery(equals.getFields(), valueList);
        boolBuilder.queryName("equal");

        return  boolBuilder.build();
    }

    private BoolQuery getRangeQuery(Range range) {
        if(range.getFields()==null)return null;

        String[] fields = range.getFields();

        if(fields.length==0)return null;

        BoolQuery.Builder bBuilder = new BoolQuery.Builder();
        bBuilder.queryName("range");

        List<Query> queryList = new ArrayList<>();

        for(String field : fields){
            if(field.isBlank())continue;
            RangeQuery.Builder rangeBuider = new RangeQuery.Builder();
            rangeBuider.field(field);

            int count = 0;
            if(range.getGt()!=null){
                rangeBuider.gt(JsonData.of(range.getGt()));
                count++;
            }
            if(range.getGte()!=null){
                rangeBuider.gte(JsonData.of(range.getGte()));
                count++;
            }
            if(range.getLt()!=null){
                rangeBuider.lt(JsonData.of(range.getLt()));
                count++;
            }
            if(range.getLte()!=null){
                rangeBuider.lte(JsonData.of(range.getLte()));
                count++;
            }
            if(count==0)return null;

            queryList.add(new Query.Builder().range(rangeBuider.build()).build());
        }

        bBuilder.must(queryList);
        return bBuilder.build();
    }

    private BoolQuery getPartQuery(Part part, Jedis jedis) {
        BoolQuery.Builder partBoolBuilder = new BoolQuery.Builder();
        boolean isCaseInSensitive;
        try{
           isCaseInSensitive = Boolean.parseBoolean(part.getIsCaseInsensitive());
        }catch(Exception e){
            replier.reply(ReplyCodeMessage.Code1012BadQuery, e.getMessage(), jedis);
            return null;
        }

        List<Query> queryList = new ArrayList<>();
        for(String field:part.getFields()){
            if(field.isBlank())continue;
            WildcardQuery wQuery = WildcardQuery.of(w -> w
                    .field(field)
                    .caseInsensitive(isCaseInSensitive)
                    .value("*"+part.getValue()+"*"));
            queryList.add(new Query.Builder().wildcard(wQuery).build());
        }
        partBoolBuilder.should(queryList);
        partBoolBuilder.queryName("part");
        return partBoolBuilder.build();
    }

    private BoolQuery getTermsQuery(Terms terms) {
        BoolQuery.Builder termsBoolBuilder;

        List<FieldValue> valueList = new ArrayList<>();
        for(String value : terms.getValues()){
            if(value.isBlank())continue;
            valueList.add(FieldValue.of(value));
        }

        termsBoolBuilder = makeBoolShouldTermsQuery(terms.getFields(),valueList);
        termsBoolBuilder.queryName("terms");
        return termsBoolBuilder.build();
    }

    private BoolQuery.Builder makeBoolShouldTermsQuery(String[] fields, List<FieldValue> valueList) {
        BoolQuery.Builder termsBoolBuider = new BoolQuery.Builder();

        List<Query> queryList = new ArrayList<>();
        for(String field:fields){
            TermsQuery tQuery = TermsQuery.of(t -> t
                    .field(field)
                    .terms(t1 -> t1
                            .value(valueList)
                    ));

            queryList.add(new Query.Builder().terms(tQuery).build());
        }
        termsBoolBuider.should(queryList);
        return termsBoolBuider;
    }

    private MatchAllQuery getMatchAllQuery() {
        MatchAllQuery.Builder queryBuilder = new MatchAllQuery.Builder();
        queryBuilder.queryName("all");
        return queryBuilder.build();
    }

}
