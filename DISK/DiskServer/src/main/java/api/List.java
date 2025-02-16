package api;

import apip.apipData.Sort;
import fcData.DiskItem;
import server.ApipApiNames;
import fcData.ReplyBody;
import initial.Initiator;
import tools.http.AuthType;
import redis.clients.jedis.Jedis;
import server.FcdslRequestHandler;
import server.HttpRequestChecker;
import appTools.Settings;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import static server.DiskApiNames.LIST;
import static constants.FieldNames.DID;
import static constants.FieldNames.SINCE;
import static constants.Strings.DATA;

@WebServlet(name = LIST, value ="/"+ ApipApiNames.VERSION_1 +"/"+ LIST)
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
        ReplyBody replier = new ReplyBody(Initiator.settings);

        //Check authorization
        try (Jedis jedis = Initiator.jedisPool.getResource()) {
            HttpRequestChecker httpRequestChecker = new HttpRequestChecker(Initiator.settings, replier);
            httpRequestChecker.checkRequestHttp(request, response, authType);
            if (httpRequestChecker ==null){
                return;
            }
            //Do request
            FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(replier, Initiator.settings);
            ArrayList<Sort> defaultSortList=null;

            if(httpRequestChecker.getRequestBody()==null || httpRequestChecker.getRequestBody().getFcdsl()==null|| httpRequestChecker.getRequestBody().getFcdsl().getSort()==null)
                defaultSortList = Sort.makeSortList(SINCE, true, DID, true, null, null);


            java.util.List<DiskItem> meetList = fcdslRequestHandler.doRequest(Settings.addSidBriefToName(Initiator.sid, DATA), defaultSortList, DiskItem.class);

            if(meetList==null){
                try {
                    response.getWriter().write(fcdslRequestHandler.getFinalReplyJson());
                } catch (IOException ignore) {
                    return;
                }
            }

            replier.reply0SuccessHttp(meetList,response);
        }
    }
}
