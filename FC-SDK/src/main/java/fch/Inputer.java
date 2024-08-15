package fch;

import clients.apipClient.ApipClient;
import crypto.Base58;
import crypto.CryptoDataByte;
import crypto.Encryptor;
import crypto.KeyTools;
import fcData.AlgorithmId;
import org.bitcoinj.core.ECKey;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Inputer extends appTools.Inputer {
    public static String inputGoodFid(BufferedReader br, String ask) {

        String fid;
        while (true) {
            System.out.println(ask);
            fid = inputString(br);
            if (fid == null) return null;
            if ("".equals(fid)) return "";
            if ("d".equals(fid)) return "d";
            if ("c".equals(fid)) return "c";
            if (!KeyTools.isValidFchAddr(fid)) {
                System.out.println("It's not a valid FID. Try again.");
                continue;
            }
            return fid;
        }
    }
    public static byte[] inputPriKeyHexOrBase58(BufferedReader br){
        String ask = "Input the private Key in Hex or Base58:";
        String priKey = appTools.Inputer.inputString(br, ask);
        if(crypto.Base58.isBase58Encoded(priKey) && priKey.length()==52)return crypto.Base58.decode(priKey);
        else if(javaTools.Hex.isHexString(priKey) && priKey.length()==64)return javaTools.Hex.fromHex(priKey);
        return null;
    }

    public static String inputOrCreateFid(String ask,BufferedReader br,byte[] symKey,ApipClient apipClient) {
        System.out.println(ask);
        String fid;
        while (true) {
            fid = inputGoodFid(br,"Input the FID or 'c' to create a new one. Enter to quit:");
            if ("".equals(fid)) return null;
            if("d".equals(fid))return null;
            if ("c".equals(fid)) {
                ECKey ecKey = KeyTools.genNewFid(br);
                byte[] priKey = ecKey.getPrivKeyBytes();
                fid = ecKey.toAddress(FchMainNetwork.MAINNETWORK).toBase58();
                String priKeyCipher = new Encryptor(AlgorithmId.FC_Aes256Cbc_No1_NrC7).encryptToJsonBySymKey(priKey,symKey);
                if(apipClient!=null){
                    apipClient.checkMaster(priKeyCipher,symKey,br);
                }
                return fid;
            }
            if (!KeyTools.isValidFchAddr(fid)) {
                System.out.println("It's not a valid FID. Try again.");
                continue;
            }
            return fid;
        }
    }

    public static String[] inputOrCreateFidArray(BufferedReader br,byte[] symKey,ApipClient apipClient){
        List<String> fidList = new ArrayList<>();
        do {
            fidList.add(inputOrCreateFid("Set FIDs...", br, symKey, apipClient));
        }while(askIfYes(br,"Add more?"));
        return fidList.toArray(new String[0]);
    }
    public static Map<String,String> inputGoodFidValueStrMap(BufferedReader br, String mapName, boolean checkFullShare)  {
        Map<String,String> map = new HashMap<>();

        while(true) {

            while(true) {
                System.out.println("Set " + mapName + ". 'y' to input. 'q' to quit. 'i' to quit ignore all changes.");
                String input;
                try {
                    input = br.readLine();
                } catch (IOException e) {
                    System.out.println("br.readLine() wrong.");
                    return null;
                }
                if("y".equals(input))break;
                if("q".equals(input)){
                    System.out.println(mapName + " is set.");
                    return map;
                }
                if("i".equals(input))return null;
                System.out.println("Invalid input. Try again.");
            }

            String key;
            while (true) {
                System.out.println("Input FID. 'q' to quit:");
                key = inputString(br);
                if(key == null)return null;
                if ("q".equals(key)) break;

                if (!KeyTools.isValidFchAddr(key)) {
                    System.out.println("It's not a valid FID. Try again.");
                    continue;
                }
                break;
            }
            Double value = null;

            if(!"q".equals(key)) {
                if (checkFullShare) {
                    value = inputGoodShare(br);
                } else {
                    String ask = "Input the number. Enter to quit.";
                    value = inputDouble(br,ask);
                }
            }

            if(value!=null){
                map.put(key,String.valueOf(value));
            }
        }
    }
    public static String[] inputFidArray(BufferedReader br, String ask, int len) {
        ArrayList<String> itemList = new ArrayList<String>();
        System.out.println(ask);
        while (true) {
            String item = appTools.Inputer.inputString(br);
            if (item.equals("")) break;
            if (!KeyTools.isValidFchAddr(item)) {
                System.out.println("Invalid FID. Try again.");
                continue;
            }
            if (item.startsWith("3")) {
                System.out.println("Multi-sign FID can not used to make new multi-sign FID. Try again.");
                continue;
            }
            if (len > 0) {
                if (item.length() != len) {
                    System.out.println("The length does not match.");
                    continue;
                }
            }
            itemList.add(item);
            System.out.println("Input next item if you want or enter to end:");
        }
        if (itemList.isEmpty()) return new String[0];

        String[] items = itemList.toArray(new String[itemList.size()]);

        return items;
    }

    public static char[] inputPriKeyWif(BufferedReader br) {
        char[] priKey = new char[52];
        int num = 0;
        try {
            num = br.read(priKey);

            if (num != 52 || !Base58.isBase58Encoded(priKey)) {
                System.out.println("The key should be 52 characters and Base58 encoded.");
                return null;
            }
            br.read();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return priKey;
    }


    public static String inputPriKeyCipher(BufferedReader br, byte[] initSymKey) {
        byte[] priKeyBytes =  inputPriKey(br);
        return makePriKeyCipher(priKeyBytes, initSymKey);
    }
    @Nullable
    public static byte[] inputPriKey(BufferedReader br) {
        return KeyTools.inputCipherGetPriKey(br);
    }
    @Nullable
    public static String makePriKeyCipher(byte[] priKeyBytes, byte[] initSymKey) {
        if (priKeyBytes == null) return null;
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_Aes256Cbc_No1_NrC7);
        CryptoDataByte cryptoDataByte = encryptor.encryptBySymKey(priKeyBytes, initSymKey);
        if(cryptoDataByte.getCode()==0) return cryptoDataByte.toJson();
        else return null;
    }
}
