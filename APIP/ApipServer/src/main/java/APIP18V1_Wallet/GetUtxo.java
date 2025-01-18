package APIP18V1_Wallet;

import handlers.CashHandler;
import constants.*;
import crypto.KeyTools;
import fcData.FcReplierHttp;
import fch.ParseTools;
import handlers.CashHandler.SearchResult;
import fch.fchData.Cash;
import initial.Initiator;
import tools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.RequestCheckResult;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

import static constants.FieldNames.AMOUNT;
import static constants.IndicesNames.ADDRESS;


@WebServlet(name = ApiNames.GetUtxo, value = "/"+ApiNames.SN_18+"/"+ApiNames.Version1 +"/"+ApiNames.GetUtxo)
public class GetUtxo extends HttpServlet {


    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }


    @SuppressWarnings("null")
    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, JedisPool jedisPool) {
        FcReplierHttp replier = new FcReplierHttp(sid,response);
        try(Jedis jedis = jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(Initiator.sid, request, replier, authType, jedis, false, Initiator.sessionHandler);
            if (requestCheckResult==null){
                return;
            }
            String idRequested = request.getParameter(ADDRESS);
            if (idRequested==null || !KeyTools.isValidFchAddr(idRequested)){
                replier.replyHttp(CodeMessage.Code2003IllegalFid,null,jedis);
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

            SearchResult<Cash> searchResult = CashHandler.getValidCashes(idRequested,amount,cd,null,0,0,null,Initiator.esClient,Initiator.jedisPool);
            if (searchResult.hasError()) {
                replier.replyHttp(CodeMessage.Code1020OtherError, searchResult.getMessage(), jedis);
                return;
            }

            List<apip.apipData.Utxo> utxoList = new ArrayList<>();
            for(Cash cash:searchResult.getData())
                utxoList.add(apip.apipData.Utxo.cashToUtxo(cash));
    
            replier.setGot((long) utxoList.size());
            replier.setTotal(searchResult.getTotal());
            replier.reply0SuccessHttp(utxoList, jedis, null);
        }
    }
}