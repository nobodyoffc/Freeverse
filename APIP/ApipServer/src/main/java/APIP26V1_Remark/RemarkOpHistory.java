package APIP26V1_Remark;

import data.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import data.feipData.RemarkHistory;
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

@WebServlet(name = ApipApiNames.REMARK_OP_HISTORY, value = "/"+ ApipApiNames.SN_26+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.REMARK_OP_HISTORY)
public class RemarkOpHistory extends HttpServlet {
    private final Settings settings = Initiator.settings;
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        FcHttpRequestHandler fcHttpRequestHandler = new FcHttpRequestHandler(settings);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.REMARK_HISTORY, RemarkHistory.class, null,null,OP,RATE, defaultSort,request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        FcHttpRequestHandler fcHttpRequestHandler = new FcHttpRequestHandler(settings);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.REMARK_HISTORY, RemarkHistory.class, null,null,OP,RATE, defaultSort,request,response,authType);
    }
} 