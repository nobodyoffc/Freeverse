package APIP5V1_Code;

import apip.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import feip.feipData.CodeHistory;
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
import static constants.FieldNames.HEIGHT;
import static constants.FieldNames.INDEX;

@WebServlet(name = ApipApiNames.CODE_OP_HISTORY, value = "/"+ ApipApiNames.SN_5+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.CODE_OP_HISTORY)
public class CodeOpHistory extends HttpServlet {
    private final Settings settings = Initiator.settings;
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(settings);
        fcdslRequestHandler.doSearchRequest(IndicesNames.CODE_HISTORY, CodeHistory.class, null,null,OP,RATE, defaultSort,request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(settings);
        fcdslRequestHandler.doSearchRequest(IndicesNames.CODE_HISTORY, CodeHistory.class, null,null,OP,RATE, defaultSort,request,response,authType);
    }
}