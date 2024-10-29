package clients.mailClient;

import appTools.Inputer;
import appTools.Menu;
import clients.Client;
import clients.apipClient.ApipClient;
import constants.Constants;
import constants.Strings;
import constants.UpStrings;
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
import feip.feipData.MailData;
import javaTools.DateTools;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.PersistentSequenceMap;
import javaTools.http.AuthType;
import javaTools.http.RequestMethod;
import javaTools.BytesTools;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import apip.apipData.CidInfo;
import appTools.Shower;

import org.jetbrains.annotations.Nullable;

import static constants.Constants.Dust;
import static constants.Constants.FEIP;
import static constants.OpNames.UPDATE;
import static constants.OpNames.READ;
import static constants.OpNames.DELETE;
import static constants.FieldNames.LAST_TIME;

public class MailClient {
    // Constants and Enums
    private static final Integer DEFAULT_SIZE = 3;

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
    private final String myFid;
    private final ApipClient apipClient;
    private final String sid;
    private final byte[] symKey;
    private final String myPriKeyCipher;
    private final PersistentSequenceMap mailFileMap;
    private final Map<String, Long> lastTimeMap;

    // Constructor
    public MailClient(String myFid, ApipClient apipClient, String sid, byte[] symKey, String myPriKeyCipher, Map<String, Long> lastTimeMap) {
        this.myFid = myFid;
        this.apipClient = apipClient;
        this.sid = sid;
        this.symKey = symKey;
        this.myPriKeyCipher = myPriKeyCipher;
        this.lastTimeMap = lastTimeMap;
        this.mailFileMap = new PersistentSequenceMap(sid, constants.Strings.MAIL);
    }

    // Public Methods
    public void menu(BufferedReader br) {
        Menu menu = new Menu("Mail Menu");
        menu.add(READ, () -> readMails(br));
        menu.add(UPDATE, () -> checkMail(br));
        menu.add(MailOp.SEND.toLowerCase(), () -> sendMails(br));
        menu.add(MailOp.DELETE.toLowerCase(), () -> choseToDelete(br));
        menu.add(MailOp.RECOVER.toLowerCase(), () -> recoverMails(br));
        menu.showAndSelect(br);
    }

    public String sendMail(BufferedReader br) {
        return opMail(null, br, MailOp.SEND);
    }

    public void sendMails(BufferedReader br) {
        while (true) {
            String result = sendMail(br);
            if (Hex.isHex32(result)) {
                System.out.println("Mail sent successfully: " + result);
            } else {
                System.out.println("Failed to send mail: " + result);
            }
            
            if (!Inputer.askIfYes(br, "Do you want to send another mail?")) {
                break;
            }
        }
    }

    public String deleteMail(String mailId, BufferedReader br) {
        MailOp op = MailOp.DELETE;
        return opMail(mailId, br, op);
    }

    public void recoverMails(BufferedReader br) {
        List<String> last = new ArrayList<>();
        List<String> chosenMailIds = new ArrayList<>();
        
        while (true) {
            List<Mail> currentBatch = fetchMailList(0L, false, last, DEFAULT_SIZE);
            if (currentBatch==null || currentBatch.isEmpty()) break;
            
            List<MailDetail> currentMailDetailList = mailToMailDetail(currentBatch);

            System.out.println("Loaded " + currentMailDetailList.size() + " deleted mails.");
            if (!Inputer.askIfYes(br,"View or choose them?")) break;
            
            List<MailDetail> chosenMailDetails = choseFromMailDetailList(currentMailDetailList, br);

            System.out.println("You chosen " + chosenMailDetails.size() + " mails.");

            if (Inputer.askIfYes(br,"View them?")) chooseToShow(chosenMailDetails, br);

            List<String> currentChosenMailIds = chosenMailDetails.stream().map(MailDetail::getMailId).collect(Collectors.toList());
            chosenMailIds.addAll(currentChosenMailIds);
            
            if (!Inputer.askIfYes(br,"Load more mails?")) break;
        }
        
        if (chosenMailIds.isEmpty()) {
            System.out.println("No deleted mails chosen.");
            return;
        }
        
        if (Inputer.askIfYes(br,"Recover " + chosenMailIds.size() + " mails?")) {
            for (String mailId : chosenMailIds) {
                recoverMail(mailId, br);
            }
        }
    }

    public String recoverMail(String mailId, BufferedReader br) {
        MailOp op = MailOp.RECOVER;
        return opMail(mailId, br, op);
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
        Long lastTime = lastTimeMap.get(Strings.MAIL);
        if (lastTime == null) lastTime = 0L;
        mailList = loadAllMailList(lastTime, true, last, DEFAULT_SIZE);
        List<MailDetail> mailDetailList = new ArrayList<>();

        if (!mailList.isEmpty()) {
            mailDetailList = mailToMailDetail(mailList);
        }

        System.out.println("You have " + mailDetailList.size() + " unread mails.");
        if (mailDetailList.size() > 0) chooseToShow(mailDetailList, br);
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
        
    
        if (Inputer.askIfYes(br, "View them before delete?")) {
            chooseToShow(chosenMails, br);
        }
        
        if (Inputer.askIfYes(br, "Delete " + chosenMails.size() + " mails?")) {
            for (MailDetail mail : chosenMails) {
                String mailId = mail.getMailId();
                String result = deleteMail(mailId, br);
                if (Hex.isHex32(result)) {    
                    System.out.println("Deleted mail " + mailId + ": " + result);
                } else {
                    System.out.println("Failed to delete mail " + mailId + ": " + result);
                }
            }
        }
    }

