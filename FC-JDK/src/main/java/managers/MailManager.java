package managers;

import core.crypto.Decryptor;
import data.fchData.Cash;
import data.fchData.Freer;
import data.feipData.*;
import ui.Inputer;
import ui.Menu;
import config.Settings;
import ui.Shower;
import clients.ApipClient;
import constants.Constants;
import core.crypto.CryptoDataByte;
import core.crypto.Encryptor;
import core.crypto.KeyTools;
import data.fcData.AlgorithmId;
import data.fcData.Op;
import core.fch.Wallet;
import org.jetbrains.annotations.Nullable;
import utils.*;
import utils.http.AuthType;
import utils.http.RequestMethod;
import db.LocalDB;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static ui.Inputer.askIfYes;
import static constants.Constants.Dust;
import static constants.OpNames.*;

public class MailManager extends Manager<Mail> {
    // Constants and Enums
    private static final Integer DEFAULT_SIZE = 50;
    private static final String LAST_HEIGHT = "last_height";

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
    private final CashManager cashHandler;
    private final byte[] symkey;
    private final String myPrikeyCipher;
    private List<String> failedDecryptMailIdList;

    // Constructor

    public MailManager(Settings settings) {
        super(settings, ManagerType.MAIL, LocalDB.SortType.BIRTH_ORDER, Mail.class, true, true);
        this.apipClient = (ApipClient) settings.getClient(ServiceType.APIP);
        this.cashHandler = (CashManager) settings.getManager(ManagerType.CASH);
        this.myFid = settings.getMainFid();
        this.symkey = settings.getSymkey();
        this.myPrikeyCipher = settings.getMyPrikeyCipher();
        this.br = settings.getBr();
    }

