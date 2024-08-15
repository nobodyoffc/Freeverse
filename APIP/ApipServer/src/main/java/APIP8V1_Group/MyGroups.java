package APIP8V1_Group;

import apip.apipData.Sort;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.ApiNames;
import constants.IndicesNames;
import fcData.FcReplier;
import feip.feipData.Group;
import initial.Initiator;
import javaTools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static constants.FieldNames.*;
import static server.FcdslRequestHandler.doRequestForList;

@WebServlet(name = ApiNames.MyGroups, value = "/"+ApiNames.SN_8+"/"+ApiNames.Version2 +"/"+ApiNames.MyGroups)
public class MyGroups extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,GID,false,null,null);
        doRequest(Initiator.sid,defaultSort,request,response,authType,Initiator.esClient,Initiator.jedisPool);  }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,GID,false,null,null);
        doRequest(Initiator.sid,defaultSort,request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    protected void doRequest(String sid, List<Sort> sortList,HttpServletRequest request, HttpServletResponse response, AuthType authType, ElasticsearchClient esClient, JedisPool jedisPool) throws ServletException, IOException {
        FcReplier replier = new FcReplier(sid,response);
        try (Jedis jedis = jedisPool.getResource()) {
            List<Group> meetList = doRequestForList(sid, IndicesNames.GROUP, Group.class, null, null, null, null, sortList, request, response, authType, esClient, replier, jedis);
            if (meetList == null) return;
            //Make data
            List<apip.apipData.MyGroupData> dataList = new ArrayList<>();
            for(Group group: meetList){
                apip.apipData.MyGroupData data = new apip.apipData.MyGroupData();
                data.setName(group.getName());
                data.setGid(group.getGid());
                data.settCdd(group.gettCdd());
                data.setMemberNum(group.getMemberNum());
                dataList.add(data);
            }
            replier.reply0Success(dataList, jedis, null);
        }
    }
}