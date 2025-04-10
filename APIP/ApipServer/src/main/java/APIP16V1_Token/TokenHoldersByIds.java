package APIP16V1_Token;

import apip.apipData.Sort;
import appTools.Settings;
import constants.IndicesNames;
import fcData.ReplyBody;
import feip.feipData.TokenHolder;
import initial.Initiator;
import server.ApipApiNames;
import server.FcHttpRequestHandler;
import utils.http.AuthType;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static constants.FieldNames.ID;
import static constants.FieldNames.LAST_HEIGHT;


@WebServlet(name = ApipApiNames.TOKEN_HOLDERS_BY_IDS, value = "/"+ ApipApiNames.SN_16+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.TOKEN_HOLDERS_BY_IDS)
public class TokenHoldersByIds extends HttpServlet {
    private final Settings settings;
    public TokenHoldersByIds() {
        settings = Initiator.settings;
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT, false, ID, true, null, null);
        doRequest(defaultSort,request,response,authType);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_HEIGHT, false, ID, true, null, null);
        doRequest(defaultSort,request,response,authType);
    }

    public void doRequest(List<Sort> sort, HttpServletRequest request, HttpServletResponse response, AuthType authType) {
        FcHttpRequestHandler fcHttpRequestHandler = new FcHttpRequestHandler(settings);

        List<TokenHolder> meetList = fcHttpRequestHandler.doRequestForList(IndicesNames.TOKEN_HOLDER, TokenHolder.class, null, null, null, null, sort, request, response, authType);
        if (meetList == null) return;
        ReplyBody replier = fcHttpRequestHandler.getReplyBody();
        Map<String, Map<String,Double>> meetMap = new HashMap<>();

        for (TokenHolder tokenHolder : meetList) {
            Map<String,Double> fidBalanceMap = meetMap.get(tokenHolder.getTokenId());
            if(fidBalanceMap==null)fidBalanceMap = new HashMap<>();
            fidBalanceMap.put(tokenHolder.getFid(),tokenHolder.getBalance());
            meetMap.put(tokenHolder.getTokenId(), fidBalanceMap);
        }
        replier.replySingleDataSuccessHttp(meetMap,response);
    }
}