package APIP3V1_CidInfo;

import apip.apipData.Sort;
import constants.ApiNames;
import constants.IndicesNames;
import feip.feipData.CidHist;
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

@WebServlet(name = ApiNames.NoticeFeeHistory, value = "/"+ApiNames.SN_3+"/"+ApiNames.Version1 +"/"+ApiNames.NoticeFeeHistory)
public class NoticeFeeHistory extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.CID_HISTORY, CidHist.class,"sn","10", null, null, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool, Initiator.sessionHandler);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.CID_HISTORY, CidHist.class,"sn","10", null, null, defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool, Initiator.sessionHandler);
    }
}
