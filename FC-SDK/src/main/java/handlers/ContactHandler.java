package handlers;

import appTools.Inputer;
import appTools.Menu;
import clients.ApipClient;
import clients.Client;
import constants.Constants;
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
import tools.BytesTools;
import tools.Hex;
import tools.JsonTools;
import tools.PersistentKeySortedMap;
import tools.StringTools;

import java.io.BufferedReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import appTools.Shower;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static appTools.Inputer.askIfYes;
import static constants.OpNames.*;

import java.io.IOException;

public class ContactHandler {
    protected static final Logger log = LoggerFactory.getLogger(ContactHandler.class);
    // Constants and Enums
    private static final Integer DEFAULT_SIZE = 10;
    private final List<String> fakeContactDetailList = new ArrayList<>();
    private final ConcurrentHashMap<String, ContactDetail> contactCache;
    private static final int CACHE_SIZE = 1000; // Configurable cache size

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
    private final BufferedReader br;
    // Instance Variables
    private final String myFid;
    private final ApipClient apipClient;
    private final CashHandler cashHandler;
    private final byte[] symKey;
    private final String myPriKeyCipher;
    private final byte[] myPubKey;
    private final PersistentKeySortedMap contactDB;

    // Constructor
    public ContactHandler(String myFid, ApipClient apipClient, CashHandler cashHandler, byte[] symKey, String myPriKeyCipher, BufferedReader br) {
        this.myFid = myFid;
        this.apipClient = apipClient;
        this.cashHandler = cashHandler;
        this.symKey = symKey;
        this.myPriKeyCipher = myPriKeyCipher;
        this.contactDB = new PersistentKeySortedMap(myFid,null, constants.Strings.CONTACT);
        this.br = br;
        this.myPubKey = KeyTools.priKeyToPubKey(Client.decryptPriKey(myPriKeyCipher,symKey));
        this.contactCache = new ConcurrentHashMap<>(CACHE_SIZE);
    }

    // Public Methods
    public void menu() {
        Menu menu = new Menu("Contact Menu");
        menu.add(READ, () -> readContacts(br));
        menu.add(UPDATE, () -> checkContacts(br));
        menu.add(FIND, () -> findContact(br));
        menu.add(ContactOp.ADD.toLowerCase(), () -> addContacts(br));
        menu.add(ContactOp.UPDATE.toLowerCase(), () -> updateContacts(br));
        menu.add(ContactOp.DELETE.toLowerCase(), () -> deleteContacts(br));
        menu.showAndSelect(br);
    }

    public String  addContact(BufferedReader br) {
        return opContact(null,null, br, ContactOp.ADD);
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
        return opContact(contactId,null, br, ContactOp.UPDATE);
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

    public String deleteContact(List<String> contactIds, BufferedReader br) {
        return opContact(null,contactIds, br, ContactOp.DELETE);
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
            List<String> contactIds = new ArrayList<>();
            for (ContactDetail contact : chosenContacts) {
                contactIds.add(contact.getContactId());
                synchronized (contactCache) {
                    contactDB.remove(contact.getFid().getBytes());
                    contactCache.remove(contact.getFid());
                }
            }

            String result = deleteContact(contactIds, br);
            if (Hex.isHex32(result)) {    
                System.out.println("Deleted contacts: " + contactIds + " in TX " + result + ".");
            } else {
                System.out.println("Failed to delete contacts: " + contactIds + ": " + result);
            }
        }
    }

    public void checkContacts(BufferedReader br) {
        Long lastHeight = contactDB.getLastHeight();
        List<Contact> contactList = loadAllContactList(lastHeight, true);
        List<ContactDetail> contactDetailList = contactToContactDetail(contactList);

        if (!contactList.isEmpty()) {
            contactDB.setLastHeight(contactList.get(0).getBirthHeight());
            deleteUnreadableContacts();
        }

        System.out.println("You have " + contactDetailList.size() + " updated contacts.");
        if (contactDetailList.size() > 0) chooseToShow(contactDetailList, br);
    }

    private void deleteUnreadableContacts(){
        if(fakeContactDetailList.isEmpty()) return;
        if(! askIfYes(br,"Got "+fakeContactDetailList.size()+" unreadable contacts. Delete them?"))return;
        String result = opContact(null,fakeContactDetailList,br,ContactOp.DELETE);
        if(Hex.isHex32(result)){
            System.out.println(fakeContactDetailList.size()+" unreadable contacts deleted in TX "+result+".");
            fakeContactDetailList.clear();
        }else{
            System.out.println("Failed to delete unreadable contacts: "+result);
        }
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
                if(chosenContacts.isEmpty())return;
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
    private String opContact(String contactId, List<String> contactIds,BufferedReader br, ContactOp op) {
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
            if(contactId!=null){
                contactDetail.setFid(contactId);
            }else{
                String fid = apipClient.chooseFid(br);
                if (fid == null) return null;
                contactDetail.setFid(fid);
            }
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
            if (contactIds == null) return null;
            contactData.setContactIds(contactIds);
        }else return null;

        Feip feip = getFeip();
        feip.setData(contactData);

        String opReturnStr = feip.toJson();
        long cd = Constants.CD_REQUIRED;

        if (askIfYes(br, "Are you sure to do below operation on chain?\n" + feip.toNiceJson() + "\n")) {
            String result = Wallet.carve(myFid,priKey, opReturnStr, cd, apipClient, cashHandler, br);
            if (Hex.isHex32(result)) {
                System.out.println("The contact is " + op.toLowerCase() + "ed: " + result + ".\n Wait a few minutes for confirmations before updating contacts...");
                return result;
            }else if(StringTools.isBase64(result)) {
                System.out.println("Sign the TX and broadcast it:\n"+result);
            } else {
                System.out.println("Failed to " + op.toLowerCase() + " contact:" + result);
            }
        }
        return null;
    }

