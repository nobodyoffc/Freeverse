package APIP7V1_App;

import apip.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import feip.feipData.AppHistory;
import initial.Initiator;
import utils.http.AuthType;
import server.FcdslRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import static constants.OpNames.RATE;
import static constants.Strings.OP;
import static constants.FieldNames.HEIGHT;
import static constants.FieldNames.INDEX;


@WebServlet(name = ApipApiNames.APP_RATE_HISTORY, value = "/"+ ApipApiNames.SN_7+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.APP_RATE_HISTORY)
public class AppRateHistory extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public AppRateHistory() {
        this.fcdslRequestHandler = new FcdslRequestHandler(Initiator.settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        fcdslRequestHandler.doSearchRequest(IndicesNames.APP_HISTORY, AppHistory.class, OP,RATE,null,null, defaultSort,request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        fcdslRequestHandler.doSearchRequest(IndicesNames.APP_HISTORY, AppHistory.class, OP,RATE,null,null, defaultSort,request,response,authType);
    }
}