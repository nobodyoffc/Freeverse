package APIP1V2_FCDSL;

import apip.apipData.Sort;
import appTools.Settings;
import server.ApipApiNames;
import fcData.ReplyBody;
import initial.Initiator;
import tools.http.AuthType;
import server.FcdslRequestHandler;
import server.HttpRequestChecker;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@WebServlet(name = ApipApiNames.GENERAL, value = "/"+ ApipApiNames.SN_1+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.GENERAL)
public class General extends HttpServlet {
    private final Settings settings;
    private final ReplyBody replier;
    private final HttpRequestChecker httpRequestChecker;

    public General() {
        this.settings = Initiator.settings;
        this.replier = new ReplyBody(settings);
        this.httpRequestChecker = new HttpRequestChecker(settings, replier);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request, response, authType, settings);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request, response, authType, settings);
    }

    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) {
        boolean isOk = httpRequestChecker.checkRequestHttp(request, response, authType);
        if (!isOk) {
                return;
        }
        List<Object> meetList;
        FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(replier, settings);
        ArrayList<Sort> defaultSortList = null;
        String index = httpRequestChecker.getRequestBody().getFcdsl().getIndex();
        meetList = fcdslRequestHandler.doRequest(index, defaultSortList, Object.class);
        if(meetList==null){
            try {
                response.getWriter().write(fcdslRequestHandler.getFinalReplyJson());
            } catch (IOException ignore) {
            }
            return;
        }
        replier.setGot((long) meetList.size());
        replier.reply0SuccessHttp(meetList,response);    
    }
}