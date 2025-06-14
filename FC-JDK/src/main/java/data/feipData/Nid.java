package data.feipData;

import data.fcData.FcObject;

import java.util.*;

import static constants.FieldNames.*;

public class Nid extends FcObject {
	//nid
    private String name;
	private String desc;
	private String oid;
	
	private String namer;
	private Long birthTime;
	private Long birthHeight;
	private Long lastTime;
	private Long lastHeight;
	private Boolean active;


	public static LinkedHashMap<String,Integer> getFieldWidthMap(){
		LinkedHashMap<String,Integer> map = new LinkedHashMap<>();
		map.put(NAMER, DEFAULT_ID_LENGTH);
		map.put(NAME, DEFAULT_ID_LENGTH);
		map.put(OID, DEFAULT_ID_LENGTH);
		map.put(BIRTH_TIME, DEFAULT_TIME_LENGTH);
		map.put(ID, DEFAULT_ID_LENGTH);
		return map;
	}
	public static List<String> getTimestampFieldList(){
		return List.of(BIRTH_TIME);
	}

	public static List<String> getSatoshiFieldList(){
		return new ArrayList<>();
	}
	public static Map<String, String> getHeightToTimeFieldMap() {
		return new HashMap<>();
	}

	public static Map<String, String> getShowFieldNameAsMap() {
		Map<String,String> map = new HashMap<>();
		map.put(OID,"Object ID");
		return map;
	}
	public static List<String> getReplaceWithMeFieldList() {
		return new ArrayList<>();
	}

	//For create with user input
	public static Map<String, Object> getInputFieldDefaultValueMap() {
		return new HashMap<>();
	}


	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}

	public String getNamer() {
		return namer;
	}

	public void setNamer(String namer) {
		this.namer = namer;
	}

	public Long getBirthTime() {
		return birthTime;
	}

	public void setBirthTime(Long birthTime) {
		this.birthTime = birthTime;
	}

	public Long getBirthHeight() {
		return birthHeight;
	}

	public void setBirthHeight(Long birthHeight) {
		this.birthHeight = birthHeight;
	}

	public Long getLastTime() {
		return lastTime;
	}

	public void setLastTime(Long lastTime) {
		this.lastTime = lastTime;
	}

	public Long getLastHeight() {
		return lastHeight;
	}

	public void setLastHeight(Long lastHeight) {
		this.lastHeight = lastHeight;
	}

	public Boolean isActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	public String getOid() {
		return oid;
	}

	public void setOid(String oid) {
		this.oid = oid;
	}
}
