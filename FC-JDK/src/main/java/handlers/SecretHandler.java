package handlers;

import ui.Inputer;
import ui.Menu;
import config.Settings;
import ui.Shower;
import constants.Constants;
import core.crypto.CryptoDataByte;
import core.crypto.Encryptor;
import core.crypto.KeyTools;
import db.LocalDB;
import data.fcData.AlgorithmId;
import data.fcData.SecretDetail;
import data.feipData.Feip;
import data.feipData.Secret;
import data.feipData.SecretOpData;
import data.feipData.Service;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import utils.DateUtils;
import utils.Hex;
import utils.JsonUtils;

import java.io.BufferedReader;
import java.util.*;
import java.util.stream.Collectors;

import static ui.Shower.DEFAULT_PAGE_SIZE;
import static constants.FieldNames.*;
import static constants.Strings.SECRET;
import static ui.Shower.showAndChooseFromStringLongMap;

public class SecretHandler extends Handler<SecretDetail> {
    public static String name = HandlerType.SECRET.name();
    public static final Object[] modules = new Object[]{
            Service.ServiceType.APIP,
            Handler.HandlerType.CASH
    };
    private final byte[] myPubKey;
    private Map<String,String> fakeSecretCipherMap;

    public SecretHandler(Settings settings) {
        super(settings, HandlerType.SECRET, LocalDB.SortType.UPDATE_ORDER, SecretDetail.class, true, true);
        this.myPubKey = KeyTools.prikeyToPubkey(prikey);
        hideFieldsInListing = List.of(CONTENT);
    }

    @Override
    public void menu(BufferedReader br, boolean isRootMenu) {
        Menu menu = newMenu("Secret",isRootMenu);
        addBasicMenuItems(br,menu);
        menu.add("List Locally Removed Secrets", () -> reloadRemovedItems(br));
        menu.add("Clear Locally Removed Records", () -> clearAllLocallyRemoved(br));
        menu.add("Check Secrets on Chain", () -> freshOnChainSecrets(br));
        menu.add("Add Secrets on Chain", () -> addSecrets(br));
        menu.add("Delete Secrets on Chain", () -> deleteSecrets(br));
        menu.add("List deleted Secrets on Chain", () -> recoverSecrets(br));
        menu.add("Clear on Chain Deleted Records", () -> clearDeletedRecord(br));
        menu.add("Test Methods", () -> testMethods(br));
        if(cashHandler!=null)menu.add("Manage Cash", () -> cashHandler.menu(br, false));
        if(isRootMenu)
            menu.add("Settings", () -> settings.setting(br, null));

        menu.showAndSelect(br);
    }

//    @Nullable
//    public static List<String> listAndChooseFromStringLongMap(BufferedReader br, Map<String, Long> removedItems, String ask) {
//        if (removedItems == null || removedItems.isEmpty()) {
//            System.out.println("No locally removed items found.");
//            return null;
//        }
//
//        // Show items and let user choose
//        Map<String, Long> selectedDisplayItems = Inputer.chooseMultiFromMapGeneric(
//                removedItems,
//                null,
//                0,
//                Shower.DEFAULT_SIZE,
//                ask,
//                br
//        );
//
//        if (selectedDisplayItems == null || selectedDisplayItems.isEmpty()) {
//            return null;
//        }
//
//        // Extract IDs from selected display strings
//        return selectedDisplayItems.keySet().stream().toList();
//    }

    protected void reloadRemovedItems(BufferedReader br) {
        // Get all locally removed items
        Map<String, Long> removedItems = localDB.getAllFromMap(LocalDB.LOCAL_REMOVED_MAP);

        List<String> chosenIds = showAndChooseFromStringLongMap(br,removedItems,"Choose to reload:");

        Map<String, Secret> items = reloadSecretsFromChain(br, chosenIds);

        if(items.isEmpty()) System.out.println("No item reloaded.");
        else System.out.println("Successfully reloaded " + items.size() + " items.");
        }

