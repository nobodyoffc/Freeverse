package APIP8V1_Group;

import appTools.Settings;
import feip.feipData.Service;
import redis.clients.jedis.JedisPool;
import server.ApipApiNames;
import constants.FieldNames;
import constants.IndicesNames;
import fcData.ReplyBody;
import feip.feipData.Group;
import initial.Initiator;
import tools.http.AuthType;
import redis.clients.jedis.Jedis;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

import static constants.FieldNames.GID;
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
        doGroupIdsRequest( FieldNames.GID, request,response,authType, settings);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doGroupIdsRequest( GID, request,response,authType, settings);
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