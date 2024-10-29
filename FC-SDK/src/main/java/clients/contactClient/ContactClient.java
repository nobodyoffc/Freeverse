package clients.contactClient;

import appTools.Inputer;
import appTools.Menu;
import clients.Client;
import clients.apipClient.ApipClient;
import com.google.gson.Gson;
import constants.Constants;
import constants.Strings;
import constants.UpStrings;
import crypto.CryptoDataByte;
import crypto.Encryptor;
import crypto.KeyTools;
import fcData.AlgorithmId;
import fcData.ContactDetail;
import fcData.Op;
import fch.Wallet;
import feip.feipData.Contact;
import feip.feipData.ContactData;
import feip.feipData.Feip;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.JsonTools;
import javaTools.PersistentKeySortedMap;
import javaTools.http.AuthType;
import javaTools.http.RequestMethod;

import java.io.BufferedReader;
import java.util.*;

import apip.apipData.CidInfo;
import appTools.Shower;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static appTools.Inputer.askIfYes;
import static constants.Constants.FEIP;
import static constants.FieldNames.ADDR_OR_PUB_KEY;
import static constants.OpNames.*;
import static constants.FieldNames.LAST_TIME;

import java.io.IOException;

public class ContactClient {
    protected static final Logger log = LoggerFactory.getLogger(ContactClient.class);
    // Constants and Enums
    private static final Integer DEFAULT_SIZE = 10;
    private List<String> fakeContactDetailList = new ArrayList<>();

    public enum ContactOp {
        ADD(Op.ADD),
        UPDATE(Op.UPDATE),
        DELETE(Op.DELETE);

        public final Op op;
        ContactOp(Op op) { this.op = op; }

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
    private final byte[] myPubKey;
    private final PersistentKeySortedMap contactFileMap;
    private final Map<String, Long> lastTimeMap;

    // Constructor
    public ContactClient(String myFid, ApipClient apipClient, String sid, byte[] symKey, String myPriKeyCipher, Map<String, Long> lastTimeMap) {
        this.myFid = myFid;
        this.apipClient = apipClient;
        this.sid = sid;
        this.symKey = symKey;
        this.myPriKeyCipher = myPriKeyCipher;
        this.lastTimeMap = lastTimeMap;
        this.contactFileMap = new PersistentKeySortedMap(sid, constants.Strings.CONTACT);
        this.myPubKey = KeyTools.priKeyToPubKey(Client.decryptPriKey(myPriKeyCipher,symKey));
    }

    // Public Methods
    public void menu(BufferedReader br) {
        Menu menu = new Menu("Contact Menu");
        menu.add(READ, () -> readContacts(br));
        menu.add(UPDATE, () -> checkContacts(br));
        menu.add(ContactOp.ADD.toLowerCase(), () -> addContacts(br));
        menu.add(ContactOp.UPDATE.toLowerCase(), () -> updateContacts(br));
        menu.add(ContactOp.DELETE.toLowerCase(), () -> deleteContacts(br));
        menu.showAndSelect(br);
    }

    public String  addContact(BufferedReader br) {
        return opContact(null, br, ContactOp.ADD);
    }

    public void addContacts(BufferedReader br) {
        while (true) {
            String result = addContact(br);
            if (Hex.isHex32(result)) {
                System.out.println("Contact added successfully: " + result);
            } else {
                System.out.println("Failed to add contact: " + result);
            }
            
            if (!askIfYes(br, "Do you want to add another contact?")) {
                break;
            }
        }
    }

    public String updateContact(String contactId, BufferedReader br) {
        return opContact(contactId, br, ContactOp.UPDATE);
    }

    public void updateContacts(BufferedReader br) {
        List<ContactDetail> chosenContacts = chooseContactDetails(br);
        for (ContactDetail contact : chosenContacts) {
            String result = updateContact(contact.getFid() != null ? contact.getFid() : contact.getCid(), br);
            if (Hex.isHex32(result)) {
                System.out.println("Updated contact " + contact.getFid() + ": " + result);
            } else {
                System.out.println("Failed to update contact " + contact.getFid() + ": " + result);
            }
        }
    }

    public String deleteContact(String contactId, BufferedReader br) {
        return opContact(contactId, br, ContactOp.DELETE);
    }

    public void deleteContacts(BufferedReader br) {
        List<ContactDetail> chosenContacts = chooseContactDetails(br);
        if (chosenContacts.isEmpty()) {
            System.out.println("No contacts chosen for deletion.");
            return;
        }
        
        if (askIfYes(br, "View them before delete?")) {
            chooseToShow(chosenContacts, br);
        }
        
        if (askIfYes(br, "Delete " + chosenContacts.size() + " contacts?")) {
            for (ContactDetail contact : chosenContacts) {
                String contactId = contact.getFid() != null ? contact.getFid() : contact.getCid();
                String result = deleteContact(contactId, br);
                if (Hex.isHex32(result)) {    
                    System.out.println("Deleted contact " + contactId + ": " + result);
                } else {
                    System.out.println("Failed to delete contact " + contactId + ": " + result);
                }
            }
        }
    }