    @NotNull
    public Map<String, Secret> reloadSecretsFromChain(BufferedReader br, List<String> selectedIds) {
        Map<String,Secret> items = apipClient.loadOnChainItemByIds(SECRET, Secret.class, selectedIds);

        List<Secret> reloadedList = new ArrayList<>();
        if(items==null|| items.isEmpty())return new HashMap<>();

        List<Secret> chosenDeletedSecretList;
        List<Secret> deletedSecretList = new ArrayList<>();
        boolean recovered;
        for(Secret item:items.values()){
            if(!item.isActive()) {
                deletedSecretList.add(item);
            }else{
                reloadedList.add(item);
            }
        }
        if(!deletedSecretList.isEmpty()){
            if(Inputer.askIfYes(br, "There are " + deletedSecretList.size() + " on chain deleted secrets. Choose to recover them?")){
                chosenDeletedSecretList = Shower.showOrChooseListInPages("Deleted secrets",deletedSecretList,DEFAULT_PAGE_SIZE, null, true, Secret.class,br);
                if(chosenDeletedSecretList!=null){
                    recovered = recoverSecrets(chosenDeletedSecretList.stream().map(Secret::getId).collect(Collectors.toList()), null, br);
                    if(recovered) {
                        reloadedList.addAll(chosenDeletedSecretList);
                    }
                }
            }
        }
        if(reloadedList.isEmpty())return new HashMap<>();

        List<SecretDetail> secretDetailList = secretToSecretDetail(reloadedList, false);
        if(secretDetailList.isEmpty())return new HashMap<>();

        putAllSecretDetail(secretDetailList);

        List<String> reloadedIdList = reloadedList.stream().map(Secret::getId).collect(Collectors.toList());
        // Remove from locally removed tracking
        localDB.removeFromMap(LocalDB.LOCAL_REMOVED_MAP, reloadedIdList);

        handleFakeSecretData(br);

        return items;
    }

    private void clearDeletedRecord(BufferedReader br) {
        if(Inputer.askIfYes(br, "Are you sure you want to clear all on chain deleting records?")){
            clearAllOnChainDeleted();
            System.out.println("All on chain deleted records cleared.");
        }
    }

    private void clearAllLocallyRemoved(BufferedReader br) {
        if(Inputer.askIfYes(br, "Are you sure you want to clear all on chain deleting records?")){
            clearAllLocallyRemoved();
            System.out.println("All local removed records cleared.");
        }
    }

    public void putSecret(String id, SecretDetail secret) {
            localDB.put(id, secret);
        }

    public SecretDetail getSecret(String id) {
        return localDB.get(id);
    }

    public Map<String, SecretDetail> getAllSecrets() {
        return localDB.getAll();
    }

    public NavigableMap<Long, String> getSecretIndexIdMap() {
        return localDB.getIndexIdMap();
    }

    public NavigableMap<String, Long> getSecretIdIndexMap() {
        return localDB.getIdIndexMap();
    }

    public SecretDetail getSecretById(String id) {
        return localDB.get(id);
    }

    public SecretDetail getSecretByIndex(long index) {
        return localDB.getByIndex(index);
    }

    public Long getSecretIndexById(String id) {
        return localDB.getIndexById(id);
    }

    public String getSecretIdByIndex(long index) {
        return localDB.getIdByIndex(index);
    }


    public List<SecretDetail> getSecretList(Integer size, Long fromIndex, String fromId,
            boolean isFromInclude, Long toIndex, String toId, boolean isToInclude, boolean isFromEnd) {
        return localDB.getList(size, fromId, fromIndex, isFromInclude, toId, toIndex, isToInclude, isFromEnd);
    }

    public LinkedHashMap<String, SecretDetail> getSecretMap(int size, Long fromIndex, String fromId,
            boolean isFromInclude, Long toIndex, String toId, boolean isToInclude, boolean isFromEnd) {
        return localDB.getMap(size, fromId,fromIndex,  isFromInclude, toId, toIndex, isToInclude, isFromEnd);
    }

    public void removeSecret(String id) {
        remove(id);
    }

    public void removeSecrets(List<String> ids) {
        remove(ids);
    }

    public void clearSecrets() {
        clear();
    }

    public List<SecretDetail> searchSecrets(String searchString) {
        return searchInValue(searchString);
    }

    public List<SecretDetail> searchSecrets(BufferedReader br, boolean withChoose, boolean withOperation) {
        return searchItems(br, withChoose, withOperation);
    }

