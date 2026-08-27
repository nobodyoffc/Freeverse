package APIP2V1_Blockchain;


import constants.ApipApiNames;
import server.FcHttpRequestHandler;
import initial.Initiator;
import utils.http.AuthType;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import config.Settings;
import java.io.IOException;

import static constants.Strings.HEIGHT;

@WebServlet(name = ApipApiNames.BLOCK_BY_HEIGHTS, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.BLOCK_BY_HEIGHTS +"/"+ ApipApiNames.VER_1)
public class BlockByHeights extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;
    public BlockByHeights() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.ENCRYPTED;
        fcHttpRequestHandler.doBlockInfoRequest(true,HEIGHT, request, response, authType);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doBlockInfoRequest(true,HEIGHT,request, response, authType);
    }
}