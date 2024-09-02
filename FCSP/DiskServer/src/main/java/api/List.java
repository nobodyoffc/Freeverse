package api;

import apip.apipData.Sort;
import clients.fcspClient.DiskItem;
import constants.ApiNames;
import fcData.FcReplier;
import initial.Initiator;
import javaTools.http.AuthType;
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

import static constants.ApiNames.LIST;
import static constants.FieldNames.DID;
import static constants.FieldNames.SINCE;
import static constants.Strings.DATA;

@WebServlet(name = LIST, value ="/"+ApiNames.Version1 +"/"+ LIST)
public class List extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request, response, authType);
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
            FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(requestCheckResult.getRequestBody(), replier,Initiator.esClient);
            ArrayList<Sort> defaultSortList=null;

            if(requestCheckResult.getRequestBody()==null || requestCheckResult.getRequestBody().getFcdsl()==null||requestCheckResult.getRequestBody().getFcdsl().getSort()==null)
                defaultSortList = Sort.makeSortList(SINCE, true, DID, true, null, null);


            java.util.List<DiskItem> meetList = fcdslRequestHandler.doRequest(Settings.addSidBriefToName(Initiator.sid, DATA), defaultSortList, DiskItem.class, jedis);

            if(meetList==null){
                return;
            }
            replier.reply0Success(meetList,jedis, null);
        }
    }
}
