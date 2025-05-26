package core.fch;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import core.crypto.Hash;
import data.fchData.Cash;
import data.fchData.P2SH;
import utils.BytesUtils;
import utils.JsonUtils;
import utils.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.*;

public final class MultiSigData {
    private long nonce;
    private String rawTxId;
    private byte[] rawTx;
    private P2SH p2SH;
    private List<Cash> cashList;
    private Map<String, List<byte[]>> fidSigMap;

    public MultiSigData(byte[] rawTx, P2SH p2SH, List<Cash> cashList) {
        this.nonce = BytesUtils.bytes4ToLongBE(BytesUtils.getRandomBytes(4));
        this.rawTx = rawTx;
        this.p2SH = p2SH;
        this.cashList = cashList;
        this.rawTxId = HexFormat.of().formatHex(Hash.sha256x2(rawTx));
    }

    public MultiSigData(String rawTxHex, String p2SHStr, String cashList) {
        this.nonce = BytesUtils.bytes4ToLongBE(BytesUtils.getRandomBytes(4));
        this.rawTx = HexFormat.of().parseHex(rawTxHex);
        this.p2SH = new Gson().fromJson(p2SHStr, P2SH.class);
        this.cashList = ObjectUtils.objectToList(cashList,Cash.class);//DataGetter.getCashList(cashList);
    }

    public MultiSigData() {
    }

    public static MultiSigData fromJson(String jsonStr) {
        MultiSigData multiSignData = new MultiSigData();
//        if(jsonStr.startsWith("\""))jsonStr.substring(1);
//        if(jsonStr.endsWith("\""))jsonStr.substring(0,jsonStr.length()-2);
        Gson gson = new Gson();
        Type t = new TypeToken<HashMap<String, Object>>() {
        }.getType();
        Map<String, Object> dataMap = gson.fromJson(jsonStr, t);

        if (dataMap.get("nonce") != null)
            multiSignData.setNonce(gson.fromJson(gson.toJson(dataMap.get("nonce")), long.class));
        if (dataMap.get("rawTxId") != null)
            multiSignData.setRawTxId(gson.fromJson(gson.toJson(dataMap.get("rawTxId")), String.class));
        if (dataMap.get("rawTx") != null)
            multiSignData.setRawTx(HexFormat.of().parseHex((String) dataMap.get("rawTx")));
        if (dataMap.get("p2SH") != null)
            multiSignData.setP2SH(gson.fromJson(gson.toJson(dataMap.get("p2SH")), P2SH.class));
        if (dataMap.get("cashList") != null)
            multiSignData.setCashList(ObjectUtils.objectToList(dataMap.get("cashList"),Cash.class));//DataGetter.getCashList(dataMap.get("cashList")));
        if (dataMap.get("fidSigMap") != null) {
            Map<String, List<byte[]>> fidSigMap = makeStringListMapToBytesListMap(dataMap.get("fidSigMap"));
            multiSignData.setFidSigMap(fidSigMap);
        }
        return multiSignData;
    }

    @NotNull
    private static Map<String, List<byte[]>> makeStringListMapToBytesListMap(Object obj) {
        Gson gson = new Gson();
        Type t;
        t = new TypeToken<Map<String, List<String>>>() {
        }.getType();
        Map<String, List<String>> map = gson.fromJson(gson.toJson(obj), t);

        Map<String, List<byte[]>> fidSigMap = new HashMap<>();

        for (String fid : map.keySet()) {
            List<String> sigHexList = map.get(fid);
            List<byte[]> sigList = new ArrayList<>();
            for (String sig : sigHexList) {
                sigList.add(HexFormat.of().parseHex(sig));
            }
            fidSigMap.put(fid, sigList);
        }
        return fidSigMap;
    }

    public Map<String, List<String>> getFidSigHexMap() {
        Map<String, List<String>> fidSigHexMap = new HashMap<>();
        for (String fid : fidSigMap.keySet()) {
            List<byte[]> sigList = fidSigMap.get(fid);

            List<String> sigHexList = new ArrayList<>();
            for (byte[] bytes : sigList) {
                String hex = HexFormat.of().formatHex(bytes);
                sigHexList.add(hex);
            }
            fidSigHexMap.put(fid, sigHexList);
        }
        return fidSigHexMap;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (MultiSigData) obj;
        return Arrays.equals(this.rawTx, that.rawTx) &&
                Objects.equals(this.p2SH, that.p2SH) &&
                Objects.equals(this.cashList, that.cashList) &&
                Objects.equals(this.fidSigMap, that.fidSigMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(rawTx), p2SH, cashList, fidSigMap);
    }

    @Override
    public String toString() {
        return "MultiSignData[\n" +
                "rawTxHex=" + HexFormat.of().formatHex(rawTx) + ", \n" +
                "p2SH=" + JsonUtils.toNiceJson(p2SH) + ", \n" +
                "cashList=" + JsonUtils.toNiceJson(cashList) + ", \n" +
                "fidSigMap=" + JsonUtils.toNiceJson(fidSigMap) + "\n]";
    }

    public Map<String, List<byte[]>> getFidSigMap() {
        return fidSigMap;
    }

    public void setFidSigMap(Map<String, List<byte[]>> fidSigMap) {
        this.fidSigMap = fidSigMap;
    }

    public byte[] getRawTx() {
        return rawTx;
    }

    public void setRawTx(byte[] rawTx) {
        this.rawTx = rawTx;
        this.rawTxId = HexFormat.of().formatHex(Hash.sha256x2(rawTx));
    }

    public P2SH getP2SH() {
        return p2SH;
    }

    public void setP2SH(P2SH p2SH) {
        this.p2SH = p2SH;
    }

    public List<Cash> getCashList() {
        return cashList;
    }

    public void setCashList(List<Cash> cashList) {
        this.cashList = cashList;
    }

    public long getNonce() {
        return nonce;
    }

    public void setNonce(long nonce) {
        this.nonce = nonce;
    }

    public String toJson() {
        Map<String, Object> dataMap = new HashMap<>();
        if (nonce != 0) dataMap.put("nonce", nonce);
        if (rawTxId != null) dataMap.put("rawTxId", rawTxId);
        if (rawTx != null) dataMap.put("rawTx", HexFormat.of().formatHex(rawTx));
        if (p2SH != null) dataMap.put("p2SH", p2SH);
        if (cashList != null) dataMap.put("cashList", cashList);
        if (fidSigMap != null) {
            dataMap.put("fidSigMap", getFidSigHexMap());
        }

        return JsonUtils.toNiceJson(dataMap);
    }

    public String getRawTxId() {
        return rawTxId;
    }

    public void setRawTxId(String rawTxId) {
        this.rawTxId = rawTxId;
    }
}