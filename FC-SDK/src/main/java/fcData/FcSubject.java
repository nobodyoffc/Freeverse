package fcData;

import java.util.ArrayList;
import java.util.List;

import org.bitcoinj.core.ECKey;

import crypto.KeyTools;
import utils.Hex;
import exception.TooManyUserCidsException;
import exception.IllegalPubKeyException;
import exception.IllegalPriKeyException;
public class FcSubject extends FcEntity {
    // The 'id'  of this class is called 'FID' in FC.
    protected String cid;
    protected List<String> usedCids;
    protected String pubKey;
    protected Boolean isNobody;
    protected transient byte[] pubKeyBytes;
    protected transient byte[] priKeyBytes;

    public FcSubject() {
    }

    public FcSubject(byte[] priKey) {
        if(priKey == null || priKey.length != 32) {
            throw new IllegalPriKeyException();
        }
        this.priKeyBytes = priKey;
        this.pubKeyBytes = KeyTools.priKeyToPubKey(priKey);
        this.pubKey = Hex.toHex(pubKeyBytes);
        this.id = KeyTools.pubKeyToFchAddr(pubKeyBytes);
    }

    public FcSubject(String pubKey) {
        this.pubKey = pubKey;
        if(pubKey!=null && !pubKey.isEmpty() && Hex.isHex32(pubKey)){
            this.pubKeyBytes = Hex.fromHex(pubKey);
            this.id = KeyTools.pubKeyToFchAddr(pubKeyBytes);
        }else{
            this.pubKeyBytes = null;
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
        if(priKeyBytes!=null)return ECKey.fromPrivate(priKeyBytes);
        if(pubKeyBytes!=null)return ECKey.fromPublicOnly(pubKeyBytes);
        if(pubKey!=null)return ECKey.fromPublicOnly(Hex.fromHex(pubKey));
        return null;
    }

    public byte[] priKeyToPubKey() {
        this.pubKeyBytes = KeyTools.priKeyToPubKey(priKeyBytes);
        this.pubKey = Hex.toHex(pubKeyBytes);
        return pubKeyBytes;
    }

    public String getPubKey() {
        return pubKey;
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

    public void setPubKey(String pubKey) {
        this.pubKey = pubKey;
        if(pubKey!=null) {
            this.pubKeyBytes = Hex.fromHex(pubKey);
            this.id = KeyTools.pubKeyToFchAddr(pubKeyBytes);
        }
    }

    public void setUsedCids(List<String> usedCids) {
        if(usedCids != null && usedCids.size() > 4) {            
            throw new TooManyUserCidsException();
        }
        this.usedCids = usedCids;
    }

    public void setPubKeyBytes(byte[] pubKeyBytes) {
        if(pubKeyBytes == null || pubKeyBytes.length != 33) {
            throw new IllegalPubKeyException();
        }
        this.pubKeyBytes = pubKeyBytes;
        this.pubKey = Hex.toHex(pubKeyBytes);
        this.id = KeyTools.pubKeyToFchAddr(pubKeyBytes);
    }

    public void setPriKeyBytes(byte[] priKey) {
        if(priKey == null || priKey.length != 32) {
            this.priKeyBytes=null;
            return;
        }
        this.priKeyBytes = priKey;
        this.pubKeyBytes = KeyTools.priKeyToPubKey(priKey);
        this.pubKey = Hex.toHex(pubKeyBytes);
        this.id = KeyTools.pubKeyToFchAddr(pubKeyBytes);
    }

    public byte[] getPubKeyBytes() {
        if(pubKeyBytes==null && pubKey!=null)pubKeyBytes=Hex.fromHex(pubKey);
        return pubKeyBytes;
    }

    public byte[] getPriKeyBytes() {
        return priKeyBytes;
    }

    public Boolean getNobody() {
        return isNobody;
    }

    public void setNobody(Boolean nobody) {
        isNobody = nobody;
    }
}
