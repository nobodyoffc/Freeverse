package data.feipData;

import core.crypto.KeyTools;
import ui.Inputer;
import clients.ApipClient;
import constants.FieldNames;
import constants.OpNames;
import constants.Strings;
import constants.Values;
import utils.StringUtils;

import utils.FchUtils;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceOpData {
	private String sid;
	private List<String> sids;
	private String op;
	private String stdName;
	private Map<String, String> localNames;
	private String desc;
	private String ver;
	protected String dealer;
	protected String dealerPubkey;
	private String type;
	private List<String> components;
	private Map<String, String> home;
	private List<String> waiters;
	private List<String> protocols;
	private List<String> codes;
	private Object params;
	private Integer rate;
	private String closeStatement;
	private List<String> services;

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

	public static void serviceToServiceData(Service service, ServiceOpData data) {
		data.setType(service.getType());
		data.setSid(service.getId());
		data.setHome(service.getHome());
		data.setStdName(service.getStdName());
		data.setLocalNames(service.getLocalNames());
		data.setProtocols(service.getProtocols());
		data.setDesc(service.getDesc());
		data.setWaiters(service.getWaiters());
		data.setServices(service.getServices());
		data.setCodes(service.getCodes());
		data.setParams(service.getParams());
		// Pricing fields
		data.setPricePerKB(service.getPricePerKB());
		data.setPricePerKBIn(service.getPricePerKBIn());
		data.setPricePerKBOut(service.getPricePerKBOut());
		data.setPricePerKBDay(service.getPricePerKBDay());
		data.setMinPayment(service.getMinPayment());
		data.setPricePerRequest(service.getPricePerRequest());
		data.setSessionDays(service.getSessionDays());
		data.setConsumeViaShare(service.getConsumeViaShare());
		data.setOrderViaShare(service.getOrderViaShare());
		data.setCurrency(service.getCurrency());
		data.setMinCredit(service.getMinCredit());
		data.setMaxDataSize(service.getMaxDataSize());
		data.setDataExpiresInDays(service.getDataExpiresInDays());
	}

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
		OP_FIELDS.put(Op.PUBLISH.toLowerCase(), new String[]{FieldNames.STD_NAME, FieldNames.LOCAL_NAMES, Values.DESC, FieldNames.VER, FieldNames.DEALER,FieldNames.DEALER_PUBKEY,FieldNames.HOME, FieldNames.WAITERS, FieldNames.PROTOCOLS, FieldNames.CODES, FieldNames.SERVICES, FieldNames.PARAMS});
		OP_FIELDS.put(Op.UPDATE.toLowerCase(), new String[]{FieldNames.SID, FieldNames.STD_NAME, FieldNames.LOCAL_NAMES, Values.DESC, FieldNames.VER,FieldNames.DEALER,FieldNames.DEALER_PUBKEY, FieldNames.HOME, FieldNames.WAITERS, FieldNames.PROTOCOLS, FieldNames.CODES, FieldNames.SERVICES, FieldNames.PARAMS});
		OP_FIELDS.put(Op.STOP.toLowerCase(), new String[]{FieldNames.SIDS});
		OP_FIELDS.put(Op.CLOSE.toLowerCase(), new String[]{FieldNames.SIDS, FieldNames.CLOSE_STATEMENT});
		OP_FIELDS.put(Op.RECOVER.toLowerCase(), new String[]{FieldNames.SIDS});
		OP_FIELDS.put(Op.RATE.toLowerCase(), new String[]{FieldNames.SID, FieldNames.RATE});
	}

	public void inputService(BufferedReader br)  {

		inputType(br);
		inputDealerPubkey(br);
		inputStdName(br);

		inputLocalNames(br);
		inputComponents(br);
		inputDesc(br);
		inputVer(br);
		inputHome(br);

		inputWaiters(br);

		inputProtocols(br);
		inputCodes(br);
		inputServices(br);
		inputPricingFields(br);
	}

	private void inputVer(BufferedReader br){
		String ask;
		ask = "Input the version of your service, if you want. Enter to end :";
		String ver = Inputer.inputString(br,ask);
		if(!"".equals(ver)) setVer(ver);
	}

	public void updateService(BufferedReader br, byte[] symKey, ApipClient apipClient) {
		updateStdName(br);

		updateLocalNames(br);

		updateDesc(br);
		updateType(br);
		updateComponents(br);
		updateVer(br);

		updateHome(br);
		updateDealerPubkey(br);

		updateWaiters(br,symKey,apipClient);

		updateProtocols(br);
		updateServices(br);
		updateCodes(br);
	}

	private void updateWaiters(BufferedReader br, byte[]symKey, ApipClient apipClient) {
		System.out.println("Waiters are: "+ StringUtils.listToString(waiters));
		inputWaiters(br);
	}

	private void updateLocalNames(BufferedReader br) {
		System.out.println("LocalNames are: "+StringUtils.mapToString(localNames));
		inputLocalNames(br);
	}

	private void updateComponents(BufferedReader br) {
		System.out.println("components are: "+StringUtils.listToString(components));
		inputComponents(br);
	}

	private void updateDesc(BufferedReader br) {
		System.out.println("Desc is: "+desc);
		inputDesc(br);
	}

	private void updateDealerPubkey(BufferedReader br) {
		System.out.println("Dealer is: "+ this.dealer);
		inputDealerPubkey(br);
	}

	private void updateVer(BufferedReader br) {
		System.out.println("The version is: "+ver);
		inputVer(br);
	}

	public void inputServicePublish(BufferedReader br)  {

		inputStdName(br);

		inputLocalNames(br);

		inputDesc(br);
		inputVer(br);
		inputHome(br);

		inputWaiters(br);

		inputProtocols(br);
		inputCodes(br);
		inputServices(br);

	}

	public void inputOp(BufferedReader br)  {
		System.out.println("Input the operation you want to do:");
		while (true) {
			String input = Inputer.inputString(br);
			if(OpNames.contains(input)) {
				setStdName(input);
				break;
			}else{
				System.out.println("It should be one of "+OpNames.showAll());
			}
		}
	}

	public void inputType(BufferedReader br)  {
		String ask = "Input the type of your service if you want. Enter to ignore :";
		String typeStr = Inputer.inputString(br, ask);
		if(typeStr!=null) setType(typeStr);
	}

	public void updateType(BufferedReader br)  {
		System.out.println("Type are: "+ type);
		inputType(br);
	}

	public void inputDealerPubkey(BufferedReader br) {
		System.out.println("Input the pubkey of the dealer. Enter to ignore:");
		String dealer;
		while(true) {
			String input = Inputer.inputString(br);
			if (!"".equals(input)) {
				try {
					dealer = KeyTools.pubkeyToFchAddr(input);
				} catch (Exception ignore) {
					System.out.println("It is not a pubkey. Try again.");
					continue;
				}
				setDealerPubkey(input);
				setDealer(dealer);
				break;
			}
		}
	}

	private void inputStdName(BufferedReader br) {
		System.out.println("Input the English name of your service. Enter to ignore:");
		String input = Inputer.inputString(br);
		if(!"".equals(input))setStdName(input);
	}

	private void inputLocalNames(BufferedReader br)  {
		Map<String, String> names = Inputer.inputStringStringMap(br,
				"Locale or language tag (e.g. zh-CN), enter empty line to end:",
				"Display name for that locale:");
		if (!names.isEmpty()) setLocalNames(names);
	}

	private void inputComponents(BufferedReader br)  {
		String ask = "Input the component names of your service one by one, if you want. Enter to ignore:";
		List<String> components = Inputer.inputStringList(br,ask,0);
		if(!components.isEmpty()) setComponents(components);
	}

	private void inputDesc(BufferedReader br)  {
		System.out.println("Input the description of your service if you want.Enter to ignore:");
		String str = Inputer.inputString(br);
		if(!str.equals("")) setDesc(str);
	}

	private void inputHome(BufferedReader br){
		if (home == null) home = new HashMap<>();

		System.out.println("Input the API URL of your service. Enter to skip:");
		String apiUrl = Inputer.inputString(br);
		if(!apiUrl.isEmpty()) home.put(Strings.API, apiUrl);

		System.out.println("Input the organization URL of your service. Enter to skip:");
		String orgUrl = Inputer.inputString(br);
		if(!orgUrl.isEmpty()) home.put("org", orgUrl);
	}

	private void inputWaiters(BufferedReader br) {
		String ask;
		ask = "Input the FCH address of the waiter for your service if you want. Enter to end:";
		List<String> waiters = Inputer.inputStringList(br,ask,0);
		if(!waiters.isEmpty()) setWaiters(waiters);
	}

	private void inputProtocols(BufferedReader br) {
		String ask;
		ask = "Input the PIDs of the protocols your service using if you want. Enter to end :";
		List<String> protocols = Inputer.inputStringList(br,ask,64);
		if(!protocols.isEmpty()) setProtocols(protocols);
	}

	private void inputCodes(BufferedReader br) {
		String ask;
		ask = "Input the codeIDs of the codes your service using if you want. Enter to end:";
		List<String> codes = Inputer.inputStringList(br,ask,64);
		if(!codes.isEmpty()) setCodes(codes);
	}

	private void inputServices(BufferedReader br) {
		String ask;
		ask = "Input the SIDs of the services your service using if you want. Enter to end:";
		List<String> services = Inputer.inputStringList(br,ask,64);
		if(!services.isEmpty()) setServices(services);
	}

	public void inputPricingFields(BufferedReader br) {
		System.out.println("Input the minimum payment (in satoshi). Enter to skip:");
		String input = Inputer.inputString(br);
		if(!input.isEmpty()) setMinPayment(satoshiInputToFch(input));

		System.out.println("Input the price per KB input (in satoshi). Enter to skip:");
		input = Inputer.inputString(br);
		if(!input.isEmpty()) setPricePerKBIn(satoshiInputToFch(input));

		System.out.println("Input the price per KB out (in satoshi). Enter to skip:");
		input = Inputer.inputString(br);
		if(!input.isEmpty()) setPricePerKBOut(satoshiInputToFch(input));

		System.out.println("Input the price per KB day (in satoshi). Enter to skip:");
		input = Inputer.inputString(br);
		if(!input.isEmpty()) setPricePerKBDay(satoshiInputToFch(input));

		System.out.println("Input the consume via share (e.g. 0.5 for 50%). Enter to skip:");
		input = Inputer.inputString(br);
		if(!input.isEmpty()) setConsumeViaShare(input);

		System.out.println("Input the order via share (e.g. 0.5 for 50%). Enter to skip:");
		input = Inputer.inputString(br);
		if(!input.isEmpty()) setOrderViaShare(input);

		System.out.println("Input the currency (e.g. fch). Enter to skip:");
		input = Inputer.inputString(br);
		if(!input.isEmpty()) setCurrency(input);

		System.out.println("Input the minimum credit (in FCH, e.g. 0.0001). Enter to skip:");
		input = Inputer.inputString(br);
		if(!input.isEmpty()) setMinCredit(input);

		System.out.println("Input the maximum data size (e.g. in Bytes). Enter to skip:");
		input = Inputer.inputString(br);
		if(!input.isEmpty()) setMaxDataSize(input);

		System.out.println("Input the days of data expire (e.g. days). Enter to skip:");
		input = Inputer.inputString(br);
		if(!input.isEmpty()) setDataExpiresInDays(input);
	}

	public void updatePricingFields(BufferedReader br) {
		System.out.println("Price per KB is: " + fchToSatoshiDisplay(pricePerKB) + " satoshi (" + pricePerKB + " FCH)");
		System.out.println("Input new value (in satoshi). Enter to keep current:");
		String input = Inputer.inputString(br);
		if(!input.isEmpty()) setPricePerKB(satoshiInputToFch(input));

		System.out.println("Price per KB input is: " + fchToSatoshiDisplay(pricePerKBIn) + " satoshi (" + pricePerKBIn + " FCH)");
		System.out.println("Input new value (in satoshi). Enter to keep current:");
		input = Inputer.inputString(br);
		if(!input.isEmpty()) setPricePerKBIn(satoshiInputToFch(input));

		System.out.println("Price per KB out is: " + fchToSatoshiDisplay(pricePerKBOut) + " satoshi (" + pricePerKBOut + " FCH)");
		System.out.println("Input new value (in satoshi). Enter to keep current:");
		input = Inputer.inputString(br);
		if(!input.isEmpty()) setPricePerKBOut(satoshiInputToFch(input));

		System.out.println("Price per KB day is: " + fchToSatoshiDisplay(pricePerKBDay) + " satoshi (" + pricePerKBDay + " FCH)");
		System.out.println("Input new value (in satoshi). Enter to keep current:");
		input = Inputer.inputString(br);
		if(!input.isEmpty()) setPricePerKBDay(satoshiInputToFch(input));

		System.out.println("Minimum payment is: " + fchToSatoshiDisplay(minPayment) + " satoshi (" + minPayment + " FCH)");
		System.out.println("Input new value (in satoshi). Enter to keep current:");
		input = Inputer.inputString(br);
		if(!input.isEmpty()) setMinPayment(satoshiInputToFch(input));

		System.out.println("Consume via share is: " + consumeViaShare);
		System.out.println("Input new value. Enter to keep current:");
		input = Inputer.inputString(br);
		if(!input.isEmpty()) setConsumeViaShare(input);

		System.out.println("Order via share is: " + orderViaShare);
		System.out.println("Input new value. Enter to keep current:");
		input = Inputer.inputString(br);
		if(!input.isEmpty()) setOrderViaShare(input);

		System.out.println("Currency is: " + currency);
		System.out.println("Input new value. Enter to keep current:");
		input = Inputer.inputString(br);
		if(!input.isEmpty()) setCurrency(input);

		System.out.println("Min credit is: " + minCredit + " FCH");
		System.out.println("Input new value (in FCH). Enter to keep current:");
		input = Inputer.inputString(br);
		if(!input.isEmpty()) setMinCredit(input);

		System.out.println("Max data size is: " + maxDataSize);
		System.out.println("Input new value. Enter to keep current:");
		input = Inputer.inputString(br);
		if(!input.isEmpty()) setMaxDataSize(input);

		System.out.println("Data expires in days: " + dataExpiresInDays);
		System.out.println("Input new value. Enter to keep current:");
		input = Inputer.inputString(br);
		if(!input.isEmpty()) setDataExpiresInDays(input);
	}

	/**
	 * Convert user input in satoshi to FCH string for on-chain storage.
	 * Returns the original input unchanged if parsing fails.
	 */
	private static String satoshiInputToFch(String satoshiInput) {
		try {
			long satoshi = Long.parseLong(satoshiInput.trim());
			return FchUtils.satoshiToCoinStr(satoshi);
		} catch (NumberFormatException e) {
			return satoshiInput;
		}
	}

	/**
	 * Display an FCH-denominated string as satoshi for the UI.
	 * Returns "N/A" if the value is null or unparseable.
	 */
	private static String fchToSatoshiDisplay(String fchValue) {
		if (fchValue == null || fchValue.isEmpty()) return "N/A";
		try {
			Long satoshi = FchUtils.coinStrToSatoshi(fchValue);
			return satoshi != null ? String.valueOf(satoshi) : "N/A";
		} catch (Exception e) {
			return "N/A";
		}
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

	public String getStdName() {
		return stdName;
	}

	public void setStdName(String stdName) {
		this.stdName = stdName;
	}

	public Map<String, String> getLocalNames() {
		return localNames;
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


	public Object getParams() {
		return params;
	}

	public void setParams(Object params) {
		this.params = params;
	}

	public Integer getRate() {
		return rate;
	}

	public void setRate(Integer rate) {
		this.rate = rate;
	}

	public String getCloseStatement() {
		return closeStatement;
	}

	public void setCloseStatement(String closeStatement) {
		this.closeStatement = closeStatement;
	}


	public void updateService(BufferedReader br) {
		updateStdName(br);

		updateLocalNames(br);

		updateDesc(br);

		updateHome(br);

		updateWaiters(br);

		updateProtocols(br);
		updateCodes(br);
		updateServices(br);
	}

	private void updateCodes(BufferedReader br) {
		System.out.println("Codes are: "+ StringUtils.listToString(codes));
		inputCodes(br);
	}

	private void updateServices(BufferedReader br) {
		System.out.println("Services are: "+ StringUtils.listToString(services));
		inputServices(br);
	}

	private void updateProtocols(BufferedReader br) {
		System.out.println("Protocols are: "+ StringUtils.listToString(protocols));
		inputProtocols(br);
	}

	private void updateWaiters(BufferedReader br) {
		System.out.println("Waiters are: "+ StringUtils.listToString(waiters));
		inputWaiters(br);
	}

	private void updateHome(BufferedReader br) {
		System.out.println("Home is: "+ home);
		inputHome(br);
	}

	private void updateStdName(BufferedReader br) {
		System.out.println("StdName is: "+stdName);
		inputStdName(br);
	}

	public String getVer() {
		return ver;
	}

	public void setVer(String ver) {
		this.ver = ver;
	}


	public static ServiceOpData makePublish(String stdName, Map<String, String> localNames, String desc,
                                            String ver, Map<String, String> home, List<String> waiters, List<String> protocols,
                                            List<String> codes, List<String> services, Object params) {
		ServiceOpData data = new ServiceOpData();
		data.setOp(Op.PUBLISH.toLowerCase());
		data.setStdName(stdName);
		data.setLocalNames(localNames);
		data.setDesc(desc);
		data.setVer(ver);
		data.setHome(home);
		data.setWaiters(waiters);
		data.setProtocols(protocols);
		data.setCodes(codes);
		data.setServices(services);
		data.setParams(params);
		return data;
	}

	public static ServiceOpData makeUpdate(String sid, String stdName, Map<String, String> localNames,
                                           String desc, String ver, Map<String, String> home, List<String> waiters, List<String> protocols,
                                           List<String> codes, List<String> services, Object params) {
		ServiceOpData data = new ServiceOpData();
		data.setOp(Op.UPDATE.toLowerCase());
		data.setSid(sid);
		data.setStdName(stdName);
		data.setLocalNames(localNames);
		data.setDesc(desc);
		data.setVer(ver);
		data.setHome(home);
		data.setWaiters(waiters);
		data.setProtocols(protocols);
		data.setCodes(codes);
		data.setServices(services);
		data.setParams(params);
		return data;
	}

	public static ServiceOpData makeStop(List<String> sids) {
		ServiceOpData data = new ServiceOpData();
		data.setOp(Op.STOP.toLowerCase());
		data.setSids(sids);
		return data;
	}

	public static ServiceOpData makeClose(List<String> sids, String closeStatement) {
		ServiceOpData data = new ServiceOpData();
		data.setOp(Op.CLOSE.toLowerCase());
		data.setSids(sids);
		data.setCloseStatement(closeStatement);
		return data;
	}

	public static ServiceOpData makeRecover(List<String> sids) {
		ServiceOpData data = new ServiceOpData();
		data.setOp(Op.RECOVER.toLowerCase());
		data.setSids(sids);
		return data;
	}

	public static ServiceOpData makeRate(String sid, Integer rate) {
		ServiceOpData data = new ServiceOpData();
		data.setOp(Op.RATE.toLowerCase());
		data.setSid(sid);
		data.setRate(rate);
		return data;
	}

	public List<String> getSids() {
		return sids;
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

	public void setSids(List<String> sids) {
		this.sids = sids;
	}

	public void setLocalNames(Map<String, String> localNames) {
		this.localNames = localNames;
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

	public String getDataExpiresInDays() {
		return dataExpiresInDays;
	}

	public void setDataExpiresInDays(String dataExpiresInDays) {
		this.dataExpiresInDays = dataExpiresInDays;
	}
}
