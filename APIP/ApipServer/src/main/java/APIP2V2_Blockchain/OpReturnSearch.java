package APIP2V2_Blockchain;

import apip.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import fch.fchData.OpReturn;
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

@WebServlet(name = ApipApiNames.OP_RETURN_SEARCH, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.OP_RETURN_SEARCH)
public class OpReturnSearch extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public OpReturnSearch() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,TX_INDEX,true,ID,true);
        fcdslRequestHandler.doSearchRequest(IndicesNames.OPRETURN, OpReturn.class, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,TX_INDEX,true,ID,true);
        fcdslRequestHandler.doSearchRequest(IndicesNames.OPRETURN, OpReturn.class, defaultSort, request,response,authType);
    }
}