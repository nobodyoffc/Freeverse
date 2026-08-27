package APIP23V1_Remark;

import constants.ApipApiNames;
import data.apipData.Sort;
import constants.IndicesNames;
import data.feipData.RemarkHistory;
import initial.Initiator;
import utils.http.AuthType;
import server.FcHttpRequestHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import config.Settings;
import static constants.OpNames.RATE;
import static constants.Strings.OP;
import static constants.FieldNames.HEIGHT;
import static constants.FieldNames.INDEX;

@WebServlet(name = ApipApiNames.REMARK_RATE_HISTORY, value = "/"+ ApipApiNames.SN_23+"/"+ ApipApiNames.REMARK_RATE_HISTORY +"/"+ ApipApiNames.VER_1)
public class RemarkRateHistory extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public RemarkRateHistory() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.ENCRYPTED;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.REMARK_HISTORY, RemarkHistory.class, OP,RATE,null,null, defaultSort,request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.REMARK_HISTORY, RemarkHistory.class, OP,RATE,null,null, defaultSort,request,response,authType);
    }
} 