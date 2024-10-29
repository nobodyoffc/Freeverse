package fcData;

import fch.ParseTools;
import com.google.gson.Gson;
import constants.ReplyCodeMessage;
import constants.Strings;
import crypto.Hash;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import server.RequestCheckResult;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static clients.redisClient.RedisTools.readHashLong;
import static constants.Strings.*;
import static appTools.Settings.addSidBriefToName;

public class FcReplierHttp {
    private Integer code;
    private String message;
    private Integer nonce;
    private Long balance;
    private Object data;
    private List<String> last;
    private Long got;
    private Long total;
    private Long bestHeight;
    private transient String sid;
    private transient RequestCheckResult requestCheckResult;
    private transient HttpServletResponse response;

    public FcReplierHttp() {
    }

    public FcReplierHttp(String sid, HttpServletResponse response) {
        this.sid = sid;
        this.response = response;
    }

    public static boolean isBadHex32(FcReplierHttp replier, Jedis jedis, String did) {
        if(!Hex.isHex32(did)){
            replier.replyOtherErrorHttp("It is not a hex string of a 32 byte array.", did, jedis);
            return true;
        }
        return false;
    }

    public void Set0Success() {
        this.code = ReplyCodeMessage.Code0Success;
        this.message = ReplyCodeMessage.Msg0Success;
    }

    public void Set1020Other(String message) {
        this.code = ReplyCodeMessage.Code1020OtherError;
        if(message==null)this.message = ReplyCodeMessage.Msg1020OtherError;
        else this.message = message;
    }
    public void SetCodeMessage(Integer code) {
        this.code = code;
        this.message = ReplyCodeMessage.getMsg(code);
    }

    public void printCodeMessage(){
        System.out.println(code+":"+message);
    }

    @Nullable
    public String getStringFromUrl(HttpServletRequest request, String name, Jedis jedis) {
        String value = request.getParameter(name);
        if (value == null) {
            replyOtherErrorHttp("Failed to get "+name+"From the URL.",null,jedis);
            return null;
        }
        return value;
    }

    public Long updateBalance(String sid, String api, Jedis jedis, Double price) {
        long length = this.toJson().length();
        return updateBalance(sid,api,length,jedis, price);
    }

    public Long updateBalance(String sid, String api, long length, Jedis jedis, Double price) {
        if(requestCheckResult.getFreeRequest()!=null && requestCheckResult.getFreeRequest().equals(Boolean.TRUE))
            return null;
        String fid = requestCheckResult.getFid();
        Map<String, String> paramsMap = jedis.hgetAll(addSidBriefToName(sid, PARAMS));

        if(fid==null)return null;
        if(fid.equals(paramsMap.get(ACCOUNT))){
            String minPay = paramsMap.get(MIN_PAYMENT);
            balance=ParseTools.coinToSatoshi(Double.parseDouble(minPay));
            return balance;
        }
        String sessionName = requestCheckResult.getSessionName();
        String via = requestCheckResult.getVia();
        long newBalance;
        if(price==null)price = Double.parseDouble(paramsMap.get(PRICE_PER_K_BYTES));
        long priceSatoshi = ParseTools.coinToSatoshi(price);
        long amount = length / 1000;
        long nPrice;
        if(api!=null)
            nPrice = readHashLong(jedis, addSidBriefToName(sid, N_PRICE), api);
        else nPrice=1;
        long cost = amount * priceSatoshi * nPrice;

        //update user balance
        long oldBalance = readHashLong(jedis, addSidBriefToName(sid, Strings.BALANCE), fid);
        newBalance = oldBalance - cost;
        if (newBalance < 0) {
            cost = oldBalance;
            jedis.hdel(addSidBriefToName(sid, Strings.BALANCE), fid);
            jedis.select(1);
            jedis.hdel(addSidBriefToName(sid, sessionName));
            jedis.select(0);
            newBalance = 0;
        } else
            jedis.hset(addSidBriefToName(sid, Strings.BALANCE), fid, String.valueOf(newBalance));

        //Update consume via balance
        if (via != null) {
            long oldViaBalance = readHashLong(jedis, addSidBriefToName(sid, CONSUME_VIA), via);
            long newViaBalance = oldViaBalance + cost;
            jedis.hset(addSidBriefToName(sid, CONSUME_VIA), via, String.valueOf(newViaBalance));
        }

        balance= Long.valueOf(newBalance);
        return newBalance;
    }