    // Public Methods
    public void menu(BufferedReader br, boolean isRootMenu) {
        Menu menu = newMenu("Mail",isRootMenu);
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
            
            List<Mail> currentMailList = mailToMailDetail(currentBatch);

            System.out.println("Loaded " + currentMailList.size() + " deleted mails.");
            if (!askIfYes(br,"View or choose them?")) break;
            
            List<Mail> chosenMails = choseFromMailDetailList(currentMailList, br);

            System.out.println("You chosen " + chosenMails.size() + " mails.");

            if (askIfYes(br,"View them?")) chooseToShowNiceJsonList(chosenMails, br);

            List<String> currentChosenMailIds = chosenMails.stream().map(Mail::getMailId).collect(Collectors.toList());
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
            Mail mail = findMail(br);
            if (mail == null) continue;
            mailId = mail.getMailId();
            break;
        }
        return mailId;
    }

    @Nullable
    public Mail findMail(BufferedReader br) {
        String input = Inputer.inputString(br,"Input the FID, CID or part of the content:");
        List<Mail> list = findMailDetails(input);
        Mail mail = chooseOneMailDetailFromList(list, br);
        if (mail == null) return null;
        return mail;
    }

    public void checkMail(BufferedReader br) {
        List<String> last = null;
        List<Mail> mailList = null;
        Long lastHeight = getLastHeight();
        mailList = loadAllMailList(lastHeight, true, last, DEFAULT_SIZE);

        List<Mail> mailDetailList = new ArrayList<>();

        if (!mailList.isEmpty()) {
            mailDetailList = mailToMailDetail(mailList);
            setLastHeight(mailList.get(0).getLastHeight());
            deleteInvalidMails(br);
        }

        System.out.println("You have " + mailDetailList.size() + " unread mails.");
        if (mailDetailList.size() > 0) chooseToShowNiceJsonList(mailDetailList, br);
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
        List<Mail> recentMails = chooseMailDetails(br);
        System.out.println("You chosen " + recentMails.size() + " mails. Read them...");
        chooseToShowNiceJsonList(recentMails, br);
    }

    public void readMails(BufferedReader br) {
        List<Mail> chosenMails = null;
        
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
                List<Mail> foundMailList = findMailDetails(input);
                chosenMails = choseFromMailDetailList(foundMailList,br);
            }

            System.out.println("You chosen " + chosenMails.size() + " mails.");

            String op = Inputer.chooseOne(
                    new String[]{READ,MailOp.DELETE.toLowerCase()},
                    null,"Select to operate the mails:",br);

            switch (op) {
                case READ:
                    chooseToShowNiceJsonList(chosenMails, br);
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
        List<Mail> chosenMails = chooseMailDetails(br);
        
        delete(br, chosenMails);
    }

    private void delete(BufferedReader br, List<Mail> chosenMails) {
        if (chosenMails.isEmpty()) {
            System.out.println("No mails chosen for deletion.");
            return;
        }
        
    
        if (askIfYes(br, "View them before delete?")) {
            chooseToShowNiceJsonList(chosenMails, br);
        }
        
        List<String> mailIds = chosenMails.stream().map(Mail::getMailId).collect(Collectors.toList());
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
        byte[] prikey = Decryptor.decryptPrikey(myPrikeyCipher, symkey);
        if (prikey == null) {
            System.out.println("Failed to get the prikey of " + myFid);
            return null;
        }
        Freer cid;

        Map<String, Mail> recoverMailMap = null;
        List<Cash> sendToList = null;
        if (op.equals(MailOp.SEND)) {
            sendToList = new ArrayList<>();
            cid = apipClient.searchCidOrFid(br);
            if (cid == null) return null;
            String to = cid.getId();
            Double amount;
            if (cid.getNoticeFee() != null) {
                amount = Double.parseDouble(cid.getNoticeFee());
                if (askIfYes(br,"He set a notice fee of " + amount + " on chain. Refuse to pay?")) {
                    return null;
                }
            } else {
                amount = Dust;
            }
            sendToList.add(new Cash(to, amount));

            String msg = Inputer.inputString(br,"Input the message:");
            if (msg == null) return null;

            Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
            String pubkey = cid.getPubkey();
            if (pubkey == null) {
                System.out.println("Failed to get the pubkey of " + cid.getId());
                return null;
            }
            CryptoDataByte cryptoDataByte = encryptor.encryptByAsyTwoWay(msg.getBytes(), prikey, Hex.fromHex(pubkey));
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
                recoverMailMap = apipClient.mailByIds(RequestMethod.POST, AuthType.ENCRYPTED, mailIds.toArray(new String[0]));
                if (recoverMailMap == null || recoverMailMap.isEmpty()) {
                    System.out.println("The mails do not exist or are deleted.");
                    return null;
                }
                mailIds = new ArrayList<>();
                for(Mail mail:recoverMailMap.values()) {
                    mailIds.add(mail.getId());
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
            String result = Wallet.carve(myFid,prikey, opReturnStr, cd, apipClient, cashHandler, br);
            if(result==null){
                System.out.println("Failed to " + op.toLowerCase() + " mail:" + apipClient.getFcClientEvent().getMessage());
                return null;
            }
            if (Hex.isHex32(result)) {
                System.out.println("The mail is " + op.toLowerCase() + "ed in TX  " + result + ".");
                if(op.equals(MailOp.DELETE)){
                    for(String mailId1:mailIds) {
                        remove(mailId1);
                    }
                }else if(op.equals(MailOp.RECOVER)){
                    if(recoverMailMap!=null && !recoverMailMap.isEmpty()) {
                        List<Mail> mailList = mailToMailDetail(new ArrayList<>(recoverMailMap.values()));
                        if(mailList !=null && !mailList.isEmpty()) {
                            for(Mail mail : mailList) {
                                put(mail.getId(), mail);
                            }
                        }
                    }   
                    deleteInvalidMails(br);
                }
                return result;
            } else if(StringUtils.isBase64(result)) {
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

    private List<Mail> mailToMailDetail(List<Mail> mailList) {
        byte[] prikey = Decryptor.decryptPrikey(myPrikeyCipher, symkey);

        if (mailList != null && !mailList.isEmpty())
            for (int i = mailList.size() - 1; i >= 0; i--) {
                Mail mail = mailList.get(i);
                mail.parseDetail(myFid, mail, prikey, apipClient);
            }
        return mailList;
    }

    private List<Mail> chooseMailDetails(BufferedReader br) {
        return chooseItems(br);
    }

    private Mail chooseOneMailDetailFromList(List<Mail> mailList, BufferedReader br) {
        if (mailList.isEmpty()) return null;

        String title = "Chosen Mail";
        showMailDetailList(mailList, title, 0);

        System.out.println();
        int input = Inputer.inputInt(br, "Enter mail number to select it, Enter to quit:", mailList.size());

        int index = input - 1;
        if (index >= 0 && index < mailList.size()) {
            return mailList.get(index);
        }
        return null;
    }

    private static void showMailDetailList(List<Mail> mailList, String title, int totalDisplayed) {
        String[] fields = new String[]{"Time", "From", "To", "Content"};
        int[] widths = new int[]{12, 13, 13, 30};
        List<List<Object>> valueListList = new ArrayList<>();

        for (Mail mail : mailList) {
            List<Object> showList = new ArrayList<>();
            showList.add(DateUtils.longToTime(mail.getTime(), "yyyy-MM-dd"));
            String from;
            if (mail.getFromCid() != null)
                from = mail.getFromCid();
            else from = mail.getFrom();

            showList.add(from);

            String to;
            if (mail.getToCid() != null) to = mail.getToCid();
            else to = mail.getTo();
            showList.add(to);
            showList.add(mail.getContent());
            valueListList.add(showList);
        }
        Shower.showOrChooseList(title, fields, widths, valueListList, null);
    }

    private static void showMailDetail(Mail mail) {
        Shower.printUnderline(20);
        System.out.println(" Mail ID: " + mail.getMailId());
        System.out.println(" Time: " + DateUtils.longToTime(mail.getTime(), "yyyy-MM-dd HH:mm:ss"));
        System.out.println(" From: " + (mail.getFromCid() != null ? mail.getFromCid() : mail.getFrom()));
        System.out.println(" To: " + (mail.getToCid() != null ? mail.getToCid() : mail.getTo()));
        System.out.println(" Content:\n  " + mail.getContent());
        Shower.printUnderline(20);
    }

    private List<Mail> choseFromMailDetailList(List<Mail> mailList, BufferedReader br) {
        List<Mail> chosenMails = new ArrayList<>();
        
        while (true) {
            String title = "Choose Mails";
            showMailDetailList(mailList, title, 0);

            System.out.println("Enter mail numbers to select (comma-separated), 'a' to select all, or 'q' to quit:");
            String input = Inputer.inputString(br);

            if ("".equalsIgnoreCase(input)) {
                break;
            }

            if ("a".equalsIgnoreCase(input)) {
                chosenMails.addAll(mailList);
                break;
            }

            String[] selections = input.split(",");
            for (String selection : selections) {
                try {
                    int index = Integer.parseInt(selection.trim()) - 1;
                    if (index >= 0 && index < mailList.size()) {
                        chosenMails.add(mailList.get(index));
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


    private static void chooseToShowOld(List<Mail> mailList, BufferedReader br) {
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
                    for (Mail mail : mailList) {
                        showMailDetail(mail);
                    }
                    Menu.anyKeyToContinue(br);
                } else {
                    int choice = Integer.parseInt(input);
                    if (choice < 1 || choice > mailList.size()) {
                        System.out.println("Invalid choice. Please enter a number between 1 and " + mailList.size());
                        return;
                    }
                    Mail chosenMail = mailList.get(choice - 1);
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

    private List<Mail> findMailDetails(String searchStr) {
        byte[] searchBytes;
        if (KeyTools.isGoodFid(searchStr)) searchBytes = KeyTools.addrToHash160(searchStr);
        else searchBytes = searchStr.getBytes();
        return findMailDetails(searchBytes);
    }

    private List<Mail> findMailDetails(byte[] searchBytes) {
        return searchInValue(new String(searchBytes));
    }

    // Getter Methods
    public Feip getFeip() {
        return Feip.fromProtocolName(Feip.FeipProtocol.MAIL);
    }

    private Long getLastHeight() {
        return getLongState(LAST_HEIGHT);
    }

    private void setLastHeight(Long height) {
        if (localDB != null) {
            localDB.putState(LAST_HEIGHT, height);
        }
    }

    private void put(String id, Mail mail) {
        if (localDB == null) return;
        try {
            localDB.put(id, mail);
        } catch (Exception e) {
            log.error("Failed to put mail detail: {}", e.getMessage());
        }
    }
}
