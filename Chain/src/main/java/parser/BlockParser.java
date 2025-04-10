package parser;
import crypto.Hash;
import crypto.KeyTools;
import fch.fchData.*;
import utils.BytesUtils;
import utils.FchUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;

import static fch.RawTxParser.parseOpReturn;


public class BlockParser {
	public ReadyBlock parseBlock(byte[] blockBytes, BlockMark blockMark) throws IOException {


		Block block = new Block();
		block.setId(blockMark.getId());
		block.setPreId(blockMark.getPreBlockId());
		block.setHeight(blockMark.getHeight());
		block.setSize(blockMark.getSize());

		ByteArrayInputStream blockInputStream = new ByteArrayInputStream(blockBytes);

		byte[] blockHeadBytes = new byte[80];
		blockInputStream.read(blockHeadBytes);

		byte[] blockBodyBytes = new byte[(int) (block.getSize() - 80)];
		blockInputStream.read(blockBodyBytes);

		parseBlockHead(blockHeadBytes, block);

		ReadyBlock readyBlock = parseBlockBody(blockBodyBytes, block);

		readyBlock.setBlockMark(blockMark);

		return readyBlock;
	}

	private void parseBlockHead(byte[] blockHeadBytes, Block block1) {

		int offset = 0;
		// Read 4 bytes of the block version
		// 读取4字节版本号
		byte[] b4Ver = Arrays.copyOfRange(blockHeadBytes, offset, offset + 4);
		offset += 4;
		block1.setVersion(BytesUtils.bytesToHexStringLE(b4Ver));

		// Read 32 bytes of the father block hash
		// 读取32字节前区块哈希
		byte[] b32PreId = Arrays.copyOfRange(blockHeadBytes, offset, offset + 32);
		offset += 32;
		String preId = BytesUtils.bytesToHexStringLE(b32PreId);
		block1.setPreId(preId);

		// Read 32 bytes of the merkle root
		// 读取32字节默克尔根
		byte[] b32MklR = Arrays.copyOfRange(blockHeadBytes, offset, offset + 32);
		offset += 32;
		block1.setMerkleRoot(BytesUtils.bytesToHexStringLE(b32MklR));

		// Read 4 bytes of the time stamp.
		// 读取4字节时间戳：
		byte[] b4Time = Arrays.copyOfRange(blockHeadBytes, offset, offset + 4);
		offset += 4;
		block1.setTime(BytesUtils.bytes4ToLongLE(b4Time));

		// Read 4 bytes of difficulty.
		// 读取4字节挖矿难度：
		byte[] b4Bits = Arrays.copyOfRange(blockHeadBytes, offset, offset + 4);
		offset += 4;
		block1.setBits(BytesUtils.bytes4ToLongLE(b4Bits));

		// Read 4 bytes of nonce
		// 读取4字节挖矿随机数：
		byte[] b4Nonce = Arrays.copyOfRange(blockHeadBytes, offset, offset + 4);
		block1.setNonce(BytesUtils.bytes4ToLongLE(b4Nonce));

	}

	private ReadyBlock parseBlockBody(byte[] blockBodyBytes, Block block1) throws IOException {

		ReadyBlock readyBlock = new ReadyBlock();
		ByteArrayInputStream blockInputStream = new ByteArrayInputStream(blockBodyBytes);

		long txCount = utils.FchUtils.parseVarint(blockInputStream).number;
		block1.setTxCount((int) txCount);

		ArrayList<Tx> txList = new ArrayList<>();
		ArrayList<TxHas> txHasList = new ArrayList<>();
		ArrayList<Cash> inList = new ArrayList<>();
		ArrayList<OpReturn> opReturnList = new ArrayList<>();

		TxResult txResult;
		txResult = parseCoinbase(blockInputStream, block1);

		txList.add(txResult.tx);
		ArrayList<Cash> outList = new ArrayList<>(txResult.outList);

		for (int i = 1; i < txCount; i++) {
			txResult = parseTx(blockInputStream, block1, i);

			txList.add(txResult.tx);
			inList.addAll(txResult.inList);
			outList.addAll(txResult.outList);
			if (txResult.opReturn != null)
				opReturnList.add(txResult.opReturn);
		}

		readyBlock.setBlock(block1);
		readyBlock.setTxList(txList);
		readyBlock.setTxHasList(txHasList);
		readyBlock.setInList(inList);
		readyBlock.setOutList(outList);
		readyBlock.setOpReturnList(opReturnList);

		return readyBlock;
	}

