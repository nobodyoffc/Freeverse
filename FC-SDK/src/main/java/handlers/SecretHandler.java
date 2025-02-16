package handlers;

import appTools.Inputer;
import appTools.Menu;
import appTools.Settings;
import constants.Constants;
import crypto.CryptoDataByte;
import crypto.Encryptor;
import crypto.KeyTools;
import fcData.AlgorithmId;
import fcData.SecretDetail;
import fcData.Op;
import fch.Wallet;
import feip.feipData.Secret;
import feip.feipData.SecretData;
import feip.feipData.Feip;
import feip.feipData.Service;
import tools.BytesTools;
import tools.Hex;
import tools.JsonTools;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import appTools.Shower;
import clients.ApipClient;
import clients.Client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.StringTools;

import static appTools.Inputer.askIfYes;
import static constants.OpNames.*;

public class SecretHandler extends Handler {
    protected static final Logger log = LoggerFactory.getLogger(SecretHandler.class);
    private static final Integer DEFAULT_SIZE = 10;
    private final List<String> fakeSecretDetailList = new ArrayList<>();

    public enum SecretOp {
        ADD(Op.ADD),
        DELETE(Op.DELETE),
        RECOVER(Op.RECOVER);

        public final Op op;
        SecretOp(Op op) { this.op = op; }

        public String toLowerCase() {
            return this.name().toLowerCase();
        }   
    }

    private final BufferedReader br;
    private final String myFid;
    private final ApipClient apipClient;
    private final CashHandler cashHandler;
    private final byte[] symKey;
    private final String myPriKeyCipher;
    private final byte[] myPubKey;
    private final tools.PersistentSequenceMap secretDB;

    // Constructor
    public SecretHandler (String myFid, ApipClient apipClient, CashHandler cashHandler, byte[] symKey, String myPriKeyCipher,String dbPath, BufferedReader br) {
        this.myFid = myFid;
        this.apipClient = apipClient;
        this.cashHandler = cashHandler;
        this.symKey = symKey;
        this.myPriKeyCipher = myPriKeyCipher;
        this.secretDB = new tools.PersistentSequenceMap(myFid, null, constants.Strings.SECRET,dbPath);
        this.br = br;
        this.myPubKey = KeyTools.priKeyToPubKey(Client.decryptPriKey(myPriKeyCipher, symKey));
    }

