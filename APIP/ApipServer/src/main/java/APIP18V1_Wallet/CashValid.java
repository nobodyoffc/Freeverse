package APIP18V1_Wallet;

import apip.apipData.Fcdsl;
import apip.apipData.RequestBody;
import apip.apipData.Sort;
import handlers.CashHandler;
import handlers.MempoolHandler;
import server.ApipApiNames;
import constants.FieldNames;
import constants.CodeMessage;
import fcData.ReplyBody;
import handlers.CashHandler.SearchResult;
import handlers.Handler.HandlerType;
import fch.fchData.Cash;
import initial.Initiator;
import utils.ObjectUtils;
import utils.http.AuthType;
import server.FcHttpRequestHandler;
import server.HttpRequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static constants.FieldNames.*;
import static constants.IndicesNames.CASH;
import static constants.Strings.FID;
import static constants.Values.FALSE;
import static constants.Values.TRUE;
import static utils.FchUtils.coinToSatoshi;

import appTools.Settings;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import feip.feipData.Service;

@WebServlet(name = ApipApiNames.CASH_VALID, value = "/"+ ApipApiNames.SN_18+"/"+ ApipApiNames.VERSION_1 +"/"+ ApipApiNames.CASH_VALID)
public class CashValid extends HttpServlet {
    private final Settings settings = Initiator.settings;
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FREE;
        doRequest(request, response, authType,settings);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(request, response, authType,settings);
    }


    protected void doRequest(HttpServletRequest request, HttpServletResponse response, AuthType authType, Settings settings) {
        ReplyBody replier = new ReplyBody(settings);

        //Do FCDSL other request
        HttpRequestChecker httpRequestChecker = new HttpRequestChecker(settings, replier);
        httpRequestChecker.checkRequestHttp(request, response, authType);
        //Do this request
        doCashValidRequest(replier, request,response,settings);
        
    }

    protected void doCashValidRequest(ReplyBody replier, HttpServletRequest request, HttpServletResponse response, Settings settings) {

        RequestBody requestBody = replier.getRequestChecker().getRequestBody();
        if(requestBody!=null)replier.setNonce(requestBody.getNonce());

        String fid = null;
        Long amount = null;
        Long cd = null;
        String amountStr = null;
        String cdStr = null;
        String msgSizeStr = null;
        String outputSizeStr = null;
        String sinceHeightStr = null;
        int msgSize = 0;
        int outputSize = 0;
        long sinceHeight = 0;
        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(Service.ServiceType.ES);
        if (requestBody != null && requestBody.getFcdsl() != null) {
            if (requestBody.getFcdsl().getOther() == null) {
                ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_TIME, false, ID, true, null, null);
                Fcdsl fcdsl;
                fcdsl = requestBody.getFcdsl();
                if (fcdsl.getFilter() == null)
                    fcdsl.addNewFilter().addNewTerms().addNewFields(VALID).addNewValues(TRUE);
                else if (fcdsl.getFilter().getMatch() == null)
                    fcdsl.getFilter().addNewMatch().addNewFields(VALID).addNewValue(TRUE);
                else if (fcdsl.getExcept() == null)
                    fcdsl.addNewExcept().addNewTerms().addNewFields(VALID).addNewValues(FALSE);
                else if (fcdsl.getQuery() == null)
                    fcdsl.addNewQuery().addNewTerms().addNewFields(VALID).addNewValues(TRUE);
                else if (fcdsl.getQuery().getTerms() == null)
                    fcdsl.getQuery().addNewTerms().addNewFields(VALID).addNewValues(TRUE);
                FcHttpRequestHandler fcHttpRequestHandler = new FcHttpRequestHandler(replier, settings);
                List<Cash> meetList = fcHttpRequestHandler.doRequest(CASH, defaultSort, Cash.class);
                if(meetList==null){
                    try {
                        response.getWriter().write(fcHttpRequestHandler.getFinalReplyJson());
                    } catch (IOException ignore) {
                        return;
                    }
                }
                MempoolHandler mempoolHandler = (MempoolHandler) settings.getHandler(HandlerType.MEMPOOL);
                mempoolHandler.updateUnconfirmedValidCash(meetList, fid);
                replier.reply0SuccessHttp(meetList,response);
                return;
            } else {
                try {
                    Object other = requestBody.getFcdsl().getOther();
                    Map<String, String> otherMap;
                    if (other != null) {
                        otherMap = ObjectUtils.objectToMap(other, String.class, String.class);
                        if (otherMap != null && !otherMap.isEmpty()) {
                            fid = otherMap.get(FID);
                            amountStr = otherMap.get(FieldNames.AMOUNT);
                            cdStr = otherMap.get(FieldNames.CD);
                            msgSizeStr = otherMap.get(FieldNames.MSG_SIZE);
                            outputSizeStr = otherMap.get(FieldNames.OUTPUT_SIZE);
                            sinceHeightStr = otherMap.get(FieldNames.SINCE_HEIGHT);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        } else if (requestBody == null) {
            fid = request.getParameter(FieldNames.FID);
            amountStr = request.getParameter(AMOUNT);
            cdStr = request.getParameter(CD);
            msgSizeStr = request.getParameter(FieldNames.MSG_SIZE);
            outputSizeStr = request.getParameter(FieldNames.OUTPUT_SIZE);
            sinceHeightStr = request.getParameter(FieldNames.SINCE_HEIGHT);
        }

        try {
            if (amountStr != null) amount = coinToSatoshi(Double.parseDouble(amountStr));
            if (cdStr != null) cd = Long.parseLong(cdStr);
            if (msgSizeStr != null) msgSize = Integer.parseInt(msgSizeStr);
            if (outputSizeStr != null) outputSize = Integer.parseInt(outputSizeStr);
            if (sinceHeightStr != null) sinceHeight = Long.parseLong(sinceHeightStr);
        } catch (Exception ignore) {
        }
        MempoolHandler mempoolHandler = (MempoolHandler) settings.getHandler(HandlerType.MEMPOOL);
        SearchResult<Cash> searchResult = CashHandler.getValidCashes(fid, amount, cd, sinceHeight, outputSize, msgSize, null, esClient, mempoolHandler);
        if (searchResult.hasError()) {
            replier.replyHttp(CodeMessage.Code1020OtherError, searchResult.getMessage(),response);
            return;
        }
        replier.setGot(searchResult.getGot());
        replier.setTotal(searchResult.getTotal());
        List<Cash> cashList = searchResult.getData();
        replier.replySingleDataSuccessHttp(cashList,response);
    }


}