    public void opOnChain(List<SecretDetail> chosenSecrets, String ask, BufferedReader br) {
        SecretOpData.Op op = null;
        String opStr = Inputer.chooseOne(
            Arrays.stream(SecretOpData.Op.values())
                  .map(SecretOpData.Op::toLowerCase)
                  .toArray(String[]::new),
            null,
            ask,
            br
        );
        if (opStr != null) {
            for (SecretOpData.Op value : SecretOpData.Op.values()) {
                if (value.name().equalsIgnoreCase(opStr)) {
                    op = value;
                    break;
                }
            }
        }
        if (op == null) return;

        switch (op) {
            case ADD -> addSecrets(chosenSecrets, br);
            case DELETE -> deleteSecrets(chosenSecrets, br);
            case RECOVER -> recoverSecrets(null, chosenSecrets, br);
        }
    }

    public String addSecret(BufferedReader br) {
        return opSecret(null, null, SecretOpData.Op.ADD, br);
    }

    public void addSecrets(List<SecretDetail> itemList,@Nullable BufferedReader br) {
        if(itemList == null && br != null) addSecrets(br);
        else{
            if(itemList==null || itemList.isEmpty())return;
            for(SecretDetail item:itemList){
                SecretOpData secretOpData = encryptSecret(item);
                carveSecretData(secretOpData, br);
                if(br!=null && !Inputer.askIfYes(br,"Carve next?"))break;
            }
        }
    }
    public void addSecrets(BufferedReader br) {
        do {
            String result = addSecret(br);
            if (Hex.isHex32(result)) {
                System.out.println("Secret added successfully: " + result);
            } else {
                System.out.println("Failed to add secret: " + result);
            }
        } while (Inputer.askIfYes(br, "Do you want to add another secret?"));
    }

    public String deleteSecret(List<String> secretIds, BufferedReader br) {
        return opSecret(secretIds, null, SecretOpData.Op.DELETE, br);
    }

    public void deleteSecrets(BufferedReader br) {
        if (dbEmpty()) return;
        List<SecretDetail> chosenSecrets = chooseItems(br);
        deleteSecrets(chosenSecrets, br);
    }

    public void deleteSecrets(List<SecretDetail> chosenSecrets, BufferedReader br) {
        if (chosenSecrets.isEmpty()) {
            System.out.println("No secrets chosen for deletion.");
            return;
        }

        if (Inputer.askIfYes(br, "View them before delete?")) {
            Shower.showOrChooseListInPages("Chosen Secrets", chosenSecrets, DEFAULT_PAGE_SIZE, null, true, SecretDetail.class, br);
        }

        if (Inputer.askIfYes(br, "Delete " + chosenSecrets.size() + " secrets?")) {
            List<String> secretIds = new ArrayList<>();
            for (SecretDetail secret : chosenSecrets) {
                secretIds.add(secret.getId());
            }

            String result = deleteSecret(secretIds, br);
            if (Hex.isHex32(result)) {
                System.out.println("Deleted secrets: " + secretIds + " in TX " + result + ".");
                markAsOnChainDeleted(secretIds);
                remove(secretIds);
            } else {
                System.out.println("Failed to delete secrets: " + secretIds + ": " + result);
            }
        }
    }

    public String recoverSecret(List<String> secretIds, BufferedReader br) {
        return opSecret(secretIds, null, SecretOpData.Op.RECOVER, br);
    }

