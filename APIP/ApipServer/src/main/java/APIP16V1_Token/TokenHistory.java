
package APIP16V1_Token;

import apip.apipData.Sort;
import constants.ApiNames;
import constants.IndicesNames;
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

import static constants.FieldNames.HEIGHT;
import static constants.FieldNames.INDEX;

@WebServlet(name = ApiNames.TokenHistory, value = "/"+ApiNames.SN_16+"/"+ApiNames.Version1 +"/"+ApiNames.TokenHistory)
public class TokenHistory extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.TOKEN_HISTORY, feip.feipData.TokenHistory.class, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool, Initiator.sessionHandler);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.TOKEN_HISTORY,TokenHistory.class, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool, Initiator.sessionHandler);
    }
}
