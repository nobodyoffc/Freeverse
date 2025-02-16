package APIP3V1_CidInfo;

import server.ApipApiNames;

import initial.Initiator;
import tools.http.AuthType;
import server.FcdslRequestHandler;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import appTools.Settings;
@WebServlet(name = ApipApiNames.CID_INFO_BY_IDS, value = "/"+ ApipApiNames.SN_3+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.CID_INFO_BY_IDS)
public class CidInfoByIds extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public CidInfoByIds() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcdslRequestHandler.doCidInfoByIdsRequestHttp(null, request, response, authType);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcdslRequestHandler.doCidInfoByIdsRequestHttp(null, request, response, authType);
    }
}