	private TxResult parseCoinbase(ByteArrayInputStream blockInputStream, Block block) throws IOException {

		ArrayList<byte[]> bytesList = new ArrayList<>();

		Tx tx = new Tx();

		byte[] b4Version = new byte[4];
		blockInputStream.read(b4Version);
		bytesList.add(b4Version);

		tx.setTxIndex(0);
		tx.setVersion(BytesUtils.bytesToIntLE(b4Version));
		tx.setBlockTime(block.getTime());
		tx.setBlockId(block.getId());
		tx.setHeight(block.getHeight());

		byte[] b37SkipInput = new byte[1 + 32 + 4];// Skip input count(1) + preHash(32 '00') + input index (4 'ff').
		blockInputStream.read(b37SkipInput);
		bytesList.add(b37SkipInput);

		byte[] b1ScriptLength = new byte[1]; // Input script length.
		blockInputStream.read(b1ScriptLength);
		bytesList.add(b1ScriptLength);

		int scriptLength = b1ScriptLength[0];
		byte[] bvScript = new byte[scriptLength]; // coinbase (input script).
		blockInputStream.read(bvScript);
		bytesList.add(bvScript);

		byte[] b4Sequence = new byte[4];
		blockInputStream.read(b4Sequence); // sequence of ffffffff.
		bytesList.add(b4Sequence);

		tx.setCoinbase(new String(bvScript));
		tx.setInCount(0);

		// Parse Outputs./解析输出。
		ParseTxOutResult parseTxOutResult = parseOut(blockInputStream, tx);
		bytesList.add(parseTxOutResult.rawBytes);
		tx = parseTxOutResult.tx;
		ArrayList<Cash> rawOutList = parseTxOutResult.rawOutList;

		// Read lock time.
		// 读取输出时间锁
		byte[] b4LockTime = new byte[4];
		blockInputStream.read(b4LockTime);
		bytesList.add(b4LockTime);
		tx.setLockTime(BytesUtils.bytes4ToLongLE(b4LockTime));

		byte[] rawTxBytes = BytesUtils.bytesMerger(bytesList);
		tx.setId(BytesUtils.bytesToHexStringLE(Hash.sha256x2(rawTxBytes)));
		ArrayList<Cash> outList = makeOutList(tx.getId(), rawOutList);

		tx.setRawTx(HexFormat.of().formatHex(rawTxBytes));
		TxResult txResult = new TxResult();
		txResult.tx = tx;
		txResult.outList = outList;
		return txResult;
	}

	private static class TxResult {
		Tx tx;
		ArrayList<Cash> inList;
		ArrayList<Cash> outList;
		OpReturn opReturn;
	}

	private ParseTxOutResult parseOut(ByteArrayInputStream blockInputStream, Tx tx1) throws IOException {
		ArrayList<byte[]> rawBytesList = new ArrayList<>(); // For returning raw bytes.
		ArrayList<Cash> rawOutList = new ArrayList<>();// For returning outputs without

		String opReturnStr = "";

		// Parse output count.
		// 解析输出数量。
		utils.FchUtils.VariantResult varintParseResult;
		varintParseResult = utils.FchUtils.parseVarint(blockInputStream);
		long outputCount = varintParseResult.number;
		byte[] b0 = varintParseResult.rawBytes;
		rawBytesList.add(b0);

		tx1.setOutCount((int) outputCount);

		// Starting operators in output script.
		// 输出脚本中的起始操作码。
		final byte OP_DUP = (byte) 0x76;
		final byte OP_HASH160 = (byte) 0xa9;
		final byte OP_RETURN = (byte) 0x6a;
		byte b1Script; // For get the first byte of output script./接收脚本中的第一个字节。

		for (int j = 0; j < outputCount; j++) {
			// Start one output.
			// 开始解析一个输出。
			Cash out = new Cash();
			out.setBirthIndex(j);
			out.setBirthTxIndex(tx1.getTxIndex());
			out.setBirthHeight(tx1.getHeight());
			out.setBirthBlockId(tx1.getBlockId());
			out.setBirthTime(tx1.getBlockTime());
			out.setLastTime(tx1.getBlockTime());
			out.setLastHeight(tx1.getHeight());

			// Read the value of this output in satoshi.
			// 读取该输出的金额，以聪为单位。
			byte[] b8Value = new byte[8];
			blockInputStream.read(b8Value);
			rawBytesList.add(b8Value);
			out.setValue(BytesUtils.bytes8ToLong(b8Value,true));

			// Parse the length of script.
			// 解析脚本长度。
			varintParseResult = utils.FchUtils.parseVarint(blockInputStream);
			long scriptSize = varintParseResult.number;
			byte[] b2 = varintParseResult.rawBytes;
			rawBytesList.add(b2);

			byte[] bScript = new byte[(int) scriptSize];
			blockInputStream.read(bScript);
			rawBytesList.add(bScript);

			b1Script = bScript[0];

			switch (b1Script) {
				case OP_DUP -> {
					out.setType("P2PKH");
					out.setLockScript(BytesUtils.bytesToHexStringBE(bScript));
					byte[] hash160Bytes = Arrays.copyOfRange(bScript, 3, 23);
					out.setOwner(KeyTools.hash160ToFchAddr(hash160Bytes));
				}
				case OP_RETURN -> {
					out.setType("OP_RETURN");
					opReturnStr = parseOpReturn(bScript);//new String(Arrays.copyOfRange(bScript, 2, bScript.length));
					out.setOwner("OpReturn");
					out.setValid(false);
					if (tx1.getTxIndex() != 0) {
						if (opReturnStr!=null && opReturnStr.length() <= 30) {
							tx1.setOpReBrief(opReturnStr);
						} else {
							if (opReturnStr!=null)tx1.setOpReBrief(opReturnStr.substring(0, 29));
						}
					}
				}
				case OP_HASH160 -> {
					out.setType("P2SH");
					out.setLockScript(BytesUtils.bytesToHexStringBE(bScript));
					byte[] hash160Bytes1 = Arrays.copyOfRange(bScript, 2, 22);
					out.setOwner(KeyTools.hash160ToMultiAddr(hash160Bytes1));
				}
				default -> {
					out.setType("Unknown");
					out.setLockScript(BytesUtils.bytesToHexStringBE(bScript));
					out.setOwner("Unknown");
				}
			}

			// Add block and tx information to output./给输出添加区块和交易信息。
			// Add information where it from/添加来源信息
			out.setValid(true);
			out.setBirthTime(tx1.getBlockTime());
			out.setBirthTxIndex(tx1.getTxIndex());
			out.setLastTime(tx1.getBlockTime());
			out.setLastHeight(tx1.getHeight());

			// Add this output to List.
			// 添加输出到列表。
			rawOutList.add(out);
		}
		byte[] rawBytes = BytesUtils.bytesMerger(rawBytesList);

		ParseTxOutResult parseTxOutResult = new ParseTxOutResult();
		parseTxOutResult.rawBytes = rawBytes;
		parseTxOutResult.rawOutList = rawOutList;
		if (tx1.getTxIndex() != 0)
			parseTxOutResult.opReturnStr = opReturnStr;
		parseTxOutResult.tx = tx1;

		return parseTxOutResult;
	}

