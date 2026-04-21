package APIP21V1_News;

import constants.ApipApiNames;
import data.apipData.Sort;
import constants.IndicesNames;
import data.fcData.News;
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
import static constants.FieldNames.*;

@WebServlet(name = ApipApiNames.NEWS_SEARCH, value = "/"+ ApipApiNames.SN_21+"/"+ ApipApiNames.NEWS_SEARCH +"/"+ ApipApiNames.VER_1)
public class NewsSearch extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public NewsSearch() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.ENCRYPTED;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,ID,true,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.NEWS, News.class, defaultSort, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(HEIGHT,false,ID,true,null,null);
        fcHttpRequestHandler.doSearchRequest(IndicesNames.NEWS, News.class, defaultSort, request,response,authType);
    }
}
