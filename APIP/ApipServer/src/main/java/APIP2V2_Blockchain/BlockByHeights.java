package APIP2V2_Blockchain;


import server.ApipApiNames;
import server.FcHttpRequestHandler;
import initial.Initiator;
import utils.http.AuthType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import config.Settings;
import java.io.IOException;

import static constants.Strings.HEIGHT;

@WebServlet(name = ApipApiNames.BLOCK_BY_HEIGHTS, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.BLOCK_BY_HEIGHTS)
public class BlockByHeights extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;
    public BlockByHeights() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcHttpRequestHandler.doBlockInfoRequest(true,HEIGHT, request, response, authType);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doBlockInfoRequest(true,HEIGHT,request, response, authType);
    }
}