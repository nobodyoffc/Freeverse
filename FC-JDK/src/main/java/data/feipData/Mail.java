package data.feipData;

import clients.ApipClient;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;

import constants.CodeMessage;
import core.crypto.Algorithm.Bitcore;
import core.crypto.CryptoDataByte;
import core.crypto.Decryptor;
import core.crypto.KeyTools;
import data.fcData.AlgorithmId;
import data.fcData.FcObject;
import utils.*;
import utils.http.AuthType;
import utils.http.RequestMethod;
import org.jetbrains.annotations.Nullable;

import java.util.Base64;


public class Mail extends FcObject {
    private String alg;
    private String cipher;

    private String from;
    private Long noticeFee;
    private String to;
    private Long birthTime;
    private Long birthHeight;
    private Long lastHeight;
    private Boolean active;

    //On chain details
    private String mailId;
    private Long time;
    private transient String fromCid;
    private transient String toCid;
    private String content;

    public byte[] toBytes(){
        try(ByteArrayBuilder bab = new ByteArrayBuilder()) {
            bab.write(BytesUtils.longToBytes(time));
            bab.write(Hex.fromHex(mailId));
            byte[] fromBytes = KeyTools.addrToHash160(from);
            bab.write(fromBytes);
            if(to ==null) bab.write(fromBytes);
            else bab.write(KeyTools.addrToHash160(to));
            if(fromCid!=null){
                byte[] fromCidBytes = fromCid.getBytes();
                bab.write(fromCidBytes.length);
                bab.write(fromCidBytes);
            }else bab.write(0);

            if(toCid!=null){
                byte[] toCidBytes = toCid.getBytes();
                bab.write(toCidBytes.length);
                bab.write(toCidBytes);
            }else bab.write(0);
            
            bab.write(content.getBytes());
            return bab.toByteArray();
        }catch (Exception e){
            return null;
        }
    }

    public Mail parseDetail(String myFid, Mail mail, byte[] priKey, ApipClient apipClient) {
        // Transfer relevant data from Mail to Mail

        CryptoDataByte cryptoDataByte = decryptMail(myFid, mail, priKey, apipClient);
        if (cryptoDataByte == null) {
            setContent(null);
            return mail;
        }

        if (cryptoDataByte.getCode() == 0) {
            setContent(new String(cryptoDataByte.getData()));
        } else {
            setContent(null);
        }

        return mail;
    }

    @Nullable
    private static CryptoDataByte decryptMail(String myFid, Mail mail, byte[] priKey, ApipClient apipClient) {
        String alg = null;
        String cipher;
        if(mail.getAlg()!=null)alg = mail.getAlg();

        if(mail.getCipher()!=null)cipher = mail.getCipher();
        else {
            CryptoDataByte cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code4013BadCipher);
            return cryptoDataByte;
        }

        CryptoDataByte cryptoDataByte = null;

        if(cipher.startsWith("{")){
            cryptoDataByte = CryptoDataByte.fromJson(cipher);
        }else if(cipher.startsWith("A")){
            if(alg!=null){
                if(alg.equals(AlgorithmId.BitCore_EccAes256.getDisplayName()))
                    cryptoDataByte = Bitcore.parseBitcoreCipher(cipher);
                else if (alg.equals(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7.getDisplayName()))
                    cryptoDataByte = CryptoDataByte.fromBundle(Base64.getDecoder().decode(cipher));
            }else{
                try{
                    cryptoDataByte = Bitcore.parseBitcoreCipher(cipher);
                }catch (Exception e){
                    cryptoDataByte = CryptoDataByte.fromBundle(Base64.getDecoder().decode(cipher));
                }
            }
        }else{
            cryptoDataByte = CryptoDataByte.fromBundle(Base64.getDecoder().decode(cipher));
        }
        if(cryptoDataByte==null) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code4013BadCipher);
            return cryptoDataByte;
        }

        if(myFid.equals(mail.getTo()))cryptoDataByte.setPrikeyB(priKey);
        if(myFid.equals(mail.getFrom())){
            cryptoDataByte.setPrikeyA(priKey);
            String pubKeyB = apipClient.getPubkey(mail.getTo(), RequestMethod.POST, AuthType.SYMKEY_ENCRYPT);
            cryptoDataByte.setPubkeyB(Hex.fromHex(pubKeyB));
        }

        Decryptor decryptor = new Decryptor();
        decryptor.decrypt(cryptoDataByte);
        return cryptoDataByte;
    }


    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public String getMailId() {
        return mailId;
    }

    public void setMailId(String mailId) {
        this.mailId = mailId;
    }

    public byte[] getIdBytes() {
        return Hex.fromHex(this.mailId);
    }

    public String getFromCid() {
        return fromCid;
    }

    public void setFromCid(String fromCid) {
        this.fromCid = fromCid;
    }

    public String getToCid() {
        return toCid;
    }

    public void setToCid(String toCid) {
        this.toCid = toCid;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getAlg() {
        return alg;
    }

    public void setAlg(String alg) {
        this.alg = alg;
    }

    public String getCipher() {
        return cipher;
    }

    public void setCipher(String cipher) {
        this.cipher = cipher;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public Long getBirthTime() {
        return birthTime;
    }

    public void setBirthTime(Long birthTime) {
        this.birthTime = birthTime;
    }

    public Long getBirthHeight() {
        return birthHeight;
    }

    public void setBirthHeight(Long birthHeight) {
        this.birthHeight = birthHeight;
    }

    public Long getLastHeight() {
        return lastHeight;
    }

    public void setLastHeight(Long lastHeight) {
        this.lastHeight = lastHeight;
    }

    public Long getNoticeFee() {
        return noticeFee;
    }

    public void setNoticeFee(Long noticeFee) {
        this.noticeFee = noticeFee;
    }
}
