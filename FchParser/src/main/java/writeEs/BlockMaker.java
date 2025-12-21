package writeEs;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import constants.IndicesNames;
import core.crypto.Hash;
import core.crypto.KeyTools;
import data.fchData.Cash;
import data.fchData.*;
import core.fch.Weight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import parser.ReadyBlock;
import utils.EsUtils;
import utils.FchUtils;
import utils.Hex;
import utils.JsonUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static constants.IndicesNames.*;
import static startFCH.StartFCH.COINBASE;

public class BlockMaker {
	private static final Logger log = LoggerFactory.getLogger(BlockMaker.class);


	public ReadyBlock makeReadyBlock(ElasticsearchClient esClient, ReadyBlock rawBlock) throws Exception {

		// Initialize p2shList
		rawBlock.setP2SHMap(new LinkedHashMap<>());

		// Use new merged method that handles inputs, transactions, and block in one pass
		ReadyBlock processedBlock = makeBlockData(esClient, rawBlock);

		// Process addresses (still needs to be separate as it depends on completed TX data)
		return makeAddress(esClient, processedBlock);
	}

	/**
	 * Merged method that efficiently processes inputs, transactions, and block data in fewer passes.
	 * This fixes the bug where same-block cashes (issued and spent in same block) were removed from outMap,
	 * causing incorrect TX calculations. It also improves performance by reducing redundant iterations.
	 *
	 * @param esClient Elasticsearch client
	 * @param rawBlock The raw block to process
	 * @return Processed ReadyBlock with all TX and block data calculated
	 * @throws Exception if processing fails
	 */
	private ReadyBlock makeBlockData(ElasticsearchClient esClient, ReadyBlock rawBlock) throws Exception {

		LinkedHashMap<String, Cash> inMap = rawBlock.getInMap();
		LinkedHashMap<String, Cash> outMap = rawBlock.getOutMap();
		LinkedHashMap<String, Tx> txMap = rawBlock.getTxLinkedMap();
		LinkedHashMap<String, OpReturn> opReturnMap = rawBlock.getOpReturnMap();
		Block block = rawBlock.getBlock();

		// Initialize block statistics
		block.setTxList(new ArrayList<>());
		long blockInValueT = 0L;
		long blockOutValueT = 0L;
		long blockCdd = 0L;

		// Step 1: Fetch and prepare input cashes if they exist
		LinkedHashMap<String, Cash> inMadeMap = new LinkedHashMap<>();
		LinkedHashMap<String, Cash> outWriteMap = new LinkedHashMap<>();

		if (inMap != null && !inMap.isEmpty()) {
			List<String> inIdList = new ArrayList<>(inMap.keySet());

			// Fetch existing cashes from Elasticsearch
			EsUtils.MgetResult<Cash> inMgetResult = EsUtils.getMultiByIdList(esClient, CASH, inIdList, Cash.class);
			ArrayList<Cash> inOldList = (ArrayList<Cash>) inMgetResult.getResultList();
			List<String> inNewIdList = inMgetResult.getMissList();

			// Process cashes that already exist in ES
			for (Cash out : inOldList) {
				Cash in = inMap.get(out.getId());
				if (out.getIssuer() != null)
					in.setIssuer(out.getIssuer());
				setInFromOut(out, in);
				inMadeMap.put(in.getId(), in);
			}

			// Process cashes that are new in this block (same-block spend scenario)
			for (String id : inNewIdList) {
				Cash in = inMap.get(id);
				Cash out = outMap.get(id);

				if (out == null) {
					System.out.println("Input missed:" + id + ". " +
							"\nTry to rollback. If it does not work, try to rollback 20000 blocks or more. " +
							"\nIf still does not work, you have to reparse all blocks. Sorry. " +
							"\n The cause is generally a interruption when rolling back.");
					RuntimeException runtimeException = new RuntimeException();
					runtimeException.printStackTrace();
					throw runtimeException;
				}

				setInFromOut(out, in);
				inMadeMap.put(in.getId(), in);
				// DON'T remove from outMap - this was the bug!
				// Instead, track which outputs should NOT be written to DB
			}

			// Prepare outWriteMap - only outputs that are NOT spent in same block
			for (String outId : outMap.keySet()) {
				if (!inNewIdList.contains(outId)) {
					outWriteMap.put(outId, outMap.get(outId));
				}
			}

			rawBlock.setInMap(inMadeMap);
			rawBlock.setOutWriteMap(outWriteMap);
		} else {
			// No inputs - all outputs should be written
			outWriteMap = new LinkedHashMap<>(outMap);
			rawBlock.setOutWriteMap(outWriteMap);
			inMadeMap = new LinkedHashMap<>();
		}

		// Step 2: Process inputs and outputs together, calculating TX and block totals in one pass

		// 2a. Process inputs - build spentCashes and calculate input totals for TX and block
		for (Cash in : inMadeMap.values()) {
			long value = in.getValue();
			long cdd = in.getCdd() == null ? 0 : in.getCdd();

			Tx tx = txMap.get(in.getSpendTxId());

			// Update TX input totals
			Long txInValueT = tx.getInValueT();
			tx.setInValueT(txInValueT == null ? value : txInValueT + value);

			Long txCdd = tx.getCdd();
			tx.setCdd(txCdd == null ? cdd : txCdd + cdd);

			// Update block input totals
			blockInValueT += value;
			blockCdd += cdd;

			// Build spentCashes list
			CashMask inMark = new CashMask();
			inMark.setId(in.getId());
			inMark.setOwner(in.getOwner());
			inMark.setValue(in.getValue());
			inMark.setCdd(in.getCdd());

			ArrayList<CashMask> spentCashes = tx.getSpentCashes();
			if (spentCashes == null) {
				spentCashes = new ArrayList<>();
				tx.setSpentCashes(spentCashes);
			}
			spentCashes.add(inMark);
		}

		// 2b. Process outputs - build issuedCashes, calculate output totals, and set issuers
		for (Cash out : outMap.values()) {
			long value = out.getValue();

			Tx tx = txMap.get(out.getBirthTxId());

			// Update TX output totals
			Long txOutValueT = tx.getOutValueT();
			tx.setOutValueT(txOutValueT == null ? value : txOutValueT + value);

			// Update block output totals
			blockOutValueT += value;

			// Build issuedCashes list
			CashMask outMark = new CashMask();
			outMark.setId(out.getId());
			outMark.setOwner(out.getOwner());
			outMark.setValue(out.getValue());

			ArrayList<CashMask> issuedCashes = tx.getIssuedCashes();
			if (issuedCashes == null) {
				issuedCashes = new ArrayList<>();
				tx.setIssuedCashes(issuedCashes);
			}
			issuedCashes.add(outMark);

			// Set issuer for this output
			ArrayList<CashMask> spentCashes = tx.getSpentCashes();
			if (spentCashes == null || spentCashes.isEmpty()) {
				out.setIssuer(COINBASE);
			} else {
				out.setIssuer(spentCashes.get(0).getOwner());
			}
		}

		// 2c. Set issuer for inputs that don't have it yet (same-block spends)
		for (Cash in : inMadeMap.values()) {
			if (in.getIssuer() == null) {
				Tx tx = txMap.get(in.getBirthTxId());
				ArrayList<CashMask> inMarks = tx.getSpentCashes();
				if (inMarks != null && !inMarks.isEmpty()) {
					in.setIssuer(inMarks.get(0).getOwner());
				}
			}
		}

		// 2d. Calculate TX fees and build block TX list
		long blockFee = 0L;
		for (Tx tx : txMap.values()) {
			// Calculate fee
			if (tx.getInCount() != 0) {
				Long inValueT = tx.getInValueT();
				Long outValueT = tx.getOutValueT();
				if (inValueT == null) inValueT = 0L;
				if (outValueT == null) outValueT = 0L;
				long fee = inValueT - outValueT;
				tx.setFee(fee);
				blockFee += fee;
			}

			// Build TX mask for block
			TxMask txMask = new TxMask();
			txMask.setId(tx.getId());
			txMask.setOutValue(tx.getOutValueT());

			if (tx.getInCount() != 0) {
				txMask.setFee(tx.getFee());
				txMask.setCdd(tx.getCdd());
			}

			block.getTxList().add(txMask);
		}

		// 2e. Process OpReturns
		if (opReturnMap != null && !opReturnMap.isEmpty()) {
			List<String> keysToRemove = new ArrayList<>();

			for (OpReturn opReturn : opReturnMap.values()) {
				if ("".equals(opReturn.getOpReturn())) {
					keysToRemove.add(opReturn.getId());
					continue;
				}

				String txId = opReturn.getId();
				Tx tx = txMap.get(txId);

				opReturn.setCdd(tx.getCdd());
				opReturn.setTime(tx.getBlockTime());

				String signer = tx.getSpentCashes().get(0).getOwner();
				opReturn.setSigner(signer);

				Long paid = null;
				for (CashMask txoB : tx.getIssuedCashes()) {
					String receiver = txoB.getOwner();
					if (!receiver.equals(signer) && !receiver.equalsIgnoreCase("Unknown") && !receiver.equalsIgnoreCase("OpReturn")) {
						if (opReturn.getRecipient() == null) {
							opReturn.setRecipient(receiver);
							if (paid == null) paid = 0L;
							paid += txoB.getValue();
						} else if (opReturn.getRecipient().equals(receiver)) {
							paid += txoB.getValue();
						}
					}
				}
				opReturn.setPaid(paid);

				if (opReturn.getRecipient() == null)
					opReturn.setRecipient(NOBODY);

				// For CLTV - process P2SH announcements
				checkAnnouncedLockTimeP2SH(outMap, txMap, opReturn, rawBlock,esClient);
			}

			for (String key : keysToRemove) {
				opReturnMap.remove(key);
			}
		}

		// Set final block statistics
		block.setInValueT(blockInValueT);
		block.setOutValueT(blockOutValueT);
		block.setFee(blockFee);
		block.setCdd(blockCdd);

		rawBlock.setBlock(block);
		rawBlock.setTxLinkedMap(txMap);
		rawBlock.setOpReturnMap(opReturnMap);

		return rawBlock;
	}