    public void recoverSecrets(BufferedReader br) {
        Map<String, Long> deletedIds = getAllOnChainDeletedRecords();
        List<String> chosenIds;
        int count = 0;
        Map<String, Secret> localDeletedSecrets = new HashMap<>();

        if(deletedIds!=null && !deletedIds.isEmpty()) {
            System.out.println("There are "+deletedIds.size()+" on chain deleted record in local DB.");
            chosenIds = showAndChooseFromStringLongMap(br,deletedIds,"Choose items to recover:" );
            if(chosenIds!=null && !chosenIds.isEmpty()) {
                localDeletedSecrets = reloadSecretsFromChain(br, chosenIds);
                count += localDeletedSecrets.size();
            }
        }else System.out.println("No local records of on chain deleted secrets.");

        if(!Inputer.askIfYes(br,"Check more deleted secrets from blockchain?"))return;

        List<Secret> onChainDeleted = loadAllOnChainItems(SECRET, LAST_HEIGHT,OWNER,0L, false,apipClient,Secret.class, br, false);
        if(onChainDeleted.isEmpty()){
            System.out.println("No deleted items on chain.");
        }

        Iterator<Secret> iterator = onChainDeleted.iterator();
        removeDeleted(deletedIds, iterator);

        List<Secret> finalChosenDeleted = new ArrayList<>();
        finalChosenDeleted.addAll(onChainDeleted);
        finalChosenDeleted.addAll(localDeletedSecrets.values());

        List<SecretDetail> secretDetailList = secretToSecretDetail(finalChosenDeleted, false);

        List<SecretDetail> chosenSecrets = chooseSecretDetailList(secretDetailList, 0, br);

        recoverSecrets(null, chosenSecrets, br);

        if(chosenSecrets!=null && !chosenSecrets.isEmpty()) {
            putAllSecretDetail(chosenSecrets);
            count += chosenSecrets.size();
        }

        System.out.println(count + " items recovered.");

        handleFakeSecretData(br);
    }

    private boolean recoverSecrets(@Nullable List<String> secretIds, List<SecretDetail> chosenSecrets, BufferedReader br) {
        String result;
        if(secretIds!=null && !secretIds.isEmpty() && Inputer.askIfYes(br, "Recover " + secretIds.size() + " secrets?")){
            result = recoverSecret(secretIds, br);
        }else if (chosenSecrets!=null && Inputer.askIfYes(br, "Recover " + chosenSecrets.size() + " secrets?")) {
            result = recoverSecret(chosenSecrets.stream().map(SecretDetail::getId).collect(Collectors.toList()), br);
        }else return false;

        if (Hex.isHex32(result)) {
            System.out.println("Recovered secrets: " + secretIds + " in TX " + result + ".");
            localDB.removeFromMap(LocalDB.ON_CHAIN_DELETED_MAP, secretIds);
            return true;
        } else {
            System.out.println("Failed to recover secrets: " + secretIds + ": " + result);
            return false;
        }

    }

    private void handleFakeSecretData(BufferedReader br) {
        if (!fakeSecretCipherMap.isEmpty()) {
            if (Inputer.askIfYes(br, "Got " + fakeSecretCipherMap.size() + " unreadable secrets. Check them?")) {
                for (Map.Entry<String, String> entry : fakeSecretCipherMap.entrySet()) {
                    System.out.println(entry.getKey() + ": " + entry.getValue());
                }
            }
            deleteUnreadableSecrets(br);
            fakeSecretCipherMap.clear();
        }
    }

    public void freshOnChainSecrets(BufferedReader br) {
        if(apipClient==null){
            System.out.println("Unable to update on chain data due to the absence of ApipClient.");
            return;
        }
        Object lastHeightObj = localDB.getState(LAST_HEIGHT);
        long lastHeight;
        if(lastHeightObj==null) lastHeight = 0;
        else  {
            if(lastHeightObj instanceof String) lastHeight = Long.parseLong((String)lastHeightObj);
            else lastHeight = ((Number)lastHeightObj).longValue();
        }
        List<Secret> secretList = loadAllOnChainItems(SECRET, LAST_HEIGHT,OWNER,lastHeight, true,apipClient,Secret.class, null, true);
        List<SecretDetail> secretDetailList;
        if (secretList!=null && !secretList.isEmpty()) {
            secretDetailList = secretToSecretDetail(secretList, true);

            putAllSecretDetail(secretDetailList);

            System.out.println("You have " + secretDetailList.size() + " updated secrets.");
            if (secretDetailList.size() > 0) chooseToShowNiceJsonList(secretDetailList, br);

            handleFakeSecretData(br);

            Menu.anyKeyToContinue(br);
        }else System.out.println("No secrets updated.");
    }

    public void putAllSecretDetail(List<SecretDetail> secretDetailList) {
        localDB.putAll(secretDetailList,ID);
    }

