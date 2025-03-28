package feip.feipData;

import appTools.Inputer;
import clients.ApipClient;
import constants.FieldNames;
import constants.OpNames;
import constants.Values;
import feip.FeipOp;

import java.io.BufferedReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class ServiceOpData {
	private String sid;
	private List<String> sids;
	private String op;
	private String stdName;
	private String[] localNames;
	private String desc;
	private String ver;
	private String[] types;
	private String[] urls;
	private String[] waiters;
	private String[] protocols;
	private String[] codes;
	private Object params;
	private Integer rate;
	private String closeStatement;
	private String[] services;

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
		OP_FIELDS.put(Op.PUBLISH.toLowerCase(), new String[]{FieldNames.STD_NAME, FieldNames.LOCAL_NAMES, Values.DESC, FieldNames.VER, FieldNames.URLS, FieldNames.WAITERS, FieldNames.PROTOCOLS, FieldNames.CODES, FieldNames.SERVICES, FieldNames.PARAMS});
		OP_FIELDS.put(Op.UPDATE.toLowerCase(), new String[]{FieldNames.SID, FieldNames.STD_NAME, FieldNames.LOCAL_NAMES, Values.DESC, FieldNames.VER, FieldNames.URLS, FieldNames.WAITERS, FieldNames.PROTOCOLS, FieldNames.CODES, FieldNames.SERVICES, FieldNames.PARAMS});
		OP_FIELDS.put(Op.STOP.toLowerCase(), new String[]{FieldNames.SIDS});
		OP_FIELDS.put(Op.CLOSE.toLowerCase(), new String[]{FieldNames.SIDS, FieldNames.CLOSE_STATEMENT});
		OP_FIELDS.put(Op.RECOVER.toLowerCase(), new String[]{FieldNames.SIDS});
		OP_FIELDS.put(Op.RATE.toLowerCase(), new String[]{FieldNames.SID, FieldNames.RATE});
	}

	public void inputServiceHead(BufferedReader br,byte[] symKey,ApipClient apipClient)  {

		inputStdName(br);

		inputLocalNames(br);

		inputDesc(br);
		inputVer(br);
		inputUrls(br);

		inputWaiters(br,symKey,apipClient);

		inputProtocols(br);
		inputCodes(br);
		inputServices(br);

	}

	public void inputServiceHead(BufferedReader br)  {

		inputStdName(br);

		inputLocalNames(br);

		inputDesc(br);

		inputUrls(br);
		inputVer(br);
		inputWaiters(br);

		inputProtocols(br);
		inputCodes(br);
		inputServices(br);

	}
	private void inputVer(BufferedReader br){
		String ask;
		ask = "Input the version of your service, if you want. Enter to end :";
		String ver = Inputer.inputString(br,ask);
		if(!"".equals(ver)) setVer(ver);
	}
	private void inputWaiters(BufferedReader br, byte[] symKey, ApipClient apipClient) {
		if(Inputer.askIfYes(br,"Input the FIDs of the waiters for your service?")) {
			String[] waiters = fch.Inputer.inputOrCreateFidArray(br,symKey,apipClient);
			if(waiters.length!=0) setWaiters(waiters);
		}
	}

	public void updateServiceHead(BufferedReader br,byte[] symKey, ApipClient apipClient) {
		updateStdName(br);

		updateLocalNames(br);

		updateDesc(br);

		updateVer(br);

		updateUrls(br);

		updateWaiters(br,symKey,apipClient);

		updateProtocols(br);
		updateServices(br);
		updateCodes(br);
	}

	private void updateWaiters(BufferedReader br, byte[]symKey, ApipClient apipClient) {
		System.out.println("Waiters are: "+ Arrays.toString(waiters));
		inputWaiters(br,symKey,apipClient);
	}

	private void updateLocalNames(BufferedReader br) {
		System.out.println("LocalNames are: "+ Arrays.toString(localNames));
		inputLocalNames(br);
	}

	private void updateDesc(BufferedReader br) {
		System.out.println("Desc is: "+desc);
		inputDesc(br);
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
		inputUrls(br);

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

	public void inputTypes(BufferedReader br)  {
		String ask = "Input the types of your service if you want. Enter to end :";
		String[] types = Inputer.inputStringArray(br,ask,0);
		if(types.length!=0) setTypes(types);
	}

	public void updateTypes(BufferedReader br)  {
		System.out.println("Types are: "+ Arrays.toString(types));
		inputTypes(br);
	}

	private void inputStdName(BufferedReader br) {
		System.out.println("Input the English name of your service. Enter to ignore:");
		String input = Inputer.inputString(br);
		if(!"".equals(input))setStdName(input);
	}

	private void inputLocalNames(BufferedReader br)  {
		String ask = "Input the local names of your service, if you want. Enter to ignore:";
		String[] localNames = Inputer.inputStringArray(br,ask,0);
		if(localNames.length!=0) setLocalNames(localNames);
	}

	private void inputDesc(BufferedReader br)  {
		System.out.println("Input the description of your service if you want.Enter to ignore:");
		String str = Inputer.inputString(br);
		if(!str.equals("")) setDesc(str);
	}

	private void inputUrls(BufferedReader br){
		String ask;
		ask = "Input the URLs of your service, if you want. Enter to end:";
		String[] urls = Inputer.inputStringArray(br,ask,0);
		if(urls.length!=0) setUrls(urls);
	}

	private void inputWaiters(BufferedReader br) {
		String ask;
		ask = "Input the FCH address of the waiter for your service if you want. Enter to end:";
		String[] waiters = Inputer.inputStringArray(br,ask,0);
		if(waiters.length!=0) setWaiters(waiters);
	}

	private void inputProtocols(BufferedReader br) {
		String ask;
		ask = "Input the PIDs of the protocols your service using if you want. Enter to end :";
		String[] protocols = Inputer.inputStringArray(br,ask,64);
		if(protocols.length!=0) setProtocols(protocols);
	}
	private void inputCodes(BufferedReader br) {
		String ask;
		ask = "Input the codeIDs of the codes your service using if you want. Enter to end:";
		String[] codes = Inputer.inputStringArray(br,ask,64);
		if(codes.length!=0) setCodes(codes);
	}

	private void inputServices(BufferedReader br) {
		String ask;
		ask = "Input the SIDs of the services your service using if you want. Enter to end:";
		String[] services = Inputer.inputStringArray(br,ask,64);
		if(services.length!=0) setServices(services);
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

	public String[] getTypes() {
		return types;
	}

	public void setTypes(String[] types) {
		this.types = types;
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


	public void updateServiceHead(BufferedReader br) {
		updateStdName(br);

		updateLocalNames(br);

		updateDesc(br);

		updateUrls(br);

		updateWaiters(br);

		updateProtocols(br);
		updateCodes(br);
		updateServices(br);
	}

	private void updateCodes(BufferedReader br) {
		System.out.println("Codes are: "+ Arrays.toString(codes));
		inputCodes(br);
	}
	private void updateServices(BufferedReader br) {
		System.out.println("Services are: "+ Arrays.toString(services));
		inputServices(br);
	}

	private void updateProtocols(BufferedReader br) {
		System.out.println("Protocols are: "+ Arrays.toString(protocols));
		inputProtocols(br);
	}

	private void updateWaiters(BufferedReader br) {
		System.out.println("Waiters are: "+ Arrays.toString(waiters));
		inputWaiters(br);
	}

	private void updateUrls(BufferedReader br) {
		System.out.println("Urls are: "+ Arrays.toString(urls));
		inputUrls(br);
	}

	private void updateStdName(BufferedReader br) {
		System.out.println("StdName is: "+stdName);
		inputStdName(br);
	}

	public String[] getServices() {
		return services;
	}

	public void setServices(String[] services) {
		this.services = services;
	}

	public String getVer() {
		return ver;
	}

	public void setVer(String ver) {
		this.ver = ver;
	}

	public List<String> getSids() {
		return sids;
	}

	public void setSids(List<String> sids) {
		this.sids = sids;
	}

	public static ServiceOpData makePublish(String stdName, String[] localNames, String desc,
                                            String ver, String[] urls, String[] waiters, String[] protocols,
                                            String[] codes, String[] services, Object params) {
		ServiceOpData data = new ServiceOpData();
		data.setOp(Op.PUBLISH.toLowerCase());
		data.setStdName(stdName);
		data.setLocalNames(localNames);
		data.setDesc(desc);
		data.setVer(ver);
		data.setUrls(urls);
		data.setWaiters(waiters);
		data.setProtocols(protocols);
		data.setCodes(codes);
		data.setServices(services);
		data.setParams(params);
		return data;
	}

	public static ServiceOpData makeUpdate(String sid, String stdName, String[] localNames,
                                           String desc, String ver, String[] urls, String[] waiters, String[] protocols,
                                           String[] codes, String[] services, Object params) {
		ServiceOpData data = new ServiceOpData();
		data.setOp(Op.UPDATE.toLowerCase());
		data.setSid(sid);
		data.setStdName(stdName);
		data.setLocalNames(localNames);
		data.setDesc(desc);
		data.setVer(ver);
		data.setUrls(urls);
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
}
