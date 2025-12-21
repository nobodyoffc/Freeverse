package APIP22V1_Text;

import constants.ApipApiNames;
import constants.IndicesNames;
import data.feipData.Text;
import initial.Initiator;
import utils.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import config.Settings;
import static constants.FieldNames.ID;
import server.FcHttpRequestHandler;

@WebServlet(name = ApipApiNames.TEXT_BY_IDS, value = "/"+ ApipApiNames.SN_22+"/"+ ApipApiNames.TEXT_BY_IDS +"/"+ ApipApiNames.VER_1)
public class TextByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public TextByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.SYMKEY_ENCRYPT;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.TEXT, Text.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.TEXT, Text.class, ID, request,response,authType);
    }
} 