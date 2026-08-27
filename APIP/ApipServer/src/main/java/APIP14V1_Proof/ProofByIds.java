
package APIP14V1_Proof;


import constants.ApipApiNames;
import constants.IndicesNames;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import data.feipData.Proof;
import initial.Initiator;
import utils.http.AuthType;
import config.Settings;
import static constants.FieldNames.ID;
import server.FcHttpRequestHandler;


@WebServlet(name = ApipApiNames.PROOF_BY_IDS, value = "/"+ ApipApiNames.SN_14+"/"+ ApipApiNames.PROOF_BY_IDS +"/"+ ApipApiNames.VER_1)
public class ProofByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public ProofByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.ENCRYPTED;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.PROOF, Proof.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.PROOF, Proof.class, ID, request,response,authType);
    }
}