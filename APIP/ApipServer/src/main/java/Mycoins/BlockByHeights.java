package Mycoins;

import config.Settings;
import constants.ApipApiNames;
import initial.Initiator;
import server.FcHttpRequestHandler;
import utils.http.AuthType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static constants.Strings.HEIGHT;

@WebServlet(name = ApipApiNames.BLOCK_BY_HEIGHTS + ApipApiNames.MYCOINS, value = ApipApiNames.MycoinsPath + ApipApiNames.BLOCK_BY_HEIGHTS)
public class BlockByHeights extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public BlockByHeights() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.ENCRYPTED;
        fcHttpRequestHandler.doBlockInfoRequest(true, HEIGHT, request, response, authType);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FREE;
        fcHttpRequestHandler.doBlockInfoRequest(true, HEIGHT, request, response, authType);
    }
}
