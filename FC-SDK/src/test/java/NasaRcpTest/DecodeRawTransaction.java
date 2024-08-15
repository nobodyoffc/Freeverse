package NasaRcpTest;

import javaTools.JsonTools;
import nasa.RpcRequest;
import org.junit.jupiter.api.Test;

public class DecodeRawTransaction {
    public static String method = "decoderawtransaction";
    private String[] params;
    private String result;

    @Test
    public void test() {
        String txid = decodeRawTransaction("010000000193083a74020e43f788bff7bddd72108500d7afa0cb5f3c488d84d1908fcec917000000006a4730440220632667637d03cb9be918ab06c731437367baa43c993206ce359ee6f3ba9f9b2102206ac34c806aa77d9bc360f8b54c228926487af1052301b30cc68dfcc4a147e28f012103d93516665542cb3646514ce94eb6c61d2e4a6abc35fdf510e77630d76a187589ffffffff0380969800000000001976a9149494f686025f04afe4c837139ea849b58ff3b99988ac0000000000000000046a0268697632023b000000001976a9149494f686025f04afe4c837139ea849b58ff3b99988ac00000000", "http://127.0.0.1:22555", "username", "password");
        if (txid == null) return;
        JsonTools.printJson(txid);
    }

    public String decodeRawTransaction(String hex, String url, String username, String password) {
        String[] params = new String[]{hex};
        RpcRequest jsonRPC2Request = new RpcRequest(method, params);

        Object result = RpcRequest.requestRpc(url, username, password, "decodeRawTransaction", jsonRPC2Request);
        return (String) result;
    }

    public String[] getParams() {
        return params;
    }

    public void setParams(String[] params) {
        this.params = params;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
