package handlers;

import appTools.Inputer;
import appTools.Menu;
import appTools.Settings;
import clients.ApipClient;
import clients.Client;
import constants.Constants;
import crypto.CryptoDataByte;
import crypto.Encryptor;
import crypto.KeyTools;
import fcData.AlgorithmId;
import fcData.ContactDetail;
import fcData.FcEntity;
import feip.feipData.Contact;
import feip.feipData.ContactData;
import feip.feipData.Feip;
import feip.feipData.Service.ServiceType;
import handlers.ContactHandler.ContactOp;

import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.BytesTools;
import tools.Hex;
import tools.JsonTools;
import tools.StringTools;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NewContactHandler extends Handler<ContactDetail> {
    private static final Logger log = LoggerFactory.getLogger(NewContactHandler.class);
    public static final String FAKE_CONTACT_DETAIL = "fakeContactDetail";
    private final ApipClient apipClient;
    private final byte[] symKey;
    private final String myPriKeyCipher;
    private final byte[] myPubKey;

    public NewContactHandler(Settings settings) {
        super(settings, HandlerType.CONTACT, LocalDB.SortType.UPDATE_ORDER, FcEntity.getMapDBSerializer(ContactDetail.class), ContactDetail.class, true);
        this.apipClient = (ApipClient) settings.getClient(ServiceType.APIP);
        this.symKey = settings.getSymKey();
        this.myPriKeyCipher = settings.getMyPriKeyCipher();
        this.myPubKey = KeyTools.priKeyToPubKey(Client.decryptPriKey(myPriKeyCipher, symKey));
    }

    public void menu(BufferedReader br) {
        Menu menu = new Menu("Contact Menu");
        menu.add("Update contacts on chain", () -> checkContacts(br));
        menu.add("Add contacts on chain", () -> addContacts(br));
        menu.add("Update contacts on chain", () -> updateContacts(br));
        menu.add("Delete contacts on chain", () -> deleteContacts(br));
        addBasicMenuItems(br, menu);
        menu.showAndSelect(br);
    }

    public String addContact(BufferedReader br) {
        return opContact(null, null, br, ContactOp.ADD);
    }
    public void addContacts(BufferedReader br) {
        while (true) {
            String result = addContact(br);
            if (Hex.isHex32(result)) {
                System.out.println("Contact added successfully: " + result);
            } else {
                System.out.println("Failed to add contact: " + result);
            }
            
            if (!Inputer.askIfYes(br, "Do you want to add another contact?")) {
                break;
            }
        }
    }
    public String updateContact(String contactId, BufferedReader br) {
        return opContact(contactId, null, br, ContactOp.UPDATE);
    }

    public void updateContacts(BufferedReader br) {
        List<ContactDetail> chosenContacts = chooseItems(br);
        updateContacts(chosenContacts, br);
    }

    public void updateContacts(List<ContactDetail> chosenContacts, BufferedReader br) {
        for (ContactDetail contact : chosenContacts) {
            String result = updateContact(contact.getFid(), br);
            if (Hex.isHex32(result)) {
                System.out.println("Updated contact " + contact.getFid() + ": " + result);
            } else {
                System.out.println("Failed to update contact " + contact.getFid() + ": " + result);
            }
        }
    }

    public String deleteContact(List<String> contactIds, BufferedReader br) {
        return opContact(null, contactIds, br, ContactOp.DELETE);
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
                remove(contact.getFid());
            }

            String result = deleteContact(contactIds, br);
            if (Hex.isHex32(result)) {
                System.out.println("Deleted contacts: " + contactIds + " in TX " + result + ".");
            } else {
                System.out.println("Failed to delete contacts: " + contactIds + ": " + result);
            }
        }
    }

    public void putAll(List<ContactDetail> items) {
        Map<String, ContactDetail> contactDetailMap = new HashMap<>();
        for (ContactDetail item : items) {
            contactDetailMap.put(item.getContactId(), item);
        }
        putAll(contactDetailMap);
    }

    private String opContact(String contactId, List<String> contactIds, BufferedReader br, ContactOp op) {
        if (op == null) return null;
        ContactData contactData = new ContactData();
        contactData.setOp(op.toLowerCase());
        ContactDetail contactDetail = new ContactDetail();
        byte[] priKey = Client.decryptPriKey(myPriKeyCipher, symKey);
        if (priKey == null) {
            System.out.println("Failed to get the priKey of " + mainFid);
            return null;
        }

        if (op.equals(ContactOp.ADD) || op.equals(ContactOp.UPDATE)) {
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
            contactData.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7.getDisplayName());
            if (op.equals(ContactOp.UPDATE)) contactData.setContactId(contactId);
            byte[] b = cryptoDataByte.toBundle();
            String cipher = Base64.getEncoder().encodeToString(b);
            contactData.setCipher(cipher);
        } else if (op.equals(ContactOp.DELETE)) {
            if (contactIds == null) return null;
            contactData.setContactIds(contactIds);
        } else return null;

        Feip feip = getFeip();
        feip.setData(contactData);

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

    public void checkContacts(BufferedReader br) {
        long lastHeight = (long) getMeta("lastHeight");
        List<Contact> contactList = loadAllContactList(lastHeight, true);
        List<ContactDetail> contactDetailList = contactToContactDetail(contactList);

        if (!contactList.isEmpty()) {
            putMeta("lastHeight", contactList.get(0).getBirthHeight());
            deleteUnreadableContacts(br);
        }

        System.out.println("You have " + contactDetailList.size() + " updated contacts.");
        if (!contactDetailList.isEmpty()) {
            showItemList("Updated Contacts", contactDetailList, 0);
        }
    }

    private List<Contact> loadAllContactList(Long lastHeight, Boolean active) {
        List<Contact> contactList = new ArrayList<>();
        List<String> last = new ArrayList<>();
        while (true) {
            List<Contact> subContactList = apipClient.freshContactSinceHeight(mainFid, lastHeight, ApipClient.DEFAULT_SIZE, last, active);
            if (subContactList == null || subContactList.isEmpty()) break;
            contactList.addAll(subContactList);
            if (subContactList.size() < ApipClient.DEFAULT_SIZE) break;
        }
        return contactList;
    }

    private List<ContactDetail> contactToContactDetail(List<Contact> contactList) {
        List<ContactDetail> contactDetailList = new ArrayList<>();
        byte[] priKey = Client.decryptPriKey(myPriKeyCipher, symKey);
        for (Contact contact : contactList) {
            ContactDetail contactDetail = ContactDetail.fromContact(contact, priKey, apipClient);
            if (contactDetail == null) {
                putInMap(FAKE_CONTACT_DETAIL, contact.getContactId(), contact.getContactId(), org.mapdb.Serializer.STRING);
                continue;
            }
            put(contactDetail.getFid(), contactDetail);
            contactDetailList.add(contactDetail);
        }
        BytesTools.clearByteArray(priKey);
        return contactDetailList;
    }
    @Override   
    public void opItems(List<ContactDetail> items, String ask, BufferedReader br) {
        String op = Inputer.chooseOne(new String[]{"Show details","Remove on chain", "Update on chain"}, null, ask, br);
        if (op == null) return;
        switch (op) {
            case "Show details":
                showItemsDetails(items, br);
                break;
            case "Remove on chain":
                deleteContacts(items, br);
                break;
            case "Recover on chain":
                updateContacts(items, br);
                break;
        }
    }
    private void deleteUnreadableContacts(BufferedReader br) {
        Map<String, String> fakeContacts = getAllFromMap(FAKE_CONTACT_DETAIL, Serializer.STRING);
        if (fakeContacts == null || fakeContacts.isEmpty()) return;
        
        if (!Inputer.askIfYes(br, "Got " + fakeContacts.size() + " unreadable contacts. Delete them?")) return;
        
        String result = opContact(null, new ArrayList<>(fakeContacts.keySet()), br, ContactOp.DELETE);
        if (Hex.isHex32(result)) {
            System.out.println(fakeContacts.size() + " unreadable contacts deleted in TX " + result + ".");
            clearMap(FAKE_CONTACT_DETAIL);
        } else {
            System.out.println("Failed to delete unreadable contacts: " + result);
        }
    }

    public Feip getFeip() {
        return Feip.fromProtocolName(Feip.ProtocolName.CONTACT);
    }
} 