package APIP3V1_Freer;

import constants.ApipApiNames;
import data.apipData.Sort;
import constants.FieldNames;
import initial.Initiator;
import utils.http.AuthType;
import server.FcHttpRequestHandler;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import config.Settings;
import static constants.FieldNames.ID;

@WebServlet(name = ApipApiNames.FREER_SEARCH, value = "/"+ ApipApiNames.SN_3+"/"+ ApipApiNames.FREER_SEARCH +"/"+ ApipApiNames.VER_1)
public class FreerSearch extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public FreerSearch() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.ENCRYPTED;
        ArrayList<Sort> sort = Sort.makeSortList(FieldNames.LAST_HEIGHT,false,ID,true,null,null);
        fcHttpRequestHandler.doCidInfoSearchRequest(sort, request, response, authType);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> sort = Sort.makeSortList(FieldNames.LAST_HEIGHT,false,ID,true,null,null);
        fcHttpRequestHandler.doCidInfoSearchRequest(sort, request, response, authType);
    }

    
}