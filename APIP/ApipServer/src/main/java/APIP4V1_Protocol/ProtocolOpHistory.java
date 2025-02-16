package APIP4V1_Protocol;

import apip.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import feip.feipData.ProtocolHistory;
import initial.Initiator;
import tools.http.AuthType;
import server.FcdslRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import appTools.Settings;

import static constants.OpNames.RATE;
import static constants.Strings.OP;


@WebServlet(name = ApipApiNames.PROTOCOL_OP_HISTORY, value = "/"+ ApipApiNames.SN_4+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.PROTOCOL_OP_HISTORY)

public class ProtocolOpHistory extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public ProtocolOpHistory() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList("height",false,"index",false,null,null);
        fcdslRequestHandler.doSearchRequest(IndicesNames.PROTOCOL_HISTORY, ProtocolHistory.class, null,null,OP,RATE, defaultSort,request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList("height",false,"index",false,null,null);
        fcdslRequestHandler.doSearchRequest(IndicesNames.PROTOCOL_HISTORY, ProtocolHistory.class, null,null,OP,RATE, defaultSort,request,response,authType);
    }
}