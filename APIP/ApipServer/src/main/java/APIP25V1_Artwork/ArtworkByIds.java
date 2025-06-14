package APIP25V1_Artwork;

import server.ApipApiNames;
import constants.IndicesNames;
import data.feipData.Artwork;
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

@WebServlet(name = ApipApiNames.ARTWORK_BY_IDS, value = "/"+ ApipApiNames.SN_25+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.ARTWORK_BY_IDS)
public class ArtworkByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public ArtworkByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.ARTWORK, Artwork.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.ARTWORK, Artwork.class, ID, request,response,authType);
    }
} 