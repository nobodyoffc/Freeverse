package data.fchData;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import core.crypto.Hash;
import core.crypto.KeyTools;
import data.fcData.FcEntity;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptOpCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Hex;
import utils.JsonUtils;

import javax.annotation.Nullable;
import java.util.*;


/**
 * P2SH (Pay-to-Script-Hash) transaction details
 * Supports CLTV (CheckLockTimeVerify), Multisig, and combined CLTV+Multisig
 * Used to mark time-locked UTXO information in opReturn
 *
 * Extends FcEntity to use hash160Hex as the id field for automatic serialization
 */
public class P2SH extends FcEntity {
    private static final Logger log = LoggerFactory.getLogger(P2SH.class);

    private P2shType type;
    private String redeemScript;

    private String redeemScriptHex;

    private Long lockTime;
    private String pubkeyHash;
    private List<String> pubkeys;
    private Integer m;
    private Integer n;
    private String fid;
    private Long birthHeight;
    private Long birthTime;
    private String birthTxId;


    public Long getBirthHeight() {
        return birthHeight;
    }

    public void setBirthHeight(Long birthHeight) {
        this.birthHeight = birthHeight;
    }

    public Long getBirthTime() {
        return birthTime;
    }

    public void setBirthTime(Long birthTime) {
        this.birthTime = birthTime;
    }

    public String getBirthTxId() {
        return birthTxId;
    }

    public void setBirthTxId(String birthTxId) {
        this.birthTxId = birthTxId;
    }

    public P2SH() {
    }

    /**
     * Create single-sig CLTV P2SH
     * @param fid Address that can spend after lockTime
     * @param lockTime Unix timestamp when funds become spendable
     */
    public P2SH(String fid, Long lockTime) {
        if (lockTime != null && fid != null){
            this.type = P2shType.CLTV;
            this.fid = fid;
            this.lockTime = lockTime;

            Script script = makeLockTimeRedeemScript(lockTime, KeyTools.addrToHash160(fid));
            this.redeemScript = Hex.toHex(script.getProgram()); // Hex format (for signing)

            // Set id as hash160Hex of the redeemScript
            byte[] hash160 = Hash.sha256hash160(script.getProgram());
            this.id = Hex.toHex(hash160);
        }
    }

    /**
     * Create multisig CLTV P2SH
     * @param pubkeys List of public keys in hex format
     * @param m Minimum number of signatures required
     * @param n Total number of public keys
     * @param lockTime Unix timestamp when funds become spendable (null for no time lock)
     */
    public P2SH(List<String> pubkeys, int m, int n, Long lockTime) {
        if (pubkeys == null || pubkeys.isEmpty()) {
            throw new IllegalArgumentException("Public keys list cannot be null or empty");
        }

        this.lockTime = lockTime;
        this.pubkeys = pubkeys;
        this.m = m;
        this.n = n;


        if (lockTime != null && lockTime > 0) {
            this.type = P2shType.MULTISIG_CLTV;
            Script script = makeMultisigLockTimeRedeemScript(lockTime, pubkeys, m, n);
            this.redeemScript = Hex.toHex(script.getProgram()); // Hex format (for signing)

            // Set id as hash160Hex of the full CLTV+multisig redeemScript
            byte[] hash160 = Hash.sha256hash160(script.getProgram());
            this.id = Hex.toHex(hash160);

            // Generate FID from the multisig portion (without CLTV) for multisig+CLTV
            Script multisigScript = makeMultisigRedeemScript(pubkeys, m, n);
            byte[] multisigHash160 = Hash.sha256hash160(multisigScript.getProgram());
            this.fid = KeyTools.hash160ToMultiAddr(multisigHash160);
        } else {
            this.type = P2shType.MULTISIG;
            Script script = makeMultisigRedeemScript(pubkeys, m, n);
            this.redeemScript = Hex.toHex(script.getProgram()); // Hex format (for signing)

            // Set id as hash160Hex of the multisig redeemScript
            byte[] hash160 = Hash.sha256hash160(script.getProgram());
            this.id = Hex.toHex(hash160);

            // Generate FID from the multisig redeem script hash
            byte[] multisigHash160 = Hash.sha256hash160(script.getProgram());
            this.fid = KeyTools.hash160ToMultiAddr(multisigHash160);
        }
    }

