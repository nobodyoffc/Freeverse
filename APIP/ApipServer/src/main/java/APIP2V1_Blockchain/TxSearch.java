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

import static constants.FieldNames.ID;

@WebServlet(name = ApipApiNames.TX_SEARCH, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.TX_SEARCH)
public class TxSearch extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public TxSearch() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcHttpRequestHandler.doTxInfoRequest(false,ID,request, response, authType);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doTxInfoRequest(false,ID,request, response, authType);
    }
}