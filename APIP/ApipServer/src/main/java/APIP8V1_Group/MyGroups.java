package APIP8V1_Group;

import apip.apipData.Sort;
import appTools.Settings;
import server.ApipApiNames;
import constants.IndicesNames;
import feip.feipData.Group;
import initial.Initiator;
import utils.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static constants.FieldNames.*;
import server.FcHttpRequestHandler;

@WebServlet(name = ApipApiNames.MY_GROUPS, value = "/"+ ApipApiNames.SN_8+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.MY_GROUPS)
public class MyGroups extends HttpServlet {
    private final Settings settings;
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public MyGroups() {
        this.settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true,null,null);
        doRequest(defaultSort,request,response,authType, settings);  }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true,null,null);
        doRequest(defaultSort,request,response,authType, settings);
    }
    protected void doRequest(List<Sort> sortList, HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) throws ServletException, IOException {
        List<Group> meetList = fcHttpRequestHandler.doRequestForList(IndicesNames.GROUP, Group.class, null, null, null, null, sortList, request, response, authType);
        if (meetList == null) return;
        for(Group group: meetList){
            group.setMembers(null);
            group.setNamers(null);
        }
        fcHttpRequestHandler.getReplyBody().reply0SuccessHttp(meetList,response);
    }
}