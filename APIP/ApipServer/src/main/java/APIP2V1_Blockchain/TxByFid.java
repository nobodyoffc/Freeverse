package APIP2V1_Blockchain;

import server.ApipApiNames;
import initial.Initiator;
import utils.http.AuthType;
import server.FcHttpRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import config.Settings;

@WebServlet(name = ApipApiNames.TX_BY_FID, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.TX_BY_FID)
public class TxByFid extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public TxByFid() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcHttpRequestHandler.doFidTxMaskRequest(request, response, authType);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doFidTxMaskRequest(request, response, authType);
    }
}
