
package APIP13V1_Mail;


import server.ApipApiNames;
import constants.IndicesNames;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import feip.feipData.Mail;
import initial.Initiator;
import utils.http.AuthType;
import appTools.Settings;
import static constants.FieldNames.ID;
import server.FcHttpRequestHandler;


@WebServlet(name = ApipApiNames.MAIL_BY_IDS, value = "/"+ ApipApiNames.SN_13+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.MAIL_BY_IDS)
public class MailByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public MailByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.MAIL, Mail.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.MAIL, Mail.class, ID, request,response,authType);
    }
}