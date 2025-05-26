package core.fch;

import clients.ApipClient;
import core.crypto.Base58;
import core.crypto.CryptoDataByte;
import core.crypto.Encryptor;
import core.crypto.KeyTools;
import data.fcData.AlgorithmId;
import org.bitcoinj.core.ECKey;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Inputer extends ui.Inputer {
    public static String inputGoodFid(BufferedReader br, String ask) {

        String fid;
        while (true) {
            System.out.println(ask);
            fid = inputString(br);
            if (fid == null) return null;
            if ("".equals(fid)) return "";
            if ("d".equals(fid)) return "d";
            if ("c".equals(fid)) return "c";
            if (!KeyTools.isGoodFid(fid)) {
                System.out.println("It's not a valid FID. Try again.");
                continue;
            }
            return fid;
        }
    }
    public static byte[] inputPrikeyHexOrBase58(BufferedReader br){
        String ask = "Input the private Key in Hex or Base58:";
        String prikey = ui.Inputer.inputString(br, ask);
        if(Base58.isBase58Encoded(prikey) && prikey.length()==52)return Base58.decode(prikey);
        else if(utils.Hex.isHexString(prikey) && prikey.length()==64)return utils.Hex.fromHex(prikey);
        return null;
    }

    public static String inputOrCreateFid(String ask,BufferedReader br,byte[] symkey,ApipClient apipClient) {
        System.out.println(ask);
        String fid;
        while (true) {
            fid = inputGoodFid(br,"Input the FID or 'c' to create a new one. Enter to quit:");
            if ("".equals(fid)) return null;
            if("d".equals(fid))return null;
            if ("c".equals(fid)) {
                ECKey ecKey = KeyTools.genNewFid(br);
                byte[] prikey = ecKey.getPrivKeyBytes();
                fid = ecKey.toAddress(FchMainNetwork.MAINNETWORK).toBase58();
                String prikeyCipher = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7).encryptToJsonBySymkey(prikey,symkey);
                if(apipClient!=null){
                    apipClient.checkMaster(prikeyCipher,symkey,br);
                }
                return fid;
            }
            if (!KeyTools.isGoodFid(fid)) {
                System.out.println("It's not a valid FID. Try again.");
                continue;
            }
            return fid;
        }
    }

    public static String[] inputOrCreateFidArray(BufferedReader br,byte[] symkey,ApipClient apipClient){
        List<String> fidList = new ArrayList<>();
        do {
            fidList.add(inputOrCreateFid("Set FIDs...", br, symkey, apipClient));
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

                if (!KeyTools.isGoodFid(key)) {
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
            String item = ui.Inputer.inputString(br);
            if (item.equals("")) break;
            if (!KeyTools.isGoodFid(item)) {
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

    public static char[] inputPrikeyWif(BufferedReader br) {
        char[] prikey = new char[52];
        int num = 0;
        try {
            num = br.read(prikey);

            if (num != 52 || !Base58.isBase58Encoded(prikey)) {
                System.out.println("The key should be 52 characters and Base58 encoded.");
                return null;
            }
            br.read();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return prikey;
    }


    public static String inputPrikeyCipher(BufferedReader br, byte[] initSymkey) {
        byte[] prikeyBytes =  importOrCreatePrikey(br);
        return makePrikeyCipher(prikeyBytes, initSymkey);
    }
    @Nullable
    public static byte[] importOrCreatePrikey(BufferedReader br) {
        return KeyTools.importOrCreatePrikey(br);
    }
    @Nullable
    public static String makePrikeyCipher(byte[] prikeyBytes, byte[] initSymkey) {
        if (prikeyBytes == null) return null;
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = encryptor.encryptBySymkey(prikeyBytes, initSymkey);
        if(cryptoDataByte.getCode()==0) return cryptoDataByte.toJson();
        else return null;
    }
}
