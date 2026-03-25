package APIP8V1_Square;

import constants.ApipApiNames;
import data.apipData.Sort;
import config.Settings;
import constants.IndicesNames;
import data.feipData.Square;
import initial.Initiator;
import utils.http.AuthType;
import server.FcHttpRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static constants.FieldNames.*;


@WebServlet(name = ApipApiNames.SQUARE_SEARCH, value = "/"+ ApipApiNames.SN_8+"/"+ ApipApiNames.SQUARE_SEARCH +"/"+ ApipApiNames.VER_1)
public class SquareSearch extends HttpServlet {
    private final Settings settings;
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public SquareSearch() {
        this.settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.SYMKEY_ENCRYPT;
        ArrayList<Sort> defaultSort = Sort.makeSortList(T_CDD,false,ID,true,null,null);
        doSquareSearchRequest(null,null,null,null, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(T_CDD,false,ID,true,null,null);
        doSquareSearchRequest(null,null,null,null, defaultSort, request,response,authType);
    }

    public void doSquareSearchRequest(String filterField, String filterValue, String exceptField, String exceptValue, List<Sort> sort, HttpServletRequest request, HttpServletResponse response, AuthType authType) {
        List<Square> meetList = fcHttpRequestHandler.doRequestForList(IndicesNames.SQUARE, Square.class, filterField, filterValue, exceptField, exceptValue, sort, request, response, authType);
        if (meetList == null) return;
        for(Square square :meetList){
            square.setMembers(null);
        }
        fcHttpRequestHandler.getReplyBody().reply0SuccessHttp(meetList,response);
    }
}
