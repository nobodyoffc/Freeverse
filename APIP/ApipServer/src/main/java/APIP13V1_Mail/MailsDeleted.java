
package APIP13V1_Mail;

import apip.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import feip.feipData.Mail;
import initial.Initiator;
import tools.http.AuthType;
import server.FcdslRequestHandler;
import appTools.Settings;
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


@WebServlet(name = ApipApiNames.MAILS_DELETED, value = "/"+ ApipApiNames.SN_13+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.MAILS_DELETED)
public class MailsDeleted extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public MailsDeleted() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,Mail_Id,true, null,null);
        fcdslRequestHandler.doSearchRequest(IndicesNames.MAIL, Mail.class, null,null,ACTIVE,TRUE,defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,Mail_Id,true, null,null);
        fcdslRequestHandler.doSearchRequest(IndicesNames.MAIL, Mail.class, null,null,ACTIVE,TRUE,defaultSort, request,response,authType);
    }
}
