
package APIP14V1_Proof;


import apip.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import feip.feipData.Proof;
import initial.Initiator;
import tools.http.AuthType;
import server.FcdslRequestHandler;
import appTools.Settings;
import static constants.FieldNames.ID;    
import static constants.FieldNames.LAST_HEIGHT;
import static constants.Strings.ACTIVE;
import static constants.Values.FALSE;


@WebServlet(name = ApipApiNames.PROOF_SEARCH, value = "/"+ ApipApiNames.SN_14+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.PROOF_SEARCH)
public class ProofSearch extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public ProofSearch() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true, null,null);
        fcdslRequestHandler.doSearchRequest(IndicesNames.PROOF, Proof.class, null,null,ACTIVE,FALSE,defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true, null,null);
        fcdslRequestHandler.doSearchRequest(IndicesNames.PROOF, Proof.class, null,null,ACTIVE,FALSE,defaultSort, request,response,authType);
    }
}