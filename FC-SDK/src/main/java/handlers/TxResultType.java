package handlers;

public enum TxResultType {
    UNSIGNED_JSON,
    TX_WITHOUT_INPUTS,
    SINGED_HEX,
    SIGNED_BASE64,
    TX_ID,
    ERROR_STRING,
    NULL;

    public static TxResultType fromString(String typeString ){
        typeString = typeString.trim().toUpperCase().replaceAll("_", "");
        switch (typeString.toUpperCase()) {
            case "UNSIGNEDJSON":
                return UNSIGNED_JSON;
            case "TXWITHOUTINPUTS":
                return TX_WITHOUT_INPUTS;
            case "SINGEDTRANSACTION":
                return SINGED_HEX;
            case "SIGNEDBASE64":
                return SIGNED_BASE64;
            case "TXID":
                return TX_ID;
            case "ERRORSTRING":
                return ERROR_STRING;
            default:
                return NULL;
        }
    }

    public String addTypeAheadResult(String result){
        return this+":"+result;
    }
    public static TxResultType parseType(String str){
        return valueOf(str.substring(0,str.indexOf(":")));
    }
}
