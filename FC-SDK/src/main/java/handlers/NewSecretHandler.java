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
import fcData.FcEntity;
import fcData.SecretDetail;
import feip.feipData.Secret;
import feip.feipData.SecretData;
import feip.feipData.Service.ServiceType;
import handlers.SecretHandler.SecretOp;
import feip.feipData.Feip;

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
import java.util.stream.Collectors;

public class NewSecretHandler extends Handler<SecretDetail> {
    private static final Logger log = LoggerFactory.getLogger(NewSecretHandler.class);
    public static final String FAKE_SECRET_DETAIL = "fakeSecretDetail";
    private final ApipClient apipClient;
    private final byte[] symKey;
    private final String myPriKeyCipher;
    private final byte[] myPubKey;

    public NewSecretHandler(Settings settings) {
        super(settings, HandlerType.SECRETE, LocalDB.SortType.UPDATE_ORDER, FcEntity.getMapDBSerializer(SecretDetail.class), SecretDetail.class, true);
        this.apipClient = (ApipClient) settings.getClient(ServiceType.APIP);
        this.symKey = settings.getSymKey();
        this.myPriKeyCipher = settings.getMyPriKeyCipher();
        this.myPubKey = KeyTools.priKeyToPubKey(Client.decryptPriKey(myPriKeyCipher, symKey));
    }

    @Override
    public void menu(BufferedReader br) {
        Menu menu = new Menu("Secret Menu");
        menu.add("Check Secrets On Chain", () -> updateSecrets(br));
        menu.add("Add Secrets On Chain", () -> addSecrets(br));
        menu.add("Delete Secrets On Chain", () -> deleteSecrets(br));
        menu.add("Recover Secrets On Chain", () -> recoverSecrets(br));
        menu.showAndSelect(br);
    }

    public void opOnChain(List<SecretDetail> chosenSecrets, String ask, BufferedReader br) {
        SecretOp op = Inputer.chooseOne(SecretOp.values(), null, ask, br);
        if (op == null) return;
        switch (op) {
            case ADD:
                addSecrets(br);
                break;
            case DELETE:
                deleteSecrets(chosenSecrets, br);
                break;
            case RECOVER:
                recoverSecrets(chosenSecrets, br);
                break;
        }
    }

    public String addSecret(BufferedReader br) {
        return opSecret(null, br, SecretOp.ADD);
    }
    public void addSecrets(BufferedReader br) {
        while (true) {
            String result = addSecret(br);
            if (Hex.isHex32(result)) {
                System.out.println("Secret added successfully: " + result);
            } else {
                System.out.println("Failed to add secret: " + result);
            }

            if (!Inputer.askIfYes(br, "Do you want to add another secret?")) {
                break;
            }
        }
    }

    public String deleteSecret(List<String> secretIds, BufferedReader br) {
        return opSecret(secretIds, br, SecretOp.DELETE);
    }

    public void deleteSecrets(BufferedReader br) {
        List<SecretDetail> chosenSecrets = chooseItems(br);
        deleteSecrets(chosenSecrets, br);
    }

    public void deleteSecrets(List<SecretDetail> chosenSecrets, BufferedReader br) {
        if (chosenSecrets.isEmpty()) {
            System.out.println("No secrets chosen for deletion.");
            return;
        }

        if (Inputer.askIfYes(br, "View them before delete?")) {
            showItemList("Secrets", chosenSecrets, 0);
        }

        if (Inputer.askIfYes(br, "Delete " + chosenSecrets.size() + " secrets?")) {
            List<String> secretIds = new ArrayList<>();
            for (SecretDetail secret : chosenSecrets) {
                secretIds.add(secret.getSecretId());
                remove(secret.getTitle());
            }

            String result = deleteSecret(secretIds, br);
            if (Hex.isHex32(result)) {
                System.out.println("Deleted secrets: " + secretIds + " in TX " + result + ".");
            } else {
                System.out.println("Failed to delete secrets: " + secretIds + ": " + result);
            }
        }
    }

    public String recoverSecret(List<String> secretIds, BufferedReader br) {
        return opSecret(secretIds, br, SecretOp.RECOVER);
    }

    public void recoverSecrets(BufferedReader br) {
        List<Secret> secretList = loadAllSecretList(0L, false);
        List<SecretDetail> secretDetailList = secretToSecretDetail(secretList, false);
        List<SecretDetail> chosenSecrets = chooseSecretDetailList(secretDetailList, new ArrayList<>(), 0, br);
        if (chosenSecrets.isEmpty()) {
            System.out.println("No secrets chosen for recovery.");
            return;
        }
        recoverSecrets(chosenSecrets, br);
    }

