package data.fchData;

import data.fcData.FcObject;

import java.util.*;

import static constants.FieldNames.*;

public class Block extends FcObject {
	//from block head
	private Long size;		//block size
	private Long height;		//block height
	private String version;		//version
	private String preId;	//previous block hash
	private String merkleRoot;	//merkle tree root
	private Long time;		//block timestamp
	private Long bits;		//The current difficulty target
	private Long nonce;		//nonce
	private Integer txCount;		//number of TXs included

	//calculated
	private Long inValueT;		//total amount of all inputs values in satoshi
	private Long outValueT;		//total amount of all outputs values in satoshi
	private Long fee;		//total amount of tx fee in satoshi
	private Long cdd;		//total amount of coindays destroyed


	//Static display methods

	public static LinkedHashMap<String,Integer> getFieldWidthMap(){
		LinkedHashMap<String,Integer> map = new LinkedHashMap<>();
		map.put(ID, DEFAULT_ID_LENGTH);
		map.put(HEIGHT, DEFAULT_ID_LENGTH);
		map.put(TIME, DEFAULT_TIME_LENGTH);
		map.put(TX_COUNT, DEFAULT_ID_LENGTH);
		map.put(IN_VALUE_T, DEFAULT_ID_LENGTH);
		map.put(FEE, DEFAULT_ID_LENGTH);
		map.put(CDD, DEFAULT_CD_LENGTH);
		return map;
	}
	public static List<String> getTimestampFieldList(){
		return List.of(TIME);
	}

	public static List<String> getSatoshiFieldList(){
		return List.of(IN_VALUE_T,OUT_VALUE_T);
	}
	public static Map<String, String> getHeightToTimeFieldMap() {
		return new HashMap<>();
	}

	public static Map<String, String> getShowFieldNameAsMap() {
		Map<String,String> map = new HashMap<>();
		map.put(ID,BLOCK_ID);
		return map;
	}
	public static List<String> getReplaceWithMeFieldList() {
		return new ArrayList<>();
	}

	//For create with user input
	public static Map<String, Object> getInputFieldDefaultValueMap() {
		return new HashMap<>();
	}

	
	public Long getSize() {
		return size;
	}
	public void setSize(Long size) {
		this.size = size;
	}
	public Long getHeight() {
		return height;
	}
	public void setHeight(Long height) {
		this.height = height;
	}
	public String getVersion() {
		return version;
	}
	public void setVersion(String version) {
		this.version = version;
	}
	public String getPreId() {
		return preId;
	}
	public void setPreId(String preId) {
		this.preId = preId;
	}
	public String getMerkleRoot() {
		return merkleRoot;
	}
	public void setMerkleRoot(String merkleRoot) {
		this.merkleRoot = merkleRoot;
	}
	public Long getTime() {
		return time;
	}
	public void setTime(Long time) {
		this.time = time;
	}
	public Long getBits() {
		return bits;
	}
	public void setBits(Long bits) {
		this.bits = bits;
	}
	public Long getNonce() {
		return nonce;
	}
	public void setNonce(Long nonce) {
		this.nonce = nonce;
	}
	public Integer getTxCount() {
		return txCount;
	}
	public void setTxCount(Integer txCount) {
		this.txCount = txCount;
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