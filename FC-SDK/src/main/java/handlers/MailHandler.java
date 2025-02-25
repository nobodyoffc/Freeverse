package handlers;

import apip.apipData.CidInfo;
import appTools.Inputer;
import appTools.Menu;
import appTools.Settings;
import appTools.Shower;
import clients.ApipClient;
import clients.Client;
import constants.Constants;
import crypto.CryptoDataByte;
import crypto.Encryptor;
import crypto.KeyTools;
import fcData.AlgorithmId;
import fcData.IdNameTools;
import fcData.MailDetail;
import fcData.Op;
import fch.Wallet;
import fch.fchData.SendTo;
import feip.feipData.Feip;
import feip.feipData.Mail;
import feip.feipData.MailOpData;
import feip.feipData.Service;
import org.jetbrains.annotations.Nullable;
import tools.*;
import tools.http.AuthType;
import tools.http.RequestMethod;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static appTools.Inputer.askIfYes;
import static constants.Constants.Dust;
import static constants.OpNames.*;

public class MailHandler extends Handler {
    // Constants and Enums
    private static final Integer DEFAULT_SIZE = 50;

    public enum MailOp {
        SEND(Op.SEND),
        DELETE(Op.DELETE),
        RECOVER(Op.RECOVER);

        public final Op op;
        MailOp(Op op) { this.op = op; }

        public String toLowerCase() {
            return this.name().toLowerCase();
        }   
    }

    // Instance Variables
    private final BufferedReader br;
    private final String myFid;
    private final ApipClient apipClient;
    private final CashHandler cashHandler;
    private final byte[] symKey;
    private final String myPriKeyCipher;
    private final PersistentSequenceMap mailDB;
    private List<String> failedDecryptMailIdList;

    // Constructor
    public MailHandler(String myFid, ApipClient apipClient, CashHandler cashHandler, byte[] symKey, String myPriKeyCipher,String dbPath, BufferedReader br) {
        this.myFid = myFid;
        this.apipClient = apipClient;
        this.cashHandler = cashHandler;
        this.symKey = symKey;
        this.myPriKeyCipher = myPriKeyCipher;
        this.br = br;
        this.mailDB = new PersistentSequenceMap(myFid,null, constants.Strings.MAIL, dbPath);
    }

