
package APIP14V1_Proof;


import server.ApipApiNames;
import constants.IndicesNames;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import data.feipData.Proof;
import initial.Initiator;
import utils.http.AuthType;
import config.Settings;
import static constants.FieldNames.ID;
import server.FcHttpRequestHandler;


@WebServlet(name = ApipApiNames.PROOF_BY_IDS, value = "/"+ ApipApiNames.SN_14+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.PROOF_BY_IDS)
public class ProofByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public ProofByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.PROOF, Proof.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.PROOF, Proof.class, ID, request,response,authType);
    }
}