package fcData;

import clients.ApipClient;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;

import constants.Strings;
import crypto.Algorithm.Bitcore;
import crypto.CryptoDataByte;
import crypto.Decryptor;
import crypto.KeyTools;
import feip.feipData.Mail;
import utils.*;
import utils.http.AuthType;
import utils.http.RequestMethod;
import org.bitcoinj.core.ECKey;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;


public class MailDetail extends FcObject{
    private transient String id;
//    private transient byte[] idBytes;
    private String mailId;
    private Long time;
    private String from;
    private String to;
    private transient String fromCid;
    private transient String toCid;
    private String content;
    private Boolean active;
//
//    public static void main(String[] args) {
//        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
//        List<MailDetail> chosenMails = new ArrayList<>();
//        byte[] lastId = null;
//        int totalDisplayed = 0;
//        PersistentSequenceMap mailDB = new PersistentSequenceMap(null,"b0819972aa5f78d28606a571c49356a2577a4df6e257e0e84a61b51fbb4417ba", Strings.MAIL,null);
//        List<MailDetail> currentList = mailDB.getListFromEnd(null, 3, (byte[] value) -> MailDetail.fromBytes(value));
//        if (currentList.isEmpty()) {
//            return;
//        }
//
//        for (int i = 0; i < currentList.size(); i++) {
//            MailDetail mail = currentList.get(i);
//            String from;
//            if(mail.getFromCid()!=null)from = StringUtils.omitMiddle(mail.getFromCid(), 13);
//            else from = StringUtils.omitMiddle(mail.getFrom(), 13);
//            String to;
//            if(mail.getToCid()!=null)to = StringUtils.omitMiddle(mail.getToCid(), 13);
//            else to = StringUtils.omitMiddle(mail.getTo(), 13);
//            String date = DateUtils.longToTime(mail.getTime(),"yy-mm-dd");
//            System.out.printf("%d. %s %s To %s Subject: %s%n", totalDisplayed + i + 1,date, from,to, StringUtils.omitMiddle(mail.getContent(), 30));
//        }
//
//        System.out.println("Enter mail numbers to select (comma-separated), 'q' to quit, or press Enter for more:");
//        String input = null;
//        try {
//            input = br.readLine().trim().toLowerCase();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//
//        if (input.equals("q")) {
//            return;
//        }
//
//        String[] selections = input.split(",");
//        for (String selection : selections) {
//            try {
//                int index = Integer.parseInt(selection.trim()) - 1;
//                if (index >= 0 && index < totalDisplayed + currentList.size()) {
//                    int listIndex = index - totalDisplayed;
//                    chosenMails.add(currentList.get(listIndex));
//                }
//            } catch (NumberFormatException e) {
//                System.out.println("Invalid input: " + selection);
//            }
//        }
//
//        totalDisplayed += currentList.size();
//        lastId = currentList.get(currentList.size() - 1).getIdBytes();
//        System.out.println("Total:"+totalDisplayed+". LastId:"+Hex.toHex(lastId));
//    }
//
//    private static void testMailBytesJson() {
//        MailDetail mailDetail = MailDetail.newSample();
//        mailDetail.getIdBytes();
//
//        JsonUtils.printJson(mailDetail);
//
//
//        byte[] bytes = mailDetail.toBytes();
//        MailDetail newMail = MailDetail.fromBytes(bytes);
//        JsonUtils.printJson(newMail);
//
//        TalkUnit talkUnit = TalkUnit.newSample();
//        JsonUtils.printJson(talkUnit);
//
//        JsonUtils.printJson(TalkUnit.fromBytes(talkUnit.toBytes()));
//    }


//    public static MailDetail newSample(){
//        MailDetail mailDetail = new MailDetail();
//        mailDetail.setMailId(Hex.toHex(BytesUtils.getRandomBytes(32)));
//        mailDetail.setTime(System.currentTimeMillis());
//        mailDetail.setFrom(KeyTools.pubKeyToFchAddr(new ECKey().getPubKey()));
//        mailDetail.setTo(KeyTools.pubKeyToFchAddr(new ECKey().getPubKey()));
//        mailDetail.setContent("The content of "+mailDetail.getMailId());
//        return mailDetail;
//    }

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

    public static MailDetail fromBytes(byte[] bundle){
        MailDetail mailDetail = new MailDetail();
        try(ByteArrayInputStream bis = new ByteArrayInputStream(bundle)) {
            mailDetail.setTime(BytesUtils.bytes8ToLong(bis.readNBytes(8),false));
            mailDetail.setMailId(Hex.toHex(bis.readNBytes(32)));
            mailDetail.setFrom(KeyTools.hash160ToFchAddr(bis.readNBytes(20)));
            mailDetail.setTo(KeyTools.hash160ToFchAddr(bis.readNBytes(20)));
            
            // Read fromCid
            int fromCidLength = bis.read();
            if (fromCidLength > 0) {
                byte[] fromCidBytes = bis.readNBytes(fromCidLength);
                mailDetail.setFromCid(new String(fromCidBytes));
            }
            
            // Read toCid
            int toCidLength = bis.read();
            if (toCidLength > 0) {
                byte[] toCidBytes = bis.readNBytes(toCidLength);
                mailDetail.setToCid(new String(toCidBytes));
            }
            
            mailDetail.setContent(new String(bis.readAllBytes()));
            return mailDetail;
        } catch (IOException e) {
            System.out.println("Failed to make Mail Detail from bundle:"+e.getMessage());
            return null;
        }
    }

    public static MailDetail fromMail(String myFid, Mail mail, byte[] priKey, ApipClient apipClient) {
        MailDetail mailDetail = new MailDetail();
        // Transfer relevant data from Mail to MailDetail

        mailDetail.setMailId(mail.getId());
        mailDetail.setTime(mail.getBirthTime() * 1000);
        mailDetail.setFrom(mail.getSender());
        mailDetail.setTo(mail.getRecipient());
        mailDetail.setActive(mail.getActive());

        CryptoDataByte cryptoDataByte = decryptMail(myFid, mail, priKey, apipClient);
        if (cryptoDataByte == null) return null;

        if (cryptoDataByte.getCode() == 0) {
            mailDetail.setContent(new String(cryptoDataByte.getData()));
        } else {
            mailDetail.setContent("Failed to decrypt mail "+mail.getId());
        }

        return mailDetail;
    }

    @Nullable
    private static CryptoDataByte decryptMail(String myFid, Mail mail, byte[] priKey, ApipClient apipClient) {
        String alg = null;
        String cipher;
        if(mail.getAlg()!=null)alg = mail.getAlg();

        if(mail.getCipher()!=null)cipher = mail.getCipher();
        else if(mail.getCipherReci()!=null && myFid.equals(mail.getRecipient()))cipher = mail.getCipherReci();
        else if(mail.getCipherSend()!=null && myFid.equals(mail.getSender()))cipher = mail.getCipherSend();
        else return null;

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
        if(cryptoDataByte==null) return null;

        if(myFid.equals(mail.getRecipient()))cryptoDataByte.setPriKeyB(priKey);
        if(myFid.equals(mail.getSender())){
            cryptoDataByte.setPriKeyA(priKey);
            String pubKeyB = apipClient.getPubKey(mail.getRecipient(), RequestMethod.POST, AuthType.FC_SIGN_BODY);
            cryptoDataByte.setPubKeyB(Hex.fromHex(pubKeyB));
        }

        Decryptor decryptor = new Decryptor();
        decryptor.decrypt(cryptoDataByte);
        return cryptoDataByte;
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
}
