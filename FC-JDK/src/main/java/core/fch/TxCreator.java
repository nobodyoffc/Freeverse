package core.fch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import constants.Constants;
import constants.IndicesNames;
import core.crypto.KeyTools;
import data.fcData.ReplyBody;
import data.fchData.Cash;
import data.fchData.Multisig;
import data.fchData.P2SH;
import data.fchData.RawTxForCsV1;
import data.nasa.TxInput;
import data.nasa.TxOutput;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.SchnorrSignature;
import org.bitcoinj.fch.FchMainNetwork;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.BytesUtils;
import utils.FchUtils;
import utils.Hex;

import javax.crypto.Cipher;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.*;

import static constants.Constants.*;
import static core.crypto.KeyTools.prikeyToFid;

/**
 * 工具类
 */
public class TxCreator {
    private static final Logger log = LoggerFactory.getLogger(TxCreator.class);
    public static final double DEFAULT_FEE_RATE = 0.00001;
    public static final byte OFF_LINE_TX_START_FLAG = (byte) 0xFF;

    static {
        fixKeyLength();
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void fixKeyLength() {
        String errorString = "Failed manually overriding key-length permissions.";
        int newMaxKeyLength;
        try {
            if ((newMaxKeyLength = Cipher.getMaxAllowedKeyLength("AES")) < 256) {
                Class<?> c = Class.forName("javax.crypto.CryptoAllPermissionCollection");
                Constructor<?> con = c.getDeclaredConstructor();
                con.setAccessible(true);
                Object allPermissionCollection = con.newInstance();
                Field f = c.getDeclaredField("all_allowed");
                f.setAccessible(true);
                f.setBoolean(allPermissionCollection, true);

                c = Class.forName("javax.crypto.CryptoPermissions");
                con = c.getDeclaredConstructor();
                con.setAccessible(true);
                Object allPermissions = con.newInstance();
                f = c.getDeclaredField("perms");
                f.setAccessible(true);
                ((Map) f.get(allPermissions)).put("*", allPermissionCollection);

                c = Class.forName("javax.crypto.JceSecurityManager");
                f = c.getDeclaredField("defaultPolicy");
                f.setAccessible(true);
                Field mf = Field.class.getDeclaredField("modifiers");
                mf.setAccessible(true);
                mf.setInt(f, f.getModifiers() & ~Modifier.FINAL);
                f.set(null, allPermissions);

                newMaxKeyLength = Cipher.getMaxAllowedKeyLength("AES");
            }
        } catch (Exception e) {
            throw new RuntimeException(errorString, e);
        }
        if (newMaxKeyLength < 256)
            throw new RuntimeException(errorString); // hack failed
    }

    public static String createAndSignFchTx(List<Cash> inputs, byte[] priKey, List<Cash> outputs, String opReturn, FchMainNetwork mainnetwork) {
        return createAndSignTx(inputs, priKey, outputs, opReturn, 0, mainnetwork);
    }
//
//    public static String createTxFch(List<Cash> inputs, byte[] priKey, List<Cash> outputs, String opReturn, double feeRateDouble, MainNetParams mainnetwork) {
//        String changeToFid = inputs.get(0).getOwner();
//        if(outputs==null)outputs = new ArrayList<>();
//        long txSize = opReturn == null ? calcTxSize(inputs.size(), outputs.size(), 0) : calcTxSize(inputs.size(), outputs.size(), opReturn.getBytes().length);
//
//        long fee =calcFee(txSize,feeRateDouble);
//
//        Transaction transaction = new Transaction(mainnetwork);
//
//        long totalMoney = 0;
//        long totalOutput = 0;
//
//        ECKey eckey = ECKey.fromPrivate(priKey);
//
//        for (Cash output : outputs) {
//            long value = utils.FchUtils.coinToSatoshi(output.getAmount());
//            totalOutput += value;
//            transaction.addOutput(Coin.valueOf(value), Address.fromBase58(mainnetwork, output.getOwner()));
//        }
//
//        if (opReturn != null && !opReturn.isEmpty()) {
//            try {
//                Script opreturnScript = ScriptBuilder.createOpReturnScript(opReturn.getBytes(StandardCharsets.UTF_8));
//                transaction.addOutput(Coin.ZERO, opreturnScript);
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
//        }
//
//        totalMoney = addInputToTx(inputs, mainnetwork, transaction);
//
//        if ((totalOutput + fee) > totalMoney) {
//            throw new RuntimeException("input is not enough");
//        }
//        long change = totalMoney - totalOutput - fee;
//        if (change > Constants.DustInSatoshi) {
//            transaction.addOutput(Coin.valueOf(change), Address.fromBase58(mainnetwork, changeToFid));
//        }
//
//        for (int i = 0; i < inputs.size(); ++i) {
//            Cash input = inputs.get(i);
//            Script script = ScriptBuilder.createP2PKHOutputScript(eckey);
//            SchnorrSignature signature = transaction.calculateSchnorrSignature(i, eckey, script.getProgram(), Coin.valueOf(input.getValue()), Transaction.SigHash.ALL, false);
//            Script schnorr = ScriptBuilder.createSchnorrInputScript(signature, eckey);
//            transaction.getInput(i).setScriptSig(schnorr);
//        }
//
//        byte[] signResult = transaction.bitcoinSerialize();
//        return Hex.toHex(signResult);
//    }

    public static String createAndSignTx(List<Cash> inputs, byte[] priKey, List<Cash> outputs, String opReturn, double feeRateDouble, MainNetParams mainnetwork) {
        RawTxInfo rawTxInfo = new RawTxInfo();
        rawTxInfo.setInputs(inputs);
        rawTxInfo.setOutputs(outputs);
        rawTxInfo.setOpReturn(opReturn);

        if(inputs!=null && !inputs.isEmpty()){
            rawTxInfo.setSender(inputs.get(0).getOwner());
        }

        rawTxInfo.setFeeRate(feeRateDouble);
        Transaction tx = createTx(rawTxInfo, mainnetwork);
        return signTx(priKey,tx,rawTxInfo.getInputs());
    }

    public static String createTxHex(RawTxInfo rawTxInfo, MainNetParams mainNetwork) {
        byte[] txBytes = createTxBytes(rawTxInfo, mainNetwork);
        if (txBytes == null) return null;
        return Hex.toHex(txBytes);
    }

    public static byte[] createTxBytes(RawTxInfo rawTxInfo, MainNetParams mainNetwork) {
        Transaction tx = createTx(rawTxInfo, mainNetwork);
        if (tx == null) return null;
        return tx.bitcoinSerialize();
    }

    public static boolean isLockTimeUnlocked(long lockTime, long bestHeight) {
        long currentTimestamp = System.currentTimeMillis() / 1000;
        return isLockTimeUnlocked(lockTime, bestHeight, currentTimestamp);
    }

    /**
     * Check whether a Bitcoin lockTime is unlocked.
     *
     * @param lockTime         The lockTime value to check
     * @param bestHeight       Current block height
     * @param currentTimestamp Current block timestamp in seconds
     * @return true if unlocked and spendable, false if still locked
     */
    private static boolean isLockTimeUnlocked(long lockTime, long bestHeight, long currentTimestamp) {
        final long LOCKTIME_THRESHOLD = 500_000_000L;

        if (lockTime == 0) return true;

        if (lockTime < LOCKTIME_THRESHOLD) {
            return bestHeight >= lockTime;
        } else {
            return currentTimestamp >= lockTime;
        }
    }

    public static Transaction createTx(RawTxInfo rawTxInfo, MainNetParams mainNetwork) {
        if (rawTxInfo.getInputs() == null || rawTxInfo.getInputs().isEmpty()) {
            log.debug("The sender is absent.");
            return null;
        }

        // Track CLTV outputs to append to opReturn

        if(rawTxInfo.getChangeTo()==null) {
            if(rawTxInfo.getSender()!=null)rawTxInfo.setChangeTo(rawTxInfo.getSender());
            else rawTxInfo.setChangeTo(rawTxInfo.getInputs().get(0).getOwner());
            if(rawTxInfo.getChangeTo()==null) return null;
        }
        if(rawTxInfo.getSenderMultisig()!=null){
            String multisignFid = rawTxInfo.getSenderMultisig().getId();
            if(rawTxInfo.getSender()==null)rawTxInfo.setSender(multisignFid);
            else if(!rawTxInfo.getSender().equals(multisignFid))return null;
        }

        if(rawTxInfo.getFeeRate()==null || rawTxInfo.getFeeRate()==0)
            rawTxInfo.setFeeRate(DEFAULT_FEE_RATE);
//        long fee;

        FeeResult feeResult = calcFee(rawTxInfo);
        if(feeResult.fee==null)return null;

        Transaction transaction = new Transaction(mainNetwork);

        // Check if any inputs have lockTime requirements (spending CLTV UTXOs)
        // When spending CLTV outputs, transaction lockTime MUST be set to >= the CLTV value
        // Note: This is different from creating CLTV outputs, where we don't set transaction lockTime
        long maxInputLockTime = 0;
        for (Cash input : rawTxInfo.getInputs()) {
            if (input.getLockTime() != null && input.getLockTime() > maxInputLockTime) {
                maxInputLockTime = input.getLockTime();
            }
        }
        if (maxInputLockTime > 0) {
            transaction.setLockTime(maxInputLockTime);
            rawTxInfo.setLockTime(maxInputLockTime);
            log.debug("Set transaction lockTime to " + maxInputLockTime + " for spending CLTV UTXO");
        }

        long totalOutput = 0;

        long totalMoney = addInputToTx(rawTxInfo.getInputs(), mainNetwork,transaction);

        if(rawTxInfo.getOutputs() !=null && !rawTxInfo.getOutputs().isEmpty()){
//            int p2shIndex = 0; // Index to track which P2SH info to use
            for (Cash output : rawTxInfo.getOutputs()) {
                long value = FchUtils.coinToSatoshi(output.getAmount());
                totalOutput += value;

                Script redeemScript;

                if(output.getRedeemScript()!=null){
                    redeemScript = new Script( Hex.fromHex(output.getRedeemScript()));
                    // Create P2SH output from the redeemScript
                    Script p2shScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
                    transaction.addOutput(Coin.valueOf(value), p2shScript);

                } else {
                    // Regular P2PKH output (no time lock, no multisig)
                    transaction.addOutput(Coin.valueOf(value), Address.fromBase58(mainNetwork, output.getOwner()));
                }

            }
        }
        // CRITICAL FIX: Simplified change output logic
        // The fee calculation (calcFee) now already determines if change output will exist
        // Just check if there's enough input and create change if applicable
        if ((totalOutput + feeResult.fee()) > totalMoney) {
            log.debug("Input is not enough: input=" + totalMoney +
                    ", output=" + totalOutput + ", fee=" + feeResult.fee());
            return null;
        }

        long change = totalMoney - totalOutput - feeResult.fee();
        if (change > Constants.DustInSatoshi) {
            // Change output is ALWAYS regular P2PKH (not time-locked), sent back to sender immediately
            // This ensures the sender can spend the change immediately, even in CLTV transactions
            transaction.addOutput(Coin.valueOf(change), Address.fromBase58(mainNetwork, rawTxInfo.getChangeTo()));
            log.debug( "Added change output: " + change + " satoshis to " + rawTxInfo.getChangeTo());
        } else {
            log.debug( "No change output: change=" + change + " satoshis (below dust threshold)");
        }

        // Add opReturn to transaction (finalOpReturnBytes was already calculated earlier)
        if (feeResult.finalOpReturnBytes() != null && feeResult.finalOpReturnBytes().length > 0) {
            try {
                Script opreturnScript = ScriptBuilder.createOpReturnScript(feeResult.finalOpReturnBytes());
                transaction.addOutput(Coin.ZERO, opreturnScript);
            } catch (Exception e) {
                log.debug("Failed to create opreturn script: "+e.getMessage());
                return null;
            }
        }

        return transaction;
    }

    public record FeeResult(Long fee, byte[] finalOpReturnBytes, List<P2SH> p2SHOutputs) {
    }

    @NotNull
    public static FeeResult calcFee(RawTxInfo rawTxInfo) {
        // Get fee rate, use default if not set
        double feeRate = (rawTxInfo.getFeeRate() != null && rawTxInfo.getFeeRate() > 0)
                ? rawTxInfo.getFeeRate() : DEFAULT_FEE_RATE;
        long feeRateLong = (long) (feeRate / 1000 * COIN_TO_SATOSHI);

        // Process outputs to determine P2SH outputs and build opReturn
        byte[] finalOpReturnBytes = (rawTxInfo.getOpReturn() != null && !rawTxInfo.getOpReturn().isEmpty())
                ? rawTxInfo.getOpReturn().getBytes() : new byte[0];

        List<P2SH> p2SHOutputs = new ArrayList<>();
        long totalOutputSize = 0;
        long totalOutputValue = 0;

        if (rawTxInfo.getOutputs() != null && !rawTxInfo.getOutputs().isEmpty()) {
            for (Cash output : rawTxInfo.getOutputs()) {
                totalOutputValue += output.getValue();
                if (output.getRedeemScript()!=null ) {
                    // P2SH output: 8 value + 1 scriptLen + 23 P2SH script = 32 bytes
                    totalOutputSize += 32;
                    P2SH p2SHInfo = new P2SH(output.getRedeemScript());//P2SH.p2shFromSendTo(output);
                    p2SHOutputs.add(p2SHInfo);
                } else if(output.getOwner().startsWith("3")){
                    totalOutputSize += 32;
                }
                else {
                    // Regular P2PKH output: 8 value + 1 scriptLen + 25 P2PKH script = 34 bytes
                    totalOutputSize += 34;
                }
            }
        }

        // Build final opReturn bytes if there are P2SH outputs
        if (!p2SHOutputs.isEmpty()) {
            String redeemScriptListJson = P2SH.makeRedeemScriptListJsonForOpReturn(p2SHOutputs);
            finalOpReturnBytes = redeemScriptListJson.getBytes(StandardCharsets.UTF_8);
        }

        // Calculate opReturn size
        int opReturnLen = (finalOpReturnBytes.length > 0)
                ? calcOpReturnLen(finalOpReturnBytes.length) : 0;

        // Calculate input sizes - parse each input's redeemScript to determine its type
        long totalInputSize = 0;
        long totalInputValue = 0;

        for (Cash input : rawTxInfo.getInputs()) {
            totalInputValue += input.getValue();
            if(rawTxInfo.getSenderMultisig()!=null || input.getLockTime()!=null){
                if( input.getRedeemScript()==null || input.getRedeemScript().isEmpty()){
                    // Extract m and n from redeemScript
                    Multisig multisig = rawTxInfo.getSenderMultisig();

                    if (multisig != null) {
                        int n = multisig.getN();
                        int m = multisig.getM();

                        // CRITICAL FIX: Calculate correct redeemScript length based on whether input has CLTV
                        int redeemScriptLen = 0;
                        if (input.getLockTime() != null && input.getLockTime() > 0) {
                            // CLTV+multisig: Build the actual redeemScript to get accurate size
                            Script cltvMultisigScript = P2SH.makeMultisigLockTimeRedeemScript(
                                    input.getLockTime(),
                                    multisig.getPubkeys(),
                                    multisig.getM(),
                                    multisig.getN()
                            );
                            redeemScriptLen = cltvMultisigScript.getProgram().length;
                        } else {
                            // Plain multisig: Use stored redeemScript length or calculate
                            if (multisig.getRedeemScript() != null) {
                                redeemScriptLen = Hex.fromHex(multisig.getRedeemScript()).length;
                            }
                            // else: multisigInputSize will calculate the length when redeemScriptLen=0
                        }

                        totalInputSize += multisigInputSize(n, m, redeemScriptLen);
                    }else
                        return new FeeResult(null,null,null);
                } else {
                    // P2SH input - parse redeemScript to determine type
                    String redeemScriptHex = input.getRedeemScript();
                    P2SH p2SH = new P2SH(redeemScriptHex);
                    if(p2SH.getId()==null)
                        return new FeeResult(null,null,null);

                    // Use improved P2SH input size calculation
                    totalInputSize += calculateP2SHInputSize(p2SH);
                }
            } else {
                // Regular P2PKH input: ~141 bytes
                totalInputSize += 141;
            }
        }

        // Calculate total transaction size
        long baseLength = 10; // Version(4) + input count(1) + output count(1) + locktime(4)

        // CRITICAL FIX: Determine change output size based on changeTo address type
        // P2PKH (1xxx/Fxxx): 8 (value) + 1 (scriptLen) + 25 (P2PKH script) = 34 bytes
        // P2SH (3xxx): 8 (value) + 1 (scriptLen) + 23 (P2SH script) = 32 bytes
        // If changeTo is not set, use sender address to determine change output type
        String changeAddress = rawTxInfo.getChangeTo();
        if (changeAddress == null || changeAddress.isEmpty()) {
            changeAddress = rawTxInfo.getSender();
        }

        long changeOutputSize = getChangeFee(changeAddress);
        // CRITICAL FIX: Calculate fee correctly by checking if change output will actually be created
        // First, calculate size and fee WITHOUT change output
        long txSizeWithoutChange = baseLength + totalInputSize + totalOutputSize + opReturnLen;
        long feeWithoutChange = feeRateLong * txSizeWithoutChange;

        // Calculate what the change amount would be
        long potentialChange = totalInputValue - totalOutputValue - feeWithoutChange;

        // Determine if change output will be created
        boolean willHaveChange;
        long finalFee;

        if (potentialChange > Constants.DustInSatoshi) {

            // Recalculate with change output included
            long txSizeWithChange = txSizeWithoutChange + changeOutputSize;
            long feeWithChange = feeRateLong * txSizeWithChange;

            // Check if there's still enough for change after accounting for its own fee
            long actualChange = totalInputValue - totalOutputValue - feeWithChange;

            if (actualChange > Constants.DustInSatoshi) {
                // Yes, change output will be created
                willHaveChange = true;
                finalFee = feeWithChange;
            } else {
                // No, the change would be too small even accounting for its fee
                willHaveChange = false;
                finalFee = feeWithoutChange;
            }
        } else {
            // Change is already below dust threshold, no change output
            willHaveChange = false;
            finalFee = feeWithoutChange;
        }

        log.debug( "Fee calculation: totalInput=" + totalInputValue +
                ", totalOutput=" + totalOutputValue +
                ", feeWithoutChange=" + feeWithoutChange +
                ", potentialChange=" + potentialChange +
                ", willHaveChange=" + willHaveChange +
                ", finalFee=" + finalFee);

        return new FeeResult(finalFee, finalOpReturnBytes, p2SHOutputs);
    }

    public static long getChangeFee(String changeToFid) {
        long changeFee;
        if(changeToFid.startsWith("3"))
            changeFee = CHANGE_P2SH_OUTPUT_FEE;
        else changeFee = CHANGE_OUTPUT_FEE;
        return changeFee;
    }

//
//    /**
//     * Create a map of hash160Hex to redeemScriptHex for opReturn
//     * This is the NEW format that eliminates duplicates for outputs to the same P2SH address
//     *
//     * @param p2SHOutputs List of P2SH outputs to include in the map
//     * @return JSON string representing the hash160->redeemScript map
//     */
//    @NotNull
//    public static String makeRedeemScriptListJsonForOpReturn(List<P2SH> p2SHOutputs) {
//        List<String> hash160ToRedeemScript = new ArrayList<>();
//
//        for (P2SH p2sh : p2SHOutputs) {
//            if (p2sh.getRedeemScript() == null || p2sh.getRedeemScript().isEmpty()) {
//                log.debug( "Skipping P2SH with null/empty redeemScript");
//                continue;
//            }
//
//            // Validate redeemScript syntax strictly
//            if (!validateRedeemScriptSyntax(p2sh.getRedeemScript())) {
//                throw new IllegalArgumentException("Invalid redeemScript syntax - script would be unspendable: "
//                        + p2sh.getRedeemScript().substring(0, Math.min(40, p2sh.getRedeemScript().length())) + "...");
//            }
//
//            // Calculate hash160 of the redeemScript
//            byte[] redeemScriptBytes = Hex.fromHex(p2sh.getRedeemScript());
//            String scriptHex = Hex.toHex(redeemScriptBytes);
//
//            // Only add if not already present (automatic de-duplication)
//            if (!hash160ToRedeemScript.contains(scriptHex)) {
//                hash160ToRedeemScript.add(p2sh.getRedeemScript());
//                log.debug( "Added P2SH to map.");
//            } else {
//                log.debug( "Skipped duplicate P2SH");
//            }
//        }
//
//        return new Gson().toJson(hash160ToRedeemScript);
//    }


    /**
     * Calculate the transaction input size for a P2SH (Pay-to-Script-Hash) output
     * Different P2SH types have different sizes:
     * - CLTV (single-sig time lock): signature + pubkey + redeemScript
     * - MULTISIG: m signatures + redeemScript
     * - MULTISIG_CLTV: m signatures + redeemScript (CLTV+multisig combined)
     *
     * @param p2sh The P2SH information (type, m, n, redeemScript)
     * @return The estimated input size in bytes
     */
    private static long calculateP2SHInputSize(P2SH p2sh) {
        int redeemScriptLen = p2sh.getRedeemScript().length() / 2; // hex to bytes

        P2SH.P2shType inputType = p2sh.getType();

        return switch (inputType) {
            case CLTV ->
                // Single-sig CLTV P2SH
                // scriptSig format: <signature(64)+sighash(1)> <pubkey(33)> <redeemScript>
                // Signature with sighash flag: 64 + 1 + 1 (length byte) = 66 bytes
                // Compressed pubkey: 33 + 1 (length byte) = 34 bytes
                // RedeemScript: variable length + length byte
                    calculateSingleSigP2SHInputSize(redeemScriptLen);
            case MULTISIG ->
                // Multisig P2SH (no CLTV)
                // scriptSig format: OP_0 <sig1> <sig2> ... <sigM> <redeemScript>
                    multisigInputSize(p2sh.getN(), p2sh.getM(), redeemScriptLen);
            case MULTISIG_CLTV ->
                // Multisig + CLTV P2SH
                // scriptSig format: OP_0 <sig1> <sig2> ... <sigM> <redeemScript>
                // The redeemScript contains both CLTV and multisig, so redeemScriptLen is accurate
                    multisigInputSize(p2sh.getN(), p2sh.getM(), redeemScriptLen);
            default -> {
                log.warn("Unknown P2SH type: " + inputType + ", using generic calculation");
                yield calculateSingleSigP2SHInputSize(redeemScriptLen);
            }
        };
    }

    /**
     * Calculate the input size for a multisig P2SH transaction
     * ScriptSig format: OP_0 <sig1> <sig2> ... <sigM> <redeemScript>
     *
     * @param n Total number of public keys in the multisig
     * @param m Required number of signatures
     * @param redeemScriptLen Length of the redeemScript in bytes (0 to auto-calculate for plain multisig)
     * @return Input size in bytes
     */
    private static long multisigInputSize(int n, int m, long redeemScriptLen) {
        // If redeemScriptLen not provided, calculate it for a standard multisig (no CLTV)
        // Format: <m> <pubkey1> ... <pubkeyN> <n> OP_CHECKMULTISIG
        if(redeemScriptLen <= 0) {
            long op_mLen = 1;
            long op_nLen = 1;
            long pubkeyLen = 33; // compressed pubkey
            long pubkeyLenLen = 1; // length byte for each pubkey
            long op_checkmultisigLen = 1;
            redeemScriptLen = op_mLen + (n * (pubkeyLenLen + pubkeyLen)) + op_nLen + op_checkmultisigLen;
        }

        // Calculate the push operation size for the redeemScript
        // Bitcoin script push operations:
        // - For data < 76 bytes: 1 byte (implicit OP_PUSHDATA)
        // - For 76-255 bytes: 2 bytes (OP_PUSHDATA1 + 1 byte length)
        // - For 256-65535 bytes: 3 bytes (OP_PUSHDATA2 + 2 byte length)
        long redeemScriptPushLen;
        if (redeemScriptLen < 76) {
            redeemScriptPushLen = 1; // length byte only
        } else if (redeemScriptLen < 256) {
            redeemScriptPushLen = 2; // OP_PUSHDATA1 + 1 length byte
        } else {
            redeemScriptPushLen = 3; // OP_PUSHDATA2 + 2 length bytes
        }

        // ScriptSig components:
        // 1. OP_0 (1 byte) - required for multisig due to off-by-one bug
        long zeroByteLen = 1;

        // 2. m signatures, each with: length byte + 64 bytes signature + 1 byte sighash
        long sigHashLen = 1;
        long signLen = 64; // Schnorr signature
        long signLenLen = 1; // length byte for each signature
        long mSignLen = m * (signLenLen + signLen + sigHashLen);

        // 3. RedeemScript: push operation + redeemScript bytes
        long redeemScriptTotalLen = redeemScriptPushLen + redeemScriptLen;

        // Total scriptSig length
        long scriptLength = zeroByteLen + mSignLen + redeemScriptTotalLen;

        // scriptSig length is encoded as VarInt
        long scriptVarInt = VarInt.sizeOf(scriptLength);

        // Total input size: txid(32) + vout(4) + scriptSig length VarInt + scriptSig + sequence(4)
        return 32 + 4 + scriptVarInt + scriptLength + 4;
    }
    /**
     * Calculate size for single-sig P2SH input (CLTV or other single-sig scripts)
     * @param redeemScriptLen Length of the redeemScript in bytes
     * @return Input size in bytes
     */
    private static long calculateSingleSigP2SHInputSize(int redeemScriptLen) {
        // scriptSig components:
        // - Signature: 64 bytes (Schnorr) + 1 byte sighash flag + 1 byte length = 66 bytes
        // - Pubkey: 33 bytes (compressed) + 1 byte length = 34 bytes
        // - RedeemScript: redeemScriptLen + VarInt length

        long redeemScriptVarInt = VarInt.sizeOf(redeemScriptLen);
        long signLen = 64; // Schnorr signature
        long sigHashLen = 1; // sighash flag
        long signLenLen = 1; // length byte for signature
        long pubkeyLen = 33; // compressed pubkey
        long pubkeyLenLen = 1; // length byte for pubkey

        long scriptLength = signLenLen + signLen + sigHashLen + pubkeyLenLen + pubkeyLen + redeemScriptVarInt + redeemScriptLen;
        long scriptVarInt = VarInt.sizeOf(scriptLength);

        // Total: txid(32) + index(4) + scriptSig varint + scriptSig + sequence(4)
        return 32 + 4 + scriptVarInt + scriptLength + 4;
    }

//    public static String createUnsignedTx(RawTxInfo rawTxInfo) {
//        Transaction tx = createUnsignedTx(rawTxInfo, FchMainNetwork.MAINNETWORK);
//        if(tx==null)return null;
//        byte[] txBytes = tx.bitcoinSerialize();
//        return Hex.toHex(txBytes);
//    }

    public static Transaction createUnsignedTx(RawTxInfo rawTxInfo, MainNetParams mainnetwork) {
        try {
            if (rawTxInfo.getInputs().get(0).getOwner() == null)
                rawTxInfo.getInputs().get(0).setOwner(rawTxInfo.getSender());
        }catch (Exception e){
            log.error("The sender is absent.");
            return null;
        }
        return createUnsignedTx(rawTxInfo.getInputs(), rawTxInfo.getOutputs(), rawTxInfo.getOpReturn(), rawTxInfo.getSenderMultisig(), rawTxInfo.getFeeRate(), null, mainnetwork);
    }

//    public static Transaction createUnsignedTx(RawTxInfo rawTxInfo, MainNetParams mainnetwork) {
//        try {
//            if (rawTxInfo.getInputs().get(0).getOwner() == null)
//                rawTxInfo.getInputs().get(0).setOwner(rawTxInfo.getSender());
//        }catch (Exception e){
//            log.error("The sender is absent.");
//            return null;
//        }
//        return createUnsignedTx(rawTxInfo.getInputs(), rawTxInfo.getOutputs(), rawTxInfo.getOpReturn(), rawTxInfo.getMultisign(), rawTxInfo.getFeeRate(), null, mainnetwork);
//    }

    public static Transaction createUnsignedTx(List<Cash> inputs, List<Cash> outputs, String opReturn, Multisig multisigForMultiSign, Double feeRate, String changeToFid, MainNetParams mainnetwork) {
        byte[] opReturnBytes= null;
        if(opReturn!=null) opReturnBytes = opReturn.getBytes();
        if(changeToFid==null)
            changeToFid = inputs.get(0).getOwner();

        boolean isMultiSign = inputs.get(0).getOwner().startsWith("3");

        if(feeRate==null || feeRate==0)feeRate=DEFAULT_FEE_RATE;
        long fee;

        int inputSize = inputs.size();
        int outputSize = outputs ==null ? 0 : outputs.size();
        int opReturnBytesLen = opReturn ==null ? 0 : opReturnBytes.length;

        fee = calcFee(inputSize, outputSize, opReturnBytesLen, feeRate, isMultiSign, multisigForMultiSign);

        Transaction transaction = new Transaction(mainnetwork);

        long totalOutput = 0;

        long totalMoney = addInputToTx(inputs, mainnetwork, transaction);

        if(outputs !=null && outputs.size()>0){
            for (Cash output : outputs) {
                long value = utils.FchUtils.coinToSatoshi(output.getAmount());
                totalOutput += value;
                transaction.addOutput(Coin.valueOf(value), Address.fromBase58(mainnetwork, output.getOwner()));
            }
        }
        long changeOutputFee = 34L * utils.FchUtils.coinToSatoshi(feeRate/ 1000);

        if(!(totalOutput + fee - changeOutputFee == totalMoney)){

            if ((totalOutput + fee ) > totalMoney) {
                System.out.println("Input is not enough");
                return null;
            }

            long change = totalMoney - totalOutput - fee;
            if (change > Constants.DustInSatoshi) {
                transaction.addOutput(Coin.valueOf(change), Address.fromBase58(mainnetwork, changeToFid));
            }
        }

        if (opReturn != null && opReturnBytes.length>0) {
            try {
                Script opreturnScript = ScriptBuilder.createOpReturnScript(opReturnBytes);
                transaction.addOutput(Coin.ZERO, opreturnScript);
            } catch (Exception e) {
                log.error("Failed to create opreturn script: "+e.getMessage());
                return null;
            }
        }

        return transaction;
    }

//    private static long addInputToTx(List<Cash> valueTxIdIndexCashList, MainNetParams mainnetwork, Transaction transaction) {
//        long totalMoney=0;
//
//        for (Cash input : valueTxIdIndexCashList) {
//            totalMoney += input.getValue();
//            TransactionOutPoint outPoint = new TransactionOutPoint(mainnetwork, input.getBirthIndex(), Sha256Hash.wrap(input.getBirthTxId()));
//            TransactionInput unsignedInput = new TransactionInput(mainnetwork, null, new byte[0], outPoint, Coin.valueOf(input.getValue()));
//            transaction.addInput(unsignedInput);
//        }
//
//        return totalMoney;
//    }
    private static long addInputToTx(List<Cash> valueTxIdIndexCashList,MainNetParams mainnetwork, Transaction transaction) {
        long totalMoney=0;
        for (Cash input : valueTxIdIndexCashList) {
            totalMoney += input.getValue();
            TransactionOutPoint outPoint = new TransactionOutPoint(mainnetwork, input.getBirthIndex(), Sha256Hash.wrap(input.getBirthTxId()));
            TransactionInput unsignedInput = new TransactionInput(mainnetwork, null, new byte[0], outPoint, Coin.valueOf(input.getValue()));

            // CRITICAL: For CLTV inputs, sequence MUST be < 0xFFFFFFFF to enable lockTime validation
            // If sequence = 0xFFFFFFFF (default), OP_CHECKLOCKTIMEVERIFY will fail and the transaction will be rejected
            if (input.getLockTime() != null && input.getLockTime() > 0) {
                unsignedInput.setSequenceNumber(0xFFFFFFFEL); // Enable lockTime checking
                log.debug("Set sequence to 0xFFFFFFFE for CLTV input: " + input.getBirthTxId() + ":" + input.getBirthIndex());
            }
            // else: default sequence 0xFFFFFFFF is fine for regular inputs
            transaction.addInput(unsignedInput);
        }

        return totalMoney;
    }

    public static String signOffLineTx(byte[] priKey, RawTxInfo rawTxInfo, core.fch.FchMainNetwork mainnetwork) {
        Transaction transaction = parseOffLineTx(rawTxInfo,mainnetwork);
        return signTx(priKey,transaction,rawTxInfo.getInputs());
    }

    @Test
    public void testTx(){
        MainNetParams mainnetwork = core.fch.FchMainNetwork.get();//BtcMainNetParams.get();//
        Transaction transaction = new Transaction(mainnetwork);
        TransactionOutPoint outPoint = new TransactionOutPoint(mainnetwork, 1, Sha256Hash.wrap("e93de4b34ee09b3c1c8bea1b083db6a16e48d7a35a27a85bae89ed478e78d07e"));
        TransactionInput unsignedInput = new TransactionInput(mainnetwork, null, new byte[0], outPoint,Coin.valueOf(1822387504));
        transaction.addInput(unsignedInput);

        try{
            byte[] txBytes = transaction.bitcoinSerialize();
            String hex = Hex.toHex(txBytes);
            System.out.println("RawTx:"+ hex);
            System.out.println("Decoded:"+decodeTxFch(hex, mainnetwork));

            byte[] hash160 = KeyTools.addrToHash160("FEk41Kqjar45fLDriztUDTUkdki7mmcjWK");
            transaction.addOutput(Coin.valueOf(1812387504),new Address(mainnetwork,hash160));
            txBytes = transaction.bitcoinSerialize();
            hex = Hex.toHex(txBytes);
            System.out.println("RawTx:"+ hex);
            System.out.println("Decoded:"+decodeTxFch(hex, mainnetwork));
        }catch (Exception e){
            e.printStackTrace();
        }


    }

//
//    public static String signTx(byte[] priKey, Transaction transaction) {
//        if(priKey==null){
//            return null;
//        }
//        ECKey eckey = ECKey.fromPrivate(priKey);
//
//        List<TransactionInput> inputs = transaction.getInputs();
//        for (int i = 0; i < inputs.size(); ++i) {
//            TransactionInput input = inputs.get(i);
//            Script script = ScriptBuilder.createP2PKHOutputScript(eckey);
//            Coin value = input.getValue();
//            if(value==null)continue;
//            SchnorrSignature signature = transaction.calculateSchnorrSignature(i, eckey, script.getProgram(), Coin.valueOf(value.getValue()), Transaction.SigHash.ALL, false);
//            Script schnorr = ScriptBuilder.createSchnorrInputScript(signature, eckey);
//            transaction.getInput(i).setScriptSig(schnorr);
//        }
//
//        byte[] signResult = transaction.bitcoinSerialize();
//        return Hex.toHex(signResult);
//    }

    /**
     * Sign a transaction with support for both regular P2PKH and P2SH inputs (including CLTV)
     * @param prikey Private key for signing
     * @param transaction Transaction to sign
     * @param inputs List of Cash inputs with redeemScript information
     * @return Signed transaction in hex format
     */
    public static String signTx(byte[] prikey, Transaction transaction, List<Cash> inputs) {
        if(prikey==null){
            return null;
        }

        ECKey eckey = ECKey.fromPrivate(prikey);

        List<TransactionInput> txInputs = transaction.getInputs();
        for (int i = 0; i < txInputs.size(); ++i) {
            TransactionInput input = txInputs.get(i);
            Coin value = input.getValue();
            if(value==null)continue;

            // Check if this input has a redeemScript (P2SH: multisig, CLTV, etc.)
            Cash cashInput = (inputs != null && i < inputs.size()) ? inputs.get(i) : null;

            // DEBUG: Log input details
            if (cashInput != null) {
                log.debug("Input " + i + ": txId=" + cashInput.getBirthTxId() +
                        ":" + cashInput.getBirthIndex() +
                        ", type=" + cashInput.getType() +
                        ", lockTime=" + cashInput.getLockTime() +
                        ", redeemScript=" + (cashInput.getRedeemScript() != null ?
                        cashInput.getRedeemScript().substring(0, Math.min(40, cashInput.getRedeemScript().length())) + "..." : "null"));
            }

            if (cashInput != null && cashInput.getRedeemScript() != null && !cashInput.getRedeemScript().isEmpty()) {
                // P2SH input - use redeemScript for signing
                // Note: redeemScript is always stored in hex format (Cash.setRedeemScript handles conversion)
                String redeemScriptStr = cashInput.getRedeemScript();
                byte[] redeemScriptBytes;
                try {
                    if(!Hex.isHexString(redeemScriptStr)){
                        redeemScriptStr = P2SH.scriptAsmToHex(redeemScriptStr);
                        log.debug("Input " + i + ": Converted P2SH redeemScript to" + redeemScriptStr );
                    }
                    if(redeemScriptStr==null)return null;
                    redeemScriptBytes = Hex.fromHex(redeemScriptStr);
                    log.debug("Input " + i + ": Using P2SH redeemScript (length: " + redeemScriptBytes.length + " bytes)");
                } catch (Exception e) {
                    log.debug("Failed to parse redeemScript hex for input " + i + ": " + e.getMessage());
                    return null;
                }

                Script redeemScript = new Script(redeemScriptBytes);

                // Calculate signature using the redeemScript
                SchnorrSignature signature = transaction.calculateSchnorrSignature(
                        i, eckey, redeemScript.getProgram(), value, Transaction.SigHash.ALL, false);

                // For P2SH (including CLTV): scriptSig = <signature> <pubkey> <redeemScript>
                ScriptBuilder builder = new ScriptBuilder();
                builder.data(signature.encodeToBitcoin());
                builder.data(eckey.getPubKey());
                builder.data(redeemScriptBytes);

                transaction.getInput(i).setScriptSig(builder.build());
                log.debug("Signed P2SH input " + i + " with redeemScript (type=" + cashInput.getType() + ")");
            } else {
                // Regular P2PKH input
                Script script = ScriptBuilder.createP2PKHOutputScript(eckey);
                SchnorrSignature signature = transaction.calculateSchnorrSignature(
                        i, eckey, script.getProgram(), value, Transaction.SigHash.ALL, false);
                Script schnorr = ScriptBuilder.createSchnorrInputScript(signature, eckey);
                transaction.getInput(i).setScriptSig(schnorr);
                log.debug("Signed regular P2PKH input " + i + (cashInput != null ? " (type=" + cashInput.getType() + ")" : ""));
            }
        }

        byte[] signResult = transaction.bitcoinSerialize();
        return Hex.toHex(signResult);
    }

    /**
     * Legacy method - signs transaction without P2SH support
     * Use signTx(byte[], Transaction, List<Cash>) for P2SH/CLTV support
     */
    public static String signTx(byte[] prikey, Transaction transaction) {
        return signTx(prikey, transaction, null);
    }

    public static long calcFee(int inputSize, int outputSize, int opReturnBytesLen, double feeRate, boolean isMultiSign, Multisig multisigForMultiSign) {
        long fee;
        if(isMultiSign) {
            long feeRateLong = (long) (feeRate / 1000 * COIN_TO_SATOSHI);
            fee = feeRateLong * TxCreator.calcSizeMultiSig(inputSize, outputSize, opReturnBytesLen, multisigForMultiSign.getM(), multisigForMultiSign.getN());
        }else {
            long txSize = calcTxSize(inputSize, outputSize, opReturnBytesLen);
            fee =calcFee(txSize, feeRate);
        }
        return fee;
    }


    public static String signRawTx(String valuesAndRawTx, byte[] priKey, MainNetParams mainnetwork) {
        Transaction transaction = parseOldCsRawTxToTx(valuesAndRawTx, mainnetwork);
        if (transaction == null) return null;
        return signTx(priKey, transaction);
    }

    //Off line TX methods

    /**
     * Parse user off-line TX request json
     */
    public static RawTxInfo parseDataForOffLineTxFromOther(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, RawTxInfo.class);
    }