    // Private Methods
    private String opMail(String mailId, BufferedReader br, MailOp op) {
        if (op == null) return null;

        MailData mailData = new MailData();
        mailData.setOp(op.toLowerCase());
        byte[] priKey = Client.decryptPriKey(myPriKeyCipher, symKey);
        if (priKey == null) {
            System.out.println("Failed to get the priKey of " + myFid);
            return null;
        }
        CidInfo cidInfo;

        List<SendTo> sendToList = null;
        if (op.equals(MailOp.SEND)) {
            sendToList = new ArrayList<>();
            cidInfo = apipClient.searchCidOrFid(br);
            if (cidInfo == null) return null;
            String to = cidInfo.getFid();
            Double amount;
            if (cidInfo.getNoticeFee() != null) {
                amount = Double.parseDouble(cidInfo.getNoticeFee());
                if (Inputer.askIfYes(br,"He set a notice fee of " + amount + " on chain. Refuse to pay?")) {
                    return null;
                }
            } else {
                amount = Dust;
            }
            sendToList.add(new SendTo(to, amount));

            String msg = Inputer.inputString(br,"Input the message:");
            if (msg == null) return null;

            mailData.setTextId(IdNameTools.makeDid(msg));
            mailData.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7.getDisplayName());
            Encryptor encryptor = new Encryptor(AlgorithmId.fromDisplayName(mailData.getAlg()));
            String pubKey = cidInfo.getPubKey();
            if (pubKey == null) {
                System.out.println("Failed to get the pubKey of " + cidInfo.getFid());
                return null;
            }
            CryptoDataByte cryptoDataByte = encryptor.encryptByAsyTwoWay(msg.getBytes(), priKey, Hex.fromHex(pubKey));
            if (cryptoDataByte.getCode() != null) {
                System.out.println("Failed to encrypt message: " + cryptoDataByte.getMessage());
                return null;
            }
            mailData.setMsg(cryptoDataByte.toJson());
        } else {
            if (mailId == null) mailId = findMailId(br);
            else {
                Map<String, Mail> mailMap = apipClient.mailByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, mailId);
                if (mailMap == null || mailMap.isEmpty() || mailMap.get(mailId) == null) {
                    System.out.println("The mail do not exist or is deleted.");
                    mailFileMap.remove(Hex.fromHex(mailId));
                    return null;
                }
            }
            if (mailId == null) return null;
            mailData.setMailId(mailId);
        }

        Feip feip = getFeip();
        feip.setData(mailData);

        String opReturnStr = feip.toJson();
        long cd = Constants.CD_REQUIRED;
        int maxCashes = Wallet.MAX_CASHE_SIZE;

        if (Inputer.askIfYes(br, "Are you sure to do below operation on chain?\n" + feip.toNiceJson() + "\n")) {
            String txSigned = Wallet.makeTx(br, priKey, null, sendToList, opReturnStr, cd, maxCashes, apipClient, null);
            if (txSigned == null) {
                System.out.println("Failed to make tx.");
                return null;
            }
            String txId = apipClient.broadcastTx(txSigned, RequestMethod.POST, AuthType.FC_SIGN_BODY);
            if (Hex.isHex32(txId)) {
                System.out.println("The mail is " + op.toLowerCase() + "ed: " + txId + ".");
                return txId;
            } else {
                System.out.println("Failed to " + op.toLowerCase() + " mail:" + txId);
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
        List<Mail> subMailList = apipClient.freshMailSinceTime(myFid, lastHeight, size, last, active);
        if (subMailList == null || subMailList.isEmpty()) return null;
        
        List<String> last1 = apipClient.getFcClientEvent().getResponseBody().getLast();
        if(last!=null && last1!=null && !last1.isEmpty()) {
            last.clear();
            last.addAll(last1);
        }
        return subMailList;
    }

    private List<MailDetail> mailToMailDetail(List<Mail> mailList) {
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
                MailDetail mailDetail = MailDetail.fromMail(mail, priKey);
                String senderCid = fidCidMap.get(mail.getSender());
                String recipientCid = fidCidMap.get(mail.getRecipient());
                if (senderCid != null) mailDetail.setFromCid(senderCid);
                if (recipientCid != null) mailDetail.setToCid(recipientCid);
                byte[] key = mailDetail.getIdBytes();
                byte[] bytes = mailDetail.toBytes();
                if (key != null && bytes != null) {
                    mailFileMap.put(key, bytes);
                    mailDetailList.add(mailDetail);
                }
            }

        if (!mailList.isEmpty()) {
            lastTimeMap.put(constants.Strings.MAIL, mailList.get(0).getLastHeight());
            JsonTools.saveToJsonFile(lastTimeMap, null, sid, LAST_TIME, false);
        }
        return mailDetailList;
    }

    private List<MailDetail> chooseMailDetails(BufferedReader br) {
        List<MailDetail> chosenMails = new ArrayList<>();
        byte[] lastId = null;
        int totalDisplayed = 0;

        while (true) {
            List<MailDetail> currentList = mailFileMap.getListFromEnd(lastId, DEFAULT_SIZE);
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
        int input = Inputer.inputInteger(br, "Enter mail number to select it, Enter to quit:", mailDetailList.size());

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
        System.out.println(" Time: " + javaTools.DateTools.longToTime(mail.getTime(), "yyyy-MM-dd HH:mm:ss"));
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

            System.out.println("Enter mail numbers to select (comma-separated), 'a' to select all, or 'q' to finish:");
            String input = Inputer.inputString(br);

            if ("q".equalsIgnoreCase(input)) {
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
            if (!Inputer.askIfYes(br, "Continue selecting?")) {
                break;
            }
        }

        return chosenMails;
    }

    private static void chooseToShow(List<MailDetail> mailList, BufferedReader br) {
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
        
        for (Map.Entry<byte[], byte[]> entry : mailFileMap.entrySet()) {
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
        return new Feip(FEIP, "7", "4", UpStrings.MAIL);
    }
}
