import apip.apipData.RequestBody;
import constants.ApiNames;
import constants.ReplyCodeMessage;
import fcData.FcReplier;
import fch.CashListReturn;
import fch.fchData.Cash;
import initial.Initiator;
import javaTools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

import static fch.Wallet.getCashListForPay;


@WebServlet(name = ApiNames.CashValid, value = "/"+ApiNames.SN_18+"/"+ApiNames.Version2 +"/"+ApiNames.CashValid)
public class CashValidForPay extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }


    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, JedisPool jedisPool) {
        FcReplier replier = new FcReplier(sid,response);
        try(Jedis jedis = jedisPool.getResource()) {
            //Do FCDSL other request
            Object other = RequestChecker.checkOtherRequest(sid, request, authType, replier, jedis);
            if (other == null) return;
            //Do this request
            doCashValidRequest(replier, jedis, other);
        }
    }


    protected void doCashValidRequest(FcReplier replier,Jedis jedis,Object other) {

        RequestBody requestBody = replier.getRequestCheckResult().getRequestBody();
        replier.setNonce(requestBody.getNonce());
        //Check API
        String addrRequested;
        try{
            addrRequested = requestBody.getFcdsl().getQuery().getTerms().getValues()[0];
        }catch (Exception ignore){
            replier.reply(ReplyCodeMessage.Code1012BadQuery,null,jedis);
            return;
        }

        long amount = 0;
        try {
            amount = (long)(Double.parseDouble((String)other)*100000000);
            if(amount<=0){
                replier.replyOtherError("amount <= 0",null,jedis);
                return;
            }
        } catch (Exception e) {
            replier.replyOtherError(e.getMessage(),null,jedis);
            return;
        }

        //response
        CashListReturn cashListReturn = getCashListForPay(amount,addrRequested,Initiator.esClient);

        if(cashListReturn.getCode()!=0){
            replier.replyOtherError(cashListReturn.getMsg(),null,jedis);
            return;
        }

        List<Cash> meetList = cashListReturn.getCashList();
        replier.setData(meetList);
        replier.setGot((long) meetList.size());
        replier.setTotal(cashListReturn.getTotal());
        replier.reply0Success(meetList,jedis, null);
    }
}