    private void deleteUnreadableSecrets(BufferedReader br) {
        if (fakeSecretCipherMap.isEmpty()) return;
        if (!Inputer.askIfYes(br, "There are " + fakeSecretCipherMap.size() + " unreadable secrets. Delete them?")) return;
        String result = opSecret(new ArrayList<>(fakeSecretCipherMap.keySet()), null, SecretOpData.Op.DELETE, br);
        if (Hex.isHex32(result)) {
            fakeSecretCipherMap.clear();
        } else {
            System.out.println("Failed to delete unreadable secrets: " + result);
        }
    }

    @Override
    public void opItems(List<SecretDetail> items, String ask, BufferedReader br) {
        Menu menu = new Menu("Secret Operations", () -> {});
        menu.add("Show details", () -> showItemDetails(items, br));
        menu.add("Remove from local", () -> removeItems(items.stream().map(SecretDetail::getId).collect(Collectors.toList()),br));
        menu.add("Delete on chain", () -> deleteSecrets(items, br));
        menu.add("Recover on chain", () -> recoverSecrets(null, items, br));
        menu.add("Add to chain", () -> addSecrets(items, br));
        menu.showAndSelect(br);
    }

    private String opSecret(List<String> secretIds, SecretDetail secretDetail, SecretOpData.Op op, BufferedReader br) {
        if (op == null) return null;

        SecretOpData secretOpData = new SecretOpData();

        if (prikey == null) {
            System.out.println("Failed to get the priKey of " + mainFid);
            return null;
        }

        if (op == SecretOpData.Op.ADD) {
            if(secretDetail == null && br != null){
                secretDetail = new SecretDetail();
                secretDetail.setType(Inputer.inputString(br, "Input type:"));
                secretDetail.setTitle(Inputer.inputString(br, "Input title:"));
                secretDetail.setContent(Inputer.inputString(br, "Input content:"));
                secretDetail.setMemo(Inputer.inputString(br, "Input memo:"));
            }
            secretOpData = encryptSecret(secretDetail);
            if(secretOpData == null) return null;
        } else {
            if (secretIds == null) {
                secretIds = Inputer.inputStringList(br, "Input the secret IDs:", 0);
                if(secretIds.isEmpty()) return null;
            }
            secretOpData.setSecretIds(secretIds);
        }

        secretOpData.setOp(op.toLowerCase());

        return carveSecretData(secretOpData, br);
    }

    @Nullable
    private String carveSecretData(SecretOpData secretOpData, BufferedReader br) {
        Feip feip = getFeip();
        feip.setData(secretOpData);

        String opReturnStr = feip.toJson();

        long cd = Constants.CD_REQUIRED;

        return carve(opReturnStr, cd, br);
    }

