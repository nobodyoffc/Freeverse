package data.fcData;

import config.Settings;
import constants.CodeMessage;
import data.fchData.Block;
import handlers.AccountHandler;
import handlers.Handler;
import handlers.SessionHandler;
import server.HttpRequestChecker;
import utils.FchUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public class ReplyBody extends FcObject {
    protected String requestId;
    protected Op op;
    protected Integer code;
    protected String message;
    protected Integer nonce;
    protected Long time;
    protected Long balance;
    protected Object data;
    protected List<String> last;
    protected Long got;
    protected Long total;
    protected Long bestHeight;
    protected String bestBlockId; //For rollback checking
    protected transient String sid;
    protected transient AccountHandler accountHandler;
    protected transient SessionHandler sessionHandler;
    protected transient HttpRequestChecker httpRequestChecker;
    protected transient Settings settings;
    protected transient String finalJson;

    public ReplyBody() {
    }

    public ReplyBody(Settings settings) {
        this.sid = settings.getSid();
        this.settings = settings;
        if (settings.getHandler(Handler.HandlerType.ACCOUNT) != null)
            accountHandler = (AccountHandler) settings.getHandler(Handler.HandlerType.ACCOUNT);
        if (settings.getHandler(Handler.HandlerType.SESSION) != null)
            sessionHandler = (SessionHandler) settings.getHandler(Handler.HandlerType.SESSION);
    }
    public String replyError(int code){
        return reply(code,null,null);
    }
    public String reply(int code, String otherErrorMsg, Object data){
        this.code = code;
        if(code== CodeMessage.Code1020OtherError)this.message = otherErrorMsg;
        else this.message= CodeMessage.getMsg(code);
        if(data!=null)this.data=data;

        setBestBlock();

        updateBalance(httpRequestChecker.getApiName());
        finalJson = this.toJson();
        return finalJson;
    }

    public void replySingleDataSuccess(Object data) {
        this.got=1L;
        this.total=1L;
        reply( 0,null,data);
    }

    public void setBestBlock() {
        Block block = settings.getBestBlock();

        this.bestHeight = block.getHeight();
        this.bestBlockId = block.getId();
    }

    public void set0Success() {
        set0Success(null);
    }

    public void set0Success(Object data) {
        code = CodeMessage.Code0Success;
        message = CodeMessage.getMsg(code);
        if(data!=null)this.data = data;
    }


    public void setOtherError(String otherError) {
        code = CodeMessage.Code1020OtherError;
        if(otherError!=null)message = otherError;
        else message = CodeMessage.getMsg(code);
    }
    public void updateBalance(String apiName) {
        long length = this.toJson().length();
        updateBalance(apiName, length);
    }
    public Long updateBalance(String apiName, long length) {
        // Skip if it's a free request
        if(httpRequestChecker.getFreeRequest()!=null && httpRequestChecker.getFreeRequest().equals(Boolean.TRUE))
            return null;

        String fid = httpRequestChecker.getFid();
        if(fid==null) return null;

        // Get session info
        String sessionName = httpRequestChecker.getSessionName();
        String via = httpRequestChecker.getVia();

        // Check if user is account owner
        if(fid.equals(accountHandler.getMainFid())){
            double minPay = accountHandler.getMinPay();
            balance = FchUtils.coinToSatoshi(minPay);
            return balance;
        }

        long newBalance = accountHandler.userSpend(fid, apiName, length,via);

        // If balance is depleted, remove session
        if (newBalance <= 0 && sessionName != null) {
            sessionHandler.removeSession(sessionName);
        }

        balance = newBalance;
        return newBalance;
    }

    @Override
    public String toString(){return toNiceJson();}

    public void clean() {
        code=null;
        message=null;
        nonce=null;
        balance=null;
        data=null;
        last=null;
    }
    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getNonce() {
        return nonce;
    }

    public void setNonce(Integer nonce) {
        this.nonce = nonce;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public Long getBalance() {
        return balance;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
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

    public Long getBestHeight() {
        return bestHeight;
    }

    public void setBestHeight(Long bestHeight) {
        this.bestHeight = bestHeight;
    }


    public Op getOp() {
        return op;
    }


    public void setOp(Op op) {
        this.op = op;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getBestBlockId() {
        return bestBlockId;
    }

    public void setBestBlockId(String bestBlockId) {
        this.bestBlockId = bestBlockId;
    }

    public HttpRequestChecker getRequestChecker() {
        return httpRequestChecker;
    }

    public void setRequestChecker(HttpRequestChecker httpRequestChecker) {
        this.httpRequestChecker = httpRequestChecker;
    }

    public void Set0Success() {
        setCodeMessage(CodeMessage.Code0Success);
    }

    public void set1020Other(String message) {
        setCodeMessage(CodeMessage.Code1020OtherError);
        if(message!=null)setMessage(message);
    }

    public void setCodeMessage(Integer code) {
        this.code = code;
        this.message = CodeMessage.getMsg(code);
    }

    public void replyHttp(int code, HttpServletResponse response){
        replyHttp(code,null,null, response);
    }
    public void replyHttp(int code, Object data, HttpServletResponse response){
        replyHttp(code,null,data, response);
    }

    public void replySingleDataSuccessHttp(Object data, HttpServletResponse response) {
        this.got=1L;
        this.total=1L;
        replyHttp( 0,null,data, response);
    }

    public void reply0SuccessHttp(HttpServletResponse response) {
        replyHttp(0,null,null, response);
    }

    public void reply0SuccessHttp(Object data, HttpServletResponse response) {
        replyHttp(CodeMessage.Code0Success,data,response);
    }

    public void replyOtherErrorHttp(String otherError, Object data, HttpServletResponse response) {
        replyHttp(CodeMessage.Code1020OtherError,otherError,data, response);
    }

    public void replyOtherErrorHttp(String otherError, HttpServletResponse response) {
        replyHttp(CodeMessage.Code1020OtherError,otherError,null,response);
    }

    private void replyHttp(int code, String otherError, Object data, HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(CodeMessage.CodeInHeader, String.valueOf(code));

        String replyStr = reply(code,otherError,data);

        String sessionKey = httpRequestChecker.getSessionKey();

        if(sessionKey !=null){
            String sign = Signature.symSign(replyStr,sessionKey);
            if(sign!=null) response.setHeader(CodeMessage.SignInHeader,sign);
        }
        try {
            response.getWriter().write(replyStr);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        clean();
    }

    public void responseFinalJsonHttp(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(CodeMessage.CodeInHeader, String.valueOf(code));

        String sessionKey = httpRequestChecker.getSessionKey();

        if(sessionKey !=null){
            String sign = Signature.symSign(finalJson,sessionKey);
            if(sign!=null) response.setHeader(CodeMessage.SignInHeader,sign);
        }
        try {
            response.getWriter().write(finalJson);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
