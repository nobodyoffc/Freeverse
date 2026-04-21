package APIP11V1_Contact;


import constants.ApipApiNames;
import data.apipData.Sort;
import constants.IndicesNames;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import data.feipData.Contact;
import initial.Initiator;
import utils.http.AuthType;
import server.FcHttpRequestHandler;
import config.Settings;
import static constants.FieldNames.ID;
import static constants.FieldNames.LAST_HEIGHT;
import static constants.Strings.ACTIVE;
import static constants.Values.FALSE;


@WebServlet(name = ApipApiNames.CONTACT_SEARCH, value = "/"+ ApipApiNames.SN_11+"/"+ ApipApiNames.CONTACT_SEARCH +"/"+ ApipApiNames.VER_1)
public class ContactSearch extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public ContactSearch() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.ENCRYPTED;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true, null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.CONTACT, Contact.class, null,null,ACTIVE,FALSE,defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true, null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.CONTACT, Contact.class, null,null,ACTIVE,FALSE,defaultSort, request,response,authType);
    }
}