    private SecretOpData encryptSecret(SecretDetail secretDetail) {
        if(secretDetail==null)return null;
        secretDetail.setUpdateHeight(null);
        secretDetail.setId(null);
        secretDetail.setId(null);
        SecretOpData secretOpData = new SecretOpData();
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = encryptor.encryptByAsyOneWay(JsonUtils.toJson(secretDetail).getBytes(), myPubKey);
        if (cryptoDataByte.getCode() != 0) {
            log.error("Failed to encrypt.");
            return null;
        }
        secretOpData.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7.getDisplayName());
        byte[] b = cryptoDataByte.toBundle();
        String cipher = Base64.getEncoder().encodeToString(b);
        secretOpData.setCipher(cipher);
        return secretOpData;
    }

    private List<SecretDetail> secretToSecretDetail(List<Secret> secretList, boolean ignoreBadCipher) {
        List<SecretDetail> secretDetailList = new ArrayList<>();
        fakeSecretCipherMap = new HashMap<>();

        for (Secret secret : secretList) {
            SecretDetail secretDetail = SecretDetail.fromSecret(secret, prikey);
            if (secretDetail == null) {
                fakeSecretCipherMap.put(secret.getId(), DateUtils.longToTime(secret.getBirthTime(), DateUtils.LONG_FORMAT)+" "+secret.getCipher());
                if (!ignoreBadCipher) {
                    secretDetail = new SecretDetail();
                    secretDetail.setId(secret.getId());
                    secretDetail.setTitle("Bad cipher: " + secret.getCipher());
                }
                continue;
            }
            secretDetailList.add(secretDetail);
        }

        return secretDetailList;
    }
    private List<SecretDetail> chooseSecretDetailList(List<SecretDetail> currentList,
                                                      int totalDisplayed, BufferedReader br) {
        List<SecretDetail> chosenSecrets = new ArrayList<>();

        String title = "Choose Secrets";
        SecretDetail.showSecretDetailList(currentList, title, totalDisplayed);

        System.out.println("Enter secret numbers to select (comma-separated), 'a' for all. 'q' to quit, or press Enter for more:");
        String input = Inputer.inputString(br);

        if ("".equals(input)) {
            return null;  // Signal to continue to next page
        }
        if("a".equals(input)) {
            return currentList;  // Signal to continue to next page
        }
        if (input.equals("q")) {
            chosenSecrets.add(null);  // Signal to break the loop
            return chosenSecrets;
        }
        String[] inputs = input.split(",");
        for (String input1 : inputs) {
            try {
                int index = Integer.parseInt(input1.trim()) - 1;
                if (index >= 0 && index < currentList.size()) {
                    chosenSecrets.add(currentList.get(index));  
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input: " + input1);
            }
        }

        return chosenSecrets;
    }

    public Feip getFeip() {
        return Feip.fromProtocolName(Feip.ProtocolName.SECRET);
    }

    private void testMethods(BufferedReader br) {
        System.out.println("Testing various methods...");

        // Test basic CRUD operations
        SecretDetail testSecret = new SecretDetail();
        testSecret.setTitle("Test Secret");
        testSecret.setContent("Test Content");
        testSecret.setType("Test Type");
        String testId = "test-" + System.currentTimeMillis();

        // Test individual put/get
        System.out.println("\nTesting put/get...");
        putSecret(testId, testSecret);
        SecretDetail retrieved = getSecret(testId);
        System.out.println("Put and retrieved secret matches: " + 
            (retrieved != null && retrieved.getTitle().equals(testSecret.getTitle())));

        // Test index-based operations
        System.out.println("\nTesting index operations...");
        Long index = getSecretIndexById(testId);
        String idByIndex = getSecretIdByIndex(index);
        System.out.println("Index lookup consistency: " + testId.equals(idByIndex));

        // Test map operations
        System.out.println("\nTesting map operations...");
        NavigableMap<Long, String> indexIdMap = getSecretIndexIdMap();
        NavigableMap<String, Long> idIndexMap = getSecretIdIndexMap();
        System.out.println("Index maps size: " + indexIdMap.size() + "/" + idIndexMap.size());

        // Test search functionality
        System.out.println("\nTesting search...");
        List<SecretDetail> searchResults = searchSecrets("Test");
        System.out.println("Search results found: " + searchResults.size());

        // Test list operations with various parameters
        System.out.println("\nTesting list operations...");
        List<SecretDetail> secretList = getSecretList(10, null, null, true, null, null, true, false);
        System.out.println("Retrieved list size: " + (secretList != null ? secretList.size() : 0));

        // Test map retrieval with parameters
        System.out.println("\nTesting map retrieval...");
        LinkedHashMap<String, SecretDetail> secretMap = getSecretMap(10, null, null, true, null, null, true, false);
        System.out.println("Retrieved map size: " + (secretMap != null ? secretMap.size() : 0));

        // Test removal tracking
        System.out.println("\nTesting removal tracking...");
        markAsLocallyRemoved(Arrays.asList(testId));
        Long removalTime = getLocalRemovalTime(testId);
        System.out.println("Local removal time recorded: " + (removalTime != null));

        markAsOnChainDeleted(Arrays.asList(testId));
        Long deletionTime = getOnChainDeletionTime(testId);
        System.out.println("On-chain deletion time recorded: " + (deletionTime != null));

        // Test map operations from Handler
        System.out.println("\nTesting Handler map operations...");
        String testMapName = "test_map";
        createMap(testMapName);
        localDB.putInMap(testMapName, "test_key", "test_value");
        String retrievedValue = localDB.getFromMap(testMapName, "test_key");
        System.out.println("Map operation successful: " + "test_value".equals(retrievedValue));

        // Clean up test data
        System.out.println("\nCleaning up test data...");
        removeSecret(testId);
        localDB.clearMap(testMapName);

        // Get all map names
        System.out.println("\nAvailable maps:");
        Set<String> mapNames = getMapNames();
        mapNames.forEach(System.out::println);

        System.out.println("\nMethod testing completed.");
    }
} 