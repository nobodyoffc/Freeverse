package fcData;

import crypto.Algorithm.Bitcore;
import org.checkerframework.checker.units.qual.radians;

import apip.apipData.CidInfo;
import clients.apipClient.ApipClient;
import feip.feipData.Contact;
import crypto.Decryptor;
import crypto.CryptoDataByte;
import javaTools.JsonTools;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

public class ContactDetail {
    private String fid;
    private String cid;
    private String pubKey;
    private Double noticeFee;

    private String memo;
    private Boolean seeStatement;
    private Boolean seeWritings;

    private Long updateHeight;

    public static ContactDetail fromCidInfo(CidInfo cidInfo) {
        ContactDetail contactDetail = new ContactDetail();
        contactDetail.setFid(cidInfo.getFid());
        contactDetail.setCid(cidInfo.getCid());
        String fee = cidInfo.getNoticeFee();
        if(fee!=null)
            contactDetail.setNoticeFee(Double.valueOf(fee));
        contactDetail.setPubKey(cidInfo.getPubKey());
        contactDetail.setUpdateHeight(cidInfo.getLastHeight());
        return contactDetail;
    }

    public byte[] toBytes(){
        return javaTools.JsonTools.toJson(this).getBytes();
    }

    public static ContactDetail fromBytes(byte[] bytes){
        return javaTools.JsonTools.fromJson(new String(bytes), ContactDetail.class);
    }

    public static ContactDetail fromContact(Contact contact,byte[] priKey, ApipClient apipClient) {
        ContactDetail contactDetail = fromContact(contact,priKey);
        if (contactDetail == null) return null;
        return makeContactDetail(contactDetail, apipClient);    
    }

    private static ContactDetail fromContact(Contact contact,byte[] priKey) {
        
        if (contact.getCipher() != null && !contact.getCipher().isEmpty()) {
            Decryptor decryptor = new Decryptor();
            String cipher = contact.getCipher();
            byte[] cipherBytes=null;

            String decryptedContent=null;
            byte[] dataBytes;
            CryptoDataByte cryptoDataByte=null;


            if(cipher.startsWith("A")){
                try {
                    cipherBytes = Base64.getDecoder().decode(cipher);
                }catch (IllegalArgumentException e){
                    return null; //Not Base64
                } 
                
                try{
                    cryptoDataByte = CryptoDataByte.fromBundle(cipherBytes);

                    //Not FC algorithm
                    if(cryptoDataByte==null ||cryptoDataByte.getCode() != 0){
                        try {
                            dataBytes = Bitcore.decrypt(cipherBytes, priKey);
                        }catch (Exception e){
                            return null;
                        }
                        ContactDetail contactDetail = JsonTools.fromJson(new String(dataBytes), ContactDetail.class);
                        if(contactDetail!=null){
                            contactDetail.setUpdateHeight(contact.getLastHeight());
                            return contactDetail;
                        }
                    return null;
                    }
                }catch (Exception ignore){
                }
            }

            //FC algorithm
            if(cryptoDataByte==null) {
                try {
                    cryptoDataByte = CryptoDataByte.fromJson(cipher);
                }catch (Exception e){
                    return null;
                }
            }

            cryptoDataByte.setPriKeyB(priKey);
            cryptoDataByte = decryptor.decrypt(cryptoDataByte);

            if (cryptoDataByte.getCode() == 0) {
                // Successfully decrypted
                decryptedContent = new String(cryptoDataByte.getData());
                ContactDetail decryptedDetail = JsonTools.fromJson(decryptedContent, ContactDetail.class);
                
                if (decryptedDetail != null) {
                    decryptedDetail.setUpdateHeight(contact.getLastHeight());
                    return decryptedDetail;
                }
            } 
        }  
        return null;
    }

    private static ContactDetail makeContactDetail(ContactDetail contactDetail, ApipClient apipClient) {
        if (contactDetail == null) return null;
        CidInfo cidInfo = apipClient.getFidCid(contactDetail.getFid());
        if (cidInfo == null) return null;
        contactDetail.setFid(cidInfo.getFid());
        contactDetail.setPubKey(cidInfo.getPubKey());
        String fee = cidInfo.getNoticeFee();
        if(fee!=null)
            contactDetail.setNoticeFee(Double.valueOf(fee));
        contactDetail.setCid(cidInfo.getCid());
        return contactDetail;
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public String getCid() {
        return cid;
    }

    public void setCid(String cid) {
        this.cid = cid;
    }

    public String getPubKey() {
        return pubKey;
    }

    public void setPubKey(String pubKey) {
        this.pubKey = pubKey;
    }

    public Double getNoticeFee() {
        return noticeFee;
    }

    public void setNoticeFee(Double noticeFee) {
        this.noticeFee = noticeFee;
    }

    public Long getUpdateHeight() {
        return updateHeight;
    }

    public void setUpdateHeight(Long updateHeight) {
        this.updateHeight = updateHeight;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public Boolean getSeeStatement() {
        return seeStatement;
    }

    public void setSeeStatement(Boolean seeStatement) {
        this.seeStatement = seeStatement;
    }

    public Boolean getSeeWritings() {
        return seeWritings;
    }

    public void setSeeWritings(Boolean seeWritings) {
        this.seeWritings = seeWritings;
    }
}