	private static class ParseTxOutResult {
		Tx tx;
		ArrayList<Cash> rawOutList;
		byte[] rawBytes;
		String opReturnStr;
	}

	private ArrayList<Cash> makeOutList(String txId, ArrayList<Cash> rawOutList1) {
		for (int j = 0; j < rawOutList1.size(); j++) {
			rawOutList1.get(j).setBirthTxId(txId);
			rawOutList1.get(j).setId(Cash.makeCashId(txId, j));
		}
		return rawOutList1;
	}

	private TxResult parseTx(ByteArrayInputStream blockInputStream, Block block, int i) throws IOException {

		ArrayList<byte[]> bytesList = new ArrayList<>();
		Tx tx = new Tx();

		// Read tx version/读取交易的版本
		byte[] b4Version = new byte[4];
		blockInputStream.read(b4Version);
		bytesList.add(b4Version);

		tx.setVersion(BytesUtils.bytesToIntLE(b4Version));
		tx.setTxIndex(i);
		tx.setBlockTime(block.getTime());
		tx.setBlockId(block.getId());
		tx.setHeight(block.getHeight());

		// Read inputs /读输入
		// ParseTxOutResult parseTxOutResult
		ParseTxInResult parseTxInResult = parseInput(blockInputStream, tx);

		bytesList.add(parseTxInResult.rawBytes);
		tx = parseTxInResult.tx;
		ArrayList<Cash> rawInList = parseTxInResult.rawInList;

		// Read outputs /读输出
		// Parse Outputs./解析输出。
		ParseTxOutResult parseTxOutResult = parseOut(blockInputStream, tx);
		bytesList.add(parseTxOutResult.rawBytes);
		tx = parseTxOutResult.tx;
		ArrayList<Cash> rawOutList = parseTxOutResult.rawOutList;

		// Read lock time.
		// 读取输出时间锁
		byte[] b4LockTime = new byte[4];
		blockInputStream.read(b4LockTime);
		bytesList.add(b4LockTime);
		tx.setLockTime((long) BytesUtils.bytesToIntLE(b4LockTime));

		byte[] rawTxBytes = BytesUtils.bytesMerger(bytesList);
		tx.setId(BytesUtils.bytesToHexStringLE(Hash.sha256x2(rawTxBytes)));
		tx.setRawTx(HexFormat.of().formatHex(rawTxBytes));

		ArrayList<Cash> inList = makeInList(tx.getId(), rawInList);
		ArrayList<Cash> outList = makeOutList(tx.getId(), rawOutList);

		OpReturn opReturn = new OpReturn();
		if(parseTxOutResult.opReturnStr!=null && !"".equals(parseTxOutResult.opReturnStr)) {
			opReturn.setOpReturn(parseTxOutResult.opReturnStr);
			opReturn.setId(tx.getId());
			opReturn.setTxIndex(tx.getTxIndex());
			opReturn.setHeight(tx.getHeight());
		}
		TxResult txResult = new TxResult();
		txResult.tx = tx;
		if(parseTxOutResult.opReturnStr!=null && !"".equals(parseTxOutResult.opReturnStr))
			txResult.opReturn = opReturn;
		txResult.inList = inList;
		txResult.outList = outList;

		return txResult;
	}

