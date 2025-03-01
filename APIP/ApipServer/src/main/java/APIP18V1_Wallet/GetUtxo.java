package APIP18V1_Wallet;

import appTools.Settings;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import feip.feipData.Service;
import handlers.CashHandler;
import constants.*;
import crypto.KeyTools;
import fcData.ReplyBody;
import fch.ParseTools;
import handlers.CashHandler.SearchResult;
import handlers.Handler.HandlerType;
import fch.fchData.Cash;
import initial.Initiator;
import server.ApipApiNames;
import tools.http.AuthType;
import server.HttpRequestChecker;
import handlers.MempoolHandler;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

import static constants.FieldNames.AMOUNT;
import static constants.FieldNames.ADDRESS;


@WebServlet(name = ApipApiNames.GET_UTXO, value = "/"+ ApipApiNames.SN_18+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.GET_UTXO)
public class GetUtxo extends HttpServlet {
    private final Settings settings = Initiator.settings;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(request, response, authType, settings);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request, response, authType, settings);
    }


    @SuppressWarnings("null")
    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) {
        ReplyBody replier = new ReplyBody(settings);

        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        httpRequestChecker.checkRequestHttp(request, response, authType);
        String idRequested = request.getParameter(ADDRESS);
        if (idRequested==null || !KeyTools.isValidFchAddr(idRequested)){
            replier.replyHttp(CodeMessage.Code2003IllegalFid,null,response);
            return;
        }

        Long amount;
        Long cd;

        if(request.getParameter(AMOUNT)!=null){
            amount= ParseTools.coinStrToSatoshi(request.getParameter(AMOUNT));
        }else{
            amount=0L;
        }
        if(request.getParameter(FieldNames.CD)!=null){
            cd= Long.parseLong(request.getParameter(FieldNames.CD));
        }else{
            cd=0L;
        }
        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        MempoolHandler mempoolHandler = (MempoolHandler) settings.getHandler(HandlerType.MEMPOOL);
        SearchResult<Cash> searchResult = CashHandler.getValidCashes(idRequested,amount,cd,null,0,0,null,esClient, mempoolHandler);
        if (searchResult.hasError()) {
            replier.replyHttp(CodeMessage.Code1020OtherError, searchResult.getMessage(),response);
            return;
        }

        List<apip.apipData.Utxo> utxoList = new ArrayList<>();
        for(Cash cash:searchResult.getData())
            utxoList.add(apip.apipData.Utxo.cashToUtxo(cash));

        replier.setGot((long) utxoList.size());
        replier.setTotal(searchResult.getTotal());
        replier.replySingleDataSuccessHttp(utxoList,response);
    }
}