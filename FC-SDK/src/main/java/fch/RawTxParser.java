package fch;

import fch.fchData.TxHasInfo;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import constants.Strings;
import fch.fchData.Cash;
import fch.fchData.Tx;
import javaTools.BytesTools;
import crypto.KeyTools;
import org.junit.jupiter.api.Test;
import clients.esClient.EsTools;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static constants.IndicesNames.CASH;
import static fch.ParseTools.parseVarint;

public class RawTxParser {

    public static TxHasInfo parseMempoolTx(ElasticsearchClient esClient, String txHex, String txid) throws Exception {
        Map<String, Map<String, Cash>> cashMapMap = parseUnconfirmedTxHex(txHex, txid);
        ArrayList<Cash> inCashList = makeInCashMap(esClient, cashMapMap.get(Strings.spendCashMapKey), cashMapMap.get(Strings.newCashMapKey));
        ArrayList<Cash> outCashList = new ArrayList<>(cashMapMap.get(Strings.newCashMapKey).values());

        for (Cash cash : inCashList) cash.setSpendTxId(txid);
        for (Cash cash : outCashList) cash.setBirthTxId(txid);

        Tx tx = makeTx(txid, inCashList, outCashList);
        TxHasInfo txInMempool = new TxHasInfo();
        txInMempool.setTx(tx);
        txInMempool.setInCashList(inCashList);
        txInMempool.setOutCashList(outCashList);
        return txInMempool;
    }

    private static Tx makeTx(String txid, ArrayList<Cash> inCashList, ArrayList<Cash> outCashList) {
        Tx tx = new Tx();
        tx.setTxId(txid);
        tx.setInCount(inCashList.size());
        long inValueT = 0;
        for (Cash cash : inCashList) {
            inValueT = inValueT + cash.getValue();
        }
        tx.setInValueT(inValueT);
        tx.setOutCount(outCashList.size());
        long outValueT = 0;
        for (Cash cash : outCashList) {
            outValueT = outValueT + cash.getValue();
        }
        tx.setOutValueT(outValueT);
        tx.setFee(inValueT - outValueT);
        return tx;
    }

    public static Map<String, Map<String, Cash>> parseUnconfirmedTxHex(String txHex, String txid) throws Exception {
        byte[] txBytes = BytesTools.hexToByteArray(txHex);
        return parseRawTxBytes(txBytes, txid);
    }

    public static Map<String, Map<String, Cash>> parseRawTxBytes(byte[] txBytes, String txid) throws Exception {

        ByteArrayInputStream blockInputStream = new ByteArrayInputStream(txBytes);

        // Read tx version/读取交易的版本
        byte[] b4Version = new byte[4];
        blockInputStream.read(b4Version);

        // Read inputs /读输入
        // ParseTxOutResult parseTxOutResult
        Map<String, Cash> spendCashMap = parseInput(blockInputStream, txid);

        // Read outputs /读输出
        // Parse Outputs./解析输出。
        Map<String, Cash> newCashMap = parseOut(blockInputStream, txid);

        Map<String, Map<String, Cash>> cashMapMap = new HashMap<>();
        cashMapMap.put(Strings.spendCashMapKey, spendCashMap);
        cashMapMap.put(Strings.newCashMapKey, newCashMap);
        return cashMapMap;
    }

