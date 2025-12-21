package data.feipData;

import core.crypto.Algorithm.Bitcore;

import data.fcData.FcEntity;
import data.fchData.Freer;
import clients.ApipClient;
import utils.FcUtils;
import core.crypto.Decryptor;
import core.crypto.CryptoDataByte;
import utils.JsonUtils;

import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import ui.Shower;

import static constants.FieldNames.*;

public class Contact extends FcEntity {

    private String alg;
    private String cipher;

    private String owner;
    private Long birthTime;
    private Long birthHeight;
    private Long lastHeight;
    private Boolean active;

    //On chain details

    private String fid;
    private String cid;
    private String pubkey;

    private List<String> titles;

    private Double noticeFee;
    private String memo;
    private Boolean seeStatement;
    private Boolean seeWritings;



	public static LinkedHashMap<String,Integer>getFieldWidthMap(){
		LinkedHashMap<String,Integer> map = new LinkedHashMap<>();
        map.put(CID, DEFAULT_ID_LENGTH);
        map.put(FID, DEFAULT_ID_LENGTH);
        map.put(TITLES, DEFAULT_TEXT_LENGTH);
        map.put(MEMO, DEFAULT_TEXT_LENGTH);
        map.put(NOTICE_FEE, DEFAULT_AMOUNT_LENGTH);
        map.put(UPDATE_HEIGHT, DEFAULT_TIME_LENGTH);
		return map;
	}
	public static List<String> getTimestampFieldList(){
		return new ArrayList<>();
	}

	public static List<String> getSatoshiFieldList(){
		return new ArrayList<>();
	}
	public static Map<String, String> getHeightToTimeFieldMap() {
		Map<String, String> map = new HashMap<>();
		map.put(UPDATE_HEIGHT, UPDATE_TIME);
		return map;
	}

	public static Map<String, String> getShowFieldNameAsMap() {
		return new HashMap<>();
	}
    public static Map<String, Object> getInputFieldDefaultValueMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(FID, "");
        map.put(TITLES, new ArrayList<>().add(""));
        map.put(MEMO, "");
        map.put(SEE_STATEMENT, true);
        map.put(SEE_WRITINGS, true);
        return map;
    }

    public static Contact fromCidInfo(Freer cid) {
        Contact contact = new Contact();
        contact.setFid(cid.getId());
        contact.setCid(cid.getCid());
        String fee = cid.getNoticeFee();
        if(fee!=null)
            contact.setNoticeFee(Double.valueOf(fee));
        contact.setPubkey(cid.getPubkey());
        contact.setLastHeight(cid.getLastHeight());
        return contact;
    }

    public byte[] toBytes(){
        return JsonUtils.toJson(this).getBytes();
    }

    public static Contact fromBytes(byte[] bytes){
        return JsonUtils.fromJson(new String(bytes), Contact.class);
    }

    public static Contact parseDetail(Contact contact, byte[] priKey, ApipClient apipClient) {
        Contact contactDetail = parseDetail(contact,priKey);
        if (contactDetail == null) return null;
        return makeContactDetail(contactDetail, apipClient);    
    }

    private static Contact parseDetail(Contact contact, byte[] priKey) {
        
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
                            if(dataBytes == null)return null;
                        }catch (Exception e){
                            return null;
                        }
                        Contact contactDetail = JsonUtils.fromJson(new String(dataBytes), Contact.class);
                        if(contactDetail!=null){
                            contactDetail.setId(contact.getId());
                            contactDetail.setLastHeight(contact.getLastHeight());
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

            cryptoDataByte.setPrikeyB(priKey);
            cryptoDataByte = decryptor.decrypt(cryptoDataByte);

            if (cryptoDataByte.getCode() == 0) {
                // Successfully decrypted
                decryptedContent = new String(cryptoDataByte.getData());
                Contact decryptedDetail = JsonUtils.fromJson(decryptedContent, Contact.class);
                
                if (decryptedDetail != null) {
                    decryptedDetail.setId(contact.getId());
                    decryptedDetail.setLastHeight(contact.getLastHeight());
                    return decryptedDetail;
                }
            } 
        }  
        return null;
    }

    private static Contact makeContactDetail(Contact contact, ApipClient apipClient) {
        if (contact == null) return null;
        Freer cid = apipClient.getFidCid(contact.getFid());
        if (cid == null) return null;
        contact.setFid(cid.getId());
        contact.setPubkey(cid.getPubkey());
        String fee = cid.getNoticeFee();
        if(fee!=null)
            contact.setNoticeFee(Double.valueOf(fee));
        contact.setCid(cid.getCid());
        return contact;
    }

    public static void showContactDetailList(List<Contact> contactList, String title, int totalDisplayed) {
        String[] fields = new String[]{"CID", "FID", "Title","Memo", "Notice Fee", "Update Time"};
        int[] widths = new int[]{10, 10, 10,30, 25, 20};
        List<List<Object>> valueListList = new ArrayList<>();

        for (Contact contact : contactList) {
            List<Object> showList = new ArrayList<>();
            showList.add(contact.getCid());
            showList.add(contact.getFid());
            showList.add(contact.getTitles());
            showList.add(contact.getMemo());
            showList.add(contact.getNoticeFee());
            showList.add(FcUtils.heightToLongDate(contact.getLastHeight()));
            valueListList.add(showList);
        }
        Shower.showOrChooseList(title, fields, widths, valueListList, null);
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

    public String getPubkey() {
        return pubkey;
    }

    public void setPubkey(String pubkey) {
        this.pubkey = pubkey;
    }

    public Double getNoticeFee() {
        return noticeFee;
    }

    public void setNoticeFee(Double noticeFee) {
        this.noticeFee = noticeFee;
    }

    public Long getLastHeight() {
        return lastHeight;
    }

    public void setLastHeight(Long lastHeight) {
        this.lastHeight = lastHeight;
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

    public List<String> getTitles() {
        return titles;
    }

    public void setTitles(List<String> titles) {
        this.titles = titles;
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
    public String getOwner() {
        return owner;
    }
    public void setOwner(String owner) {
        this.owner = owner;
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
    public Boolean getActive() {
        return active;
    }
    public void setActive(Boolean active) {
        this.active = active;
    }
}
