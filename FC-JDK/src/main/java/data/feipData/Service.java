package data.feipData;

import com.google.gson.Gson;
import constants.Strings;
import data.fcData.FcObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Service extends FcObject {
	private static final Logger log = LoggerFactory.getLogger(Service.class);
//sid
	protected String stdName;
	protected Map<String, String> localNames;
	protected String desc;
	protected String type;
	protected List<String> components;
	protected String ver;
	protected String dealer;
	protected String dealerPubkey;
	protected Map<String, String> home;
	protected List<String> waiters;
	protected List<String> protocols;
	protected List<String> codes;
	protected List<String> services;
	private Object params;
	protected String owner;

	// Pricing and service configuration fields (moved from Params)
	protected String pricePerKB;
	protected String pricePerKBIn;   // Price for incoming data (requests) - FCH per KB
	protected String pricePerKBOut;  // Price for outgoing data (responses) - FCH per KB
	protected String pricePerKBDay;  // Price for storage - FCH per KB per day
	protected String minPayment;
	protected String pricePerRequest;
	protected String sessionDays;
	protected String consumeViaShare;
	protected String orderViaShare;
	protected String currency;
	protected String minCredit;
	protected String maxDataSize;
	private String dataExpiresInDays;
	
	protected Long birthTime;
	protected Long birthHeight;
	protected String lastTxId;
	protected Long lastTime;
	protected Long lastHeight;
	protected Long tCdd;
	protected Float tRate;
	protected Boolean active;
	protected Boolean closed;
	protected String closeStatement;


	public String toJson() {
		Gson gson = new Gson();
		return gson.toJson(this);
	}

	public String getApiUrl() {
		return home != null ? home.get(Strings.API) : null;
	}

	public void setApiUrl(String apiUrl) {
		if (home == null) home = new HashMap<>();
		if (apiUrl != null) {
			home.put(Strings.API, apiUrl);
		}
	}

	public String getOrgUrl() {
		return home != null ? home.get("org") : null;
	}

	public void setOrgUrl(String orgUrl) {
		if (home == null) home = new HashMap<>();
		if (orgUrl != null) {
			home.put("org", orgUrl);
		}
	}

	public String getDocUrl() {
		return home != null ? home.get("doc") : null;
	}

	public void setDocUrl(String docUrl) {
		if (home == null) home = new HashMap<>();
		if (docUrl != null) {
			home.put("doc", docUrl);
		}
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

	public ServiceType fetchServiceType() {
		if (type == null) return null;
		ServiceType result = ServiceType.fromString(type);
		if (result == null) {
			log.warn("Unknown ServiceType: '{}'. Known types: {}", type, java.util.Arrays.toString(ServiceType.values()));
		}
		return result;
	}

	public void makeServiceType(ServiceType type) {
		if (type != null) {
			String typeStr = type.name();
			int underscoreIndex = typeStr.indexOf('_');
			if (underscoreIndex >= 0) {
				typeStr = typeStr.substring(0, underscoreIndex) + "@" + typeStr.substring(underscoreIndex + 1);
			}
			this.type = typeStr;
			return;
		}
		this.type = null;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
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

	public List<String> getCodes() {
		return codes;
	}

	public void setCodes(List<String> codes) {
		this.codes = codes;
	}

	public List<String> getServices() {
		return services;
	}

	public void setServices(List<String> services) {
		this.services = services;
	}

	public Object getParams() {
		return params;
	}

	public void setParams(Object params) {
		this.params = params;
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
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

	public String getLastTxId() {
		return lastTxId;
	}

	public void setLastTxId(String lastTxId) {
		this.lastTxId = lastTxId;
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

	public Long gettCdd() {
		return tCdd;
	}

	public void settCdd(Long tCdd) {
		this.tCdd = tCdd;
	}

	public Float gettRate() {
		return tRate;
	}

	public void settRate(Float tRate) {
		this.tRate = tRate;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	public Boolean isClosed() {
		return closed;
	}
	public void setClosed(Boolean closed) {
		this.closed = closed;
	}
	public String getCloseStatement() {
		return closeStatement;
	}
	public void setCloseStatement(String closeStatement) {
		this.closeStatement = closeStatement;
	}

	public String getVer() {
		return ver;
	}

	public void setVer(String ver) {
		this.ver = ver;
	}

	public Boolean getActive() {
		return active;
	}

	public Boolean getClosed() {
		return closed;
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

	public String getDealerPubkey() {
		return dealerPubkey;
	}

	public void setDealerPubkey(String dealerPubkey) {
		this.dealerPubkey = dealerPubkey;
	}

	public String getDealer() {
		return dealer;
	}

	public void setDealer(String dealer) {
		this.dealer = dealer;
	}

	public List<String> getComponents() {
		return components;
	}

	public void setComponents(List<String> components) {
		this.components = components;
	}

	public String getDataExpiresInDays() {
		return dataExpiresInDays;
	}

	public void setDataExpiresInDays(String dataExpiresInDays) {
		this.dataExpiresInDays = dataExpiresInDays;
	}
}
