package APIP2V2_Blockchain;

import server.ApipApiNames;
import initial.Initiator;
import utils.http.AuthType;
import server.FcHttpRequestHandler;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import config.Settings;

import java.io.IOException;

import static constants.FieldNames.ID;

@WebServlet(name = ApipApiNames.BLOCK_SEARCH, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.BLOCK_SEARCH)
public class BlockSearch extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public BlockSearch() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcHttpRequestHandler.doBlockInfoRequest(false, ID, request, response, authType);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doBlockInfoRequest(false,ID,request, response, authType);
    }
}