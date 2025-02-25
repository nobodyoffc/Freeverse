package feip.feipData;

import feip.FeipOp;
import java.util.Map;

import constants.FieldNames;

import java.util.List;
import java.util.HashMap;

public class AppOpData {

	private String aid;
	private List<String> aids;
	private String op;
	private String ver;
	private String stdName;
	private String[] localNames;
	private String desc;
	private String[] types;
	private String[] urls;
	private App.Download[] downloads;
	private String[] waiters;
	private String[] protocols;
	private String[] codes;
	private String[] services;
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
		OP_FIELDS.put(Op.PUBLISH.toLowerCase(), new String[]{FieldNames.STD_NAME, FieldNames.LOCAL_NAMES, FieldNames.DESC, FieldNames.TYPES, FieldNames.URLS, FieldNames.DOWNLOADS, FieldNames.WAITERS, FieldNames.PROTOCOLS, FieldNames.CODES, FieldNames.SERVICES});
		OP_FIELDS.put(Op.UPDATE.toLowerCase(), new String[]{FieldNames.AID, FieldNames.STD_NAME, FieldNames.LOCAL_NAMES, FieldNames.DESC, FieldNames.TYPES, FieldNames.URLS, FieldNames.DOWNLOADS, FieldNames.WAITERS, FieldNames.PROTOCOLS, FieldNames.CODES, FieldNames.SERVICES});
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

	public String[] getLocalNames() {
		return localNames;
	}

	public void setLocalNames(String[] localNames) {
		this.localNames = localNames;
	}

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}

	public String[] getUrls() {
		return urls;
	}

	public void setUrls(String[] urls) {
		this.urls = urls;
	}

	public String[] getWaiters() {
		return waiters;
	}

	public void setWaiters(String[] waiters) {
		this.waiters = waiters;
	}

	public String[] getProtocols() {
		return protocols;
	}

	public void setProtocols(String[] protocols) {
		this.protocols = protocols;
	}

	public String[] getServices() {
		return services;
	}

	public void setServices(String[] services) {
		this.services = services;
	}

	public String[] getTypes() {
		return types;
	}

	public void setTypes(String[] types) {
		this.types = types;
	}

	public Integer getRate() {
		return rate;
	}

	public void setRate(Integer rate) {
		this.rate = rate;
	}

	public String[] getCodes() {
		return codes;
	}

	public void setCodes(String[] codes) {
		this.codes = codes;
	}

	public String getCloseStatement() {
		return closeStatement;
	}

	public void setCloseStatement(String closeStatement) {
		this.closeStatement = closeStatement;
	}

	public App.Download[] getDownloads() {
		return downloads;
	}

	public void setDownloads(App.Download[] downloads) {
		this.downloads = downloads;
	}



	public List<String> getAids() {
		return aids;
	}

	public void setAids(List<String> aids) {
		this.aids = aids;
	}

	// Factory method for PUBLISH operation
	public static AppOpData makePublish(String aid, String stdName, String[] localNames,
										String desc, String[] types, String[] urls, App.Download[] downloads,
										String[] waiters, String[] protocols, String[] codes, String[] services) {
		AppOpData data = new AppOpData();
		data.setOp(Op.PUBLISH.toLowerCase());
		data.setAid(aid);
		data.setStdName(stdName);
		data.setLocalNames(localNames);
		data.setDesc(desc);
		data.setTypes(types);
		data.setUrls(urls);
		data.setDownloads(downloads);
		data.setWaiters(waiters);
		data.setProtocols(protocols);
		data.setCodes(codes);
		data.setServices(services);
		return data;
	}

	// Factory method for UPDATE operation
	public static AppOpData makeUpdate(String aid, String stdName, String[] localNames,
									   String desc, String[] types, String[] urls, App.Download[] downloads,
									   String[] waiters, String[] protocols, String[] codes, String[] services) {
		AppOpData data = new AppOpData();
		data.setOp(Op.UPDATE.toLowerCase());
		data.setAid(aid);
		data.setStdName(stdName);
		data.setLocalNames(localNames);
		data.setDesc(desc);
		data.setTypes(types);
		data.setUrls(urls);
		data.setDownloads(downloads);
		data.setWaiters(waiters);
		data.setProtocols(protocols);
		data.setCodes(codes);
		data.setServices(services);
		return data;
	}

	// Factory method for STOP operation
	public static AppOpData makeStop(List<String> aids) {
		AppOpData data = new AppOpData();
		data.setOp(Op.STOP.toLowerCase());
		data.setAids(aids);
		return data;
	}

	// Factory method for CLOSE operation
	public static AppOpData makeClose(List<String> aids, String closeStatement) {
		AppOpData data = new AppOpData();
		data.setOp(Op.CLOSE.toLowerCase());
		data.setAids(aids);
		data.setCloseStatement(closeStatement);
		return data;
	}

	// Factory method for RECOVER operation
	public static AppOpData makeRecover(List<String> aids) {
		AppOpData data = new AppOpData();
		data.setOp(Op.RECOVER.toLowerCase());
		data.setAids(aids);
		return data;
	}

	// Factory method for RATE operation
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
