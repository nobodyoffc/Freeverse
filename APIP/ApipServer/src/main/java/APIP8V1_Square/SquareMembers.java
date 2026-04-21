package APIP8V1_Square;

import config.Settings;
import constants.ApipApiNames;
import constants.IndicesNames;
import data.feipData.Square;
import initial.Initiator;
import server.FcHttpRequestHandler;
import utils.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@WebServlet(name = ApipApiNames.SQUARE_MEMBERS, value = "/"+ ApipApiNames.SN_8+"/"+ ApipApiNames.SQUARE_MEMBERS +"/"+ ApipApiNames.VER_1)
public class SquareMembers extends HttpServlet {
    private final Settings settings;
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public SquareMembers() {
        this.settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.ENCRYPTED;
        doRequest(request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request,response,authType);
    }
    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType) throws ServletException, IOException {
        List<Square> meetList = fcHttpRequestHandler.doRequestForList(IndicesNames.SQUARE, Square.class, null, null, null, null, null, request, response, authType);
        if (meetList == null) return;
        //Make data
        Map<String,String[]> dataMap = new HashMap<>();
        for(Square square:meetList){
            dataMap.put(square.getId(),square.getMembers());
        }
        fcHttpRequestHandler.getReplyBody().reply0SuccessHttp(dataMap,response);
    }
}
