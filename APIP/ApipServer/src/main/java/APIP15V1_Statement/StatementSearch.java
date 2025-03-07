
package APIP15V1_Statement;


import apip.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import feip.feipData.Statement;
import initial.Initiator;
import tools.http.AuthType;
import server.FcdslRequestHandler;
import appTools.Settings;
import static constants.FieldNames.ID;
import static constants.FieldNames.LAST_HEIGHT;


@WebServlet(name = ApipApiNames.STATEMENT_SEARCH, value = "/"+ ApipApiNames.SN_15+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.STATEMENT_SEARCH)
public class StatementSearch extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public StatementSearch() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true, null,null);
        fcdslRequestHandler.doSearchRequest(IndicesNames.STATEMENT, Statement.class, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true, null,null);
        fcdslRequestHandler.doSearchRequest(IndicesNames.STATEMENT, Statement.class, defaultSort, request,response,authType);
    }
}
