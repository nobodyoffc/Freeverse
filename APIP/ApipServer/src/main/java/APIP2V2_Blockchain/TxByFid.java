package APIP2V2_Blockchain;

import server.ApipApiNames;
import initial.Initiator;
import utils.http.AuthType;
import server.FcdslRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import appTools.Settings;

@WebServlet(name = ApipApiNames.TX_BY_FID, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.TX_BY_FID)
public class TxByFid extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public TxByFid() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcdslRequestHandler.doFidTxMaskRequest(request, response, authType);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcdslRequestHandler.doFidTxMaskRequest(request, response, authType);
    }
}
