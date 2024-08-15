package api;

import apip.apipData.Sort;
import clients.fcspClient.DiskItem;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import constants.ApiNames;
import constants.ReplyCodeMessage;
import initial.Initiator;
import javaTools.http.AuthType;
import fcData.FcReplier;
import redis.clients.jedis.Jedis;
import server.FcdslRequestHandler;
import server.RequestCheckResult;
import server.RequestChecker;
import server.Settings;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import static constants.FieldNames.*;
import static constants.Strings.DATA;

@WebServlet(name = "list", value ="/"+ApiNames.Version1 +"/"+ApiNames.LIST)
public class List extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request, response, authType);
    }

    private java.util.List<DiskItem> doGetRequest(HttpServletRequest request, FcReplier replier, Jedis jedis) throws IOException {

        String last = request.getParameter(LAST);
        String sort = request.getParameter(SORT);
        String order = request.getParameter(ORDER);
        String size = request.getParameter(SIZE);

        SearchRequest.Builder builder = new SearchRequest.Builder();
        builder.index(Settings.addSidBriefToName(Initiator.sid,DATA));
        if(sort!=null) {
            if(order!=null){
                builder.sort(s -> s.field(f -> f.field(sort).order(SortOrder.valueOf(order))));
            }else builder.sort(s -> s.field(f -> f.field(sort).order(SortOrder.Asc)));
        }
        if(size!=null)builder.size(Integer.valueOf(size));
        else builder.size(50);
        if(last!=null){
            java.util.List<String> lasts = java.util.List.of(last.split(","));
            builder.searchAfter(lasts);
        }
//        builder.size(1);
        SearchResponse<DiskItem> result;
        try {
            SearchRequest esRequest = builder.build();
            result = Initiator.esClient.search(esRequest, DiskItem.class);
        }catch (Exception e){
            e.printStackTrace();
            replier.replyOtherError(e.getMessage(),null,jedis);
            return null;
        }
        if(result==null || result.hits()==null||result.hits().total()==null){
            replier.replyOtherError("Failed to get data from ES.",null,jedis);
            return null;
        }

        if(result.hits().total().value()==0){
            replier.reply(ReplyCodeMessage.Code1011DataNotFound, null,jedis);
        }

        java.util.List<DiskItem> diskItemList = new ArrayList<>();
        java.util.List<String> newLast = null;
        for(Hit<DiskItem> hit: result.hits().hits()){
            DiskItem diskItem = hit.source();
            if(diskItem ==null)continue;
            diskItemList.add(diskItem);
            newLast = hit.sort();
        }

        if(newLast!=null) {
            replier.setLast(newLast);
        }
        return diskItemList;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request, response, authType);
    }

    private static void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType) {
        FcReplier replier = new FcReplier(Initiator.sid, response);

        //Check authorization
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(Initiator.sid, request, replier, authType, jedis, false);
            if (requestCheckResult==null){
                return;
            }
            //Do request
            FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(requestCheckResult.getRequestBody(), response,replier,Initiator.esClient);
            ArrayList<Sort> defaultSortList=null;

            if(requestCheckResult.getRequestBody()==null || requestCheckResult.getRequestBody().getFcdsl()==null||requestCheckResult.getRequestBody().getFcdsl().getSort()==null)
                defaultSortList = Sort.makeSortList(SINCE, true, DID, true, null, null);
            java.util.List<DiskItem> meetList = fcdslRequestHandler.doRequest(Settings.addSidBriefToName(Initiator.sid, DATA), defaultSortList, DiskItem.class, jedis);

            if(meetList==null){
                replier.reply(ReplyCodeMessage.Code1011DataNotFound,null,jedis);
                return;
            }
            replier.reply0Success(meetList,jedis, null);
        }
    }
}