    /**
     * Convert off-line TX information to Transaction.
     */
    public static Transaction parseOffLineTx(RawTxInfo rawTxInfo, MainNetParams mainnetwork) {
        List<Cash> cashList = rawTxInfo.getInputs();
        List<Cash> sendToList = rawTxInfo.getOutputs();
        String msg = rawTxInfo.getOpReturn();
        return createUnsignedTx(cashList, sendToList, msg, rawTxInfo.getSenderMultisig(), rawTxInfo.getFeeRate(), null, mainnetwork);
    }
//
//    public static class OffLineTxInfo {
//        private String sender;
//        private List<SendTo> outputs;
//        private Long cd;
//        private String msg;
//        private String ver;
//
//        public String getVer() {
//            return ver;
//        }
//
//        public void setVer(String ver) {
//            this.ver = ver;
//        }
//
//        public String getSender() {
//            return sender;
//        }
//
//        public void setSender(String sender) {
//            this.sender = sender;
//        }
//
//        public List<SendTo> getOutputs() {
//            return outputs;
//        }
//
//        public void setOutputs(List<SendTo> outputs) {
//            this.outputs = outputs;
//        }
//
//        public Long getCd() {
//            return cd;
//        }
//
//        public void setCd(Long cd) {
//            this.cd = cd;
//        }
//
//        public String getMsg() {
//            return msg;
//        }
//
//        public void setMsg(String msg) {
//            this.msg = msg;
//        }
//    }


