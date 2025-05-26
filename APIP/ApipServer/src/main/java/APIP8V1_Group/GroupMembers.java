package APIP8V1_Group;

import config.Settings;
import constants.IndicesNames;
import data.feipData.Group;
import initial.Initiator;
import server.ApipApiNames;
import server.FcHttpRequestHandler;
import utils.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@WebServlet(name = ApipApiNames.GROUP_MEMBERS, value = "/"+ ApipApiNames.SN_8+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.GROUP_MEMBERS)
public class GroupMembers extends HttpServlet {
    private final Settings settings;
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public GroupMembers() {
        this.settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request,response,authType);
    }
    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType) throws ServletException, IOException {
        List<Group> meetList = fcHttpRequestHandler.doRequestForList(IndicesNames.GROUP, Group.class, null, null, null, null, null, request, response, authType);
        if (meetList == null) return;
        //Make data
        Map<String,String[]> dataMap = new HashMap<>();
        for(Group group:meetList){
            dataMap.put(group.getId(),group.getMembers());
        }
        fcHttpRequestHandler.getReplyBody().reply0SuccessHttp(dataMap,response);
    }
}