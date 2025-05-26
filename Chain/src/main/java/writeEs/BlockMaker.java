package writeEs;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import core.crypto.KeyTools;
import data.fchData.*;
import core.fch.Weight;
import parser.ReadyBlock;
import utils.EsUtils;
import utils.FchUtils;

import java.util.*;

import static constants.IndicesNames.*;

public class BlockMaker {

	public ReadyBlock makeReadyBlock(ElasticsearchClient esClient, ReadyBlock rawBlock) throws Exception {

		if (rawBlock.getInList() == null || rawBlock.getInList().isEmpty()) {

			ReadyBlock txTxHasOpMadeBlock = makeTxTxHasOpReturn(rawBlock);
			ReadyBlock blockBlockHasMadeBlock = makeBlockBlockHas(txTxHasOpMadeBlock);
			ReadyBlock addrMadeBlock = makeAddress(esClient, blockBlockHasMadeBlock);
			addrMadeBlock.setOutWriteList(addrMadeBlock.getOutList());
			return addrMadeBlock;

		} else {
			ReadyBlock inputMadeBlock = makeInputList(esClient, rawBlock);
			inputMadeBlock.setOutList(rawBlock.getOutList());
			ReadyBlock txTxHasOpMadeBlock = makeTxTxHasOpReturn(inputMadeBlock);
			ReadyBlock blockBlockHasMadeBlock = makeBlockBlockHas(txTxHasOpMadeBlock);

			return makeAddress(esClient, blockBlockHasMadeBlock);
		}

	}

