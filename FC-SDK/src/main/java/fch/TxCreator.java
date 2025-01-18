package fch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.google.common.base.Preconditions;
import constants.Constants;
import constants.IndicesNames;
import crypto.KeyTools;
import fch.fchData.Cash;
import fch.fchData.P2SH;
import fch.fchData.SendTo;
import tools.BytesTools;
import tools.Hex;
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
import java.io.ByteArrayInputStream;
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

    public static String createTxFch(List<TxInput> inputs, List<TxOutput> outputs, String opReturn, String returnAddr) {
        FchMainNetwork mainnetwork = FchMainNetwork.MAINNETWORK;
        return createTxClassic(mainnetwork, inputs, outputs, opReturn, returnAddr, 0);
    }

    public static String createTxFch(List<TxInput> inputs, List<TxOutput> outputs, String opReturn, String returnAddr, double feeRateDouble) {
        FchMainNetwork mainnetwork = FchMainNetwork.MAINNETWORK;
        return createTxClassic(mainnetwork, inputs, outputs, opReturn, returnAddr, feeRateDouble);
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

    public static String createTxFch(List<Cash> inputs, byte[] priKey, List<SendTo> outputs, String opReturn) {
        return createTxFch(inputs, priKey, outputs, opReturn, 0);
    }

    public static String createTxFch(List<Cash> inputs, byte[] priKey, List<SendTo> outputs, String opReturn, double feeRateDouble) {
        String changeToFid = inputs.get(0).getOwner();
        if(outputs==null)outputs = new ArrayList<>();
        long txSize = opReturn == null ? calcTxSize(inputs.size(), outputs.size(), 0) : calcTxSize(inputs.size(), outputs.size(), opReturn.getBytes().length);

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
            TransactionOutPoint outPoint = new TransactionOutPoint(FchMainNetwork.MAINNETWORK, input.getBirthIndex(), Sha256Hash.wrap(input.getBirthTxId()));
            TransactionInput unsignedInput = new TransactionInput(new FchMainNetwork(), null, new byte[0], outPoint);
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


    public static String createUnsignedTxFch(List<Cash> inputs, List<SendTo> outputs, String opReturn, P2SH p2shForMultiSign, double feeRate) {
        byte[] opReturnBytes= null;
        if(opReturn!=null) opReturnBytes = opReturn.getBytes();
        byte[] unsignedTx = createUnsignedTxFch(inputs,outputs,opReturnBytes,p2shForMultiSign,feeRate);
        if(unsignedTx==null)return null;
        return Base64.getEncoder().encodeToString(unsignedTx);
    }

    public static byte[] createUnsignedTxFch(List<Cash> inputs, List<SendTo> outputs, byte[] opReturn, P2SH p2shForMultiSign, double feeRate) {
        String changeToFid = inputs.get(0).getOwner();

        boolean isMultiSign;
        if (changeToFid.startsWith("3"))isMultiSign=true;
        else isMultiSign = false;

        long fee;

        int inputSize = inputs.size();
        int outputSize = outputs==null ? 0 : outputs.size();
        int opReturnBytesLen = opReturn==null ? 0 : opReturn.length;

        fee = calcFee(inputSize, outputSize, opReturnBytesLen, feeRate, isMultiSign, p2shForMultiSign);

        Transaction transaction = new Transaction(FchMainNetwork.MAINNETWORK);

        long totalMoney = 0;
        long totalOutput = 0;

        if(outputs!=null)
            for (SendTo output : outputs) {
                long value = ParseTools.coinToSatoshi(output.getAmount());
                totalOutput += value;
                transaction.addOutput(Coin.valueOf(value), Address.fromBase58(FchMainNetwork.MAINNETWORK, output.getFid()));
            }

        if (opReturn != null && opReturn.length>0) {
            try {
                Script opreturnScript = ScriptBuilder.createOpReturnScript(opReturn);
                transaction.addOutput(Coin.ZERO, opreturnScript);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.write(new byte[]{(byte) 0xFF});
        byte[] b2 = BytesTools.intTo2ByteArray(inputSize);
        byteArrayBuilder.write(b2);
        for (Cash input : inputs) {
            byteArrayBuilder.write(BytesTools.longToBytes(input.getValue()));
            totalMoney += input.getValue();
            TransactionOutPoint outPoint = new TransactionOutPoint(FchMainNetwork.MAINNETWORK, input.getBirthIndex(), Sha256Hash.wrap(input.getBirthTxId()));
            TransactionInput unsignedInput = new TransactionInput(new FchMainNetwork(), null, new byte[0], outPoint,Coin.valueOf(input.getValue()));
            transaction.addInput(unsignedInput);
        }

        if ((totalOutput + fee) > totalMoney) {
            System.out.println("Input is not enough");
            return null;
        }
        long change = totalMoney - totalOutput - fee;
        if (change > Constants.DustInSatoshi) {
            transaction.addOutput(Coin.valueOf(change), Address.fromBase58(FchMainNetwork.MAINNETWORK, changeToFid));
        }

        byte[] txBytes = transaction.bitcoinSerialize();
        if(txBytes==null)return null;

        byteArrayBuilder.write(txBytes);

        byte[] valuesAndRawTxBytes = byteArrayBuilder.toByteArray();
        byteArrayBuilder.close();
        return valuesAndRawTxBytes;
    }

    public static long calcFee(int inputSize, int outputSize, int opReturnBytesLen, double feeRate, boolean isMultiSign, P2SH p2shForMultiSign) {
        long fee;
        if(isMultiSign) {
            long feeRateLong = (long) (feeRate / 1000 * COIN_TO_SATOSHI);
            fee = feeRateLong * TxCreator.calcSizeMultiSign(inputSize, outputSize, opReturnBytesLen, p2shForMultiSign.getM(), p2shForMultiSign.getN());
        }else {
            long txSize = calcTxSize(inputSize, outputSize, opReturnBytesLen);
            fee =calcFee(txSize, feeRate);
        }
        return fee;
    }


    public static String signRawTxFch(String valuesAndRawTxBase64, byte[] priKey) {
        byte[] valuesAndRawTxBytes = Base64.getDecoder().decode(valuesAndRawTxBase64);
        byte[] signResult = signRawTxFch(valuesAndRawTxBytes, priKey);
        if(signResult==null)return null;
        return Base64.getEncoder().encodeToString(signResult);
    }

    public static byte[] signRawTxFch(byte[] valuesAndRawTxBytes, byte[] priKey) {
        List<Long> inputValueList = new ArrayList<>();
        byte[] rawTx;
        try(ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(valuesAndRawTxBytes)) {
            int flag = byteArrayInputStream.read();
            if(flag!= 255){
                System.out.println("Missing the required input values for sign.");
                return null;
            }
            byte[] b2 = new byte[2];
            byteArrayInputStream.read(b2);
            int size = BytesTools.bytes2ToIntBE(b2);
            byte[] b8 = new byte[8];
            for (int i = 0; i < size; i++) {
                byteArrayInputStream.read(b8);
                inputValueList.add(BytesTools.bytes8ToLong(b8, false));
            }
            if (inputValueList.size() != size) return null;
            rawTx = byteArrayInputStream.readAllBytes();
        } catch (IOException e) {
            System.out.println("Failed to parse valuesAndRawTxHex: "+e.getMessage());
            return null;
        }
        if(inputValueList.isEmpty())return null;

        Transaction transaction = new Transaction(FchMainNetwork.MAINNETWORK, rawTx);

        List<TransactionInput> inputs = transaction.getInputs();

        ECKey eckey = ECKey.fromPrivate(priKey);
        for (int i=0;i<inputs.size();i++) {
            Coin value = Coin.valueOf(inputValueList.get(i));
            Script script = ScriptBuilder.createP2PKHOutputScript(eckey);
            SchnorrSignature signature = transaction.calculateSchnorrSignature(i, eckey, script.getProgram(), value, Transaction.SigHash.ALL, false);
            Script schnorr = ScriptBuilder.createSchnorrInputScript(signature, eckey);
            transaction.getInput(i).setScriptSig(schnorr);
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
        Iterator<byte[]> var3 = signatures.iterator();
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

    public static String decodeTxFch(String rawTx) {
        byte[] rawTxBytes;
        try{
            if(Hex.isHexString(rawTx))rawTxBytes = Hex.fromHex(rawTx);
            else {
                rawTxBytes = Base64.getDecoder().decode(rawTx);
            }
        }catch (Exception e){
            return null;
        }
        return decodeTxFch(rawTxBytes);
    }


    public static String decodeTxFch(byte[] rawTxBytes) {
        if(rawTxBytes==null) return null;

        Transaction transaction;
            // Handle parsing of combined format with input values
            List<Long> inputValueList = new ArrayList<>();
            byte[] rawTx;
            try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(rawTxBytes)) {
                int flag = byteArrayInputStream.read();
                if(flag== 255) {
                    byte[] b2 = new byte[2];
                    byteArrayInputStream.read(b2);
                    int size = BytesTools.bytes2ToIntBE(b2);
                    byte[] b8 = new byte[8];
                    for (int i = 0; i < size; i++) {
                        byteArrayInputStream.read(b8);
                        inputValueList.add(BytesTools.bytes8ToLong(b8, false));
                    }
                    rawTx = byteArrayInputStream.readAllBytes();
                    transaction = new Transaction(FchMainNetwork.MAINNETWORK, rawTx);
                }else {
                    transaction = new Transaction(FchMainNetwork.MAINNETWORK, rawTxBytes);
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
                    Address address = output.getScriptPubKey().getToAddress(FchMainNetwork.MAINNETWORK);
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

    @Test
    public void test(){
        int opReturnBytesLen = "hi".getBytes().length;
        System.out.println(calcSizeMultiSign(1,1, opReturnBytesLen,2,3));
        String txHex= "020000000185231da3cc3a00496258f633d7e48442e51ffa51c9b0efe92d80a84eb61b43c103000000f0004151e694db47016366908a43f9900a00ab537e5fba8da4892e1db3ba4f00b893792d920c13d7fc3dafa88ec6bbc3027cfb308ef2264f470fdb819e90d16d956fec4141447743a23a589ecef0e30d05dc2957c8213127e66efeb6738142df035e5d1f21d2ed933a9e7eb00bef22af016b248ee34b9877f52bf0fcfd98714d4ecf4b218f414c695221030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a2102536e4f3a6871831fa91089a5d5a950b96a31c861956f01459c0cd4f4374b2f672103f0145ddf5debc7169952b17b5c6a8a566b38742b6aa7b33b667c0a7fa73762e253aeffffffff03809698000000000017a914d86ffd4d1ade6ca5f19e8205bb4ddb0a05c92a72870000000000000000046a026869fe457f0f0000000017a914d86ffd4d1ade6ca5f19e8205bb4ddb0a05c92a728700000000";
        System.out.println(txHex.length()/2);
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
}