    private List<Contact> loadAllContactList(Long lastHeight, Boolean active) {
        List<Contact> contactList = new ArrayList<>();
        List<String> last = new ArrayList<>();
        while (true) {
            List<Contact> subContactList = apipClient.freshContactSinceHeight(myFid, lastHeight, DEFAULT_SIZE, last, active);
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
            contactDB.put(contactDetail.getFid().getBytes(), contactDetail.toBytes());
            addToCache(contactDetail); // Add to cache
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
            List<ContactDetail> currentList = contactDB.getListFromEnd(lastKey, DEFAULT_SIZE, ContactDetail::fromBytes);
            if (currentList.isEmpty()) {
                break;
            }

            String title = "Choose Contacts";
            showContactDetailList(currentList, title, totalDisplayed);

            System.out.println("Enter contact numbers to select (comma-separated), 'a' to all. 'q' to quit, or press Enter for more:");
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

            if(input.equals("a")){
                chosenContacts.addAll(currentList);
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
        int[] widths = new int[]{13, 17, 20, 10, 12, 11};
        List<List<Object>> valueListList = new ArrayList<>();

        for (ContactDetail contactDetail : contactDetailList) {
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

            System.out.println("Enter contact numbers to select (comma-separated), 'a' to select all, or enter to finish:");
            String input = Inputer.inputString(br);

            if ("".equalsIgnoreCase(input)) {
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

    public List<ContactDetail> findContactDetails(String searchStr) {
        List<ContactDetail> foundContacts = Collections.synchronizedList(new ArrayList<>());
        
        // First search in cache
        contactCache.values().forEach(contact -> {
            if (matchesSearchCriteria(contact, searchStr)) {
                foundContacts.add(contact);
            }
        });
        
        // Then search in persistent storage
        for (Map.Entry<byte[], byte[]> entry : contactDB.entrySet()) {
            String fid = new String(entry.getKey());
            // Skip if already found in cache
            if (contactCache.containsKey(fid)) continue;
            
            ContactDetail contactDetail = ContactDetail.fromBytes(entry.getValue());
            if (contactDetail != null && matchesSearchCriteria(contactDetail, searchStr)) {
                foundContacts.add(contactDetail);
                addToCache(contactDetail); // Add to cache for future use
            }
        }
        return foundContacts;
    }

    private boolean matchesSearchCriteria(ContactDetail contact, String searchStr) {
        return (contact.getFid() != null && contact.getFid().contains(searchStr)) ||
               (contact.getCid() != null && contact.getCid().contains(searchStr)) ||
               (contact.getMemo() != null && contact.getMemo().contains(searchStr));
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
        int input = Inputer.inputInt(br, "Enter contact number to select it, Enter to quit:", contactDetailList.size());

        int index = input - 1;
        if (index >= 0 && index < contactDetailList.size()) {
            return contactDetailList.get(index);
        }
        return null;
    }

    // Getter Methods
    public Feip getFeip() {
        return Feip.fromProtocolName(Feip.ProtocolName.CONTACT);
    }

    // Helper methods for cache management
    private void addToCache(ContactDetail contact) {
        if (contact != null && contact.getFid() != null) {
            synchronized (contactCache) {
                if (contactCache.size() >= CACHE_SIZE) {
                    // Remove oldest entry when cache is full
                    Optional<Map.Entry<String, ContactDetail>> oldest = contactCache.entrySet().stream()
                        .min(Comparator.comparingLong(e -> e.getValue().getUpdateHeight()));
                    oldest.ifPresent(entry -> contactCache.remove(entry.getKey()));
                }
                contactCache.put(contact.getFid(), contact);
            }
        }
    }

    private ContactDetail getFromCache(String fid) {
        ContactDetail contact = contactCache.get(fid);
        if (contact != null) {
            // Update access time by re-putting
            synchronized (contactCache) {
                contactCache.put(fid, contact);
            }
        }
        return contact;
    }

    // Add this new method
    public ContactDetail getContact(String id) {
        // First try to get from cache
        ContactDetail contact = getFromCache(id);
        if (contact != null) {
            return contact;
        }
        
        // If not in cache, try to get from file map
        byte[] contactBytes = contactDB.get(id.getBytes());
        if (contactBytes != null) {
            contact = ContactDetail.fromBytes(contactBytes);
            if (contact != null) {
                addToCache(contact); // Add to cache for future use
            }
            return contact;
        }
        
        return null;
    }
}
