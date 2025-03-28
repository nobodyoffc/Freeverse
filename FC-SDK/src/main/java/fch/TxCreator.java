package fch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import constants.Constants;
import constants.IndicesNames;
import crypto.KeyTools;
import fch.fchData.*;
import org.bitcoinj.core.Address;
import org.bitcoinj.params.MainNetParams;
import org.jetbrains.annotations.NotNull;
import utils.BytesUtils;
import utils.Hex;
import utils.JsonUtils;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import static constants.Constants.COIN_TO_SATOSHI;
import static crypto.KeyTools.priKeyToFid;

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

    public static String createTxFch(List<Cash> inputs, byte[] priKey, List<SendTo> outputs, String opReturn, FchMainNetwork mainnetwork) {
        return createTxFch(inputs, priKey, outputs, opReturn, 0, mainnetwork);
    }

    public static String createTxFch(List<Cash> inputs, byte[] priKey, List<SendTo> outputs, String opReturn, double feeRateDouble, MainNetParams mainnetwork) {
        String changeToFid = inputs.get(0).getOwner();
        if(outputs==null)outputs = new ArrayList<>();
        long txSize = opReturn == null ? calcTxSize(inputs.size(), outputs.size(), 0) : calcTxSize(inputs.size(), outputs.size(), opReturn.getBytes().length);

        long fee =calcFee(txSize,feeRateDouble);

        Transaction transaction = new Transaction(mainnetwork);

        long totalMoney = 0;
        long totalOutput = 0;

        ECKey eckey = ECKey.fromPrivate(priKey);

        for (SendTo output : outputs) {
            long value = FchUtils.coinToSatoshi(output.getAmount());
            totalOutput += value;
            transaction.addOutput(Coin.valueOf(value), Address.fromBase58(mainnetwork, output.getFid()));
        }

        if (opReturn != null && !opReturn.isEmpty()) {
            try {
                Script opreturnScript = ScriptBuilder.createOpReturnScript(opReturn.getBytes(StandardCharsets.UTF_8));
                transaction.addOutput(Coin.ZERO, opreturnScript);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        totalMoney = addInputToTx(inputs, mainnetwork, transaction);

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


    public static Transaction createUnsignedTx(OffLineTxInfo offLineTxInfo, MainNetParams mainnetwork) {
        try {
            if (offLineTxInfo.getInputs().get(0).getOwner() == null)
                offLineTxInfo.getInputs().get(0).setOwner(offLineTxInfo.getSender());
        }catch (Exception e){
            log.error("The sender is absent.");
            return null;
        }
        return createUnsignedTx(offLineTxInfo.getInputs(), offLineTxInfo.getOutputs(), offLineTxInfo.getMsg(), offLineTxInfo.getP2sh(), offLineTxInfo.getFeeRate(), null, mainnetwork);
    }

    public static Transaction createUnsignedTx(List<Cash> inputs, List<SendTo> outputs, String opReturn, P2SH p2shForMultiSign, double feeRate, String changeToFid, MainNetParams mainnetwork) {
        byte[] opReturnBytes= null;
        if(opReturn!=null) opReturnBytes = opReturn.getBytes();
        if(changeToFid==null)
            changeToFid = inputs.get(0).getOwner();

        boolean isMultiSign = inputs.get(0).getOwner().startsWith("3");

        long fee;

        int inputSize = inputs.size();
        int outputSize = outputs ==null ? 0 : outputs.size();
        int opReturnBytesLen = opReturn ==null ? 0 : opReturnBytes.length;

        fee = calcFee(inputSize, outputSize, opReturnBytesLen, feeRate, isMultiSign, p2shForMultiSign);

        Transaction transaction = new Transaction(mainnetwork);

        long totalOutput = 0;

        long totalMoney = addInputToTx(inputs, mainnetwork, transaction);

        if(outputs !=null && outputs.size()>0){
            for (SendTo output : outputs) {
                long value = FchUtils.coinToSatoshi(output.getAmount());
                totalOutput += value;
                transaction.addOutput(Coin.valueOf(value), Address.fromBase58(mainnetwork, output.getFid()));
            }
        }
        long changeOutputFee = 34L * FchUtils.coinToSatoshi(feeRate/ 1000);

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

    private static long addInputToTx(List<Cash> valueTxIdIndexCashList, MainNetParams mainnetwork, Transaction transaction) {
        long totalMoney=0;

        for (Cash input : valueTxIdIndexCashList) {
            totalMoney += input.getValue();
            TransactionOutPoint outPoint = new TransactionOutPoint(mainnetwork, input.getBirthIndex(), Sha256Hash.wrap(input.getBirthTxId()));
            TransactionInput unsignedInput = new TransactionInput(mainnetwork, null, new byte[0], outPoint, Coin.valueOf(input.getValue()));
            transaction.addInput(unsignedInput);
        }

        return totalMoney;
    }

    @Test
    public void testTx(){
        MainNetParams mainnetwork = fch.FchMainNetwork.get();//BtcMainNetParams.get();//
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

    @NotNull
    public static String signTx(byte[] priKey, Transaction transaction) {
        ECKey eckey = ECKey.fromPrivate(priKey);

        List<TransactionInput> inputs = transaction.getInputs();
        for (int i = 0; i < inputs.size(); ++i) {
            TransactionInput input = inputs.get(i);
            Script script = ScriptBuilder.createP2PKHOutputScript(eckey);
            Coin value = input.getValue();
            if(value==null)continue;
            SchnorrSignature signature = transaction.calculateSchnorrSignature(i, eckey, script.getProgram(), Coin.valueOf(value.getValue()), Transaction.SigHash.ALL, false);
            Script schnorr = ScriptBuilder.createSchnorrInputScript(signature, eckey);
            transaction.getInput(i).setScriptSig(schnorr);
        }

        byte[] signResult = transaction.bitcoinSerialize();
        return Hex.toHex(signResult);
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


    public static String signRawTx(String valuesAndRawTx, byte[] priKey, MainNetParams mainnetwork) {
        Transaction transaction = parseOldCsRawTxToTx(valuesAndRawTx, mainnetwork);
        if (transaction == null) return null;
        return signTx(priKey, transaction);
    }

    //Off line TX methods

    /**
     * Parse user off-line TX request json
     */
    public static OffLineTxRequestData parseDataForOffLineTxFromOther(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, OffLineTxRequestData.class);
    }

    /**
     * Convert off-line TX information to Transaction.
     */
    public static Transaction parseOffLineTx(OffLineTxInfo offLineTxInfo, MainNetParams mainnetwork) {
        List<Cash> cashList = offLineTxInfo.getInputs();
        List<SendTo> sendToList = offLineTxInfo.getOutputs();
        String msg = offLineTxInfo.getMsg();
        return createUnsignedTx(cashList, sendToList, msg, offLineTxInfo.getP2sh(), offLineTxInfo.getFeeRate(), null, mainnetwork);
    }

    public static class OffLineTxRequestData {
        private String fromFid;
        private List<SendTo> sendToList;
        private Long cd;
        private String msg;

        public String getFromFid() {
            return fromFid;
        }

        public void setFromFid(String fromFid) {
            this.fromFid = fromFid;
        }

        public List<SendTo> getSendToList() {
            return sendToList;
        }

        public void setSendToList(List<SendTo> sendToList) {
            this.sendToList = sendToList;
        }

        public Long getCd() {
            return cd;
        }

        public void setCd(Long cd) {
            this.cd = cd;
        }

        public String getMsg() {
            return msg;
        }

        public void setMsg(String msg) {
            this.msg = msg;
        }
    }


    //Old methods
    public static String createTxFch(List<TxInput> inputs, List<TxOutput> outputs, String opReturn, String returnAddr) {
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

    //For old CryptoSign off line TX
    public static String createUnsignedTxForOldCs(List<Cash> cashList, List<SendTo> outputs, String opReturn, P2SH p2shForMultiSign, double feeRate, MainNetParams mainnetwork) {
        List<RawTxForCs> rawTxForCsList = new ArrayList<>();

        for(int i = 0; i < cashList.size(); i++){
            Cash cash = cashList.get(i);
            rawTxForCsList.add(RawTxForCs.newInput(cash.getOwner(), FchUtils.satoshiToCoin(cash.getValue()), cash.getBirthTxId(), cash.getBirthIndex(), i));
        }

        int j=0;
        for(; j < outputs.size(); j++){
            SendTo output = outputs.get(j);
            RawTxForCs rawTxForCs = RawTxForCs.newOutput(output.getFid(), output.getAmount(), j);
            if(rawTxForCs!=null)rawTxForCsList.add(rawTxForCs);
        }
        if(opReturn!=null){
            RawTxForCs rawTxForCs = RawTxForCs.newOpReturn(opReturn, j);
            if(rawTxForCs!=null)rawTxForCsList.add(rawTxForCs);
        }


        return new Gson().toJson(rawTxForCsList);
    }

    public static String makeOldCsTxRequiredJson(OffLineTxRequestData sendRequestForCs, List<Cash> meetList) {
        Gson gson = new Gson();
        StringBuilder RawTx = new StringBuilder("[");
        int i = 0;
        for (Cash cash : meetList) {
            if (i > 0) RawTx.append(",");
            RawTxForCs rawTxForCs = new RawTxForCs();
            rawTxForCs.setAddress(cash.getOwner());
            rawTxForCs.setAmount((double) cash.getValue() / COIN_TO_SATOSHI);
            rawTxForCs.setTxid(cash.getBirthTxId());
            rawTxForCs.setIndex(cash.getBirthIndex());
            rawTxForCs.setSeq(i);
            rawTxForCs.setDealType(RawTxForCs.DealType.INPUT);
            RawTx.append(gson.toJson(rawTxForCs));
            i++;
        }
        int j = 0;
        if (sendRequestForCs.getSendToList() != null) {
            for (SendTo sendTo : sendRequestForCs.getSendToList()) {
                RawTxForCs rawTxForCs = new RawTxForCs();
                rawTxForCs.setAddress(sendTo.getFid());
                rawTxForCs.setAmount(sendTo.getAmount());
                rawTxForCs.setSeq(j);
                rawTxForCs.setDealType(RawTxForCs.DealType.OUTPUT);
                RawTx.append(",");
                RawTx.append(gson.toJson(rawTxForCs));
                j++;
            }
        }

        if (sendRequestForCs.getMsg() != null) {
            RawTxForCs rawOpReturnForCs = new RawTxForCs();
            rawOpReturnForCs.setMsg(sendRequestForCs.getMsg());
            rawOpReturnForCs.setSeq(j);
            rawOpReturnForCs.setDealType(RawTxForCs.DealType.OP_RETURN);
            RawTx.append(",");
            RawTx.append(gson.toJson(rawOpReturnForCs));
        }
        RawTx.append("]");
        return RawTx.toString();
    }

    //TODO Same function below 2 methods
    public static Transaction parseCsRawTxToTx(String oldCsUnsignedTx, MainNetParams mainnetwork) {
        List<RawTxForCs> rawTxForCsList = JsonUtils.listFromJson(oldCsUnsignedTx, RawTxForCs.class);
        if(rawTxForCsList == null) {
            System.out.println("Invalid TX information.");
            return null;
        }
        OffLineTxInfo offLineTxInfo = OffLineTxInfo.fromRawTxForCs(rawTxForCsList);
        return parseOffLineTx(offLineTxInfo, mainnetwork);
    }

    public static Transaction parseOldCsRawTxToTx(String oldCsUnsignedTx, MainNetParams mainnetwork) {
        List<Cash> cashList = new ArrayList<>();
        List<SendTo> sendToList = new ArrayList<>();
        String msg = null;

        // Parse the JSON array
        List<RawTxForCs> rawTxForCsList = parseRawTxForCsList(oldCsUnsignedTx);

        for (RawTxForCs element : rawTxForCsList) {
            
            int dealType = element.getDealType().getValue();

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
                    SendTo sendTo = new SendTo();
                    sendTo.setFid(element.getAddress());
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

    private static List<RawTxForCs> parseRawTxForCsList(String oldCsUnsignedTx) {
        Gson gson = new Gson();
        return gson.fromJson(oldCsUnsignedTx, new TypeToken<List<RawTxForCs>>() {}.getType());
    }

// Sign TX
    public static String signSchnorrMultiSignTx(String multiSignDataJson, byte[] priKey, MainNetParams mainNetParams) {
        MultiSigData multiSignData = MultiSigData.fromJson(multiSignDataJson);
        return signSchnorrMultiSignTx(multiSignData, priKey, mainNetParams).toJson();
    }

    public static MultiSigData signSchnorrMultiSignTx(MultiSigData multiSignData, byte[] priKey, MainNetParams mainnetwork) {

        byte[] rawTx = multiSignData.getRawTx();
        byte[] redeemScript = HexFormat.of().parseHex(multiSignData.getP2SH().getRedeemScript());
        List<Cash> cashList = multiSignData.getCashList();

        Transaction transaction = new Transaction(mainnetwork, rawTx);
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

    public static boolean rawTxSigVerify(byte[] rawTx, byte[] pubKey, byte[] sig, int inputIndex, long inputValue, byte[] redeemScript, MainNetParams mainnetwork) {
        Transaction transaction = new Transaction(mainnetwork, rawTx);
        Script script = new Script(redeemScript);
        Sha256Hash hash = transaction.hashForSignatureWitness(inputIndex, script, Coin.valueOf(inputValue), Transaction.SigHash.ALL, false);
        return SchnorrSignature.schnorr_verify(hash.getBytes(), pubKey, sig);
    }

    public static String buildSchnorrMultiSignTx(byte[] rawTx, Map<String, List<byte[]>> sigListMap, P2SH p2sh, MainNetParams mainnetwork) {

        if (sigListMap.size() > p2sh.getM())
            sigListMap = dropRedundantSigs(sigListMap, p2sh.getM());

        Transaction transaction = new Transaction(mainnetwork, rawTx);

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
//            System.out.println(HexFormat.of().formatHex(inputScript.getProgram()));
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
        if (signatures.size() >= 16) return null;
        ScriptBuilder builder = new ScriptBuilder();
        builder.smallNum(0);
        Iterator<byte[]> var3 = signatures.iterator();
        byte[] sigHashAll = new byte[]{0x41};

        while (var3.hasNext()) {
            byte[] signature = (byte[]) var3.next();
            builder.data(BytesUtils.bytesMerger(signature, sigHashAll));
        }

        if (multisigProgramBytes != null) {
            builder.data(multisigProgramBytes);
        }

        return builder.build();
    }

    public static String createTimeLockedTransaction(List<Cash> inputs, byte[] priKey, List<SendTo> outputs, long lockUntil, String opReturn, MainNetParams mainnetwork) {

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

        for (SendTo output : outputs) {
            long value = FchUtils.coinToSatoshi(output.getAmount());
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
    public static P2SH createP2sh(List<byte[]> pubKeyList, int m) {
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


    public static String buildSignedTx(String[] signedData, MainNetParams mainnetwork) {
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

        return buildSchnorrMultiSignTx(rawTx, fidSigListMap, p2sh, mainnetwork);
    }

    public static fch.fchData.Block getBestBlock(ElasticsearchClient esClient) throws ElasticsearchException, IOException {
        SearchResponse<fch.fchData.Block> result = esClient.search(s -> s
                        .index(IndicesNames.BLOCK)
                        .size(1)
                        .sort(so -> so.field(f -> f.field("height").order(SortOrder.Desc)))
                , fch.fchData.Block.class);
        return result.hits().hits().get(0).source();
    }

    //Unfinished
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
    @Test
    public void test(){
        int opReturnBytesLen = "hi".getBytes().length;
        System.out.println(calcSizeMultiSign(1,1, opReturnBytesLen,2,3));
        String txHex= "020000000185231da3cc3a00496258f633d7e48442e51ffa51c9b0efe92d80a84eb61b43c103000000f0004151e694db47016366908a43f9900a00ab537e5fba8da4892e1db3ba4f00b893792d920c13d7fc3dafa88ec6bbc3027cfb308ef2264f470fdb819e90d16d956fec4141447743a23a589ecef0e30d05dc2957c8213127e66efeb6738142df035e5d1f21d2ed933a9e7eb00bef22af016b248ee34b9877f52bf0fcfd98714d4ecf4b218f414c695221030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a2102536e4f3a6871831fa91089a5d5a950b96a31c861956f01459c0cd4f4374b2f672103f0145ddf5debc7169952b17b5c6a8a566b38742b6aa7b33b667c0a7fa73762e253aeffffffff03809698000000000017a914d86ffd4d1ade6ca5f19e8205bb4ddb0a05c92a72870000000000000000046a026869fe457f0f0000000017a914d86ffd4d1ade6ca5f19e8205bb4ddb0a05c92a728700000000";
        System.out.println(txHex.length()/2);
    }
}