    public void recoverSecrets(List<SecretDetail> chosenSecrets, BufferedReader br) {
        if (Inputer.askIfYes(br, "Recover " + chosenSecrets.size() + " secrets?")) {
            String result = recoverSecret(chosenSecrets.stream().map(SecretDetail::getSecretId).collect(Collectors.toList()), br);
            if (Hex.isHex32(result)) {
                System.out.println("Recovered secrets: " + chosenSecrets.stream().map(SecretDetail::getSecretId).collect(Collectors.toList()) + " in TX " + result + ".");
            } else {
                System.out.println("Failed to recover secrets: " + chosenSecrets.stream().map(SecretDetail::getSecretId).collect(Collectors.toList()) + ": " + result);
            }
        }
    }

    public void updateSecrets(BufferedReader br) {
        long lastHeight = (long) getMeta("lastHeight");
        List<Secret> secretList = loadAllSecretList(lastHeight, true);
        List<SecretDetail> secretDetailList = secretToSecretDetail(secretList, true);

        if (!secretList.isEmpty()) {
            putMeta("lastHeight", secretList.get(0).getBirthHeight());
            deleteUnreadableSecrets(br);
            putAll(secretDetailList);
        }

        System.out.println("You have " + secretDetailList.size() + " updated secrets.");
        if (secretDetailList.size() > 0) chooseToShow(secretDetailList, br);
    }

    public void putAll(List<SecretDetail> items) {
        Map<String, SecretDetail> secretDetailMap = new HashMap<>();
        for (SecretDetail item : items) {
            secretDetailMap.put(item.getSecretId(), item);
        }
        putAll(secretDetailMap);
    }

    private void deleteUnreadableSecrets(BufferedReader br) {
       Map<String, SecretDetail> fakeSecretDetails = getAllFromMap(FAKE_SECRET_DETAIL, valueSerializer);
        if (fakeSecretDetails.isEmpty()) return;
        if (!Inputer.askIfYes(br, "Got " + fakeSecretDetails.size() + " unreadable secrets. Delete them?")) return;
        String result = opSecret(new ArrayList<>(fakeSecretDetails.keySet()), br, SecretOp.DELETE);
        if (Hex.isHex32(result)) {
            clearMap(FAKE_SECRET_DETAIL);
        } else {
            System.out.println("Failed to delete unreadable secrets: " + result);
        }
    }
//
//    public void readSecrets(BufferedReader br) {
//        List<SecretDetail> chosenSecrets = null;
//
//        String input;
//        while (true) {
//            input = Inputer.inputString(br, "Input search string or secret ID. 'q' to quit. Enter to list all secrets and choose some:");
//            if ("q".equals(input)) return;
//            if ("".equals(input)) {
//                chosenSecrets = showItems(HandlerType.SECRETE.toString(), null, br, true, true);
//                if (chosenSecrets.isEmpty()) {
//                    return;
//                }
//            } else {
//                SecretDetail secretDetail = get(input);
//                if (secretDetail != null) {
//                    chosenSecrets = new ArrayList<>();
//                    chosenSecrets.add(secretDetail);
//                } else {
//                    List<String> foundSecretIds = searchInValue(input);
//                    chosenSecrets = getFromMap(HandlerType.SECRETE.toString(), foundSecretIds);
//                }
//                if (chosenSecrets.isEmpty()) return;
//            }
//
//            System.out.println("You chosen " + chosenSecrets.size() + " secrets.");
//
//            String op = Inputer.chooseOne(
//                    new String[]{"Read", "Delete", "Recover"},
//                    null, "Select to operate the secrets:", br);
//
//            switch (op) {
//                case "Read":
//                    chooseToShow(chosenSecrets, br);
//                    break;
//                case "Delete":
//                    deleteSecrets(chosenSecrets, br);
//                    break;
//                case "Recover":
//                    recoverSecrets(chosenSecrets, br);
//                    break;
//                default:
//                    break;
//            }
//        }
//    }

    @Override   
    public void opItems(List<SecretDetail> items, String ask, BufferedReader br) {
        String op = Inputer.chooseOne(new String[]{"Show details","Remove on chain", "Recover on chain"}, null, ask, br);
        if (op == null) return;
        switch (op) {
            case "Show details":
                showItemsDetails(items, br);
                break;
            case "Remove on chain":
                deleteSecrets(items, br);
                break;
            case "Recover on chain":
                recoverSecrets(items, br);
                break;
        }
    }

