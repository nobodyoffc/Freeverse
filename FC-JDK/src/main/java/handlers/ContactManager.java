package handlers;

import ui.Inputer;
import ui.Menu;
import config.Settings;
import ui.Shower;
import clients.ApipClient;
import constants.Constants;
import constants.FieldNames;
import core.crypto.CryptoDataByte;
import core.crypto.Decryptor;
import core.crypto.Encryptor;
import db.LocalDB;
import data.fcData.AlgorithmId;
import data.fcData.ContactDetail;
import data.fchData.Cid;
import data.feipData.Contact;
import data.feipData.ContactOpData;
import data.feipData.Feip;
import data.feipData.Service.ServiceType;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.DateUtils;
import utils.Hex;
import utils.JsonUtils;
import utils.MapQueue;
import utils.StringUtils;

import java.io.BufferedReader;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import static ui.Shower.DEFAULT_PAGE_SIZE;
import static constants.FieldNames.*;
import static constants.IndicesNames.CONTACT;
import static constants.Strings.TIME;

public class ContactManager extends Manager<ContactDetail> {
    private static final Logger log = LoggerFactory.getLogger(ContactManager.class);
    public static String name = ManagerType.CONTACT.name();

    private final ApipClient apipClient;
    private final byte[] symkey;
    private final String myPrikeyCipher;
    private final String myPubKey;
    private Map<String,String> fakeContactCipherMap;
    private final MapQueue<String, ContactDetail> recentContactDetailMapQueue;

    public ContactManager(Settings settings) {
        super(settings, ManagerType.CONTACT, LocalDB.SortType.UPDATE_ORDER, ContactDetail.class, true, true);

        this.apipClient = (ApipClient) settings.getClient(ServiceType.APIP);
        this.symkey = settings.getSymkey();
        this.myPrikeyCipher = settings.getMyPrikeyCipher();
        this.myPubKey = settings.getConfig().getMainCidInfoMap().get(mainFid).getPubkey();
        this.recentContactDetailMapQueue = new MapQueue<>(200);
    }

    @Override
    public void menu(BufferedReader br, boolean isRootMenu) {
        if(cashHandler==null) cashHandler = (CashManager) settings.getManager(ManagerType.CASH);

        freshOnChainContacts(br);

        Menu menu = newMenu(itemName, isRootMenu);

        menu.add("List Local "+ StringUtils.capitalize(itemName), () -> listItems(br));
        menu.add("Search Local "+ StringUtils.capitalize(itemName), () -> searchItems(br, true,true));
        menu.add("Fresh Contacts on Chain", () -> freshOnChainContacts(br));
        menu.add("Add Contacts on Chain", () -> addContacts(br));
        menu.add("Delete Contacts on Chain", () -> deleteContacts(br));
        menu.add("List on Chain deleted Contacts", () -> recoverContacts(br));
        menu.add("List Locally Removed Contacts", () -> reloadRemovedItems(br));
        menu.add("Clear Locally Removed Records", () -> clearAllLocallyRemoved(br));
        menu.add("Clear on Chain Deleted Records", () -> clearDeletedRecord(br));
        menu.add("Clear and Reload",()->reloadAllContactsFromChain(br));
        if(isRootMenu && cashHandler!=null)
            menu.add("My Cash", () -> cashHandler.menu(br, false));
        if(isRootMenu)
            menu.add("Settings", () -> settings.setting(br, null));

        menu.showAndSelect(br);
    }



    private void reloadAllContactsFromChain(BufferedReader br) {
        clearTheDatabase(br);
        freshOnChainContacts(br);
    }

