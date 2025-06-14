package data.fchData;

import data.fcData.FcObject;

import java.util.*;

import static constants.FieldNames.*;

public class Tx extends FcObject {
	
	//from block;
	private Integer version;		//version
	private Long lockTime;	//lockTime
	private Long blockTime;		//blockTime
	private String blockId;		//block ID, hash of block head
	private Integer txIndex;		//the index of this tx in the block
	private String coinbase;	//string of the coinbase script
	private Integer outCount;		//number of outputs
	private Integer inCount;		//number of inputs
	private Long height;		//block height of the block
	
	private String opReBrief; 	//Former 30 bytes of OP_RETURN data in String.
	
	//calculated
	private Long inValueT;		//total amount of inputs
	private Long outValueT;		//total amount of outputs
	private Long fee;		//tx fee
	
	private Long cdd;
	transient private String rawTx;

	//Static methods


	public static LinkedHashMap<String,Integer> getFieldWidthMap(){
		LinkedHashMap<String,Integer> map = new LinkedHashMap<>();
		map.put(ID, DEFAULT_ID_LENGTH);
		map.put(IN_VALUE_T, DEFAULT_ID_LENGTH);
		map.put(BLOCK_TIME, DEFAULT_TIME_LENGTH);
		map.put(OUT_COUNT, DEFAULT_ID_LENGTH);
		map.put(IN_COUNT, DEFAULT_ID_LENGTH);
		map.put(FEE, DEFAULT_ID_LENGTH);
		map.put(CDD, DEFAULT_ID_LENGTH);
		map.put(OP_RE_BRIEF, DEFAULT_ID_LENGTH);
		map.put(HEIGHT, DEFAULT_ID_LENGTH);
		map.put(TX_INDEX, DEFAULT_ID_LENGTH);

		return map;
	}
	public static List<String> getTimestampFieldList(){
		return List.of(BLOCK_TIME,LOCK_TIME);
	}

	public static List<String> getSatoshiFieldList(){
		return List.of(IN_VALUE_T,OUT_VALUE_T);
	}
	public static Map<String, String> getHeightToTimeFieldMap() {
		return new HashMap<>();
	}

	public static Map<String, String> getShowFieldNameAsMap() {
		Map<String,String> map = new HashMap<>();
		map.put(ID,CASH_ID);
		return map;
	}
	public static List<String> getReplaceWithMeFieldList() {
		return new ArrayList<>();
	}

	//For create with user input
	public static Map<String, Object> getInputFieldDefaultValueMap() {
		return new HashMap<>();
	}

	public String getRawTx() {
		return rawTx;
	}

	public void setRawTx(String rawTx) {
		this.rawTx = rawTx;
	}

	public Integer getVersion() {
		return version;
	}

	public void setVersion(Integer version) {
		this.version = version;
	}

	public Long getLockTime() {
		return lockTime;
	}

	public void setLockTime(Long lockTime) {
		this.lockTime = lockTime;
	}

	public Long getBlockTime() {
		return blockTime;
	}

	public void setBlockTime(Long blockTime) {
		this.blockTime = blockTime;
	}

	public String getBlockId() {
		return blockId;
	}

	public void setBlockId(String blockId) {
		this.blockId = blockId;
	}

	public Integer getTxIndex() {
		return txIndex;
	}

	public void setTxIndex(Integer txIndex) {
		this.txIndex = txIndex;
	}

	public String getCoinbase() {
		return coinbase;
	}

	public void setCoinbase(String coinbase) {
		this.coinbase = coinbase;
	}

	public Integer getOutCount() {
		return outCount;
	}

	public void setOutCount(Integer outCount) {
		this.outCount = outCount;
	}

	public Integer getInCount() {
		return inCount;
	}

	public void setInCount(Integer inCount) {
		this.inCount = inCount;
	}

	public Long getHeight() {
		return height;
	}

	public void setHeight(Long height) {
		this.height = height;
	}

	public String getOpReBrief() {
		return opReBrief;
	}

	public void setOpReBrief(String opReBrief) {
		this.opReBrief = opReBrief;
	}

	public Long getInValueT() {
		return inValueT;
	}

	public void setInValueT(Long inValueT) {
		this.inValueT = inValueT;
	}

	public Long getOutValueT() {
		return outValueT;
	}

	public void setOutValueT(Long outValueT) {
		this.outValueT = outValueT;
	}

	public Long getFee() {
		return fee;
	}

	public void setFee(Long fee) {
		this.fee = fee;
	}

	public Long getCdd() {
		return cdd;
	}

	public void setCdd(Long cdd) {
		this.cdd = cdd;
	}

}
