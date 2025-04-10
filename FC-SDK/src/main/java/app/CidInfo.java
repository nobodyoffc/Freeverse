package app;

import appTools.Shower;
import constants.FieldNames;
import crypto.Decryptor;
import crypto.Encryptor;
import crypto.KeyTools;
import fch.fchData.Cid;
import utils.Hex;

import java.io.BufferedReader;
import java.util.List;

import static appTools.Shower.DEFAULT_PAGE_SIZE;

public class CidInfo extends Cid {
    public static final String KEY_INFO_FILE_PATH = "keyInfo.json";

    private String priKeyCipher;
    private String label; //For CryptoSign
    private Boolean watchOnly;

    //For contact
    private String memo;
    private Boolean seeStatement;
    private Boolean seeWritings;
    private String contactId;


    public static String[] DefaultShowField = {
        FieldNames.ID,
        FieldNames.LABEL,
        FieldNames.IS_NOBODY,
        FieldNames.WATCH_ONLY
    };

    public static Integer[] DefaultShowWidth = {
        ID_DEFAULT_SHOW_SIZE,
        TEXT_DEFAULT_SHOW_SIZE,
        BOOLEAN_DEFAULT_SHOW_SIZE,
        BOOLEAN_DEFAULT_SHOW_SIZE
    };

    public CidInfo(){}
    public CidInfo(byte[] priKey, byte[] symKey) {
        super();
//        this.priKeyBytes=priKey;
        this.priKeyCipher = Encryptor.encryptBySymKeyToJson(priKey, symKey);
        this.pubKey = Hex.toHex(KeyTools.priKeyToPubKey(priKey));
        this.id = KeyTools.priKeyToFid(priKey);
        makeAddresses();
    }

    public CidInfo(String label, byte[] priKey, byte[] symKey) {
        super();
        this.priKeyBytes=priKey;
        this.priKeyCipher = Encryptor.encryptBySymKeyToJson(priKey, symKey);
        this.label = label;
        this.pubKey = Hex.toHex(KeyTools.priKeyToPubKey(priKey));
        this.id = KeyTools.priKeyToFid(priKey);
        makeAddresses();
    }

    public CidInfo(String label, String pubKey) {
        super();
        this.label = label;
        this.pubKey = pubKey;
        this.id = KeyTools.pubKeyToFchAddr(pubKey);
        makeAddresses();
    }


    public static List<CidInfo> showList(List<CidInfo> cidInfoList, BufferedReader br) {
        return Shower.showOrChooseListInPages("FID Info", cidInfoList, DEFAULT_PAGE_SIZE, null, true, CidInfo.class, br);
    }

    public void makeAddresses() {
        btcAddr = KeyTools.pubKeyToBtcAddr(pubKey);
        ethAddr = KeyTools.pubKeyToEthAddr(pubKey);
        bchAddr = KeyTools.pubKeyToBchBesh32Addr(pubKey);
        ltcAddr = KeyTools.pubKeyToLtcAddr(pubKey);
        dogeAddr = KeyTools.pubKeyToDogeAddr(pubKey);
        trxAddr = KeyTools.pubKeyToTrxAddr(pubKey);
    }

    public static CidInfo newKeyInfo(String label, byte[] priKey, byte[] symKey) {
        return new CidInfo(label, priKey, symKey);
    }

    public static CidInfo newKeyInfo(String label, String pubKey) {
        return new CidInfo(label, pubKey);
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
        cidInfo.setPubKey(cid.getPubKey());
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
     * Converts a Cid object to a CidInfo object and sets the priKeyCipher
     * @param cid The Cid object to convert
     * @param priKeyCipher The encrypted private key
     * @return A new CidInfo object with all properties from the Cid object and the provided priKeyCipher
     */
    public static CidInfo fromCid(Cid cid, String priKeyCipher) {
        CidInfo cidInfo = fromCid(cid);
        if (cidInfo != null) {
            cidInfo.setPriKeyCipher(priKeyCipher);
        }
        return cidInfo;
    }
    public String getPriKeyCipher() {
        return priKeyCipher;
    }

    public void setPriKeyCipher(String priKeyCipher) {
        this.priKeyCipher = priKeyCipher;
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

    public byte[] decryptPriKey(byte[] symKey) {
        return Decryptor.decryptPriKey(priKeyCipher,symKey);
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