    public SecretHandler(Settings settings){
        this.myFid = settings.getMainFid();
        this.br = settings.getBr();
        this.apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);
        this.cashHandler = (CashHandler) settings.getHandler(HandlerType.CASH);
        this.symKey = settings.getSymKey();
        this.myPriKeyCipher = settings.getMyPriKeyCipher();
        this.secretDB = new tools.PersistentSequenceMap(myFid, null, constants.Strings.SECRET,settings.getDbDir());
        this.myPubKey = KeyTools.priKeyToPubKey(Client.decryptPriKey(settings.getMyPriKeyCipher(), settings.getSymKey()));
    }

    // Public Methods
    public void menu() {
        Menu menu = new Menu("Secret Menu");
        menu.add("Check Secrets On Chain", () -> checkSecrets(br));
        menu.add(FIND, () -> findSecret(br));
        menu.add(SecretOp.ADD.toLowerCase()+" On Chain", () -> addSecrets(br));
        menu.add(SecretOp.DELETE.toLowerCase()+" On Chain", () -> deleteSecrets(br));
        menu.add(SecretOp.RECOVER.toLowerCase()+" On Chain", () -> recoverSecrets(br));
        menu.showAndSelect(br);
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
            
            if (!askIfYes(br, "Do you want to add another secret?")) {
                break;
            }
        }
    }

    public String deleteSecret(List<String> secretIds, BufferedReader br) {
        return opSecret(secretIds, br, SecretOp.DELETE);
    }
    public void deleteSecrets(BufferedReader br) {
        List<SecretDetail> chosenSecrets = chooseSecretDetails(br);
        deleteSecrets(chosenSecrets, br);
    }

    public void deleteSecrets(List<SecretDetail> chosenSecrets, BufferedReader br) {
        if (chosenSecrets.isEmpty()) {
            System.out.println("No secrets chosen for deletion.");
            return;
        }
        
        if (askIfYes(br, "View them before delete?")) {
            chooseToShow(chosenSecrets, br);
        }
        
        if (askIfYes(br, "Delete " + chosenSecrets.size() + " secrets?")) {
            List<String> secretIds = new ArrayList<>();
            for (SecretDetail secret : chosenSecrets) {
                secretIds.add(secret.getSecretId());
                secretDB.remove(secret.getTitle().getBytes());
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
        List<SecretDetail> chosenSecrets = chooseSecretDetailList(secretDetailList,new ArrayList<>(),0,br);
        if (chosenSecrets.isEmpty()) {
            System.out.println("No secrets chosen for recovery.");
            return;
        }
        recoverSecrets(chosenSecrets, br);
    }
    public void recoverSecrets(List<SecretDetail> chosenSecrets, BufferedReader br) {

        if (askIfYes(br, "Recover " + chosenSecrets.size() + " secrets?")) {
            String result = recoverSecret(chosenSecrets.stream().map(SecretDetail::getSecretId).collect(Collectors.toList()), br);
            if (Hex.isHex32(result)) {
                System.out.println("Recovered secrets: " + chosenSecrets.stream().map(SecretDetail::getSecretId).collect(Collectors.toList()) + " in TX " + result + ".");
            } else {
                System.out.println("Failed to recover secrets: " + chosenSecrets.stream().map(SecretDetail::getSecretId).collect(Collectors.toList()) + ": " + result);
            }
        }
    }

    public void checkSecrets(BufferedReader br) {
        long lastHeight = secretDB.getLastHeight();
        List<Secret> secretList = loadAllSecretList(lastHeight, true);
        List<SecretDetail> secretDetailList = secretToSecretDetail(secretList, true);

        if (!secretList.isEmpty()) {
            secretDB.setLastHeight(secretList.get(0).getBirthHeight());
            deleteUnreadableSecrets();
        }

        System.out.println("You have " + secretDetailList.size() + " updated secrets.");
        if (secretDetailList.size() > 0) chooseToShow(secretDetailList, br);
    }

    private void deleteUnreadableSecrets() {
        if (fakeSecretDetailList.isEmpty()) return;
        if (!askIfYes(br, "Got " + fakeSecretDetailList.size() + " unreadable secrets. Delete them?")) return;
        String result = opSecret(fakeSecretDetailList, br, SecretOp.DELETE);
        if (Hex.isHex32(result)) {
            fakeSecretDetailList.clear();
        } else {
            System.out.println("Failed to delete unreadable secrets: " + result);
        }
    }

    // public void readSecrets(BufferedReader br) {
    //     List<SecretDetail> chosenSecrets = null;
        
    //     String input;
    //     while (true) {
    //         input = Inputer.inputString(br, "Input search string or secret ID. 'q' to quit. Enter to list all secrets and choose some:");
    //         if ("q".equals(input)) return;
    //         if ("".equals(input)) {
    //             chosenSecrets = chooseSecretDetails(br);
    //             if (chosenSecrets.isEmpty()) {
    //                 return;
    //             }
    //         } else {
    //             List<SecretDetail> foundSecretDetailList = findSecretDetails(input);
    //             chosenSecrets = choseFromSecretDetailList(foundSecretDetailList, br);
    //             if(chosenSecrets.isEmpty())return;
    //         }

    //         System.out.println("You chosen " + chosenSecrets.size() + " secrets.");

    //         String op = Inputer.chooseOne(
    //                 new String[]{READ, SecretOp.DELETE.toLowerCase(), SecretOp.RECOVER.toLowerCase()},
    //                 null, "Select to operate the secrets:", br);

    //         switch (op) {
    //             case READ:
    //                 chooseToShow(chosenSecrets, br);
    //                 break;
    //             case DELETE:
    //                 deleteSecrets(chosenSecrets, br);
    //                 break;
    //             case RECOVER:
    //                 recoverSecrets(chosenSecrets, br);
    //                 break;
    //             default:
    //                 break;
    //         }
    //     }
    // }

    private String opSecret(List<String> secretIds, BufferedReader br, SecretOp op) {
        if (op == null) return null;
        SecretData secretData = new SecretData();
        secretData.setOp(op.toLowerCase());
        SecretDetail secretDetail = new SecretDetail();
        byte[] priKey = Client.decryptPriKey(myPriKeyCipher, symKey);
        if (priKey == null) {
            System.out.println("Failed to get the priKey of " + myFid);
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

        if (askIfYes(br, "Are you sure to do below operation on chain?\n" + feip.toNiceJson() + "\n")) {
            String result = cashHandler.carve(opReturnStr, null);
            if (Hex.isHex32(result)) {
                System.out.println("The secrets are " + op.toLowerCase() + "ed: " + result + ".\n Wait a few minutes for confirmations before updating secrets...");
                return result;
            } else if(StringTools.isBase64(result)) {
                System.out.println("Sign the TX and broadcast it:\n"+result);
            }else{
                System.out.println("Failed to " + op.toLowerCase() + " secret:" + result);
            }
        }
        return null;
    }

    private List<Secret> loadAllSecretList(Long lastHeight, Boolean active) {
        List<Secret> secretList = new ArrayList<>();
        List<String> last = new ArrayList<>();
        while (true) {
            List<Secret> subSecretList = apipClient.freshSecretSinceHeight(myFid, lastHeight, DEFAULT_SIZE, last, active);
            if (subSecretList == null || subSecretList.isEmpty()) break;
            secretList.addAll(subSecretList);
            if (subSecretList.size() < DEFAULT_SIZE) break;
        }
        return secretList;
    }

    private List<SecretDetail> secretToSecretDetail(List<Secret> secretList, boolean ignoreBadCipher) {
        List<SecretDetail> secretDetailList = new ArrayList<>();
        byte[] priKey = Client.decryptPriKey(myPriKeyCipher, symKey);
        for (Secret secret : secretList) {
            SecretDetail secretDetail = SecretDetail.fromSecret(secret, priKey);
            if (secretDetail == null) {
                fakeSecretDetailList.add(secret.getSecretId());
                if(ignoreBadCipher)continue;
                else {
                    secretDetail = new SecretDetail();
                    secretDetail.setSecretId(secret.getSecretId());
                    secretDetail.setTitle("Bad cipher: "+secret.getCipher());
                }
            }
            secretDB.put(secretDetail.getTitle().getBytes(), secretDetail.toBytes());
            secretDetailList.add(secretDetail);
        }
        BytesTools.clearByteArray(priKey);
        return secretDetailList;
    }

    private List<SecretDetail> chooseSecretDetails(BufferedReader br) {
        List<SecretDetail> chosenSecrets = new ArrayList<>();
        byte[] lastKey = null;
        int totalDisplayed = 0;

        while (true) {
            List<SecretDetail> currentList = secretDB.getListFromEnd(lastKey, DEFAULT_SIZE, (byte[] value) -> SecretDetail.fromBytes(value));
            if (currentList.isEmpty()) {
                break;
            }

            List<SecretDetail> result = chooseSecretDetailList(currentList, chosenSecrets, totalDisplayed, br);
            if (result == null) {
                totalDisplayed += currentList.size();
                lastKey = currentList.get(currentList.size() - 1).getTitle().getBytes();
                continue;
            } else if (result.contains(null)) {
                result.remove(null);  // Remove the break signal
                break;
            }

            totalDisplayed += currentList.size();
            lastKey = currentList.get(currentList.size() - 1).getTitle().getBytes();
        }

        return chosenSecrets;
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

        if (input.equals("a")) {
            chosenSecrets.addAll(currentList);
            chosenSecrets.add(null);  // Signal to break the loop
            return chosenSecrets;
        }

        String[] selections = input.split(",");
        for (String selection : selections) {
            try {
                int index = Integer.parseInt(selection.trim()) - 1;
                if (index >= 0 && index < totalDisplayed + currentList.size()) {
                    int listIndex = index - totalDisplayed;
                    chosenSecrets.add(currentList.get(listIndex));
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input: " + selection);
            }
        }
        
        return chosenSecrets;
    }

    private static void chooseToShowOld(List<SecretDetail> secretList, BufferedReader br) {
        if (secretList == null || secretList.isEmpty()) {
            System.out.println("No secrets to display.");
            return;
        }

        while (true) {
            SecretDetail.showSecretDetailList(secretList, "View Secrets", 0);

            System.out.println("Enter secret numbers to view (comma-separated), 'a' to view all. 'q' to quit:");
            try {
                String input = br.readLine();
                if ("".equals(input)) continue;
                if ("q".equals(input)) return;
                if (input.contains(",")) {
                    String[] choices = input.replaceAll("\\s+", "").split(",");
                    for (String choice : choices) {
                        int choiceInt = Integer.parseInt(choice);
                        if (choiceInt < 1 || choiceInt > secretList.size()) {
                            System.out.println("Invalid choice. Please enter a number between 1 and " + secretList.size());
                            return;
                        }
                        showSecretDetail(secretList.get(choiceInt - 1));
                    }
                } else if ("a".equalsIgnoreCase(input)) {
                    for (SecretDetail secretDetail : secretList) {
                        showSecretDetail(secretDetail);
                    }
                    Menu.anyKeyToContinue(br);
                } else {
                    int choice = Integer.parseInt(input);
                    if (choice < 1 || choice > secretList.size()) {
                        System.out.println("Invalid choice. Please enter a number between 1 and " + secretList.size());
                        return;
                    }
                    SecretDetail chosenSecret = secretList.get(choice - 1);
                    showSecretDetail(chosenSecret);
                    Menu.anyKeyToContinue(br);
                }
            } catch (IOException e) {
                System.out.println("Error reading input: " + e.getMessage());
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
            }
        }
    }

    private static void showSecretDetail(SecretDetail secret) {
        Shower.printUnderline(20);
        System.out.println(" Type: " + secret.getType());
        System.out.println(" Title: " + secret.getTitle());
        System.out.println(" Content: " + secret.getContent());
        System.out.println(" Memo: " + secret.getMemo());
        System.out.println(" Update Height: " + secret.getUpdateHeight());
        Shower.printUnderline(20);
    }

    private List<SecretDetail> findSecretDetails(String searchStr) {
        List<SecretDetail> foundSecrets = new ArrayList<>();
        
        for (Map.Entry<byte[], byte[]> entry : secretDB.entrySet()) {
            SecretDetail secretDetail = SecretDetail.fromBytes(entry.getValue());
            if (secretDetail != null) {
                if ((secretDetail.getTitle() != null && secretDetail.getTitle().contains(searchStr)) ||
                    (secretDetail.getType() != null && secretDetail.getType().contains(searchStr)) ||
                    (secretDetail.getMemo() != null && secretDetail.getMemo().contains(searchStr))) {
                    foundSecrets.add(secretDetail);
                }
            }
        }
        return foundSecrets;
    }

    private SecretDetail findSecret(BufferedReader br) {
        String input = Inputer.inputString(br, "Input the title, type or part of the memo:");
        List<SecretDetail> list = findSecretDetails(input);
        return chooseOneSecretDetailFromList(list, br);
    }

    private SecretDetail chooseOneSecretDetailFromList(List<SecretDetail> secretDetailList, BufferedReader br) {
        if (secretDetailList.isEmpty()) return null;

        String title = "Choose Secret";
        SecretDetail.showSecretDetailList(secretDetailList, title, 0);

        System.out.println();
        int input = Inputer.inputInt(br, "Enter secret number to select it, Enter to quit:", secretDetailList.size());

        int index = input - 1;
        if (index >= 0 && index < secretDetailList.size()) {
            return secretDetailList.get(index);
        }
        return null;
    }

    private List<SecretDetail> choseFromSecretDetailList(List<SecretDetail> secretDetailList, BufferedReader br) {
        List<SecretDetail> chosenSecrets = new ArrayList<>();
        
        while (true) {
            String title = "Choose Secrets";
            SecretDetail.showSecretDetailList(secretDetailList, title, 0);

            System.out.println("Enter the numbers to select (comma-separated), 'a' to select all, or enter to quit:");
            String input = Inputer.inputString(br);

            if ("".equalsIgnoreCase(input)) {
                break;
            }

            if ("a".equalsIgnoreCase(input)) {
                chosenSecrets.addAll(secretDetailList);
                break;
            }

            String[] selections = input.split(",");
            for (String selection : selections) {
                try {
                    int index = Integer.parseInt(selection.trim()) - 1;
                    if (index >= 0 && index < secretDetailList.size()) {
                        chosenSecrets.add(secretDetailList.get(index));
                    } else {
                        System.out.println("Invalid selection: " + (index + 1));
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input: " + selection);
                }
            }

            System.out.println("Selected " + chosenSecrets.size() + " secrets. Continue selecting?");
            if (!askIfYes(br, "Continue selecting?")) {
                break;
            }
        }

        return chosenSecrets;
    }

    public Feip getFeip() {
        return Feip.fromProtocolName(Feip.ProtocolName.SECRET);
    }
}
