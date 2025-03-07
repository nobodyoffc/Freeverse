package fcData;

import com.google.gson.*;

import java.lang.reflect.Type;

public enum AlgorithmId {
    BitCore_EccAes256("ECC256k1-AES256CBC"),//The earliest one from bitcore which is the same as EccAes256BitPay@No1_NrC7.
    EccAes256K1P7_No1_NrC7("EccAes256K1P7@No1_NrC7"),
    FC_EccK1AesCbc256_No1_NrC7("EccK1AesCbc256@No1_NrC7"),
    BTC_EcdsaSignMsg_No1_NrC7("BTC-EcdsaSignMsg@No1_NrC7"),
    FC_Sha256SymSignMsg_No1_NrC7("Sha256SymSignMsg@No1_NrC7"),
    FC_SchnorrSignTx_No1_NrC7("SchnorrSignTx@No1_NrC7"),
    FC_SchnorrSignMsg_No1_NrC7("SchnorrSignMsg@No1_NrC7"),
    FC_AesCbc256_No1_NrC7("AesCbc256@No1_NrC7");

    private final String displayName;

    AlgorithmId(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
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

}
