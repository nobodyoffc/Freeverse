package APIP2V2_Blockchain;

import server.ApipApiNames;
import initial.Initiator;
import tools.http.AuthType;
import server.FcdslRequestHandler;
import server.HttpRequestChecker;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import appTools.Settings;
import fcData.ReplyBody;

import java.io.IOException;

import static constants.FieldNames.BLOCK_ID;

@WebServlet(name = ApipApiNames.BLOCK_BY_IDS, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.BLOCK_BY_IDS)
public class BlockByIds extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public BlockByIds() {
        Settings settings = Initiator.settings;
        fcdslRequestHandler = new FcdslRequestHandler(settings);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcdslRequestHandler.doBlockInfoRequest(true,BLOCK_ID, request, response, authType);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcdslRequestHandler.doBlockInfoRequest(true,BLOCK_ID,request, response, authType);
    }
}
