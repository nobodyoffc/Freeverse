package startFEIP;

import java.util.Arrays;
import java.util.List;

/**
 * Centralized constants for the FEIP parser module.
 */
public class FeipConstants {

    private FeipConstants() {}

    // --- Authorization / Promise strings ---
    public static final String PROMISE_MASTER = "The master owns all my rights.";
    public static final String CONFIRM_TRANSFER_TEAM = "I transfer the team to the transferee.";
    public static final String CONFIRM_AGREE_CONSENSUS = "I agree with the new consensus.";
    public static final String CONFIRM_JOIN_TEAM = "I join the team and agree with the team consensus.";
    public static final String CONFIRM_STATEMENT = "This is a formal and irrevocable statement.";

    // --- FEIP-managed fields on the Freer index ---
    // These are cleared during rollback and reparse, while blockchain fields
    // (balance, cash, income, cd, cdd, weight, etc.) are preserved.
    public static final List<String> FREER_FEIP_FIELDS = Arrays.asList(
            "cid", "usedCids", "nameTime", "master",
            "home", "noticeFee", "reputation", "hot"
    );

    // --- Protocol rules ---
    public static final int MAX_CID_COUNT = 4;
    public static final int MAX_RATE = 5;
    public static final int INITIAL_CID_SUFFIX_LENGTH = 4;
    public static final long CDD_MULTIPLIER = 100;

    // --- Input validation limits ---
    public static final int MAX_NAME_LENGTH = 256;
    public static final int MAX_DESC_LENGTH = 4096;
    public static final int MAX_CONTENT_LENGTH = 10240;
    public static final int MAX_GENERAL_FIELD_LENGTH = 1024;

    /**
     * Validates that a string field does not exceed the given max length.
     * Returns true if the value is null (caller handles null separately) or within limit.
     */
    public static boolean isWithinLimit(String value, int maxLength) {
        return value == null || value.length() <= maxLength;
    }
}
