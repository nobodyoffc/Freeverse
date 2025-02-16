package APIP3V1_CidInfo;

import apip.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import fch.fchData.Cash;
import feip.feipData.Nobody;
import initial.Initiator;
import tools.http.AuthType;
import server.FcdslRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import appTools.Settings;

import static constants.FieldNames.*;

@WebServlet(name = ApipApiNames.NOBODY_SEARCH, value = "/"+ ApipApiNames.SN_3+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.NOBODY_SEARCH)
public class NobodySearch extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public NobodySearch() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(DEATH_HEIGHT,false, DEATH_TX_INDEX,true,FID,true);
        fcdslRequestHandler.doSearchRequest(IndicesNames.NOBODY, Nobody.class, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_TIME,false,CASH_ID,true,null,null);
        fcdslRequestHandler.doSearchRequest(IndicesNames.CASH, Cash.class, defaultSort, request,response,authType);
    }
}
