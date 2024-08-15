package APIP11V1_Contact;


import apip.apipData.Sort;
import constants.ApiNames;
import constants.IndicesNames;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import feip.feipData.Contact;
import initial.Initiator;
import javaTools.http.AuthType;
import server.FcdslRequestHandler;

import static constants.FieldNames.Contact_Id;
import static constants.FieldNames.LAST_HEIGHT;
import static constants.Strings.ACTIVE;
import static constants.Values.FALSE;


@WebServlet(name = ApiNames.ContactSearch, value = "/"+ApiNames.SN_11+"/"+ApiNames.Version2 +"/"+ApiNames.ContactSearch)
public class ContactSearch extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,Contact_Id,true, null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.CONTACT, Contact.class, null,null,ACTIVE,FALSE,defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,Contact_Id,true, null,null);
        FcdslRequestHandler.doSearchRequest(Initiator.sid,IndicesNames.CONTACT, Contact.class, null,null,ACTIVE,FALSE,defaultSort, request,response,authType,Initiator.esClient,Initiator.jedisPool);
    }
}
