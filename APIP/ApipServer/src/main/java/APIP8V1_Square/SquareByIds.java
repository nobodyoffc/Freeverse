package APIP8V1_Square;

import config.Settings;
import constants.ApipApiNames;
import constants.FieldNames;
import constants.IndicesNames;
import data.feipData.Square;
import initial.Initiator;
import utils.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

import server.FcHttpRequestHandler;


@WebServlet(name = ApipApiNames.SQUARE_BY_IDS, value = "/"+ ApipApiNames.SN_8+"/"+ ApipApiNames.SQUARE_BY_IDS +"/"+ ApipApiNames.VER_1)
public class SquareByIds extends HttpServlet {
    private final Settings settings;

    public SquareByIds() {
        this.settings = Initiator.settings;
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.ENCRYPTED;
        doSquareIdsRequest( FieldNames.ID, request,response,authType, settings);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doSquareIdsRequest( FieldNames.ID, request,response,authType, settings);
    }

    public static void doSquareIdsRequest(String keyFieldName, HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) {
        FcHttpRequestHandler fcHttpRequestHandler = FcHttpRequestHandler.checkRequest(request, response, authType, settings);
        if (fcHttpRequestHandler == null) return;
        Map<String, Square> meetMap = fcHttpRequestHandler.doRequestForMap(IndicesNames.SQUARE, Square.class, keyFieldName,response);
        if (meetMap == null) return;

        for(Square square :meetMap.values()){
            square.setMembers(null);
        }
        fcHttpRequestHandler.getReplyBody().reply0SuccessHttp(meetMap,response);
    }

}
