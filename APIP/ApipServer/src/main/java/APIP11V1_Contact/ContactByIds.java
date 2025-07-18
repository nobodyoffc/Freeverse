package APIP11V1_Contact;


import server.ApipApiNames;
import constants.IndicesNames;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import config.Settings;
import data.feipData.Contact;
import initial.Initiator;
import utils.http.AuthType;

import static constants.FieldNames.ID;
import server.FcHttpRequestHandler;


@WebServlet(name = ApipApiNames.CONTACT_BY_IDS, value = "/"+ ApipApiNames.SN_11+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.CONTACT_BY_IDS)
public class ContactByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public ContactByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.CONTACT, Contact.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.CONTACT, Contact.class, ID, request,response,authType);
    }
}