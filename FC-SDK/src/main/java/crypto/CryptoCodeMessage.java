//package crypto;
//
//import java.util.HashMap;
//import java.util.Map;
//
//public class CryptoCodeMessage {
//
//
//    public static final int OK = 0;
//    public static final int NO_SUCH_ALGORITHM = 1;
//    public static final int NO_SUCH_PROVIDER = 2;
//    public static final int NO_SUCH_PADDING = 3;
//    public static final int INVALID_ALGORITHM_PARAMETER = 4;
//    public static final int INVALID_KEY = 5;
//    public static final int IO_EXCEPTION = 6;
//    public static final int FAILED_TO_PARSE_HEX = 7;
//    public static final int FAILED_TO_PARSE_CRYPTO_DATA = 8;
//    public static final int OTHER_ERROR = 9;
//    public static final int STREAM_ERROR = 10;
//    public static final int FILE_NOT_FOUND = 11;
//    public static final int MISSING_KEY = 12;
//    public static final int MISSING_IV = 13;
//    public static final int WRONG_KEY_LENGTH = 14;
//    public static final int MISSING_PRI_KEY = 15;
//    public static final int MISSING_PUB_KEY = 16;
//    public static final int MISSING_CIPHER = 17;
//    public static final int MISSING_BUNDLE = 18;
//    public static final int PUBKEY_PRIVATE_KEY_DIFFERENT_PAIRS = 19;
//    public static final int BAD_SUM = 20;
//    public static final int MISSING_SOURCE_FILE = 21;
//    public static final int ALGORITHM_NOT_ASSIGNED = 22;
//    public static final int MISSING_DID = 23;
//    public static final int MISSING_SUM = 24;
//    public static final int MISSING_DATA_FILE_NAME = 25;
//    public static final int BAD_CIPHER = 26;
//
//    public static String getMessage(int code) {
//        Map<Integer, String> codeMsgMap = new HashMap<>();
//        codeMsgMap.put(OK, "OK");
//        codeMsgMap.put(NO_SUCH_ALGORITHM, "No such algorithm.");
//        codeMsgMap.put(NO_SUCH_PROVIDER, "No such provider.");
//        codeMsgMap.put(NO_SUCH_PADDING, "No such padding.");
//        codeMsgMap.put(INVALID_ALGORITHM_PARAMETER, "Invalid algorithm parameter.");
//        codeMsgMap.put(INVALID_KEY, "Invalid key.");
//        codeMsgMap.put(IO_EXCEPTION, "IO exception.");
//        codeMsgMap.put(FAILED_TO_PARSE_HEX, "Failed to parse hex.");
//        codeMsgMap.put(FAILED_TO_PARSE_CRYPTO_DATA, "Failed to parse crypto data.");
//        codeMsgMap.put(OTHER_ERROR, "Other error.");
//        codeMsgMap.put(STREAM_ERROR, "Stream error.");
//        codeMsgMap.put(FILE_NOT_FOUND, "File not found.");
//        codeMsgMap.put(MISSING_KEY, "Missing key.");
//        codeMsgMap.put(MISSING_IV, "Missing iv.");
//        codeMsgMap.put(WRONG_KEY_LENGTH, "Wrong key length.");
//        codeMsgMap.put(MISSING_PRI_KEY, "Missing priKey.");
//        codeMsgMap.put(MISSING_PUB_KEY, "Missing pubKey.");
//        codeMsgMap.put(MISSING_CIPHER, "Missing cipher.");
//        codeMsgMap.put(MISSING_BUNDLE, "Missing bundle.");
//        codeMsgMap.put(PUBKEY_PRIVATE_KEY_DIFFERENT_PAIRS, "The pubKey and priKey have to be from different key pairs.");
//        codeMsgMap.put(BAD_SUM, "Bad sum: the first 4 bytes of the value of sha256(symKey+iv+did).");
//        codeMsgMap.put(MISSING_SOURCE_FILE, "Missing source file.");
//        codeMsgMap.put(ALGORITHM_NOT_ASSIGNED, "The algorithm has to assigned.");
//        codeMsgMap.put(MISSING_DID, "Missing DID.");
//        codeMsgMap.put(MISSING_SUM, "Missing sum.");
//        codeMsgMap.put(MISSING_DATA_FILE_NAME, "Missing data file name.");
//        codeMsgMap.put(BAD_CIPHER, "Bad cipher.");
//        return codeMsgMap.get(code);
//    }
//
//    public static String getErrorStringCodeMsg(int code) {
//        return "Error:"+code+"_"+CryptoCodeMessage.getMessage(code);
//    }
//}
