
package APIP16V1_Token;


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

import feip.feipData.Token;
import initial.Initiator;
import tools.http.AuthType;
import server.FcdslRequestHandler;
import appTools.Settings;
import static constants.FieldNames.Token_Id;
import static constants.FieldNames.LAST_HEIGHT;


@WebServlet(name = ApipApiNames.TOKEN_SEARCH, value = "/"+ ApipApiNames.SN_16+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.TOKEN_SEARCH)
public class TokenSearch extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public TokenSearch() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,Token_Id,true, null,null);
        fcdslRequestHandler.doSearchRequest(IndicesNames.TOKEN, Token.class,defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,Token_Id,true, null,null);
        fcdslRequestHandler.doSearchRequest(IndicesNames.TOKEN, Token.class,defaultSort, request,response,authType);
    }
}