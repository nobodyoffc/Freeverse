
package APIP13V1_Mail;


import apip.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import feip.feipData.Mail;
import initial.Initiator;
import utils.http.AuthType;
import server.FcHttpRequestHandler;
import appTools.Settings;
import static constants.FieldNames.ID;
import static constants.FieldNames.LAST_HEIGHT;
import static constants.Strings.ACTIVE;
import static constants.Values.FALSE;


@WebServlet(name = ApipApiNames.MAIL_SEARCH, value = "/"+ ApipApiNames.SN_13+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.MAIL_SEARCH)
public class MailSearch extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public MailSearch() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true, null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.MAIL, Mail.class, null,null,ACTIVE,FALSE,defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true, null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.MAIL, Mail.class, null,null,ACTIVE,FALSE,defaultSort, request,response,authType);
    }
}