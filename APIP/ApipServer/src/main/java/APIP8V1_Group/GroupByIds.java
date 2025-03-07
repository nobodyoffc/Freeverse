package APIP8V1_Group;

import appTools.Settings;
import server.ApipApiNames;
import constants.FieldNames;
import constants.IndicesNames;
import feip.feipData.Group;
import initial.Initiator;
import tools.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

import server.FcdslRequestHandler;


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
        FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(settings);
        Map<String, Group> meetMap = fcdslRequestHandler.doRequestForMap(IndicesNames.GROUP, Group.class, keyFieldName);
        if (meetMap == null) return;

        for(Group group :meetMap.values()){
            group.setMembers(null);
        }
        fcdslRequestHandler.getReplyBody().reply0SuccessHttp(meetMap,response);
    }
}