package APIP18V1_Wallet;

import apip.apipData.UnconfirmedInfo;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import constants.ApiNames;
import constants.ReplyCodeMessage;
import fcData.FcReplierHttp;
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
import java.util.Map;

import static constants.FieldNames.*;

@WebServlet(name = ApiNames.UnconfirmedCashes, value = "/"+ApiNames.SN_18+"/"+ApiNames.Version1 +"/"+ApiNames.UnconfirmedCashes)
public class UnconfirmedCashes extends HttpServlet {
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_URL;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        AuthType authType = AuthType.FC_SIGN_BODY;
        doRequest(Initiator.sid,request, response, authType,Initiator.jedisPool);
    }


    protected void doRequest(String sid, HttpServletRequest request, HttpServletResponse response, AuthType authType, JedisPool jedisPool)  {
        FcReplierHttp replier = new FcReplierHttp(sid,response);

        //Check authorization
        try (Jedis jedis = jedisPool.getResource()) {
            RequestCheckResult requestCheckResult = RequestChecker.checkRequest(sid, request, replier, authType, jedis, false);
            if (requestCheckResult == null) {
                return;
            }
            List<String> fidList = null;
            if(requestCheckResult.getRequestBody()!=null && requestCheckResult.getRequestBody().getFcdsl()!=null && requestCheckResult.getRequestBody().getFcdsl().getIds()!=null){
                fidList = requestCheckResult.getRequestBody().getFcdsl().getIds();
            }
            List<Cash> meetList = new ArrayList<>();
            jedis.select(3);
            

            Map<String, String> spentCashesStringMap = jedis.hgetAll(SPEND_CASHES);
            for(Map.Entry<String, String> entry: spentCashesStringMap.entrySet()){
                Cash cash = new Gson().fromJson(entry.getValue(), Cash.class);
                if(fidList!=null && fidList.size()>0){
                    if(fidList.contains(cash.getOwner())){
                        meetList.add(cash);
                    }
                }else{
                    meetList.add(cash);
                }   
            }
            Map<String, String> newCashesStringMap = jedis.hgetAll(NEW_CASHES);
            for(Map.Entry<String, String> entry: newCashesStringMap.entrySet()){
                Cash cash = new Gson().fromJson(entry.getValue(), Cash.class);
                if(fidList!=null && fidList.size()>0){
                    if(fidList.contains(cash.getOwner())){
                        meetList.add(cash);
                    }
                }else{
                    meetList.add(cash);
                }
            }
            replier.replySingleDataSuccess(meetList,jedis);
        }
    }
}
