package api;

import data.apipData.Sort;
import data.fcData.DiskItem;
import server.ApipApiNames;
import data.fcData.ReplyBody;
import initial.Initiator;
import utils.http.AuthType;
import server.FcHttpRequestHandler;
import server.HttpRequestChecker;
import config.Settings;

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

    private final Settings settings = Initiator.settings;

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

    private void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType) {
        ReplyBody replier = new ReplyBody(settings);

        //Check authorization
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        httpRequestChecker.checkRequestHttp(request, response, authType);

        //Do request
        FcHttpRequestHandler fcHttpRequestHandler = new FcHttpRequestHandler(replier, settings);
        ArrayList<Sort> defaultSortList=null;

        if(httpRequestChecker.getRequestBody()==null || httpRequestChecker.getRequestBody().getFcdsl()==null|| httpRequestChecker.getRequestBody().getFcdsl().getSort()==null)
            defaultSortList = Sort.makeSortList(SINCE, true, DID, true, null, null);


        java.util.List<DiskItem> meetList = fcHttpRequestHandler.doRequest(Settings.addSidBriefToName(settings.getSid(), DATA), defaultSortList, DiskItem.class);

        if(meetList==null){
            try {
                response.getWriter().write(fcHttpRequestHandler.getFinalReplyJson());
            } catch (IOException ignore) {
                return;
            }
        }

        replier.reply0SuccessHttp(meetList,response);
    }
}
