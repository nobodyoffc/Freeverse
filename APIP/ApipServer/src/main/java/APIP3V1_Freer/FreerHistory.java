package APIP3V1_Freer;

import constants.ApipApiNames;
import data.apipData.Sort;
import constants.FieldNames;
import constants.IndicesNames;
import data.feipData.FreerHist;
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

import static constants.FieldNames.HEIGHT;
import static constants.FieldNames.INDEX;

@WebServlet(name = ApipApiNames.FREER_HISTORY, value = "/"+ ApipApiNames.SN_3+"/"+ ApipApiNames.FREER_HISTORY +"/"+ ApipApiNames.VER_1)
public class FreerHistory extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public FreerHistory() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.ENCRYPTED;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.FREER_HISTORY, FreerHist.class, FieldNames.SN,"3", null, null, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.FREER_HISTORY, FreerHist.class,"sn","3", null, null, defaultSort, request,response,authType);
    }
}
