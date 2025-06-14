package app;

import ui.Shower;
import constants.FieldNames;
import core.crypto.Decryptor;
import core.crypto.Encryptor;
import core.crypto.KeyTools;
import data.fchData.Cid;
import utils.Hex;

import java.io.BufferedReader;
import java.util.*;

import static constants.FieldNames.*;
import static constants.Values.ASC;
import static constants.Values.DESC;
import static ui.Shower.DEFAULT_PAGE_SIZE;

public class CidInfo extends Cid {
    public static final String KEY_INFO_FILE_PATH = "keyInfo.json";

    private String prikeyCipher;
    private String label; //For CryptoSign
    private Boolean watchOnly;

    //For contact
    private String memo;
    private Boolean seeStatement;
    private Boolean seeWritings;
    private String contactId;
    private Long addedTime;


    public static String[] DefaultShowField = {
        FieldNames.ID,
        FieldNames.LABEL,
        FieldNames.IS_NOBODY,
        FieldNames.WATCH_ONLY
    };

    public static Integer[] DefaultShowWidth = {
            DEFAULT_ID_LENGTH,
            DEFAULT_TEXT_LENGTH,
            DEFAULT_BOOLEAN_LENGTH,
            DEFAULT_BOOLEAN_LENGTH
    };


    public static LinkedHashMap<String,Integer> getFieldWidthMap(){
        LinkedHashMap<String,Integer> map = new LinkedHashMap<>();
        map.put(ID, DEFAULT_ID_LENGTH);
        map.put(LABEL, DEFAULT_ID_LENGTH);
        map.put(IS_NOBODY, DEFAULT_BOOLEAN_LENGTH);
        map.put(WATCH_ONLY, DEFAULT_BOOLEAN_LENGTH);
        return map;
    }
    public static List<String> getTimestampFieldList(){
        return new ArrayList<>();
    }

    public static List<String> getSatoshiFieldList(){
        return new ArrayList<>();
    }
    public static Map<String, String> getHeightToTimeFieldMap() {
        return new HashMap<>();
    }

    public static Map<String, String> getShowFieldNameAsMap() {
        Map<String,String> map = new HashMap<>();
        map.put(ID,FID);
        return map;
    }
    public static Map<String, String> getFieldOrderMap() {
        Map<String, String> map = new HashMap<>();
        map.put(ADDED_TIME,DESC);
        map.put(LABEL,ASC);
        return map;
    }

    public static List<String> getReplaceWithMeFieldList() {
        return new ArrayList<>();
    }

