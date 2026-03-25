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
import constants.Strings;
import data.apipData.Sort;
import data.fcData.ReplyBody;
import data.feipData.ServiceType;
import feature.swap.SwapPriceData;
import initial.Initiator;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import utils.EsUtils;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static constants.FieldNames.*;
import static constants.IndicesNames.SWAP_PRICE;
import static constants.Values.ASC;

@WebServlet(ApipApiNames.SwapHallPath + ApipApiNames.SwapPrice)
public class SwapPrices extends HttpServlet {
    private final Settings settings = Initiator.settings;

    /*
        - https://cid.cash/APIP/swapHall/v1/swapPrice
        - https://cid.cash/APIP/swapHall/v1/swapPrice?sid=c12aef3c9341a8ab135bd412e1ad798f480519004649871d0a59e7ba799a6f06
        - https://cid.cash/APIP/swapHall/v1/swapPrice?sid=c12aef3c9341a8ab135bd412e1ad798f480519004649871d0a59e7ba799a6f06&startTime=1708157907767&endTime=1713237231924&last=1712476822738,c12aef3c9341a8ab135bd412e1ad798f480519004649871d0a59e7ba799a6f06
        - https://cid.cash/APIP/swapHall/v1/swapPrice?gTick=fch&mTick=doge&startTime=1708157907767&endTime=1713237231924&last=1712476822738,c12aef3c9341a8ab135bd412e1ad798f480519004649871d0a59e7ba799a6f06&last=1712476822738,c12aef3c9341a8ab135bd412e1ad798f480519004649871d0a59e7ba799a6f06
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ReplyBody replier = new ReplyBody();

        JedisPool jedisPool = (JedisPool) settings.getClient(ServiceType.REDIS);
        try(Jedis jedis = jedisPool.getResource()) {
            replier.setBestHeight(Long.parseLong(jedis.get(Strings.BEST_HEIGHT)));
        }

        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);

        String sid = request.getParameter(SID);

        String gTick = request.getParameter(G_TICK);
        String mTick = request.getParameter(M_TICK);

        String lastStr = request.getParameter(FieldNames.LAST);
        String startTime = request.getParameter(START_TIME);
        String endTime = request.getParameter(END_TIME);
        String size = request.getParameter(SIZE);

        SearchRequest.Builder searchBuilder = new SearchRequest.Builder();

        List<SortOptions> sortOptionsList = Sort.makeTwoFieldsSort(FieldNames.TIME,DESC,FieldNames.SID,ASC);

        searchBuilder.index(SWAP_PRICE);
        searchBuilder.sort(sortOptionsList);
        if(size!=null)searchBuilder.size(Integer.valueOf(size));
        else searchBuilder.size(50);
        if(lastStr!=null) {
            String[] last = lastStr.split(",");
            searchBuilder.searchAfter(EsUtils.toFieldValueList(Arrays.asList(last)));
        }

        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        List<Query> queryList=new ArrayList<>();
        if(sid!=null) {
            Query query = EsUtils.getTermsQuery(SID,sid.toLowerCase());
            queryList.add(query);
        }else {
            if (gTick != null) {
                Query query = EsUtils.getTermsQuery(G_TICK, gTick.toLowerCase());
                queryList.add(query);
            }
            if (mTick != null) {
                Query query = EsUtils.getTermsQuery(M_TICK, mTick.toLowerCase());
                queryList.add(query);
            }
        }

        if(startTime!=null||endTime!=null){
            RangeQuery.Builder rqb = new RangeQuery.Builder();
            rqb.field(TIME);
            if(startTime!=null)
                rqb.gte(JsonData.of(Long.parseLong(startTime)));
            if(endTime!=null)
                rqb.lt(JsonData.of(Long.parseLong(endTime)));
            Query query = new Query.Builder().range(rqb.build()).build();
            queryList.add(query);
        }

        BoolQuery boolQuery = boolBuilder.must(queryList).build();

        Query query = new Query(boolQuery);
        searchBuilder.query(query);
        SearchRequest searchRequest = searchBuilder.build();
        SearchResponse<SwapPriceData> result = esClient.search(searchRequest, SwapPriceData.class);

        long total=0;
        if(result!=null && result.hits().total()!=null)
            total=result.hits().total().value();
        if(total==0){
            replier.replyHttp(CodeMessage.Code1011DataNotFound,response);
            return;
        }

        String[] last = null;
        if(result.hits().hits().size() >0){
            last = EsUtils.toStringList(result.hits().hits().get(result.hits().hits().size() - 1).sort()).toArray(new String[0]);
        }

        List<Hit<SwapPriceData>> hitList = result.hits().hits();
        List<SwapPriceData> swapPriceList = new ArrayList<>();
        for(Hit<SwapPriceData> hit : hitList){
            swapPriceList.add(hit.source());
        }

        if(swapPriceList.isEmpty()){
            replier.replyHttp(CodeMessage.Code1011DataNotFound,response);
            return;
        }

        replier.setData(swapPriceList);
        replier.setTotal(total);
        replier.setLast(last != null ? List.of(last) : null);
        replier.setGot((long) swapPriceList.size());
        replier.reply0SuccessHttp(swapPriceList,response);
    }

}
