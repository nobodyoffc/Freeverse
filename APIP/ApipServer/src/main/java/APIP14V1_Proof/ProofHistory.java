
package APIP14V1_Proof;

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


@WebServlet(name = ApipApiNames.PROOF_HISTORY, value = "/"+ ApipApiNames.SN_14+"/"+ ApipApiNames.PROOF_HISTORY +"/"+ ApipApiNames.VER_1)
public class ProofHistory extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public ProofHistory() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.ENCRYPTED;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.PROOF_HISTORY, data.feipData.ProofHistory.class, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.PROOF_HISTORY,ProofHistory.class, defaultSort, request,response,authType);
    }
}