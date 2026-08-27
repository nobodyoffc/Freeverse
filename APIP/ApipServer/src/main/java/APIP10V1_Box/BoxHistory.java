package APIP10V1_Box;

import constants.ApipApiNames;
import data.apipData.Sort;
import constants.IndicesNames;
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
import static constants.FieldNames.HEIGHT;
import static constants.FieldNames.INDEX;


@WebServlet(name = ApipApiNames.BOX_HISTORY, value = "/"+ ApipApiNames.SN_10+"/"+ ApipApiNames.BOX_HISTORY +"/"+ ApipApiNames.VER_1)
public class BoxHistory extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public BoxHistory() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }   
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.ENCRYPTED;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.BOX_HISTORY, data.feipData.BoxHistory.class, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.BOX_HISTORY,BoxHistory.class, defaultSort, request,response,authType);
    }
}