	private ParseTxInResult parseInput(ByteArrayInputStream blockInputStream, Tx tx1) throws IOException {
		ArrayList<byte[]> rawBytesList = new ArrayList<>(); // For returning raw bytes
		ArrayList<Cash> rawInList = new ArrayList<>();// For returning inputs without

		// Get input count./获得输入数量
		FchUtils.VariantResult varintParseResult;
		varintParseResult = utils.FchUtils.parseVarint(blockInputStream);
		long inputCount = varintParseResult.number;
		tx1.setInCount((int) inputCount);

		byte[] bvVarint = varintParseResult.rawBytes;
		rawBytesList.add(bvVarint);

		// Read inputs /读输入
		for (int j = 0; j < inputCount; j++) {
			Cash input = new Cash();

			input.setSpendTime(tx1.getBlockTime());
			input.setSpendTxIndex(tx1.getTxIndex());
			input.setBirthBlockId(tx1.getBlockId());
			input.setSpendHeight(tx1.getHeight());
			input.setSpendBlockId(tx1.getBlockId());
			input.setSpendIndex(j);
			input.setValid(false);
			input.setLastTime(tx1.getBlockTime());
			input.setLastHeight(tx1.getHeight());

			// Read preTXHash and preOutIndex./读取前交易哈希和输出索引。
			byte[] b36PreTxIdAndIndex = new byte[32 + 4];
			blockInputStream.read(b36PreTxIdAndIndex);
			rawBytesList.add(b36PreTxIdAndIndex);

			input.setId(Cash.makeCashId(b36PreTxIdAndIndex));

			// Read the length of script./读脚本长度。
			varintParseResult = utils.FchUtils.parseVarint(blockInputStream);
			long scriptLength = varintParseResult.number;
			byte[] bvVarint1 = varintParseResult.rawBytes;
			rawBytesList.add(bvVarint1);

			// Get script./获取脚本。
			byte[] bvScript = new byte[(int) scriptLength];
			blockInputStream.read(bvScript);
			rawBytesList.add(bvScript);
			input.setUnlockScript(BytesUtils.bytesToHexStringBE(bvScript));

			// Parse sigHash.
			// 解析sigHash。
			int sigLen = Byte.toUnsignedInt(bvScript[0]);// Length of signature;
			// Skip signature/跳过签名。
			byte sigHash;
			if(sigLen!=0){                                // No multiSig
				sigHash = bvScript[sigLen];                // 签名类型标志
				setInputSigHash(input,sigHash);
			}

			// Get sequence./获取sequence。
			byte[] b4Sequence = new byte[4];
			blockInputStream.read(b4Sequence);
			rawBytesList.add(b4Sequence);
			input.setSequence(BytesUtils.bytesToHexStringBE(b4Sequence));

			rawInList.add(input);
		}
		byte[] rawBytes = BytesUtils.bytesMerger(rawBytesList);

		ParseTxInResult parseTxInResult = new ParseTxInResult();

		parseTxInResult.rawBytes = rawBytes;
		parseTxInResult.rawInList = rawInList;
		parseTxInResult.tx = tx1;

		return parseTxInResult;
	}

	private void setInputSigHash(Cash input, byte sigHash) {
		switch (sigHash) {
			case 0x41 -> input.setSigHash("ALL");
			case 0x42 -> input.setSigHash("NONE");
			case 0x43 -> input.setSigHash("SINGLE");
			case (byte) 0xc1 -> input.setSigHash("ALLIANYONECANPAY");
			case (byte) 0xc2 -> input.setSigHash("NONEIANYONECANPAY");
			case (byte) 0xc3 -> input.setSigHash("SINGLEIANYONECANPAY");
			default -> input.setSigHash(null);
		}
	}

	private static class ParseTxInResult {
		Tx tx;
		ArrayList<Cash> rawInList;
		byte[] rawBytes;
	}

	private ArrayList<Cash> makeInList(String txId, ArrayList<Cash> rawInList) {

		ArrayList<Cash> inList = rawInList;
		for (Cash in : inList) {
			in.setSpendTxId(txId);
		}
		return inList;
	}
}
