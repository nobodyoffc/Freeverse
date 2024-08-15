package apip.apipData;

import com.google.gson.Gson;
import javaTools.BytesTools;

public class RequestBody {
    private String sid;

    private String url;
    private Long time;
    private Long nonce;
    private String via;
    private Object data;
    private Fcdsl fcdsl;
    private SignInMode mode;
    public static enum SignInMode{
        REFRESH,NORMAL
    }

    public RequestBody() {
    }

    public RequestBody(String url, String via) {
        setTime(System.currentTimeMillis());
        setNonce((BytesTools.bytes4ToLongBE(BytesTools.getRandomBytes(4))));
        setVia(via);
        setUrl(url);
    }

    public RequestBody(String url, String via, SignInMode mode) {
        setTime(System.currentTimeMillis());
        setNonce((BytesTools.bytes4ToLongBE(BytesTools.getRandomBytes(4))));
        setVia(via);
        setUrl(url);
        setMode(mode);
    }

    public String toJson() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    public void makeRequestBody(String url, String via) {
        setTime(System.currentTimeMillis());
        setNonce((BytesTools.bytes4ToLongBE(BytesTools.getRandomBytes(4))));
        setVia(via);
        setUrl(url);
    }

    public void makeRequestBody(String url, String via, SignInMode mode) {
        setTime(System.currentTimeMillis());
        setNonce((BytesTools.bytes4ToLongBE(BytesTools.getRandomBytes(4))));
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

    public Long getNonce() {
        return nonce;
    }

    public void setNonce(Long nonce) {
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
}
