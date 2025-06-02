package APIP21V1_Essay;

import server.ApipApiNames;
import constants.IndicesNames;
import data.feipData.Essay;
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

@WebServlet(name = ApipApiNames.ESSAY_BY_IDS, value = "/"+ ApipApiNames.SN_21+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.ESSAY_BY_IDS)
public class EssayByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public EssayByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.ESSAY, Essay.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.ESSAY, Essay.class, ID, request,response,authType);
    }
} 