    public List<String> inputOrSearchFidList(BufferedReader br){
        List<String> fidList = new ArrayList<>();
        String op = Inputer.chooseOne(new String[]{"Input FIDs","Search in contacts","Search on chain"}, null, "Select an algorithm", br);
        switch (op) {
            case "Input FIDs" -> {
                String[] fidArray = Inputer.inputFidArray(br, "Input the FIDs of the receivers. Enter to send to yourself:", 0);
                if (fidArray.length > 0) {
                    fidList.addAll(Arrays.asList(fidArray));
                }
            }
            case "Search in contacts" -> {
                List<ContactDetail> contactDetailList = searchFidInContact(br);
                if (contactDetailList != null && !contactDetailList.isEmpty()) {
                    List<ContactDetail> chosenContactDetailList = Shower.showOrChooseListInPages("Contacts",contactDetailList,DEFAULT_PAGE_SIZE, null, true,ContactDetail.class,br);
                    if (chosenContactDetailList != null && !chosenContactDetailList.isEmpty()) {
                        fidList.addAll(chosenContactDetailList.stream().map(ContactDetail::getFid).collect(Collectors.toList()));
                    }
                }
            }
            case "Search on chain" -> {
                List<Cid> cidList = searchCidOnChain(br);
                if (cidList != null && !cidList.isEmpty()) {
                    fidList.addAll(cidList.stream().map(Cid::getId).toList());
                }
            }
        }
        return fidList;
    }

    public List<ContactDetail> searchFidInContact(BufferedReader br){
        List<ContactDetail> contactDetailList = new ArrayList<>();
        while(true){
            List<ContactDetail> batchContactDetailList = searchContacts(br, true, false);
            contactDetailList.addAll(batchContactDetailList);
            if(Inputer.askIfYes(br, "Search more?"))continue;
            else break;
        }
        return contactDetailList;
    }

    public List<Cid> searchCidOnChain(BufferedReader br){
        List<Cid> cidList = new ArrayList<>();
        while(true){
            List<Cid> batchCidList = apipClient.searchCidList(br, true);
            if(batchCidList!=null && !batchCidList.isEmpty())
                cidList.addAll(batchCidList);
            else System.out.println("No items found. Mind the capitalization.");
            if(Inputer.askIfYes(br, "Search more?"))continue;
            else break;
        }
        return cidList;
    }

    protected void reloadRemovedItems(BufferedReader br) {
        Map<String, Long> removedItems = localDB.getAllFromMap(LocalDB.LOCAL_REMOVED_MAP);
        List<String> chosenIds = Shower.showAndChooseFromStringLongMap(br,removedItems,"Choose to reload:");
        Map<String, Contact> items = reloadContactsFromChain(br, chosenIds);
        
        if(items.isEmpty()) System.out.println("No item reloaded.");
        else System.out.println("Successfully reloaded " + items.size() + " items.");
    }

