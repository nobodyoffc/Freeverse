package NasaRcpTest;

import javaTools.JsonTools;
import nasa.RpcRequest;
import org.junit.jupiter.api.Test;

public class GetRawTx {
    public static final String GETRAWTRANSACTION = "getrawtransaction";
    private String params;
    private String result;

    @Test
    public void test() {
        String txId = "bf9f366d8e207928c90c819e81276c69864dbd42466798f53d33ad974cb0fb6b";
        params = txId;
        String url = "http://127.0.0.1:8332";
//        String url = "http://127.0.0.1:22555";
        String rawTx = getRawTx(params, url, "username", "password");
        JsonTools.printJson(rawTx);
    }

    public String getRawTx(String txId, String url, String username, String password) {
        RpcRequest jsonRPC2Request = new RpcRequest(GETRAWTRANSACTION, new Object[]{txId});
        Object result = RpcRequest.requestRpc(url, username, password, GETRAWTRANSACTION, jsonRPC2Request);
        return (String) result;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