    public void checkContacts(BufferedReader br) {
        Long lastTime =null;
        if(lastTimeMap!=null)lastTime = lastTimeMap.get(Strings.CONTACT);
        if (lastTime == null) lastTime = 0L;
        List<Contact> contactList = loadAllContactList(lastTime, true);
        List<ContactDetail> contactDetailList = contactToContactDetail(contactList);

        if(!fakeContactDetailList.isEmpty() && askIfYes(br,"Got "+fakeContactDetailList.size()+" unreadable contacts. Delete them?")){

        }

        if (!contactList.isEmpty()) {
            lastTimeMap.put(constants.Strings.CONTACT, contactList.get(0).getBirthTime());
            JsonTools.saveToJsonFile(lastTimeMap, myFid, sid, LAST_TIME, false);
        }

        System.out.println("You have " + contactDetailList.size() + " updated contacts.");
        if (contactDetailList.size() > 0) chooseToShow(contactDetailList, br);
    }

    public void readContacts(BufferedReader br) {
        List<ContactDetail> chosenContacts = null;
        
        String input;
        while (true) {
            input = Inputer.inputString(br, "Input FID, CID or search string. 'q' to quit. Enter to list all contacts and choose some:");
            if ("q".equals(input)) return;
            if ("".equals(input)) {
                chosenContacts = chooseContactDetails(br);
                if (chosenContacts.isEmpty()) {
                    return;
                }
            } else {
                List<ContactDetail> foundContactDetailList = findContactDetails(input);
                chosenContacts = choseFromContactDetailList(foundContactDetailList, br);
            }

            System.out.println("You chosen " + chosenContacts.size() + " contacts.");

            String op = Inputer.chooseOne(
                    new String[]{READ, ContactOp.UPDATE.toLowerCase(), ContactOp.DELETE.toLowerCase()},
                    null, "Select to operate the contacts:", br);

            switch (op) {
                case READ:
                    chooseToShow(chosenContacts, br);
                    break;
                case UPDATE:
                    updateContacts(br);
                    break;
                case DELETE:
                    deleteContacts(br);
                    break;
                default:
                    break;
            }
        }
    }

    // Private Methods
    private String opContact(String contactId, BufferedReader br, ContactOp op) {
        if (op == null) return null;
        ContactData contactData = new ContactData();
        contactData.setOp(op.toLowerCase());
        ContactDetail contactDetail = new ContactDetail();
        byte[] priKey = Client.decryptPriKey(myPriKeyCipher, symKey);
        if (priKey == null) {
            System.out.println("Failed to get the priKey of " + myFid);
            return null;
        }

        if (op.equals(ContactOp.ADD) || op.equals(ContactOp.UPDATE)) {
            String fid = apipClient.chooseFid(br);
            if (fid == null) return null;
            contactDetail.setFid(fid);
            contactDetail.setMemo(Inputer.inputString(br, "Input memo:"));
            contactDetail.setSeeStatement(askIfYes(br, "See its statements?"));
            contactDetail.setSeeWritings(askIfYes(br, "See its writings?"));

            Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
            CryptoDataByte cryptoDataByte = encryptor.encryptByAsyOneWay(JsonTools.toJson(contactDetail).getBytes(),myPubKey);
            if(cryptoDataByte.getCode()!=0){
                log.error("Failed to encrypt.");
                return null;
            }
            contactData.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7.getDisplayName());
            if(op.equals(ContactOp.UPDATE))contactData.setContactId(contactId);
            byte[] b = cryptoDataByte.toBundle();
            String cipher = Base64.getEncoder().encodeToString(b);
            contactData.setCipher(cipher);
        } else if (op.equals(ContactOp.DELETE)) {
            if (contactId == null) {
                ContactDetail foundContact = findContact(br);
                if (foundContact == null) return null;
                contactFileMap.remove(foundContact.getFid().getBytes());
                contactFileMap.remove(foundContact.getCid().getBytes());
            }
            contactData.setContactId(contactId);
        }

        Feip feip = getFeip();
        feip.setData(contactData);

        String opReturnStr = feip.toJson();
        long cd = Constants.CD_REQUIRED;
        int maxCashes = Wallet.MAX_CASHE_SIZE;

