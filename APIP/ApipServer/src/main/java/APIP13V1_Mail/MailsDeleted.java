
package APIP13V1_Mail;

import apip.apipData.Sort;
import constants.ApiNames;
import constants.IndicesNames;
import feip.feipData.Mail;
import initial.Initiator;
import javaTools.http.AuthType;
import server.FcdslRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import static constants.FieldNames.Mail_Id;
import static constants.FieldNames.LAST_HEIGHT;
import static constants.Strings.ACTIVE;
import static constants.Values.TRUE;


@WebServlet(name = ApiNames.MailsDeleted, value = "/"+ApiNames.SN_13+"/"+ApiNames.Version2 +"/"+ApiNames.MailsDeleted)
public class MailsDeleted extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,Mail_Id,true, null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.MAIL, Mail.class, null,null,ACTIVE,TRUE,defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,Mail_Id,true, null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.MAIL, Mail.class, null,null,ACTIVE,TRUE,defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}
