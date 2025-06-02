package data.feipData;

import utils.JsonUtils;

public class Feip {

	private String type;
	private String sn;
	private String ver;
	private String name;
	private String pid;
	private String did;
	private Object data;

	public Feip() {
	}


	public Feip(String sn, String ver, String name) {
		this.type = "FEIP";
		this.sn = sn;
		this.ver = ver;
		this.name = name;
	}

	public static enum ProtocolName {
		PROTOCOL("1","7", "Protocol"),
		CODE("2","1", "Code"),
		CID("3","4", "CID"),
		NOBODY("4","1", "Nobody"),
		SERVICE("5","2", "Service"),
		MASTER("6","6", "Master"),
		MAIL("7","4", "Mail"),
		STATEMENT("8","5", "Statement"),
		HOMEPAGE("9","1", "Homepage"),
		NOTICE_FEE("10","1", "NoticeFee"),
		NID("11","1", "NID"),
		CONTACT("12","3", "Contact"),
		BOX("13","1", "Box"),
		PROOF("14","1", "Proof"),
		APP("15","1", "APP"),
		REPUTATION("16","1", "Reputation"),
		SECRET("17","3", "Secret"),
		TEAM("18","1", "Team"),
		GROUP("19","3", "Group"),
		TOKEN("20","1", "Token"),
		BOOK("21","1", "Book"),
		PAPER("22","1", "Paper"),
		REPORT("23","1", "Report"),
		ESSAY("24","1", "Essay");

		private final String sn;
		private final String ver;
		private final String name;

		ProtocolName(String sn, String ver, String name) {
			this.sn = sn;
			this.ver = ver;
			this.name = name;
		}

		public Feip getFeip() {
			return new Feip(getSn(), getVer(), getName());
		}

		public String getSn() {
			return sn;
		}

		public String getVer() {
			return ver;
		}

		public String getName() {
			return name;
		}
	}

	public static void main(String[] args) {
		Feip feip = Feip.fromProtocolName(ProtocolName.APP);
		System.out.println(feip.toJson());

		Feip feip2 = Feip.fromName("APP");
		System.out.println(feip2.toJson());

		Feip feip3 = Feip.fromProtocolName(ProtocolName.TEAM);
		System.out.println(feip3.toJson());

		Feip feip4 = Feip.fromName("team");
		System.out.println(feip4.toJson());
	}

	public static Feip fromProtocolName(ProtocolName protocolName) {
		return new Feip(protocolName.getSn(), protocolName.getVer(), protocolName.getName());
	}

	public static Feip fromName(String name) {
		ProtocolName protocolName = ProtocolName.valueOf(name.toUpperCase());
		return new Feip(protocolName.getSn(), protocolName.getVer(), protocolName.getName());
	}

	public String toJson(){
		return JsonUtils.toJson(this);
	}

	public String toNiceJson(){
		return JsonUtils.toNiceJson(this);
	}

	public String getDid() {
		return did;
	}

	public void setDid(String did) {
		this.did = did;
	}

	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public String getSn() {
		return sn;
	}
	public void setSn(String sn) {
		this.sn = sn;
	}
	public String getVer() {
		return ver;
	}
	public void setVer(String ver) {
		this.ver = ver;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getPid() {
		return pid;
	}
	public void setPid(String pid) {
		this.pid = pid;
	}
	public Object getData() {
		return data;
	}
	public void setData(Object data) {
		this.data = data;
	}

	
}
