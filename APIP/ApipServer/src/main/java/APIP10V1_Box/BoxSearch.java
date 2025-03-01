package APIP10V1_Box;

import apip.apipData.Sort;
import server.ApipApiNames;
import constants.IndicesNames;
import feip.feipData.Box;
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

import static constants.FieldNames.*;

@WebServlet(name = ApipApiNames.BOX_SEARCH, value = "/"+ ApipApiNames.SN_10+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.BOX_SEARCH)
public class BoxSearch extends HttpServlet {
    private final FcdslRequestHandler fcdslRequestHandler;

    public BoxSearch() {
        Settings settings = Initiator.settings;
        this.fcdslRequestHandler = new FcdslRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true, null,null);
        fcdslRequestHandler.doSearchRequest(IndicesNames.BOX, Box.class, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT,false,ID,true, null,null);
        fcdslRequestHandler.doSearchRequest(IndicesNames.BOX, Box.class, defaultSort, request,response,authType);
    }
}