package APIP8V1_Group;

import apip.apipData.Sort;
import appTools.Settings;
import feip.feipData.Service;
import redis.clients.jedis.JedisPool;
import server.ApipApiNames;
import constants.IndicesNames;
import fcData.ReplyBody;
import feip.feipData.Group;
import initial.Initiator;
import tools.http.AuthType;
import redis.clients.jedis.Jedis;
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


@WebServlet(name = ApipApiNames.GROUP_SEARCH, value = "/"+ ApipApiNames.SN_8+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.GROUP_SEARCH)
public class GroupSearch extends HttpServlet {
    private final Settings settings;
    private final FcdslRequestHandler fcdslRequestHandler;

    public GroupSearch() {
        this.settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(T_CDD,false,GID,true,null,null);
        doGroupSearchRequest(null,null,null,null, defaultSort, request,response,authType, settings);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(T_CDD,false,GID,true,null,null);
        doGroupSearchRequest(null,null,null,null, defaultSort, request,response,authType, settings);
    }

    public void doGroupSearchRequest(String filterField, String filterValue, String exceptField, String exceptValue, List<Sort> sort, HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) {
        List<Group> meetList = fcdslRequestHandler.doRequestForList(IndicesNames.GROUP, Group.class, filterField, filterValue, exceptField, exceptValue, sort, request, response, authType);
        if (meetList == null) return;
        for(Group group :meetList){
            group.setMembers(null);
        }
        fcdslRequestHandler.getReplyBody().reply0SuccessHttp(meetList,response);
    }
}