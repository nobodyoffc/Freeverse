package APIP8V1_Group;

import appTools.Settings;
import server.ApipApiNames;
import constants.FieldNames;
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
import java.util.Map;

import server.FcHttpRequestHandler;


@WebServlet(name = ApipApiNames.GROUP_BY_IDS, value = "/"+ ApipApiNames.SN_8+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.GROUP_BY_IDS)
public class GroupByIds extends HttpServlet {
    private final Settings settings;

    public GroupByIds() {
        this.settings = Initiator.settings;
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doGroupIdsRequest( FieldNames.ID, request,response,authType, settings);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doGroupIdsRequest( FieldNames.ID, request,response,authType, settings);
    }

    public static void doGroupIdsRequest(String keyFieldName, HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) {
        FcHttpRequestHandler fcHttpRequestHandler = FcHttpRequestHandler.checkRequest(request, response, authType, settings);
        if (fcHttpRequestHandler == null) return;
        Map<String, Group> meetMap = fcHttpRequestHandler.doRequestForMap(IndicesNames.GROUP, Group.class, keyFieldName);
        if (meetMap == null) return;

        for(Group group :meetMap.values()){
            group.setMembers(null);
        }
        fcHttpRequestHandler.getReplyBody().reply0SuccessHttp(meetMap,response);
    }

}