package nabox;

import config.Settings;
import constants.ApiNames;
import constants.ApipApiNames;
import initial.Initiator;
import server.FcHttpRequestHandler;
import utils.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = ApipApiNames.TX_BY_FID+ ApiNames.NABOX, value = "/"+ "sn2"  +"/"+ ApipApiNames.VER_1 +"/"+ ApipApiNames.TX_BY_FID )
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
