package fcData;

import apip.apipData.Session;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import crypto.Hash;
import javaTools.BytesTools;
import javaTools.Hex;
import crypto.KeyTools;
import org.bitcoinj.core.ECKey;

import javax.annotation.Nullable;
import java.security.SignatureException;
import java.util.HexFormat;

public class Signature {
    private String fid;
    private String msg;
    private String sign;
    private AlgorithmId alg;



    private String address;
    private String message;
    private String signature;
    private AlgorithmId algorithm;

    private String symKeyName;


    private transient byte[] symKey;

    public Signature() {
    }

    public Signature(String symSign, String symKeyName) {
        this.sign = symSign;
        this.symKeyName = symKeyName;
    }

    public Signature(String fid, String msg, String sign, AlgorithmId alg, String symKeyName) {
        if (fid != null) {
            this.fid = fid;
            this.address = fid;
        }

        if (symKeyName != null) {
            this.symKeyName = symKeyName;
        }

        if (msg != null) {
            this.msg = msg;
            this.message = msg;
        }

        if (sign != null) {
            this.sign = sign;
            this.signature = sign;
        }

        if (alg != null) {
            this.alg = alg;
            this.algorithm = alg;
        }
    }

    public static String symSign(String msg, String symKey) {
        if(msg==null || symKey==null)return null;
        byte[] replyJsonBytes = msg.getBytes();
        byte[] keyBytes = Hex.fromHex(symKey);
        byte[] bytes = BytesTools.bytesMerger(replyJsonBytes,keyBytes);
        byte[] signBytes = Hash.sha256x2(bytes);
        return Hex.toHex(signBytes);
    }

    public Signature sign(String msg,byte[] key,AlgorithmId alg){
        this.alg = alg;
        this.msg = msg;
        this.sign=null;

        ECKey ecKey = ECKey.fromPrivate(key);

        switch (alg){
            case BTC_EcdsaSignMsg_No1_NrC7 -> {
                this.fid = KeyTools.priKeyToFid(key);
                this.sign = ecKey.signMessage(msg);
            }
            case FC_AesSymSignMsg_No1_NrC7 -> {
                String keyHex = Hex.toHex(key);
                this.symKeyName = Session.makeSessionName(keyHex);
                this.sign = symSign(msg, keyHex);
            }
        }
        return this;
    }


    @Nullable
    public static Signature parseSignature(String rawSignJson) {
        Signature signature;
        try {
            Gson gson = new Gson();
            if (rawSignJson.contains("----")) {
                signature = parseOldSign(rawSignJson);
                signature.setAlg(AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7);
            } else {
                signature = gson.fromJson(rawSignJson, Signature.class);
                signature.makeSignature();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return signature;
    }

    public static Signature parseOldSign(String oldSign) {
        String[] elm = oldSign.split("----");
        Signature signature = new Signature();
        signature.setMsg(elm[0].replaceAll("\"", ""));
        signature.setFid(elm[1]);
        signature.setSign(elm[2]
                .replaceAll("\"", "")
                .replaceAll("\\u003d", "="));

        return signature;
    }

    public void makeSignature() {
        if (fid == null && address != null) fid = address;
        if (msg == null && message != null) msg = message;
        if (sign == null && signature != null) sign = signature;
        if (alg == null && algorithm != null) alg = algorithm;
    }

    public boolean isAsyPrepared() {
        return (fid != null && msg != null && sign != null && alg != null);
    }
    public boolean isSymPrepared() {
        return (symKey!= null &&msg != null && sign != null && alg != null);
    }

    public boolean verify() {
        switch (alg){
            case BTC_EcdsaSignMsg_No1_NrC7 ->{
                try {
                    String pubKey = ECKey.signedMessageToKey(message, sign).getPublicKeyAsHex();
                    String signFid = KeyTools.pubKeyToFchAddr(pubKey);
                    return fid.equals(signFid);
                } catch (SignatureException e) {
                    return false;
                }
            }
            case FC_AesSymSignMsg_No1_NrC7 -> {
                if(sign==null)return false;
                byte[] signBytes = BytesTools.bytesMerger(msg.getBytes(), symKey);
                String doubleSha256Hash = HexFormat.of().formatHex(Hash.sha256x2(signBytes));
                return sign.equals(doubleSha256Hash);
            }
        }
        return false;
    }

    public String toJson() {
        Gson gson = new Gson();
        return toJson(gson);
    }

    public String toNiceJson() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setPrettyPrinting();
        Gson gson = gsonBuilder.create();
        return toJson(gson);
    }

    private String toJson(Gson gson) {
        switch (alg){
            case BTC_EcdsaSignMsg_No1_NrC7 -> {
                return gson.toJson(new ShortSign(fid, null, msg, sign, alg));
            }
            case FC_AesSymSignMsg_No1_NrC7-> {
                return gson.toJson(new ShortSign(null, symKeyName, msg, sign, alg));
            }
        }
        return new Gson().toJson(this);
    }

    public static Signature fromJson(String signatureJson){
        Signature signature1 = new Gson().fromJson(signatureJson, Signature.class);
        signature1.makeSignature();
        return signature1;
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    public AlgorithmId getAlg() {
        return alg;
    }

    public void setAlg(AlgorithmId alg) {
        this.alg = alg;
    }


    public void setAddress(String address) {
        this.address = address;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    public void setSignature(String signature) {
        this.signature = signature;
    }
    public void setAlgorithm(AlgorithmId algorithm) {
        this.algorithm = algorithm;
    }

    public String getSymKeyName() {
        return symKeyName;
    }
    public enum Type {
        SymSign, AsySign
    }

    static class ShortSign {
        String fid;
        String symKeyName;
        String msg;
        String sign;
        AlgorithmId alg;

        ShortSign(String fid,String symKeyName, String msg, String sign, AlgorithmId alg) {
            this.fid = fid;
            this.symKeyName = symKeyName;
            this.msg = msg;
            this.sign = sign;
            this.alg = alg;
        }
    }

    public byte[] getSymKey() {
        return symKey;
    }

    public void setSymKey(byte[] symKey) {
        this.symKey = symKey;
    }
}
