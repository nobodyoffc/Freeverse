package data.apipData;

import data.fcData.FcEntity;
import data.fcData.Op;
import utils.BytesUtils;

public class RequestBody extends FcEntity {
    private String sid;
    private Op op;
    private String url;
    private Long time;
    private Integer nonce;
    private String via;
    private Object data;
    private Fcdsl fcdsl;
    private SignInMode mode;

    public void renew() {
        this.nonce = Math.abs(BytesUtils.bytesToIntBE(BytesUtils.getRandomBytes(4)));
        this.time = System.currentTimeMillis();
    }

    public enum SignInMode{
        REFRESH,NORMAL
    }

    public RequestBody() {
        this.nonce = Math.abs(BytesUtils.bytesToIntBE(BytesUtils.getRandomBytes(4)));
        this.time = System.currentTimeMillis();
    }

    public RequestBody(Op op,Object data) {
        this.nonce = Math.abs(BytesUtils.bytesToIntBE(BytesUtils.getRandomBytes(4)));
        this.time = System.currentTimeMillis();
        this.op = op;
        this.data = data;
    }

    public RequestBody(String url, String via) {
        setTime(System.currentTimeMillis());
        this.nonce = Math.abs(BytesUtils.bytesToIntBE(BytesUtils.getRandomBytes(4)));
        setVia(via);
        setUrl(url);
    }

    public RequestBody(String url, String via, SignInMode mode) {
        setTime(System.currentTimeMillis());
        this.nonce = Math.abs(BytesUtils.bytesToIntBE(BytesUtils.getRandomBytes(4)));
        setVia(via);
        setUrl(url);
        setMode(mode);
    }

    // public String toJson() {
    //     Gson gson = new Gson();
    //     return gson.toJson(this);
    // }

    // public static RequestBody fromJson(String json) {
    //     Gson gson = new Gson();
    //     return gson.fromJson(json,RequestBody.class);
    // }

    public void makeRequestBody(String url, String via) {
        setTime(System.currentTimeMillis());
        this.nonce = Math.abs(BytesUtils.bytesToIntBE(BytesUtils.getRandomBytes(4)));
        setVia(via);
        setUrl(url);
    }

    public void makeRequestBody(String url, String via, SignInMode mode) {
        setTime(System.currentTimeMillis());
        this.nonce = Math.abs(BytesUtils.bytesToIntBE(BytesUtils.getRandomBytes(4)));
        setVia(via);
        setUrl(url);
        if (mode != null) setMode(mode);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public Integer getNonce() {
        return nonce;
    }

    public void setNonce(Integer nonce) {
        this.nonce = nonce;
    }

    public String getVia() {
        return via;
    }

    public void setVia(String via) {
        this.via = via;
    }

    public Fcdsl getFcdsl() {
        return fcdsl;
    }

    public void setFcdsl(Fcdsl fcdsl) {
        this.fcdsl = fcdsl;
    }

    public SignInMode getMode() {
        return mode;
    }

    public void setMode(SignInMode mode) {
        this.mode = mode;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public Op getOp() {
        return op;
    }

    public void setOp(Op op) {
        this.op = op;
    }
}
