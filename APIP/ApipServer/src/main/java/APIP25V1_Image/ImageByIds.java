package APIP25V1_Image;

import constants.ApipApiNames;
import constants.IndicesNames;
import data.feipData.Image;
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

@WebServlet(name = ApipApiNames.IMAGE_BY_IDS, value = "/"+ ApipApiNames.SN_25+"/"+ ApipApiNames.IMAGE_BY_IDS +"/"+ ApipApiNames.VER_1)
public class ImageByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public ImageByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.ENCRYPTED;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.IMAGE, Image.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.IMAGE, Image.class, ID, request,response,authType);
    }
}
