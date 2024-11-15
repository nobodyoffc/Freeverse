package fcData;

import tools.DateTools;
import tools.JsonTools;
import feip.feipData.Secret;
import crypto.Decryptor;
import crypto.CryptoDataByte;
import crypto.Algorithm.Bitcore;
import java.util.Base64;
import java.util.List;

import java.util.ArrayList;
import appTools.Shower;

public class SecretDetail {
    private String secretId;
    private String type;
    private String title;
    private String content;
    private String memo;
    private Long updateHeight;

    // Similar utility methods as ContactDetail
    public byte[] toBytes() {
        return tools.JsonTools.toJson(this).getBytes();
    }

    public static SecretDetail fromBytes(byte[] bytes) {
        return tools.JsonTools.fromJson(new String(bytes), SecretDetail.class);
    }

    public static SecretDetail fromSecret(Secret secret, byte[] priKey) {
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
                            SecretDetail secretDetail = JsonTools.fromJson(new String(dataBytes), SecretDetail.class);
                            if (secretDetail != null) {
                                secretDetail.setSecretId(secret.getSecretId());
                                secretDetail.setUpdateHeight(secret.getLastHeight());
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

            cryptoDataByte.setPriKeyB(priKey);
            cryptoDataByte = decryptor.decrypt(cryptoDataByte);

            if (cryptoDataByte.getCode() == 0) {
                // Successfully decrypted
                String decryptedContent = new String(cryptoDataByte.getData());
                SecretDetail decryptedDetail = JsonTools.fromJson(decryptedContent, SecretDetail.class);
                
                if (decryptedDetail != null) {
                    decryptedDetail.setSecretId(secret.getSecretId());
                    decryptedDetail.setUpdateHeight(secret.getLastHeight());
                    return decryptedDetail;
                }
            }
        }
        return null;
    }

    public static void showSecretDetailList(List<SecretDetail> secretList, String title, int totalDisplayed) {
        String[] fields = new String[]{"Secret ID", "Update Time", "Title", "Type", "Memo"};
        int[] widths = new int[]{13, 10, 20, 10, 25};
        List<List<Object>> valueListList = new ArrayList<>();

        for (SecretDetail secret : secretList) {
            List<Object> showList = new ArrayList<>();
            showList.add(secret.getSecretId());
            if(secret.getUpdateHeight()!=null)showList.add(DateTools.longToTime(secret.getUpdateHeight(), "yyyy-MM-dd"));
            else showList.add(null);
            showList.add(secret.getTitle());
            showList.add(secret.getType());
            showList.add(secret.getMemo());
            valueListList.add(showList);
        }
        Shower.showDataTable(title, fields, widths, valueListList, totalDisplayed);
    }

    // Getters and Setters
    public String getSecretId() {
        return secretId;
    }

    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

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

    public Long getUpdateHeight() {
        return updateHeight;
    }

    public void setUpdateHeight(Long updateHeight) {
        this.updateHeight = updateHeight;
    }
}
