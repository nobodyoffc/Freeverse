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

import static constants.FieldNames.ID;

@WebServlet(name = ApipApiNames.TX_BY_IDS+ ApiNames.NABOX, value = "/"+ "sn2" +"/"+ ApipApiNames.VER_1+"/"+ ApipApiNames.TX_BY_IDS )
public class TxByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public TxByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcHttpRequestHandler.doTxInfoRequest(true,ID,request, response, authType);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doTxInfoRequest(true,ID,request, response, authType);
    }
}
