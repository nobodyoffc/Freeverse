package APIP8V1_Group;

import apip.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import feip.feipData.GroupHistory;
import initial.Initiator;
import utils.http.AuthType;
import server.FcdslRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

import appTools.Settings;
import static constants.FieldNames.INDEX;
import static constants.OpNames.RATE;
import static constants.Strings.HEIGHT;
import static constants.Strings.OP;


@WebServlet(name = ApipApiNames.GROUP_OP_HISTORY, value = "/"+ ApipApiNames.SN_8+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.GROUP_OP_HISTORY)

public class GroupOpHistory extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public GroupOpHistory() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        fcdslRequestHandler.doSearchRequest(IndicesNames.GROUP_HISTORY, GroupHistory.class, null,null,OP,RATE, defaultSort,request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,INDEX,false,null,null);
        fcdslRequestHandler.doSearchRequest(IndicesNames.GROUP_HISTORY, GroupHistory.class, null,null,OP,RATE, defaultSort,request,response,authType);
    }
}