    public static Map<String, Object> parseRawTxBytes(byte[] txBytes) throws Exception {

        List<Cash> spentCashList = new ArrayList<>();
        List<Cash> issuredCashList = new ArrayList<>();
        String msg = null;

        ByteArrayInputStream txInputStream = new ByteArrayInputStream(txBytes);

        // Read tx version/读取交易的版本
        byte[] b4Version = new byte[4];
        txInputStream.read(b4Version);

        // Read inputs /读输入
        // ParseTxOutResult parseTxOutResult

        // Get input count./获得输入数量
        ParseTools.VarintResult varintParseResult;
        varintParseResult = parseVarint(txInputStream);
        long inputCount = varintParseResult.number;

        // Read inputs /读输入
        for (int j = 0; j < inputCount; j++) {
            Cash spentCash = new Cash();
            // Read preTXHash and preOutIndex./读取前交易哈希和输出索引。
            byte[] b36PreTxIdAndIndex = new byte[32 + 4];
            txInputStream.read(b36PreTxIdAndIndex);
            String cashId = ParseTools.calcTxoIdFromBytes(b36PreTxIdAndIndex);
            spentCash.setCashId(cashId);


            // Read the length of script./读脚本长度。
            varintParseResult = parseVarint(txInputStream);
            long scriptCount = varintParseResult.number;

            if (scriptCount != 0) {
                // Get script./获取脚本。
                byte[] bvScript = new byte[(int) scriptCount];
                txInputStream.read(bvScript);

                // Parse sigHash.
                // 解析sigHash。
                int sigLen = Byte.toUnsignedInt(bvScript[0]);// Length of signature;
                // Skip signature/跳过签名。
                byte sigHash = bvScript[sigLen];// 交易类型标志
                switch (sigHash) {
                    case 0x41:
                        spentCash.setSigHash("ALL");
                        break;
                    case 0x42:
                        spentCash.setSigHash("NONE");
                        break;
                    case 0x43:
                        spentCash.setSigHash("SINGLE");
                        break;
                    case (byte) 0xc1:
                        spentCash.setSigHash("ALLIANYONECANPAY");
                        break;
                    case (byte) 0xc2:
                        spentCash.setSigHash("NONEIANYONECANPAY");
                        break;
                    case (byte) 0xc3:
                        spentCash.setSigHash("SINGLEIANYONECANPAY");
                        break;
                    default:
                        spentCash.setSigHash(null);
                }
            }
            // Get sequence./获取sequence。
            byte[] b4Sequence = new byte[4];
            txInputStream.read(b4Sequence);
            spentCash.setSequence(BytesTools.bytesToHexStringBE(b4Sequence));
            spentCashList.add(spentCash);

        }

        // Parse Outputs./解析输出。
        // Parse output count.
        // 解析输出数量。
        ParseTools.VarintResult varintParseResult1 = parseVarint(txInputStream);
        long outputCount = varintParseResult1.number;

        // Starting operators in output script.
        // 输出脚本中的起始操作码。
        final byte OP_DUP = (byte) 0x76;
        final byte OP_HASH160 = (byte) 0xa9;
        final byte OP_RETURN = (byte) 0x6a;
        byte b1Script = 0x00; // For get the first byte of output script./接收脚本中的第一个字节。

        for (int j = 0; j < outputCount; j++) {
            // Start one output.
            // 开始解析一个输出。
            Cash newCash = new Cash();
            newCash.setBirthIndex(j);

            // Read the value of this output in satoshi.
            // 读取该输出的金额，以聪为单位。
            byte[] b8Value = new byte[8];
            txInputStream.read(b8Value);
            newCash.setValue(BytesTools.bytes8ToLong(b8Value, true));

            // Parse the length of script.
            // 解析脚本长度。
            varintParseResult1 = parseVarint(txInputStream);
            long scriptSize = varintParseResult1.number;
            byte[] b2 = varintParseResult1.rawBytes;

            byte[] bScript = new byte[(int) scriptSize];
            txInputStream.read(bScript);

            b1Script = bScript[0];

            switch (b1Script) {
                case OP_DUP -> {
                    newCash.setType("P2PKH");
                    newCash.setLockScript(BytesTools.bytesToHexStringBE(bScript));
                    byte[] hash160Bytes = Arrays.copyOfRange(bScript, 3, 23);
                    newCash.setOwner(KeyTools.hash160ToFchAddr(hash160Bytes));
                }
                case OP_RETURN -> {
                    newCash.setType("OP_RETURN");
                    msg = parseOpReturn(bScript);//new String(Arrays.copyOfRange(bScript, 2, bScript.length));
                    newCash.setOwner("OpReturn");
                    newCash.setValid(false);
                }
                case OP_HASH160 -> {
                    newCash.setType("P2SH");
                    newCash.setLockScript(BytesTools.bytesToHexStringBE(bScript));
                    byte[] hash160Bytes1 = Arrays.copyOfRange(bScript, 2, 22);
                    newCash.setOwner(KeyTools.hash160ToMultiAddr(hash160Bytes1));
                }
                default -> {
                    newCash.setType("Unknown");
                    newCash.setLockScript(BytesTools.bytesToHexStringBE(bScript));
                    newCash.setOwner("Unknown");
                }
            }

            // Add block and tx information to output./给输出添加区块和交易信息。
            // Add information where it from/添加来源信息
            newCash.setValid(true);

            // Add this output to List.
            // 添加输出到列表。
            issuredCashList.add(newCash);
        }

        Map<String, Object> result = new HashMap<>();
        result.put(Strings.spendCashMapKey, spentCashList);
        result.put(Strings.newCashMapKey, issuredCashList);
        result.put(Strings.OPRETURN, msg);
        return result;
    }

