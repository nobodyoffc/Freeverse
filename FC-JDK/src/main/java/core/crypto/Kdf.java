package core.crypto;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import utils.BytesUtils;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * Password-based key derivation functions supported for EncryptType.Password.
 * Each ciphertext stamped with a Kdf must be decryptable under that Kdf forever,
 * so parameter changes require minting a new Kdf id.
 */
public enum Kdf {
    Sha256Iv_No1_NrC7(Constants.SHA256_IV_NO1_NRC7),
    Argon2id_No1_NrC7(Constants.ARGON2ID_NO1_NRC7);

    public static final int ARGON2ID_ITERATIONS = 3;
    public static final int ARGON2ID_MEMORY_KIB = 65536;
    public static final int ARGON2ID_PARALLELISM = 1;
    public static final int DERIVED_KEY_LEN = 32;

    private final String displayName;

    Kdf(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public byte[] deriveSymkey(char[] password, byte[] salt) {
        return switch (this) {
            case Sha256Iv_No1_NrC7 -> {
                byte[] passwordBytes = BytesUtils.charArrayToByteArray(password, StandardCharsets.UTF_8);
                yield Decryptor.sha256(BytesUtils.addByteArray(Decryptor.sha256(passwordBytes), salt));
            }
            case Argon2id_No1_NrC7 -> {
                Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                        .withIterations(ARGON2ID_ITERATIONS)
                        .withMemoryAsKB(ARGON2ID_MEMORY_KIB)
                        .withParallelism(ARGON2ID_PARALLELISM)
                        .withSalt(salt)
                        .build();
                Argon2BytesGenerator gen = new Argon2BytesGenerator();
                gen.init(params);
                byte[] out = new byte[DERIVED_KEY_LEN];
                gen.generateBytes(password, out);
                yield out;
            }
        };
    }

    public static Kdf fromDisplayName(String displayName) {
        for (Kdf k : Kdf.values()) {
            if (k.displayName.equals(displayName)) return k;
        }
        throw new IllegalArgumentException("Unknown Kdf displayName: " + displayName);
    }

    public static class KdfSerializer implements JsonSerializer<Kdf> {
        @Override
        public JsonElement serialize(Kdf src, Type typeOfSrc, JsonSerializationContext context) {
            return context.serialize(src.getDisplayName());
        }
    }

    public static class KdfDeserializer implements JsonDeserializer<Kdf> {
        @Override
        public Kdf deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return Kdf.fromDisplayName(json.getAsString());
        }
    }

    public static class Constants {
        public static final String SHA256_IV_NO1_NRC7 = "Sha256Iv@No1_NrC7";
        public static final String ARGON2ID_NO1_NRC7 = "Argon2id@No1_NrC7";
    }
}
