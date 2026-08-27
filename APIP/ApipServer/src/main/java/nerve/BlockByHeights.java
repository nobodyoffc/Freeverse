package nerve;

import config.Settings;
import constants.ApiNames;
import initial.Initiator;
import server.FcHttpRequestHandler;
import utils.http.AuthType;

import static constants.Strings.HEIGHT;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(ApiNames.NERVE + ApiNames.BlockByHeights)
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