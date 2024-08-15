package APIP18V1_Wallet;

import apip.apipData.UnconfirmedInfo;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import constants.ApiNames;
import constants.ReplyCodeMessage;
import fcData.FcReplier;
import initial.Initiator;
import javaTools.http.AuthType;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import server.RequestCheckResult;
import server.RequestChecker;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static constants.FieldNames.*;
import static constants.Strings.FID;

@WebServlet(name = ApiNames.Unconfirmed, value = "/"+ApiNames.SN_18+"/"+ApiNames.Version2 +"/"+ApiNames.Unconfirmed)
public class Unconfirmed extends HttpServlet {
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }


    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, JedisPool jedisPool)  {
        FcReplier replier = new FcReplier(sid,response);
        //Check authorization
        try (Jedis jedis = jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(sid, request, replier, authType, jedis, false);
            if (requestCheckResult == null) {
                return;
            }
            if(requestCheckResult.getRequestBody()==null || requestCheckResult.getRequestBody().getFcdsl()==null){
                replier.reply(ReplyCodeMessage.Code1003BodyMissed,null,jedis);
                return;
            }

            if (requestCheckResult.getRequestBody().getFcdsl().getIds()==null) {
                replier.reply(ReplyCodeMessage.Code1015FidMissed,null,jedis);
                return;
            }
            List<UnconfirmedInfo> meetList = new ArrayList<>();
            jedis.select(3);
            for(String id: requestCheckResult.getRequestBody().getFcdsl().getIds()) {
                Map<String, String> resultMap = null;
                try {
                    resultMap = jedis.hgetAll(id);
                }catch(Exception e){
                    UnconfirmedInfo info = new UnconfirmedInfo();
                    info.setFid(id);
                    info.setIncomeCount(0);
                    info.setIncomeValue(0);
                    info.setSpendCount(0);
                    info.setSpendValue(0);
                    info.setNet(0);
                    meetList.add(info);
                    continue;
                }
                UnconfirmedInfo info = new UnconfirmedInfo();
                info.setFid(id);

                if (resultMap.get(IncomeCount) != null) info.setIncomeCount(Integer.parseInt(resultMap.get(IncomeCount)));
                if (resultMap.get(IncomeValue) != null) info.setIncomeValue(Long.parseLong(resultMap.get(IncomeValue)));
                if (resultMap.get(SpendCount) != null) info.setSpendCount(Integer.parseInt(resultMap.get(SpendCount)));
                if (resultMap.get(SpendValue) != null) info.setSpendValue(Long.parseLong(resultMap.get(SpendValue)));
                if(resultMap.get(TxValueMap)!=null){
                    Type mapType = new TypeToken<Map<String, Long>>(){}.getType();

                    info.setTxValueMap(new Gson().fromJson(resultMap.get(TxValueMap),mapType));
                }
                info.setNet(info.getIncomeValue() -info.getSpendValue());
                meetList.add(info);
            }
            replier.replySingleDataSuccess(meetList,jedis);
        }
    }
}
