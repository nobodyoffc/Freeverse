package fch;

import clients.apipClient.ApipClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.google.common.base.Preconditions;
import configure.ApiAccount;
import constants.Constants;
import constants.IndicesNames;
import crypto.KeyTools;
import fch.fchData.Cash;
import fch.fchData.P2SH;
import fch.fchData.SendTo;
import javaTools.BytesTools;
import javaTools.Hex;
import javaTools.http.AuthType;
import javaTools.http.RequestMethod;
import nasa.data.TxInput;
import nasa.data.TxOutput;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.SchnorrSignature;
import org.bitcoinj.fch.FchMainNetwork;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.*;

import static constants.Constants.COIN_TO_SATOSHI;
import static crypto.KeyTools.priKeyToFid;

/**
 * 工具类
 */
public class TxCreator {

    public static final double DEFAULT_FEE_RATE = 0.00001;

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

    /**
     * 创建签名
     */

    public static String createTransactionSignFch(List<TxInput> inputs, List<TxOutput> outputs, String opReturn, String returnAddr) {
        FchMainNetwork mainnetwork = FchMainNetwork.MAINNETWORK;
        return createTransactionSignClassic(mainnetwork, inputs, outputs, opReturn, returnAddr, 0);
    }

    public static String createTransactionSignFch(List<TxInput> inputs, List<TxOutput> outputs, String opReturn, String returnAddr, double feeRateDouble) {
        FchMainNetwork mainnetwork = FchMainNetwork.MAINNETWORK;
        return createTransactionSignClassic(mainnetwork, inputs, outputs, opReturn, returnAddr, feeRateDouble);
    }