        if (askIfYes(br, "Are you sure to do below operation on chain?\n" + feip.toNiceJson() + "\n")) {
            String txSigned = Wallet.makeTx(br,priKey, null, null, opReturnStr, cd, maxCashes, apipClient, null);
            if (txSigned == null) {
                System.out.println("Failed to make tx.");
                return null;
            }
            String txId = apipClient.broadcastTx(txSigned, RequestMethod.POST, AuthType.FC_SIGN_BODY);
            if (Hex.isHex32(txId)) {
                System.out.println("The contact is " + op.toLowerCase() + "ed: " + txId + ".\n Wait a few minutes for confirmations before updating contacts...");
                return txId;
            } else {
                System.out.println("Failed to " + op.toLowerCase() + " contact:" + txId);
            }
        }
        return null;
    }

    private List<Contact> loadAllContactList(Long lastHeight, Boolean active) {
        List<Contact> contactList = new ArrayList<>();
        List<String> last = new ArrayList<>();
        while (true) {
            List<Contact> subContactList = apipClient.freshContactSinceTime(myFid, lastHeight, DEFAULT_SIZE, last, active);
            if (subContactList == null || subContactList.isEmpty()) break;
            contactList.addAll(subContactList);
            if (subContactList.size() < DEFAULT_SIZE) break;
        }
        return contactList;
    }

    private List<ContactDetail> contactToContactDetail(List<Contact> contactList) {
        List<ContactDetail> contactDetailList = new ArrayList<>();
        byte[] priKey = Client.decryptPriKey(myPriKeyCipher,symKey);
        for (Contact contact : contactList) {
            ContactDetail contactDetail = ContactDetail.fromContact(contact,priKey,apipClient);
            if(contactDetail==null){
                fakeContactDetailList.add(contact.getContactId());
                continue;
            }
            String key = contactDetail.getFid() != null ? contactDetail.getFid() : contactDetail.getCid();
            contactFileMap.put(key.getBytes(), contactDetail.toBytes());
            contactDetailList.add(contactDetail);
        }
        BytesTools.clearByteArray(priKey);

        return contactDetailList;
    }

    private List<ContactDetail> chooseContactDetails(BufferedReader br) {
        List<ContactDetail> chosenContacts = new ArrayList<>();
        byte[] lastKey = null;
        int totalDisplayed = 0;

        while (true) {
            List<ContactDetail> currentList = contactFileMap.getListFromEnd(lastKey, DEFAULT_SIZE, ContactDetail::fromBytes);
            if (currentList.isEmpty()) {
                break;
            }

            String title = "Choose Contacts";
            showContactDetailList(currentList, title, totalDisplayed);

            System.out.println("Enter contact numbers to select (comma-separated), 'q' to quit, or press Enter for more:");
            String input = Inputer.inputString(br);

            if ("".equals(input)) {
                totalDisplayed += currentList.size();
                lastKey = currentList.get(currentList.size() - 1).getFid() != null ? 
                    currentList.get(currentList.size() - 1).getFid().getBytes() : 
                    currentList.get(currentList.size() - 1).getCid().getBytes();
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
                        chosenContacts.add(currentList.get(listIndex));
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input: " + selection);
                }
            }

            totalDisplayed += currentList.size();
            lastKey = currentList.get(currentList.size() - 1).getFid() != null ? 
                currentList.get(currentList.size() - 1).getFid().getBytes() : 
                currentList.get(currentList.size() - 1).getCid().getBytes();
        }

