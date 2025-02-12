package APIP18V1_Wallet;

import handlers.CashHandler;
import fch.ParseTools;
import constants.ApiNames;
import constants.CodeMessage;
import fcData.FcReplierHttp;
import fch.*;
import handlers.CashHandler.SearchResult;
import fch.fchData.Cash;
import fch.fchData.SendTo;
import initial.Initiator;
import tools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static constants.Constants.Dust;
import static constants.FieldNames.DATA_FOR_OFF_LINE_TX;
import static fch.CryptoSign.parseDataForOffLineTxFromOther;


@WebServlet(name = ApiNames.OffLineTx, value = "/"+ApiNames.SN_18+"/"+ApiNames.Version1 +"/"+ApiNames.OffLineTx)
public class OffLineTx extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid, request, response, authType, Initiator.jedisPool);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(Initiator.sid,request, response, authType, Initiator.jedisPool);
    }
    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, JedisPool jedisPool) throws IOException {
        FcReplierHttp replier = new FcReplierHttp(sid,response);
        //Check authorization
        try (Jedis jedis = jedisPool.getResource()) {
            //Do FCDSL other request
            Map<String, String> other = RequestChecker.checkOtherRequest(sid, request, authType, replier, jedis, Initiator.sessionHandler);
            if (other == null) return;
            //Do this request
            String json = other.get(DATA_FOR_OFF_LINE_TX);
            DataForOffLineTx dataForSignInCs = parseDataForOffLineTxFromOther(json);

            //Check API
            if(dataForSignInCs==null) {
                replier.replyHttp(CodeMessage.Code1012BadQuery,null,jedis);
                return;
            }

            String fromFid = dataForSignInCs.getFromFid();
            Long cd = dataForSignInCs.getCd();
            List<SendTo> sendToList = dataForSignInCs.getSendToList();
            int outputSize=0;
            if(sendToList!=null)outputSize=sendToList.size();
            String msg = dataForSignInCs.getMsg();
            int msgSize=0;
            if(msg!=null)msgSize = msg.getBytes().length;

            long amount = 0;
            if(dataForSignInCs.getSendToList()!=null && !dataForSignInCs.getSendToList().isEmpty()) {
                for (SendTo sendTo : dataForSignInCs.getSendToList()) {
                    if (sendTo.getAmount() < Dust) {
                        replier.replyOtherErrorHttp("The amount must be more than "+Dust+"fch.",null,jedis);
                        return;
                    }
                    amount += ParseTools.coinToSatoshi(sendTo.getAmount());
                }
            }
            SearchResult<Cash> cashListReturn=null;

            if(cd!=null) {
                cashListReturn = CashHandler.getValidCashes(fromFid,amount, cd, null, outputSize, msgSize,null,Initiator.esClient,Initiator.jedisPool);
            }
            if(cashListReturn==null){
                replier.replyOtherErrorHttp("Can't get cashes. Check ES.",null,jedis);
                return;
            }
            List<Cash> meetList = cashListReturn.getData();
            if(meetList==null || meetList.isEmpty()){
                replier.replyOtherErrorHttp(cashListReturn.getMessage(),null,jedis);
                return;
            }

            long totalValue = 0;
            for(Cash cash :meetList){
                totalValue += cash.getValue();
            }

            if(totalValue < amount + 1000){
                replier.replyOtherErrorHttp("Cashes meeting this cd can not match the total amount of outputs.",null,jedis);
                return;
            }

            String rawTxForCs = CryptoSign.makeRawTxForCs(dataForSignInCs,meetList);
            replier.replySingleDataSuccess(rawTxForCs, jedis);
        }
    }
}