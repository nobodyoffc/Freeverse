package APIP8V1_Group;

import apip.apipData.Sort;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.ApiNames;
import constants.IndicesNames;
import fcData.FcReplierHttp;
import feip.feipData.Group;
import initial.Initiator;
import tools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.FcdslRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static constants.FieldNames.*;


@WebServlet(name = ApiNames.GroupSearch, value = "/"+ApiNames.SN_8+"/"+ApiNames.Version1 +"/"+ApiNames.GroupSearch)
public class GroupSearch extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(T_CDD,false,GID,true,null,null);
        doGroupSearchRequest(Initiator.sid,null,null,null,null, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(T_CDD,false,GID,true,null,null);
        doGroupSearchRequest(Initiator.sid,null,null,null,null, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }

    public static void doGroupSearchRequest(String sid, String filterField, String filterValue, String exceptField, String exceptValue, List<Sort> sort, HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) {
        FcReplierHttp replier = new FcReplierHttp(sid,response);
        try (Jedis jedis = jedisPool.getResource()) {
            List<Group> meetList = FcdslRequestHandler.doRequestForList(sid, IndicesNames.GROUP, Group.class, filterField, filterValue, exceptField, exceptValue, sort, request, authType, esClient, replier, jedis, Initiator.sessionHandler);
            if (meetList == null) return;
            for(Group group :meetList){
                group.setMembers(null);
            }
            replier.reply0SuccessHttp(meetList, jedis, null);
        }
    }
}