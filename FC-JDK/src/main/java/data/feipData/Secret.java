package data.feipData;

import data.fcData.FcObject;
import ui.Inputer;
import core.crypto.Base58;
import core.crypto.Encryptor;
import org.jetbrains.annotations.NotNull;
import utils.BytesUtils;
import utils.FcUtils;
import utils.JsonUtils;
import core.crypto.Decryptor;
import core.crypto.CryptoDataByte;
import core.crypto.Algorithm.Bitcore;

import java.io.BufferedReader;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import ui.Shower;

import static ui.Inputer.askIfYes;
import static constants.FieldNames.*;
import static constants.FieldNames.ID;

public class Secret extends FcObject {

    private String alg;
    private String cipher;

    private String owner;
    private Long birthTime;
    private Long birthHeight;
    private Long lastHeight;
    private Boolean active;

    //On chain detail
    private String type;
    private String title;
    private String content;
    private String memo;
    private String contentCipher;

    public static LinkedHashMap<String,Integer> getFieldWidthMap(){
        LinkedHashMap<String,Integer> fieldWidthMap = new LinkedHashMap<>();
        fieldWidthMap.put(TITLE, DEFAULT_TEXT_LENGTH);
        fieldWidthMap.put(TYPE, DEFAULT_SHORT_TEXT_LENGTH);
        fieldWidthMap.put(UPDATE_HEIGHT, DEFAULT_TIME_LENGTH);
        fieldWidthMap.put(MEMO, DEFAULT_TEXT_LENGTH);
        fieldWidthMap.put(ID, DEFAULT_ID_LENGTH);
        return fieldWidthMap;
    }

    public static List<String> getTimestampFieldList(){
        List<String> timestampFieldList = new ArrayList<>();
        timestampFieldList.add(UPDATE_HEIGHT);
        return timestampFieldList;
    }

    public static Map<String,String> getShowFieldNameAs(){
        Map<String,String> showFieldNameAs = new HashMap<>();
        showFieldNameAs.put(UPDATE_HEIGHT, "Update Time");
        return showFieldNameAs;
    }
    
    @NotNull
    public static Secret inputSecret(BufferedReader br, byte[] symKey) {
        Secret secret = new Secret();
        secret.setTitle(Inputer.inputString(br, "Input the title:"));
        String content= null;
        if(askIfYes(br, "Generate a random content?")){
            int length = Inputer.inputInt(br, "Input the length no more than 128:", 128);
            String randomContent = Base58.encode(BytesUtils.getRandomBytes(length));
            System.out.println("Random content: "+randomContent);
            if(askIfYes(br,"Input more for content?"))content = Inputer.inputString(br, "Input:");
            content = randomContent + content;
        }else{
            content = Inputer.inputString(br, "Input the content:");
        }
        if(content !=null && !content.isEmpty()){
            String cipher = Encryptor.encryptBySymkeyToJson(content.getBytes(), symKey);
            secret.setContentCipher(cipher);
        }
        secret.setType(Inputer.inputString(br, "Input the type:"));
        secret.setMemo(Inputer.inputString(br, "Input the memo:"));
        return secret;
    }

    // Similar utility methods as ContactDetail
    public byte[] toBytes() {
        return JsonUtils.toJson(this).getBytes();
    }

    public static Secret fromBytes(byte[] bytes) {
        return JsonUtils.fromJson(new String(bytes), Secret.class);
    }

    public static Secret parseDetail(Secret secret, byte[] priKey) {
        if (secret.getCipher() != null && !secret.getCipher().isEmpty()) {
            Decryptor decryptor = new Decryptor();
            String cipher = secret.getCipher();
            byte[] cipherBytes = null;
            CryptoDataByte cryptoDataByte = null;

            if (cipher.startsWith("A")) {
                try {
                    cipherBytes = Base64.getDecoder().decode(cipher);
                } catch (IllegalArgumentException e) {
                    return null; // Not Base64
                }

                try {
                    cryptoDataByte = CryptoDataByte.fromBundle(cipherBytes);

                    // Not FC algorithm
                    if (cryptoDataByte == null || cryptoDataByte.getCode() != 0) {
                        try {
                            byte[] dataBytes = Bitcore.decrypt(cipherBytes, priKey);
                            Secret secretDetail = JsonUtils.fromJson(new String(dataBytes), Secret.class);
                            if (secretDetail != null) {
                                secretDetail.setId(secret.getId());
                                secretDetail.setLastHeight(secret.getLastHeight());
                                return secretDetail;
                            }
                        } catch (Exception e) {
                            return null;
                        }
                        return null;
                    }
                } catch (Exception ignore) {
                }
            }

            // FC algorithm
            if (cryptoDataByte == null) {
                try {
                    cryptoDataByte = CryptoDataByte.fromJson(cipher);
                } catch (Exception e) {
                    return null;
                }
            }

            cryptoDataByte.setPrikeyB(priKey);
            cryptoDataByte = decryptor.decrypt(cryptoDataByte);

            if (cryptoDataByte.getCode() == 0) {
                // Successfully decrypted
                String decryptedContent = new String(cryptoDataByte.getData());
                Secret decryptedDetail = JsonUtils.fromJson(decryptedContent, Secret.class);

                if (decryptedDetail != null) {
                    decryptedDetail.setId(secret.getId());
                    decryptedDetail.setLastHeight(secret.getLastHeight());
                    return decryptedDetail;
                }
            }
        }
        return null;
    }

    public static void showSecretDetailList(List<Secret> secretList, String title, int totalDisplayed) {
        String[] fields = new String[]{"Secret ID", "Update Time", "Title", "Type", "Memo"};
        int[] widths = new int[]{13, 10, 20, 10, 25};
        List<List<Object>> valueListList = new ArrayList<>();

        for (Secret secret : secretList) {
            List<Object> showList = new ArrayList<>();
            showList.add(secret.getId());
            if (secret.getLastHeight() != null)
                showList.add(FcUtils.heightToLongDate(secret.getLastHeight()));
            else showList.add(null);
            showList.add(secret.getTitle());
            showList.add(secret.getType());
            showList.add(secret.getMemo());
            valueListList.add(showList);
        }
        Shower.showOrChooseList(title, fields, widths, valueListList, null);
    }

    // Getters and Setters

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public Long getLastHeight() {
        return lastHeight;
    }

    public void setLastHeight(Long lastHeight) {
        this.lastHeight = lastHeight;
    }

    public String getContentCipher() {
        return contentCipher;
    }

    public void setContentCipher(String contentCipher) {
        this.contentCipher = contentCipher;
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
    public void setActive(Boolean active) {
        this.active = active;
    }
    public Boolean getActive() {
        return active;
    }
}
