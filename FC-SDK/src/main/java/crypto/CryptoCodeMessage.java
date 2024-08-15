package crypto;

import java.util.HashMap;
import java.util.Map;

public class CryptoCodeMessage {
    public static String getMessage(int code){
        Map<Integer,String> codeMsgMap = new HashMap<>();
        codeMsgMap.put(0,"OK");
        codeMsgMap.put(1,"No such algorithm.");
        codeMsgMap.put(2,"No such provider.");
        codeMsgMap.put(3,"No such padding.");
        codeMsgMap.put(4,"Invalid algorithm parameter.");
        codeMsgMap.put(5,"Invalid key.");
        codeMsgMap.put(6,"IO exception.");
        codeMsgMap.put(7,"Failed to parse hex.");
        codeMsgMap.put(8,"Failed to parse crypto data.");
        codeMsgMap.put(9,"Other error.");
        codeMsgMap.put(10,"Stream error.");
        codeMsgMap.put(11,"File not found.");
        codeMsgMap.put(12,"Missing key.");
        codeMsgMap.put(13,"Missing iv.");
        codeMsgMap.put(14,"Wrong key length.");
        codeMsgMap.put(15,"Missing priKey.");
        codeMsgMap.put(16,"Missing pubKey.");
        codeMsgMap.put(17,"Missing cipher.");
        codeMsgMap.put(18,"Missing bundle.");
        codeMsgMap.put(19,"The pubKey and priKey have to be from different key pairs.");
        codeMsgMap.put(20,"Bad sum: the first 4 bytes of the value of sha256(symKey+iv+did).");
        codeMsgMap.put(21,"Missing source file.");
        codeMsgMap.put(22,"The algorithm has to assigned.");
        codeMsgMap.put(23,"Missing DID.");
        codeMsgMap.put(24,"Missing sum.");
        codeMsgMap.put(25,"Missing data file name.");
        return codeMsgMap.get(code);
    }

    public static String getErrorStringCodeMsg(int code) {
        return "Error:"+code+"_"+CryptoCodeMessage.getMessage(code);
    }


}
