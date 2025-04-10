package APIP11V1_Contact;

import apip.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import feip.feipData.Contact;
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

import appTools.Settings;
import static constants.FieldNames.ID;
import static constants.FieldNames.LAST_HEIGHT;
import static constants.Strings.ACTIVE;
import static constants.Values.TRUE;


@WebServlet(name = ApipApiNames.CONTACTS_DELETED, value = "/"+ ApipApiNames.SN_11+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.CONTACTS_DELETED)
public class ContactsDeleted extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public ContactsDeleted() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true, null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.CONTACT, Contact.class, null,null,ACTIVE,TRUE,defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true, null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.CONTACT, Contact.class, null,null,ACTIVE,TRUE,defaultSort, request,response,authType);
    }
}