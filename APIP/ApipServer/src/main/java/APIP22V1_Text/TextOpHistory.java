package APIP22V1_Text;

import data.apipData.Sort;
import constants.ApipApiNames;
import constants.IndicesNames;
import data.feipData.TextHistory;
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

import config.Settings;
import static constants.OpNames.RATE;
import static constants.Strings.OP;
import static constants.FieldNames.HEIGHT;
import static constants.FieldNames.INDEX;

@WebServlet(name = ApipApiNames.TEXT_OP_HISTORY, value = "/"+ ApipApiNames.SN_22+"/"+ ApipApiNames.TEXT_OP_HISTORY +"/"+ ApipApiNames.VER_1)
public class TextOpHistory extends HttpServlet {
    private final Settings settings = Initiator.settings;
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.SYMKEY_ENCRYPT;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        FcHttpRequestHandler fcHttpRequestHandler = new FcHttpRequestHandler(settings);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.TEXT_HISTORY, TextHistory.class, null,null,OP,RATE, defaultSort,request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        FcHttpRequestHandler fcHttpRequestHandler = new FcHttpRequestHandler(settings);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.TEXT_HISTORY, TextHistory.class, null,null,OP,RATE, defaultSort,request,response,authType);
    }
} 