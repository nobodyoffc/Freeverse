package handlers;

public enum TxResultType {
    UNSIGNED_JSON,
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
}
