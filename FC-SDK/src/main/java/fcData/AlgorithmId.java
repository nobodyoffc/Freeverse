package fcData;

import com.google.gson.*;

import java.lang.reflect.Type;

public enum AlgorithmId {
    BitCore_EccAes256(Constants.ECC_256_K_1_AES_256_CBC),//The earliest one from bitcore which is the same as EccAes256BitPay@No1_NrC7.
    EccAes256K1P7_No1_NrC7(Constants.ECC_AES_256_K_1_P_7_NO_1_NR_C_7),
    FC_EccK1AesCbc256_No1_NrC7(Constants.ECC_K_1_AES_256_CBC_NO_1_NR_C_7),
    BTC_EcdsaSignMsg_No1_NrC7(Constants.BTC_ECDSA_SIGNMSG_NO1_NRC7),
    FC_Sha256SymSignMsg_No1_NrC7(Constants.FC_SHA256SYM_SIGNMSG_NO1_NRC7),
    FC_SchnorrSignTx_No1_NrC7(Constants.FC_SCHNORR_SIGNTX_NO1_NRC7),
    FC_SchnorrSignMsg_No1_NrC7(Constants.FC_SCHNORR_SIGNMSG_NO1_NRC7),
    FC_AesCbc256_No1_NrC7(Constants.FC_AES_CBC_256_NO_1_NR_C_7);

    private final String displayName;

    AlgorithmId(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static String[] getDisplayNames() {
        String[] values = new String[AlgorithmId.values().length];
        for (int i = 0; i < AlgorithmId.values().length; i++) {
            values[i] = AlgorithmId.values()[i].getDisplayName();
        }
        return values;
    }

    public String getName() {
        return this.name();
    }
    public static class AlgorithmTypeSerializer implements JsonSerializer<AlgorithmId> {
        @Override
        public JsonElement serialize(AlgorithmId src, Type typeOfSrc, JsonSerializationContext context) {
            return context.serialize(src.getDisplayName());
        }
    }
    public static AlgorithmId fromDisplayName(String displayName) {
        for (AlgorithmId type : AlgorithmId.values()) {
            if (type.getDisplayName().equals(displayName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown displayName: " + displayName);
    }
    public static class AlgorithmTypeDeserializer implements JsonDeserializer<AlgorithmId> {
        @Override
        public AlgorithmId deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return AlgorithmId.fromDisplayName(json.getAsString());
        }
    }

    public static class Constants {
        public static final String ECC_256_K_1_AES_256_CBC = "ECC256k1-AES256CBC";
        public static final String ECC_AES_256_K_1_P_7_NO_1_NR_C_7 = "EccAes256K1P7@No1_NrC7";
        public static final String ECC_K_1_AES_256_CBC_NO_1_NR_C_7 = "EccK1AesCbc256@No1_NrC7";
        public static final String BTC_ECDSA_SIGNMSG_NO1_NRC7 = "BTC-EcdsaSignMsg@No1_NrC7";
        public static final String FC_SHA256SYM_SIGNMSG_NO1_NRC7 = "Sha256SymSignMsg@No1_NrC7";
        public static final String FC_SCHNORR_SIGNTX_NO1_NRC7 = "SchnorrSignTx@No1_NrC7";
        public static final String FC_SCHNORR_SIGNMSG_NO1_NRC7 = "SchnorrSignMsg@No1_NrC7";
        public static final String FC_AES_CBC_256_NO_1_NR_C_7 = "AesCbc256@No1_NrC7";
    }
}
