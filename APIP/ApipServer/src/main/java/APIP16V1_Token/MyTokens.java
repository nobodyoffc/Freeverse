
package APIP16V1_Token;

import data.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import data.feipData.TokenHolder;
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
import static constants.FieldNames.ID;
import static constants.FieldNames.LAST_HEIGHT;

@WebServlet(name = ApipApiNames.MY_TOKENS, value = "/"+ ApipApiNames.SN_16+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.MY_TOKENS)
public class MyTokens extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public MyTokens() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true, null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.TOKEN_HOLDER, TokenHolder.class, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true, null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.TOKEN_HOLDER, TokenHolder.class, defaultSort, request,response,authType);
    }
}