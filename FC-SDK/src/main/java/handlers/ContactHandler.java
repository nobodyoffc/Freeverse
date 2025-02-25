package handlers;

import appTools.Inputer;
import appTools.Menu;
import appTools.Settings;
import appTools.Shower;
import clients.ApipClient;
import clients.Client;
import constants.Constants;
import constants.FieldNames;
import crypto.CryptoDataByte;
import crypto.Encryptor;
import crypto.KeyTools;
import db.LocalDB;
import fcData.AlgorithmId;
import fcData.ContactDetail;
import fcData.FcEntity;
import feip.feipData.Contact;
import feip.feipData.ContactOpData;
import feip.feipData.Feip;
import feip.feipData.Service;
import feip.feipData.Service.ServiceType;

import org.jetbrains.annotations.NotNull;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.DateTools;
import tools.Hex;
import tools.JsonTools;
import tools.MapQueue;
import tools.StringTools;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public class ContactHandler extends Handler<ContactDetail> {
    private static final Logger log = LoggerFactory.getLogger(ContactHandler.class);
    public static final String FAKE_CONTACT_DETAIL = "fakeContactDetail";
    public static String name = HandlerType.CONTACT.name();
    public static final Object[] modules = new Object[]{
            Service.ServiceType.APIP,
            Handler.HandlerType.CASH
    };
    private final ApipClient apipClient;
    private final byte[] symKey;
    private final String myPriKeyCipher;
    private final byte[] myPubKey;
    private Map<String,String> fakeContactCipherMap;
    private final MapQueue<String, ContactDetail> recentContactDetailMapQueue;


    public ContactHandler(Settings settings) {
        super(settings, HandlerType.CONTACT, LocalDB.SortType.UPDATE_ORDER, FcEntity.getMapDBSerializer(ContactDetail.class), ContactDetail.class, true);
        this.apipClient = (ApipClient) settings.getClient(ServiceType.APIP);
        this.symKey = settings.getSymKey();
        this.myPriKeyCipher = settings.getMyPriKeyCipher();
        this.myPubKey = KeyTools.priKeyToPubKey(Client.decryptPriKey(myPriKeyCipher, symKey));
        this.recentContactDetailMapQueue = new MapQueue<>(200);
    }

    @Override
    public void menu(BufferedReader br, boolean withSettings) {
        Menu menu = new Menu("Contact Menu", this::close);
        addBasicMenuItems(br,menu);
        menu.add("List Locally Removed Contacts", () -> reloadRemovedItems(br));
        menu.add("Clear Locally Removed Records", () -> clearAllLocallyRemoved(br));
        menu.add("Check Contacts on Chain", () -> freshOnChainContacts(br));
        menu.add("Add Contacts on Chain", () -> addContacts(br));
        menu.add("Delete Contacts on Chain", () -> deleteContacts(br));
        menu.add("List deleted Contacts on Chain", () -> recoverContacts(br));
        menu.add("Clear on Chain Deleted Records", () -> clearDeletedRecord(br));
        if(cashHandler!=null)menu.add("Manage Cash", cashHandler::menu);
        if(withSettings)
            menu.add("Settings", () -> settings.setting(br, null));

        menu.showAndSelect(br);
    }

    protected void reloadRemovedItems(BufferedReader br) {
        Map<String, Long> removedItems = getAllFromMap(LocalDB.LOCAL_REMOVED_MAP, Serializer.LONG);
        List<String> chosenIds = listAndChooseFromStringLongMap(br,removedItems,"Choose to reload:");
        Map<String, Contact> items = reloadContactsFromChain(br, chosenIds);
        
        if(items.isEmpty()) System.out.println("No item reloaded.");
        else System.out.println("Successfully reloaded " + items.size() + " items.");
    }

    @NotNull
    public Map<String, Contact> reloadContactsFromChain(BufferedReader br, List<String> selectedIds) {
        Map<String,Contact> items = apipClient.loadOnChainItemByIds("contact", Contact.class, FieldNames.CONTACT_ID, selectedIds);

        List<Contact> reloadedList = new ArrayList<>();
        if(items==null|| items.isEmpty())return new HashMap<>();

        List<Contact> chosenDeletedContactList;
        List<Contact> deletedContactList = new ArrayList<>();
        boolean recovered;
        for(Contact item:items.values()){
            if(!item.isActive()) {
                deletedContactList.add(item);
            }else{
                reloadedList.add(item);
            }
        }
        if(!deletedContactList.isEmpty()){
            if(Inputer.askIfYes(br, "There are " + deletedContactList.size() + " on chain deleted contacts. Choose to recover them?")){
                chosenDeletedContactList = Inputer.chooseMultiFromListGeneric(deletedContactList, 0, 20, "Choose the deleted contacts to reload:", br);
                if(chosenDeletedContactList!=null){
                    recovered = recoverContacts(chosenDeletedContactList.stream().map(Contact::getContactId).collect(Collectors.toList()), null, br);
                    if(recovered) {
                        reloadedList.addAll(chosenDeletedContactList);
                    }
                }
            }
        }
        if(reloadedList.isEmpty())return new HashMap<>();

        List<ContactDetail> contactDetailList = contactToContactDetail(reloadedList);
        if(contactDetailList.isEmpty())return new HashMap<>();

        putAllContactDetail(contactDetailList);

        List<String> reloadedIdList = reloadedList.stream().map(Contact::getContactId).collect(Collectors.toList());
        removeFromMap(LocalDB.LOCAL_REMOVED_MAP, reloadedIdList);

        handleFakeContactData(br);

        return items;
    }

    private void clearDeletedRecord(BufferedReader br) {
        if(Inputer.askIfYes(br, "Are you sure you want to clear all on chain deleting records?")){
            clearAllOnChainDeleted();
            System.out.println("All on chain deleted records cleared.");
        }
    }

    public void putContact(String id, ContactDetail contact) {
        put(id, contact);
        recentContactDetailMapQueue.put(id, contact);
    }

    public ContactDetail getContact(String id) {
        ContactDetail contact = recentContactDetailMapQueue.get(id);
        if (contact != null) {
            return contact;
        }
        contact = get(id);
        if (contact != null) {
            recentContactDetailMapQueue.put(id, contact);
        }
        return contact;
    }

    public Map<String, ContactDetail> getAllContacts() {
        return getAll();
    }

    public NavigableMap<Long, String> getContactIndexIdMap() {
        return getIndexIdMap();
    }

    public NavigableMap<String, Long> getContactIdIndexMap() {
        return getIdIndexMap();
    }

    public ContactDetail getContactById(String id) {
        return getItemById(id);
    }

    public ContactDetail getContactByIndex(long index) {
        return getItemByIndex(index);
    }

    public Long getContactIndexById(String id) {
        return getIndexById(id);
    }

    public String getContactIdByIndex(long index) {
        return getIdByIndex(index);
    }

    public List<ContactDetail> getContactList(Integer size, Long fromIndex, String fromId,
            boolean isFromInclude, Long toIndex, String toId, boolean isToInclude, boolean isFromEnd) {
        return getItemList(size, fromIndex, fromId, isFromInclude, toIndex, toId, isToInclude, isFromEnd);
    }

    public LinkedHashMap<String, ContactDetail> getContactMap(int size, Long fromIndex, String fromId,
            boolean isFromInclude, Long toIndex, String toId, boolean isToInclude, boolean isFromEnd) {
        return getItemMap(size, fromIndex, fromId, isFromInclude, toIndex, toId, isToInclude, isFromEnd);
    }

    public void removeContact(String id) {
        remove(id);
        recentContactDetailMapQueue.remove(id);
    }

    public void removeContacts(List<String> ids) {
        remove(ids);
    }

    public void clearContacts() {
        clear();
        recentContactDetailMapQueue.clear();
    }

    public List<ContactDetail> searchContacts(String searchString) {
        return searchInValue(searchString);
    }

    public List<ContactDetail> searchContacts(BufferedReader br, boolean withChoose, boolean withOperation) {
        return searchItems(br, withChoose, withOperation);
    }

    public void opOnChain(List<ContactDetail> chosenContacts, String ask, BufferedReader br) {
        ContactOpData.Op op = null;
        String opStr = Inputer.chooseOne(
            Arrays.stream(ContactOpData.Op.values())
                  .map(ContactOpData.Op::toLowerCase)
                  .toArray(String[]::new),
            null,
            ask,
            br
        );
        if (opStr != null) {
            for (ContactOpData.Op value : ContactOpData.Op.values()) {
                if (value.name().equalsIgnoreCase(opStr)) {
                    op = value;
                    break;
                }
            }
        }
        if (op == null) return;

        switch (op) {
            case ADD -> addContacts(chosenContacts, br);
            case DELETE -> deleteContacts(chosenContacts, br);
            case RECOVER -> recoverContacts(null, chosenContacts, br);
        }
    }

    public String addContact(BufferedReader br) {
        return opContact(null, null, ContactOpData.Op.ADD, br);
    }

    public void addContacts(List<ContactDetail> itemList, @Nullable BufferedReader br) {
        if(itemList == null && br != null) addContacts(br);
        else{
            if(itemList==null || itemList.isEmpty())return;
            for(ContactDetail item:itemList){
                ContactOpData contactOpData = encryptContact(item);
                carveContactData(contactOpData, br);
                if(br!=null && !Inputer.askIfYes(br,"Carve next?"))break;
            }
        }
    }

    public void addContacts(BufferedReader br) {
        do {
            String result = addContact(br);
            if (Hex.isHex32(result)) {
                System.out.println("Contact added successfully: " + result);
            } else {
                System.out.println("Failed to add contact: " + result);
            }
        } while (Inputer.askIfYes(br, "Do you want to add another contact?"));
    }


    public String deleteContact(List<String> contactIds, BufferedReader br) {
        return opContact(null, contactIds, ContactOpData.Op.DELETE, br);
    }

    public void deleteContacts(BufferedReader br) {
        List<ContactDetail> chosenContacts = chooseItems(br);
        deleteContacts(chosenContacts, br);
    }

    public void deleteContacts(List<ContactDetail> chosenContacts, BufferedReader br) {
        if (chosenContacts.isEmpty()) {
            System.out.println("No contacts chosen for deletion.");
            return;
        }

        if (Inputer.askIfYes(br, "View them before delete?")) {
            showItemList("Contacts", chosenContacts, 0);
        }

        if (Inputer.askIfYes(br, "Delete " + chosenContacts.size() + " contacts?")) {
            List<String> contactIds = new ArrayList<>();
            for (ContactDetail contact : chosenContacts) {
                contactIds.add(contact.getContactId());
            }

            String result = deleteContact(contactIds, br);
            if (Hex.isHex32(result)) {
                System.out.println("Deleted contacts: " + contactIds + " in TX " + result + ".");
                markAsOnChainDeleted(contactIds);
                remove(contactIds);

            } else {
                System.out.println("Failed to delete contacts: " + contactIds + ": " + result);
            }
        }
    }

    public void putAllContactDetail(List<ContactDetail> items) {
        Map<String, ContactDetail> contactDetailMap = new HashMap<>();
        for (ContactDetail item : items) {
            contactDetailMap.put(item.getContactId(), item);
        }
        putAllContactDetail(contactDetailMap);
    }


    public void putAllContactDetail(Map<String, ContactDetail> items) {
        super.putAll(items);
        for (Map.Entry<String, ContactDetail> entry : items.entrySet()) {
            recentContactDetailMapQueue.put(entry.getKey(), entry.getValue());
        }
    }

    private String opContact(String contactId, List<String> contactIds, ContactOpData.Op op,  BufferedReader br) {
        if (op == null) return null;
        ContactOpData contactOpData = new ContactOpData();
        contactOpData.setOp(op.toLowerCase());
        ContactDetail contactDetail = new ContactDetail();
        byte[] priKey = Client.decryptPriKey(myPriKeyCipher, symKey);
        if (priKey == null) {
            System.out.println("Failed to get the priKey of " + mainFid);
            return null;
        }

        if (op.equals(ContactOpData.Op.ADD) ) {
            if (contactId != null) {
                contactDetail.setFid(contactId);
            } else {
                String fid = apipClient.chooseFid(br);
                if (fid == null) return null;
                contactDetail.setFid(fid);
            }
            contactDetail.setMemo(Inputer.inputString(br, "Input memo:"));
            contactDetail.setSeeStatement(Inputer.askIfYes(br, "See its statements?"));
            contactDetail.setSeeWritings(Inputer.askIfYes(br, "See its writings?"));

            Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
            CryptoDataByte cryptoDataByte = encryptor.encryptByAsyOneWay(JsonTools.toJson(contactDetail).getBytes(), myPubKey);
            if (cryptoDataByte.getCode() != 0) {
                log.error("Failed to encrypt.");
                return null;
            }
            contactOpData.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7.getDisplayName());
            byte[] b = cryptoDataByte.toBundle();
            String cipher = Base64.getEncoder().encodeToString(b);
            contactOpData.setCipher(cipher);
        } else if (op.equals(ContactOpData.Op.DELETE)) {
            if (contactIds == null) return null;
            contactOpData.setContactIds(contactIds);
        } else return null;

        Feip feip = getFeip();
        feip.setData(contactOpData);

        String opReturnStr = feip.toJson();
        long cd = Constants.CD_REQUIRED;

        if (Inputer.askIfYes(br, "Are you sure to do below operation on chain?\n" + feip.toNiceJson() + "\n")) {
            String result = CashHandler.carve(opReturnStr, cd, priKey, apipClient);
            if (Hex.isHex32(result)) {
                System.out.println("The contact is " + op.toLowerCase() + "ed: " + result + ".\n Wait a few minutes for confirmations before updating contacts...");
                return result;
            } else if (StringTools.isBase64(result)) {
                System.out.println("Sign the TX and broadcast it:\n" + result);
            } else {
                System.out.println("Failed to " + op.toLowerCase() + " contact:" + result);
            }
        }
        return null;
    }

    public void freshOnChainContacts(BufferedReader br) {
        if(apipClient==null){
            System.out.println("Unable to update on chain data due to the absence of ApipClient.");
            return;
        }
        Object lastHeightObj = getMeta(FieldNames.LAST_HEIGHT);
        long lastHeight;
        if(lastHeightObj==null) lastHeight = 0;
        else lastHeight = ((Number)lastHeightObj).longValue();

        List<Contact> contactList = loadAllOnChainItems("contact", FieldNames.CONTACT_ID, FieldNames.LAST_HEIGHT, FieldNames.OWNER, lastHeight, true, apipClient, Contact.class, null);
        List<ContactDetail> contactDetailList;
        
        if (contactList!=null && !contactList.isEmpty()) {
            contactDetailList = contactToContactDetail(contactList);
            putAllContactDetail(contactDetailList);

            System.out.println("You have " + contactDetailList.size() + " updated contacts.");
            if (contactDetailList.size() > 0)
                chooseToShow(contactDetailList, br);

            handleFakeContactData(br);

            Menu.anyKeyToContinue(br);
        } else {
            System.out.println("No contacts updated.");
        }
    }

    private void handleFakeContactData(BufferedReader br) {
        if (!fakeContactCipherMap.isEmpty()) {
            if (Inputer.askIfYes(br, "Got " + fakeContactCipherMap.size() + " unreadable contacts. Check them?")) {
                for (Map.Entry<String, String> entry : fakeContactCipherMap.entrySet()) {
                    System.out.println(entry.getKey() + ": " + entry.getValue());
                }
            }
            deleteUnreadableContacts(br);
            fakeContactCipherMap.clear();
        }
    }

    private void deleteUnreadableContacts(BufferedReader br) {
        if (fakeContactCipherMap.isEmpty()) return;
        if (!Inputer.askIfYes(br, "There are " + fakeContactCipherMap.size() + " unreadable contacts. Delete them?")) return;
        String result = opContact(null, new ArrayList<>(fakeContactCipherMap.keySet()), ContactOpData.Op.DELETE, br);
        if (Hex.isHex32(result)) {
            fakeContactCipherMap.clear();
        } else {
            System.out.println("Failed to delete unreadable contacts: " + result);
        }
    }

    public Feip getFeip() {
        return Feip.fromProtocolName(Feip.ProtocolName.CONTACT);
    }

    @Nullable
    private static List<String> listAndChooseFromStringLongMap(BufferedReader br, Map<String, Long> removedItems, String ask) {
        if (removedItems == null || removedItems.isEmpty()) {
            System.out.println("No locally removed items found.");
            return null;
        }

        // Show items and let user choose
        Map<String, Long> selectedDisplayItems = Inputer.chooseMultiFromMapGeneric(
                removedItems,
                null,
                0,
                Shower.DEFAULT_SIZE,
                ask,
                br
        );

        if (selectedDisplayItems == null || selectedDisplayItems.isEmpty()) {
            return null;
        }

        // Extract IDs from selected display strings
        return selectedDisplayItems.keySet().stream().toList();
    }

    private void clearAllLocallyRemoved(BufferedReader br) {
        if(Inputer.askIfYes(br, "Are you sure you want to clear all locally removed records?")){
            clearAllLocallyRemoved();
            System.out.println("All local removed records cleared.");
        }
    }

    public void recoverContacts(BufferedReader br) {
        Map<String, Long> deletedIds = getAllOnChainDeleted();
        List<String> chosenIds;
        int count = 0;

        if(deletedIds!=null && !deletedIds.isEmpty()) {
            System.out.println("There are "+deletedIds.size()+" on chain deleted record in local DB.");
            chosenIds = listAndChooseFromStringLongMap(br,deletedIds,"Choose items to recover:" );
            if(chosenIds!=null && !chosenIds.isEmpty()) {
                Map<String, Contact> result = reloadContactsFromChain(br, chosenIds);
                count += result.size();
            }
        }else System.out.println("No local records of on chain deleted contacts.");

        if(!Inputer.askIfYes(br,"Check more deleted contacts from blockchain?"))return;

        List<Contact> contactList = loadAllOnChainItems("contact",FieldNames.CONTACT_ID,FieldNames.LAST_HEIGHT,FieldNames.OWNER,0L, false,apipClient,Contact.class, br);

        Iterator<Contact> iterator = contactList.iterator();
        Set<String> checkedRemovedIds = deletedIds.keySet();

        while (iterator.hasNext()){
            if(checkedRemovedIds.contains(iterator.next().getContactId()))
                iterator.remove();
        }

        List<ContactDetail> contactDetailList = contactToContactDetail(contactList);

        List<ContactDetail> chosenContacts = chooseContactDetailList(contactDetailList, new ArrayList<>(), 0, br);

        if(chosenContacts!=null && !chosenContacts.isEmpty()) {
            putAllContactDetail(chosenContacts);
            count += chosenContacts.size();
        }

        System.out.println(count + " items recovered.");

        handleFakeContactData(br);
    }

    private List<ContactDetail> contactToContactDetail(List<Contact> contactList) {
        List<ContactDetail> contactDetailList = new ArrayList<>();
        fakeContactCipherMap = new HashMap<>();

        for (Contact contact : contactList) {
            ContactDetail contactDetail = ContactDetail.fromContact(contact, priKey, apipClient);
            if (contactDetail == null) {
                fakeContactCipherMap.put(contact.getContactId(), 
                    DateTools.longToTime(contact.getBirthTime(),DateTools.FULL_FORMAT)+" "+contact.getCipher());
                continue;
            }
            contactDetailList.add(contactDetail);
        }

        return contactDetailList;
    }

    private List<ContactDetail> chooseContactDetailList(List<ContactDetail> currentList, List<ContactDetail> chosenContacts,
                                                      int totalDisplayed, BufferedReader br) {
        String title = "Choose Contacts";
        ContactDetail.showContactDetailList(currentList, title, totalDisplayed);

        System.out.println("Enter contact numbers to select (comma-separated), 'a' for all. 'q' to quit, or press Enter for more:");
        String input = Inputer.inputString(br);

        if ("".equals(input)) {
            return null;  // Signal to continue to next page
        }

        if (input.equals("q")) {
            chosenContacts.add(null);  // Signal to break the loop
            return chosenContacts;
        }

        return chosenContacts;
    }

    private ContactOpData encryptContact(ContactDetail contactDetail) {
        if(contactDetail==null) return null;
        
        // Clear fields that shouldn't be included in encryption
        contactDetail.setUpdateHeight(null);
        contactDetail.setId(null);
        contactDetail.setContactId(null);
        
        ContactOpData contactOpData = new ContactOpData();
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = encryptor.encryptByAsyOneWay(JsonTools.toJson(contactDetail).getBytes(), myPubKey);
        
        if (cryptoDataByte.getCode() != 0) {
            log.error("Failed to encrypt.");
            return null;
        }
        
        contactOpData.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7.getDisplayName());
        byte[] b = cryptoDataByte.toBundle();
        String cipher = Base64.getEncoder().encodeToString(b);
        contactOpData.setCipher(cipher);
        
        return contactOpData;
    }

    @Nullable
    private String carveContactData(ContactOpData contactOpData, BufferedReader br) {
        if (contactOpData == null) return null;
        
        Feip feip = getFeip();
        feip.setData(contactOpData);
        
        String opReturnStr = feip.toJson();
        long cd = Constants.CD_REQUIRED;

        if (br != null && !Inputer.askIfYes(br, "Are you sure to do below operation on chain?\n" + feip.toNiceJson() + "\n")) {
            return null;
        }
        
        return carve(opReturnStr, cd, br);
    }

    public boolean recoverContacts(@Nullable List<String> contactIds, @Nullable List<ContactDetail> chosenContacts, BufferedReader br) {
        String result;
        if(contactIds != null && !contactIds.isEmpty()) {
            if (!Inputer.askIfYes(br, "Recover " + contactIds.size() + " contacts?")) {
                return false;
            }
            result = recoverContact(contactIds, br);
        } else if (chosenContacts != null && !chosenContacts.isEmpty()) {
            if (!Inputer.askIfYes(br, "Recover " + chosenContacts.size() + " contacts?")) {
                return false;
            }
            List<String> ids = chosenContacts.stream()
                .map(ContactDetail::getContactId)
                .collect(Collectors.toList());
            result = recoverContact(ids, br);
        } else {
            return false;
        }

        if (Hex.isHex32(result)) {
            System.out.println("Recovered contacts: " + (contactIds != null ? contactIds : chosenContacts.stream()
                .map(ContactDetail::getContactId)
                .collect(Collectors.toList())) + " in TX " + result + ".");
            if (contactIds != null) {
                removeFromMap(LocalDB.ON_CHAIN_DELETED_MAP, contactIds);
            } else if (chosenContacts != null) {
                List<String> ids = chosenContacts.stream()
                    .map(ContactDetail::getContactId)
                    .collect(Collectors.toList());
                removeFromMap(LocalDB.ON_CHAIN_DELETED_MAP, ids);
            }
            return true;
        } else {
            System.out.println("Failed to recover contacts: " + (contactIds != null ? contactIds : chosenContacts.stream()
                .map(ContactDetail::getContactId)
                .collect(Collectors.toList())) + ": " + result);
            return false;
        }
    }

    public String recoverContact(List<String> contactIds, BufferedReader br) {
        return opContact(null, contactIds, ContactOpData.Op.RECOVER, br);
    }

    @Override
    public void opItems(List<ContactDetail> items, String ask, BufferedReader br) {
        Menu menu = new Menu("Contact Operations", () -> {});
        menu.add("Show details", () -> showItemDetails(items, br));
        menu.add("Remove from local", () -> removeItems(items.stream().map(ContactDetail::getContactId).collect(Collectors.toList()), br));
        menu.add("Delete on chain", () -> deleteContacts(items, br));
        menu.add("Recover on chain", () -> recoverContacts(null, items, br));
        menu.add("Add to chain", () -> addContacts(items, br));
        menu.showAndSelect(br);
    }

    /**
     * Gets all contacts currently stored in the recent contacts cache.
     * Note that this only returns the most recently accessed contacts (up to 200),
     * not all contacts in the system.
     * 
     * @return Map of contact IDs to ContactDetails from the recent contacts cache
     */
    public Map<String, ContactDetail> getAllRecentContacts() {
        return recentContactDetailMapQueue.getMap();
    }
} 