    public void symSign(String sessionKey) {
        if(sessionKey==null){
            return;
        }
        String json = this.toNiceJson();
        byte[] replyJsonBytes = json.getBytes();
        byte[] keyBytes = Hex.fromHex(sessionKey);
        byte[] bytes = BytesTools.bytesMerger(replyJsonBytes,keyBytes);
        byte[] signBytes = Hash.sha256x2(bytes);
        String sign = BytesTools.bytesToHexStringBE(signBytes);
        response.setHeader(ReplyCodeMessage.SignInHeader,sign);
    }
    public void replyHttp(int code, Object data, Jedis jedis){
        replyHttp(code,null,data,jedis, null);
    }
    public void reply0SuccessHttp(Object data, Jedis jedis, Double price) {
        replyHttp(0,null,data,jedis, price);
    }

    public void replySingleDataSuccess(Object data, Jedis jedis) {
        this.got=1L;
        this.total=1L;
        replyHttp(0,null,data,jedis, null);
    }

    public void reply0SuccessHttp(Jedis jedis) {
        replyHttp(0,null,data,jedis, null);
    }
    public void set0Success() {
        set0Success(null);
    }

    public void set0Success(Object data) {
        code = ReplyCodeMessage.Code0Success;
        message = ReplyCodeMessage.getMsg(code);
        this.data = data;
    }
    public void reply0SuccessHttp(Object data, HttpServletResponse response) {
        code = ReplyCodeMessage.Code0Success;
        message = ReplyCodeMessage.getMsg(code);
        this.data = data;
        try {
            response.getWriter().write(this.toNiceJson());
        } catch (IOException ignore) {
            System.out.println("Failed to reply success.");
        }
    }

    public void replyOtherErrorHttp(String otherError, Object data, Jedis jedis) {
        replyHttp(ReplyCodeMessage.Code1020OtherError,otherError,data,jedis, null);
    }
    public void setOtherError(String otherError) {
        code = ReplyCodeMessage.Code1020OtherError;
        if(otherError!=null)message = otherError;
        else message = ReplyCodeMessage.getMsg(code);
    }

    public void replyOtherErrorHttp(String otherError, HttpServletResponse response) {
        code = ReplyCodeMessage.Code1020OtherError;
        if(otherError!=null)message = otherError;
        else message = ReplyCodeMessage.getMsg(code);
        String replyStr = this.toNiceJson();
        try {
            response.getWriter().write(replyStr);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public String replyOtherJson(String otherError,Object data,Jedis jedis){
        return replyJson(ReplyCodeMessage.Code1020OtherError,otherError,data,jedis);
    }
    public String replyJson(int code,Object data,Jedis jedis){
        return replyJson(code,null,data,jedis);
    }
    public String replyJson(int code,String otherError,Object data,Jedis jedis){
        this.code = code;
        if(code==ReplyCodeMessage.Code1020OtherError)this.message = otherError;
        else this.message=ReplyCodeMessage.getMsg(code);
        if(data!=null)this.data=data;
        updateBalance(sid, null, jedis, null);
        return this.toJson();
    }

    private void replyHttp(int code, String otherError, Object data, Jedis jedis, Double price) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(ReplyCodeMessage.CodeInHeader, String.valueOf(code));
        this.code = code;
        if(code==ReplyCodeMessage.Code1020OtherError)this.message = otherError;
        else this.message=ReplyCodeMessage.getMsg(code);
        if(data!=null)this.data=data;
        jedis.select(0);
        try {
            String bestHeightStr = jedis.get(BEST_HEIGHT);
            bestHeight = Long.parseLong(bestHeightStr);
        }catch (Exception ignore){}
        updateBalance(sid, requestCheckResult.getApiName(), jedis, price);
        String sessionKey = requestCheckResult.getSessionKey();
        String replyStr = this.toNiceJson();
        if(sessionKey !=null){
            String sign = Signature.symSign(replyStr,sessionKey);
            if(sign!=null) response.setHeader(ReplyCodeMessage.SignInHeader,sign);
        }
        try {
            response.getWriter().write(replyStr);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public void clean() {
        code=null;
        message=null;
        nonce=null;
        balance=null;
        data=null;
        last=null;
    }
    public String toJson(){
        return new Gson().toJson(this);
    }
    public String toNiceJson(){
        return JsonTools.toNiceJson(this);
    }
    @Override
    public String toString(){return toNiceJson();}

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public Long getBalance() {
        return balance;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public List<String> getLast() {
        return last;
    }

    public void setLast(List<String> last) {
        this.last = last;
    }

    public Integer getNonce() {
        return nonce;
    }

    public void setNonce(Integer nonce) {
        this.nonce = nonce;
    }

    public Long getGot() {
        return got;
    }

    public void setGot(Long got) {
        this.got = got;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public RequestCheckResult getRequestCheckResult() {
        return requestCheckResult;
    }

    public void setRequestCheckResult(RequestCheckResult requestCheckResult) {
        this.requestCheckResult = requestCheckResult;
    }

    public Long getBestHeight() {
        return bestHeight;
    }

    public void setBestHeight(Long bestHeight) {
        this.bestHeight = bestHeight;
    }

}