	private void setInFromOut(Cash out, Cash in) {
		in.setOwner(out.getOwner());
		in.setBirthIndex(out.getBirthIndex());
		in.setType(out.getType());
		in.setValue(out.getValue());
		in.setLockScript(out.getLockScript());
		in.setBirthTxId(out.getBirthTxId());
		in.setBirthTxIndex(out.getBirthTxIndex());
		in.setBirthTime(out.getBirthTime());
		in.setBirthHeight(out.getBirthHeight());
		in.setCdd(FchUtils.cdd(in.getValue(), in.getBirthTime(), in.getSpendTime()));
	}


	private static void checkAnnouncedLockTimeP2SH(LinkedHashMap<String, Cash> outMap, Map<String, Tx> txMap, OpReturn opReturn, ReadyBlock blockForMaking, ElasticsearchClient esClient) {
		Tx tx;
		try{
			Map<String, String> p2shMap = new HashMap<>();;

			String opReturnText = opReturn.getOpReturn();
			if(opReturnText==null || opReturnText.isEmpty())
				return;
			if(opReturnText.trim().startsWith("[{")) {
				List<P2SH> p2SHList = JsonUtils.listFromJson(opReturnText, P2SH.class);
				if(!p2SHList.isEmpty()) {
					for(P2SH p2SH:p2SHList){
						String redeemScriptHex = p2SH.getRedeemScript();
						if(redeemScriptHex==null){
							redeemScriptHex= p2SH.getRedeemScriptHex();
							if(redeemScriptHex==null)
								return;
						}
						byte[] calculatedHash = Hash.sha256hash160(Hex.fromHex(redeemScriptHex));
						p2shMap.put(Hex.toHex(calculatedHash),redeemScriptHex);
					}
				}
			}else if(opReturnText.trim().startsWith("{")){
				// Parse P2SH map format: {hash160Hex: redeemScriptHex}
				p2shMap = parseP2SHMapFromOpReturn(opReturnText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			}else if(opReturnText.trim().startsWith("[")){
				String[] redeemScripts = JsonUtils.fromJson(opReturnText,String[].class);
				for(String scriptHex:redeemScripts) {
					try {
						byte[] calculatedHash = Hash.sha256hash160(Hex.fromHex(scriptHex));
						p2shMap.put(Hex.toHex(calculatedHash), scriptHex);
					}catch (Exception ignore){}
				}
			}

			if(p2shMap != null && !p2shMap.isEmpty()){

				tx = txMap.get(opReturn.getId());

				for (CashMask cashMask : tx.getIssuedCashes()) {
					Cash cash = outMap.get(cashMask.getId());
					if (cash.getType().equals("P2SH")) {
						String lockScript = cash.getLockScript();
						if (lockScript.length() < 44)
							continue;

						// Extract hash160 from P2SH lockScript using P2SH method
						byte[] lockScriptBytes = Hex.fromHex(lockScript);
						byte[] hash160BytesFromLockScript = data.fchData.P2SH.extractHash160FromLockScript(lockScriptBytes);

						if (hash160BytesFromLockScript != null) {
							String hash160HexFromLockScript = Hex.toHex(hash160BytesFromLockScript);

							// Look up redeemScript in map
							String redeemScriptHex = p2shMap.get(hash160HexFromLockScript);
							if (redeemScriptHex != null && !redeemScriptHex.isEmpty()) {
								data.fchData.P2SH p2SH = parseP2shForIssuedCash(cash, tx,redeemScriptHex,blockForMaking,esClient);
								if(p2SH!=null)cashMask.setOwner(cash.getOwner());
							}
						}
					}
				}
			}
		}catch (Exception ignore){}
	}


	/**
	 * Parse P2SH map from OP_RETURN data bytes (NEW format: hash160->redeemScript map)
	 *
	 * @param opReturnData The OP_RETURN data bytes
	 * @return Map of hash160Hex to redeemScriptHex if valid, null otherwise
	 */
	@Nullable
	public static Map<String, String> parseP2SHMapFromOpReturn(byte[] opReturnData) {
		try {
			// Try to parse as text
			String opReturnText = new String(opReturnData, StandardCharsets.UTF_8);

			// Check if it's a JSON object (map format)
			if (opReturnText.trim().startsWith("{")) {
				// Parse as map
				java.lang.reflect.Type type = new TypeToken<Map<String, String>>(){}.getType();
				Map<String, String> map = new Gson().fromJson(opReturnText, type);

				if (map == null || map.isEmpty()) {
					return null;
				}

				// Verify integrity: hash160(redeemScript) must match key
				// AND validate syntax of each redeemScript
				for (Map.Entry<String, String> entry : map.entrySet()) {
					String hash160Hex = entry.getKey();
					String redeemScriptHex = entry.getValue();

					if (!Hex.isHexString(hash160Hex) || !Hex.isHexString(redeemScriptHex)) {
						throw new IllegalArgumentException("Invalid hex format in P2SH map");
					}

					// Verify hash160 integrity
					byte[] redeemScript = Hex.fromHex(redeemScriptHex);
					byte[] calculatedHash = Hash.sha256hash160(redeemScript);
					String calculatedHashHex = Hex.toHex(calculatedHash);

					if (!calculatedHashHex.equals(hash160Hex)) {
						throw new IllegalArgumentException("Hash160 mismatch for entry: " + hash160Hex);
					}
				}

				log.debug("P2SH", "Successfully parsed and validated P2SH map with " + map.size() + " entries");
				return map;
			}
		} catch (Exception ignore) {}
		return null;
	}

	public static P2SH parseP2shForIssuedCash(Cash cash, Tx tx, String redeemScriptHex, ReadyBlock blockForMaking, ElasticsearchClient esClient) {
		if (redeemScriptHex == null || redeemScriptHex.isEmpty()) {
			return null;
		}

		try {
			// Use P2SH constructor to validate and parse the script
			P2SH p2sh = new P2SH(redeemScriptHex);

			p2sh.setBirthHeight(tx.getHeight());
			p2sh.setBirthTime(tx.getBlockTime());
			p2sh.setBirthTxId(tx.getId());

			// Only process if it's a CLTV-related type
			cash.setOwner(p2sh.getFid());
			cash.setLockTime(p2sh.getLockTime());
			switch (p2sh.getType()) {
				case CLTV -> {
					cash.setType(Cash.CashType.P2SH_CLTV.getValue());
				}
				case MULTISIG_CLTV -> {
					cash.setType(Cash.CashType.P2SH_MULTISIG_CLTV.getValue());
					updateMultisig(blockForMaking, esClient, p2sh);
				}
				case MULTISIG -> {
					cash.setType(Cash.CashType.P2SH_MULTISIG.getValue());
					updateMultisig(blockForMaking, esClient, p2sh);
				}
			}
			cash.setRedeemScript(redeemScriptHex);
			updateP2SH(blockForMaking,esClient,p2sh);
			// Plain multisig without CLTV - return false
			return p2sh;
		} catch (Exception e) {
			log.debug(e.getMessage(),e);
			return null;
		}
	}

	private static void updateMultisig(ReadyBlock blockForMaking, ElasticsearchClient esClient, data.fchData.P2SH p2sh) throws IOException {
		GetResponse<Multisig> resultGetMultisign = esClient.get(g->g.index(IndicesNames.MULTISIG).id(p2sh.getFid()), Multisig.class);
		if(!resultGetMultisign.found()) {
			Multisig multisig = new Multisig(p2sh);
			if(multisig.getId()!=null)
				blockForMaking.getMultisigMap().put(multisig.getId(),multisig);
		}
	}

	private static void updateP2SH(ReadyBlock blockForMaking, ElasticsearchClient esClient, data.fchData.P2SH p2sh) throws IOException {
		GetResponse<P2SH> resultGetP2SH = esClient.get(g->g.index(IndicesNames.P2SH).id(p2sh.getId()), P2SH.class);
		if(!resultGetP2SH.found()) {
			blockForMaking.getP2SHMap().put(p2sh.getId(),p2sh);
		}
	}


	private ReadyBlock makeAddress(ElasticsearchClient esClient, ReadyBlock readyBlock) throws Exception {

		List<String> addrStrList = getAddrStrList(readyBlock);

		ArrayList<Freer> addrList = readAddrListFromEs(esClient, addrStrList);

		LinkedHashMap<String, Freer> cidMap = new LinkedHashMap<>();

		for (Freer addr : addrList) {
			cidMap.put(addr.getId(), addr);
		}

		LinkedHashMap<String, Tx> txLinkedMap = readyBlock.getTxLinkedMap();
		LinkedHashMap<String, Cash> outMap = readyBlock.getOutMap();
		LinkedHashMap<String, Cash> inMap = readyBlock.getInMap();

		for (String id: txLinkedMap.keySet()) {
			Tx tx = txLinkedMap.get(id);
			if (tx.getSpentCashes() != null && !tx.getSpentCashes().isEmpty()) {
				for (CashMask inb : tx.getSpentCashes()) {
					String inAddr = inb.getOwner();
					long inValue = inb.getValue();
					long cdd = inb.getCdd();

					Freer addr = cidMap.get(inAddr);
					if(addr.getExpend()==null)addr.setExpend(inValue);
					else addr.setExpend(addr.getExpend() + inValue);
					if(addr.getBalance()==null)addr.setBalance(inValue);
					else addr.setBalance(addr.getBalance() - inValue);
					if(addr.getCdd()==null)addr.setCdd(cdd);
					else addr.setCdd(addr.getCdd() + cdd);
					if(addr.getWeight()==null)addr.setWeight((cdd* Weight.CDD_WEIGHT)/100);
					else addr.setWeight(addr.getWeight()+(cdd* Weight.CDD_WEIGHT)/100);
					addr.setLastHeight(tx.getHeight());
					if(addr.getCash()==null)addr.setCash(1L);
					else addr.setCash(addr.getCash() - 1);

					if (addr.getPubkey() == null) {
						for (Cash in : inMap.values()) {
							if (in.getOwner().equals(addr.getId()) && in.getType().equals("P2PKH")) {
								String pk = KeyTools.parsePkFromScript(in.getUnlockScript());
								addr.makeAddresses(pk);
								break;
							}
							if (in.getOwner().equals(addr.getId()) && in.getType().equals("P2SH")) {

								GetResponse<Multisig> resultGetMultisign = esClient.get(g->g.index(IndicesNames.MULTISIG).id(in.getOwner()), Multisig.class);
								if(resultGetMultisign.found()) continue;
								String hash160 = data.fchData.P2SH.hash160FromLockScript(in.getLockScript());
								if(hash160==null)
									continue;

								LinkedHashMap<String, data.fchData.P2SH> p2SHMap = readyBlock.getP2SHMap();
								if(p2SHMap.get(hash160)!=null)continue;

								String redeemScript = data.fchData.P2SH.redeemScriptFromUnlockScript(in.getUnlockScript(),hash160);

								// Create P2SH object and add to p2shList
								P2SH p2sh = new P2SH(redeemScript);
								if(p2sh.getFid() == null)continue;

								p2sh.setBirthHeight(in.getBirthHeight());
								p2sh.setBirthTime(in.getBirthTime());
								p2sh.setBirthTxId(in.getBirthTxId());


								p2SHMap.put(p2sh.getId(),p2sh);

								LinkedHashMap<String, Multisig> multisigMap = readyBlock.getMultisigMap();
								Multisig multisig = new Multisig(p2sh);
								multisigMap.put(multisig.getId(),multisig);

								break;
							}
						}
					}
				}
			}

			for (CashMask outB : tx.getIssuedCashes()) {
				String outAddr = outB.getOwner();
				long outValue = outB.getValue();
				Freer cid = cidMap.get(outAddr);
				if(cid.getIncome()==null)cid.setIncome(outValue);
				else cid.setIncome(cid.getIncome() + outValue);

				if(cid.getBalance()==null)cid.setBalance(outValue);
				else cid.setBalance(cid.getBalance() + outValue);

				cid.setLastHeight(tx.getHeight());

				if(cid.getCash()==null)cid.setCash(1L);
				else cid.setCash(cid.getCash() + 1);

				if (cid.getBirthHeight() == null)
					cid.setBirthHeight(tx.getHeight());

				if (cid.getGuide() == null) {
					if (tx.getSpentCashes() != null && !tx.getSpentCashes().isEmpty()) {
						cid.setGuide(tx.getSpentCashes().get(0).getOwner());
					} else
						cid.setGuide(COINBASE);
				}

				// Extract pubkey from P2PK outputs if pubkey is not yet set
				if (cid.getPubkey() == null) {
					Cash out = outMap.get(outB.getId());
					if (out != null && out.getType().equals("P2PK")) {
						// For P2PK, the pubkey is in the lockScript
						String lockScriptHex = out.getLockScript();
						if (lockScriptHex != null && !lockScriptHex.isEmpty()) {
							// Parse pubkey from P2PK lockScript
							String pubkeyHex = KeyTools.parsePkFromScript(lockScriptHex);
							if (pubkeyHex != null && !pubkeyHex.isEmpty()) {
								cid.makeAddresses(pubkeyHex);
							}
						}
					}
				}
			}
		}

		readyBlock.setAddrMap(cidMap);

		return readyBlock;
	}

	private List<String> getAddrStrList(ReadyBlock readyBlock) {

		LinkedHashMap<String, Cash> inMap = readyBlock.getInMap();
		LinkedHashMap<String, Cash> outMap = readyBlock.getOutMap();
		Set<String> addrStrSet = new HashSet<>();

		for (Cash in : inMap.values())
			addrStrSet.add(in.getOwner());
		for (Cash out : outMap.values())
			addrStrSet.add(out.getOwner());

		return new ArrayList<>(addrStrSet);
	}

	private ArrayList<Freer> readAddrListFromEs(ElasticsearchClient esClient, List<String> addrStrList)
			throws Exception {
		EsUtils.MgetResult<Freer> addrMgetResult = EsUtils.getMultiByIdList(esClient, FREER, addrStrList,
				Freer.class);

		ArrayList<Freer> addrOldList = (ArrayList<Freer>) addrMgetResult.getResultList();

		List<String> addrNewStrList = addrMgetResult.getMissList();
		ArrayList<Freer> addrNewList = new ArrayList<>();

		for (String addrStr : addrNewStrList) {
			Freer addr = new Freer();
			addr.setId(addrStr);
			addrNewList.add(addr);
		}

		ArrayList<Freer> addrList = new ArrayList<>();
		addrList.addAll(addrOldList);
		addrList.addAll(addrNewList);
		return addrList;
	}
}