    @NotNull
    public Map<String, Contact> reloadContactsFromChain(BufferedReader br, List<String> selectedIds) {
        Map<String,Contact> items = apipClient.loadOnChainItemByIds("contact", Contact.class, selectedIds);

        List<Contact> reloadedList = new ArrayList<>();
        if(items==null|| items.isEmpty())return new HashMap<>();

        List<Contact> chosenDeletedContactList = null;
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
                chosenDeletedContactList = Shower.showOrChooseListInPages("Delete Contacts",chosenDeletedContactList,DEFAULT_PAGE_SIZE, null, true,Contact.class,br);
                if(chosenDeletedContactList!=null){
                    recovered = recoverContacts(chosenDeletedContactList.stream().map(Contact::getId).collect(Collectors.toList()), null, br);
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

        List<String> reloadedIdList = reloadedList.stream().map(Contact::getId).collect(Collectors.toList());
        localDB.removeFromMap(LocalDB.LOCAL_REMOVED_MAP, reloadedIdList);

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
        localDB.put(id, contact);
        recentContactDetailMapQueue.put(id, contact);
    }

    public ContactDetail getContact(String id) {
        ContactDetail contact = recentContactDetailMapQueue.get(id);
        if (contact != null) {
            return contact;
        }
        contact = localDB.get(id);
        if (contact != null) {
            recentContactDetailMapQueue.put(id, contact);
        }
        return contact;
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

    public void addContacts(BufferedReader br) {
        List<String> fids = inputOrSearchFidList(br);

        if(fids.size()==1) {
            opContact(fids.get(0), null, ContactOpData.Op.ADD, br);
            return;
        }
        System.out.println(fids.size()+" contact will be encrypted and carve on chain...");
        for (String fid:fids){
            opContact(fid, null, ContactOpData.Op.ADD, br);
            if(Inputer.askIfYes(br,"Stop to add?"))break;
        }
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


    public String deleteContact(List<String> contactIds, BufferedReader br) {
        return opContact(null, contactIds, ContactOpData.Op.DELETE, br);
    }

    public void deleteContacts(BufferedReader br) {
        if (dbEmpty()) return;
        List<ContactDetail> finalChosenList = chooseItemList(br);
        if(finalChosenList!=null && finalChosenList.size()>0)
            deleteContacts(finalChosenList, br);
    }

    public void deleteContacts(List<ContactDetail> chosenContacts, BufferedReader br) {
        if (chosenContacts.isEmpty()) {
            System.out.println("No contacts chosen for deletion.");
            return;
        }

        if (Inputer.askIfYes(br, "View them before delete?")) {
            Shower.showOrChooseListInPages("Chosen  Contacts", chosenContacts, DEFAULT_PAGE_SIZE, null, true, ContactDetail.class, br);
        }

        if (Inputer.askIfYes(br, "Delete " + chosenContacts.size() + " contacts?")) {
            List<String> contactIds = new ArrayList<>();
            for (ContactDetail contact : chosenContacts) {
                contactIds.add(contact.getId());
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
            contactDetailMap.put(item.getId(), item);
        }
        putAllContactDetail(contactDetailMap);
    }


    public void putAllContactDetail(Map<String, ContactDetail> items) {
        super.localDB.putAll(items);
        for (Map.Entry<String, ContactDetail> entry : items.entrySet()) {
            recentContactDetailMapQueue.put(entry.getKey(), entry.getValue());
        }
    }

    private String opContact(String id, List<String> ids, ContactOpData.Op op,  BufferedReader br) {
        if (op == null) return null;
        ContactOpData contactOpData = new ContactOpData();
        contactOpData.setOp(op.toLowerCase());

        byte[] priKey = Decryptor.decryptPrikey(myPrikeyCipher, symkey);
        if (priKey == null) {
            System.out.println("Failed to get the priKey of " + mainFid);
            return null;
        }

        if (op.equals(ContactOpData.Op.ADD) ) {
            String fid;
            if (id != null) {
                fid = id;
            } else {
                fid = apipClient.chooseFid(br);
                if (fid == null) return null;

            }

            ContactDetail contactDetail = new ContactDetail();
            System.out.println("\nAdding "+fid+"...");
            contactDetail.setFid(fid);
            contactDetail.setTitles(Inputer.inputStringList(br, "Input titles:",0));
            contactDetail.setMemo(Inputer.inputString(br, "Input memo:"));
            contactDetail.setSeeStatement(Inputer.askIfYes(br, "See its statements?"));
            contactDetail.setSeeWritings(Inputer.askIfYes(br, "See its writings?"));

            if (!encryptContactDetail(contactOpData, contactDetail)) return null;
        } else {
            if (ids == null) return null;
            contactOpData.setContactIds(ids);
        }

        Feip feip = getFeip();
        feip.setData(contactOpData);

        String opReturnStr = feip.toJson();
        long cd = Constants.CD_REQUIRED;

        if (Inputer.askIfYes(br, "Are you sure to do below operation on chain?\n" + feip.toNiceJson() + "\n")) {
            String result = CashManager.carve(opReturnStr, cd, priKey, apipClient);
            if (Hex.isHex32(result)) {
                System.out.println("The contact is " + op.toLowerCase() + "ed: " + result + ".\nWait a few minutes for confirmations before updating contacts...");
                return result;
            } else if (StringUtils.isBase64(result)) {
                System.out.println("Sign the TX and broadcast it:\n" + result);
            } else {
                System.out.println("Failed to " + op.toLowerCase() + " contact:" + result);
            }
        }
        return null;
    }

    public boolean encryptContactDetail(ContactOpData contactOpData, ContactDetail contactDetail) {
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        if(myPubKey==null)return false;
        byte[] pubKey = Hex.fromHex(myPubKey);
        CryptoDataByte cryptoDataByte = encryptor.encryptByAsyOneWay(JsonUtils.toJson(contactDetail).getBytes(), pubKey);
        if (cryptoDataByte.getCode() != 0) {
            log.error("Failed to encrypt.");
            return false;
        }
        contactOpData.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7.getDisplayName());
        byte[] b = cryptoDataByte.toBundle();
        String cipher = Base64.getEncoder().encodeToString(b);
        contactOpData.setCipher(cipher);
        return true;
    }

    public void freshOnChainContacts(BufferedReader br) {
        if(apipClient==null){
            System.out.println("Unable to update on chain data due to the absence of ApipClient.");
            return;
        }
        Object lastHeightObj = localDB.getMeta(FieldNames.LAST_HEIGHT);
        long lastHeight;
        if(lastHeightObj==null) lastHeight = 0;
        else lastHeight = ((Number)lastHeightObj).longValue();

        List<Contact> contactList = loadAllOnChainItems(CONTACT, FieldNames.LAST_HEIGHT, FieldNames.OWNER, lastHeight, true, apipClient, Contact.class, null, true);
        List<ContactDetail> contactDetailList;
        
        if (contactList!=null && !contactList.isEmpty()) {
            contactDetailList = contactToContactDetail(contactList);
            putAllContactDetail(contactDetailList);

            System.out.println("You have " + contactDetailList.size() + " updated contacts.");
            if (contactDetailList.size() > 0)
                chooseToShowNiceJsonList(contactDetailList, br);

            handleFakeContactData(br);

            Menu.anyKeyToContinue(br);
        } else {
            System.out.println("No contacts updated.");
        }
    }

    private void handleFakeContactData(BufferedReader br) {
        if (!fakeContactCipherMap.isEmpty()) {
            if (Inputer.askIfYes(br, "Got " + fakeContactCipherMap.size() + " unreadable contacts. Check them?")) {

                List<List<Object>> valueListList = new ArrayList<>();

                String[] fields = new String[]{
                        ID,
                        TIME+" & " +CIPHER
                };
                int[] widths = new int[]{
                        64,
                        64
                };

                for (Map.Entry<String, String> entry : fakeContactCipherMap.entrySet()) {
                    List<Object> valueList = new ArrayList<>();
                    valueList.add(entry.getKey());
                    valueList.add(entry.getValue());

                    valueListList.add(valueList);
                }

                Shower.showOrChooseList("Failed to decrypted",fields, widths, valueListList, null);
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

    private void clearAllLocallyRemoved(BufferedReader br) {
        if(Inputer.askIfYes(br, "Are you sure you want to clear all locally removed records?")){
            clearAllLocallyRemoved();
            System.out.println("All local removed records cleared.");
        }
    }

    public void recoverContacts(BufferedReader br) {
        Map<String, Long> deletedIds = getAllOnChainDeletedRecords();
        List<String> chosenIds;
        int count = 0;
        Map<String, Contact> localDeletedContacts = new HashMap<>();
        if(deletedIds!=null && !deletedIds.isEmpty()) {
            System.out.println("There are "+deletedIds.size()+" on chain deleted record in local DB.");
            chosenIds = Shower.showAndChooseFromStringLongMap(br,deletedIds,"Choose items to recover:" );
            if(chosenIds!=null && !chosenIds.isEmpty()) {
                localDeletedContacts = reloadContactsFromChain(br, chosenIds);
                count += localDeletedContacts.size();
            }
        }else System.out.println("No local records of on chain deleted contacts.");

        if(!Inputer.askIfYes(br,"Check more deleted contacts from blockchain?"))return;

        List<Contact> onChainDeleted = loadAllOnChainItems(CONTACT, FieldNames.LAST_HEIGHT,FieldNames.OWNER,0L, false,apipClient,Contact.class, br, false);
        if(onChainDeleted.isEmpty()){
            System.out.println("No deleted items on chain.1");
//            return;
        }
        Iterator<Contact> iterator = onChainDeleted.iterator();
        removeDeleted(deletedIds, iterator);

        List<Contact> finalChosenDeleted = new ArrayList<>();
        finalChosenDeleted.addAll(onChainDeleted);
        finalChosenDeleted.addAll(localDeletedContacts.values());

        List<ContactDetail> contactDetailList = contactToContactDetail(finalChosenDeleted);

        List<ContactDetail> chosenContacts = Shower.showOrChooseListInPages("Chosen  Contacts", contactDetailList, DEFAULT_PAGE_SIZE, null, true, ContactDetail.class, br);

        recoverContacts(null,chosenContacts,br);

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
            ContactDetail contactDetail = ContactDetail.fromContact(contact, prikey, apipClient);
            if (contactDetail == null) {
                fakeContactCipherMap.put(contact.getId(),
                    DateUtils.longToTime(contact.getBirthTime()*1000, DateUtils.LONG_FORMAT)+" "+contact.getCipher());
                continue;
            }
            contactDetailList.add(contactDetail);
        }

        return contactDetailList;
    }
//
//    private List<ContactDetail> chooseContactDetailList(List<ContactDetail> currentList, int totalDisplayed, BufferedReader br) {
//
//        List<ContactDetail> chosenContacts = new ArrayList<>();
//        String title = "Choose Contacts";
//        ContactDetail.showContactDetailList(currentList, title, totalDisplayed,true);
//
//        System.out.println("Enter contact numbers to select (comma-separated), 'a' for all. 'q' to quit, or press Enter for more:");
//        String input = Inputer.inputString(br);
//
//        if ("".equals(input)) {
//            return null;  // Signal to continue to next page
//        }
//
//        if ("a".equals(input)) {
//            return currentList;  // Signal to continue to next page
//        }
//
//        if (input.equals("q")) {
//            chosenContacts.add(null);  // Signal to break the loop
//            return chosenContacts;
//        }
//
//        String[] inputs = input.split(",");
//        for (String input1 : inputs) {
//            try {
//                int index = Integer.parseInt(input1.trim()) - 1;
//                if (index >= 0 && index < currentList.size()) {
//                    chosenContacts.add(currentList.get(index));
//                }
//            } catch (NumberFormatException e) {
//                System.out.println("Invalid input: " + input1);
//            }
//        }
//
//        return chosenContacts;
//    }

    private ContactOpData encryptContact(ContactDetail contactDetail) {
        if(contactDetail==null) return null;
        
        // Clear fields that shouldn't be included in encryption
        contactDetail.setUpdateHeight(null);
        contactDetail.setId(null);

        
        ContactOpData contactOpData = new ContactOpData();
        if (!encryptContactDetail(contactOpData, contactDetail)) return null;

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
                .map(ContactDetail::getId)
                .collect(Collectors.toList());
            result = recoverContact(ids, br);
        } else {
            return false;
        }

        if (Hex.isHex32(result)) {
            System.out.println("Recovered contacts: " + (contactIds != null ? contactIds : chosenContacts.stream()
                .map(ContactDetail::getId)
                .toList()) + " in TX " + result + ".");
            if (contactIds != null) {
                localDB.removeFromMap(LocalDB.ON_CHAIN_DELETED_MAP, contactIds);
            } else {
                List<String> ids = chosenContacts.stream()
                    .map(ContactDetail::getId)
                    .collect(Collectors.toList());
                localDB.removeFromMap(LocalDB.ON_CHAIN_DELETED_MAP, ids);
            }
            return true;
        } else {
            System.out.println("Failed to recover contacts: " + (contactIds != null ? contactIds : chosenContacts.stream()
                .map(ContactDetail::getId)
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
        menu.add("Remove from local", () -> removeItems(items.stream().map(ContactDetail::getId).collect(Collectors.toList()), br));
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