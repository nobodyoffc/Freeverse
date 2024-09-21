package fcData;

import com.google.gson.Gson;
import constants.ReplyCodeMessage;
import javaTools.JsonTools;

import java.util.List;

public class FcReplier {
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

    public String reply(int code, String otherError, Object data, Integer nonce){
        this.code = code;
        if(code== ReplyCodeMessage.Code1020OtherError)this.message = otherError;
        else this.message=ReplyCodeMessage.getMsg(code);
        if(data!=null)this.data=data;
        if(nonce!=null)this.nonce=nonce;
        return this.toJson();
    }



    public String toJson(){
        return new Gson().toJson(this);
    }
    public String toNiceJson(){
        return JsonTools.toNiceJson(this);
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
}
