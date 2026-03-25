package APIP8V1_Square;

import constants.ApipApiNames;
import data.apipData.Sort;
import config.Settings;
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
import java.util.ArrayList;
import java.util.List;

import static constants.FieldNames.*;
import server.FcHttpRequestHandler;

@WebServlet(name = ApipApiNames.MY_SQUARES, value = "/"+ ApipApiNames.SN_8+"/"+ ApipApiNames.MY_SQUARES +"/"+ ApipApiNames.VER_1)
public class MySquares extends HttpServlet {
    private final Settings settings;
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public MySquares() {
        this.settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.SYMKEY_ENCRYPT;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true,null,null);
        doRequest(defaultSort,request,response,authType, settings);  }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true,null,null);
        doRequest(defaultSort,request,response,authType, settings);
    }
    protected void doRequest(List<Sort> sortList, HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) throws ServletException, IOException {
        List<Square> meetList = fcHttpRequestHandler.doRequestForList(IndicesNames.SQUARE, Square.class, null, null, null, null, sortList, request, response, authType);
        if (meetList == null) return;
        for(Square square: meetList){
            square.setMembers(null);
            square.setNamers(null);
        }
        fcHttpRequestHandler.getReplyBody().reply0SuccessHttp(meetList,response);
    }
}
