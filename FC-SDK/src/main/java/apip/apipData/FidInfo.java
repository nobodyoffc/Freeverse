package apip.apipData;


import fch.Inputer;
import crypto.KeyTools;
import crypto.old.EccAes256K1P7;

import java.io.BufferedReader;
import java.util.HexFormat;
import java.util.Map;

import static crypto.KeyTools.priKeyToFid;
import static crypto.KeyTools.priKeyToPubKey;

public class FidInfo {
    private Map<String, String> addresses;
    private byte[] priKey;
    private String fid;
    private String priKeyCipher;
    private String pubKey;
    private String[] cids;

    public FidInfo() {
    }

    public FidInfo(byte[] priKey) {
        this.priKey = priKey;
        this.fid = priKeyToFid(priKey);
        this.pubKey = HexFormat.of().formatHex(priKeyToPubKey(priKey));
        this.addresses = KeyTools.pubKeyToAddresses(pubKey);
    }

    public FidInfo(byte[] priKey, byte[] symKey) {
        this.priKey = priKey;
        this.fid = priKeyToFid(priKey);
        this.pubKey = HexFormat.of().formatHex(priKeyToPubKey(priKey));
        this.addresses = KeyTools.pubKeyToAddresses(pubKey);
        this.priKeyCipher = EccAes256K1P7.encryptWithSymKey(priKey, symKey);
    }

    public static FidInfo inputPriKey(BufferedReader br, byte[] initSymKey) {

        byte[] priKey32;

        while (true) {
            priKey32 = Inputer.inputPriKey(br);
            if (priKey32 == null) return null;

            FidInfo fidInfo = new FidInfo(priKey32, initSymKey);
            if (fidInfo.getFid() == null) {
                System.out.println("Wrong input. Try again.");
                continue;
            }
            return fidInfo;
        }
    }

    public Map<String, String> getAddresses() {
        return addresses;
    }

    public void setAddresses(Map<String, String> addresses) {
        this.addresses = addresses;
    }

    public byte[] getPriKey() {
        return priKey;
    }

    public void setPriKey(byte[] priKey) {
        this.priKey = priKey;
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public String getPriKeyCipher() {
        return priKeyCipher;
    }

    public void setPriKeyCipher(String priKeyCipher) {
        this.priKeyCipher = priKeyCipher;
    }

    public String getPubKey() {
        return pubKey;
    }

    public void setPubKey(String pubKey) {
        this.pubKey = pubKey;
    }

    public String[] getCids() {
        return cids;
    }

    public void setCids(String[] cids) {
        this.cids = cids;
    }
}
