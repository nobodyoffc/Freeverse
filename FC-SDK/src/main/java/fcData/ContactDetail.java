package fcData;

import crypto.Algorithm.Bitcore;

import fch.fchData.Cid;
import clients.ApipClient;
import tools.FchTools;
import feip.feipData.Contact;
import crypto.Decryptor;
import crypto.CryptoDataByte;
import tools.JsonTools;

import java.util.Base64;
import java.util.List;
import java.util.ArrayList;
import appTools.Shower;

public class ContactDetail extends FcObject {
    private String fid;
    private String cid;
    private String pubKey;
    private Double noticeFee;

    private String memo;
    private Boolean seeStatement;
    private Boolean seeWritings;

    private Long updateHeight;
    private String contactId;

    public static ContactDetail fromCidInfo(Cid cid) {
        ContactDetail contactDetail = new ContactDetail();
        contactDetail.setFid(cid.getId());
        contactDetail.setCid(cid.getCid());
        String fee = cid.getNoticeFee();
        if(fee!=null)
            contactDetail.setNoticeFee(Double.valueOf(fee));
        contactDetail.setPubKey(cid.getPubKey());
        contactDetail.setUpdateHeight(cid.getLastHeight());
        return contactDetail;
    }

    public byte[] toBytes(){
        return tools.JsonTools.toJson(this).getBytes();
    }

    public static ContactDetail fromBytes(byte[] bytes){
        return tools.JsonTools.fromJson(new String(bytes), ContactDetail.class);
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
                            contactDetail.setContactId(contact.getId());
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
                    decryptedDetail.setContactId(contact.getId());
                    decryptedDetail.setUpdateHeight(contact.getLastHeight());
                    return decryptedDetail;
                }
            } 
        }  
        return null;
    }

    private static ContactDetail makeContactDetail(ContactDetail contactDetail, ApipClient apipClient) {
        if (contactDetail == null) return null;
        Cid cid = apipClient.getFidCid(contactDetail.getFid());
        if (cid == null) return null;
        contactDetail.setFid(cid.getId());
        contactDetail.setPubKey(cid.getPubKey());
        String fee = cid.getNoticeFee();
        if(fee!=null)
            contactDetail.setNoticeFee(Double.valueOf(fee));
        contactDetail.setCid(cid.getCid());
        return contactDetail;
    }

    public static void showContactDetailList(List<ContactDetail> contactList, String title, int totalDisplayed,boolean isShortTimestamp) {
        String[] fields = new String[]{"CID", "FID", "Memo", "Notice Fee", "Update Time"};
        int[] widths = new int[]{10, 10, 10, 25, 20};
        List<List<Object>> valueListList = new ArrayList<>();

        for (ContactDetail contact : contactList) {
            List<Object> showList = new ArrayList<>();
            showList.add(contact.getCid());
            showList.add(contact.getFid());
            showList.add(contact.getMemo());
            showList.add(contact.getNoticeFee());
            showList.add(FchTools.heightToLongDate(contact.getUpdateHeight()));
            valueListList.add(showList);
        }
        Shower.showDataTable(title, fields, widths, valueListList, totalDisplayed, isShortTimestamp);
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

    public String getContactId() {
        return contactId;
    }

    public void setContactId(String contactId) {
        this.contactId = contactId;
    }
}
