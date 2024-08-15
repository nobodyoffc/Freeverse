package clients.esClient;

import appTools.Menu;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.*;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.BulkRequest.Builder;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.DeleteIndexResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import constants.Strings;

import fch.fchData.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static constants.IndicesNames.BLOCK;

public class EsTools {

    public static final int READ_MAX = 5000;
    public static final int WRITE_MAX = 3000;
    final static Logger log = LoggerFactory.getLogger(EsTools.class);
    public static void recreateIndex(String index, String mappingJsonStr, ElasticsearchClient esClient) throws InterruptedException {

        if(esClient==null) {
            System.out.println("Create a Java client for ES first.");
            return;
        }
        try {
            DeleteIndexResponse req = esClient.indices().delete(c -> c.index(index));

            if(req.acknowledged()) {
                log.debug("Index {} was deleted.", index);
            }
        }catch(ElasticsearchException | IOException e) {
            log.debug("Deleting index {} failed.", index,e);
        }

        TimeUnit.SECONDS.sleep(2);

        createIndex(esClient, index, mappingJsonStr);
    }

    public static Block getBestBlock(ElasticsearchClient esClient) throws ElasticsearchException, IOException {
        SearchResponse<Block> result = esClient.search(s->s
                        .index(BLOCK)
                        .size(1)
                        .sort(so->so.field(f->f.field("height").order(SortOrder.Desc)))
                , Block.class);
        return result.hits().hits().get(0).source();
    }
    public static void createIndex(ElasticsearchClient esClient, String index, String mappingJsonStr) {

        InputStream orderJsonStrIs = new ByteArrayInputStream(mappingJsonStr.getBytes());
        try {
            CreateIndexResponse req = esClient.indices().create(c -> c.index(index).withJson(orderJsonStrIs));
            orderJsonStrIs.close();
            System.out.println(req.toString());
            if(req.acknowledged()) {
                log.debug("Index {} was created.", index);
            }else {
                log.debug("Creating index {} failed.", index);
            }
        }catch(ElasticsearchException | IOException e) {
            log.debug("Creating index {} failed.", index,e);
        }
    }
    public static boolean noSuchIndex(ElasticsearchClient esClient, String index){
        BooleanResponse result;
        try {
            result = esClient.indices().exists(e -> e.index(index));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return !result.value();
    }

    public static void checkEsIndices(ElasticsearchClient esClient, Map<String,String> nameMappingMap) {
        for(String name:nameMappingMap.keySet()){
            if(noSuchIndex(esClient,name)){
                createIndex(esClient, name, nameMappingMap.get(name));
            }
        }
    }

    public static void recreateApipIndex(BufferedReader br, ElasticsearchClient esClient, String index, String mappingJsonStr) {
        try {
            recreateIndex(index, mappingJsonStr, esClient);
        } catch (InterruptedException e) {
            log.debug("Recreate index {} wrong.",index);
        }
        Menu.anyKeyToContinue(br);
    }

    public static <T> T getBestOne(ElasticsearchClient esClient, String index, String orderField, SortOrder sortOrder, Class<T> clazz) throws IOException {
        if (esClient == null) {
            System.out.println("Start a ES client first.");
            return null;
        }
        SearchRequest.Builder builder = new SearchRequest.Builder();
        builder.index(index);
        builder.sort(s -> s.field(f -> f.field(orderField).order(sortOrder)));
        builder.size(1);
        SearchRequest request = builder.build();
        SearchResponse<T> result = esClient.search(request, clazz);
        if (result.hits().total().value() == 0) return null;
        T bestT = result.hits().hits().get(0).source();
        return bestT;
    }

    public static <T> ArrayList<T> rangeGt(ElasticsearchClient esClient,
                                           String index,
                                           String queryField,
                                           long queryValue,
                                           String sortField,
                                           SortOrder order,
                                           String filterField,
                                           String filterValue,
                                           Class<T> clazz) {
        ArrayList<T> list = new ArrayList<T>();
        SearchResponse<T> result = null;
        int size = READ_MAX;
        try {
            result = esClient.search(s -> s
                            .index(index)
                            .sort(so -> so.field(f -> f
                                    .field(sortField)
                                    .order(order)))
                            .size(size)
                            .query(q -> q
                                    .bool(b -> b
                                            .filter(f -> f
                                                    .term(t -> t
                                                            .field(filterField).value(filterValue)))
                                            .must(m -> m
                                                    .range(r -> r
                                                            .field(queryField).gt(JsonData.of(queryValue))))))
                    , clazz);
            if (result.hits().total().value() != 0) {
                List<Hit<T>> hitList = result.hits().hits();
                for (Hit<T> hit : hitList) {
                    list.add(hit.source());
                }
            }
            long hitSize = result.hits().total().value();
            if (hitSize == 0) return list;
            List<String> last = result.hits().hits().get((int) (hitSize - 1)).sort();

            while (hitSize >= size) {
                List<String> finalLast = last;
                result = esClient.search(s -> s
                                .index(index)
                                .sort(so -> so.field(f -> f
                                        .field(sortField)
                                        .order(order)))
                                .size(size)
                                .searchAfter(finalLast)
                                .query(q -> q
                                        .bool(b -> b
                                                .filter(f -> f
                                                        .term(t -> t
                                                                .field(filterField).value(filterValue)))
                                                .must(m -> m
                                                        .range(r -> r
                                                                .field(queryField).gt(JsonData.of(queryValue))))))
                        , clazz);
                if (result.hits().total().value() != 0) {
                    List<Hit<T>> hitList = result.hits().hits();
                    for (Hit<T> hit : hitList) {
                        list.add(hit.source());
                    }
                }
                hitSize = result.hits().total().value();
                if (hitSize == 0) break;
                last = result.hits().hits().get((int) (hitSize - 1)).sort();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static <T> T getById(ElasticsearchClient esClient,
                                String index,
                                String id,
                                Class<T> clazz) throws ElasticsearchException, IOException {

        GetResponse<T> result = esClient.get(g -> g.index(index).id(id), clazz);
        if (result.found() == false) return null;

        return result.source();
    }

    public static <T> MgetResult<T> getMultiByIdList(
            ElasticsearchClient esClient,
            String index,
            List<String> idList,
            Class<T> classType
    ) throws Exception {
        MgetResult<T> result = new MgetResult<T>();

        ArrayList<T> resultList = new ArrayList<T>();
        ArrayList<String> missList = new ArrayList<String>();

        if (idList.size() > READ_MAX) {

            Iterator<String> iter = idList.iterator();
            for (int i = 0; i < idList.size() / READ_MAX + 1; i++) {

                ArrayList<String> idSubList = new ArrayList<String>();
                for (int j = 0; j < idList.size() - i * READ_MAX && j < READ_MAX; j++) {
                    idSubList.add(iter.next());
                }

                MgetResult<T> mgetResult = mgetWithNull(esClient, index, idSubList, classType);

                resultList.addAll(mgetResult.getResultList());
                missList.addAll(mgetResult.getMissList());
            }
            result.setResultList(resultList);
            result.setMissList(missList);
        } else {
            result = mgetWithNull(esClient, index, idList, classType);
        }
        return result;
    }

    private static <T> MgetResult<T> mgetWithNull(ElasticsearchClient esClient, String index, List<String> idList, Class<T> classType) throws ElasticsearchException, IOException {

        ArrayList<T> resultList = new ArrayList<T>();
        ArrayList<String> missList = new ArrayList<String>();

        MgetRequest.Builder mgetRequestBuilder = new MgetRequest.Builder();
        mgetRequestBuilder
                .index(index)
                .ids(idList);
        MgetRequest mgetRequest = mgetRequestBuilder.build();
        MgetResponse<T> mgetResponse = null;

        mgetResponse = esClient.mget(mgetRequest, classType);


        List<MultiGetResponseItem<T>> items = mgetResponse.docs();

        ListIterator<MultiGetResponseItem<T>> iter = items.listIterator();
        while (iter.hasNext()) {
            MultiGetResponseItem<T> item = iter.next();
            if (item.result().found()) {
                resultList.add(item.result().source());
            } else {
                missList.add(item.result().id());
            }
        }
        MgetResult<T> result = new MgetResult<T>();
        result.setMissList(missList);
        result.setResultList(resultList);

        return result;
    }

    public static <T> ArrayList<T> getListByTermsSinceHeight(ElasticsearchClient esClient, String index, String termField, String[] termValues, long sinceHeight, String sortField, SortOrder order, Class<T> clazz) throws IOException {

        List<FieldValue> values = new ArrayList<>();
        for (String v : termValues) {
            values.add(FieldValue.of(v));
        }

        SearchResponse<T> result = esClient.search(s -> s.index(index)
                .query(q -> q.bool(b -> b
                        .must(m1 -> m1.terms(t -> t.field(termField).terms(t1 -> t1.value(values))))
                        .must(m2 -> m2.range(r -> r.field(Strings.BIRTH_HEIGHT).gte(JsonData.of(sinceHeight))))
                ))
                .size(EsTools.READ_MAX)
                .sort(s1 -> s1
                        .field(f -> f
                                .field(sortField).order(order)
                        )), clazz);

        if (result.hits().total().value() == 0) return null;

        List<String> lastSort = result.hits().hits().get(result.hits().hits().size() - 1).sort();

        ArrayList<T> itemList = new ArrayList<T>();

        for (Hit<T> hit : result.hits().hits()) {
            itemList.add(hit.source());
        }
        while (true) {

            if (result.hits().total().value() == EsTools.READ_MAX) {

                List<String> lastSort1 = lastSort;

                result = esClient.search(s -> s.index(index)
                        .query(q -> q.terms(t -> t.field(termField).terms(t1 -> t1.value(values))))
                        .size(EsTools.READ_MAX)
                        .sort(s1 -> s1
                                .field(f -> f
                                        .field(sortField).order(SortOrder.Asc)
                                ))
                        .searchAfter(lastSort1), clazz);

                if (result.hits().total().value() == 0) break;
                lastSort = result.hits().hits().get(result.hits().hits().size() - 1).sort();
                for (Hit<T> hit : result.hits().hits()) {
                    itemList.add(hit.source());
                }
            } else break;
        }
        return itemList;
    }

    public static <T> ArrayList<T> getListSinceHeight(ElasticsearchClient esClient, String index, String field, long height, Class<T> clazz) throws IOException {

        SearchResponse<T> result = esClient.search(s -> s.index(index)
                .query(q -> q.range(r -> r.field(field).gt(JsonData.of(height))))
                .size(EsTools.READ_MAX)
                .sort(s1 -> s1
                        .field(f -> f
                                .field(field).order(SortOrder.Asc)
                        )), clazz);

        if (result.hits().total().value() == 0) return null;

        List<String> lastSort = result.hits().hits().get(result.hits().hits().size() - 1).sort();

        ArrayList<T> itemList = new ArrayList<T>();

        for (Hit<T> hit : result.hits().hits()) {
            itemList.add(hit.source());
        }
        while (true) {

            if (result.hits().total().value() == EsTools.READ_MAX) {

                List<String> lastSort1 = lastSort;

                result = esClient.search(s -> s.index(index)
                        .query(q -> q.range(r -> r.field(field).gt(JsonData.of(height))))
                        .size(EsTools.READ_MAX)
                        .sort(s1 -> s1
                                .field(f -> f
                                        .field(field).order(SortOrder.Asc)
                                ))
                        .searchAfter(lastSort1), clazz);

                if (result.hits().total().value() == 0) break;
                lastSort = result.hits().hits().get(result.hits().hits().size() - 1).sort();
                for (Hit<T> hit : result.hits().hits()) {
                    itemList.add(hit.source());
                }
            } else break;
        }
        return itemList;
    }

    public static <T> ArrayList<T> getAllList(ElasticsearchClient esClient, String index, String sortField, SortOrder order, Class<T> clazz) throws IOException {

        SearchResponse<T> result = esClient.search(s -> s.index(index)
                        .query(q -> q.matchAll(m -> m))
                        .size(EsTools.READ_MAX)
//                .sort(s1 -> s1
//                        .field(f -> f
//                                .field(sortField).order(order)
//                        ))
                , clazz);

        if (result.hits().total().value() == 0) return null;

        List<String> lastSort = result.hits().hits().get(result.hits().hits().size() - 1).sort();

        ArrayList<T> itemList = new ArrayList<T>();

        for (Hit<T> hit : result.hits().hits()) {
            itemList.add(hit.source());
        }
        while (true) {

            if (result.hits().total().value() == EsTools.READ_MAX) {

                List<String> lastSort1 = lastSort;

                result = esClient.search(s -> s.index(index)
                        .query(q -> q.matchAll(m -> m))
                        .size(EsTools.READ_MAX)
                        .sort(s1 -> s1
                                .field(f -> f
                                        .field(sortField).order(order)
                                ))
                        .searchAfter(lastSort1), clazz);

                if (result.hits().total().value() == 0) break;
                lastSort = result.hits().hits().get(result.hits().hits().size() - 1).sort();
                for (Hit<T> hit : result.hits().hits()) {
                    itemList.add(hit.source());
                }
            } else break;
        }
        return itemList;
    }

    public static void deleteIndex(ElasticsearchClient esClient, String indexName) throws IOException {
        try {
            DeleteIndexResponse req = esClient.indices().delete(c -> c.index(indexName));
            if (req.acknowledged()) {
                System.out.println("Index " + indexName + " deleted.");
            }
        } catch (ElasticsearchException e) {
            System.out.println("Index " + indexName + " does not exist.");
        }
    }

    public static <T> BulkResponse bulkWriteList(ElasticsearchClient esClient
            , String indexT, List<T> tList
            , List<String> idList
            , Class<T> classT) throws Exception {
        if (tList.isEmpty()) return null;
        BulkResponse response = null;

        Iterator<T> iter = tList.iterator();
        Iterator<String> iterId = idList.iterator();
        for (int i = 0; i < tList.size() / READ_MAX + 1; i++) {

            BulkRequest.Builder br = new BulkRequest.Builder();

            for (int j = 0; j < READ_MAX && i * READ_MAX + j < tList.size(); j++) {
                T t = iter.next();
                String tid = iterId.next();
                br.operations(op -> op.index(in -> in
                        .index(indexT)
                        .id(tid)
                        .document(t)));
            }
            response = bulkWithBuilder(esClient, br);
            if (response.errors()) return response;
        }
        return response;
    }

    public static BulkResponse bulkDeleteList(ElasticsearchClient esClient, String index, ArrayList<String> idList) throws Exception {
        if (idList == null || idList.isEmpty()) return null;

        BulkResponse response = null;

        BulkRequest.Builder br = new BulkRequest.Builder();
        br.timeout(t -> t.time("600s"));

        for (int i = 0; i < idList.size(); i++) {
            String id = idList.get(i);
            br.operations(op -> op.delete(in -> in
                    .index(index)
                    .id(id)));

            if (i != 0 && i % WRITE_MAX == 0) {
                response = esClient.bulk(br.build());
            }
        }

        response = esClient.bulk(br.build());

        return response;
    }

    public static BulkResponse bulkWithBuilder(ElasticsearchClient esClient, Builder br) throws Exception {
        br.timeout(t -> t.time("600s"));
        return esClient.bulk(br.build());
    }

    public static <T> List<T> getHistsForReparse(ElasticsearchClient esClient, String index, String termsField, ArrayList<String> itemIdList, Class<T> clazz) throws ElasticsearchException, IOException {
        // TODO Auto-generated method stub
        List<FieldValue> itemValueList = new ArrayList<FieldValue>();
        for (String v : itemIdList) {
            itemValueList.add(FieldValue.of(v));
        }

        List<SortOptions> soList = new ArrayList<>();
        FieldSort fs1 = FieldSort.of(f -> f.field("height").order(SortOrder.Asc));
        SortOptions so1 = SortOptions.of(s -> s.field(fs1));
        soList.add(so1);

        FieldSort fs2 = FieldSort.of(f -> f.field("index").order(SortOrder.Asc));
        SortOptions so2 = SortOptions.of(s -> s.field(fs2));
        soList.add(so2);

        List<String> lastSort = new ArrayList<String>();

        SearchResponse<T> result = esClient.search(s -> s.index(index)
                .query(q -> q.terms(t -> t.field(termsField).terms(t1 -> t1.value(itemValueList))))
                .size(EsTools.READ_MAX)
                .sort(soList), clazz);

        if (result == null || result.hits().total().value() == 0) return null;

        lastSort = result.hits().hits().get(result.hits().hits().size() - 1).sort();

        List<T> historyList = new ArrayList<T>();

        for (Hit<T> hit : result.hits().hits()) {
            historyList.add(hit.source());
        }
        while (true) {

            if (result.hits().total().value() == EsTools.READ_MAX) {

                List<String> lastSort1 = lastSort;

                result = esClient.search(s -> s.index(index)
                        .query(q -> q.terms(t -> t.field(termsField).terms(t1 -> t1.value(itemValueList))))
                        .size(EsTools.READ_MAX)
                        .sort(soList)
                        .searchAfter(lastSort1), clazz);


                if (result.hits().total().value() == 0) break;

                lastSort = result.hits().hits().get(result.hits().hits().size() - 1).sort();

                for (Hit<T> hit : result.hits().hits()) {
                    historyList.add(hit.source());
                }
            } else break;
        }
        return historyList;
    }

    public static Query getTermsQuery(String field, String value) {
        TermsQuery.Builder termsBuilder = new TermsQuery.Builder();
        termsBuilder.field(field);
        FieldValue fieldValue = FieldValue.of(value);
        List<FieldValue> fieldValueList = new ArrayList<>();
        fieldValueList.add(fieldValue);
        termsBuilder.terms(t -> t.value(fieldValueList));
        TermsQuery termsQuery = termsBuilder.build();
        return new Query.Builder().terms(termsQuery).build();
    }

    public static class MgetResult<E> {
        private List<String> missList;
        private List<E> resultList;

        public List<String> getMissList() {
            return missList;
        }

        public void setMissList(List<String> missList) {
            this.missList = missList;
        }

        public List<E> getResultList() {
            return resultList;
        }

        public void setResultList(List<E> resultList) {
            this.resultList = resultList;
        }
    }
}