        return chosenContacts;
    }

    private static void showContactDetailList(List<ContactDetail> contactDetailList, String title, int totalDisplayed) {
        String[] fields = new String[]{"CID", "FID", "Memo", "NoticeFee", "SeeStatement", "SeeWritings"};
        int[] widths = new int[]{13, 13, 20, 10, 12, 11};
        List<List<Object>> valueListList = new ArrayList<>();

        for (int i = 0; i < contactDetailList.size(); i++) {
            ContactDetail contactDetail = contactDetailList.get(i);
            List<Object> showList = new ArrayList<>();
            showList.add(contactDetail.getCid());
            showList.add(contactDetail.getFid());
            showList.add(contactDetail.getMemo());
            showList.add(contactDetail.getNoticeFee());
            showList.add(contactDetail.getSeeStatement());
            showList.add(contactDetail.getSeeWritings());
            valueListList.add(showList);
        }
        Shower.showDataTable(title, fields, widths, valueListList, totalDisplayed);
    }

    private static void showContactDetail(ContactDetail contact) {
        Shower.printUnderline(20);
        System.out.println(" CID: " + contact.getCid());
        System.out.println(" FID: " + contact.getFid());
        System.out.println(" Memo: " + contact.getMemo());
        System.out.println(" Notice Fee: " + contact.getNoticeFee());
        System.out.println(" See Statement: " + contact.getSeeStatement());
        System.out.println(" See Writings: " + contact.getSeeWritings());
        System.out.println(" Update Height: " + contact.getUpdateHeight());
        Shower.printUnderline(20);
    }

    private List<ContactDetail> choseFromContactDetailList(List<ContactDetail> contactDetailList, BufferedReader br) {
        List<ContactDetail> chosenContacts = new ArrayList<>();
        
        while (true) {
            String title = "Choose Contacts";
            showContactDetailList(contactDetailList, title, 0);

            System.out.println("Enter contact numbers to select (comma-separated), 'a' to select all, or 'q' to finish:");
            String input = Inputer.inputString(br);

            if ("q".equalsIgnoreCase(input)) {
                break;
            }

            if ("a".equalsIgnoreCase(input)) {
                chosenContacts.addAll(contactDetailList);
                break;
            }

            String[] selections = input.split(",");
            for (String selection : selections) {
                try {
                    int index = Integer.parseInt(selection.trim()) - 1;
                    if (index >= 0 && index < contactDetailList.size()) {
                        chosenContacts.add(contactDetailList.get(index));
                    } else {
                        System.out.println("Invalid selection: " + (index + 1));
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input: " + selection);
                }
            }

            System.out.println("Selected " + chosenContacts.size() + " contacts. Continue selecting?");
            if (!askIfYes(br, "Continue selecting?")) {
                break;
            }
        }

        return chosenContacts;
    }

    private static void chooseToShow(List<ContactDetail> contactList, BufferedReader br) {
        if (contactList == null || contactList.isEmpty()) {
            System.out.println("No contacts to display.");
            return;
        }
        while (true) {
            showContactDetailList(contactList, "View Contacts", 0);

            System.out.println("Enter contact numbers to select (comma-separated), 'a' to view all. 'q' to quit:");
            try {
                String input = br.readLine();
                if ("".equals(input)) continue;
                if ("q".equals(input)) return;
                if (input.contains(",")) {
                    String[] choices = input.replaceAll("\\s+", "").split(",");
                    for (String choice : choices) {
                        int choiceInt = Integer.parseInt(choice);
                        if (choiceInt < 1 || choiceInt > contactList.size()) {
                            System.out.println("Invalid choice. Please enter a number between 1 and " + contactList.size());
                            return;
                        }
                        showContactDetail(contactList.get(choiceInt - 1));
                    }
                } else if ("A".equalsIgnoreCase(input)) {
                    for (ContactDetail contactDetail : contactList) {
                        showContactDetail(contactDetail);
                    }
                    Menu.anyKeyToContinue(br);
                } else {
                    int choice = Integer.parseInt(input);
                    if (choice < 1 || choice > contactList.size()) {
                        System.out.println("Invalid choice. Please enter a number between 1 and " + contactList.size());
                        return;
                    }
                    ContactDetail chosenContact = contactList.get(choice - 1);
                    showContactDetail(chosenContact);
                    Menu.anyKeyToContinue(br);
                }
            } catch (IOException e) {
                System.out.println("Error reading input: " + e.getMessage());
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
            }
        }
    }

    private List<ContactDetail> findContactDetails(String searchStr) {
        List<ContactDetail> foundContacts = new ArrayList<>();
        
        for (Map.Entry<byte[], byte[]> entry : contactFileMap.entrySet()) {
            ContactDetail contactDetail = ContactDetail.fromBytes(entry.getValue());
            if (contactDetail != null) {
                if ((contactDetail.getFid() != null && contactDetail.getFid().contains(searchStr)) ||
                    (contactDetail.getCid() != null && contactDetail.getCid().contains(searchStr)) ||
                    (contactDetail.getMemo() != null && contactDetail.getMemo().contains(searchStr))) {
                    foundContacts.add(contactDetail);
                }
            }
        }
        return foundContacts;
    }

    private ContactDetail findContact(BufferedReader br) {
        String input = Inputer.inputString(br,"Input the FID, CID or part of the memo:");
        List<ContactDetail> list = findContactDetails(input);
        return chooseOneContactDetailFromList(list, br);
    }

    private ContactDetail chooseOneContactDetailFromList(List<ContactDetail> contactDetailList, BufferedReader br) {
        if (contactDetailList.isEmpty()) return null;

        String title = "Chosen Contact";
        showContactDetailList(contactDetailList, title, 0);   

        System.out.println();
        int input = Inputer.inputInteger(br, "Enter contact number to select it, Enter to quit:", contactDetailList.size());

        int index = input - 1;
        if (index >= 0 && index < contactDetailList.size()) {
            return contactDetailList.get(index);
        }
        return null;
    }

    // Getter Methods
    public Feip getFeip() {
        return new Feip(FEIP, "12", "3", UpStrings.CONTACT);
    }
}
