package APIP8V1_Group;

import data.apipData.Sort;
import config.Settings;
import server.ApipApiNames;
import constants.IndicesNames;
import data.feipData.Group;
import initial.Initiator;
import utils.http.AuthType;
import server.FcHttpRequestHandler;

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
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public GroupSearch() {
        this.settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(T_CDD,false,ID,true,null,null);
        doGroupSearchRequest(null,null,null,null, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(T_CDD,false,ID,true,null,null);
        doGroupSearchRequest(null,null,null,null, defaultSort, request,response,authType);
    }

    public void doGroupSearchRequest(String filterField, String filterValue, String exceptField, String exceptValue, List<Sort> sort, HttpServletRequest request, HttpServletResponse response, AuthType authType) {
        List<Group> meetList = fcHttpRequestHandler.doRequestForList(IndicesNames.GROUP, Group.class, filterField, filterValue, exceptField, exceptValue, sort, request, response, authType);
        if (meetList == null) return;
        for(Group group :meetList){
            group.setMembers(null);
        }
        fcHttpRequestHandler.getReplyBody().reply0SuccessHttp(meetList,response);
    }
}