    public MailHandler(Settings settings){
        this.apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);
        this.cashHandler = (CashHandler) settings.getHandler(HandlerType.CASH);
        this.myFid = settings.getMainFid();
        this.symKey = settings.getSymKey();
        this.myPriKeyCipher = settings.getMyPriKeyCipher();
        this.br = settings.getBr(); 
        this.mailDB = new PersistentSequenceMap(myFid,null, constants.Strings.MAIL, settings.getDbDir());
    }   

    // Public Methods
    public void menu() {
        Menu menu = new Menu("Mail Menu", this::close);
        menu.add(READ, () -> readMails(br));
        menu.add(UPDATE, () -> checkMail(br));
        menu.add(MailOp.SEND.toLowerCase(), () -> sendMails(br));
        menu.add(MailOp.DELETE.toLowerCase(), () -> choseToDelete(br));
        menu.add(MailOp.RECOVER.toLowerCase(), () -> recoverMails(br));
        menu.showAndSelect(br);
    }

    public String sendMail(BufferedReader br) {
        return opMail(null, null, br, MailOp.SEND);
    }

    public void sendMails(BufferedReader br) {
        while (true) {
            sendMail(br);
            if (!askIfYes(br, "Do you want to send another mail?")) {
                break;
            }
        }
    }


    public void recoverMails(BufferedReader br) {
        List<String> last = new ArrayList<>();
        List<String> chosenMailIds = new ArrayList<>();
        
        while (true) {
            List<Mail> currentBatch = fetchMailList(0L, false, last, DEFAULT_SIZE);
            if (currentBatch==null || currentBatch.isEmpty()) break;
            
            List<MailDetail> currentMailDetailList = mailToMailDetail(currentBatch);

            System.out.println("Loaded " + currentMailDetailList.size() + " deleted mails.");
            if (!askIfYes(br,"View or choose them?")) break;
            
            List<MailDetail> chosenMailDetails = choseFromMailDetailList(currentMailDetailList, br);

            System.out.println("You chosen " + chosenMailDetails.size() + " mails.");

            if (askIfYes(br,"View them?")) chooseToShow(chosenMailDetails, br);

            List<String> currentChosenMailIds = chosenMailDetails.stream().map(MailDetail::getMailId).collect(Collectors.toList());
            chosenMailIds.addAll(currentChosenMailIds);
            
            if (!askIfYes(br,"Load more mails?")) break;
        }
        
        if (chosenMailIds.isEmpty()) {
            System.out.println("No deleted mails chosen.");
            return;
        }
        
        if (askIfYes(br,"Recover " + chosenMailIds.size() + " mails?")) {
            MailOp op = MailOp.RECOVER;
            opMail(null, chosenMailIds, br, op);
        }
    }

    public String findMailId(BufferedReader br) {
        String mailId;
        while (true) {
            MailDetail mailDetail = findMail(br);
            if (mailDetail == null) continue;
            mailId = mailDetail.getMailId();
            break;
        }
        return mailId;
    }

    @Nullable
    public MailDetail findMail(BufferedReader br) {
        String input = Inputer.inputString(br,"Input the FID, CID or part of the content:");
        List<MailDetail> list = findMailDetails(input);
        MailDetail mailDetail = chooseOneMailDetailFromList(list, br);
        if (mailDetail == null) return null;
        return mailDetail;
    }

    public void checkMail(BufferedReader br) {
        List<String> last = null;
        List<Mail> mailList = null;
        Long lastHeight = mailDB.getLastHeight();
        mailList = loadAllMailList(lastHeight, true, last, DEFAULT_SIZE);

        List<MailDetail> mailDetailList = new ArrayList<>();

        if (!mailList.isEmpty()) {
            mailDetailList = mailToMailDetail(mailList);
            mailDB.setLastHeight(mailList.get(0).getLastHeight());
            deleteInvalidMails(br);
        }

        System.out.println("You have " + mailDetailList.size() + " unread mails.");
        if (mailDetailList.size() > 0) chooseToShow(mailDetailList, br);
    }

    private void deleteInvalidMails(BufferedReader br) {
        if(failedDecryptMailIdList.isEmpty()) return;   
        if(! askIfYes(br,"Found "+failedDecryptMailIdList.size()+"invalid mails. Delete them?")){
            return;
        }
        String result = opMail(null,failedDecryptMailIdList, br,MailOp.DELETE);
        if(Hex.isHex32(result)){
            System.out.println(failedDecryptMailIdList.size()+" invalid mails deleted in TX "+result+".");
            failedDecryptMailIdList.clear();
        }else{
            System.out.println("Failed to delete invalid mails: "+result);
        }
    }

    public void readRecentMails(BufferedReader br) {
        List<MailDetail> recentMails = chooseMailDetails(br);
        System.out.println("You chosen " + recentMails.size() + " mails. Read them...");
        chooseToShow(recentMails, br);
    }

    public void readMails(BufferedReader br) {
        List<MailDetail> chosenMails = null;
        
        String input;
        while (true) {
            input = Inputer.inputString(br, "Input FID or search string. 'q' to quit. Enter to list all mails and choose some:");
            if ("q".equals(input)) return;
            if ("".equals(input)) {
                chosenMails = chooseMailDetails(br);
                if (chosenMails.isEmpty()) {
                    return;
                }
            } else {
                List<MailDetail> foundMailDetailList = findMailDetails(input);
                chosenMails = choseFromMailDetailList(foundMailDetailList,br);
            }

            System.out.println("You chosen " + chosenMails.size() + " mails.");

            String op = Inputer.chooseOne(
                    new String[]{READ,MailOp.DELETE.toLowerCase()},
                    null,"Select to operate the mails:",br);

            switch (op) {
                case READ:
                    chooseToShow(chosenMails, br);
                    break;
                case DELETE:
                    delete(br, chosenMails);
                    break;
                default:
                    break;
            }
            
        }
    }

    public void choseToDelete(BufferedReader br) {
        List<MailDetail> chosenMails = chooseMailDetails(br);
        
        delete(br, chosenMails);
    }

    private void delete(BufferedReader br, List<MailDetail> chosenMails) {
        if (chosenMails.isEmpty()) {
            System.out.println("No mails chosen for deletion.");
            return;
        }
        
    
        if (askIfYes(br, "View them before delete?")) {
            chooseToShow(chosenMails, br);
        }
        
        List<String> mailIds = chosenMails.stream().map(MailDetail::getMailId).collect(Collectors.toList());
        if (askIfYes(br, "Delete " + chosenMails.size() + " mails?")) {
            MailOp op = MailOp.DELETE;
            String result = opMail(null, mailIds, br, op);
            if (Hex.isHex32(result)) {    
                System.out.println("Deleted mails.");
            } else {
                System.out.println("Failed to delete mails.");
            }
        }
    }

    // Private Methods
    private String opMail(String mailId, List<String> mailIds, BufferedReader br, MailOp op) {
        if (op == null) return null;

        MailOpData mailOpData = new MailOpData();
        mailOpData.setOp(op.toLowerCase());
        byte[] priKey = Client.decryptPriKey(myPriKeyCipher, symKey);
        if (priKey == null) {
            System.out.println("Failed to get the priKey of " + myFid);
            return null;
        }
        CidInfo cidInfo;

        Map<String, Mail> recoverMailMap = null;
        List<SendTo> sendToList = null;
        if (op.equals(MailOp.SEND)) {
            sendToList = new ArrayList<>();
            cidInfo = apipClient.searchCidOrFid(br);
            if (cidInfo == null) return null;
            String to = cidInfo.getFid();
            Double amount;
            if (cidInfo.getNoticeFee() != null) {
                amount = Double.parseDouble(cidInfo.getNoticeFee());
                if (askIfYes(br,"He set a notice fee of " + amount + " on chain. Refuse to pay?")) {
                    return null;
                }
            } else {
                amount = Dust;
            }
            sendToList.add(new SendTo(to, amount));

            String msg = Inputer.inputString(br,"Input the message:");
            if (msg == null) return null;

            mailOpData.setTextId(IdNameTools.makeDid(msg));
//            mailData.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7.getDisplayName());
            Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
            String pubKey = cidInfo.getPubKey();
            if (pubKey == null) {
                System.out.println("Failed to get the pubKey of " + cidInfo.getFid());
                return null;
            }
            CryptoDataByte cryptoDataByte = encryptor.encryptByAsyTwoWay(msg.getBytes(), priKey, Hex.fromHex(pubKey));
            if (cryptoDataByte.getCode() == null || cryptoDataByte.getCode()!=0) {
                System.out.println("Failed to encrypt message: " + cryptoDataByte.getMessage());
                return null;
            }
            mailOpData.setCipher(cryptoDataByte.toJson());
        } else {
            if (mailIds == null){
                mailIds = new ArrayList<>();
                while(true){
                    mailId = findMailId(br);
                    if(mailId==null)break;
                    mailIds.add(mailId);
                }
            } else {
                recoverMailMap = apipClient.mailByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, mailIds.toArray(new String[0]));
                if (recoverMailMap == null || recoverMailMap.isEmpty()) {
                    System.out.println("The mails do not exist or are deleted.");
                    return null;
                }
                mailIds = new ArrayList<>();
                for(Mail mail:recoverMailMap.values()) {
                    mailIds.add(mail.getMailId());
                }
            }
            if (mailIds.isEmpty()) return null;
            mailOpData.setMailIds(mailIds);
        }

        Feip feip = getFeip();
        feip.setData(mailOpData);

        String opReturnStr = feip.toJson();
        long cd = Constants.CD_REQUIRED;

        if (askIfYes(br, "Are you sure to do below operation on chain?\n" + feip.toNiceJson() + "\n")) {
            String result = Wallet.carve(myFid,priKey, opReturnStr, cd, apipClient, cashHandler, br);
            if(result==null){
                System.out.println("Failed to " + op.toLowerCase() + " mail:" + apipClient.getFcClientEvent().getMessage());
                return null;
            }
            if (Hex.isHex32(result)) {
                System.out.println("The mail is " + op.toLowerCase() + "ed in TX  " + result + ".");
                if(op.equals(MailOp.DELETE)){
                    for(String mailId1:mailIds) {
                        mailDB.remove(Hex.fromHex(mailId1));
                    }
                }else if(op.equals(MailOp.RECOVER)){
                    if(recoverMailMap!=null && !recoverMailMap.isEmpty()) {
                        List<MailDetail> mailDetailList = mailToMailDetail(new ArrayList<>(recoverMailMap.values()));
                        if(mailDetailList!=null && !mailDetailList.isEmpty()) {
                            for(MailDetail mailDetail:mailDetailList) {
                                mailDB.put(mailDetail.getIdBytes(), mailDetail.toBytes());
                            }
                        }
                    }   
                    deleteInvalidMails(br);
                }
                return result;
            } else if(StringTools.isBase64(result)) {
                System.out.println("Sign the TX and broadcast it:\n"+result);
            }else {
                System.out.println("Failed to " + op.toLowerCase() + " mail:" + result);
            }
        }

        return null;
    }

    private List<Mail> loadAllMailList(Long lastHeight, Boolean active, final List<String> last, int size) {
        List<Mail> mailList = new ArrayList<>();
        List<Mail> subMailList;
        while (true) {
            subMailList = fetchMailList(lastHeight, active, last, size);
            if (subMailList == null || subMailList.isEmpty()) break;
            mailList.addAll(subMailList);
            if (subMailList.size() < size) break;
        }
        return mailList;
    }

    private List<Mail> fetchMailList(Long lastHeight, Boolean active, final List<String> last, int size) {
        List<Mail> subMailList = apipClient.freshMailSinceHeight(myFid, lastHeight, size, last, active);
        if (subMailList == null || subMailList.isEmpty()) return null;
        
        List<String> last1 = apipClient.getFcClientEvent().getResponseBody().getLast();
        if(last!=null && last1!=null && !last1.isEmpty()) {
            last.clear();
            last.addAll(last1);
        }
        return subMailList;
    }

    private List<MailDetail> mailToMailDetail(List<Mail> mailList) {
        if(failedDecryptMailIdList ==null) failedDecryptMailIdList = new ArrayList<>();
        List<MailDetail> mailDetailList = new ArrayList<>();
        byte[] priKey = Client.decryptPriKey(myPriKeyCipher, symKey);
        Set<String> fidSet = new HashSet<>();

        for (Mail mail : mailList) {
            fidSet.add(mail.getSender());
            fidSet.add(mail.getRecipient());
            if (mail.getRecipient() == null) mail.setRecipient(mail.getSender());
        }

        Map<String, String> fidCidMap = apipClient.getFidCidMap(new ArrayList<>(fidSet));
        if (fidCidMap != null)
            for (int i = mailList.size() - 1; i >= 0; i--) {
                Mail mail = mailList.get(i);
                MailDetail mailDetail = MailDetail.fromMail(myFid,mail, priKey,apipClient);
                if(mailDetail==null){
                    failedDecryptMailIdList.add(mail.getMailId());
                    continue;
                }
                String senderCid = fidCidMap.get(mail.getSender());
                String recipientCid = fidCidMap.get(mail.getRecipient());
                if (senderCid != null) mailDetail.setFromCid(senderCid);
                if (recipientCid != null) mailDetail.setToCid(recipientCid);
                byte[] key = mailDetail.getIdBytes();
                byte[] bytes = mailDetail.toBytes();
                if (key != null && bytes != null) {
                    mailDB.put(key, bytes);
                    mailDetailList.add(mailDetail);
                }
            }
        return mailDetailList;
    }

    private List<MailDetail> chooseMailDetails(BufferedReader br) {
        List<MailDetail> chosenMails = new ArrayList<>();
        byte[] lastId = null;
        int totalDisplayed = 0;

        while (true) {
            List<MailDetail> currentList = mailDB.getListFromEnd(lastId, DEFAULT_SIZE, (byte[] value) -> MailDetail.fromBytes(value));
            if (currentList.isEmpty()) {
                break;
            }

            String title = "Choose Mails";
            showMailDetailList(currentList, title, totalDisplayed);

            System.out.println("Enter mail numbers to select (comma-separated), 'q' to quit, or press Enter for more:");
            String input = Inputer.inputString(br);

            if ("".equals(input)) {
                totalDisplayed += currentList.size();
                lastId = currentList.get(currentList.size() - 1).getIdBytes();
                continue;
            }

            if (input.equals("q")) {
                break;
            } 

            String[] selections = input.split(",");
            for (String selection : selections) {
                try {
                    int index = Integer.parseInt(selection.trim()) - 1;
                    if (index >= 0 && index < totalDisplayed + currentList.size()) {
                        int listIndex = index - totalDisplayed;
                        chosenMails.add(currentList.get(listIndex));
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input: " + selection);
                }
            }

            totalDisplayed += currentList.size();
            lastId = currentList.get(currentList.size() - 1).getIdBytes();
        }

        return chosenMails;
    }

    private MailDetail chooseOneMailDetailFromList(List<MailDetail> mailDetailList, BufferedReader br) {
        if (mailDetailList.isEmpty()) return null;

        String title = "Chosen Mail";
        showMailDetailList(mailDetailList, title, 0);   

        System.out.println();
        int input = Inputer.inputInt(br, "Enter mail number to select it, Enter to quit:", mailDetailList.size());

        int index = input - 1;
        if (index >= 0 && index < mailDetailList.size()) {
            return mailDetailList.get(index);
        }
        return null;
    }

    private static void showMailDetailList(List<MailDetail> mailDetailList, String title, int totalDisplayed) {
        String[] fields = new String[]{"Time", "From", "To", "Content"};
        int[] widths = new int[]{12, 13, 13, 30};
        List<List<Object>> valueListList = new ArrayList<>();

        for (MailDetail mailDetail : mailDetailList) {
            List<Object> showList = new ArrayList<>();
            showList.add(DateTools.longToTime(mailDetail.getTime(), "yyyy-MM-dd"));
            String from;
            if (mailDetail.getFromCid() != null) from = mailDetail.getFromCid();
            else from = mailDetail.getFrom();
            showList.add(from);

            String to;
            if (mailDetail.getToCid() != null) to = mailDetail.getToCid();
            else to = mailDetail.getTo();
            showList.add(to);
            showList.add(mailDetail.getContent());
            valueListList.add(showList);
        }
        Shower.showDataTable(title, fields, widths, valueListList, totalDisplayed);
    }

    private static void showMailDetail(MailDetail mail) {
        Shower.printUnderline(20);
        System.out.println(" Mail ID: " + mail.getMailId());
        System.out.println(" Time: " + tools.DateTools.longToTime(mail.getTime(), "yyyy-MM-dd HH:mm:ss"));
        System.out.println(" From: " + (mail.getFromCid() != null ? mail.getFromCid() : mail.getFrom()));
        System.out.println(" To: " + (mail.getToCid() != null ? mail.getToCid() : mail.getTo()));
        System.out.println(" Content:\n  " + mail.getContent());
        Shower.printUnderline(20);
    }

    private List<MailDetail> choseFromMailDetailList(List<MailDetail> mailDetailList, BufferedReader br) {
        List<MailDetail> chosenMails = new ArrayList<>();
        
        while (true) {
            String title = "Choose Mails";
            showMailDetailList(mailDetailList, title, 0);

            System.out.println("Enter mail numbers to select (comma-separated), 'a' to select all, or 'q' to quit:");
            String input = Inputer.inputString(br);

            if ("".equalsIgnoreCase(input)) {
                break;
            }

            if ("a".equalsIgnoreCase(input)) {
                chosenMails.addAll(mailDetailList);
                break;
            }

            String[] selections = input.split(",");
            for (String selection : selections) {
                try {
                    int index = Integer.parseInt(selection.trim()) - 1;
                    if (index >= 0 && index < mailDetailList.size()) {
                        chosenMails.add(mailDetailList.get(index));
                    } else {
                        System.out.println("Invalid selection: " + (index + 1));
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input: " + selection);
                }
            }

            System.out.println("Selected " + chosenMails.size() + " mails. Continue selecting?");
            if (!askIfYes(br, "Continue selecting?")) {
                break;
            }
        }

        return chosenMails;
    }


    private static void chooseToShowOld(List<MailDetail> mailList, BufferedReader br) {
        if (mailList == null || mailList.isEmpty()) {
            System.out.println("No mails to display.");
            return;
        }
        while (true) {
            showMailDetailList(mailList, "View Mails", 0);

            System.out.println("Enter mail the numbers to select (comma-separated), 'a' to view all. 'q' to quit:");
            try {
                String input = br.readLine();
                if ("".equals(input)) continue;
                if ("q".equals(input)) return;
                if (input.contains(",")) {
                    String[] choices = input.replaceAll("\\s+", "").split(",");
                    for (String choice : choices) {
                        int choiceInt = Integer.parseInt(choice);
                        if (choiceInt < 1 || choiceInt > mailList.size()) {
                            System.out.println("Invalid choice. Please enter a number between 1 and " + mailList.size());
                            return;
                        }
                        showMailDetail(mailList.get(choiceInt - 1));
                    }
                } else if ("A".equalsIgnoreCase(input)) {
                    for (MailDetail mailDetail : mailList) {
                        showMailDetail(mailDetail);
                    }
                    Menu.anyKeyToContinue(br);
                } else {
                    int choice = Integer.parseInt(input);
                    if (choice < 1 || choice > mailList.size()) {
                        System.out.println("Invalid choice. Please enter a number between 1 and " + mailList.size());
                        return;
                    }
                    MailDetail chosenMail = mailList.get(choice - 1);
                    showMailDetail(chosenMail);
                    Menu.anyKeyToContinue(br);
                }
            } catch (IOException e) {
                System.out.println("Error reading input: " + e.getMessage());
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
            }
        }
    }

    private List<MailDetail> findMailDetails(String searchStr) {
        byte[] searchBytes;
        List<MailDetail> chosenMails;
        if (KeyTools.isValidFchAddr(searchStr)) searchBytes = KeyTools.addrToHash160(searchStr);
        else searchBytes = searchStr.getBytes();
        chosenMails = findMailDetails(searchBytes);
        return chosenMails;
    }

    private List<MailDetail> findMailDetails(byte[] searchBytes) {
        List<MailDetail> foundMails = new ArrayList<>();
        
        for (Map.Entry<byte[], byte[]> entry : mailDB.entrySet()) {
            if (BytesTools.contains(entry.getValue(), searchBytes)) {
                MailDetail mailDetail = MailDetail.fromBytes(entry.getValue());
                if (mailDetail != null) {
                    foundMails.add(mailDetail);
                }
            }
        }
        return foundMails;
    }

    // Getter Methods
    public Feip getFeip() {
        return Feip.fromProtocolName(Feip.ProtocolName.MAIL);
    }
}