        private String opSecret(List<String> secretIds, BufferedReader br, SecretOp op) {
        if (op == null) return null;
        SecretData secretData = new SecretData();
        secretData.setOp(op.toLowerCase());
        SecretDetail secretDetail = new SecretDetail();
        byte[] priKey = Client.decryptPriKey(myPriKeyCipher, symKey);
        if (priKey == null) {
            System.out.println("Failed to get the priKey of " + mainFid);
            return null;
        }

        if (op.equals(SecretOp.ADD)) {
            secretDetail.setType(Inputer.inputString(br, "Input type:"));
            secretDetail.setTitle(Inputer.inputString(br, "Input title:"));
            secretDetail.setContent(Inputer.inputString(br, "Input content:"));
            secretDetail.setMemo(Inputer.inputString(br, "Input memo:"));

            Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
            CryptoDataByte cryptoDataByte = encryptor.encryptByAsyOneWay(JsonTools.toJson(secretDetail).getBytes(), myPubKey);
            if (cryptoDataByte.getCode() != 0) {
                log.error("Failed to encrypt.");
                return null;
            }
            secretData.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7.getDisplayName());
            byte[] b = cryptoDataByte.toBundle();
            String cipher = Base64.getEncoder().encodeToString(b);
            secretData.setCipher(cipher);
        } else {
            if (secretIds == null) return null;
            secretData.setSecretIds(secretIds);
        }

        Feip feip = getFeip();
        feip.setData(secretData);

        String opReturnStr = feip.toJson();
        long cd = Constants.CD_REQUIRED;

        if (Inputer.askIfYes(br, "Are you sure to do below operation on chain?\n" + feip.toNiceJson() + "\n")) {
            String result = CashHandler.carve(opReturnStr, cd, priKey, apipClient);
            if (Hex.isHex32(result)) {
                System.out.println("The secrets are " + op.toLowerCase() + "ed: " + result + ".\n Wait a few minutes for confirmations before updating secrets...");
                return result;
            } else if (StringTools.isBase64(result)) {
                System.out.println("Sign the TX and broadcast it:\n" + result);
            } else {
                System.out.println("Failed to " + op.toLowerCase() + " secret:" + result);
            }
        }
        return null;
    }

    private List<Secret> loadAllSecretList(Long lastHeight, Boolean active) {
        List<Secret> secretList = new ArrayList<>();
        List<String> last = new ArrayList<>();
        while (true) {
            List<Secret> subSecretList = apipClient.freshSecretSinceHeight(mainFid, lastHeight, ApipClient.DEFAULT_SIZE, last, active);
            if (subSecretList == null || subSecretList.isEmpty()) break;
            secretList.addAll(subSecretList);
            if (subSecretList.size() < ApipClient.DEFAULT_SIZE) break;
        }
        return secretList;
    }

    private List<SecretDetail> secretToSecretDetail(List<Secret> secretList, boolean ignoreBadCipher) {
        List<SecretDetail> secretDetailList = new ArrayList<>();
        byte[] priKey = Client.decryptPriKey(myPriKeyCipher, symKey);
        for (Secret secret : secretList) {
            SecretDetail secretDetail = SecretDetail.fromSecret(secret, priKey);
            if (secretDetail == null) {
                putInMap("fakeSecretDetailList", secret.getSecretId(), secret.getSecretId(), Serializer.STRING);
                if (ignoreBadCipher) continue;
                else {
                    secretDetail = new SecretDetail();
                    secretDetail.setSecretId(secret.getSecretId());
                    secretDetail.setTitle("Bad cipher: " + secret.getCipher());
                }
            }
            put(secretDetail.getTitle(), secretDetail);
            secretDetailList.add(secretDetail);
        }
        BytesTools.clearByteArray(priKey);
        return secretDetailList;
    }
    private List<SecretDetail> chooseSecretDetailList(List<SecretDetail> currentList, List<SecretDetail> chosenSecrets,
                                                      int totalDisplayed, BufferedReader br) {
        String title = "Choose Secrets";
        SecretDetail.showSecretDetailList(currentList, title, totalDisplayed);

        System.out.println("Enter secret numbers to select (comma-separated), 'a' for all. 'q' to quit, or press Enter for more:");
        String input = Inputer.inputString(br);

        if ("".equals(input)) {
            return null;  // Signal to continue to next page
        }

        if (input.equals("q")) {
            chosenSecrets.add(null);  // Signal to break the loop
            return chosenSecrets;
        }

        // Process input and add selected secrets to chosenSecrets
        // ...

        return chosenSecrets;
    }

    public Feip getFeip() {
        return Feip.fromProtocolName(Feip.ProtocolName.SECRET);
    }

} 