	private ReadyBlock makeInputList(ElasticsearchClient esClient, ReadyBlock rawBlock) throws Exception {

		ArrayList<Cash> inList = rawBlock.getInList();
		Map<String, Cash> inMap = new HashMap<>();
		List<String> inStrList = new ArrayList<>();
		for (Cash in : inList) {
			inMap.put(in.getId(), in);
			inStrList.add(in.getId());
		}

		ArrayList<Cash> outList = rawBlock.getOutList();
		Map<String, Cash> outMap = new HashMap<>();
		for (Cash out : outList) {
			outMap.put(out.getId(), out);
		}

		EsUtils.MgetResult<Cash> inMgetResult = EsUtils.getMultiByIdList(esClient, CASH, inStrList, Cash.class);
		ArrayList<Cash> inOldList = (ArrayList<Cash>) inMgetResult.getResultList();
		List<String> inNewIdList = inMgetResult.getMissList();

		ArrayList<Cash> inMadeList = new ArrayList<>();
		ArrayList<Cash> outWriteList = new ArrayList<>();

		for (Cash out : inOldList) {
			Cash in = inMap.get(out.getId());
			if(out.getIssuer()!=null)
				in.setIssuer(out.getIssuer());
			setInFromOut(out, in);
			inMadeList.add(in);
		}

		for (String id : inNewIdList) {
			Cash in = inMap.get(id);
			Cash out = outMap.get(id);

			//这些本区块产生的cash 还没有被设置issuer
//			in.setIssuer(out.getIssuer());
			if(out==null){
				System.out.println("Input missed:"+id+". " +
						"\nTry to rollback. If it does not work, try to rollback 20000 blocks or more. " +
						"\nIf still does not work, you have to reparse all blocks. Sorry. " +
						"\n The cause is generally a interruption when rolling back.");
				RuntimeException runtimeException = new RuntimeException();
				runtimeException.printStackTrace();
				throw runtimeException;
			}
			setInFromOut(out, in);
			outMap.remove(id);
			inMadeList.add(in);
		}

		Set<String> idSet = outMap.keySet();
		for (String id : idSet) {
			outWriteList.add(outMap.get(id));
		}

		rawBlock.setInList(inMadeList);
		rawBlock.setOutWriteList(outWriteList);
		rawBlock.setOutList(outList);
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

	private ReadyBlock makeTxTxHasOpReturn(ReadyBlock blockForMaking) {

		ArrayList<Cash> outList = blockForMaking.getOutList();
		ArrayList<Tx> txList = blockForMaking.getTxList();
		ArrayList<OpReturn> opList = blockForMaking.getOpReturnList();

		Map<String, Tx> txMap = new HashMap<>();
		Map<String, TxHas> txHasMap = new HashMap<>();

		for (Tx tx : txList) {
			txMap.put(tx.getId(), tx);

			TxHas txHas = new TxHas();
			ArrayList<CashMark> inMarks = new ArrayList<>();
			ArrayList<CashMark> outMarks = new ArrayList<>();
			txHas.setId(tx.getId());
			txHas.setRawTx(tx.getRawTx());
			txHas.setHeight(tx.getHeight());
			txHas.setInMarks(inMarks);
			txHas.setOutMarks(outMarks);
			txHasMap.put(tx.getId(), txHas);
		}

		if (blockForMaking.getInList() != null)
			for (Cash in : blockForMaking.getInList()) {
				long value = in.getValue();
				long cdd = in.getCdd();

				Tx tx = txMap.get(in.getSpendTxId());

				if(tx.getInValueT()!=null)tx.setInValueT(tx.getInValueT() + value);
				else tx.setInValueT(value);

				if(tx.getCdd()!=null)tx.setCdd(tx.getCdd() + cdd);
				else tx.setCdd(cdd);

				TxHas txHas = txHasMap.get(in.getSpendTxId());
				CashMark inMark = new CashMark();
				inMark.setId(in.getId());
				inMark.setOwner(in.getOwner());
				inMark.setValue(in.getValue());
				inMark.setCdd(in.getCdd());

				txHas.getInMarks().add(inMark);
			}

		for (Cash out : outList) {
			long value = out.getValue();

			Tx tx = txMap.get(out.getBirthTxId());

			if(tx.getOutValueT()!=null)
				tx.setOutValueT(tx.getOutValueT() + value);
			else tx.setOutValueT(value);

			TxHas txHas = txHasMap.get(out.getBirthTxId());
			CashMark outMark = new CashMark();
			outMark.setId(out.getId());
			outMark.setOwner(out.getOwner());
			outMark.setValue(out.getValue());

			txHas.getOutMarks().add(outMark);

			if(txHas.getInMarks().size()>0){
				out.setIssuer(txHas.getInMarks().get(0).getOwner());
			}else {
				out.setIssuer("coinbase");
			}
		}

		for(Cash in : blockForMaking.getInList()){
			if(in.getIssuer()==null){
				TxHas txHas = txHasMap.get(in.getBirthTxId());
				ArrayList<CashMark> inMarks = txHas.getInMarks();
				in.setIssuer(inMarks.get(0).getOwner());
			}
		}

		if (opList != null && !opList.isEmpty()) {

			//TODO
			Iterator<OpReturn> iterOp = opList.iterator();
			OpReturn op;
			while(iterOp.hasNext()) {
				op = iterOp.next();
				if("".equals(op.getOpReturn()))iterOp.remove();
			}

			for (OpReturn opReturn : opList) {
				String txId = opReturn.getId();

				Tx tx = txMap.get(txId);
				opReturn.setCdd(tx.getCdd());
				opReturn.setTime(tx.getBlockTime());

				TxHas txhas = txHasMap.get(txId);
				String signer = txhas.getInMarks().get(0).getOwner();
				opReturn.setSigner(signer);

				for (CashMark txoB : txhas.getOutMarks()) {
					String addr = txoB.getOwner();
					if (!addr.equals(signer) && !addr.equals("unknown") && !addr.equals("OpReturn")) {
						opReturn.setRecipient(addr);
						break;
					}
				}
				if (opReturn.getRecipient() == null)
					opReturn.setRecipient("nobody");
			}
		}

		Iterator<Tx> iterTx = txList.iterator();
		ArrayList<Tx> txGoodList = new ArrayList<>();
		while (iterTx.hasNext()) {
			Tx tx = txMap.get(iterTx.next().getId());
			if (tx.getInCount() != 0)
				tx.setFee(tx.getInValueT() - tx.getOutValueT());
			txGoodList.add(tx);
		}

		ArrayList<TxHas> txHasGoodList = new ArrayList<>();
		for (Tx tx : txList) {
			txHasGoodList.add(txHasMap.get(tx.getId()));
		}

		blockForMaking.setTxList(txGoodList);
		blockForMaking.setTxHasList(txHasGoodList);
		blockForMaking.setOpReturnList(opList);

		return blockForMaking;
	}

	private ReadyBlock makeBlockBlockHas(ReadyBlock txAndTxHasMadeBlock) {

		ArrayList<Tx> txList = txAndTxHasMadeBlock.getTxList();
		Block block = txAndTxHasMadeBlock.getBlock();
		BlockHas blockHas = new BlockHas();
		blockHas.setId(block.getId());
		blockHas.setHeight(block.getHeight());
		blockHas.setTxMarks(new ArrayList<>());

		for (Tx tx : txList) {
			Long blockInValueT = block.getInValueT();
			if(blockInValueT==null)blockInValueT=0L;

			Long blockOutValueT = block.getOutValueT();
			if(blockOutValueT==null)blockOutValueT=0L;

			Long txInValueT = tx.getInValueT();
			if(txInValueT==null)txInValueT=0L;

			Long txOutValueT = tx.getOutValueT();
			if(txOutValueT==null)txOutValueT=0L;

			block.setInValueT(blockInValueT + txInValueT);
			block.setOutValueT(blockOutValueT + txOutValueT);

			Long blockFee = block.getFee();
			if(blockFee==null)blockFee=0L;
			Long blockCdd = block.getCdd();
			if(blockCdd==null)blockCdd=0L;

			Long txFee = tx.getFee();
			if(txFee==null)txFee=0L;
			Long txCdd = tx.getCdd();
			if(txCdd==null)txCdd=0L;

			block.setFee(blockFee + txFee);
			block.setCdd(blockCdd + txCdd);

			TxMark txMark = new TxMark();
			txMark.setId(tx.getId());
			txMark.setOutValue(tx.getOutValueT());

			if (tx.getInCount() != 0) {
				long fee = tx.getFee();
				txMark.setFee(fee);
				txMark.setCdd(tx.getCdd());
			}
			blockHas.getTxMarks().add(txMark);
		}

		txAndTxHasMadeBlock.setBlock(block);
		txAndTxHasMadeBlock.setBlockHas(blockHas);

		return txAndTxHasMadeBlock;
	}

	private ReadyBlock makeAddress(ElasticsearchClient esClient, ReadyBlock readyBlock) throws Exception {

		List<String> addrStrList = getAddrStrList(readyBlock);

		ArrayList<Cid> addrList = readAddrListFromEs(esClient, addrStrList);

		Map<String, Cid> addrMap = new HashMap<>();

		for (Cid addr : addrList) {
			addrMap.put(addr.getId(), addr);
		}

		ArrayList<TxHas> txHasList = readyBlock.getTxHasList();

		for (TxHas txHas : txHasList) {

			if (txHas.getInMarks() != null && !txHas.getInMarks().isEmpty()) {
				for (CashMark inb : txHas.getInMarks()) {
					String inAddr = inb.getOwner();
					long inValue = inb.getValue();
					long cdd = inb.getCdd();

					Cid addr = addrMap.get(inAddr);
					if(addr.getExpend()==null)addr.setExpend(inValue);
					else addr.setExpend(addr.getExpend() + inValue);
					if(addr.getBalance()==null)addr.setBalance(inValue);
					else addr.setBalance(addr.getBalance() - inValue);
					if(addr.getCdd()==null)addr.setCdd(cdd);
					else addr.setCdd(addr.getCdd() + cdd);
					if(addr.getWeight()==null)addr.setWeight((cdd* Weight.cddPercentInWeight)/100);
					else addr.setWeight(addr.getWeight()+(cdd* Weight.cddPercentInWeight)/100);
					addr.setLastHeight(txHas.getHeight());
					if(addr.getCash()==null)addr.setCash(1L);
					else addr.setCash(addr.getCash() - 1);

					if (addr.getPubkey() == null) {
						ArrayList<Cash> inList = readyBlock.getInList();
						for (Cash in : inList) {
							if (in.getOwner().equals(addr.getId()) && in.getType().equals("P2PKH")) {
								setPKAndMoreAddrs(addr, in.getUnlockScript());
								break;
							}
							if (in.getOwner().equals(addr.getId()) && in.getType().equals("P2SH")) {
								data.fchData.P2SH p2sh = new P2SH();
								p2sh.parseP2SH(esClient, in);
								break;
							}
						}
					}
				}
			}
			for (CashMark outB : txHas.getOutMarks()) {
				String outAddr = outB.getOwner();
				long outValue = outB.getValue();
				Cid addr = addrMap.get(outAddr);
				if(addr.getIncome()==null)addr.setIncome(outValue);
				else addr.setIncome(addr.getIncome() + outValue);

				if(addr.getBalance()==null)addr.setBalance(outValue);
				else addr.setBalance(addr.getBalance() + outValue);

				addr.setLastHeight(txHas.getHeight());

				if(addr.getCash()==null)addr.setCash(1L);
				else addr.setCash(addr.getCash() + 1);

				if (addr.getBirthHeight() == null)
					addr.setBirthHeight(txHas.getHeight());

				if (addr.getGuide() == null) {
					if (txHas.getInMarks() != null && !txHas.getInMarks().isEmpty()) {
						addr.setGuide(txHas.getInMarks().get(0).getOwner());
					} else
						addr.setGuide("coinbase");
				}
			}
		}

		Collection<Cid> addrs = addrMap.values();

		ArrayList<Cid> readyAddrList = new ArrayList<>(addrs);

		readyBlock.setAddrList(readyAddrList);

		return readyBlock;
	}

	private List<String> getAddrStrList(ReadyBlock readyBlock) {

		ArrayList<Cash> inList = readyBlock.getInList();
		ArrayList<Cash> outList = readyBlock.getOutList();
		Set<String> addrStrSet = new HashSet<>();

		for (Cash in : inList)
			addrStrSet.add(in.getOwner());
		for (Cash out : outList)
			addrStrSet.add(out.getOwner());

		return new ArrayList<>(addrStrSet);
	}

	private ArrayList<Cid> readAddrListFromEs(ElasticsearchClient esClient, List<String> addrStrList)
			throws Exception {
		EsUtils.MgetResult<Cid> addrMgetResult = EsUtils.getMultiByIdList(esClient, CID, addrStrList,
				Cid.class);

		ArrayList<Cid> addrOldList = (ArrayList<Cid>) addrMgetResult.getResultList();

		List<String> addrNewStrList = addrMgetResult.getMissList();
		ArrayList<Cid> addrNewList = new ArrayList<>();

		for (String addrStr : addrNewStrList) {
			Cid addr = new Cid();
			addr.setId(addrStr);
			addrNewList.add(addr);
		}

		ArrayList<Cid> addrList = new ArrayList<>();
		addrList.addAll(addrOldList);
		addrList.addAll(addrNewList);
		return addrList;
	}

	private void setPKAndMoreAddrs(Cid addr, String unLockScript) {

		String pk = KeyTools.parsePkFromUnlockScript(unLockScript);

		addr.setPubkey(pk);
		addr.setBtcAddr(KeyTools.pubkeyToBtcAddr(pk));
		addr.setEthAddr(KeyTools.pubkeyToEthAddr(pk));
		addr.setBchAddr(KeyTools.pubkeyToBchBesh32Addr(pk));
		addr.setLtcAddr(KeyTools.pubkeyToLtcAddr(pk));
		addr.setDogeAddr(KeyTools.pubkeyToDogeAddr(pk));
		addr.setTrxAddr(KeyTools.pubkeyToTrxAddr(pk));
	}
}