    //Old methods
    public static String createTx(List<TxInput> inputs, List<TxOutput> outputs, String opReturn, String returnAddr) {
        FchMainNetwork mainnetwork = FchMainNetwork.MAINNETWORK;
        return createTxClassic(mainnetwork, inputs, outputs, opReturn, returnAddr, 0);
    }


    public static String createTxClassic(NetworkParameters networkParameters, List<TxInput> inputs, List<TxOutput> outputs, String opReturn, String returnAddr, double feeRateDouble) {

        long txSize = opReturn == null ? calcTxSize(inputs.size(), outputs.size(), 0) : calcTxSize(inputs.size(), outputs.size(), opReturn.getBytes().length);

        long feeRateLong;
        if (feeRateDouble != 0) {
            feeRateLong = (long) (feeRateDouble / 1000 * COIN_TO_SATOSHI);
        } else feeRateLong = (long) (DEFAULT_FEE_RATE / 1000 * COIN_TO_SATOSHI);
        long fee = feeRateLong * txSize;
        Transaction transaction = new Transaction(networkParameters);

        long totalMoney = 0;
        long totalOutput = 0;

        List<ECKey> ecKeys = new ArrayList<>();
        for (TxOutput output : outputs) {
            totalOutput += output.getAmount();
            transaction.addOutput(Coin.valueOf(output.getAmount()), Address.fromBase58(networkParameters, output.getAddress()));
        }

        if (opReturn != null && !opReturn.isEmpty()) {
            try {
                Script opreturnScript = ScriptBuilder.createOpReturnScript(opReturn.getBytes(StandardCharsets.UTF_8));
                transaction.addOutput(Coin.ZERO, opreturnScript);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        for (TxInput input : inputs) {
            totalMoney += input.getAmount();

            ECKey eckey = ECKey.fromPrivate(input.getPrikey32());

            ecKeys.add(eckey);
            UTXO utxo = new UTXO(Sha256Hash.wrap(input.getTxId()), input.getIndex(), Coin.valueOf(input.getAmount()), 0, false, ScriptBuilder.createP2PKHOutputScript(eckey));
            TransactionOutPoint outPoint = new TransactionOutPoint(networkParameters, utxo.getIndex(), utxo.getHash());
            TransactionInput unsignedInput = new TransactionInput(new FchMainNetwork(), transaction, new byte[0], outPoint);
            transaction.addInput(unsignedInput);
        }
        if ((totalOutput + fee) > totalMoney) {
            throw new RuntimeException("input is not enough");
        }
        long change = totalMoney - totalOutput - fee;

        if (change > Constants.DustInSatoshi) {
            if (returnAddr == null)
                returnAddr = ECKey.fromPrivate(inputs.get(0).getPrikey32()).toAddress(networkParameters).toBase58();
            transaction.addOutput(Coin.valueOf(change), Address.fromBase58(networkParameters, returnAddr));
        }

        for (int i = 0; i < inputs.size(); ++i) {
            TxInput input = inputs.get(i);
            ECKey eckey = ecKeys.get(i);
            Script script = ScriptBuilder.createP2PKHOutputScript(eckey);
            SchnorrSignature signature = transaction.calculateSchnorrSignature(i, eckey, script.getProgram(), Coin.valueOf(input.getAmount()), Transaction.SigHash.ALL, false);
            Script schnorr = ScriptBuilder.createSchnorrInputScript(signature, eckey);
            transaction.getInput(i).setScriptSig(schnorr);
        }

        byte[] signResult = transaction.bitcoinSerialize();
        return Hex.toHex(signResult);
    }


//    public static List<TxInput> cashListToTxInputList(List<Cash> cashList, byte[] priKey32) {
//        List<TxInput> txInputList = new ArrayList<>();
//        for (Cash cash : cashList) {
//            TxInput txInput = cashToTxInput(cash, priKey32);
//            if (txInput != null) {
//                txInputList.add(txInput);
//            }
//        }
//        if (txInputList.isEmpty()) return null;
//        return txInputList;
//    }
//
//    public static TxInput cashToTxInput(Cash cash, byte[] priKey32) {
//        if (cash == null) {
//            System.out.println("Cash is null.");
//            return null;
//        }
//        if (!cash.isValid()) {
//            System.out.println("Cash has been spent.");
//            return null;
//        }
//        TxInput txInput = new TxInput();
//
//        txInput.setPrikey32(priKey32);
//        txInput.setAmount(cash.getValue());
//        txInput.setTxId(cash.getBirthTxId());
//        txInput.setIndex(cash.getBirthIndex());
//
//        return txInput;
//    }

    //For old CryptoSign off-line TX

    public static String makeOffLineTxRequiredJson(RawTxInfo sendRequestForCs, List<Cash> meetList) {
        if(sendRequestForCs.getVer().equals("1"))return makeCsTxRequiredJsonV1(sendRequestForCs,meetList);
        sendRequestForCs.setInputs(meetList);
        return sendRequestForCs.toJson();
    }

        public static String makeCsTxRequiredJsonV1(RawTxInfo sendRequestForCs, List<Cash> meetList) {
        Gson gson = new Gson();
        StringBuilder RawTx = new StringBuilder("[");
        int i = 0;
        for (Cash cash : meetList) {
            if (i > 0) RawTx.append(",");
            RawTxForCsV1 rawTxForCsV1 = new RawTxForCsV1();
            rawTxForCsV1.setAddress(cash.getOwner());
            rawTxForCsV1.setAmount((double) cash.getValue() / COIN_TO_SATOSHI);
            rawTxForCsV1.setTxid(cash.getBirthTxId());
            rawTxForCsV1.setIndex(cash.getBirthIndex());
            rawTxForCsV1.setSeq(i);
            rawTxForCsV1.setDealType(RawTxForCsV1.DealType.INPUT);
            RawTx.append(gson.toJson(rawTxForCsV1));
            i++;
        }
        int j = 0;
        if (sendRequestForCs.getOutputs() != null) {
            for (Cash sendTo : sendRequestForCs.getOutputs()) {
                RawTxForCsV1 rawTxForCsV1 = new RawTxForCsV1();
                rawTxForCsV1.setAddress(sendTo.getOwner());
                rawTxForCsV1.setAmount(sendTo.getAmount());
                rawTxForCsV1.setSeq(j);
                rawTxForCsV1.setDealType(RawTxForCsV1.DealType.OUTPUT);
                RawTx.append(",");
                RawTx.append(gson.toJson(rawTxForCsV1));
                j++;
            }
        }

        if (sendRequestForCs.getOpReturn() != null) {
            RawTxForCsV1 rawOpReturnForCs = new RawTxForCsV1();
            rawOpReturnForCs.setMsg(sendRequestForCs.getOpReturn());
            rawOpReturnForCs.setSeq(j);
            rawOpReturnForCs.setDealType(RawTxForCsV1.DealType.OP_RETURN);
            RawTx.append(",");
            RawTx.append(gson.toJson(rawOpReturnForCs));
        }
        RawTx.append("]");
        return RawTx.toString();
    }

    public static Transaction parseOldCsRawTxToTx(String oldCsUnsignedTx, MainNetParams mainnetwork) {
        List<Cash> cashList = new ArrayList<>();
        List<Cash> sendToList = new ArrayList<>();
        String msg = null;

        // Parse the JSON array
        List<RawTxForCsV1> rawTxForCsV1List = parseRawTxForCsList(oldCsUnsignedTx);

        for (RawTxForCsV1 element : rawTxForCsV1List) {
            
            int dealType = element.getDealType();

            switch (dealType) {
                case 1: // Cash entries
                    Cash cash = new Cash();
                    cash.setOwner(element.getAddress());
                    cash.setValue((long)(element.getAmount() * COIN_TO_SATOSHI));
                    cash.setBirthTxId(element.getTxid());
                    cash.setBirthIndex(element.getIndex());
                    cashList.add(cash);
                    break;

                case 2: // SendTo entries
                    Cash sendTo = new Cash();
                    sendTo.setOwner(element.getAddress());
                    sendTo.setAmount(element.getAmount());
                    sendToList.add(sendTo);
                    break;

                case 3: // Message
                    msg = element.getMsg();
                    break;
            }
        }
        return createUnsignedTx(cashList, sendToList, msg, null, DEFAULT_FEE_RATE, null, mainnetwork);
    }

    private static List<RawTxForCsV1> parseRawTxForCsList(String oldCsUnsignedTx) {
        Gson gson = new Gson();
        return gson.fromJson(oldCsUnsignedTx, new TypeToken<List<RawTxForCsV1>>() {}.getType());
    }

// Sign TX
    public static String signSchnorrMultiSigTx(String multiSignDataJson, byte[] priKey, MainNetParams mainNetParams) {
        RawTxInfo  multiSignData = RawTxInfo .fromJson(multiSignDataJson,RawTxInfo.class);
        return signSchnorrMultiSigTx(multiSignData, priKey, mainNetParams).toJson();
    }

    /**
     * Sign a multisig transaction with support for CLTV inputs.
     * When spending CLTV multisig UTXOs, each input may have its own redeemScript
     * (stored on the Cash) or may require a CLTV+multisig redeemScript to be built.
     * Transaction lockTime and input sequence numbers are adjusted to satisfy CLTV rules.
     */
    public static RawTxInfo signSchnorrMultiSigTx(RawTxInfo multiSignData, byte[] priKey, MainNetParams mainnetwork) {

        byte[] rawTx = createTxBytes(multiSignData, mainnetwork);
        if (rawTx == null) rawTx = multiSignData.getRawTx();
        List<Cash> cashList = multiSignData.getInputs();
        Multisig multisig = multiSignData.getSenderMultisig();

        Transaction transaction = new Transaction(mainnetwork, rawTx);
        List<TransactionInput> inputs = transaction.getInputs();

        // Handle CLTV inputs: set sequence and transaction lockTime for time-locked multisig UTXOs.
        // Spending CLTV outputs requires tx.lockTime >= CLTV value AND input sequence < 0xFFFFFFFF.
        long maxInputLockTime = 0;
        for (int i = 0; i < inputs.size(); i++) {
            Cash cashInput = cashList.get(i);
            if (cashInput.getLockTime() != null && cashInput.getLockTime() > 0) {
                inputs.get(i).setSequenceNumber(0xFFFFFFFEL);
                if (cashInput.getLockTime() > maxInputLockTime) {
                    maxInputLockTime = cashInput.getLockTime();
                }
                log.debug("Set sequence to 0xFFFFFFFE for CLTV multisig input: "
                        + cashInput.getBirthTxId() + ":" + cashInput.getBirthIndex());
            }
        }
        if (maxInputLockTime > 0) {
            transaction.setLockTime(maxInputLockTime);
            log.debug("Set transaction lockTime to " + maxInputLockTime + " for CLTV multisig");
        }

        ECKey ecKey = ECKey.fromPrivate(priKey);
        BigInteger priKeyBigInteger = ecKey.getPrivKey();
        List<String> sigList = new ArrayList<>();

        // Sign each input with the correct redeemScript for that input.
        for (int i = 0; i < inputs.size(); ++i) {
            byte[] redeemScript;
            Cash cashInput = cashList.get(i);

            if (cashInput.getRedeemScript() != null && !cashInput.getRedeemScript().isEmpty()) {
                redeemScript = Hex.fromHex(cashInput.getRedeemScript());
                log.debug("Signing input " + i + " with stored redeemScript: " + cashInput.getRedeemScript());
            } else if (cashInput.getLockTime() != null && cashInput.getLockTime() > 0) {
                Script cltvMultisigScript = P2SH.makeMultisigLockTimeRedeemScript(
                        cashInput.getLockTime(),
                        multisig.getPubkeys(),
                        multisig.getM(),
                        multisig.getN()
                );
                redeemScript = cltvMultisigScript.getProgram();
                log.debug("Signing input " + i + " with CLTV+multisig redeemScript, lockTime: "
                        + cashInput.getLockTime());
            } else {
                redeemScript = Hex.fromHex(multisig.getRedeemScript());
                log.debug("Signing input " + i + " with plain multisig redeemScript: " + multisig.getRedeemScript());
            }
            if (redeemScript == null) {
                log.debug("Failed to sign multisig input. RedeemScript is null.");
                return multiSignData;
            }
            Script script = new Script(redeemScript);
            Sha256Hash hash = transaction.hashForSignatureWitness(i, script, Coin.valueOf(cashInput.getValue()), Transaction.SigHash.ALL, false);
            byte[] sig = SchnorrSignature.schnorr_sign(hash.getBytes(), priKeyBigInteger);
            sigList.add(Hex.toHex(sig));
        }

        String fid = prikeyToFid(priKey);
        if (multiSignData.getFidSigMap() == null) {
            Map<String, List<String>> fidSigListMap = new HashMap<>();
            multiSignData.setFidSigMap(fidSigListMap);
        }
        multiSignData.getFidSigMap().put(fid, sigList);
        return multiSignData;
    }

    public static boolean rawTxSigVerify(byte[] rawTx, byte[] pubKey, byte[] sig, int inputIndex, long inputValue, byte[] redeemScript, MainNetParams mainnetwork) {
        if (mainnetwork == null) mainnetwork = FchMainNetwork.MAINNETWORK;
        Transaction transaction = new Transaction(mainnetwork, rawTx);
        Script script = new Script(redeemScript);
        Sha256Hash hash = transaction.hashForSignatureWitness(inputIndex, script, Coin.valueOf(inputValue), Transaction.SigHash.ALL, false);
        return SchnorrSignature.schnorr_verify(hash.getBytes(), pubKey, sig);
    }

    public static String buildSchnorrMultiSigTx(byte[] rawTx, Map<String, List<byte[]>> sigListMap, Multisig multisig, MainNetParams mainnetwork) {
        if(sigListMap==null || sigListMap.isEmpty())return null;

        if (sigListMap.size() > multisig.getM())
            sigListMap = dropRedundantSigs(sigListMap, multisig.getM());

        Transaction transaction = new Transaction(mainnetwork, rawTx);

        for (int i = 0; i < transaction.getInputs().size(); i++) {
            List<byte[]> sigListByTx = new ArrayList<>();
            for (String fid : multisig.getFids()) {
                try {
                    byte[] sig = sigListMap.get(fid).get(i);
                    sigListByTx.add(sig);
                } catch (Exception ignore) {
                }
            }

            Script inputScript = createSchnorrMultiSigInputScriptBytes(sigListByTx, HexFormat.of().parseHex(multisig.getRedeemScript())); // Include all required signatures
//            System.out.println(HexFormat.of().formatHex(inputScript.getProgram()));
            TransactionInput input = transaction.getInput(i);
            input.setScriptSig(inputScript);
        }

        byte[] signResult = transaction.bitcoinSerialize();
        return Hex.toHex(signResult);
    }

    /**
     * Build a complete multisig transaction from collected signatures, with CLTV support.
     * For CLTV multisig, transaction lockTime and input sequences are set appropriately,
     * and each input uses its own redeemScript (stored on Cash) when available.
     */
    public static String buildSchnorrMultiSigTx(RawTxInfo rawTxInfo, MainNetParams mainnetwork) {
        Map<String, List<String>> sigListMap = rawTxInfo.getFidSigMap();
        Multisig multisig = rawTxInfo.getSenderMultisig();
        if (sigListMap == null || sigListMap.isEmpty() || multisig == null) return null;

        byte[] rawTx = createTxBytes(rawTxInfo, mainnetwork);
        if (rawTx == null) rawTx = rawTxInfo.getRawTx();
        if (rawTx == null) return null;

        if (sigListMap.size() > multisig.getM())
            sigListMap = dropRedundantStringSigs(sigListMap, multisig.getM());

        Transaction transaction = new Transaction(mainnetwork, rawTx);

        // Ensure lockTime and sequences are set for any CLTV inputs.
        List<Cash> cashList = rawTxInfo.getInputs();
        if (cashList != null) {
            long maxInputLockTime = 0;
            for (int i = 0; i < transaction.getInputs().size() && i < cashList.size(); i++) {
                Cash cashInput = cashList.get(i);
                if (cashInput.getLockTime() != null && cashInput.getLockTime() > 0) {
                    transaction.getInput(i).setSequenceNumber(0xFFFFFFFEL);
                    if (cashInput.getLockTime() > maxInputLockTime) {
                        maxInputLockTime = cashInput.getLockTime();
                    }
                }
            }
            if (maxInputLockTime > 0) {
                transaction.setLockTime(maxInputLockTime);
                log.debug("Set transaction lockTime to " + maxInputLockTime + " for CLTV multisig build");
            }
        }

        // Build scriptSig for each input using the correct redeemScript for that input.
        for (int i = 0; i < transaction.getInputs().size(); i++) {
            List<byte[]> sigListByTx = new ArrayList<>();
            for (String fid : multisig.getFids()) {
                try {
                    String sig = sigListMap.get(fid).get(i);
                    sigListByTx.add(Hex.fromHex(sig));
                } catch (Exception ignore) {
                }
            }

            byte[] redeemScriptForBuild;
            Cash cashInput = (cashList != null && i < cashList.size()) ? cashList.get(i) : null;

            if (cashInput != null && cashInput.getRedeemScript() != null && !cashInput.getRedeemScript().isEmpty()) {
                redeemScriptForBuild = Hex.fromHex(cashInput.getRedeemScript());
                log.debug("Building scriptSig with stored redeemScript for input " + i);
            } else if (cashInput != null && cashInput.getLockTime() != null && cashInput.getLockTime() > 0) {
                Script cltvMultisigScript = P2SH.makeMultisigLockTimeRedeemScript(
                        cashInput.getLockTime(),
                        multisig.getPubkeys(),
                        multisig.getM(),
                        multisig.getN()
                );
                redeemScriptForBuild = cltvMultisigScript.getProgram();
                log.debug("Building scriptSig with CLTV+multisig redeemScript for input " + i);
            } else {
                redeemScriptForBuild = Hex.fromHex(multisig.getRedeemScript());
                log.debug("Building scriptSig with plain multisig redeemScript for input " + i);
            }

            Script inputScript = createSchnorrMultiSigInputScriptBytes(sigListByTx, redeemScriptForBuild);
            transaction.getInput(i).setScriptSig(inputScript);
        }

        return Hex.toHex(transaction.bitcoinSerialize());
    }

    private static Map<String, List<String>> dropRedundantStringSigs(Map<String, List<String>> sigListMap, int m) {
        Map<String, List<String>> newMap = new HashMap<>();
        int i = 0;
        for (String key : sigListMap.keySet()) {
            newMap.put(key, sigListMap.get(key));
            i++;
            if (i == m) return newMap;
        }
        return newMap;
    }

    /**
     * Merge multiple signed-multisig JSON payloads, verifying each signature before inclusion.
     * Returns a ReplyBody whose data is the merged RawTxInfo on success.
     */
    public static ReplyBody mergeMultisignTxData(String[] signedDatas, MainNetParams mainnetwork) {
        Map<String, List<String>> fidSigListMap = new HashMap<>();
        RawTxInfo finalRawTxInfo = null;
        byte[] rawTx = null;
        Multisig multisig = null;
        ReplyBody replyBody;
        for (String dataJson : signedDatas) {
            try {
                RawTxInfo multiSignData = RawTxInfo.fromJson(dataJson, RawTxInfo.class);

                if (multisig == null && multiSignData.getSenderMultisig() != null) {
                    multisig = multiSignData.getSenderMultisig();
                }

                if (rawTx == null) {
                    rawTx = createTxBytes(multiSignData, mainnetwork);
                }

                for (String fid : multiSignData.getFidSigMap().keySet()) {
                    List<String> sign = multiSignData.getFidSigMap().get(fid);
                    if (fidSigListMap.get(fid) == null) {
                        replyBody = verifySig(fid, multiSignData, mainnetwork);
                        if (replyBody.getCode() == 0)
                            fidSigListMap.put(fid, sign);
                        else return replyBody;
                    }
                }
                finalRawTxInfo = multiSignData;
            } catch (Exception ignored) {
                replyBody = new ReplyBody();
                replyBody.set1020Other("Failed to parse the signed data.");
                return replyBody;
            }
        }
        if (rawTx == null || rawTx.length == 0 || multisig == null || finalRawTxInfo == null) return null;

        finalRawTxInfo.setSenderMultisig(multisig);
        finalRawTxInfo.setFidSigMap(fidSigListMap);

        replyBody = new ReplyBody();
        replyBody.set0Success();
        replyBody.setData(finalRawTxInfo);
        return replyBody;
    }

    private static ReplyBody verifySig(String fid, RawTxInfo multiSignData, MainNetParams mainnetwork) {
        ReplyBody replyBody = new ReplyBody();
        try {
            if (!multiSignData.getSenderMultisig().getFids().contains(fid)) {
                replyBody.set1020Other("The FID is not a member of " + multiSignData.getSenderMultisig().getId());
                return replyBody;
            }
            int pubKeyIndex = multiSignData.getSenderMultisig().getFids().indexOf(fid);
            String pubKey = multiSignData.getSenderMultisig().getPubkeys().get(pubKeyIndex);
            String redeemScript = multiSignData.getSenderMultisig().getRedeemScript();
            byte[] rawTx = createTxBytes(multiSignData, mainnetwork);
            for (int i = 0; i < multiSignData.getInputs().size(); i++) {
                if (!rawTxSigVerify(rawTx, Hex.fromHex(pubKey),
                        Hex.fromHex(multiSignData.getFidSigMap().get(fid).get(i)),
                        i, multiSignData.getInputs().get(i).getValue(),
                        Hex.fromHex(redeemScript), mainnetwork)) {
                    replyBody.set1020Other("The signature is invalid");
                    return replyBody;
                }
            }
        } catch (Exception e) {
            replyBody.set1020Other("Failed to verify the signature.");
            return replyBody;
        }
        replyBody.set0Success();
        return replyBody;
    }

    /**
     * Higher-level helper: merge signed payloads and build the final multisig transaction.
     */
    public static String buildSignedMultisigTx(String[] signedData, MainNetParams mainnetwork) {
        ReplyBody replyBody = mergeMultisignTxData(signedData, mainnetwork);
        if (replyBody == null) return null;
        if (replyBody.getCode() != 0) {
            System.out.println(replyBody.getMessage());
            return null;
        }
        RawTxInfo finalRawTxInfo = (RawTxInfo) replyBody.getData();
        if (finalRawTxInfo == null) return null;
        return buildSchnorrMultiSigTx(finalRawTxInfo, mainnetwork);
    }

    private static Map<String, List<byte[]>> dropRedundantSigs(Map<String, List<byte[]>> sigListMap, int m) {
        Map<String, List<byte[]>> newMap = new HashMap<>();
        int i = 0;
        for (String key : sigListMap.keySet()) {
            newMap.put(key, sigListMap.get(key));
            i++;
            if (i == m) return newMap;
        }
        return newMap;
    }

    public static Script createSchnorrMultiSigInputScriptBytes(List<byte[]> signatures, byte[] multisigProgramBytes) {
        if (signatures.size() >= 16) return null;
        ScriptBuilder builder = new ScriptBuilder();
        builder.smallNum(0);
        Iterator<byte[]> var3 = signatures.iterator();
        byte[] sigHashAll = new byte[]{0x41};

        while (var3.hasNext()) {
            byte[] signature = var3.next();
            builder.data(BytesUtils.bytesMerger(signature, sigHashAll));
        }

        if (multisigProgramBytes != null) {
            builder.data(multisigProgramBytes);
        }

        return builder.build();
    }

    public static String createTimeLockedTransaction(List<Cash> inputs, byte[] priKey, List<Cash> outputs, long lockUntil, String opReturn, MainNetParams mainnetwork) {

        String changeToFid = inputs.get(0).getOwner();

        long fee;
        if (opReturn != null) {
            fee = TxCreator.calcTxSize(inputs.size(), outputs.size(), opReturn.getBytes().length);
        } else fee = TxCreator.calcTxSize(inputs.size(), outputs.size(), 0);

        Transaction transaction = new Transaction(mainnetwork);
//        transaction.setLockTime(nLockTime);

        long totalMoney = 0;
        long totalOutput = 0;

        ECKey eckey = ECKey.fromPrivate(priKey);

        for (Cash output : outputs) {
            long value = utils.FchUtils.coinToSatoshi(output.getAmount());
            byte[] pubKeyHash = KeyTools.addrToHash160(output.getOwner());
            totalOutput += value;

            ScriptBuilder builder = new ScriptBuilder();

            builder.number(lockUntil)
                    .op(ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY)
                    .op(ScriptOpCodes.OP_DROP);

            builder.op(ScriptOpCodes.OP_DUP)
                    .op(ScriptOpCodes.OP_HASH160)
                    .data(pubKeyHash)
                    .op(ScriptOpCodes.OP_EQUALVERIFY)
                    .op(ScriptOpCodes.OP_CHECKSIG);

            Script cltvScript = builder.build();

            transaction.addOutput(Coin.valueOf(value), cltvScript);
        }

        if (opReturn != null && !opReturn.isEmpty()) {
            try {
                Script opreturnScript = ScriptBuilder.createOpReturnScript(opReturn.getBytes(StandardCharsets.UTF_8));
                transaction.addOutput(Coin.ZERO, opreturnScript);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        for (Cash input : inputs) {
            totalMoney += input.getValue();

            TransactionOutPoint outPoint = new TransactionOutPoint(mainnetwork, input.getBirthIndex(), Sha256Hash.wrap(input.getBirthTxId()));
            TransactionInput unsignedInput = new TransactionInput(mainnetwork, null, new byte[0], outPoint,Coin.valueOf(input.getValue()));
            transaction.addInput(unsignedInput);
        }

        if ((totalOutput + fee) > totalMoney) {
            throw new RuntimeException("input is not enough");
        }

        long change = totalMoney - totalOutput - fee;
        if (change > Constants.DustInSatoshi) {
            transaction.addOutput(Coin.valueOf(change), Address.fromBase58(mainnetwork, changeToFid));
        }

        for (int i = 0; i < inputs.size(); ++i) {
            Cash input = inputs.get(i);
            Script script = ScriptBuilder.createP2PKHOutputScript(eckey);
            SchnorrSignature signature = transaction.calculateSchnorrSignature(i, eckey, script.getProgram(), Coin.valueOf(input.getValue()), Transaction.SigHash.ALL, false);

            Script schnorr = ScriptBuilder.createSchnorrInputScript(signature, eckey);
            transaction.getInput(i).setScriptSig(schnorr);
        }

        byte[] signResult = transaction.bitcoinSerialize();
        return Hex.toHex(signResult);
    }

    //Tools
    public static Multisig createMultisig(List<byte[]> pubKeyList, int m) {
        List<ECKey> keys = new ArrayList<>();
        for (byte[] bytes : pubKeyList) {
            ECKey ecKey = ECKey.fromPublicOnly(bytes);
            keys.add(ecKey);
        }

        Script multiSigScript = ScriptBuilder.createMultiSigOutputScript(m, keys);

        byte[] redeemScriptBytes = multiSigScript.getProgram();

        Multisig multisig;
        try {
            multisig = Multisig.parseMultisigRedeemScript(HexFormat.of().formatHex(redeemScriptBytes));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return multisig;
    }

    public static long calcTxSize(int inputNum, int outputNum, int opReturnBytesLen) {

        long baseLength = 10;
        long inputLength = 141 * (long) inputNum;
        long outputLength = 34 * (long) (outputNum + 1); // Include change output

        int opReturnLen = 0;
        if (opReturnBytesLen != 0)
            opReturnLen = calcOpReturnLen(opReturnBytesLen);

        return baseLength + inputLength + outputLength + opReturnLen;
    }

    private static int calcOpReturnLen(int opReturnBytesLen) {
        int dataLen;
        if (opReturnBytesLen < 76) {
            dataLen = opReturnBytesLen + 1;
        } else if (opReturnBytesLen < 256) {
            dataLen = opReturnBytesLen + 2;
        } else dataLen = opReturnBytesLen + 3;
        int scriptLen;
        scriptLen = (dataLen + 1) + VarInt.sizeOf(dataLen + 1);
        int amountLen = 8;
        return scriptLen + amountLen;
    }

    public static long calcFee(long txSize, double feeRate) {
        long feeRateLong;
        if (feeRate != 0) {
            feeRateLong = (long) (feeRate / 1000 * COIN_TO_SATOSHI);
        } else feeRateLong = (long) (DEFAULT_FEE_RATE / 1000 * COIN_TO_SATOSHI);
        return feeRateLong * txSize;
    }

    public static String decodeTxFch(String rawTx, MainNetParams mainNetParams) {
        byte[] rawTxBytes;
        try{
            if(Hex.isHexString(rawTx))rawTxBytes = Hex.fromHex(rawTx);
            else {
                rawTxBytes = Base64.getDecoder().decode(rawTx);
            }
        }catch (Exception e){
            return null;
        }
        return decodeTxFch(rawTxBytes,mainNetParams);
    }


    public static String decodeTxFch(byte[] rawTxBytes, MainNetParams mainnetwork) {
        if(rawTxBytes==null) return null;

        Transaction transaction;
            // Handle parsing of combined format with input values
            List<Long> inputValueList = new ArrayList<>();
            byte[] rawTx;
            try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(rawTxBytes)) {
                int flag = byteArrayInputStream.read();
                if(flag== OFF_LINE_TX_START_FLAG) {
                    byte[] b2 = new byte[2];
                    byteArrayInputStream.read(b2);
                    int size = BytesUtils.bytes2ToIntBE(b2);
                    byte[] b8 = new byte[8];
                    for (int i = 0; i < size; i++) {
                        byteArrayInputStream.read(b8);
                        inputValueList.add(BytesUtils.bytes8ToLong(b8, false));
                    }
                    rawTx = byteArrayInputStream.readAllBytes();
                    transaction = new Transaction(mainnetwork, rawTx);
                }else {
                    transaction = new Transaction(mainnetwork, rawTxBytes);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }


        // Build JSON structure
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append(String.format("  \"txid\": \"%s\",\n", transaction.getTxId()));
        json.append(String.format("  \"hash\": \"%s\",\n", transaction.getHash()));
        json.append(String.format("  \"version\": %d,\n", transaction.getVersion()));
        json.append(String.format("  \"size\": %d,\n", transaction.getMessageSize()));
        json.append(String.format("  \"locktime\": %d,\n", transaction.getLockTime()));

        // Handle inputs
        json.append("  \"vin\": [\n");
        List<TransactionInput> inputs = transaction.getInputs();
        for (int i = 0; i < inputs.size(); i++) {
            TransactionInput input = inputs.get(i);
            json.append("    {\n");
            json.append(String.format("      \"txid\": \"%s\",\n", input.getOutpoint().getHash()));
            json.append(String.format("      \"vout\": %d,\n", input.getOutpoint().getIndex()));
            json.append("      \"scriptSig\": {\n");
            json.append(String.format("        \"asm\": \"%s\",\n", input.getScriptSig().toString()));
            json.append(String.format("        \"hex\": \"%s\"\n", Hex.toHex(input.getScriptSig().getProgram())));
            json.append("      },\n");
            json.append(String.format("      \"sequence\": %d\n", input.getSequenceNumber()));
            json.append("    }").append(i < inputs.size() - 1 ? ",\n" : "\n");
        }
        json.append("  ],\n");

        // Handle outputs
        json.append("  \"vout\": [\n");
        List<TransactionOutput> outputs = transaction.getOutputs();
        for (int i = 0; i < outputs.size(); i++) {
            TransactionOutput output = outputs.get(i);
            json.append("    {\n");
            json.append(String.format("      \"value\": %.8f,\n", output.getValue().getValue() / 100000000.0));
            json.append(String.format("      \"n\": %d,\n", i));
            json.append("      \"scriptPubKey\": {\n");
            json.append(String.format("        \"asm\": \"%s\",\n", output.getScriptPubKey().toString()));
            json.append(String.format("        \"hex\": \"%s\",\n", Hex.toHex(output.getScriptPubKey().getProgram())));

            // Determine script type and addresses
            String type = getScriptType(output.getScriptPubKey());
            json.append(String.format("        \"type\": \"%s\"", type));

            if (!type.equals("nulldata")) {
                json.append(",\n        \"addresses\": [\n");
                try {
                    Address address = output.getScriptPubKey().getToAddress(mainnetwork);
                    json.append(String.format("          \"%s\"\n", address.toString()));
                } catch (Exception e) {
                    // Handle non-standard scripts
                }
                json.append("        ]");
            }
            json.append("\n      }\n");
            json.append("    }").append(i < outputs.size() - 1 ? ",\n" : "\n");
        }
        json.append("  ]\n");
        json.append("}");

        return json.toString();
    }

    private static String getScriptType(Script script) {
        if (script.isSentToAddress() || script.isSentToRawPubKey())
            return "pubkeyhash";
        else if (script.isPayToScriptHash())
            return "scripthash";
        else if (script.isOpReturn())
            return "nulldata";
        else
            return "nonstandard";
    }

    public static long calcSizeMultiSig(int inputNum, int outputNum, int opReturnBytesLen, int m, int n) {

        /*多签单个Input长度：
            基础字节40（preTxId 32，preIndex 4，sequence 4），
            可变脚本长度：？
            脚本：
                op_0    1
                签名：m * (1+64+1)     // length + pubKeyLength + sigHash ALL
                可变redeemScript 长度：？
                redeem script：
                    op_m    1
                    pubKeys    n * 33
                    op_n    1
                    OP_CHECKMULTISIG    1
         */

        long op_mLen =1;
        long op_nLen =1;
        long pubKeyLen = 33;
        long pubKeyLenLen = 1;
        long op_checkmultisigLen = 1;

        long redeemScriptLength = op_mLen + (n * (pubKeyLenLen + pubKeyLen)) + op_nLen + op_checkmultisigLen; //105 n=3
        long redeemScriptVarInt = VarInt.sizeOf(redeemScriptLength);//1 n=3

        long op_pushDataLen = 1;
        long sigHashLen = 1;
        long signLen=64;
        long signLenLen = 1;
        long zeroByteLen = 1;

        long mSignLen = m * (signLenLen + signLen + sigHashLen); //132 m=2

        long scriptLength = zeroByteLen + mSignLen + op_pushDataLen + redeemScriptVarInt + redeemScriptLength;//236 m=2
        long scriptVarInt = VarInt.sizeOf(scriptLength);

        long preTxIdLen = 32;
        long preIndexLen = 4;
        long sequenceLen = 4;

        long inputLength = preTxIdLen + preIndexLen + sequenceLen + scriptVarInt + scriptLength;//240 n=3,m=2


        long opReturnLen = 0;
        if (opReturnBytesLen != 0)
            opReturnLen = calcOpReturnLen(opReturnBytesLen);

        long outputValueLen=8;
        long unlockScriptLen = 25; //If sending to multiSignAddr, it will be 23.
        long unlockScriptLenLen =1;
        long outPutLen = outputValueLen + unlockScriptLenLen + unlockScriptLen;

        long inputCountLen=1;
        long outputCountLen=1;
        long txVerLen = 4;
        long nLockTimeLen = 4;
        long txFixedLen = inputCountLen + outputCountLen + txVerLen + nLockTimeLen;

        long length;
        length = txFixedLen + inputLength * inputNum + outPutLen * (outputNum + 1) + opReturnLen;

        return length;
    }


    public static String buildSignedTx(String[] signedData, MainNetParams mainnetwork) {
        Map<String, List<String>> fidSigListMap = new HashMap<>();
        byte[] rawTx = null;
        Multisig multisig = null;

        for (String dataJson : signedData) {
            try {
                System.out.println(dataJson);

                RawTxInfo multiSignData = RawTxInfo.fromJson(dataJson,RawTxInfo.class);

                if (multisig == null
                        && multiSignData.getSenderMultisig() != null) {
                    multisig = multiSignData.getSenderMultisig();
                }

                if (rawTx == null
                        && multiSignData.getRawTx() != null
                        && multiSignData.getRawTx().length > 0) {
                    rawTx = multiSignData.getRawTx();
                }

                fidSigListMap.putAll(multiSignData.getFidSigMap());

            } catch (Exception ignored) {
            }
        }
        if (rawTx == null || multisig == null) return null;

        Map<String, List<byte[]>> sigBytesListMap = getStringListMap(fidSigListMap);

        return buildSchnorrMultiSigTx(rawTx, sigBytesListMap, multisig, mainnetwork);
    }

    @NotNull
    public static Map<String, List<byte[]>> getStringListMap(Map<String, List<String>> fidSigListMap) {
        Map<String, List<byte[]>> sigBytesListMap = new HashMap<>();
        for(String key: fidSigListMap.keySet()){
            List<byte[]> bytesList = new ArrayList<>();
            for(String hex : fidSigListMap.get(key)){
                if(hex!=null)
                    bytesList.add(Hex.fromHex(hex));
            }
            if(bytesList.isEmpty())continue;
            sigBytesListMap.put(key,bytesList);
        }
        return sigBytesListMap;
    }

    public static data.fchData.Block getBestBlock(ElasticsearchClient esClient) throws ElasticsearchException, IOException {
        SearchResponse<data.fchData.Block> result = esClient.search(s -> s
                        .index(IndicesNames.BLOCK)
                        .size(1)
                        .sort(so -> so.field(f -> f.field("height").order(SortOrder.Desc)))
                , data.fchData.Block.class);
        return result.hits().hits().get(0).source();
    }

    /**
     * Get a list of Cash outputs from a signed transaction for a specific FID.
     * Useful for updating the local cash database after broadcasting a transaction.
     *
     * @param signedTx The signed transaction in hex format
     * @param fid      The FID to filter outputs for
     * @return List of Cash objects belonging to the specified FID
     */
    public static List<Cash> getIssuedCashListForFid(String signedTx, String fid) {
        if (signedTx == null || fid == null) {
            return new ArrayList<>();
        }

        List<Cash> cashList = new ArrayList<>();

        try {
            Transaction transaction = new Transaction(FchMainNetwork.MAINNETWORK, Hex.fromHex(signedTx));
            String txId = transaction.getTxId().toString();
            List<TransactionOutput> outputs = transaction.getOutputs();

            for (int i = 0; i < outputs.size(); i++) {
                TransactionOutput output = outputs.get(i);

                try {
                    Address address = output.getScriptPubKey().getToAddress(FchMainNetwork.MAINNETWORK);

                    if (fid.equals(address.toString())) {
                        Cash cash = new Cash();
                        cash.setBirthTxId(txId);
                        cash.setBirthIndex(i);
                        cash.setOwner(fid);
                        cash.setValue(output.getValue().getValue());
                        cash.setValid(true);
                        cash.makeId(txId, i);
                        cashList.add(cash);
                    }
                } catch (Exception e) {
                    // Skip outputs that can't be converted to addresses (e.g., OP_RETURN)
                    log.debug("Skipping output " + i + " - cannot convert to address: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse signed transaction: " + e.getMessage());
            return new ArrayList<>();
        }

        return cashList;
    }


    @Test
    public void test(){
        int opReturnBytesLen = "hi".getBytes().length;
        System.out.println(calcSizeMultiSig(1,1, opReturnBytesLen,2,3));
        String txHex= "020000000185231da3cc3a00496258f633d7e48442e51ffa51c9b0efe92d80a84eb61b43c103000000f0004151e694db47016366908a43f9900a00ab537e5fba8da4892e1db3ba4f00b893792d920c13d7fc3dafa88ec6bbc3027cfb308ef2264f470fdb819e90d16d956fec4141447743a23a589ecef0e30d05dc2957c8213127e66efeb6738142df035e5d1f21d2ed933a9e7eb00bef22af016b248ee34b9877f52bf0fcfd98714d4ecf4b218f414c695221030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a2102536e4f3a6871831fa91089a5d5a950b96a31c861956f01459c0cd4f4374b2f672103f0145ddf5debc7169952b17b5c6a8a566b38742b6aa7b33b667c0a7fa73762e253aeffffffff03809698000000000017a914d86ffd4d1ade6ca5f19e8205bb4ddb0a05c92a72870000000000000000046a026869fe457f0f0000000017a914d86ffd4d1ade6ca5f19e8205bb4ddb0a05c92a728700000000";
        System.out.println(txHex.length()/2);
    }

}