    public static String createTransactionSignClassic(NetworkParameters networkParameters, List<TxInput> inputs, List<TxOutput> outputs, String opReturn, String returnAddr, double feeRateDouble) {

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

        if (opReturn != null && !"".equals(opReturn)) {
            try {
                Script opreturnScript = ScriptBuilder.createOpReturnScript(opReturn.getBytes(StandardCharsets.UTF_8));
                transaction.addOutput(Coin.ZERO, opreturnScript);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        for (TxInput input : inputs) {
            totalMoney += input.getAmount();

            ECKey eckey = ECKey.fromPrivate(input.getPriKey32());

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
                returnAddr = ECKey.fromPrivate(inputs.get(0).getPriKey32()).toAddress(networkParameters).toBase58();
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

    public static String createTransactionSignFch(List<Cash> inputs, byte[] priKey, List<SendTo> outputs, String opReturn) {
        return createTransactionSignFch(inputs, priKey, outputs, opReturn, 0);
    }

    public static String createTransactionSignFch(List<Cash> inputs, byte[] priKey, List<SendTo> outputs, String opReturn, double feeRateDouble) {
        String changeToFid = inputs.get(0).getOwner();
        if(outputs==null)outputs = new ArrayList<>();
        long txSize = opReturn == null ? calcTxSize(inputs.size(), outputs.size(), 0) : calcTxSize(inputs.size(), outputs.size(), opReturn.getBytes().length);

//        long feeRateLong;
//        if (feeRateDouble != 0) {
//            feeRateLong = (long) (feeRateDouble / 1000 * COIN_TO_SATOSHI);
//        } else feeRateLong = (long) (DEFAULT_FEE_RATE / 1000 * COIN_TO_SATOSHI);
//        long fee = feeRateLong * txSize;

        long fee =calcFee(txSize,feeRateDouble);

        Transaction transaction = new Transaction(FchMainNetwork.MAINNETWORK);

        long totalMoney = 0;
        long totalOutput = 0;

        ECKey eckey = ECKey.fromPrivate(priKey);

        for (SendTo output : outputs) {
            long value = ParseTools.coinToSatoshi(output.getAmount());
            totalOutput += value;
            transaction.addOutput(Coin.valueOf(value), Address.fromBase58(FchMainNetwork.MAINNETWORK, output.getFid()));
        }

        if (opReturn != null && !"".equals(opReturn)) {
            try {
                Script opreturnScript = ScriptBuilder.createOpReturnScript(opReturn.getBytes(StandardCharsets.UTF_8));
                transaction.addOutput(Coin.ZERO, opreturnScript);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        for (Cash input : inputs) {
            totalMoney += input.getValue();
            TransactionOutPoint outPoint = new TransactionOutPoint(FchMainNetwork.MAINNETWORK, input.getBirthIndex(), Sha256Hash.wrap(input.getBirthTxId()));
            TransactionInput unsignedInput = new TransactionInput(new FchMainNetwork(), transaction, new byte[0], outPoint);
            transaction.addInput(unsignedInput);
        }

        if ((totalOutput + fee) > totalMoney) {
            throw new RuntimeException("input is not enough");
        }
        long change = totalMoney - totalOutput - fee;
        if (change > Constants.DustInSatoshi) {
            transaction.addOutput(Coin.valueOf(change), Address.fromBase58(FchMainNetwork.MAINNETWORK, changeToFid));
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

    public static P2SH genMultiP2sh(List<byte[]> pubKeyList, int m) {
        List<ECKey> keys = new ArrayList<>();
        for (byte[] bytes : pubKeyList) {
            ECKey ecKey = ECKey.fromPublicOnly(bytes);
            keys.add(ecKey);
        }

        Script multiSigScript = ScriptBuilder.createMultiSigOutputScript(m, keys);

        byte[] redeemScriptBytes = multiSigScript.getProgram();

        P2SH p2sh;
        try {
            p2sh = P2SH.parseP2shRedeemScript(HexFormat.of().formatHex(redeemScriptBytes));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return p2sh;
    }

    public static byte[] createMultiSignRawTx(List<Cash> inputs, List<SendTo> outputs, String opReturn, P2SH p2SH, double feeRate) {

        String changeToFid = inputs.get(0).getOwner();
        if (!changeToFid.startsWith("3"))
            throw new RuntimeException("It's not a multisig address.");
        ;

        long fee;
        long feeRateLong = (long) (feeRate / 1000 * COIN_TO_SATOSHI);
        if (opReturn != null) {
            fee = feeRateLong * TxCreator.calcSizeMultiSign(inputs.size(), outputs.size(), opReturn.getBytes().length, p2SH.getM(), p2SH.getN());
        } else
            fee = feeRateLong * TxCreator.calcSizeMultiSign(inputs.size(), outputs.size(), 0, p2SH.getM(), p2SH.getN());

        Transaction transaction = new Transaction(FchMainNetwork.MAINNETWORK);

        long totalMoney = 0;
        long totalOutput = 0;

        for (SendTo output : outputs) {
            long value = ParseTools.coinToSatoshi(output.getAmount());
            totalOutput += value;
            transaction.addOutput(Coin.valueOf(value), Address.fromBase58(FchMainNetwork.MAINNETWORK, output.getFid()));
        }

        if (opReturn != null && !"".equals(opReturn)) {
            try {
                Script opreturnScript = ScriptBuilder.createOpReturnScript(opReturn.getBytes(StandardCharsets.UTF_8));
                transaction.addOutput(Coin.ZERO, opreturnScript);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        for (Cash input : inputs) {
            totalMoney += input.getValue();
            TransactionOutPoint outPoint = new TransactionOutPoint(FchMainNetwork.MAINNETWORK, input.getBirthIndex(), Sha256Hash.wrap(input.getBirthTxId()));
            TransactionInput unsignedInput = new TransactionInput(new FchMainNetwork(), transaction, new byte[0], outPoint);
            transaction.addInput(unsignedInput);
        }

        if ((totalOutput + fee) > totalMoney) {
            throw new RuntimeException("input is not enough");
        }
        long change = totalMoney - totalOutput - fee;
        if (change > Constants.DustInSatoshi) {
            transaction.addOutput(Coin.valueOf(change), Address.fromBase58(FchMainNetwork.MAINNETWORK, changeToFid));
        }

        return transaction.bitcoinSerialize();
    }

    public static String signSchnorrMultiSignTx(String multiSignDataJson, byte[] priKey) {
        MultiSigData multiSignData = MultiSigData.fromJson(multiSignDataJson);
        return signSchnorrMultiSignTx(multiSignData, priKey).toJson();
    }

    public static MultiSigData signSchnorrMultiSignTx(MultiSigData multiSignData, byte[] priKey) {

        byte[] rawTx = multiSignData.getRawTx();
        byte[] redeemScript = HexFormat.of().parseHex(multiSignData.getP2SH().getRedeemScript());
        List<Cash> cashList = multiSignData.getCashList();

        Transaction transaction = new Transaction(FchMainNetwork.MAINNETWORK, rawTx);
        List<TransactionInput> inputs = transaction.getInputs();

        ECKey ecKey = ECKey.fromPrivate(priKey);
        BigInteger priKeyBigInteger = ecKey.getPrivKey();
        List<byte[]> sigList = new ArrayList<>();
        for (int i = 0; i < inputs.size(); ++i) {
            Script script = new Script(redeemScript);
            Sha256Hash hash = transaction.hashForSignatureWitness(i, script, Coin.valueOf(cashList.get(i).getValue()), Transaction.SigHash.ALL, false);
            byte[] sig = SchnorrSignature.schnorr_sign(hash.getBytes(), priKeyBigInteger);
            sigList.add(sig);
        }

        String fid = priKeyToFid(priKey);
        if (multiSignData.getFidSigMap() == null) {
            Map<String, List<byte[]>> fidSigListMap = new HashMap<>();
            multiSignData.setFidSigMap(fidSigListMap);
        }
        multiSignData.getFidSigMap().put(fid, sigList);
        return multiSignData;
    }

    public static boolean rawTxSigVerify(byte[] rawTx, byte[] pubKey, byte[] sig, int inputIndex, long inputValue, byte[] redeemScript) {
        Transaction transaction = new Transaction(FchMainNetwork.MAINNETWORK, rawTx);
        Script script = new Script(redeemScript);
        Sha256Hash hash = transaction.hashForSignatureWitness(inputIndex, script, Coin.valueOf(inputValue), Transaction.SigHash.ALL, false);
        return SchnorrSignature.schnorr_verify(hash.getBytes(), pubKey, sig);
    }

    public static String buildSchnorrMultiSignTx(byte[] rawTx, Map<String, List<byte[]>> sigListMap, P2SH p2sh) {

        if (sigListMap.size() > p2sh.getM())
            sigListMap = dropRedundantSigs(sigListMap, p2sh.getM());

        Transaction transaction = new Transaction(FchMainNetwork.MAINNETWORK, rawTx);

        for (int i = 0; i < transaction.getInputs().size(); i++) {
            List<byte[]> sigListByTx = new ArrayList<>();
            for (String fid : p2sh.getFids()) {
                try {
                    byte[] sig = sigListMap.get(fid).get(i);
                    sigListByTx.add(sig);
                } catch (Exception ignore) {
                }
            }

            Script inputScript = createSchnorrMultiSigInputScriptBytes(sigListByTx, HexFormat.of().parseHex(p2sh.getRedeemScript())); // Include all required signatures

            System.out.println(HexFormat.of().formatHex(inputScript.getProgram()));
            TransactionInput input = transaction.getInput(i);
            input.setScriptSig(inputScript);
        }

        byte[] signResult = transaction.bitcoinSerialize();
        return Hex.toHex(signResult);
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
        if (signatures.size() <= 16) return null;
        ScriptBuilder builder = new ScriptBuilder();
        builder.smallNum(0);
        Iterator var3 = signatures.iterator();
        byte[] sigHashAll = new byte[]{0x41};

        while (var3.hasNext()) {
            byte[] signature = (byte[]) var3.next();
            builder.data(BytesTools.bytesMerger(signature, sigHashAll));
        }

        if (multisigProgramBytes != null) {
            builder.data(multisigProgramBytes);
        }

        return builder.build();
    }

    public static String createTimeLockedTransaction(List<Cash> inputs, byte[] priKey, List<SendTo> outputs, long lockUntil, String opReturn) {

        String changeToFid = inputs.get(0).getOwner();

        long fee;
        if (opReturn != null) {
            fee = TxCreator.calcTxSize(inputs.size(), outputs.size(), opReturn.getBytes().length);
        } else fee = TxCreator.calcTxSize(inputs.size(), outputs.size(), 0);

        Transaction transaction = new Transaction(FchMainNetwork.MAINNETWORK);
//        transaction.setLockTime(nLockTime);

        long totalMoney = 0;
        long totalOutput = 0;

        ECKey eckey = ECKey.fromPrivate(priKey);

        for (SendTo output : outputs) {
            long value = ParseTools.coinToSatoshi(output.getAmount());
            byte[] pubKeyHash = KeyTools.addrToHash160(output.getFid());
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

        if (opReturn != null && !"".equals(opReturn)) {
            try {
                Script opreturnScript = ScriptBuilder.createOpReturnScript(opReturn.getBytes(StandardCharsets.UTF_8));
                transaction.addOutput(Coin.ZERO, opreturnScript);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        for (Cash input : inputs) {
            totalMoney += input.getValue();

            TransactionOutPoint outPoint = new TransactionOutPoint(FchMainNetwork.MAINNETWORK, input.getBirthIndex(), Sha256Hash.wrap(input.getBirthTxId()));
            TransactionInput unsignedInput = new TransactionInput(new FchMainNetwork(), transaction, new byte[0], outPoint);
            transaction.addInput(unsignedInput);
        }

        if ((totalOutput + fee) > totalMoney) {
            throw new RuntimeException("input is not enough");
        }

        long change = totalMoney - totalOutput - fee;
        if (change > Constants.DustInSatoshi) {
            transaction.addOutput(Coin.valueOf(change), Address.fromBase58(FchMainNetwork.MAINNETWORK, changeToFid));
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

    /**
     * 随机私钥
     *
     */
    public static IdInfo createRandomIdInfo(String secret) {
        return IdInfo.genRandomIdInfo();
    }

    /**
     * 公钥转地址
     *
     */
    public static String pubkeyToAddr(String pukey) {

        ECKey eckey = ECKey.fromPublicOnly(Hex.fromHex(pukey));
        return eckey.toAddress(FchMainNetwork.MAINNETWORK).toString();

    }

    /**
     * 通过wif创建私钥
     */
    public static IdInfo createIdInfoFromWIFPrivateKey(byte[] wifKey) {

        return new IdInfo(wifKey);
    }

    /**
     * 消息签名
     */
    public static String signMsg(String msg, byte[] wifkey) {
        IdInfo idInfo = new IdInfo(wifkey);
        return idInfo.signMsg(msg);
    }

    public static String signFullMsg(String msg, byte[] wifkey) {
        IdInfo idInfo = new IdInfo(wifkey);
        return idInfo.signFullMessage(msg);
    }

    public static String signFullMsgJson(String msg, byte[] wifkey) {
        IdInfo idInfo = new IdInfo(wifkey);
        return idInfo.signFullMessageJson(msg);
    }

    /**
     * 签名验证
     */
    public static boolean verifyFullMsg(String msg) {
        String args[] = msg.split("----");
        try {
            ECKey key = ECKey.signedMessageToKey(args[0], args[2]);
            Address targetAddr = key.toAddress(FchMainNetwork.MAINNETWORK);
            return args[1].equals(targetAddr.toString());
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean verifyFullMsgJson(String msg) {
        FchProtocol.SignMsg signMsg = FchProtocol.parseSignMsg(msg);
        try {
            ECKey key = ECKey.signedMessageToKey(signMsg.getMessage(), signMsg.getSignature());
            Address targetAddr = key.toAddress(FchMainNetwork.MAINNETWORK);
            return signMsg.getAddress().equals(targetAddr.toString());
        } catch (Exception e) {
            return false;
        }
    }

    public static String msgHash(String msg) {
        try {
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            return Hex.toHex(Sha256Hash.hash(data));
        } catch (Exception e) {

            throw new RuntimeException(e);
        }
    }

    public static String msgFileHash(String path) {
        try {
            File f = new File(path);
            return Hex.toHex(Sha256Hash.of(f).getBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] aesCBCEncrypt(byte[] srcData, byte[] key, byte[] iv) throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException, InvalidKeyException {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));
        return cipher.doFinal(srcData);

    }

    public static byte[] aesCBCDecrypt(byte[] encData, byte[] key, byte[] iv) throws NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException, InvalidKeyException {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

        cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
        byte[] decbbdt = cipher.doFinal(encData);
        return decbbdt;
    }

    //fee = txSize * (feeRate/1000)*100000000
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
        long fee = feeRateLong * txSize;
        return fee;
    }

    @Test
    public void test(){
        int opReturnBytesLen = "hi".getBytes().length;
        System.out.println(calcSizeMultiSign(1,1, opReturnBytesLen,2,3));
        String txHex= "020000000185231da3cc3a00496258f633d7e48442e51ffa51c9b0efe92d80a84eb61b43c103000000f0004151e694db47016366908a43f9900a00ab537e5fba8da4892e1db3ba4f00b893792d920c13d7fc3dafa88ec6bbc3027cfb308ef2264f470fdb819e90d16d956fec4141447743a23a589ecef0e30d05dc2957c8213127e66efeb6738142df035e5d1f21d2ed933a9e7eb00bef22af016b248ee34b9877f52bf0fcfd98714d4ecf4b218f414c695221030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a2102536e4f3a6871831fa91089a5d5a950b96a31c861956f01459c0cd4f4374b2f672103f0145ddf5debc7169952b17b5c6a8a566b38742b6aa7b33b667c0a7fa73762e253aeffffffff03809698000000000017a914d86ffd4d1ade6ca5f19e8205bb4ddb0a05c92a72870000000000000000046a026869fe457f0f0000000017a914d86ffd4d1ade6ca5f19e8205bb4ddb0a05c92a728700000000";
        String txHex1 = "020000000c62fcf9481f0184c7a3407069f9df5269f0b1cddd04bb777d73759fce5325f17a00000000f00041ad21b4a7d279d003f16f8ece4ea4b602306595400eed1b29a87b87b45020c81dfb9002eb74b795bf643b1a04a1209c5756c7d3c8aa07f16e650b7027aa2febaf41413a74c8ae2313ae963ba0c5e716fc003d41c8500aaf3e10714607362056646feda4867695b7dc1609f020ddb9efbf7521a3ad1088d611c03920e2b24045ca2db4414c69522103b77df3d540817d20d3414f920ccec61b3395e1dc1ef298194e5ff696e038edd921024173f84acb3de56b3ef99894fa3b9a1fe4c48c1bdc39163c37c274cd0334ba752102c0a82ba398612daa4133a891b3f52832114e0d3d6210348543f1872020556ded53aeffffffff848d0005ad4359873be4af70538ff84acf269c348b4af60e7c20f9860363a86c00000000f000410e1464bf47b04f561bca305ba4ed05899c14517642e392311b139a42080bf95b028dcb6f5baebc6db79ef1605f6ee237abd4927e838727e7814b36c5da19ff6341410ae8c3b49711992a0a2cbd58662b5eff802a1d7f8370805eb28a7656843a0c91e32d1add8ad4d7da6e9cd18b4f8e6942df367549fd0ac6233f50d07b7154bf88414c69522103b77df3d540817d20d3414f920ccec61b3395e1dc1ef298194e5ff696e038edd921024173f84acb3de56b3ef99894fa3b9a1fe4c48c1bdc39163c37c274cd0334ba752102c0a82ba398612daa4133a891b3f52832114e0d3d6210348543f1872020556ded53aeffffffff6776b172ad4f9c14bbf6e66f80f540037b8f3bb6f9c9f5023e90ccaec75ae4ba00000000f00041c0ba35934a9199722d3625d04d0aba2e720dbc3753140b5772ad228ffeb09a248015264801d53014d025d92962aac147ada6a2fa70aee5690d2c5740658932ac41413fc0f8d56dafaec63c0cddc146b85d4ff2fe9960d62d3187150bf0cb03a51e184eddd75fefb2f81431a4f1f9981187b1691ec8f2a7f233096146056144c7a52c414c69522103b77df3d540817d20d3414f920ccec61b3395e1dc1ef298194e5ff696e038edd921024173f84acb3de56b3ef99894fa3b9a1fe4c48c1bdc39163c37c274cd0334ba752102c0a82ba398612daa4133a891b3f52832114e0d3d6210348543f1872020556ded53aeffffffff29bedc118ce8c7ef7e21a4f3d5d20478b7334cb5c373a4bd1474895b3a4959c300000000f000416cab3c3e4e0811b32828e4e35a3e8d5bfb320399db3ac6d224e0eb128ee40d06873e890fd5c91f25020cea5512649448483eb01da51620c29c414cebf0334ddb4141c63608acdebcd5a31053ba138c8d7571ba29b5b92b3824b4ff57dae622b7ac202d5a0960893df45fde871c6515cb087133ecc64920c13d085441b373a45a0dd6414c69522103b77df3d540817d20d3414f920ccec61b3395e1dc1ef298194e5ff696e038edd921024173f84acb3de56b3ef99894fa3b9a1fe4c48c1bdc39163c37c274cd0334ba752102c0a82ba398612daa4133a891b3f52832114e0d3d6210348543f1872020556ded53aeffffffff7554425de93c5a602226848926d8d25e728b62fd4874f5caa7fe1bb9bb68164a00000000f00041c94c6009a50c402ceff7b856a1da81d952c3e92907d5d87714b10cec0a4cbe50cd934a409d929b6827e403a9a532eeffae126cae872db1d3345000f619ea940441418e6e09a3df459e6c291468dc979d16a508e27d3fdfa3d8fec10b43c883e326291a4e851fd19d9692e880d90598fe4a3b50b87943e2f78079361c1f61fd721a7c414c69522103b77df3d540817d20d3414f920ccec61b3395e1dc1ef298194e5ff696e038edd921024173f84acb3de56b3ef99894fa3b9a1fe4c48c1bdc39163c37c274cd0334ba752102c0a82ba398612daa4133a891b3f52832114e0d3d6210348543f1872020556ded53aeffffffff1cd8ee79fde87fbac4471102a578dac01447403b04550c0d3d8d0bd1c4fb2c4000000000f000418fff73aca75ca0697c4f1a86b497836b6691e4b2fa45f3e914318bda672395b849bd91d6a75b1eecfcfab2a59b2c61986e9f8fd17a5d841e01a78128a2561d024141cb8a2a1907f0bbcc589a173814ea98cd41c50c7f18dbfd1c1b15618611f19e16bc75dd0bec4f1428b8ada2f486aa97af10797f6fdd4953ac5bdd678ca73e8744414c69522103b77df3d540817d20d3414f920ccec61b3395e1dc1ef298194e5ff696e038edd921024173f84acb3de56b3ef99894fa3b9a1fe4c48c1bdc39163c37c274cd0334ba752102c0a82ba398612daa4133a891b3f52832114e0d3d6210348543f1872020556ded53aeffffffffe40c52f0a1d263fb41ff6d686ab7fe8fba3ad399860494aaa5420ed38b43bdba00000000f00041efa7c036202de9bb056275b88b8dfd8a6e489d16c664e5e2d2cd247aff920fe60bdf5f17eddb6599a111722cd1cef7a86b40f11649c068e9d581d9c8933cacdd41412bd86362b76da8bfaa32ab2889bfbfdadd4f23bfce7468de7426645a61f31c238c88142c7b545c8cae8b751512652e1bb3157458463a6162c841daffbeb4502e414c69522103b77df3d540817d20d3414f920ccec61b3395e1dc1ef298194e5ff696e038edd921024173f84acb3de56b3ef99894fa3b9a1fe4c48c1bdc39163c37c274cd0334ba752102c0a82ba398612daa4133a891b3f52832114e0d3d6210348543f1872020556ded53aeffffffffed6ad21ac2b88cdafae2b63d40bbdb5b7732066fc542f1b6681c8acfc00bec7200000000f000412dc852879b140d812e1f413241b3c55ce6cec6dd6acd083c913ec7e9ba90c7fb0aa1aecb379c3c414a527a08ae087c0698af563715d8635884cfcfc01bc5ed3a414172407736e34d8f9d3aacc67ebf6c123c89b7aa67205d91e9c1eb9e50200def21fa8efcf8917960d51f95c94e7515d6c7bd37405dedbf0e3d3b9652bea59f9158414c69522103b77df3d540817d20d3414f920ccec61b3395e1dc1ef298194e5ff696e038edd921024173f84acb3de56b3ef99894fa3b9a1fe4c48c1bdc39163c37c274cd0334ba752102c0a82ba398612daa4133a891b3f52832114e0d3d6210348543f1872020556ded53aeffffffff8bc812263d2af74c7bae97ce07e0f4cd3ee6f965968013bbd277217673455de700000000f00041c3401c0656ff304c6cb5f233b51d04f8d344bb127cf859ddef92e93717319d3a01348973afb12db689c1f62351ead0912c0d5bd0d0d993550b1f85e60534a87641411f36feebdc6766eb302428c9a6e428bae940777c14526454d8967321c8fe32356fbc44a23c20baa89fe81b9c1e13e03c64b3e1e8d39090c5a776a7013a0ae294414c69522103b77df3d540817d20d3414f920ccec61b3395e1dc1ef298194e5ff696e038edd921024173f84acb3de56b3ef99894fa3b9a1fe4c48c1bdc39163c37c274cd0334ba752102c0a82ba398612daa4133a891b3f52832114e0d3d6210348543f1872020556ded53aeffffffff83c1143190f8e791ed4721fc0c37eeb10a6fe1f28d64735d488cce4431eb89dd00000000f0004107aca154af37c325be9fd9708d28392b40477c80fba1658763922bd1def219c7ae6f0f8d40942b99c5844e77a162f8b439324e7fd4f2b86c8c66b27f9a5c24e241418fdf5b38aa4d769d6268ef063aba49d288babe9a6e038ea75f329de43872060558871872f7886f4572f7b43339cc65c14b34fcd4fa3cb14a0cacf6e0084d7c4c414c69522103b77df3d540817d20d3414f920ccec61b3395e1dc1ef298194e5ff696e038edd921024173f84acb3de56b3ef99894fa3b9a1fe4c48c1bdc39163c37c274cd0334ba752102c0a82ba398612daa4133a891b3f52832114e0d3d6210348543f1872020556ded53aeffffffff0052413adef6ab4075cbb918506c6e2c2a536b3cb8c7cb72d16cd7e70803091a00000000f00041db821df040923aa6c372224ed7feeec7d7bce621f9c889f8a4da20fc023cd3071fc15603bc8c4ea8a79029ee49333f032ccb8b0c23a0c7758e4ab85146f823e34141864e2592c9d728ed3a4a996a506ffd04233d9d99562db30e8065fc2a3957571888873291f46a206d180706e844c01c8a82f36dea15a68a7db181d141da76020e414c69522103b77df3d540817d20d3414f920ccec61b3395e1dc1ef298194e5ff696e038edd921024173f84acb3de56b3ef99894fa3b9a1fe4c48c1bdc39163c37c274cd0334ba752102c0a82ba398612daa4133a891b3f52832114e0d3d6210348543f1872020556ded53aeffffffff850df62f493f53c00cf233c71f07a86f74bdd92bfd2aa78920854bb58c2fa9c901000000f000417dcdce786b38e4d41c8b50b385a18fa4977c5e2edfea12c4d371630e8a6387179cc5a90572f1513dc5664be8b2a4cc4a9b4c1b4f4bc9c987cb1c1fdf0b7267bd41411a1c2ed97ea72068d2941f4e0e18ec4b69ee3d8c931b6291814a8e1eae478defa2e42b90d48a701c0a900a63ca59c2b95d8eb10c71f583c99f660252f5a6acf2414c69522103b77df3d540817d20d3414f920ccec61b3395e1dc1ef298194e5ff696e038edd921024173f84acb3de56b3ef99894fa3b9a1fe4c48c1bdc39163c37c274cd0334ba752102c0a82ba398612daa4133a891b3f52832114e0d3d6210348543f1872020556ded53aeffffffff01e680d502000000001976a9143fda920e686292be324b438d6509123ecd8e1e9f88ac00000000";
        System.out.println(txHex1.length()/2);
    }

    public static long calcSizeMultiSign(int inputNum, int outputNum, int opReturnBytesLen, int m, int n) {

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


    public static String buildSignedTx(String[] signedData) {
        Map<String, List<byte[]>> fidSigListMap = new HashMap<>();
        byte[] rawTx = null;
        P2SH p2sh = null;

        for (String dataJson : signedData) {
            try {
                System.out.println(dataJson);

                MultiSigData multiSignData = MultiSigData.fromJson(dataJson);

                if (p2sh == null
                        && multiSignData.getP2SH() != null) {
                    p2sh = multiSignData.getP2SH();
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
        if (rawTx == null || p2sh == null) return null;

        return buildSchnorrMultiSignTx(rawTx, fidSigListMap, p2sh);
    }

    public static fch.fchData.Block getBestBlock(ElasticsearchClient esClient) throws ElasticsearchException, IOException {
        SearchResponse<fch.fchData.Block> result = esClient.search(s -> s
                        .index(IndicesNames.BLOCK)
                        .size(1)
                        .sort(so -> so.field(f -> f.field("height").order(SortOrder.Desc)))
                , fch.fchData.Block.class);
        return result.hits().hits().get(0).source();
    }

    public static List<TxInput> cashListToTxInputList(List<Cash> cashList, byte[] priKey32) {
        List<TxInput> txInputList = new ArrayList<>();
        for (Cash cash : cashList) {
            TxInput txInput = cashToTxInput(cash, priKey32);
            if (txInput != null) {
                txInputList.add(txInput);
            }
        }
        if (txInputList.isEmpty()) return null;
        return txInputList;
    }

    public static TxInput cashToTxInput(Cash cash, byte[] priKey32) {
        if (cash == null) {
            System.out.println("Cash is null.");
            return null;
        }
        if (!cash.isValid()) {
            System.out.println("Cash has been spent.");
            return null;
        }
        TxInput txInput = new TxInput();

        txInput.setPriKey32(priKey32);
        txInput.setAmount(cash.getValue());
        txInput.setTxId(cash.getBirthTxId());
        txInput.setIndex(cash.getBirthIndex());

        return txInput;
    }

    public static Transaction buildLockedTx() {
        Transaction transaction = new Transaction(new fch.FchMainNetwork());

        ScriptBuilder scriptBuilder = new ScriptBuilder();
        byte[] hash = KeyTools.addrToHash160("FKi3bRKUPUbUfQuzxT9CfbYwT7m4KEu13R");
        Script script = scriptBuilder.op(169).data(hash).op(135).build();
        return transaction;
    }

    public static Script createP2PKHOutputScript(byte[] hash) {
        Preconditions.checkArgument(hash.length == 20);
        ScriptBuilder builder = new ScriptBuilder();
        builder.op(118);
        builder.op(169);
        builder.data(hash);
        builder.op(136);
        builder.op(172);
        return builder.build();
    }

    public static String sendTxForMsgByAPIP(configure.ApiAccount apiAccount, byte[] symKey, byte[] priKey, List<SendTo> sendToList, String msg) {

        byte[] sessionKey = ApiAccount.decryptSessionKey(apiAccount.getSession().getSessionKeyCipher(),symKey);

        String sender = priKeyToFid(priKey);

        double sum = 0;
        int sendToSize = 0;
        if (sendToList != null && !sendToList.isEmpty()) {
            sendToSize = sendToList.size();
            for (SendTo sendTo : sendToList) sum += sendTo.getAmount();
        }

        long fee = calcTxSize(0, sendToSize, msg.length());

        String urlHead = apiAccount.getApiUrl();
        System.out.println("Getting cashes from " + urlHead + " ...");
        ApipClient apipClient = (ApipClient) apiAccount.getClient();
        List<Cash> cashList =apipClient.cashValid(sender, sum + ((double) fee / COIN_TO_SATOSHI),null, RequestMethod.POST, AuthType.FC_SIGN_BODY);

//        if (apipClientData.checkResponse() != 0) {
//            System.out.println("Failed to get cashes." + apipClientData.getMessage() + apipClientData.getResponseBody().getData());
//            JsonTools.gsonPrint(apipClientData);
//            return apipClientData.getMessage();
//        }
//
//        List<Cash> cashList = DataGetter.getCashList(apipClientData.getResponseBody().getData());

        String txSigned = TxCreator.createTransactionSignFch(cashList, priKey, sendToList, msg);

        System.out.println("Broadcast with " + urlHead + " ...");

        return apipClient.broadcastTx(txSigned, RequestMethod.POST, AuthType.FC_SIGN_BODY);
    }
}
