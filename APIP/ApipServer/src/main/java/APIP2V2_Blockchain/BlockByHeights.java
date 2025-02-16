package APIP2V2_Blockchain;


import server.ApipApiNames;
import server.FcdslRequestHandler;
import initial.Initiator;
import tools.http.AuthType;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import appTools.Settings;
import java.io.IOException;

import static constants.Strings.HEIGHT;

@WebServlet(name = ApipApiNames.BLOCK_BY_HEIGHTS, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.BLOCK_BY_HEIGHTS)
public class BlockByHeights extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;
    public BlockByHeights() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcdslRequestHandler.doBlockInfoRequest(true,HEIGHT, request, response, authType);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcdslRequestHandler.doBlockInfoRequest(true,HEIGHT,request, response, authType);
    }
}