    /**
     * Create P2SH from existing redeemScript hex
     * Automatically detects the type (MULTISIG, CLTV, or MULTISIG_CLTV) and extracts parameters
     *
     * @param redeemScript The redeemScript in hex format
     * @throws IllegalArgumentException if redeemScript is invalid or hash160 doesn't match
     */
    public P2SH(String redeemScript) {
        if (redeemScript == null || redeemScript.isEmpty()) {
            throw new IllegalArgumentException("RedeemScript cannot be null or empty");
        }

        if (!Hex.isHexString(redeemScript)) {
            throw new IllegalArgumentException("RedeemScript is not valid hex");
        }

        this.redeemScript = redeemScript;

        // Calculate and validate hash160
        byte[] redeemScriptBytes = Hex.fromHex(redeemScript);
        byte[] calculatedHash160 = Hash.sha256hash160(redeemScriptBytes);

        this.id = Hex.toHex(calculatedHash160);

        try {
            // Parse the redeemScript to extract type and parameters
            Script script = new Script(redeemScriptBytes);
            List<ScriptChunk> chunks = script.getChunks();

            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("RedeemScript has no chunks");
            }

            // Check for CLTV prefix
            int startIdx = 0;
            boolean hasCLTV = false;

            ScriptChunk firstChunk = chunks.get(0);
            if (firstChunk.data != null && firstChunk.data.length > 0) {
                // Check if followed by CLTV and DROP opcodes
                if (chunks.size() > 2 &&
                        chunks.get(1).opcode == ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY &&
                        chunks.get(2).opcode == ScriptOpCodes.OP_DROP) {
                    hasCLTV = true;
                    this.lockTime = bytesToLong(firstChunk.data);
                    startIdx = 3; // Skip lockTime, CLTV, DROP
                }
            }

            // Parse the remaining script (either P2PKH or multisig)
            if (startIdx >= chunks.size()) {
                throw new IllegalArgumentException("No script body after CLTV prefix");
            }

            ScriptChunk nextChunk = chunks.get(startIdx);

            // Check if it's single-sig P2PKH pattern (OP_DUP)
            if (nextChunk.opcode == ScriptOpCodes.OP_DUP) {
                // Single-sig CLTV: DUP HASH160 <pubkeyHash> EQUALVERIFY CHECKSIG
                this.type = P2shType.CLTV;

                if (chunks.size() < startIdx + 5) {
                    throw new IllegalArgumentException("Incomplete single-sig CLTV script");
                }

                ScriptChunk pubkeyHashChunk = chunks.get(startIdx + 2);
                if (pubkeyHashChunk.data != null && pubkeyHashChunk.data.length == 20) {
                    this.pubkeyHash = Hex.toHex(pubkeyHashChunk.data);
                    this.fid = KeyTools.hash160ToFchAddr(pubkeyHashChunk.data);
                }
            } else {
                // Multisig pattern: <m> <pubkey1> ... <pubkeyN> <n> OP_CHECKMULTISIG
                // Extract m value
                this.m = decodeSmallNum(nextChunk);

                // Extract n from second-to-last chunk
                ScriptChunk nChunk = chunks.get(chunks.size() - 2);
                this.n = decodeSmallNum(nChunk);

                // Extract pubkeys (between m and n)
                this.pubkeys = new ArrayList<>();
                for (int i = startIdx + 1; i < chunks.size() - 2; i++) {
                    ScriptChunk pubkeyChunk = chunks.get(i);
                    if (pubkeyChunk.data != null) {
                        this.pubkeys.add(Hex.toHex(pubkeyChunk.data));
                    }
                }

                // Set type based on whether CLTV is present
                if (hasCLTV) {
                    this.type = P2shType.MULTISIG_CLTV;
                    // Generate FID from the multisig portion (without CLTV)
                    Script multisigScript = makeMultisigRedeemScript(this.pubkeys, this.m, this.n);
                    byte[] multisigHash160 = Hash.sha256hash160(multisigScript.getProgram());
                    this.fid = KeyTools.hash160ToMultiAddr(multisigHash160);
                } else {
                    this.type = P2shType.MULTISIG;
                    // FID is from the multisig script itself
                    this.fid = KeyTools.hash160ToMultiAddr(calculatedHash160);
                }
            }

        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse redeemScript: " + e.getMessage(), e);
        }
    }

    /**
     * Create single-sig CLTV redeemScript
     * Format: <lockTime> OP_CLTV OP_DROP OP_DUP OP_HASH160 <pubkeyHash> OP_EQUALVERIFY OP_CHECKSIG
     */
    public static Script makeLockTimeRedeemScript(long lockUntil, byte[] pubkeyHash) {
        ScriptBuilder builder = new ScriptBuilder();

        builder.number(lockUntil)
                .op(ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY)
                .op(ScriptOpCodes.OP_DROP);

        builder.op(ScriptOpCodes.OP_DUP)
                .op(ScriptOpCodes.OP_HASH160)
                .data(pubkeyHash)
                .op(ScriptOpCodes.OP_EQUALVERIFY)
                .op(ScriptOpCodes.OP_CHECKSIG);

        return builder.build();
    }

    /**
     * Create multisig redeemScript (no time lock)
     * Format: <m> <pubkey1> <pubkey2> ... <pubkeyN> <n> OP_CHECKMULTISIG
     */
    public static Script makeMultisigRedeemScript(List<String> pubkeyHexList, int m, int n) {
        ScriptBuilder builder = new ScriptBuilder();

        builder.smallNum(m);
        for (String pubkeyHex : pubkeyHexList) {
            builder.data(Hex.fromHex(pubkeyHex));
        }
        builder.smallNum(n);
        builder.op(ScriptOpCodes.OP_CHECKMULTISIG);

        return builder.build();
    }

    /**
     * Create multisig + CLTV redeemScript
     * Format: <lockTime> OP_CLTV OP_DROP <m> <pubkey1> <pubkey2> ... <pubkeyN> <n> OP_CHECKMULTISIG
     * This means: First the time lock must be satisfied, then m-of-n signatures are required
     */
    public static Script makeMultisigLockTimeRedeemScript(long lockUntil, List<String> pubkeyHexList, int m, int n) {
        ScriptBuilder builder = new ScriptBuilder();

        // CLTV part: time lock validation
        builder.number(lockUntil)
                .op(ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY)
                .op(ScriptOpCodes.OP_DROP);

        // Multisig part: m-of-n signature requirement
        builder.smallNum(m);
        for (String pubkeyHex : pubkeyHexList) {
            builder.data(Hex.fromHex(pubkeyHex));
        }
        builder.smallNum(n);
        builder.op(ScriptOpCodes.OP_CHECKMULTISIG);

        return builder.build();
    }


    /**
     * Strictly validate redeemScript syntax for CLTV and multisig to ensure spendability
     * This prevents creating cash that cannot be spent due to malformed scripts
     *
     * @param redeemScriptHex The redeemScript in hex format
     * @return true if the script is valid and spendable, false otherwise
     */
    public static boolean validateRedeemScriptSyntax(String redeemScriptHex) {
        if (redeemScriptHex == null || redeemScriptHex.isEmpty()) {
            log.error("RedeemScript is null or empty");
            return false;
        }

        if (!Hex.isHexString(redeemScriptHex)) {
            log.error("RedeemScript is not valid hex");
            return false;
        }

        try {
            byte[] redeemScriptBytes = Hex.fromHex(redeemScriptHex);
            Script script = new Script(redeemScriptBytes);
            List<ScriptChunk> chunks = script.getChunks();

            if (chunks.isEmpty()) {
                log.error("RedeemScript has no chunks");
                return false;
            }

            // Determine script type and validate accordingly
            int startIdx = 0;
            boolean hasCLTV = false;

            // Check for CLTV prefix
            ScriptChunk firstChunk = chunks.get(0);
            if (firstChunk.data != null && firstChunk.data.length > 0) {
                // Check if followed by CLTV (0xb1 = 177) and DROP (0x75 = 117)
                if (chunks.size() > 2 &&
                        chunks.get(1).opcode == ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY &&
                        chunks.get(2).opcode == ScriptOpCodes.OP_DROP) {
                    hasCLTV = true;
                    startIdx = 3;

                    // Validate lockTime value
                    long lockTime = bytesToLong(firstChunk.data);
                    if (lockTime <= 0) {
                        log.error("Invalid lockTime value: " + lockTime);
                        return false;
                    }
                } else {
                    log.error("CLTV prefix incomplete - missing CLTV or DROP opcode");
                    return false;
                }
            }

            // Validate the remaining script (either single-sig P2PKH or multisig)
            if (startIdx >= chunks.size()) {
                log.error("No script body after CLTV prefix");
                return false;
            }

            ScriptChunk nextChunk = chunks.get(startIdx);

            // Check if it's single-sig P2PKH pattern
            if (nextChunk.opcode == ScriptOpCodes.OP_DUP) {
                // Validate single-sig CLTV: DUP HASH160 <pubkeyHash> EQUALVERIFY CHECKSIG
                if (chunks.size() < startIdx + 5) {
                    log.error("Incomplete single-sig CLTV script");
                    return false;
                }

                if (chunks.get(startIdx + 1).opcode != ScriptOpCodes.OP_HASH160) {
                    log.error("Missing OP_HASH160 in single-sig script");
                    return false;
                }

                ScriptChunk pubkeyHashChunk = chunks.get(startIdx + 2);
                if (pubkeyHashChunk.data == null || pubkeyHashChunk.data.length != 20) {
                    log.error("Invalid pubkeyHash length: " +
                            (pubkeyHashChunk.data != null ? pubkeyHashChunk.data.length : "null"));
                    return false;
                }

                if (chunks.get(startIdx + 3).opcode != ScriptOpCodes.OP_EQUALVERIFY) {
                    log.error("Missing OP_EQUALVERIFY in single-sig script");
                    return false;
                }

                if (chunks.get(startIdx + 4).opcode != ScriptOpCodes.OP_CHECKSIG) {
                    log.error("Missing OP_CHECKSIG in single-sig script");
                    return false;
                }

                log.debug("P2SH", "Valid " + (hasCLTV ? "CLTV+" : "") + "single-sig redeemScript");
                return true;

            } else {
                // Validate multisig pattern: <m> <pubkey1> ... <pubkeyN> <n> OP_CHECKMULTISIG
                // Extract m value
                int m = decodeSmallNum(nextChunk);
                if (m <= 0 || m > 16) {
                    log.error("Invalid m value: " + m);
                    return false;
                }

                // Find n and OP_CHECKMULTISIG at the end
                if (chunks.size() < startIdx + 3) { // Minimum: m, 1 pubkey, n, CHECKMULTISIG
                    log.error("Incomplete multisig script");
                    return false;
                }

                ScriptChunk lastChunk = chunks.get(chunks.size() - 1);
                if (lastChunk.opcode != ScriptOpCodes.OP_CHECKMULTISIG) {
                    log.error("Missing OP_CHECKMULTISIG at end of multisig script");
                    return false;
                }

                ScriptChunk nChunk = chunks.get(chunks.size() - 2);
                int n = decodeSmallNum(nChunk);
                if (n <= 0 || n > 16 || m > n) {
                    log.error("Invalid n value: " + n + " (m=" + m + ")");
                    return false;
                }

                // Validate pubkeys count
                int expectedPubkeys = n;
                int actualPubkeys = chunks.size() - startIdx - 3; // Exclude m, n, CHECKMULTISIG

                if (actualPubkeys != expectedPubkeys) {
                    log.error("Pubkey count mismatch: expected " + expectedPubkeys +
                            ", got " + actualPubkeys);
                    return false;
                }

                // Validate each pubkey
                for (int i = startIdx + 1; i < chunks.size() - 2; i++) {
                    ScriptChunk pubkeyChunk = chunks.get(i);
                    if (pubkeyChunk.data == null || (pubkeyChunk.data.length != 33 && pubkeyChunk.data.length != 65)) {
                        log.error("Invalid pubkey length at index " + i + ": " +
                                (pubkeyChunk.data != null ? pubkeyChunk.data.length : "null"));
                        return false;
                    }
                }

                log.debug("P2SH", "Valid " + (hasCLTV ? "CLTV+" : "") + "multisig redeemScript (m=" + m + ", n=" + n + ")");
                return true;
            }

        } catch (Exception e) {
            log.error("RedeemScript validation failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Decode small number from script chunk (OP_0 to OP_16)
     */
    private static int decodeSmallNum(ScriptChunk chunk) {
        // OP_1 = 81, OP_2 = 82, ..., OP_16 = 96
        if (chunk.opcode >= ScriptOpCodes.OP_1 && chunk.opcode <= ScriptOpCodes.OP_16) {
            return chunk.opcode - 80;
        }
        // OP_0 = 0
        if (chunk.opcode == ScriptOpCodes.OP_0) {
            return 0;
        }
        // If it's data, try to decode as number
        if (chunk.data != null && chunk.data.length == 1) {
            return chunk.data[0] & 0xFF;
        }
        return -1;
    }

    public enum P2shType {
        MULTISIG,
        CLTV,
        MULTISIG_CLTV  // Combined multisig + CLTV
    }

    // Getters and setters

    public String getRedeemScript() {
        return redeemScript;
    }

    public void setRedeemScript(String redeemScript) {
        this.redeemScript = redeemScript;
    }

    public P2shType getType() {
        return type;
    }

    public void setType(P2shType type) {
        this.type = type;
    }

    public Long getLockTime() {
        return lockTime;
    }

    public void setLockTime(Long lockTime) {
        this.lockTime = lockTime;
    }

    public List<String> getPubkeys() {
        return pubkeys;
    }

    public void setPubkeys(List<String> pubkeys) {
        this.pubkeys = pubkeys;
    }

    public Integer getM() {
        return m;
    }

    public void setM(Integer m) {
        this.m = m;
    }

    public Integer getN() {
        return n;
    }

    public void setN(Integer n) {
        this.n = n;
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    /**
     * Convert to JSON string
     */
    public String toJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }

    /**
     * Convert to compact JSON string (no pretty printing, used for opReturn)
     */
    public String toCompactJson() {
        Gson gson = new GsonBuilder().create();
        return gson.toJson(this);
    }

    /**
     * Calculate the size of P2SH opReturn for a list of CLTV outputs
     * This is used to predict the opReturn size before actually creating the outputs
     * @param existingP2SHCount Number of existing P2SH outputs already in the transaction
     * @param newP2SHCount Number of new P2SH outputs to be added
     * @param newP2SHs List of new P2SH objects to be added (for accurate size calculation)
     * @return The total opReturn size in bytes
     */
    public static int calculateP2SHOpReturnSize(int existingP2SHCount, int newP2SHCount, List<P2SH> newP2SHs) {
        if (existingP2SHCount == 0 && newP2SHCount == 0) {
            return 0;
        }

        int totalSize = 0;

        // Array brackets: [ and ]
        totalSize += 2;

        // Separators (commas) between existing and new outputs
        int totalP2SHCount = existingP2SHCount + newP2SHCount;
        if (totalP2SHCount > 1) {
            totalSize += (totalP2SHCount - 1); // commas
        }

        // Add size of each new P2SH JSON (compact format, no pretty printing)
        if (newP2SHs != null) {
            for (P2SH p2sh : newP2SHs) {
                String compactJson = p2sh.toCompactJson();
                totalSize += compactJson.length();
            }
        }

        return totalSize;
    }

    /**
     * Estimate the JSON size for a single P2SH entry (for quick calculation without creating object)
     * @param fid FID address (for single-sig CLTV)
     * @param lockTime Lock time value
     * @param isMultisig Whether this is multisig
     * @param pubkeyCount Number of pubkeys (for multisig)
     * @return Estimated JSON size in bytes
     */
    public static int estimateP2SHJsonSize(String fid, Long lockTime, boolean isMultisig, int pubkeyCount) {
        // Approximate JSON structure:
        // {"fid":"FPqZ...","lockTime":123456789}
        // or for multisig:
        // {"pubkeys":["03abcd...","03efgh..."],"m":2,"n":3,"lockTime":123456789}

        int size = 2; // {}

        if (isMultisig) {
            size += 12; // "pubkeys":[]
            size += pubkeyCount * 68; // Each pubkey: "03abcd...ef"(66) + ","(1) + quotes(2) ≈ 69, minus 1 for last
            size += 10; // ,"m":2,"n":3
            if (lockTime != null) {
                size += 20; // ,"lockTime":123456789
            }
        } else {
            size += 40; // "fid":"FPqZ...Abc"  (FID is ~34 chars)
            if (lockTime != null) {
                size += 25; // ,"lockTime":123456789
            }
        }

        return size;
    }

    /**
     * Create from JSON string
     */
    public static P2SH fromJson(String json) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(json, P2SH.class);
    }

    @Override
    public String toString() {
        return toJson();
    }

    public String getPubkeyHash() {
        return pubkeyHash;
    }

    public void setPubkeyHash(String pubkeyHash) {
        this.pubkeyHash = pubkeyHash;
    }

    /**
     * Convert script hex to ASM (human-readable) format
     * @param scriptHex Script in hexadecimal format
     * @return Script in ASM format (human-readable)
     */
    public static String scriptHexToAsm(String scriptHex) {
        if (scriptHex == null || scriptHex.isEmpty()) {
            return null;
        }
        try {
            byte[] scriptBytes = Hex.fromHex(scriptHex);
            Script script = new Script(scriptBytes);
            return script.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid script hex: " + e.getMessage(), e);
        }
    }

    /**
     * Convert script ASM to hex format
     * Note: This is a simplified conversion. For complex scripts, use ScriptBuilder directly.
     * @param scriptAsm Script in ASM format (human-readable)
     * @return Script in hexadecimal format
     */
    public static String scriptAsmToHex(String scriptAsm) {
        if (scriptAsm == null || scriptAsm.isEmpty()) {
            return null;
        }
        try {
            // Parse ASM and rebuild script
            Script script = parseAsmToScript(scriptAsm);
            return Hex.toHex(script.getProgram());
        } catch (Exception e) {
            log.debug("TxHandler",e.getMessage());
            throw new IllegalArgumentException("Invalid script ASM: " + e.getMessage(), e);
        }
    }

    /**
     * Parse ASM format string to Script object
     * This is a helper method for scriptAsmToHex
     */
    private static Script parseAsmToScript(String asm) {
        // Split by whitespace first to handle PUSHDATA format
        String[] parts = asm.trim().split("\\s+");

        ScriptBuilder builder = new ScriptBuilder();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            // Handle PUSHDATA(n)[hex] format from BitcoinJ's Script.toString()
            // Example: PUSHDATA(3)[6c7c2d] or PUSHDATA(20)[61c42abb...]
            // This is a single token containing both the size and the hex data
            if (part.startsWith("PUSHDATA(") && part.contains("[") && part.endsWith("]")) {
                // Extract hex data from PUSHDATA(n)[hex]
                int bracketStart = part.indexOf('[');
                int bracketEnd = part.indexOf(']');

                if (bracketStart != -1 && bracketEnd != -1 && bracketEnd > bracketStart) {
                    String hexData = part.substring(bracketStart + 1, bracketEnd);
                    try {
                        byte[] data = Hex.fromHex(hexData);
                        builder.data(data);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Invalid hex data in PUSHDATA: " + hexData);
                    }
                } else {
                    throw new IllegalArgumentException("Invalid PUSHDATA format: " + part);
                }
            }
            // Check if it's an opcode (with or without OP_ prefix)
            else if (part.startsWith("OP_") || isOpcodeWithoutPrefix(part)) {
                // Try to get opcode, adding OP_ prefix if needed
                String opcodeName = part.startsWith("OP_") ? part : "OP_" + part;
                try {
                    int opcode = getOpcodeFromName(opcodeName);
                    builder.op(opcode);
                } catch (IllegalArgumentException e) {
                    // If it's not a recognized opcode, fall through to try as hex
                    try {
                        byte[] data = Hex.fromHex(part);
                        builder.data(data);
                    } catch (Exception hexError) {
                        throw new IllegalArgumentException("Unknown opcode or invalid hex: " + part);
                    }
                }
            }
            // Check if it's a number
            else if (part.matches("-?\\d+")) {
                long number = Long.parseLong(part);
                if (number >= 0 && number <= 16) {
                    builder.smallNum((int) number);
                } else {
                    builder.number(number);
                }
            }
            // Check if it's a standalone hex in brackets [hex] (shouldn't happen with PUSHDATA, but handle it)
            else if (part.startsWith("[") && part.endsWith("]")) {
                String hexData = part.substring(1, part.length() - 1);
                try {
                    byte[] data = Hex.fromHex(hexData);
                    builder.data(data);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid hex data in brackets: " + hexData);
                }
            }
            // Otherwise treat as raw hex data (for backward compatibility)
            else {
                try {
                    byte[] data = Hex.fromHex(part);
                    builder.data(data);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid hex data in ASM: " + part);
                }
            }
        }

        return builder.build();
    }

    /**
     * Check if a string is likely an opcode name without the OP_ prefix
     * BitcoinJ's Script.toString() sometimes omits the OP_ prefix
     */
    private static boolean isOpcodeWithoutPrefix(String part) {
        // Common opcodes that might appear without OP_ prefix
        switch (part) {
            case "DUP":
            case "HASH160":
            case "EQUALVERIFY":
            case "CHECKSIG":
            case "CHECKMULTISIG":
            case "CHECKLOCKTIMEVERIFY":
            case "DROP":
            case "EQUAL":
            case "VERIFY":
            case "RETURN":
            case "IF":
            case "ELSE":
            case "ENDIF":
            case "NOTIF":
            case "CHECKSIGVERIFY":
            case "CHECKMULTISIGVERIFY":
                return true;
            default:
                return false;
        }
    }

    /**
     * Check if the hash160 of the redeem script matches the provided hash160
     * This is used to verify P2SH addresses
     * @param redeemScript The redeem script as hex
     * @param lockScript The lock script
     * @return true if the hash160 of the redeem script matches the provided hash160
     */
    public static boolean checkRedeemScriptHash(String redeemScript, String lockScript) {
        if (!Hex.isHexString(redeemScript) || !Hex.isHexString(lockScript)) {
            return false;
        }
        byte[] redeemScriptBytes = Hex.fromHex(redeemScript);
        byte[] lockScriptBytes = Hex.fromHex(lockScript);

        return checkRedeemScriptHash(lockScriptBytes, redeemScriptBytes);
    }

    public static boolean checkRedeemScriptHash(byte[] lockScriptBytes, byte[] redeemScriptBytes) {
        byte[] hash160 = Arrays.copyOfRange(lockScriptBytes,2,22);

        // Calculate hash160 of the redeem script (SHA256 then RIPEMD160)
        byte[] calculatedHash = Hash.sha256hash160(redeemScriptBytes);

        // Compare the calculated hash with the provided hash
        if (calculatedHash.length != hash160.length) {
            return false;
        }

        for (int i = 0; i < calculatedHash.length; i++) {
            if (calculatedHash[i] != hash160[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * Extract the script hash (hash160) from a P2SH output script
     * P2SH script format: OP_HASH160 <20 bytes hash160> OP_EQUAL
     * @param scriptBytes The P2SH output script bytes
     * @return The 20-byte hash160, or null if not a valid P2SH script
     */
    public static byte[] extractHash160FromLockScript(byte[] scriptBytes) {
        // P2SH script format: OP_HASH160 (0xa9) <20 bytes> OP_EQUAL (0x87)
        // Total length should be 23 bytes: 1 + 1 + 20 + 1
        if (scriptBytes == null || scriptBytes.length != 23) {
            return null;
        }

        if (scriptBytes[0] != (byte) 0xa9 || // OP_HASH160
                scriptBytes[1] != 0x14 ||        // Push 20 bytes
                scriptBytes[22] != (byte) 0x87) { // OP_EQUAL
            return null;
        }

        // Extract the 20-byte hash160 (bytes 2-21)
        return Arrays.copyOfRange(scriptBytes, 2, 22);
    }

    /**
     * Parse hash160 from a P2SH lockScript (hex format)
     * P2SH script format: OP_HASH160 <20 bytes hash160> OP_EQUAL
     * @param lockScript The lockScript in hex format
     * @return The hash160 as hex string, or null if not a valid P2SH script
     */
    public static String hash160FromLockScript(String lockScript) {
        if (lockScript == null || lockScript.isEmpty()) {
            return null;
        }

        if (!Hex.isHexString(lockScript)) {
            log.error("LockScript is not valid hex");
            return null;
        }

        byte[] lockScriptBytes = Hex.fromHex(lockScript);
        byte[] hash160Bytes = extractHash160FromLockScript(lockScriptBytes);

        if (hash160Bytes == null) {
            return null;
        }

        return Hex.toHex(hash160Bytes);
    }

    /**
     * Extract lockTime value from a redeemScript hex string
     * @param redeemScriptHex The redeemScript in hex format
     * @return The lockTime value if present, null otherwise
     */
    public static Long extractLockTimeFromRedeemScript(String redeemScriptHex) {
        if (redeemScriptHex == null || redeemScriptHex.isEmpty()) {
            return null;
        }

        try {
            byte[] redeemScriptBytes = Hex.fromHex(redeemScriptHex);
            Script script = new Script(redeemScriptBytes);
            List<ScriptChunk> chunks = script.getChunks();

            if (chunks.isEmpty()) {
                return null;
            }

            // Check if first chunk is lockTime data
            ScriptChunk firstChunk = chunks.get(0);
            if (firstChunk.data != null && firstChunk.data.length > 0) {
                // Check if followed by CLTV and DROP opcodes
                if (chunks.size() > 2 &&
                        chunks.get(1).opcode == ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY &&
                        chunks.get(2).opcode == ScriptOpCodes.OP_DROP) {
                    // This is a CLTV script, extract lockTime
                    return bytesToLong(firstChunk.data);
                }
            }

            return null;
        } catch (Exception e) {
            log.error("Error extracting lockTime from redeemScript: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get opcode value from opcode name
     */
    private static int getOpcodeFromName(String opcodeName) {
        switch (opcodeName) {
            case "OP_DUP": return ScriptOpCodes.OP_DUP;
            case "OP_HASH160": return ScriptOpCodes.OP_HASH160;
            case "OP_EQUALVERIFY": return ScriptOpCodes.OP_EQUALVERIFY;
            case "OP_CHECKSIG": return ScriptOpCodes.OP_CHECKSIG;
            case "OP_CHECKMULTISIG": return ScriptOpCodes.OP_CHECKMULTISIG;
            case "OP_CHECKLOCKTIMEVERIFY": return ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY;
            case "OP_DROP": return ScriptOpCodes.OP_DROP;
            case "OP_EQUAL": return ScriptOpCodes.OP_EQUAL;
            case "OP_VERIFY": return ScriptOpCodes.OP_VERIFY;
            case "OP_RETURN": return ScriptOpCodes.OP_RETURN;
            case "OP_IF": return ScriptOpCodes.OP_IF;
            case "OP_ELSE": return ScriptOpCodes.OP_ELSE;
            case "OP_ENDIF": return ScriptOpCodes.OP_ENDIF;
            case "OP_NOTIF": return ScriptOpCodes.OP_NOTIF;
            case "OP_CHECKSIGVERIFY": return ScriptOpCodes.OP_CHECKSIGVERIFY;
            case "OP_CHECKMULTISIGVERIFY": return ScriptOpCodes.OP_CHECKMULTISIGVERIFY;
            default:
                throw new IllegalArgumentException("Unknown opcode: " + opcodeName);
        }
    }

    /**
     * Parse P2SH list from OP_RETURN output
     * @param transaction The transaction to parse
     * @return List of P2SH objects if found, null otherwise
     */
    public static List<P2SH> parseP2SHListFromOpReturn(Transaction transaction) {
        try {
            List<TransactionOutput> outputs = transaction.getOutputs();
            for (TransactionOutput output : outputs) {
                org.bitcoinj.script.Script script = output.getScriptPubKey();

                // Check if this is an OP_RETURN output
                if (script.isOpReturn()) {
                    // Get the data from OP_RETURN
                    byte[] opReturnData = script.getChunks().get(1).data;
                    if (opReturnData != null && opReturnData.length > 0) {
                        List<P2SH> p2SHList = parseP2SHListFromOpReturn(opReturnData);
                        if (p2SHList != null) return p2SHList;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing P2SH list from OP_RETURN: " + e.getMessage());
        }
        return null;
    }

    @Nullable
    public static List<P2SH> parseP2SHListFromOpReturn(byte[] opReturnData) {
        try {
            // Try to parse as text
            String opReturnText = new String(opReturnData, java.nio.charset.StandardCharsets.UTF_8);

            // Check if it's a JSON array
            if (!opReturnText.isEmpty() && opReturnText.trim().startsWith("[{")) {
                List<P2SH> p2SHList = JsonUtils.listFromJson(opReturnText, P2SH.class);
                if (!p2SHList.isEmpty()) {
                    log.debug("Found P2SH list with " + p2SHList.size() + " entries in OP_RETURN");
                    return p2SHList;
                }
            }
        } catch (Exception e) {
            // Not a valid P2SH JSON array, continue
            log.debug("OP_RETURN is not P2SH JSON array: " + e.getMessage());
        }
        return null;
    }


    public static void addCLTVInfoToCash(Cash cash, String redeemScriptHex) {
        try {
            // Parse P2SH script from hex format by converting to Script object
            // Three possible formats:
            // 1. Single-sig CLTV: <lockTime> CLTV DROP DUP HASH160 <pubkeyHash> EQUALVERIFY CHECKSIG
            //    -> Set lockTime AND update owner to pubkeyHash
            // 2. Multisig: <m> <pubkey1> ... <pubkeyN> <n> CHECKMULTISIG
            //    -> Ignore (do nothing)
            // 3. Multisig + CLTV: <lockTime> CLTV DROP <m> <pubkey1> ... <pubkeyN> <n> CHECKMULTISIG
            //    -> Set lockTime but keep original owner

            // Convert hex to Script object
            byte[] redeemScriptBytes = Hex.fromHex(redeemScriptHex);
            Script script = new Script(redeemScriptBytes);

            // Get script chunks (parsed opcodes and data)
            List<ScriptChunk> chunks = script.getChunks();

            if(chunks.isEmpty()) return;

            // Check if first chunk is a lockTime (data or number)
            Long lockTime = null;
            int startIdx = 0;

            ScriptChunk firstChunk = chunks.get(0);
            if(firstChunk.data != null && firstChunk.data.length > 0) {
                // Check if followed by CLTV (0xb1 = 177)
                if(chunks.size() > 2 && chunks.get(1).opcode == 177) { // OP_CHECKLOCKTIMEVERIFY
                    lockTime = bytesToLong(firstChunk.data);
                    cash.setLockTime(lockTime);
                    cash.setType(Cash.CashType.P2SH_CLTV.getValue());
                    startIdx = 3; // Skip lockTime, CLTV, DROP
                }
            }

            // If no CLTV found, this is plain multisig - ignore it
            if(lockTime == null) {
                return;
            }

            // Now check if it's single-sig CLTV or multisig+CLTV
            if(startIdx < chunks.size()) {
                ScriptChunk nextChunk = chunks.get(startIdx);

                // Check if it's single-sig CLTV (has DUP HASH160 pattern)
                if(nextChunk.opcode == 118) { // OP_DUP
                    // Single-sig CLTV: DUP HASH160 <pubkeyHash> EQUALVERIFY CHECKSIG
                    // Extract pubkeyHash and update owner
                    if(startIdx + 4 < chunks.size() && chunks.get(startIdx + 1).opcode == 169) { // OP_HASH160
                        ScriptChunk pubkeyHashChunk = chunks.get(startIdx + 2);
                        if(pubkeyHashChunk.data != null && pubkeyHashChunk.data.length == 20) {
                            byte[] pubkeyHashBytes = pubkeyHashChunk.data;
                            String fid = KeyTools.hash160ToFchAddr(pubkeyHashBytes);
                            cash.setOwner(fid);
                            cash.setType(Cash.CashType.P2SH_CLTV.getValue());
                        }
                    }
                }else{  // This is multisig+CLTV - extract multisig script and compute owner FID
                    cash.setType(Cash.CashType.P2SH_MULTISIG_CLTV.getValue());

                    // Extract multisig portion (everything from startIdx onwards)
                    // to create the standard multisig FID
                    byte[] multisigScript = extractMultisigScript(chunks, startIdx);
                    if(multisigScript != null) {
                        byte[] multisigHash160 = Hash.sha256hash160(multisigScript);
                        String multisigFid = KeyTools.hash160ToMultiAddr(multisigHash160);
                        cash.setOwner(multisigFid);
                    }
                }
            }
        } catch (Exception ignore) {
            // Silently continue if parsing fails
        }
    }


    /**
     * Extract multisig script portion from script chunks
     * Used to rebuild the standard multisig script without CLTV for proper FID generation
     * @param chunks The script chunks
     * @param startIdx Starting index (after CLTV DROP)
     * @return The multisig script bytes, or null if extraction fails
     */
    private static byte[] extractMultisigScript(List<ScriptChunk> chunks, int startIdx) {
        try {
            // Rebuild script from remaining chunks (multisig portion only)
            // Format: <m> <pubkey1> ... <pubkeyN> <n> CHECKMULTISIG
            ScriptBuilder builder = new ScriptBuilder();

            for(int i = startIdx; i < chunks.size(); i++) {
                ScriptChunk chunk = chunks.get(i);
                if(chunk.data != null) {
                    builder.data(chunk.data);
                } else {
                    builder.op(chunk.opcode);
                }
            }

            return builder.build().getProgram();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Convert byte array to long value (little-endian)
     * Used for parsing lockTime from Bitcoin script chunks
     * @param bytes The byte array
     * @return The long value
     */
    private static long bytesToLong(byte[] bytes) {
        long result = 0;
        for(int i = 0; i < bytes.length && i < 8; i++) {
            result |= ((long)(bytes[i] & 0xFF)) << (8 * i);
        }
        return result;
    }

    /**
     * Parse P2SH redeemScript from an unlockScript (scriptSig)
     *
     * In P2SH transactions, the unlockScript (scriptSig) format is:
     * - For single-sig CLTV: <sig> <redeemScript>
     * - For multisig: OP_0 <sig1> <sig2> ... <redeemScript>
     * - For multisig+CLTV: OP_0 <sig1> <sig2> ... <redeemScript>
     *
     * The redeemScript is always the LAST chunk in the unlockScript.
     *
     * @param unlockScript The unlockScript (scriptSig) in hex format
     * @param hash160Hex Optional hash160 to validate against (can be null)
     * @return The redeemScript in hex format, or null if parsing fails or validation fails
     */
    @Nullable
    public static String redeemScriptFromUnlockScript(String unlockScript, String hash160Hex) {
        if (unlockScript == null || unlockScript.isEmpty()) {
            log.error("UnlockScript is null or empty");
            return null;
        }

        if (!Hex.isHexString(unlockScript)) {
            log.error("UnlockScript is not valid hex");
            return null;
        }

        try {
            // Parse unlockScript as Script
            byte[] unlockScriptBytes = Hex.fromHex(unlockScript);
            Script script = new Script(unlockScriptBytes);
            List<ScriptChunk> chunks = script.getChunks();

            if (chunks.isEmpty()) {
                log.error("UnlockScript has no chunks");
                return null;
            }

            // The redeemScript is always the LAST chunk in P2SH unlockScripts
            ScriptChunk lastChunk = chunks.get(chunks.size() - 1);

            if (lastChunk.data == null || lastChunk.data.length == 0) {
                log.error("Last chunk in unlockScript has no data");
                return null;
            }

            // Extract redeemScript bytes
            byte[] redeemScriptBytes = lastChunk.data;
            String redeemScriptHex = Hex.toHex(redeemScriptBytes);

            // Validate redeemScript syntax to ensure it's actually a redeemScript
            if (!validateRedeemScriptSyntax(redeemScriptHex)) {
                log.error("Extracted data is not a valid redeemScript");
                return null;
            }

            // If hash160 is provided, validate it
            if (hash160Hex != null && !hash160Hex.isEmpty()) {
                if (!Hex.isHexString(hash160Hex)) {
                    log.error("Provided hash160 is not valid hex");
                    return null;
                }

                // Calculate hash160 of the redeemScript
                byte[] calculatedHash = Hash.sha256hash160(redeemScriptBytes);
                String calculatedHashHex = Hex.toHex(calculatedHash);

                // Compare with provided hash160
                if (!calculatedHashHex.equalsIgnoreCase(hash160Hex)) {
                    log.error("RedeemScript hash160 mismatch: expected " + hash160Hex +
                             ", got " + calculatedHashHex);
                    return null;
                }

                log.debug("RedeemScript hash160 validation passed");
            }

            log.debug("Successfully extracted redeemScript from unlockScript");
            return redeemScriptHex;

        } catch (Exception e) {
            log.error("Error parsing redeemScript from unlockScript: " + e.getMessage());
            return null;
        }
    }

    public String getRedeemScriptHex() {
        return redeemScriptHex;
    }

    public void setRedeemScriptHex(String redeemScriptHex) {
        this.redeemScriptHex = redeemScriptHex;
    }
}

