package APIP18V1_Wallet;

import apip.apipData.Fcdsl;
import apip.apipData.RequestBody;
import apip.apipData.Sort;
import handlers.CashHandler;
import constants.ApiNames;
import constants.FieldNames;
import constants.CodeMessage;
import fcData.FcReplierHttp;
import handlers.CashHandler.SearchResult;
import fch.fchData.Cash;
import initial.Initiator;
import tools.ObjectTools;
import tools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.FcdslRequestHandler;
import server.RequestCheckResult;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static constants.FieldNames.*;
import static constants.IndicesNames.CASH;
import static constants.Strings.FID;
import static constants.Values.FALSE;
import static constants.Values.TRUE;
import static fch.ParseTools.coinToSatoshi;


@WebServlet(name = ApiNames.CashValid, value = "/"+ApiNames.SN_18+"/"+ApiNames.Version1 +"/"+ApiNames.CashValid)
public class CashValid extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FREE;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }


    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, JedisPool jedisPool) {
        FcReplierHttp replier = new FcReplierHttp(sid,response);
        try(Jedis jedis = jedisPool.getResource()) {
            //Do FCDSL other request
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(Initiator.sid, request, replier, authType, jedis, false, Initiator.sessionHandler);
            if (requestCheckResult == null) {
                return;
            }
            //Do this request
            doCashValidRequest(replier, jedis,  request);
        }
    }

    protected void doCashValidRequest(FcReplierHttp replier, Jedis jedis, HttpServletRequest request) {

        RequestBody requestBody = replier.getRequestCheckResult().getRequestBody();
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
        
        if(requestBody!=null && requestBody.getFcdsl()!=null) {
            if( requestBody.getFcdsl().getOther()==null) {
                ArrayList<Sort> defaultSort = Sort.makeSortList(LAST_TIME,false,CASH_ID,true,null,null);
                Fcdsl fcdsl;
                fcdsl = requestBody.getFcdsl();
                if(fcdsl.getFilter()==null)fcdsl.addNewFilter().addNewTerms().addNewFields(VALID).addNewValues(TRUE);
                else if(fcdsl.getFilter().getMatch()==null)fcdsl.getFilter().addNewMatch().addNewFields(VALID).addNewValue(TRUE);
                else if(fcdsl.getExcept()==null)fcdsl.addNewExcept().addNewTerms().addNewFields(VALID).addNewValues(FALSE);
                else if(fcdsl.getQuery()==null)fcdsl.addNewQuery().addNewTerms().addNewFields(VALID).addNewValues(TRUE);
                else if(fcdsl.getQuery().getTerms()==null)fcdsl.getQuery().addNewTerms().addNewFields(VALID).addNewValues(TRUE);
                FcdslRequestHandler fcdslRequestHandler = new FcdslRequestHandler(requestBody, replier, Initiator.esClient);
                List<Cash> meetList = fcdslRequestHandler.doRequest(CASH, defaultSort, Cash.class, jedis);
                CashHandler.checkUnconfirmedSpentByJedis(meetList, jedis);
                replier.reply0SuccessHttp(meetList, jedis, null);
                return;
            }else {
                try {
                    Object other = requestBody.getFcdsl().getOther();
                    Map<String, String> otherMap;
                    if (other != null) {
                        otherMap = ObjectTools.objectToMap(other, String.class, String.class);
                        if (otherMap != null && !otherMap.isEmpty()) {
                            fid = otherMap.get(FID);
                            amountStr = otherMap.get(FieldNames.AMOUNT);
                            cdStr = otherMap.get(FieldNames.CD);
                            msgSizeStr = otherMap.get(FieldNames.MSG_SIZE);
                            outputSizeStr = otherMap.get(FieldNames.OUTPUT_SIZE);
                            sinceHeightStr = otherMap.get(FieldNames.SINCE_HEIGHT);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }else if(requestBody==null){
            fid = request.getParameter(FieldNames.FID);
            amountStr= request.getParameter(AMOUNT);
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
        }catch (Exception ignore){}

        SearchResult<Cash> searchResult = CashHandler.getValidCashes(fid,amount,cd,sinceHeight,outputSize,msgSize,null,Initiator.esClient,Initiator.jedisPool);
        if (searchResult.hasError()) {
            replier.replyHttp(CodeMessage.Code1020OtherError, searchResult.getMessage(), jedis);
            return;
        }
        replier.setGot(searchResult.getGot());
        replier.setTotal(searchResult.getTotal());
        List<Cash> cashList = searchResult.getData();
        CashHandler.checkUnconfirmedSpentByJedis(cashList, jedis);
        replier.reply0SuccessHttp(cashList, jedis, null);
     }
}