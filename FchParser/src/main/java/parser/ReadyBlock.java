package parser;

import java.util.LinkedHashMap;

import data.fchData.Cash;
import data.fchData.*;

public class ReadyBlock {

	private BlockMask blockMask;
	private Block block;
	private LinkedHashMap<String, Tx> txLinkedMap;
	private LinkedHashMap<String, Cash> inMap;
	private LinkedHashMap<String, Cash> outMap;
	private LinkedHashMap<String, OpReturn> opReturnMap;
	private LinkedHashMap<String, Freer> addrMap;
	private LinkedHashMap<String, Cash> outWriteMap;
	private LinkedHashMap<String,P2SH> p2SHMap;
	private LinkedHashMap<String,Multisig> multisigMap;
	
	public BlockMask getBlockMark() {
		return blockMask;
	}
	public void setBlockMark(BlockMask blockMask) {
		this.blockMask = blockMask;
	}
	public Block getBlock() {
		return block;
	}
	public void setBlock(Block block) {
		this.block = block;
	}
	public LinkedHashMap<String, Tx> getTxLinkedMap() {
		return txLinkedMap;
	}
	public void setTxLinkedMap(LinkedHashMap<String, Tx> txLinkedMap) {
		this.txLinkedMap = txLinkedMap;
	}

	public LinkedHashMap<String, Cash> getInMap() {
		return inMap;
	}
	public void setInMap(LinkedHashMap<String, Cash> inMap) {
		this.inMap = inMap;
	}
	public LinkedHashMap<String, Cash> getOutMap() {
		return outMap;
	}
	public void setOutMap(LinkedHashMap<String, Cash> outMap) {
		this.outMap = outMap;
	}
	public LinkedHashMap<String, OpReturn> getOpReturnMap() {
		return opReturnMap;
	}
	public void setOpReturnMap(LinkedHashMap<String, OpReturn> opReturnMap) {
		this.opReturnMap = opReturnMap;
	}
	public LinkedHashMap<String, Freer> getAddrMap() {
		return addrMap;
	}
	public void setAddrMap(LinkedHashMap<String, Freer> addrMap) {
		this.addrMap = addrMap;
	}
	public LinkedHashMap<String, Cash> getOutWriteMap() {
		return outWriteMap;
	}
	public void setOutWriteMap(LinkedHashMap<String, Cash> outWriteMap) {
		this.outWriteMap = outWriteMap;
	}

	public LinkedHashMap<String, P2SH> getP2SHMap() {
		if(p2SHMap==null)p2SHMap=new LinkedHashMap<>();
		return p2SHMap;
	}

	public void setP2SHMap(LinkedHashMap<String, P2SH> p2SHMap) {
		this.p2SHMap = p2SHMap;
	}

	public LinkedHashMap<String, Multisig> getMultisigMap() {
		if(multisigMap==null)multisigMap= new LinkedHashMap<>();
		return multisigMap;
	}

	public void setMultisigMap(LinkedHashMap<String, Multisig> multisigMap) {
		this.multisigMap = multisigMap;
	}
}
