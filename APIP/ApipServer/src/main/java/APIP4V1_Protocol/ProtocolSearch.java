package APIP4V1_Protocol;

import data.apipData.Sort;
import server.ApipApiNames;
import constants.FieldNames;
import constants.IndicesNames;
import data.feipData.Protocol;
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
import static constants.FieldNames.ID;
import static constants.Strings.ACTIVE;


@WebServlet(name = ApipApiNames.PROTOCOL_SEARCH, value = "/"+ ApipApiNames.SN_4+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.PROTOCOL_SEARCH)
public class ProtocolSearch extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public ProtocolSearch() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(ACTIVE, false, FieldNames.T_RATE, false, ID, true);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.PROTOCOL, Protocol.class, defaultSort, request, response, authType);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(ACTIVE, false, FieldNames.T_RATE, false, ID, true);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.PROTOCOL, Protocol.class, defaultSort, request, response, authType);
    }
}