package fchData;

public class Block {
	//from block head
	private long size;		//block size
	private long height;		//block height
	private String version;		//version
	private String preBlockId;	//previous block hash
	private String merkleRoot;	//merkle tree root
	private long time;		//block timestamp
	private long bits;		//The current difficulty target
	private long nonce;		//nonce
	private int txCount;		//number of TXs included

	//calculated
	private String blockId;		//block hash
	private long inValueT;		//total amount of all inputs values in satoshi
	private long outValueT;		//total amount of all outputs values in satoshi
	private long fee;		//total amount of tx fee in satoshi
	private long cdd;		//total amount of coindays destroyed
	
	public long getSize() {
		return size;
	}
	public void setSize(long size) {
		this.size = size;
	}
	public long getHeight() {
		return height;
	}
	public void setHeight(long height) {
		this.height = height;
	}
	public String getVersion() {
		return version;
	}
	public void setVersion(String version) {
		this.version = version;
	}
	public String getPreBlockId() {
		return preBlockId;
	}
	public void setPreBlockId(String preBlockId) {
		this.preBlockId = preBlockId;
	}
	public String getMerkleRoot() {
		return merkleRoot;
	}
	public void setMerkleRoot(String merkleRoot) {
		this.merkleRoot = merkleRoot;
	}
	public long getTime() {
		return time;
	}
	public void setTime(long time) {
		this.time = time;
	}
	public long getBits() {
		return bits;
	}
	public void setBits(long bits) {
		this.bits = bits;
	}
	public long getNonce() {
		return nonce;
	}
	public void setNonce(long nonce) {
		this.nonce = nonce;
	}
	public int getTxCount() {
		return txCount;
	}
	public void setTxCount(int txCount) {
		this.txCount = txCount;
	}
	public String getBlockId() {
		return blockId;
	}
	public void setBlockId(String blockId) {
		this.blockId = blockId;
	}
	public long getInValueT() {
		return inValueT;
	}
	public void setInValueT(long inValueT) {
		this.inValueT = inValueT;
	}
	public long getOutValueT() {
		return outValueT;
	}
	public void setOutValueT(long outValueT) {
		this.outValueT = outValueT;
	}
	public long getFee() {
		return fee;
	}
	public void setFee(long fee) {
		this.fee = fee;
	}
	public long getCdd() {
		return cdd;
	}
	public void setCdd(long cdd) {
		this.cdd = cdd;
	}
}