    //For create with user input
    public static Map<String, Object> getInputFieldDefaultValueMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(IS_NOBODY,false);
        map.put(WATCH_ONLY,false);
        return map;
    }


    public CidInfo(){}
    public CidInfo(byte[] prikey, byte[] symkey) {
        super();
//        this.prikeyBytes=prikey;
        this.prikeyCipher = Encryptor.encryptBySymkeyToJson(prikey, symkey);
        this.pubkey = Hex.toHex(KeyTools.prikeyToPubkey(prikey));
        this.id = KeyTools.prikeyToFid(prikey);
        makeAddresses();
    }

    public CidInfo(String label, byte[] prikey, byte[] symkey) {
        super();
        this.prikeyBytes =prikey;
        this.prikeyCipher = Encryptor.encryptBySymkeyToJson(prikey, symkey);
        this.label = label;
        this.pubkey = Hex.toHex(KeyTools.prikeyToPubkey(prikey));
        this.id = KeyTools.prikeyToFid(prikey);
        makeAddresses();
    }

    public CidInfo(String label, String pubkey) {
        super();
        this.label = label;
        this.pubkey = pubkey;
        this.id = KeyTools.pubkeyToFchAddr(pubkey);
        makeAddresses();
    }


    public static List<CidInfo> showList(List<CidInfo> cidInfoList, BufferedReader br) {
        return Shower.showOrChooseListInPages("FID Info", cidInfoList, DEFAULT_PAGE_SIZE, null, true, CidInfo.class, br);
    }

    public void makeAddresses() {
        btcAddr = KeyTools.pubkeyToBtcAddr(pubkey);
        ethAddr = KeyTools.pubkeyToEthAddr(pubkey);
        bchAddr = KeyTools.pubkeyToBchBesh32Addr(pubkey);
        ltcAddr = KeyTools.pubkeyToLtcAddr(pubkey);
        dogeAddr = KeyTools.pubkeyToDogeAddr(pubkey);
        trxAddr = KeyTools.pubkeyToTrxAddr(pubkey);
    }

    public static CidInfo newKeyInfo(String label, byte[] prikey, byte[] symkey) {
        return new CidInfo(label, prikey, symkey);
    }

    public static CidInfo newKeyInfo(String label, String pubkey) {
        return new CidInfo(label, pubkey);
    }

    /**
     * Converts a Cid object to a CidInfo object
     * @param cid The Cid object to convert
     * @return A new CidInfo object with all properties from the Cid object
     */
    public static CidInfo fromCid(Cid cid) {
        if (cid == null) return null;
        
        CidInfo cidInfo = new CidInfo();
        cidInfo.setId(cid.getId());
        cidInfo.setCid(cid.getCid());
        cidInfo.setPubkey(cid.getPubkey());
        cidInfo.setMaster(cid.getMaster());
        cidInfo.setBalance(cid.getBalance());
        cidInfo.setCash(cid.getCash());
        cidInfo.setIncome(cid.getIncome());
        cidInfo.setExpend(cid.getExpend());
        cidInfo.setCd(cid.getCd());
        cidInfo.setCdd(cid.getCdd());
        cidInfo.setReputation(cid.getReputation());
        cidInfo.setHot(cid.getHot());
        cidInfo.setWeight(cid.getWeight());
        cidInfo.setGuide(cid.getGuide());
        cidInfo.setNoticeFee(cid.getNoticeFee());
        cidInfo.setHomepages(cid.getHomepages());
        cidInfo.setBtcAddr(cid.getBtcAddr());
        cidInfo.setEthAddr(cid.getEthAddr());
        cidInfo.setLtcAddr(cid.getLtcAddr());
        cidInfo.setDogeAddr(cid.getDogeAddr());
        cidInfo.setTrxAddr(cid.getTrxAddr());
        cidInfo.setBchAddr(cid.getBchAddr());
        cidInfo.setBirthHeight(cid.getBirthHeight());
        cidInfo.setNameTime(cid.getNameTime());
        cidInfo.setLastHeight(cid.getLastHeight());
        
        return cidInfo;
    }
    
    /**
     * Converts a Cid object to a CidInfo object and sets the prikeyCipher
     * @param cid The Cid object to convert
     * @param prikeyCipher The encrypted private key
     * @return A new CidInfo object with all properties from the Cid object and the provided prikeyCipher
     */
    public static CidInfo fromCid(Cid cid, String prikeyCipher) {
        CidInfo cidInfo = fromCid(cid);
        if (cidInfo != null) {
            cidInfo.setPrikeyCipher(prikeyCipher);
        }
        return cidInfo;
    }
    public String getPrikeyCipher() {
        return prikeyCipher;
    }

    public void setPrikeyCipher(String prikeyCipher) {
        this.prikeyCipher = prikeyCipher;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Boolean getWatchOnly() {
        return watchOnly;
    }

    public void setWatchOnly(Boolean watchOnly) {
        this.watchOnly = watchOnly;
    }

    public byte[] decryptPrikey(byte[] symkey) {
        return Decryptor.decryptPrikey(prikeyCipher,symkey);
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

    public Long getAddedTime() {
        return addedTime;
    }

    public void setAddedTime(Long addedTime) {
        this.addedTime = addedTime;
    }
}
