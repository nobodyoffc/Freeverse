package APIP11V1_Contact;


import constants.ApiNames;
import constants.IndicesNames;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import feip.feipData.Contact;
import initial.Initiator;
import javaTools.http.AuthType;

import static constants.FieldNames.Contact_Id;
import static server.FcdslRequestHandler.doIdsRequest;


@WebServlet(name = ApiNames.ContactByIds, value = "/"+ApiNames.SN_11+"/"+ApiNames.Version2 +"/"+ApiNames.ContactByIds)
public class ContactByIds extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doIdsRequest(IndicesNames.CONTACT, Contact.class, Contact_Id, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doIdsRequest(IndicesNames.CONTACT, Contact.class, Contact_Id, Initiator.sid, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}