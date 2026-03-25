package SwapHall;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.google.gson.Gson;
import config.Settings;
import constants.ApipApiNames;
import constants.CodeMessage;
import constants.Strings;
import data.apipData.Sort;
import data.fcData.ReplyBody;
import data.feipData.Service;
import data.feipData.ServiceType;
import feature.swap.SwapInfoData;
import feature.swap.SwapParams;
import feature.swap.SwapStateData;
import initial.Initiator;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import utils.EsUtils;
import utils.NumberUtils;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

import static constants.FieldNames.LAST;
import static constants.FieldNames.SID;
import static constants.IndicesNames.SERVICE;
import static constants.IndicesNames.SWAP_STATE;
import static constants.Values.ASC;

@WebServlet(ApipApiNames.SwapHallPath + ApipApiNames.SwapInfo)
public class SwapInfo extends HttpServlet {
    private final Settings settings = Initiator.settings;
    
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ReplyBody replier = new ReplyBody();

        JedisPool jedisPool = (JedisPool) settings.getClient(ServiceType.REDIS);
        try(Jedis jedis = jedisPool.getResource()) {
            replier.setBestHeight(Long.parseLong(jedis.get(Strings.BEST_HEIGHT)));
        }

        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);

        String sidStr = request.getParameter(SID);

        if(sidStr!=null){
            String[] sids = sidStr.split(",");
            EsUtils.MgetResult<SwapStateData> mgetResult = null;
            try {
                mgetResult = EsUtils.getMultiByIdList(esClient, SWAP_STATE, Arrays.asList(sids), SwapStateData.class);
            } catch (Exception e) {
                replier.replyOtherErrorHttp("Failed to mGet from ES.",response);
                return;
            }
            if(mgetResult.getResultList().isEmpty()){
                replier.replyHttp(CodeMessage.Code1011DataNotFound,response);
                return;
            }
            List<SwapStateData> swapStateList = mgetResult.getResultList();
            List<SwapInfoData> swapInfoList = makeSwapInfoList(swapStateList, response, replier, esClient);
            if (swapInfoList == null || swapInfoList.isEmpty()) {
                replier.replyHttp(CodeMessage.Code1011DataNotFound,response);
                return;
            }

            replier.setData(swapInfoList);
            replier.setTotal((long) swapInfoList.size());
            replier.setLast(null);
            replier.setGot((long) swapInfoList.size());
            replier.reply0SuccessHttp(swapInfoList,response);
            return;
        }

        SearchRequest.Builder searchBuilder = new SearchRequest.Builder();
        searchBuilder.index(SWAP_STATE);

        Sort sort = new Sort();
        sort.setField(SID);
        sort.setOrder(ASC);

        ArrayList<Sort> sortList = new ArrayList<>();
        sortList.add(sort);
        List<SortOptions> sortOptionsList = Sort.getSortList(sortList);

        searchBuilder.size(20);
        searchBuilder.sort(sortOptionsList);
        String lastStr = request.getParameter(LAST);
        if(lastStr!=null){
            String[] last = lastStr.split(",");
            searchBuilder.searchAfter(EsUtils.toFieldValueList(Arrays.asList(last)));
        }

        SearchResponse<SwapStateData> result = esClient.search(searchBuilder.build(), SwapStateData.class);
        long total = 0;
        if(result.hits().total()!=null) total = result.hits().total().value();
        if(result.hits().total()==null || total==0){
            replier.replyHttp(CodeMessage.Code1011DataNotFound,response);
            return;
        }
        String[] last = EsUtils.toStringList(result.hits().hits().get(result.hits().hits().size() - 1).sort()).toArray(new String[0]);

        List<SwapStateData> swapStateList = new ArrayList<>();
        for(Hit<SwapStateData> hit : result.hits().hits()){
            if(hit.source()==null)continue;
            swapStateList.add(hit.source());
        }

        List<SwapInfoData> swapInfoList = makeSwapInfoList(swapStateList, response, replier, esClient);
        if (swapInfoList == null || swapInfoList.isEmpty()) {
            replier.replyHttp(CodeMessage.Code1011DataNotFound,response);
            return;
        }

        replier.setData(swapInfoList);
        replier.setTotal(total);
        replier.setLast(List.of(last));
        replier.setGot((long) swapInfoList.size());
        replier.reply0SuccessHttp(swapInfoList,response);
    }

    @Nullable
    private List<SwapInfoData> makeSwapInfoList(List<SwapStateData> swapStateList, HttpServletResponse response, ReplyBody replier, ElasticsearchClient esClient) {
        Gson gson = new Gson();
        List<String> sidList = new ArrayList<>();
        Map<String, SwapStateData> swapStateMap = new HashMap<>();

        for(SwapStateData swapState : swapStateList){
            sidList.add(swapState.getSid());
            swapStateMap.put(swapState.getSid(),swapState);
        }

        EsUtils.MgetResult<Service> mgetResult1 = null;
        try {
            mgetResult1 = EsUtils.getMultiByIdList(esClient, SERVICE, sidList, Service.class);
        } catch (Exception e) {
            replier.replyOtherErrorHttp("Failed to mGet from ES.",response);
            return null;
        }

        if(mgetResult1.getResultList().isEmpty()){
            replier.replyHttp(CodeMessage.Code1011DataNotFound,response);
            return null;
        }

        List<SwapInfoData> swapInfoList = new ArrayList<>();

        List<Service> swapServiceList = mgetResult1.getResultList();

        for(Service service:swapServiceList){
            if(service.getActive()){
                SwapParams swapParams = gson.fromJson(gson.toJson(service.getParams()),SwapParams.class);
                if(swapParams!=null){
                    service.setParams(swapParams);
                    SwapInfoData swapInfo = makeSwapInfo(swapStateMap.get(service.getId()),service,swapParams);
                    swapInfoList.add(swapInfo);
                }
            }
        }
        if(swapInfoList.isEmpty()){
            replier.replyHttp(CodeMessage.Code1011DataNotFound,response);
            return null;
        }
        return swapInfoList;
    }

    private SwapInfoData makeSwapInfo(SwapStateData swapState, Service service, SwapParams swapParams) {
        SwapInfoData swapInfoData = new SwapInfoData();

        swapInfoData.setSid(swapState.getSid());

        swapInfoData.setName(service.getStdName());
        swapInfoData.setOwner(service.getOwner());
        swapInfoData.settRate(String.valueOf(service.gettRate()));
        swapInfoData.settCdd(String.valueOf(service.gettCdd()));

        swapInfoData.setgTick(swapParams.getgTick());
        swapInfoData.setmTick(swapParams.getmTick());
        swapInfoData.setgAddr(swapParams.getgAddr());
        swapInfoData.setmAddr(swapParams.getmAddr());
        swapInfoData.setgConfirm(swapParams.getgConfirm());
        swapInfoData.setmConfirm(swapParams.getmConfirm());
        swapInfoData.setSwapFee(swapParams.getSwapFee());
        swapInfoData.setServiceFee(swapParams.getServiceFee());
        swapInfoData.setgWithdrawFee(swapParams.getgWithdrawFee());
        swapInfoData.setmWithdrawFee(swapParams.getmWithdrawFee());

        swapInfoData.setgSum(swapState.getgSum());
        swapInfoData.setmSum(swapState.getmSum());
        swapInfoData.setgPendingSum(swapState.getgPendingSum());
        swapInfoData.setmPendingSum(swapState.getmPendingSum());

        double dM = 1-Double.parseDouble(swapInfoData.getSwapFee())-Double.parseDouble(swapInfoData.getServiceFee());
        double dG = ammCalculator(swapState.getmSum() + swapState.getmPendingSum(), swapState.getgSum() + swapState.getgPendingSum(), dM);
        double price = NumberUtils.roundDouble8(1/ dG);

        swapInfoData.setPrice(price);
        swapInfoData.setLastTime(swapState.getLastTime());

        return swapInfoData;
    }

    public static double ammCalculator(double x, double y, double dX){
        return (y*dX)/(x+dX);
    }
}
