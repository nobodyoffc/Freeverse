package data.fchData;

import data.fcData.FcObject;

import java.util.*;

import static constants.FieldNames.*;

public class OpReturn extends FcObject {

	private Long height;		//block height
	private Long time;
	private Integer txIndex;		//tx index in the block
	private String opReturn;	//OP_RETURN text
	private String signer;	//address of the first input.
	private String recipient;	//address of the first output, but the first input address and opReturn output.
	private Long cdd;

	//Static display methods

	public static LinkedHashMap<String,Integer> getFieldWidthMap(){
		LinkedHashMap<String,Integer> map = new LinkedHashMap<>();
		map.put(ID, DEFAULT_ID_LENGTH);
		map.put(SIGNER, DEFAULT_ID_LENGTH);
		map.put(OP_RETURN, DEFAULT_ID_LENGTH);
		map.put(CDD, DEFAULT_CD_LENGTH);
		return map;
	}
	public static List<String> getTimestampFieldList(){
		return List.of(TIME);
	}

	public static List<String> getSatoshiFieldList(){
		return new ArrayList<>();
	}
	public static Map<String, String> getHeightToTimeFieldMap() {
		return new HashMap<>();
	}

	public static Map<String, String> getShowFieldNameAsMap() {
		Map<String,String> map = new HashMap<>();
		map.put(ID,OP_RETURN_ID);
		return map;
	}
	public static List<String> getReplaceWithMeFieldList() {
		return new ArrayList<>();
	}

	//For create with user input
	public static Map<String, Object> getInputFieldDefaultValueMap() {
		return new HashMap<>();
	}


	public Long getHeight() {
		return height;
	}
	public void setHeight(Long height) {
		this.height = height;
	}
	public Long getTime() {
		return time;
	}
	public void setTime(Long time) {
		this.time = time;
	}
	public Integer getTxIndex() {
		return txIndex;
	}
	public void setTxIndex(Integer txIndex) {
		this.txIndex = txIndex;
	}
	public String getOpReturn() {
		return opReturn;
	}
	public void setOpReturn(String opReturn) {
		this.opReturn = opReturn;
	}
	public String getSigner() {
		return signer;
	}
	public void setSigner(String signer) {
		this.signer = signer;
	}
	public String getRecipient() {
		return recipient;
	}
	public void setRecipient(String recipient) {
		this.recipient = recipient;
	}
	public Long getCdd() {
		return cdd;
	}
	public void setCdd(Long cdd) {
		this.cdd = cdd;
	}

	
	
}
