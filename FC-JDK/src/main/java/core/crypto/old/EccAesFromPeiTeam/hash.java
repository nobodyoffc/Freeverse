package core.crypto.old.EccAesFromPeiTeam;


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class hash {
    public static byte[] SHA256(String str) throws NoSuchAlgorithmException {
        MessageDigest digest;
        digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(str.getBytes(StandardCharsets.UTF_8));
        return hash;
    }

    public static byte[] SHA512(String str) throws NoSuchAlgorithmException {
        MessageDigest digest;
        digest = MessageDigest.getInstance("SHA-512");
        byte[] hash = digest.digest(str.getBytes(StandardCharsets.UTF_8));
        return hash;
    }

}
