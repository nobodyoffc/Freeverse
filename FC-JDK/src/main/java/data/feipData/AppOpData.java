package data.feipData;

import constants.Values;

import java.util.Map;

import constants.FieldNames;

import java.util.List;
import java.util.HashMap;

public class AppOpData{

	private String aid;
	private List<String> aids;
	private String op;
	private String ver;
	private String stdName;
	private Map<String, String> localNames;
	private String desc;
	private List<String> types;
	private Map<String, String> home;
	private List<App.Download> downloads;
	private List<String> waiters;
	private List<String> protocols;
	private List<String> codes;
	private List<String> services;
	private Integer rate;
	private String closeStatement;

	public enum Op {
		PUBLISH(FeipOp.PUBLISH),
		UPDATE(FeipOp.UPDATE),
		STOP(FeipOp.STOP),
		CLOSE(FeipOp.CLOSE),
		RECOVER(FeipOp.RECOVER),
		RATE(FeipOp.RATE);

		private final FeipOp feipOp;

		Op(FeipOp feipOp) {
			this.feipOp = feipOp;
		}

		public FeipOp getFeipOp() {
			return feipOp;
		}

		public static Op fromString(String text) {
			for (Op op : Op.values()) {
				if (op.name().equalsIgnoreCase(text)) {
					return op;
				}
			}
			throw new IllegalArgumentException("No constant with text " + text + " found");
		}

		public String toLowerCase() {
			return feipOp.getValue().toLowerCase();
		}
	}

	public static final Map<String, String[]> OP_FIELDS = new HashMap<>();

	static {
		OP_FIELDS.put(Op.PUBLISH.toLowerCase(), new String[]{FieldNames.STD_NAME, FieldNames.LOCAL_NAMES, Values.DESC, FieldNames.TYPES, FieldNames.HOME, FieldNames.DOWNLOADS, FieldNames.WAITERS, FieldNames.PROTOCOLS, FieldNames.CODES, FieldNames.SERVICES});
		OP_FIELDS.put(Op.UPDATE.toLowerCase(), new String[]{FieldNames.AID, FieldNames.STD_NAME, FieldNames.LOCAL_NAMES, Values.DESC, FieldNames.TYPES, FieldNames.HOME, FieldNames.DOWNLOADS, FieldNames.WAITERS, FieldNames.PROTOCOLS, FieldNames.CODES, FieldNames.SERVICES});
		OP_FIELDS.put(Op.STOP.toLowerCase(), new String[]{FieldNames.AIDS});
		OP_FIELDS.put(Op.CLOSE.toLowerCase(), new String[]{FieldNames.AIDS, FieldNames.CLOSE_STATEMENT});
		OP_FIELDS.put(Op.RECOVER.toLowerCase(), new String[]{FieldNames.AIDS});
		OP_FIELDS.put(Op.RATE.toLowerCase(), new String[]{FieldNames.AID, FieldNames.RATE});
	}

	public String getAid() {
		return aid;
	}

	public void setAid(String aid) {
		this.aid = aid;
	}

	public String getOp() {
		return op;
	}

	public void setOp(String op) {
		this.op = op;
	}

	public String getStdName() {
		return stdName;
	}

	public void setStdName(String stdName) {
		this.stdName = stdName;
	}

	public Map<String, String> getLocalNames() {
		return localNames;
	}

	public void setLocalNames(Map<String, String> localNames) {
		this.localNames = localNames;
	}

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}

	public Map<String, String> getHome() {
		return home;
	}

	public void setHome(Map<String, String> home) {
		this.home = home;
	}

	public List<String> getWaiters() {
		return waiters;
	}

	public void setWaiters(List<String> waiters) {
		this.waiters = waiters;
	}

	public List<String> getProtocols() {
		return protocols;
	}

	public void setProtocols(List<String> protocols) {
		this.protocols = protocols;
	}

	public List<String> getServices() {
		return services;
	}

	public void setServices(List<String> services) {
		this.services = services;
	}

	public List<String> getTypes() {
		return types;
	}

	public void setTypes(List<String> types) {
		this.types = types;
	}

	public Integer getRate() {
		return rate;
	}

	public void setRate(Integer rate) {
		this.rate = rate;
	}

	public List<String> getCodes() {
		return codes;
	}

	public void setCodes(List<String> codes) {
		this.codes = codes;
	}

	public String getCloseStatement() {
		return closeStatement;
	}

	public void setCloseStatement(String closeStatement) {
		this.closeStatement = closeStatement;
	}

	public List<App.Download> getDownloads() {
		return downloads;
	}

	public void setDownloads(List<App.Download> downloads) {
		this.downloads = downloads;
	}

	public List<String> getAids() {
		return aids;
	}

	public void setAids(List<String> aids) {
		this.aids = aids;
	}

	public static AppOpData makePublish(String stdName, Map<String, String> localNames,
										String desc, List<String> types, Map<String, String> home, List<App.Download> downloads,
										List<String> waiters, List<String> protocols, List<String> codes, List<String> services) {
		AppOpData data = new AppOpData();
		data.setOp(Op.PUBLISH.toLowerCase());
		data.setStdName(stdName);
		data.setLocalNames(localNames);
		data.setDesc(desc);
		data.setTypes(types);
		data.setHome(home);
		data.setDownloads(downloads);
		data.setWaiters(waiters);
		data.setProtocols(protocols);
		data.setCodes(codes);
		data.setServices(services);
		return data;
	}

	public static AppOpData makeUpdate(String aid, String stdName, Map<String, String> localNames,
									   String desc, List<String> types, Map<String, String> home, List<App.Download> downloads,
									   List<String> waiters, List<String> protocols, List<String> codes, List<String> services) {
		AppOpData data = new AppOpData();
		data.setOp(Op.UPDATE.toLowerCase());
		data.setAid(aid);
		data.setStdName(stdName);
		data.setLocalNames(localNames);
		data.setDesc(desc);
		data.setTypes(types);
		data.setHome(home);
		data.setDownloads(downloads);
		data.setWaiters(waiters);
		data.setProtocols(protocols);
		data.setCodes(codes);
		data.setServices(services);
		return data;
	}

	public static AppOpData makeStop(List<String> aids) {
		AppOpData data = new AppOpData();
		data.setOp(Op.STOP.toLowerCase());
		data.setAids(aids);
		return data;
	}

	public static AppOpData makeClose(List<String> aids, String closeStatement) {
		AppOpData data = new AppOpData();
		data.setOp(Op.CLOSE.toLowerCase());
		data.setAids(aids);
		data.setCloseStatement(closeStatement);
		return data;
	}

	public static AppOpData makeRecover(List<String> aids) {
		AppOpData data = new AppOpData();
		data.setOp(Op.RECOVER.toLowerCase());
		data.setAids(aids);
		return data;
	}

	public static AppOpData makeRate(String aid, Integer rate) {
		AppOpData data = new AppOpData();
		data.setOp(Op.RATE.toLowerCase());
		data.setAid(aid);
		data.setRate(rate);
		return data;
	}

	public String getVer() {
		return ver;
	}

	public void setVer(String ver) {
		this.ver = ver;
	}

}