    public static String parseOpReturn(byte[] bScript) throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(bScript);
        byte[] op_return = new byte[1];
        byte[] opcode = new byte[1];
        byte[] dataLen1 = new byte[1];
        byte[] dataLen2 = new byte[2];
        byte[] msgBytes = null;

        bis.read(op_return);
        if (op_return[0] != 0x6a) return null;

        bis.read(opcode);
        if (opcode[0] < 76) {
            msgBytes = new byte[opcode[0]];
        }
        if (opcode[0] == 76) {
            bis.read(dataLen1);
            msgBytes = new byte[(dataLen1[0] & 0xFF)];//new byte[bScript.length-3];
        }
        if (opcode[0] == 77) {
            bis.read(dataLen2);
            msgBytes = new byte[BytesTools.bytes2ToIntLE(dataLen2)];//new byte[bScript.length-4];
        }
        if (opcode[0] > 77) {
            msgBytes = new byte[bScript.length - 2];
        }

        bis.read(msgBytes);
        bis.close();
        return new String(msgBytes, StandardCharsets.UTF_8);
    }

    private static Map<String, Cash> parseInput(ByteArrayInputStream rawTxInputStream, String txid) throws IOException {

        // Get input count./获得输入数量
        ParseTools.VarintResult varintParseResult;
        varintParseResult = parseVarint(rawTxInputStream);
        long inputCount = varintParseResult.number;

        // Read inputs /读输入
        Map<String, Cash> spendCashMap = new HashMap<>();

        for (int j = 0; j < inputCount; j++) {
            Cash spentCash = new Cash();
            spentCash.setSpendTxId(txid);
            // Read preTXHash and preOutIndex./读取前交易哈希和输出索引。
            byte[] b36PreTxIdAndIndex = new byte[32 + 4];
            rawTxInputStream.read(b36PreTxIdAndIndex);
            String cashId = ParseTools.calcTxoIdFromBytes(b36PreTxIdAndIndex);
            spentCash.setCashId(cashId);

            // Read the length of script./读脚本长度。
            varintParseResult = parseVarint(rawTxInputStream);
            long scriptCount = varintParseResult.number;

            // Get script./获取脚本。
            byte[] bvScript = new byte[(int) scriptCount];
            rawTxInputStream.read(bvScript);
            spentCash.setUnlockScript(BytesTools.bytesToHexStringBE(bvScript));

            // Parse sigHash.
            // 解析sigHash。
            int sigLen = Byte.toUnsignedInt(bvScript[0]);// Length of signature;
            // Skip signature/跳过签名。
            byte sigHash = bvScript[sigLen];// 交易类型标志
            switch (sigHash) {
                case 0x41:
                    spentCash.setSigHash("ALL");
                    break;
                case 0x42:
                    spentCash.setSigHash("NONE");
                    break;
                case 0x43:
                    spentCash.setSigHash("SINGLE");
                    break;
                case (byte) 0xc1:
                    spentCash.setSigHash("ALLIANYONECANPAY");
                    break;
                case (byte) 0xc2:
                    spentCash.setSigHash("NONEIANYONECANPAY");
                    break;
                case (byte) 0xc3:
                    spentCash.setSigHash("SINGLEIANYONECANPAY");
                    break;
                default:
                    spentCash.setSigHash(null);
            }

            // Get sequence./获取sequence。
            byte[] b4Sequence = new byte[4];
            rawTxInputStream.read(b4Sequence);

            spentCash.setSequence(BytesTools.bytesToHexStringBE(b4Sequence));
            spendCashMap.put(cashId, spentCash);
        }
        return spendCashMap;
    }

    private static Map<String, Cash> parseOut(ByteArrayInputStream rawTxInputStream, String txid) throws IOException {
        Map<String, Cash> rawNewCashMap = new HashMap<>();

        // Parse output count.
        // 解析输出数量。
        ParseTools.VarintResult varintParseResult = new ParseTools.VarintResult();
        varintParseResult = parseVarint(rawTxInputStream);
        long outputCount = varintParseResult.number;

        // Starting operators in output script.
        // 输出脚本中的起始操作码。
        final byte OP_DUP = (byte) 0x76;
        final byte OP_HASH160 = (byte) 0xa9;
        final byte OP_RETURN = (byte) 0x6a;
        byte b1Script = 0x00; // For get the first byte of output script./接收脚本中的第一个字节。

        for (int j = 0; j < outputCount; j++) {
            // Start one output.
            // 开始解析一个输出。
            Cash newCash = new Cash();
            newCash.setBirthTxId(txid);
            newCash.setBirthIndex(j);
            String cashId = ParseTools.calcTxoId(txid, j);
            newCash.setCashId(cashId);

            // Read the value of this output in satoshi.
            // 读取该输出的金额，以聪为单位。
            byte[] b8Value = new byte[8];
            rawTxInputStream.read(b8Value);
            newCash.setValue(BytesTools.bytes8ToLong(b8Value, true));

            // Parse the length of script.
            // 解析脚本长度。
            varintParseResult = parseVarint(rawTxInputStream);
            long scriptSize = varintParseResult.number;
            byte[] b2 = varintParseResult.rawBytes;

            byte[] bScript = new byte[(int) scriptSize];
            rawTxInputStream.read(bScript);

            b1Script = bScript[0];

            switch (b1Script) {
                case OP_DUP -> {
                    newCash.setType("P2PKH");
                    newCash.setLockScript(BytesTools.bytesToHexStringBE(bScript));
                    byte[] hash160Bytes = Arrays.copyOfRange(bScript, 3, 23);
                    newCash.setOwner(KeyTools.hash160ToFchAddr(hash160Bytes));
                }
                case OP_RETURN -> {
                    newCash.setType("OP_RETURN");
                    newCash.setOwner("OpReturn");
                    newCash.setValid(false);
                }
                case OP_HASH160 -> {
                    newCash.setType("P2SH");
                    newCash.setLockScript(BytesTools.bytesToHexStringBE(bScript));
                    byte[] hash160Bytes1 = Arrays.copyOfRange(bScript, 2, 22);
                    newCash.setOwner(KeyTools.hash160ToMultiAddr(hash160Bytes1));
                }
                default -> {
                    newCash.setType("Unknown");
                    newCash.setLockScript(BytesTools.bytesToHexStringBE(bScript));
                    newCash.setOwner("Unknown");
                }
            }

            // Add block and tx information to output./给输出添加区块和交易信息。
            // Add information where it from/添加来源信息
            newCash.setValid(true);

            // Add this output to List.
            // 添加输出到列表。
            rawNewCashMap.put(cashId, newCash);
        }
        return rawNewCashMap;
    }

    public static ArrayList<Cash> makeInCashMap(ElasticsearchClient esClient, Map<String, Cash> rawInCashMap, Map<String, Cash> outCashMap) throws Exception {
        ArrayList<String> inIdList = new ArrayList<>(rawInCashMap.keySet());
        Map<String, Cash> esInCashMap = getInCashListFromEs(esClient, inIdList);
        return mergeInCash(esInCashMap, rawInCashMap, outCashMap);
    }

    private static Map<String, Cash> getInCashListFromEs(ElasticsearchClient esClient, List<String> inIdList) throws Exception {
        if (inIdList == null || inIdList.size() == 0) return null;
        EsTools.MgetResult<Cash> result = EsTools.getMultiByIdList(esClient, CASH, inIdList, Cash.class);
        List<Cash> cashList = result.getResultList();
        List<String> missList = result.getMissList();
        Map<String, Cash> cashMap = new HashMap<>();
        for (Cash cash : cashList) cashMap.put(cash.getCashId(), cash);
        for (String cashId : missList) {
            cashMap.put(cashId, null);
        }
        return cashMap;
    }

    private static ArrayList<Cash> mergeInCash(Map<String, Cash> esCashMap, Map<String, Cash> rawCashMap, Map<String, Cash> outCashMap) {
        ArrayList<Cash> inCashList = new ArrayList<>();
        for (String id : rawCashMap.keySet()) {
            Cash cash;
            if (esCashMap.get(id) == null) {
                cash = outCashMap.get(id);
                if (cash == null) {
                    System.out.println("Cash " + id + " missed. Check if FCH-Parser is running.");
                    continue;
                }
                cash.setUnlockScript(rawCashMap.get(id).getUnlockScript());
                cash.setSequence(rawCashMap.get(id).getSequence());
                cash.setSigHash(rawCashMap.get(id).getSigHash());
                inCashList.add(cash);
            } else {
                cash = esCashMap.get(id);
                if (cash == null) {
                    System.out.println("Cash " + id + " missed. Check if FCH-Parser is running.");
                    continue;
                }
                cash.setUnlockScript(rawCashMap.get(id).getUnlockScript());
                cash.setSequence(rawCashMap.get(id).getSequence());
                cash.setSigHash(rawCashMap.get(id).getSigHash());
                inCashList.add(cash);
            }
        }
        return inCashList;
    }

    @Test
    public void parseOpTest() {

        String data2 = "6a026869";
        ;
        String data76 = "6a4cc5464549507c357c317c4920616d20777869645f696730786f696a72743477323532407765636861742d2d2d2d46504c3434594a52775064643269707a6946767171367932747734566e56767041762d2d2d2d494c7066324672574339634253716b726e463839544473783052785565757161566145626c6f31537a575343513578316a47596a41716b77536871517839386b6b574d724572543542756f524c546d45665767667245733d7c4920636f6e6669726d656420746869732073746174656d656e74";
        String data77 = "6a4c4d7b22706179466f72223a2262396430653061306537666337363234633665366137613164663833666534653234396333323436333330326336323234633833336534346334643765386339227d";
        String data440 = "6a4db8017b2275726c223a22687474703a2f2f6c6f63616c686f73743a383038302f415049502f746f6f6c732f766572696679222c2274696d65223a313637373637333832313236372c226e6f6e6365223a3839322c22666364736c223a7b226f74686572223a7b2261646472657373223a2246456b34314b716a61723435664c4472697a74554454556b646b69376d6d636a574b222c226d657373616765223a227b5c2275726c5c223a5c2268747470733a2f2f6369642e636173682f415049502f61706970302f76312f7369676e496e5c222c5c227075624b65795c223a5c223033306265316437653633336665623233333861373461383630653736643839336261633532356633356135383133636237623231653237626131626338333132615c222c5c226e6f6e63655c223a3132332c5c2274696d655c223a313637373537313534313839357d222c227369676e6174757265223a22494c65326a4f675765465272594233586f646e30334334516535417974396f69786a67483379574237496a45576452626f4b4f51414545423332567361747875574c4b674161535a616f657964457471493743696a65455c7530303364227d7d7d";

        try {
            System.out.println("<76:" + parseOpReturn(HexFormat.of().parseHex(data2)));
            System.out.println("=76:" + parseOpReturn(HexFormat.of().parseHex(data76)));
            System.out.println("=77:" + parseOpReturn(HexFormat.of().parseHex(data77)));
            System.out.println(">77:" + parseOpReturn(HexFormat.of().parseHex(data440)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println(new String(HexFormat.of().parseHex("7b7d"), StandardCharsets.UTF_8));
        byte[] len1 = HexFormat.of().parseHex("5801");
        String str = "{\"payFor\":\"b9d0e0a0e7fc7624c6e6a7a1df83fe4e249c32463302c6224c833e44c4d7e8c9\"}";
        System.out.println(BytesTools.bytes2ToIntBE(len1));
        System.out.println(BytesTools.bytes2ToIntLE(len1));
        System.out.println("4c=" + (int) 0x4c);
        System.out.println("ct=" + (int) 0xc5);

        System.out.println(str);
        System.out.println(str.length());
        System.out.println(HexFormat.of().formatHex(str.getBytes()));
    }
}
