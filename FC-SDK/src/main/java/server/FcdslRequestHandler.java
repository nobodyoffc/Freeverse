package server;

import apip.apipData.*;
import appTools.Settings;
import feip.feipData.Service;
import tools.EsTools;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TrackHits;
import co.elastic.clients.json.JsonData;
import constants.Constants;
import constants.IndicesNames;
import constants.CodeMessage;
import fcData.FidTxMask;
import fcData.ReplyBody;
import fch.fchData.*;
import feip.feipData.Cid;
import tools.ObjectTools;
import tools.http.AuthType;

import org.jetbrains.annotations.Nullable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;

import static constants.FieldNames.*;
import static constants.IndicesNames.ADDRESS;
import static constants.IndicesNames.CASH;
import static constants.Strings.HEIGHT;

public class FcdslRequestHandler {
    private RequestBody requestBody;
    private final ElasticsearchClient esClient;
    private final Settings settings;
    private ReplyBody replyBody;
    private String finalReplyJson = null;

    public FcdslRequestHandler(Settings settings) {
        this.settings = settings;
        this.replyBody = new ReplyBody(settings);
        this.requestBody = null;
        // if(this.requestBody.getFcdsl()==null)
        //     this.requestBody.setFcdsl(new Fcdsl());
        if(settings.getClient(Service.ServiceType.ES)!=null)
          this.esClient = (ElasticsearchClient)settings.getClient(Service.ServiceType.ES);
        else this.esClient = null;
    }

    public FcdslRequestHandler(ReplyBody replyBody, Settings settings) {
        this.settings = settings;
        this.replyBody = replyBody;
        this.requestBody = replyBody.getRequestChecker().getRequestBody();
        if(this.requestBody.getFcdsl()==null)
            this.requestBody.setFcdsl(new Fcdsl());
        if(settings.getClient(Service.ServiceType.ES)!=null)
          this.esClient = (ElasticsearchClient)settings.getClient(Service.ServiceType.ES);
        else this.esClient = null;
    }


