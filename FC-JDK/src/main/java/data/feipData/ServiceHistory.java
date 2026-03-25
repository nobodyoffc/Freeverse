package data.feipData;

import data.fcData.FcObject;

import java.util.List;
import java.util.Map;

public class ServiceHistory extends FcObject {

	private Long height;
	private Integer index;
	private Long time;
	private String signer;
	private String ver;

	protected String dealer;
	protected String dealerPubkey;
	
	private String stdName;
	private Map<String, String> localNames;
	private String desc;
	private String type;
	private List<String> components;
	private Map<String, String> home;
	private List<String> waiters;
	private List<String> protocols;
	private List<String> services;
	private List<String> codes;
	private Object params;
	private String closeStatement;

	// Pricing and service configuration fields (moved from Params)
	private String pricePerKB;
	private String pricePerKBIn;   // Price for incoming data (requests) - FCH per KB
	private String pricePerKBOut;  // Price for outgoing data (responses) - FCH per KB
	private String pricePerKBDay;  // Price for storage - FCH per KB per day
	private String minPayment;
	private String pricePerRequest;
	private String sessionDays;
	private String consumeViaShare;
	private String orderViaShare;
	private String currency;
	private String minCredit;
	private String maxDataSize;
	private String dataExpiresInDays;
	
	private String sid;
	private List<String> sids;
	private String op;
	private Integer rate;
	private Long cdd;

	public Long getHeight() {
		return height;
	}

	public void setHeight(Long height) {
		this.height = height;
	}

	public Integer getIndex() {
		return index;
	}

	public void setIndex(Integer index) {
		this.index = index;
	}

	public Long getTime() {
		return time;
	}

	public void setTime(Long time) {
		this.time = time;
	}

	public String getSigner() {
		return signer;
	}

	public void setSigner(String signer) {
		this.signer = signer;
	}

	public String getVer() {
		return ver;
	}

	public void setVer(String ver) {
		this.ver = ver;
	}

	public String getStdName() {
		return stdName;
	}

	public void setStdName(String stdName) {
		this.stdName = stdName;
	}


	public Object getParams() {
		return params;
	}

	public void setParams(Object params) {
		this.params = params;
	}

	public String getCloseStatement() {
		return closeStatement;
	}

	public void setCloseStatement(String closeStatement) {
		this.closeStatement = closeStatement;
	}

	public String getSid() {
		return sid;
	}

	public void setSid(String sid) {
		this.sid = sid;
	}

	public String getOp() {
		return op;
	}

	public void setOp(String op) {
		this.op = op;
	}

	public Integer getRate() {
		return rate;
	}

	public void setRate(Integer rate) {
		this.rate = rate;
	}

	public Long getCdd() {
		return cdd;
	}

	public void setCdd(Long cdd) {
		this.cdd = cdd;
	}

	public String getDealer() {
		return dealer;
	}

	public void setDealer(String dealer) {
		this.dealer = dealer;
	}

	public String getDealerPubkey() {
		return dealerPubkey;
	}

	public void setDealerPubkey(String dealerPubkey) {
		this.dealerPubkey = dealerPubkey;
	}

	// Pricing field getters and setters
	public String getPricePerKB() {
		return pricePerKB;
	}

	public void setPricePerKB(String pricePerKB) {
		this.pricePerKB = pricePerKB;
	}

	public String getPricePerKBIn() {
		return pricePerKBIn;
	}

	public void setPricePerKBIn(String pricePerKBIn) {
		this.pricePerKBIn = pricePerKBIn;
	}

	public String getPricePerKBOut() {
		return pricePerKBOut;
	}

	public void setPricePerKBOut(String pricePerKBOut) {
		this.pricePerKBOut = pricePerKBOut;
	}

	public String getPricePerKBDay() {
		return pricePerKBDay;
	}

	public void setPricePerKBDay(String pricePerKBDay) {
		this.pricePerKBDay = pricePerKBDay;
	}

	public String getMinPayment() {
		return minPayment;
	}

	public void setMinPayment(String minPayment) {
		this.minPayment = minPayment;
	}

	public String getPricePerRequest() {
		return pricePerRequest;
	}

	public void setPricePerRequest(String pricePerRequest) {
		this.pricePerRequest = pricePerRequest;
	}

	public String getSessionDays() {
		return sessionDays;
	}

	public void setSessionDays(String sessionDays) {
		this.sessionDays = sessionDays;
	}

	public String getConsumeViaShare() {
		return consumeViaShare;
	}

	public void setConsumeViaShare(String consumeViaShare) {
		this.consumeViaShare = consumeViaShare;
	}

	public String getOrderViaShare() {
		return orderViaShare;
	}

	public void setOrderViaShare(String orderViaShare) {
		this.orderViaShare = orderViaShare;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public String getMinCredit() {
		return minCredit;
	}

	public void setMinCredit(String minCredit) {
		this.minCredit = minCredit;
	}

	public String getMaxDataSize() {
		return maxDataSize;
	}

	public void setMaxDataSize(String maxDataSize) {
		this.maxDataSize = maxDataSize;
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

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public List<String> getComponents() {
		return components;
	}

	public void setComponents(List<String> components) {
		this.components = components;
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

	public List<String> getCodes() {
		return codes;
	}

	public void setCodes(List<String> codes) {
		this.codes = codes;
	}

	public List<String> getSids() {
		return sids;
	}

	public void setSids(List<String> sids) {
		this.sids = sids;
	}

	public String getDataExpiresInDays() {
		return dataExpiresInDays;
	}

	public void setDataExpiresInDays(String dataExpiresInDays) {
		this.dataExpiresInDays = dataExpiresInDays;
	}
}
