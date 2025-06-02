package APIP24V1_Book;

import server.ApipApiNames;
import constants.IndicesNames;
import data.feipData.Book;
import initial.Initiator;
import utils.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import config.Settings;
import static constants.FieldNames.ID;
import server.FcHttpRequestHandler;

@WebServlet(name = ApipApiNames.BOOK_BY_IDS, value = "/"+ ApipApiNames.SN_24+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.BOOK_BY_IDS)
public class BookByIds extends HttpServlet {
    private final FcHttpRequestHandler fcHttpRequestHandler;

    public BookByIds() {
        Settings settings = Initiator.settings;
        this.fcHttpRequestHandler = new FcHttpRequestHandler(settings);
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.BOOK, Book.class, ID, request,response,authType);
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        fcHttpRequestHandler.doIdsRequest(IndicesNames.BOOK, Book.class, ID, request,response,authType);
    }
} 