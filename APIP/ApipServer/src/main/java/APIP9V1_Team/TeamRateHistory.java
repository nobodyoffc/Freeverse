package APIP9V1_Team;

import constants.ApipApiNames;
import data.apipData.Sort;
import constants.IndicesNames;
import data.feipData.TeamHistory;
import initial.Initiator;
import utils.http.AuthType;
import server.FcHttpRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import config.Settings;
import static constants.FieldNames.INDEX;
import static constants.OpNames.RATE;
import static constants.Strings.HEIGHT;
import static constants.Strings.OP;


@WebServlet(name = ApipApiNames.TEAM_RATE_HISTORY, value = "/"+ ApipApiNames.SN_9+"/"+ ApipApiNames.TEAM_RATE_HISTORY +"/"+ ApipApiNames.VER_1)

public class TeamRateHistory extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public TeamRateHistory() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.ENCRYPTED;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.TEAM_HISTORY, TeamHistory.class, OP,RATE, null,null,defaultSort,request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.TEAM_HISTORY, TeamHistory.class, OP,RATE,null,null, defaultSort,request,response,authType);
    }
}