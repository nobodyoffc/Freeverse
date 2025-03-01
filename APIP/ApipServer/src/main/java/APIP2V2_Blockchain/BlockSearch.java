package APIP2V2_Blockchain;

import server.ApipApiNames;
import initial.Initiator;
import tools.http.AuthType;
import server.FcdslRequestHandler;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import appTools.Settings;

import java.io.IOException;

import static constants.FieldNames.ID;

@WebServlet(name = ApipApiNames.BLOCK_SEARCH, value = "/"+ ApipApiNames.SN_2+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.BLOCK_SEARCH)
public class BlockSearch extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public BlockSearch() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcdslRequestHandler.doBlockInfoRequest(false, ID, request, response, authType);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcdslRequestHandler.doBlockInfoRequest(false,ID,request, response, authType);
    }
}