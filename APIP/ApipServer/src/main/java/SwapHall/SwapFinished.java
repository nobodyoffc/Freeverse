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
import constants.Strings;
import data.apipData.Sort;
import data.fcData.ReplyBody;
import data.feipData.Service;
import feature.swap.SwapAffair;
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

import static constants.FieldNames.DESC;
import static constants.FieldNames.SID;
import static constants.IndicesNames.SWAP_FINISHED;
import static constants.Values.ASC;

@WebServlet(ApipApiNames.SwapHallPath + ApipApiNames.SwapFinished)
public class SwapFinished extends HttpServlet {
    private final Settings settings = Initiator.settings;
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ReplyBody replier = new ReplyBody();

        JedisPool jedisPool = (JedisPool) settings.getClient(Service.ServiceType.REDIS);
        try(Jedis jedis = jedisPool.getResource()) {
            replier.setBestHeight(Long.parseLong(jedis.get(Strings.BEST_HEIGHT)));
        }

        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);

        String sid = request.getParameter(SID);
        if(sid==null){
            replier.replyOtherErrorHttp("SID is required.",response);
            return;
        }
        String lastStr = request.getParameter(FieldNames.LAST);

        SearchRequest.Builder searchBuilder = new SearchRequest.Builder();

        List<SortOptions> sortOptionsList = Sort.makeTwoFieldsSort(FieldNames.GET_TIME,DESC,FieldNames.ID,ASC);

        searchBuilder.index(SWAP_FINISHED);
        searchBuilder.sort(sortOptionsList);
        searchBuilder.size(20);
        if(lastStr!=null) {
            String[] last = lastStr.split(",");
            searchBuilder.searchAfter(Arrays.asList(last));
        }

        Query query = EsUtils.getTermsQuery(SID,sid.toLowerCase());

        searchBuilder.query(query);
        SearchRequest searchRequest = searchBuilder.build();
        SearchResponse<SwapAffair> result = esClient.search(searchRequest, SwapAffair.class);

        if(result==null||result.hits().total()==null){
            replier.replyOtherErrorHttp("Searching ES wrong.",response);
            return;
        }
        if(result.hits().total().value()==0){
            replier.replyHttp(CodeMessage.Code1011DataNotFound,response);
            return;
        }
        String[] last = result.hits().hits().get(result.hits().hits().size() - 1).sort().toArray(new String[0]);
        long total = result.hits().total().value();
        List<Hit<SwapAffair>> hitList = result.hits().hits();
        List<SwapAffair> swapAffairList = new ArrayList<>();
        for(Hit<SwapAffair> hit : hitList){
            swapAffairList.add(hit.source());
        }

        if(swapAffairList.isEmpty()){
            replier.replyHttp(CodeMessage.Code1011DataNotFound,response);
            return;
        }

        replier.setData(swapAffairList);
        replier.setTotal(total);
        replier.setLast(List.of(last));
        replier.setGot((long) swapAffairList.size());
        replier.reply0SuccessHttp(swapAffairList,response);
    }
}
