
package APIP13V1_Mail;


import constants.ApiNames;
import constants.IndicesNames;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import feip.feipData.Mail;
import initial.Initiator;
import javaTools.http.AuthType;

import static constants.FieldNames.Mail_Id;
import static server.FcdslRequestHandler.doIdsRequest;


@WebServlet(name = ApiNames.MailByIds, value = "/"+ApiNames.SN_13+"/"+ApiNames.Version2 +"/"+ApiNames.MailByIds)
public class MailByIds extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doIdsRequest(IndicesNames.MAIL, Mail.class, Mail_Id, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doIdsRequest(IndicesNames.MAIL, Mail.class, Mail_Id, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}