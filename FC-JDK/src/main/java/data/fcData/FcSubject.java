package data.fcData;

import java.util.ArrayList;
import java.util.List;

import org.bitcoinj.core.ECKey;

import core.crypto.KeyTools;
import utils.Hex;
import exception.TooManyUserCidsException;

public class FcSubject extends FcEntity {
    // The 'id'  of this class is called 'FID' in FC.
    protected String cid;
    protected List<String> usedCids;
    protected String pubkey;
    protected Boolean isNobody;
    protected transient byte[] pubkeyBytes;
    protected transient byte[] prikeyBytes;

    public FcSubject() {
    }

    public FcSubject(byte[] prikey) {
        if(prikey == null || prikey.length != 32) {
            return;
        }
        this.prikeyBytes = prikey;
        this.pubkeyBytes = KeyTools.prikeyToPubkey(prikey);
        this.pubkey = Hex.toHex(pubkeyBytes);
        this.id = KeyTools.pubkeyToFchAddr(pubkeyBytes);
    }

    public FcSubject(String pubkey) {
        this.pubkey = pubkey;
        if(pubkey !=null && !pubkey.isEmpty() && Hex.isHex32(pubkey)){
            this.pubkeyBytes = Hex.fromHex(pubkey);
            this.id = KeyTools.pubkeyToFchAddr(pubkeyBytes);
        }else{
            this.pubkeyBytes = null;
            this.id = null;
        }
    }

    public static FcSubject createNew() {
        ECKey eckey = new ECKey();
        byte[] priKey = eckey.getPrivKeyBytes();
        return new FcSubject(priKey);
    }

    public static FcSubject getNew(String pubKey) {
        return new FcSubject(pubKey);
    }

    public ECKey makeECKey(){
        if(prikeyBytes !=null)return ECKey.fromPrivate(prikeyBytes);
        if(pubkeyBytes !=null)return ECKey.fromPublicOnly(pubkeyBytes);
        if(pubkey !=null)return ECKey.fromPublicOnly(Hex.fromHex(pubkey));
        return null;
    }

    public byte[] prikeyToPubkey() {
        this.pubkeyBytes = KeyTools.prikeyToPubkey(prikeyBytes);
        this.pubkey = Hex.toHex(pubkeyBytes);
        return pubkeyBytes;
    }

    public String getPubkey() {
        return pubkey;
    }

    public String getCid() {
        return cid;
    }

    public List<String> addUsedCid(String newCid) {
        if(newCid == null || newCid.isEmpty()) {
            return null;
        }
        if(usedCids == null) {
            usedCids = new ArrayList<>();
        }
        
        if(!usedCids.contains(newCid) && usedCids.size() >= 3) {
            throw new TooManyUserCidsException();
        }else if(usedCids.size() >= 4) {
            throw new TooManyUserCidsException();
        }

        usedCids.add(newCid);
        return usedCids;
    }

    public List<String> getUsedCids() {
        return usedCids;
    }

    public void setCid(String cid) {
        this.cid = cid;
    }

    public void setPubkey(String pubkey) {
        this.pubkey = pubkey;
        if(pubkey !=null) {
            this.pubkeyBytes = Hex.fromHex(pubkey);
            this.id = KeyTools.pubkeyToFchAddr(pubkeyBytes);
        }
    }

    public void setUsedCids(List<String> usedCids) {
        if(usedCids != null && usedCids.size() > 4) {            
            throw new TooManyUserCidsException();
        }
        this.usedCids = usedCids;
    }

    public void setPubkeyBytes(byte[] pubkeyBytes) {
        if(pubkeyBytes == null || pubkeyBytes.length != 33) {
            return;
        }
        this.pubkeyBytes = pubkeyBytes;
        this.pubkey = Hex.toHex(pubkeyBytes);
        this.id = KeyTools.pubkeyToFchAddr(pubkeyBytes);
    }

    public void setPrikeyBytes(byte[] priKey) {
        if(priKey == null || priKey.length != 32) {
            this.prikeyBytes =null;
            return;
        }
        this.prikeyBytes = priKey;
        this.pubkeyBytes = KeyTools.prikeyToPubkey(priKey);
        this.pubkey = Hex.toHex(pubkeyBytes);
        this.id = KeyTools.pubkeyToFchAddr(pubkeyBytes);
    }

    public byte[] getPubkeyBytes() {
        if(pubkeyBytes ==null && pubkey !=null) pubkeyBytes =Hex.fromHex(pubkey);
        return pubkeyBytes;
    }

    public byte[] getPrikeyBytes() {
        return prikeyBytes;
    }

    public Boolean getNobody() {
        return isNobody;
    }

    public void setNobody(Boolean nobody) {
        isNobody = nobody;
    }
}
