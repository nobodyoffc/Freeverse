package feip.feipData;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import configure.ServiceType;
import feip.feipData.serviceParams.Params;
import javaTools.StringTools;
import org.jetbrains.annotations.NotNull;
import appTools.Settings;

import java.util.Map;

import static constants.FieldNames.*;
import static constants.FieldNames.BIRTH_HEIGHT;
import static constants.FieldNames.DESC;
import static constants.FieldNames.SID;
import static constants.FieldNames.TYPES;
import static constants.Strings.*;

public class Service {

	protected String sid;
	protected String stdName;
	protected String[] localNames;
	protected String desc;
	protected String[] types;
	protected String ver;
	protected String[] urls;
	protected String[] waiters;
	protected String[] protocols;
	protected String[] codes;
	protected String[] services;
	private Object params;
	protected String owner;
	
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
	public String toNiceJson() {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		return gson.toJson(this);
	}
	public static Service fromMap(Map<String, String> map, Class<? extends Params> paramsClass) {
		Service service = new Service();

		service.sid = map.get(SID);
		service.stdName = map.get(STD_NAME);
		service.localNames = StringTools.splitString(map.get(LOCAL_NAMES));
		service.desc = map.get(DESC);
		service.ver = map.get(VER);
		service.types = StringTools.splitString(map.get(TYPES));
		service.urls = StringTools.splitString(map.get(URLS));
		service.waiters = StringTools.splitString(map.get(WAITERS));
		service.protocols = StringTools.splitString(map.get(PROTOCOLS));
		service.services = StringTools.splitString(map.get(SERVICES));
		service.codes = StringTools.splitString(map.get(CODES));

		service.owner = map.get(OWNER);

		if(map.get(BIRTH_TIME)!=null)service.birthTime = StringTools.parseLong(map.get(BIRTH_TIME));
		if(map.get(BIRTH_HEIGHT)!=null)service.birthHeight = StringTools.parseLong(map.get(BIRTH_HEIGHT));
		service.lastTxId = map.get(LAST_TX_ID);
		if(map.get(LAST_TIME)!=null)service.lastTime = StringTools.parseLong(map.get(LAST_TIME));
		if(map.get(LAST_HEIGHT)!=null)service.lastHeight = StringTools.parseLong(map.get(LAST_HEIGHT));
		if(map.get(T_CDD)!=null)service.tCdd = StringTools.parseLong(map.get(T_CDD));
		if(map.get(T_RATE)!=null)service.tRate = StringTools.parseFloat(map.get(T_RATE));
		service.active = StringTools.parseBoolean(map.get(ACTIVE));
		service.closed = StringTools.parseBoolean(map.get(CLOSED));
		if(map.get(CLOSE_STATEMENT)!=null)service.closeStatement = map.get(CLOSE_STATEMENT);

		service.params = new Gson().fromJson(map.get(PARAMS),paramsClass);

		return service;
	}

    @NotNull
    public static String makeServerDataDir(String sid, ServiceType serviceType) {
        return System.getProperty("user.home") + "/" + Settings.addSidBriefToName(sid, serviceType.name().toLowerCase() + "_data");
    }

    public String[] getServices() {
		return services;
	}

	public void setServices(String[] services) {
		this.services = services;
	}
	
	public String getSid() {
		return sid;
	}
	public void setSid(String sid) {
		this.sid = sid;
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
	public String getOwner() {
		return owner;
	}
	public void setOwner(String signer) {
		this.owner = signer;
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
	public Boolean isActive() {
		return active;
	}
	public void setActive(Boolean active) {
		this.active = active;
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
	public String[] getCodes() {
		return codes;
	}
	public void setCodes(String[] codes) {
		this.codes = codes;
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
}