    public  <T> List<T> doRequest(String index, List<Sort> defaultSortList, Class<T> tClass) {

        if(index==null||tClass==null)return null;

        SearchRequest.Builder searchBuilder = new SearchRequest.Builder();
        SearchRequest searchRequest;
        searchBuilder.index(index);


        Fcdsl fcdsl;
        if (requestBody.getFcdsl() == null) {
            MatchAllQuery matchAllQuery = getMatchAllQuery();
            searchBuilder.query(q -> q.matchAll(matchAllQuery));
        } else {
            fcdsl = requestBody.getFcdsl();

            if (fcdsl.getIds() != null)
                return doIdsRequest(index, tClass);

            if (fcdsl.getQuery() == null && fcdsl.getExcept() == null && fcdsl.getFilter() == null) {
                MatchAllQuery matchAllQuery = getMatchAllQuery();
                searchBuilder.query(q -> q.matchAll(matchAllQuery));
            } else {
                List<Query> queryList = null;
                if (fcdsl.getQuery() != null) {
                    FcQuery fcQuery = fcdsl.getQuery();
                    queryList = getQueryList(fcQuery);
                    if(queryList==null)return null;
                }

                List<Query> filterList = null;
                if (fcdsl.getFilter() != null) {
                    Filter fcFilter = fcdsl.getFilter();
                    filterList = getQueryList(fcFilter);
                    if(filterList==null)return null;
                }

                List<Query> exceptList = null;
                if (fcdsl.getExcept() != null) {
                    Except fcExcept = fcdsl.getExcept();
                    exceptList = getQueryList(fcExcept);
                    if(exceptList==null)return null;
                }

                BoolQuery.Builder bBuilder = QueryBuilders.bool();
                if (queryList != null && queryList.size() > 0)
                    bBuilder.must(queryList);
                if (filterList != null && filterList.size() > 0)
                    bBuilder.filter(filterList);
                if (exceptList != null && exceptList.size() > 0)
                    bBuilder.mustNot(exceptList);

                searchBuilder.query(q -> q.bool(bBuilder.build()));
            }

            int size = 0;
            try {
                if (fcdsl.getSize() != null) {
                    size = Integer.parseInt(fcdsl.getSize());
                }
            } catch (Exception e) {
                finalReplyJson = replyBody.reply(CodeMessage.Code1012BadQuery, null,e.getMessage());
                return null;
            }
            if (size == 0 || size > Constants.MaxRequestSize) size = Constants.DefaultSize;
            searchBuilder.size(size);

            if (fcdsl.getSort() != null) {
                defaultSortList = fcdsl.getSort();
            }
            if (defaultSortList != null) {
                if (defaultSortList.size() > 0) {
                    searchBuilder.sort(Sort.getSortList(defaultSortList));
                }
            }
            if (fcdsl.getAfter() != null) {
                List<String> after = fcdsl.getAfter();
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
            finalReplyJson = replyBody.replyError(CodeMessage.Code1012BadQuery);
            e.printStackTrace();
            return null;
        }

        if(result==null){
            finalReplyJson = replyBody.replyError(CodeMessage.Code1012BadQuery);
            return null;
        }

        replyBody.setTotal((long) result.hits().hits().size());

        List<Hit<T>> hitList = result.hits().hits();
        if(hitList.size()==0){
            finalReplyJson = replyBody.replyError(CodeMessage.Code1011DataNotFound);
            return null;
        }

        List<T> tList = new ArrayList<>();
        for(Hit<T> hit : hitList){
            tList.add(hit.source());
        }

        List<String> sortList = hitList.get(hitList.size()-1).sort();

        if(sortList.size()>0)
            replyBody.setLast(sortList);

        return tList;
    }

    public  <T> void doIdsRequest(String indexName, Class<T>  tClass, String keyFieldName, HttpServletRequest request, HttpServletResponse response, AuthType authType) {

        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings);
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) {
            return;
        }
        replyBody = httpRequestChecker.getReplyBody();
        requestBody = httpRequestChecker.getRequestBody();
        FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(replyBody,settings);
        Map<String, T> meetMap = fcdslRequestHandler.doRequestForMap(indexName, tClass, keyFieldName);
        if (meetMap == null) {
            replyBody.responseFinalJsonHttp(response);
            return;
        }
        replyBody.replySingleDataSuccessHttp(meetMap, response);
    }

    @Nullable
    public <T> Map<String, T> doRequestForMap(String indexName, Class<T> tClass, String keyFieldName) {
        HttpRequestChecker httpRequestChecker = replyBody.getRequestChecker();
        if (httpRequestChecker.getRequestBody().getFcdsl().getIds() == null) {
            replyBody.replyOtherErrorHttp("The parameter 'ids' is required.", null);
            return null;
        }

        List<T> meetList;
        FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(replyBody, settings);
        meetList = fcdslRequestHandler.doRequest(indexName, null, tClass);
        if(meetList==null){
            return null;
        }

        if (meetList.size() == 0) return new HashMap<>();

        Map<String, T> meetMap = ObjectTools.listToMap(meetList, keyFieldName);

        replyBody.setGot((long) meetMap.size());
        replyBody.setTotal((long) meetMap.size());
        return meetMap;
    }

    public  <T> void doSearchRequest(String indexName, Class<T> tClass, List<Sort> sort, HttpServletRequest request, HttpServletResponse response, AuthType authType) {
        doSearchRequest(indexName,tClass,null,null, null, null, sort,request,response,authType);
    }
    public <T> void doSearchRequest(String indexName, Class<T> tClass, String filterField, String filterValue, String exceptField, String exceptValue, List<Sort> sort, HttpServletRequest request, HttpServletResponse response, AuthType authType) {

        List<T> meetList = doRequestForList(indexName, tClass, filterField, filterValue, exceptField, exceptValue, sort, request, response, authType);
        if (meetList == null) return;

        replyBody.replySingleDataSuccessHttp(meetList,response);
    }

    @Nullable
    public <T> List<T> doRequestForList(String indexName, Class<T> tClass, String filterField, String filterValue, String exceptField, String exceptValue, List<Sort> sort, HttpServletRequest request, HttpServletResponse response, AuthType authType) {

        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings);
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) {
            return null;
        }
        replyBody = httpRequestChecker.getReplyBody();
        requestBody = httpRequestChecker.getRequestBody();

        if (requestBody == null) {
            replyBody.replyHttp(CodeMessage.Code1013BadRequest, null);
            return null;
        }
        Fcdsl fcdsl = requestBody.getFcdsl();
        if (fcdsl == null) fcdsl = new Fcdsl();

        if (filterField != null) requestBody.getFcdsl().setFilterTerms(filterField, filterValue);
        if (exceptField != null) requestBody.getFcdsl().setExceptTerms(exceptField, exceptValue);

        if (fcdsl.getSort() == null) {
            fcdsl.setSort(sort);
        }
        //Request
        List<T> meetList = doRequest(indexName, fcdsl.getSort(), tClass);
        if (meetList == null) {
            replyBody.responseFinalJsonHttp(response);
            return null;
        }
        return meetList;
    }

    public void doBlockInfoRequest(boolean isForMap, String idFieldName, HttpServletRequest request, HttpServletResponse response, AuthType authType) {
        try{
            HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings);

            boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
            if (!isOk) {
                return;
            }

            replyBody = httpRequestChecker.getReplyBody();
            requestBody = httpRequestChecker.getRequestBody();

            if (httpRequestChecker.getApiName().equals(ApipApiNames.BLOCK_BY_IDS) && httpRequestChecker.getRequestBody().getFcdsl().getIds() == null) {
                replyBody.replyOtherErrorHttp("The parameter 'ids' is required.", response);
                return;
            }

            if (httpRequestChecker.getApiName().equals(ApipApiNames.BLOCK_BY_HEIGHTS)) {
                FcQuery query = httpRequestChecker.getRequestBody().getFcdsl().getQuery();
                if(query == null||query.getTerms()==null) {
                    replyBody.replyOtherErrorHttp("The terms query on the height field is required.", response);
                    return;
                }
            }

            //Set default sort.
            ArrayList<Sort> defaultSortList = Sort.makeSortList(HEIGHT, false, BLOCK_ID, true, null, null);

            //Request
            String index = IndicesNames.BLOCK_HAS;

            List<BlockHas> blockHasList = doRequest(index, defaultSortList, BlockHas.class);
            if (blockHasList == null || blockHasList.size() == 0) {
                return;
            }

            List<String> idList = new ArrayList<>();
            for (BlockHas blockHas : blockHasList) {
                idList.add(blockHas.getBlockId());
            }

            List<Block> blockList;
            ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
            if(esClient == null) {
                replyBody.replyOtherErrorHttp("Failed to get ES client.", response);
                return;
            }
            blockList = EsTools.getMultiByIdList(esClient, IndicesNames.BLOCK, idList, Block.class).getResultList();
            if (blockList == null ) {
                replyBody.replyOtherErrorHttp("Failed to get block info.", response);
                return;
            }
            if (blockList.size()==0 ) {
                replyBody.replyHttp(CodeMessage.Code1011DataNotFound,null);
                return;
            }

            List<BlockInfo> meetList = BlockInfo.mergeBlockAndBlockHas(blockList, blockHasList);


            Map<String, BlockInfo> meetMap = null;

            if(isForMap){
                meetMap= ObjectTools.listToMap(meetList,idFieldName);
                replyBody.setLast(null);
            }

            //response
            replyBody.setGot((long) meetList.size());
            replyBody.setTotal((long) meetList.size());
            if(isForMap)replyBody.replySingleDataSuccessHttp(meetMap, response);
            else replyBody.replySingleDataSuccessHttp(meetList, response);

        } catch (Exception e) {
            e.printStackTrace();
            replyBody.replyOtherErrorHttp(e.getMessage(), response);
        }
    }

    public void doCidInfoByIdsRequestHttp(ArrayList<Sort> sort, HttpServletRequest request, HttpServletResponse response, AuthType authType) {
        //Check authorization
        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);

        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings);
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) {
            return;
        }
        replyBody = httpRequestChecker.getReplyBody();
        requestBody = httpRequestChecker.getRequestBody();
        List<String> idList = httpRequestChecker.getRequestBody().getFcdsl().getIds();
        if (idList == null || idList.isEmpty()) {
            replyBody.replyOtherErrorHttp("The parameter 'ids' is required.", response);
            return;
        }

        List<Address> meetAddrList;
        List<Cid> meetCidList;

        FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(replyBody, settings);
        meetAddrList = fcdslRequestHandler.doRequest(ADDRESS, sort, Address.class);

        if (meetAddrList == null) return;

        EsTools.MgetResult<Cid> multiResult;
        try {
            multiResult = EsTools.getMultiByIdList(esClient, CID, idList, Cid.class);
        } catch (Exception e) {
            replyBody.replyOtherErrorHttp("Reading ES wrong.",null);
            return;
        }
        meetCidList = multiResult.getResultList();

        List<CidInfo> cidInfoList = CidInfo.mergeCidInfoList(meetAddrList, meetCidList);

        if (cidInfoList.size() == 0) {
            replyBody.replyHttp(CodeMessage.Code1011DataNotFound, null);
            return;
        }

        for (CidInfo cidInfo : cidInfoList) {
            cidInfo.reCalcWeight();
        }

        Map<String, CidInfo> meetMap;
        meetMap= ObjectTools.listToMap(cidInfoList,FID);
        replyBody.setGot((long) cidInfoList.size());
        replyBody.setTotal((long) cidInfoList.size());
        replyBody.replySingleDataSuccessHttp(meetMap, response);
    }

    public Map<String, Long> sumCashValueByOwners(List<String> idList, ElasticsearchClient esClient) {
        if (idList == null || idList.isEmpty() || esClient == null) {
            return Collections.emptyMap();
        }

        try {
            SearchRequest searchRequest = new SearchRequest.Builder()
                .index(CASH)
                .query(q -> q
                    .bool(b -> b
                        .must(m -> m
                            .term(t -> t
                                .field(VALID)
                                .value(true)
                            )
                        )
                        .filter(f -> f
                            .terms(t -> t
                                .field(OWNER)
                                .terms(ft -> ft
                                    .value(idList.stream().map(FieldValue::of).collect(Collectors.toList()))
                                )
                            )
                        )
                    )
                )
                .aggregations("sum_by_owner", a -> a
                    .terms(t -> t
                        .field(OWNER)
                    )
                    .aggregations("value_sum", sa -> sa
                        .sum(s -> s
                            .field(VALUE)
                        )
                    )
                )
                .size(0)
                .build();

            SearchResponse<Void> response = esClient.search(searchRequest, Void.class);

            Map<String, Long> result = new HashMap<>();
            List<StringTermsBucket> buckets = response.aggregations()
                .get("sum_by_owner")
                .sterms()
                .buckets().array();

            for (StringTermsBucket bucket : buckets) {
                String owner = bucket.key();
                long sum = (long) bucket.aggregations().get("value_sum").sum().value();
                result.put(owner, sum);
            }

            return result;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    public void doCidInfoSearchRequest(ArrayList<Sort> sort, HttpServletRequest request, HttpServletResponse response, AuthType authType) {
        ReplyBody replier = new ReplyBody(settings);
        //Check authorization
        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        try {
            HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
            boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
            if (!isOk) {
                return;
            }
            replyBody = httpRequestChecker.getReplyBody();
            requestBody = httpRequestChecker.getRequestBody();
            List<Cid> meetCidList=null;
            List<Address> meetAddrList=null;
            List<CidInfo> cidInfoList = new ArrayList<>();
            List<String> idList;

            try{
                FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(replier, settings);
                meetAddrList = fcdslRequestHandler.doRequest(ADDRESS, sort, Address.class);
            }catch(Exception ignored){
            }

            if(meetAddrList==null)
                try{
                    FcdslRequestHandler fcdslRequestHandlerCid = new FcdslRequestHandler(replier, settings);
                    meetCidList = fcdslRequestHandlerCid.doRequest(CID, sort, Cid.class);
                }catch(Exception ignored){
                }

            if(meetAddrList!=null){
                idList = meetAddrList.stream().map(Address::getFid).collect(Collectors.toList());
                EsTools.MgetResult<Cid> result = EsTools.getMultiByIdList(esClient, CID, idList, Cid.class);
                meetCidList = result.getResultList();
                cidInfoList = CidInfo.mergeCidInfoList(meetAddrList, meetCidList);
            }else if(meetCidList!=null){
                idList = meetCidList.stream().map(Cid::getFid).collect(Collectors.toList());
                EsTools.MgetResult<Address> result = EsTools.getMultiByIdList(esClient, ADDRESS, idList, Address.class);
                meetAddrList = result.getResultList();
                cidInfoList = CidInfo.mergeCidInfoList(meetAddrList, meetCidList);
            }else {
                replier.replyHttp(CodeMessage.Code1011DataNotFound, null);
                return;
            }


            if (cidInfoList.size() == 0) {
                replier.replyHttp(CodeMessage.Code1011DataNotFound, null);
                return;
            }

            for (CidInfo cidInfo : cidInfoList) {
                cidInfo.reCalcWeight();
            }

            replier.setGot((long) cidInfoList.size());
            replier.setTotal((long) cidInfoList.size());
            replier.replySingleDataSuccessHttp(cidInfoList, response);
        } catch (Exception ignore) {
        }
    }

    private boolean ifForMapWithoutIds(boolean isForMap, ReplyBody replier, HttpServletResponse response, HttpRequestChecker httpRequestChecker) {
        if(isForMap)
            if (httpRequestChecker.getRequestBody().getFcdsl().getIds() == null) {
                replier.replyOtherErrorHttp("The parameter 'ids' is required.", response);
                return true;
            }
        return false;
    }


    public void doFidTxMaskRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType) {

        ReplyBody replier = new ReplyBody(settings);
        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        try {
            HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
            boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
            if (!isOk) {
                return;
            }
            replyBody = httpRequestChecker.getReplyBody();
            requestBody = httpRequestChecker.getRequestBody();
            String fid;
            try {
                fid = httpRequestChecker.getRequestBody().getFcdsl().getQuery().getTerms().getValues()[0];
            }catch (Exception e){
                replier.replyOtherErrorHttp("Failed to get the FID.", null);
                return;
            }

            //Set default sort.
            ArrayList<Sort> defaultSortList = Sort.makeSortList(HEIGHT, false, TX_ID, true, null, null);

            //Request
            String index = IndicesNames.TX_HAS;

            FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(replier, settings);
            List<TxHas> txHasList = fcdslRequestHandler.doRequest(index, defaultSortList, TxHas.class);
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
                replier.replyOtherErrorHttp("Failed to get TX info.", response);
                return;
            }
            if (txList.size()==0 ) {
                replier.replyHttp(CodeMessage.Code1011DataNotFound,response);
                return;
            }

            List<TxInfo> meetList = TxInfo.mergeTxAndTxHas(txList, txHasList);

            List<FidTxMask> fidTxMaskList = new ArrayList<>();
            for (TxInfo txInfo : meetList) {
                FidTxMask fidTxMask = FidTxMask.fromTxInfo(fid, txInfo);
                fidTxMaskList.add(fidTxMask);
            }
            //response
            replier.setGot((long) fidTxMaskList.size());
            replier.setTotal((long) fidTxMaskList.size());
            replier.replySingleDataSuccessHttp(fidTxMaskList, response);

        } catch (Exception e) {
            replier.replyOtherErrorHttp(e.getMessage(), response);
            e.printStackTrace();
        }
    }

    public void doTxInfoRequest(boolean isForMap, String idFieldName, HttpServletRequest request, HttpServletResponse response, AuthType authType) {

        ReplyBody replier = new ReplyBody(settings);
        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        try {
            HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
            boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
            if (!isOk) {
                return;
            }
            replyBody = httpRequestChecker.getReplyBody();
            requestBody = httpRequestChecker.getRequestBody();
            if (ifForMapWithoutIds(isForMap, replier, response, httpRequestChecker)) return;
            //Set default sort.
            ArrayList<Sort> defaultSortList = Sort.makeSortList(HEIGHT, false, TX_ID, true, null, null);

            //Request
            String index = IndicesNames.TX_HAS;

            FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(replier, settings);
            List<TxHas> txHasList = fcdslRequestHandler.doRequest(index, defaultSortList, TxHas.class);
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
                replier.replyOtherErrorHttp("Failed to get TX info.", response);
                return;
            }
            if (txList.size()==0 ) {
                replier.replyHttp(CodeMessage.Code1011DataNotFound,response);
                return;
            }

            List<TxInfo> meetList = TxInfo.mergeTxAndTxHas(txList, txHasList);


            Map<String, TxInfo> meetMap = null;
            if(isForMap)meetMap= ObjectTools.listToMap(meetList,idFieldName);

            //response
            replier.setGot((long) meetList.size());
            replier.setTotal((long) meetList.size());
            if(isForMap)replier.replySingleDataSuccessHttp(meetMap, response);
            else replier.replySingleDataSuccessHttp(meetList, response);

        } catch (Exception e) {
            replier.replyOtherErrorHttp(e.getMessage(), null);
        }
    }

    private List<Query> getQueryList(FcQuery query) {
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
            partQuery = getPartQuery(query.getPart());
            if(partQuery==null)return null;
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
            equalsQuery = getEqualQuery(query.getEquals());
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

    private <T> List<T> doIdsRequest(String index, Class<T> clazz) {

        List<String> idList = requestBody.getFcdsl().getIds();
        if(idList.size()> Constants.MaxRequestSize) {
            Integer data = new HashMap<String, Integer>().put("maxSize", Constants.MaxRequestSize);
            finalReplyJson = replyBody.reply(CodeMessage.Code1010TooMuchData, null,data);
            return null;
        }

        MgetResponse<T> result;
        try {
            result = esClient.mget(m -> m.index(index).ids(idList), clazz);
        }catch(Exception e){
            finalReplyJson = replyBody.reply(CodeMessage.Code1012BadQuery, null,null);
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
            finalReplyJson = replyBody.replyError(CodeMessage.Code1011DataNotFound);
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

    private BoolQuery getEqualQuery(Equals equals) {
        if(equals.getValues()==null|| equals.getFields()==null)return null;

        BoolQuery.Builder boolBuilder;

        List<FieldValue> valueList = new ArrayList<>();
        for(String str: equals.getValues()){
            if(str.isBlank())continue;
            if(str.contains(".")){
                try {
                    valueList.add(FieldValue.of(Double.parseDouble(str)));
                }catch(Exception e){
                    replyBody.replyHttp(CodeMessage.Code1012BadQuery, null);
                    return null;
                }
            }else{
                try {
                    valueList.add(FieldValue.of(Long.parseLong(str)));
                }catch(Exception e){
                    replyBody.replyHttp(CodeMessage.Code1012BadQuery, null);
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
            RangeQuery.Builder rangeBuilder = new RangeQuery.Builder();
            rangeBuilder.field(field);

            int count = 0;
            if(range.getGt()!=null){
                rangeBuilder.gt(JsonData.of(range.getGt()));
                count++;
            }
            if(range.getGte()!=null){
                rangeBuilder.gte(JsonData.of(range.getGte()));
                count++;
            }
            if(range.getLt()!=null){
                rangeBuilder.lt(JsonData.of(range.getLt()));
                count++;
            }
            if(range.getLte()!=null){
                rangeBuilder.lte(JsonData.of(range.getLte()));
                count++;
            }
            if(count==0)return null;

            queryList.add(new Query.Builder().range(rangeBuilder.build()).build());
        }

        bBuilder.must(queryList);
        return bBuilder.build();
    }

    private BoolQuery getPartQuery(Part part) {
        BoolQuery.Builder partBoolBuilder = new BoolQuery.Builder();
        boolean isCaseInSensitive;
        try{
           isCaseInSensitive = Boolean.parseBoolean(part.getIsCaseInsensitive());
        }catch(Exception e){
            replyBody.replyError(CodeMessage.Code1012BadQuery);
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
        BoolQuery.Builder termsBoolBuilder = new BoolQuery.Builder();

        List<Query> queryList = new ArrayList<>();
        for(String field:fields){
            TermsQuery tQuery = TermsQuery.of(t -> t
                    .field(field)
                    .terms(t1 -> t1
                            .value(valueList)
                    ));

            queryList.add(new Query.Builder().terms(tQuery).build());
        }
        termsBoolBuilder.should(queryList);
        return termsBoolBuilder;
    }

    private MatchAllQuery getMatchAllQuery() {
        MatchAllQuery.Builder queryBuilder = new MatchAllQuery.Builder();
        queryBuilder.queryName("all");
        return queryBuilder.build();
    }

    public void updateAddressBalances(Map<String, Long> fidBalanceMap, ElasticsearchClient esClient) {
        if (fidBalanceMap == null || fidBalanceMap.isEmpty() || esClient == null) {
            return;
        }

        try {
            // First get the current address documents
            List<String> fids = new ArrayList<>(fidBalanceMap.keySet());
            MgetResponse<Address> response = esClient.mget(m -> m
                .index(ADDRESS)
                .ids(fids), 
                Address.class
            );

            // Prepare bulk update
            List<BulkOperation> operations = new ArrayList<>();
            
            for (MultiGetResponseItem<Address> item : response.docs()) {
                if (!item.result().found()) {
                    continue;
                }
                
                Address address = item.result().source();
                if(address==null)continue;
                String fid = address.getFid();
                Long newBalance = fidBalanceMap.get(fid);
                
                if (newBalance != null) {
                    // Create update operation
                    operations.add(new BulkOperation.Builder()
                        .update(u -> u
                            .index(ADDRESS)
                            .id(fid)
                            .action(a -> a
                                .doc(JsonData.of(Map.of("balance", newBalance)))
                            )
                        )
                        .build()
                    );
                }
            }

            // Execute bulk update if there are operations
            if (!operations.isEmpty()) {
                esClient.bulk(b -> b
                    .operations(operations)
                );
            }

        } catch (Exception e) {
            // Handle or log error as needed
        }
    }

    public ReplyBody getReplyBody() {
        return replyBody;
    }

    public void setReplyBody(ReplyBody replyBody) {
        this.replyBody = replyBody;
    }

    public String getFinalReplyJson() {
        return finalReplyJson;
    }

    public void setFinalReplyJson(String finalReplyJson) {
        this.finalReplyJson = finalReplyJson;
    }
}
