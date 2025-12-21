import core.crypto.NewAlgorithmTest;

public class TestRunner {
    public static void main(String[] args) {
        NewAlgorithmTest test = new NewAlgorithmTest();

        try {
            System.out.println("\n===== Running All Tests =====\n");

            System.out.println("[1/8] Testing AES-GCM-256 Symmetric Encryption...");
            test.testAesGcm256SymmetricEncryption();

            System.out.println("\n[2/8] Testing ECC K1 + AES-GCM-256 Asymmetric Encryption...");
            test.testEccK1AesGcm256AsymmetricEncryption();

            System.out.println("\n[3/8] Testing X25519 + AES-GCM-256 Asymmetric Encryption...");
            test.testX25519AesGcm256AsymmetricEncryption();

            System.out.println("\n[4/8] Testing Bundle Serialization for AES-GCM...");
            test.testBundleSerializationAesGcm();

            System.out.println("\n[5/8] Testing Bundle Serialization for X25519...");
            test.testBundleSerializationX25519();

            System.out.println("\n[6/8] Testing JSON Serialization for AES-GCM...");
            test.testJsonSerializationAesGcm();

            System.out.println("\n[7/8] Testing Password-Based Encryption with AES-GCM...");
            test.testPasswordBasedEncryptionWithAesGcm();

            System.out.println("\n[8/8] Testing AsyOneWay Encryption with X25519...");
            test.testAsyOneWayEncryptionWithX25519();

            System.out.println("\n\n========================================");
            System.out.println("✓✓✓ ALL TESTS PASSED SUCCESSFULLY! ✓✓✓");
            System.out.println("========================================\n");

        } catch (Exception e) {
            System.err.println("\n\n× TEST FAILED ×");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
