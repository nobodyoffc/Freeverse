package construct;

import co.elastic.clients.elasticsearch.core.BulkResponse;
import core.crypto.KeyTools;
import data.fcData.News;
import data.fchData.Freer;
import data.feipData.*;
import utils.EsUtils;
import utils.StringUtils;
import constants.IndicesNames;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;

import com.google.gson.Gson;
import data.fchData.OpReturn;
import startFEIP.StartFEIP;

import java.util.ArrayList;
import java.util.List;

import static constants.OpNames.*;
import static constants.Values.CREATED;
import static constants.Values.UPDATED;

public class ConstructParser {

	public ProtocolHistory makeProtocol(OpReturn opre, Feip feip) {

		Gson gson = new Gson();

		ProtocolOpData protocolRaw = new ProtocolOpData();
		try {
			protocolRaw = gson.fromJson(gson.toJson(feip.getData()), ProtocolOpData.class);
			if(protocolRaw==null){
				System.out.println("Protocol raw is null");
				return null;
			}
		}catch(Exception e) {
			System.out.println("Failed to parse protocol");
			return null;
		}

		ProtocolHistory protocolHist = new ProtocolHistory();

		if(protocolRaw.getOp()==null){
			System.out.println("OP is null");
			return null;
		}

		protocolHist.setOp(protocolRaw.getOp());

		switch(protocolRaw.getOp()) {

			case PUBLISH:
				if(protocolRaw.getSn()==null|| protocolRaw.getName()==null||"".equals(protocolRaw.getName())){
					System.out.println("Sn or name is null or empty");
					return null;
				}
				if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired){
					System.out.println("Height is greater than CddCheckHeight and Cdd is less than CddRequired");
					return null;
				}
				protocolHist.setId(opre.getId());

				protocolHist.setPid(opre.getId());
				protocolHist.setHeight(opre.getHeight());
				protocolHist.setIndex(opre.getTxIndex());
				protocolHist.setTime(opre.getTime());
				protocolHist.setSigner(opre.getSigner());

				if(protocolRaw.getType()!=null)protocolHist.setType(protocolRaw.getType());
				if(protocolRaw.getSn()!=null)protocolHist.setSn(protocolRaw.getSn());
				if(protocolRaw.getVer()!=null)protocolHist.setVer(protocolRaw.getVer());
				if(protocolRaw.getDid()!=null)protocolHist.setDid(protocolRaw.getDid());
				if(protocolRaw.getName()!=null)protocolHist.setName(protocolRaw.getName());
				if(protocolRaw.getDesc()!=null)protocolHist.setDesc(protocolRaw.getDesc());
				if(protocolRaw.getLang()!=null)protocolHist.setLang(protocolRaw.getLang());
				if(protocolRaw.getHome()!=null)protocolHist.setHome(protocolRaw.getHome());
				if(protocolRaw.getPreDid()!=null)protocolHist.setPrePid(protocolRaw.getPreDid());
				if(protocolRaw.getWaiters()!=null)protocolHist.setWaiters(protocolRaw.getWaiters());

				break;

			case UPDATE:

				if(protocolRaw.getPid()==null|| protocolRaw.getSn()==null|| protocolRaw.getName()==null||"".equals(protocolRaw.getName())){
					System.out.println("Pid or Sn or name is null or empty");
					return null;
				}
				protocolHist.setId(opre.getId());
				protocolHist.setHeight(opre.getHeight());
				protocolHist.setIndex(opre.getTxIndex());
				protocolHist.setTime(opre.getTime());
				protocolHist.setSigner(opre.getSigner());

				protocolHist.setPid(protocolRaw.getPid());

				if(protocolRaw.getType()!=null)protocolHist.setType(protocolRaw.getType());
				if(protocolRaw.getSn()!=null)protocolHist.setSn(protocolRaw.getSn());
				if(protocolRaw.getVer()!=null)protocolHist.setVer(protocolRaw.getVer());
				if(protocolRaw.getDid()!=null)protocolHist.setDid(protocolRaw.getDid());
				if(protocolRaw.getName()!=null)protocolHist.setName(protocolRaw.getName());
				if(protocolRaw.getDesc()!=null)protocolHist.setDesc(protocolRaw.getDesc());
				if(protocolRaw.getLang()!=null)protocolHist.setLang(protocolRaw.getLang());
				if(protocolRaw.getHome()!=null)protocolHist.setHome(protocolRaw.getHome());
				if(protocolRaw.getPreDid()!=null)protocolHist.setPrePid(protocolRaw.getPreDid());
				if(protocolRaw.getWaiters()!=null)protocolHist.setWaiters(protocolRaw.getWaiters());

				break;
			case STOP:
			case RECOVER:
			case CLOSE:
				if (protocolRaw.getPids() == null || protocolRaw.getPids().isEmpty()) {
					System.out.println("Pids is null or empty");
					return null;
				}
				protocolHist.setPids(protocolRaw.getPids());
				protocolHist.setId(opre.getId());
				protocolHist.setHeight(opre.getHeight());
				protocolHist.setIndex(opre.getTxIndex());
				protocolHist.setTime(opre.getTime());
				protocolHist.setSigner(opre.getSigner());

				if (protocolRaw.getOp().equals(CLOSE)) {
					protocolHist.setCloseStatement(protocolRaw.getCloseStatement());
				}
				break;

			case RATE:
				if(protocolRaw.getPid()==null){
					System.out.println("Pid is null");
					return null;
				}
				if (opre.getCdd() < StartFEIP.CddRequired){
					System.out.println("Cdd is less than CddRequired");
					return null;
				}
				protocolHist.setPid(protocolRaw.getPid());
				protocolHist.setRate(protocolRaw.getRate());
				protocolHist.setCdd(opre.getCdd());

				protocolHist.setId(opre.getId());
				protocolHist.setHeight(opre.getHeight());
				protocolHist.setIndex(opre.getTxIndex());
				protocolHist.setTime(opre.getTime());
				protocolHist.setSigner(opre.getSigner());
				break;
			default:
				System.out.println("Invalid operation");
				return null;
		}
		return protocolHist;
	}

	public ServiceHistory makeService(OpReturn opre, Feip feip) {

		Gson gson = new Gson();
		ServiceOpData serviceRaw = new ServiceOpData();

		try {
			serviceRaw = gson.fromJson(gson.toJson(feip.getData()), ServiceOpData.class);
			if(serviceRaw==null){
				System.out.println("Service raw is null");
				return null;
			}
		}catch(Exception e) {
			System.out.println("Failed to parse service");
			return null;
		}

		ServiceHistory serviceHist = new ServiceHistory();

		if(serviceRaw.getOp()==null){
			System.out.println("OP is null");
			return null;
		}

		serviceHist.setOp(serviceRaw.getOp());

		switch(serviceRaw.getOp()) {
			case PUBLISH:
				if(serviceRaw.getStdName()==null||"".equals(serviceRaw.getStdName())){
					System.out.println("StdName is null or empty");
					return null;
				}
				if(serviceRaw.getSid()!=null){
					System.out.println("Sid is not null");
					return null;
				}
				if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired){
					System.out.println("Height is greater than CddCheckHeight and Cdd is less than CddRequired");
					return null;
				}
				serviceHist.setId(opre.getId());
				serviceHist.setSid(opre.getId());
				serviceHist.setHeight(opre.getHeight());
				serviceHist.setIndex(opre.getTxIndex());
				serviceHist.setTime(opre.getTime());
				serviceHist.setSigner(opre.getSigner());

				if(serviceRaw.getStdName()!=null)serviceHist.setStdName(serviceRaw.getStdName());
				if(serviceRaw.getLocalNames()!=null)serviceHist.setLocalNames(serviceRaw.getLocalNames());
				if(serviceRaw.getDesc()!=null)serviceHist.setDesc(serviceRaw.getDesc());
				if(serviceRaw.getType()!=null)serviceHist.setType(serviceRaw.getType());
				if(serviceRaw.getComponents()!=null)serviceHist.setComponents(serviceRaw.getComponents());
				if(serviceRaw.getHome()!=null)serviceHist.setHome(serviceRaw.getHome());
				if(serviceRaw.getWaiters()!=null)serviceHist.setWaiters(serviceRaw.getWaiters());
				if(serviceRaw.getProtocols()!=null)serviceHist.setProtocols(serviceRaw.getProtocols());
				if(serviceRaw.getServices()!=null)serviceHist.setServices(serviceRaw.getServices());
				if(serviceRaw.getCodes()!=null)serviceHist.setCodes(serviceRaw.getCodes());
				if(serviceRaw.getVer()!=null)serviceHist.setVer(serviceRaw.getVer());
				if(serviceRaw.getParams()!=null) {
					serviceHist.setParams(serviceRaw.getParams());
				}
				if(serviceRaw.getDealer()!=null && serviceRaw.getDealerPubkey()!=null && !serviceRaw.getDealer().equals(KeyTools.pubkeyToFchAddr(serviceRaw.getDealerPubkey()))) {
					System.out.println("The dealerPubkey does not match the dealer.");
					return null;
				}else{
					serviceHist.setDealer(serviceRaw.getDealer());
					serviceHist.setDealerPubkey(serviceRaw.getDealerPubkey());
				}
				// Pricing fields
				if(serviceRaw.getPricePerKB()!=null)serviceHist.setPricePerKB(serviceRaw.getPricePerKB());
				if(serviceRaw.getPricePerKBIn()!=null)serviceHist.setPricePerKBIn(serviceRaw.getPricePerKBIn());
				if(serviceRaw.getPricePerKBOut()!=null)serviceHist.setPricePerKBOut(serviceRaw.getPricePerKBOut());
				if(serviceRaw.getPricePerKBDay()!=null)serviceHist.setPricePerKBDay(serviceRaw.getPricePerKBDay());
				if(serviceRaw.getMinPayment()!=null)serviceHist.setMinPayment(serviceRaw.getMinPayment());
				if(serviceRaw.getPricePerRequest()!=null)serviceHist.setPricePerRequest(serviceRaw.getPricePerRequest());
				if(serviceRaw.getSessionDays()!=null)serviceHist.setSessionDays(serviceRaw.getSessionDays());
				if(serviceRaw.getConsumeViaShare()!=null)serviceHist.setConsumeViaShare(serviceRaw.getConsumeViaShare());
				if(serviceRaw.getOrderViaShare()!=null)serviceHist.setOrderViaShare(serviceRaw.getOrderViaShare());
				if(serviceRaw.getCurrency()!=null)serviceHist.setCurrency(serviceRaw.getCurrency());
				if(serviceRaw.getMinCredit()!=null)serviceHist.setMinCredit(serviceRaw.getMinCredit());
				if(serviceRaw.getMaxDataSize()!=null)serviceHist.setMaxDataSize(serviceRaw.getMaxDataSize());
				if(serviceRaw.getDataExpiresInDays()!=null)serviceHist.setDataExpiresInDays(serviceRaw.getDataExpiresInDays());


				break;
			case UPDATE:
				if(serviceRaw.getSid()==null){
					System.out.println("Sid is null");
					return null;
				}
				if(serviceRaw.getStdName()==null||"".equals(serviceRaw.getStdName())){
					System.out.println("StdName is null or empty");
					return null;
				}

				serviceHist.setId(opre.getId());
				serviceHist.setSid(serviceRaw.getSid());
				serviceHist.setHeight(opre.getHeight());
				serviceHist.setIndex(opre.getTxIndex());
				serviceHist.setTime(opre.getTime());
				serviceHist.setSigner(opre.getSigner());

				if(serviceRaw.getStdName()!=null)serviceHist.setStdName(serviceRaw.getStdName());
				if(serviceRaw.getLocalNames()!=null)serviceHist.setLocalNames(serviceRaw.getLocalNames());
				if(serviceRaw.getDesc()!=null)serviceHist.setDesc(serviceRaw.getDesc());
				if(serviceRaw.getType()!=null)serviceHist.setType(serviceRaw.getType());
				if(serviceRaw.getComponents()!=null)serviceHist.setComponents(serviceRaw.getComponents());
				if(serviceRaw.getHome()!=null)serviceHist.setHome(serviceRaw.getHome());
				if(serviceRaw.getWaiters()!=null)serviceHist.setWaiters(serviceRaw.getWaiters());
				if(serviceRaw.getProtocols()!=null)serviceHist.setProtocols(serviceRaw.getProtocols());
				if(serviceRaw.getServices()!=null)serviceHist.setServices(serviceRaw.getServices());
				if(serviceRaw.getCodes()!=null)serviceHist.setCodes(serviceRaw.getCodes());
				if(serviceRaw.getVer()!=null)serviceHist.setVer(serviceRaw.getVer());
				if(serviceRaw.getParams()!=null) {
					serviceHist.setParams(serviceRaw.getParams());
				}
				if(serviceRaw.getDealer()!=null && serviceRaw.getDealerPubkey()!=null && !serviceRaw.getDealer().equals(KeyTools.pubkeyToFchAddr(serviceRaw.getDealerPubkey()))) {
					System.out.println("The dealerPubkey does not match the dealer.");
					return null;
				}else{
					serviceHist.setDealer(serviceRaw.getDealer());
					serviceHist.setDealerPubkey(serviceRaw.getDealerPubkey());
				}
				// Pricing fields
				if(serviceRaw.getPricePerKB()!=null)serviceHist.setPricePerKB(serviceRaw.getPricePerKB());
				if(serviceRaw.getPricePerKBIn()!=null)serviceHist.setPricePerKBIn(serviceRaw.getPricePerKBIn());
				if(serviceRaw.getPricePerKBOut()!=null)serviceHist.setPricePerKBOut(serviceRaw.getPricePerKBOut());
				if(serviceRaw.getPricePerKBDay()!=null)serviceHist.setPricePerKBDay(serviceRaw.getPricePerKBDay());
				if(serviceRaw.getMinPayment()!=null)serviceHist.setMinPayment(serviceRaw.getMinPayment());
				if(serviceRaw.getPricePerRequest()!=null)serviceHist.setPricePerRequest(serviceRaw.getPricePerRequest());
				if(serviceRaw.getSessionDays()!=null)serviceHist.setSessionDays(serviceRaw.getSessionDays());
				if(serviceRaw.getConsumeViaShare()!=null)serviceHist.setConsumeViaShare(serviceRaw.getConsumeViaShare());
				if(serviceRaw.getOrderViaShare()!=null)serviceHist.setOrderViaShare(serviceRaw.getOrderViaShare());
				if(serviceRaw.getCurrency()!=null)serviceHist.setCurrency(serviceRaw.getCurrency());
				if(serviceRaw.getMinCredit()!=null)serviceHist.setMinCredit(serviceRaw.getMinCredit());
				if(serviceRaw.getMaxDataSize()!=null)serviceHist.setMaxDataSize(serviceRaw.getMaxDataSize());
				if(serviceRaw.getDataExpiresInDays()!=null)serviceHist.setDataExpiresInDays(serviceRaw.getDataExpiresInDays());
				break;
			case STOP:
			case "recover":
			case "close":
				if(serviceRaw.getSids()==null||serviceRaw.getSids().isEmpty()){
					System.out.println("Sids is null or empty");
					return null;
				}
				serviceHist.setSids(serviceRaw.getSids());
				serviceHist.setId(opre.getId());
				serviceHist.setHeight(opre.getHeight());
				serviceHist.setIndex(opre.getTxIndex());
				serviceHist.setTime(opre.getTime());
				serviceHist.setSigner(opre.getSigner());

				if (serviceRaw.getOp().equals(CLOSE)) {
					serviceHist.setCloseStatement(serviceRaw.getCloseStatement());
				}
				break;
			case RATE:
				if(serviceRaw.getSid()==null){
					System.out.println("Sid is null");
					return null;
				}
				if(serviceRaw.getRate()<0 ||serviceRaw.getRate()>5){
					System.out.println("Rate should be between 0 and 5");
					return null;
				}
				if (opre.getCdd() < StartFEIP.CddRequired){
					System.out.println("Cdd is less than CddRequired");
					return null;
				}
				serviceHist.setSid(serviceRaw.getSid());
				serviceHist.setId(opre.getId());
				serviceHist.setHeight(opre.getHeight());
				serviceHist.setIndex(opre.getTxIndex());
				serviceHist.setTime(opre.getTime());
				serviceHist.setSigner(opre.getSigner());
				serviceHist.setRate(serviceRaw.getRate());
				serviceHist.setCdd(opre.getCdd());
				break;
			default:
				System.out.println("Invalid operation");
				return null;
		}
		return serviceHist;
	}

	public AppHistory makeApp(OpReturn opre, Feip feip) {

		Gson gson = new Gson();

		AppOpData appRaw = new AppOpData();

		try {
			appRaw = gson.fromJson(gson.toJson(feip.getData()), AppOpData.class);
			if(appRaw==null){
				System.out.println("App raw is null");
				return null;
			}
		}catch(Exception e) {
			System.out.println("Failed to parse app");
			return null;
		}

		AppHistory appHist = new AppHistory();

		if(appRaw.getOp()==null){
			System.out.println("OP is null");
			return null;
		}
		appHist.setOp(appRaw.getOp());

		switch(appRaw.getOp()) {

			case PUBLISH:
				if(appRaw.getStdName()==null||"".equals(appRaw.getStdName())){
					System.out.println("StdName is null or empty");
					return null;
				}
				if(appRaw.getAid()!=null){
					System.out.println("Aid is not null");
					return null;
				}
            if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired){
				System.out.println("Height is greater than CddCheckHeight and Cdd is less than CddRequired");
				return null;
			}
				appHist.setId(opre.getId());
				appHist.setAid(opre.getId());
				appHist.setHeight(opre.getHeight());
				appHist.setIndex(opre.getTxIndex());
				appHist.setTime(opre.getTime());
				appHist.setSigner(opre.getSigner());

				if(appRaw.getStdName()!=null)appHist.setStdName(appRaw.getStdName());
				if(appRaw.getLocalNames()!=null)appHist.setLocalNames(appRaw.getLocalNames());
				if(appRaw.getDesc()!=null)appHist.setDesc(appRaw.getDesc());
				if(appRaw.getTypes()!=null)appHist.setTypes(appRaw.getTypes());
				if(appRaw.getVer()!=null)appHist.setVer(appRaw.getVer());
				if(appRaw.getHome()!=null)appHist.setHome(appRaw.getHome());
				if(appRaw.getDownloads()!=null)appHist.setDownloads(appRaw.getDownloads());
				if(appRaw.getWaiters()!=null)appHist.setWaiters(appRaw.getWaiters());
				if(appRaw.getProtocols()!=null)appHist.setProtocols(appRaw.getProtocols());
				if(appRaw.getServices() !=null)appHist.setServices(appRaw.getServices());
				if(appRaw.getCodes() !=null)appHist.setCodes(appRaw.getCodes());

				break;

			case UPDATE:
				if(appRaw.getAid()==null){
					System.out.println("Aid is null");
					return null;
				}
				if(appRaw.getStdName()==null||"".equals(appRaw.getStdName())){
					System.out.println("StdName is null or empty");
					return null;
				}

				appHist.setAid(appRaw.getAid());
				appHist.setId(opre.getId());
				appHist.setHeight(opre.getHeight());
				appHist.setIndex(opre.getTxIndex());
				appHist.setTime(opre.getTime());
				appHist.setSigner(opre.getSigner());

				if(appRaw.getStdName()!=null)appHist.setStdName(appRaw.getStdName());
				if(appRaw.getLocalNames()!=null)appHist.setLocalNames(appRaw.getLocalNames());
				if(appRaw.getDesc()!=null)appHist.setDesc(appRaw.getDesc());
				if(appRaw.getTypes()!=null)appHist.setTypes(appRaw.getTypes());
				if(appRaw.getVer()!=null)appHist.setVer(appRaw.getVer());
				if(appRaw.getHome()!=null)appHist.setHome(appRaw.getHome());
				if(appRaw.getDownloads()!=null)appHist.setDownloads(appRaw.getDownloads());
				if(appRaw.getWaiters()!=null)appHist.setWaiters(appRaw.getWaiters());
				if(appRaw.getProtocols()!=null)appHist.setProtocols(appRaw.getProtocols());
				if(appRaw.getServices() !=null)appHist.setServices(appRaw.getServices());
				if(appRaw.getCodes() !=null)appHist.setCodes(appRaw.getCodes());

				break;

			case STOP:
			case RECOVER:
			case CLOSE:
				if(appRaw.getAids()==null||appRaw.getAids().isEmpty()){
					System.out.println("Aids is null or empty");
					return null;
				}
				appHist.setAids(appRaw.getAids());

				appHist.setId(opre.getId());
				appHist.setHeight(opre.getHeight());
				appHist.setIndex(opre.getTxIndex());
				appHist.setTime(opre.getTime());
				appHist.setSigner(opre.getSigner());

				if (appRaw.getOp().equals(CLOSE)) {
					appHist.setCloseStatement(appRaw.getCloseStatement());
				}
				break;
			case RATE:
				if(appRaw.getAid()==null){
					System.out.println("Aid is null");
					return null;
				}
				if(appRaw.getRate()<0 ||appRaw.getRate()>5){
					System.out.println("Rate should be between 0 and 5");
					return null;
				}
            if (opre.getCdd() < StartFEIP.CddRequired){
				System.out.println("Cdd is less than CddRequired");
				return null;
			}
				appHist.setAid(appRaw.getAid());
				appHist.setRate(appRaw.getRate());
				appHist.setCdd(opre.getCdd());

				appHist.setId(opre.getId());
				appHist.setHeight(opre.getHeight());
				appHist.setIndex(opre.getTxIndex());
				appHist.setTime(opre.getTime());
				appHist.setSigner(opre.getSigner());
				break;
			default:
				System.out.println("Invalid operation");
				return null;
		}
		return appHist;
	}

	public CodeHistory makeCode(OpReturn opre, Feip feip) {

		Gson gson = new Gson();
		CodeOpData codeRaw = new CodeOpData();

		try {
			codeRaw = gson.fromJson(gson.toJson(feip.getData()), CodeOpData.class);
			if(codeRaw==null){
				System.out.println("Code raw is null");
				return null;
			}
		}catch(Exception e) {
			System.out.println("Failed to parse code");
			return null;
		}

		CodeHistory codeHist = new CodeHistory();

		if(codeRaw.getOp()==null){
			System.out.println("OP is null");
			return null;
		}

		codeHist.setOp(codeRaw.getOp());

		switch(codeRaw.getOp()) {
			case PUBLISH:
				if(codeRaw.getName()==null||"".equals(codeRaw.getName())){
					System.out.println("Name is null or empty");
					return null;
				}
				if(codeRaw.getCodeId()!=null){
					System.out.println("CodeId is not null");
					return null;
				}

				codeHist.setId(opre.getId());
				codeHist.setCodeId(opre.getId());
				codeHist.setHeight(opre.getHeight());
				codeHist.setIndex(opre.getTxIndex());
				codeHist.setTime(opre.getTime());
				codeHist.setSigner(opre.getSigner());

				if(codeRaw.getName()!=null)codeHist.setName(codeRaw.getName());
				if(codeRaw.getVer()!=null)codeHist.setVer(codeRaw.getVer());
				if(codeRaw.getDid()!=null)codeHist.setDid(codeRaw.getDid());
				if(codeRaw.getDesc()!=null)codeHist.setDesc(codeRaw.getDesc());
				if(codeRaw.getHome()!=null)codeHist.setHome(codeRaw.getHome());
				if(codeRaw.getLangs()!=null)codeHist.setLangs(codeRaw.getLangs());
				if(codeRaw.getProtocols()!=null)codeHist.setProtocols(codeRaw.getProtocols());
				if(codeRaw.getWaiters()!=null)codeHist.setWaiters(codeRaw.getWaiters());
				break;
			case UPDATE:
				if(codeRaw.getCodeId()==null){
					System.out.println("CodeId is null");
					return null;
				}
				if(codeRaw.getName()==null||"".equals(codeRaw.getName())){
					System.out.println("Name is null or empty");
					return null;
				}

				codeHist.setId(opre.getId());
				codeHist.setCodeId(codeRaw.getCodeId());
				codeHist.setHeight(opre.getHeight());
				codeHist.setIndex(opre.getTxIndex());
				codeHist.setTime(opre.getTime());
				codeHist.setSigner(opre.getSigner());

				if(codeRaw.getName()!=null)codeHist.setName(codeRaw.getName());
				if(codeRaw.getVer()!=null)codeHist.setVer(codeRaw.getVer());
				if(codeRaw.getDid()!=null)codeHist.setDid(codeRaw.getDid());
				if(codeRaw.getDesc()!=null)codeHist.setDesc(codeRaw.getDesc());
				if(codeRaw.getHome()!=null)codeHist.setHome(codeRaw.getHome());
				if(codeRaw.getLangs()!=null)codeHist.setLangs(codeRaw.getLangs());
				if(codeRaw.getProtocols()!=null)codeHist.setProtocols(codeRaw.getProtocols());
				if(codeRaw.getWaiters()!=null)codeHist.setWaiters(codeRaw.getWaiters());
				break;
			case STOP:
			case RECOVER:
			case CLOSE:
				if (codeRaw.getCodeIds() == null || codeRaw.getCodeIds().isEmpty()) {
					System.out.println("CodeIds is null or empty");
					return null;
				}
				codeHist.setCodeIds(codeRaw.getCodeIds());

				codeHist.setId(opre.getId());
				codeHist.setHeight(opre.getHeight());
				codeHist.setIndex(opre.getTxIndex());
				codeHist.setTime(opre.getTime());
				codeHist.setSigner(opre.getSigner());

				if (codeRaw.getOp().equals(CLOSE)) {
					codeHist.setCloseStatement(codeRaw.getCloseStatement());
				}
				break;
			case RATE:
				if(codeRaw.getCodeId()==null){
					System.out.println("CodeId is null");
					return null;
				}
				if(codeRaw.getRate()<0 ||codeRaw.getRate()>5){
					System.out.println("Rate should be between 0 and 5");
					return null;
				}
				if (opre.getCdd() < StartFEIP.CddRequired){
					System.out.println("Cdd is less than CddRequired");
					return null;
				}
				codeHist.setCodeId(codeRaw.getCodeId());
				codeHist.setId(opre.getId());
				codeHist.setHeight(opre.getHeight());
				codeHist.setIndex(opre.getTxIndex());
				codeHist.setTime(opre.getTime());
				codeHist.setSigner(opre.getSigner());
				codeHist.setRate(codeRaw.getRate());
				codeHist.setCdd(opre.getCdd());
				break;
			default:
				System.out.println("Invalid operation");
				return null;
		}
		return codeHist;
	}

	public boolean parseProtocol(ElasticsearchClient esClient, ProtocolHistory protocolHist) throws Exception {

		if(protocolHist==null){
			System.out.println("Protocol hist is null");
			return false;
		}
		Protocol protocol;
		switch (protocolHist.getOp()) {
			case PUBLISH -> {
				protocol = EsUtils.getById(esClient, IndicesNames.PROTOCOL, protocolHist.getPid(), Protocol.class);
				if (protocol == null) {
					protocol = new Protocol();
					protocol.setId(protocolHist.getPid());
					protocol.setType(protocolHist.getType());
					protocol.setSn(protocolHist.getSn());
					protocol.setVer(protocolHist.getVer());
					protocol.setDid(protocolHist.getDid());
					protocol.setName(protocolHist.getName());

					protocol.setLang(protocolHist.getLang());
					protocol.setDesc(protocolHist.getDesc());
					protocol.setPrePid(protocolHist.getPrePid());
					protocol.setHome(protocolHist.getHome());

					protocol.setTitle(protocolHist.getType() + protocolHist.getSn() + "V" + protocolHist.getVer() + "_" + protocolHist.getName() + "(" + protocolHist.getLang() + ")");
					protocol.setOwner(protocolHist.getSigner());

					protocol.setBirthTime(protocolHist.getTime());
					protocol.setBirthHeight(protocolHist.getHeight());

					protocol.setLastTxId(protocolHist.getId());
					protocol.setLastTime(protocolHist.getTime());
					protocol.setLastHeight(protocolHist.getHeight());

					protocol.setActive(true);
					protocol.setClosed(false);

					Protocol protocol1 = protocol;

					IndexResponse result = esClient.index(i -> i.index(IndicesNames.PROTOCOL).id(protocolHist.getPid()).document(protocol1));
					if(result==null||result.result()==null){
						System.out.println("Failed to create protocol");
						return false;
					}
					if (!CREATED.equals(result.result().jsonValue()))
						if (!UPDATED.equals(result.result().jsonValue())) {
							System.out.println("Failed to create protocol");
							return false;
						}

					System.out.println(result.result());

					// Create news
					News.createNews(esClient, protocolHist.getId(), protocolHist.getSigner(), PUBLISH,
							Feip.FeipProtocol.PROTOCOL.getName(), protocolHist.getId(), protocolHist.getName(), protocolHist.getDesc(),
							protocolHist.getHeight(), protocolHist.getTime());
					return true;
				} else {
					System.out.println("Protocol already exists");
					return false;
				}
			}
			case UPDATE -> {
				protocol = EsUtils.getById(esClient, IndicesNames.PROTOCOL, protocolHist.getPid(), Protocol.class);
				if (protocol == null) {
					System.out.println("Protocol not found");
					return false;
				}
				if (Boolean.TRUE.equals(protocol.isClosed())) {
					System.out.println("Protocol is closed");
					return false;
				}
				if (!protocol.getOwner().equals(protocolHist.getSigner())) {
					System.out.println("Protocol owner is not the same as the signer");
					return false;
				}
				if (Boolean.FALSE.equals(protocol.isActive())) {
					System.out.println("Protocol is not active");
					return false;
				}
				protocol.setType(protocolHist.getType());
				protocol.setSn(protocolHist.getSn());
				protocol.setVer(protocolHist.getVer());
				protocol.setDid(protocolHist.getDid());
				protocol.setName(protocolHist.getName());
				protocol.setLang(protocolHist.getLang());
				protocol.setDesc(protocolHist.getDesc());
				protocol.setPrePid(protocolHist.getPrePid());
				protocol.setHome(protocolHist.getHome());
				protocol.setTitle(protocolHist.getType() + protocolHist.getSn() + "V" + protocolHist.getVer() + "_" + protocolHist.getName() + "(" + protocolHist.getLang() + ")");
				protocol.setLastTxId(protocolHist.getId());
				protocol.setLastTime(protocolHist.getTime());
				protocol.setLastHeight(protocolHist.getHeight());
				Protocol protocol2 = protocol;
				IndexResponse result = esClient.index(i -> i.index(IndicesNames.PROTOCOL).id(protocolHist.getPid()).document(protocol2));

				if(result==null || result.result()==null){
					System.out.println("Failed to update protocol");
					return false;
				}
				System.out.println(result.result());
				return CREATED.equals(result.result().jsonValue()) || UPDATED.equals(result.result().jsonValue());
			}
			case STOP, RECOVER, CLOSE -> {
				List<String> idList = new ArrayList<>();
				if (protocolHist.getPids() != null && !protocolHist.getPids().isEmpty()) {
					idList.addAll(protocolHist.getPids());
				} else {
					System.out.println("Pids is null or empty");
					return false;
				}

				EsUtils.MgetResult<Protocol> result = EsUtils.getMultiByIdList(esClient, IndicesNames.PROTOCOL, idList, Protocol.class);
				List<Protocol> protocols = result.getResultList();

				List<Protocol> updatedProtocols = new ArrayList<>();
				for (Protocol protocolItem : protocols) {
					if (Boolean.TRUE.equals(protocolItem.isClosed())) {
						continue;
					}

					if (!protocolItem.getOwner().equals(protocolHist.getSigner())) {
						Freer resultCid = EsUtils.getById(esClient, IndicesNames.FREER, protocolHist.getSigner(), Freer.class);
						if (resultCid == null ||resultCid.getMaster() == null || !resultCid.getMaster().equals(protocolHist.getSigner())) {
							continue;
						}
					}

					switch (protocolHist.getOp()) {
						case STOP:
							protocolItem.setActive(false);
							break;
						case RECOVER:
							protocolItem.setActive(true);
							break;
						case CLOSE:
							protocolItem.setClosed(true);
							protocolItem.setActive(false);
							break;
					}

					protocolItem.setLastTxId(protocolHist.getId());
					protocolItem.setLastTime(protocolHist.getTime());
					protocolItem.setLastHeight(protocolHist.getHeight());

					updatedProtocols.add(protocolItem);
				}

				if (!updatedProtocols.isEmpty()) {
					BulkRequest.Builder br = new BulkRequest.Builder();
					for (Protocol updatedProtocol : updatedProtocols) {
						br.operations(op -> op
								.index(idx -> idx
										.index(IndicesNames.PROTOCOL)
										.id(updatedProtocol.getId())
										.document(updatedProtocol)
								)
						);
					}
					BulkResponse result1 = esClient.bulk(br.build());
					if(result1.errors()){
						System.out.println("Failed to bulk update protocol");
						return false;
					}else System.out.println("Done");
					// Create news
					News.createNews(esClient, protocolHist.getId(), protocolHist.getSigner(), protocolHist.getOp(),
							Feip.FeipProtocol.PROTOCOL.getName(), null, null, StringUtils.listToString(protocolHist.getPids()),
							protocolHist.getHeight(), protocolHist.getTime());

					return true;
				}
			}

			case RATE -> {
				protocol = EsUtils.getById(esClient, IndicesNames.PROTOCOL, protocolHist.getPid(), Protocol.class);
				if (protocol == null) {
					System.out.println("Protocol not found");
					return false;
				}
				if (protocol.getOwner().equals(protocolHist.getSigner())) {
					System.out.println("Protocol owner is the same as the signer");
					return false;
				}

				if((protocolHist.getCdd()==null || protocolHist.getRate()==null)){
					System.out.println("Cdd or rate is null");
					return false;
				}

				if(protocol.gettCdd()==null||protocol.gettRate()==null){
					protocol.settRate(Float.valueOf(protocolHist.getRate()));
					protocol.settCdd(protocolHist.getCdd());
				}else{
					protocol.settRate(
							(protocol.gettRate()*protocol.gettCdd()+protocolHist.getRate()*protocolHist.getCdd())
									/(protocol.gettCdd()+protocolHist.getCdd())
					);
					protocol.settCdd(protocol.gettCdd() + protocolHist.getCdd());
				}

				protocol.setLastTxId(protocolHist.getId());
				protocol.setLastTime(protocolHist.getTime());
				protocol.setLastHeight(protocolHist.getHeight());
				Protocol protocol3 = protocol;
				IndexResponse result = esClient.index(i -> i.index(IndicesNames.PROTOCOL).id(protocolHist.getPid()).document(protocol3));

				if(result==null || result.result()==null){
					System.out.println("Failed to update protocol");
					return false;
				}
				System.out.println(result.result());
				return CREATED.equals(result.result().jsonValue()) || UPDATED.equals(result.result().jsonValue());
			}
		}

		return false;
	}

	public boolean parseService(ElasticsearchClient esClient, ServiceHistory serviceHist) throws Exception {

		if(serviceHist==null){
			System.out.println("Service hist is null");
			return false;
		}
		Service service;
		switch(serviceHist.getOp()) {
			case PUBLISH:
				service = EsUtils.getById(esClient, IndicesNames.SERVICE, serviceHist.getSid(), Service.class);
				if(service==null) {
					service = new Service();
					service.setId(serviceHist.getId());
					service.setStdName(serviceHist.getStdName());
					service.setLocalNames(serviceHist.getLocalNames());
					service.setDesc(serviceHist.getDesc());
					service.setType(serviceHist.getType());
					service.setVer(serviceHist.getVer());
					service.setDealer(serviceHist.getDealer());

					if(serviceHist.getDealerPubkey()!=null){
						service.setDealerPubkey(serviceHist.getDealerPubkey());
						String dealer = KeyTools.pubkeyToFchAddr(serviceHist.getDealerPubkey());
						service.setDealer(dealer);
					}

					service.setHome(serviceHist.getHome());
					service.setComponents(serviceHist.getComponents());
					service.setWaiters(serviceHist.getWaiters());
					service.setProtocols(serviceHist.getProtocols());
					service.setCodes(serviceHist.getCodes());
					service.setServices(serviceHist.getServices());
					service.setParams(serviceHist.getParams());
					service.setOwner(serviceHist.getSigner());

					// Pricing fields
					service.setPricePerKB(serviceHist.getPricePerKB());
					service.setPricePerKBIn(serviceHist.getPricePerKBIn());
					service.setPricePerKBOut(serviceHist.getPricePerKBOut());
					service.setPricePerKBDay(serviceHist.getPricePerKBDay());
					service.setMinPayment(serviceHist.getMinPayment());
					service.setPricePerRequest(serviceHist.getPricePerRequest());
					service.setSessionDays(serviceHist.getSessionDays());
					service.setConsumeViaShare(serviceHist.getConsumeViaShare());
					service.setOrderViaShare(serviceHist.getOrderViaShare());
					service.setCurrency(serviceHist.getCurrency());
					service.setMinCredit(serviceHist.getMinCredit());
					service.setMaxDataSize(serviceHist.getMaxDataSize());
					service.setDataExpiresInDays(serviceHist.getDataExpiresInDays());


					service.setLastTxId(serviceHist.getId());
					service.setLastTime(serviceHist.getTime());
					service.setLastHeight(serviceHist.getHeight());

					service.setBirthTime(serviceHist.getTime());
					service.setBirthHeight(serviceHist.getHeight());

					service.setActive(true);
					service.setClosed(false);

					Service service1 = service;
					IndexResponse result = esClient.index(i->i.index(IndicesNames.SERVICE).id(serviceHist.getSid()).document(service1));
					if(result==null||result.result()==null){
						System.out.println("Failed to create service");
						return false;
					}
					if(!CREATED.equals(result.result().jsonValue()) && !UPDATED.equals(result.result().jsonValue())){
						System.out.println("Failed to create service");
						return false;
					}

					System.out.println(result.result());

					// Create news
					News.createNews(esClient, serviceHist.getId(), serviceHist.getSigner(), PUBLISH,
							Feip.FeipProtocol.SERVICE.getName(), serviceHist.getId(), serviceHist.getStdName(), serviceHist.getDesc(),
							serviceHist.getHeight(), serviceHist.getTime());

					return true;
				}else {
					System.out.println("Service already exists");
					return false;
				}
			case STOP, RECOVER, CLOSE : {
				if(serviceHist.getSids()==null||serviceHist.getSids().isEmpty()){
					System.out.println("Sids is null or empty");
					return false;
				}

				EsUtils.MgetResult<Service> result = EsUtils.getMultiByIdList(esClient, IndicesNames.SERVICE, serviceHist.getSids(), Service.class);
				List<Service> services = result.getResultList();

				List<Service> updatedServices = new ArrayList<>();
				for (Service serviceItem : services) {
					if (Boolean.TRUE.equals(serviceItem.isClosed())) {
						continue;
					}

					if (!serviceItem.getOwner().equals(serviceHist.getSigner())) {
						Freer resultCid = EsUtils.getById(esClient, IndicesNames.FREER, serviceHist.getSigner(), Freer.class);
						if (resultCid.getMaster() == null || !resultCid.getMaster().equals(serviceHist.getSigner())) {
							System.out.println("Service owner is not the same as the signer");
							continue;
						}
					}

					switch (serviceHist.getOp()) {
						case STOP:
							serviceItem.setActive(false);
							break;
						case RECOVER:
							serviceItem.setActive(true);
							break;
						case CLOSE:
							serviceItem.setClosed(true);
							serviceItem.setActive(false);
							break;
					}

					serviceItem.setLastTxId(serviceHist.getId());
					serviceItem.setLastTime(serviceHist.getTime());
					serviceItem.setLastHeight(serviceHist.getHeight());

					updatedServices.add(serviceItem);
				}

				if (!updatedServices.isEmpty()) {
					BulkRequest.Builder br = new BulkRequest.Builder();
					for (Service updatedService : updatedServices) {
						br.operations(op -> op
								.index(idx -> idx
										.index(IndicesNames.SERVICE)
										.id(updatedService.getId())
										.document(updatedService)
								)
						);
					}
					BulkResponse result1 = esClient.bulk(br.build());
					if(result1.errors()){
						System.out.println("Failed to bulk update service");
						return false;
					} else System.out.println("Done");

					// Create news
					News.createNews(esClient, serviceHist.getId(), serviceHist.getSigner(), serviceHist.getOp(),
							Feip.FeipProtocol.SERVICE.getName(), null, null, StringUtils.listToString(serviceHist.getSids()),
							serviceHist.getHeight(), serviceHist.getTime());
					return true;
				}
			}

			case UPDATE:
				service = EsUtils.getById(esClient, IndicesNames.SERVICE, serviceHist.getSid(), Service.class);

				if(service==null) {
					System.out.println("Service not found");
					return false;
				}

				if(Boolean.TRUE.equals(service.isClosed())) {
					System.out.println("Service is closed");
					return false;
				}

				if(! (service.getOwner().equals(serviceHist.getSigner()))) {
					System.out.println("Service owner is not the same as the signer");
					return false;
				}

				service.setStdName(serviceHist.getStdName());
				service.setLocalNames(serviceHist.getLocalNames());
				service.setDesc(serviceHist.getDesc());
				service.setType(serviceHist.getType());
				service.setVer(serviceHist.getVer());
				service.setDealer(serviceHist.getDealer());

				if(serviceHist.getDealerPubkey()!=null){
					service.setDealerPubkey(serviceHist.getDealerPubkey());
					String dealer = KeyTools.pubkeyToFchAddr(serviceHist.getDealerPubkey());
					service.setDealer(dealer);
				}

				service.setHome(serviceHist.getHome());
				service.setComponents(serviceHist.getComponents());
				service.setWaiters(serviceHist.getWaiters());
				service.setProtocols(serviceHist.getProtocols());
				service.setCodes(serviceHist.getCodes());
				service.setServices(serviceHist.getServices());
				service.setParams(serviceHist.getParams());

				// Pricing fields
				if(serviceHist.getPricePerKB()!=null)service.setPricePerKB(serviceHist.getPricePerKB());
				if(serviceHist.getPricePerKBIn()!=null)service.setPricePerKBIn(serviceHist.getPricePerKBIn());
				if(serviceHist.getPricePerKBOut()!=null)service.setPricePerKBOut(serviceHist.getPricePerKBOut());
				if(serviceHist.getPricePerKBDay()!=null)service.setPricePerKBDay(serviceHist.getPricePerKBDay());
				if(serviceHist.getMinPayment()!=null)service.setMinPayment(serviceHist.getMinPayment());
				if(serviceHist.getPricePerRequest()!=null)service.setPricePerRequest(serviceHist.getPricePerRequest());
				if(serviceHist.getSessionDays()!=null)service.setSessionDays(serviceHist.getSessionDays());
				if(serviceHist.getConsumeViaShare()!=null)service.setConsumeViaShare(serviceHist.getConsumeViaShare());
				if(serviceHist.getOrderViaShare()!=null)service.setOrderViaShare(serviceHist.getOrderViaShare());
				if(serviceHist.getCurrency()!=null)service.setCurrency(serviceHist.getCurrency());
				if(serviceHist.getMinCredit()!=null)service.setMinCredit(serviceHist.getMinCredit());
				if(serviceHist.getMaxDataSize()!=null)service.setMaxDataSize(serviceHist.getMaxDataSize());
				if(serviceHist.getDataExpiresInDays()!=null)service.setDataExpiresInDays(serviceHist.getDataExpiresInDays());


				service.setLastTxId(serviceHist.getId());
				service.setLastTime(serviceHist.getTime());
				service.setLastHeight(serviceHist.getHeight());

				Service service4 = service;

				IndexResponse result = esClient.index(i->i.index(IndicesNames.SERVICE).id(serviceHist.getSid()).document(service4));
				if(result==null || result.result()==null){
					System.out.println("Failed to update service");
					return false;
				}
				System.out.println(result.result());
				return CREATED.equals(result.result().jsonValue()) || UPDATED.equals(result.result().jsonValue());

			case RATE:
				service = EsUtils.getById(esClient, IndicesNames.SERVICE, serviceHist.getSid(), Service.class);

				if(service==null) {
					System.out.println("Service not found");
					return false;
				}

				if(service.getOwner().equals(serviceHist.getSigner())) {
					System.out.println("Service owner is the same as the signer");
					return false;
				}

				if((serviceHist.getCdd()==null || serviceHist.getRate()==null)){
					System.out.println("Cdd or rate is null");
					return false;
				}

				if(service.gettCdd()==null||service.gettRate()==null){
					service.settRate(Float.valueOf(serviceHist.getRate()));
					service.settCdd(serviceHist.getCdd());
				}else{
					service.settRate(
							(service.gettRate()*service.gettCdd()+serviceHist.getRate()*serviceHist.getCdd())
									/(service.gettCdd()+serviceHist.getCdd())
					);
					service.settCdd(service.gettCdd() + serviceHist.getCdd());
				}

				service.setLastTxId(serviceHist.getId());
				service.setLastTime(serviceHist.getTime());
				service.setLastHeight(serviceHist.getHeight());

				Service service5 = service;

				IndexResponse result2 = esClient.index(i->i.index(IndicesNames.SERVICE).id(serviceHist.getSid()).document(service5));
				if(result2==null || result2.result()==null){
					System.out.println("Failed to update service");
					return false;
				}
				System.out.println(result2.result());
				return CREATED.equals(result2.result().jsonValue()) || UPDATED.equals(result2.result().jsonValue());

		}
		return false;
	}

	public boolean parseApp(ElasticsearchClient esClient, AppHistory appHist) throws Exception {
		if(appHist==null){
			System.out.println("App hist is null");
			return false;
		}
		App app;
		switch(appHist.getOp()) {
			case PUBLISH:
				app = EsUtils.getById(esClient, IndicesNames.APP, appHist.getAid(), App.class);
				if(app==null) {
					app = new App();
					app.setId(appHist.getId());
					app.setStdName(appHist.getStdName());
					app.setLocalNames(appHist.getLocalNames());
					app.setDesc(appHist.getDesc());
					app.setTypes(appHist.getTypes());
					app.setVer(appHist.getVer());
					app.setHome(appHist.getHome());
					app.setDownloads(appHist.getDownloads());
					app.setWaiters(appHist.getWaiters());
					app.setOwner(appHist.getSigner());
					app.setProtocols(appHist.getProtocols());
					app.setServices(appHist.getServices());
					app.setCodes(appHist.getCodes());

					app.setBirthTime(appHist.getTime());
					app.setBirthHeight(appHist.getHeight());

					app.setLastTxId(appHist.getId());
					app.setLastTime(appHist.getTime());
					app.setLastHeight(appHist.getHeight());

					app.setActive(true);

					App app1=app;
					IndexResponse result = esClient.index(i->i.index(IndicesNames.APP).id(appHist.getAid()).document(app1));
					if(result==null||result.result()==null){
						System.out.println("Failed to create app");
						return false;
					}
					if(!CREATED.equals(result.result().jsonValue()) && !UPDATED.equals(result.result().jsonValue())){
						System.out.println("Failed to create app");
						return false;
					}
					System.out.println(result.result());

					// Create news
					News.createNews(esClient, appHist.getId(), appHist.getSigner(), PUBLISH,
							Feip.FeipProtocol.APP.getName(), appHist.getId(), appHist.getStdName(), appHist.getDesc(),
							appHist.getHeight(), appHist.getTime());
					return true;
				}else {
					System.out.println("App already exists");
					return false;
				}

			case STOP, RECOVER, CLOSE:
				if(appHist.getAids()==null||appHist.getAids().isEmpty()){
					System.out.println("Aids is null or empty");
					return false;
				}

				EsUtils.MgetResult<App> result = EsUtils.getMultiByIdList(esClient, IndicesNames.APP, appHist.getAids(), App.class);
				List<App> apps = result.getResultList();

				List<App> updatedApps = new ArrayList<>();
				for (App appItem : apps) {
					if (Boolean.TRUE.equals(appItem.isClosed())) {
						continue;
					}

					if (!appItem.getOwner().equals(appHist.getSigner())) {
						Freer resultCid = EsUtils.getById(esClient, IndicesNames.FREER, appHist.getSigner(), Freer.class);
						if (resultCid.getMaster() == null || !resultCid.getMaster().equals(appHist.getSigner())) {
							System.out.println("App owner is not the same as the signer");
							continue;
						}
					}

					switch (appHist.getOp()) {
						case STOP:
							appItem.setActive(false);
							break;
						case RECOVER:
							appItem.setActive(true);
							break;
						case CLOSE:
							appItem.setClosed(true);
							appItem.setActive(false);
							break;
					}

					appItem.setLastTxId(appHist.getId());
					appItem.setLastTime(appHist.getTime());
					appItem.setLastHeight(appHist.getHeight());

					updatedApps.add(appItem);
				}

				if (!updatedApps.isEmpty()) {
					BulkRequest.Builder br = new BulkRequest.Builder();
					for (App updatedApp : updatedApps) {
						br.operations(op -> op
								.index(idx -> idx
										.index(IndicesNames.APP)
										.id(updatedApp.getId())
										.document(updatedApp)
								)
						);
					}
					BulkResponse result1 = esClient.bulk(br.build());
					if(result1.errors()){
						System.out.println("Failed to bulk update app");
						return false;
					} else System.out.println("Done");

					// Create news
					News.createNews(esClient, appHist.getId(), appHist.getSigner(), appHist.getOp(),
							Feip.FeipProtocol.APP.getName(), null, null, StringUtils.listToString(appHist.getAids()),
							appHist.getHeight(), appHist.getTime());
					return true;
				}
				break;
			case UPDATE:
				app = EsUtils.getById(esClient, IndicesNames.APP, appHist.getAid(), App.class);

				if(app==null) {
					System.out.println("App not found");
					return false;
				}

				if(Boolean.TRUE.equals(app.isClosed())) {
					System.out.println("App is closed");
					return false;
				}

				if(! app.getOwner().equals(appHist.getSigner())) {
					System.out.println("App owner is not the same as the signer");
					return false;
				}

				if(Boolean.FALSE.equals(app.isActive())) {
					System.out.println("App is not active");
					return false;
				}

				app.setStdName(appHist.getStdName());
				app.setLocalNames(appHist.getLocalNames());
				app.setDesc(appHist.getDesc());
				app.setTypes(appHist.getTypes());
				app.setVer(appHist.getVer());
				app.setHome(appHist.getHome());
				app.setDownloads(appHist.getDownloads());
				app.setWaiters(appHist.getWaiters());
				app.setOwner(appHist.getSigner());
				app.setProtocols(appHist.getProtocols());
				app.setServices(appHist.getServices());
				app.setCodes(appHist.getCodes());

				app.setLastTxId(appHist.getId());
				app.setLastTime(appHist.getTime());
				app.setLastHeight(appHist.getHeight());

				App app2 = app;

				IndexResponse result1 = esClient.index(i->i.index(IndicesNames.APP).id(appHist.getAid()).document(app2));
				if(result1==null || result1.result()==null){
					System.out.println("Failed to update app");
					return false;
				}
				System.out.println(result1.result());
				return CREATED.equals(result1.result().jsonValue()) || UPDATED.equals(result1.result().jsonValue());

			case RATE:
				app = EsUtils.getById(esClient, IndicesNames.APP, appHist.getAid(), App.class);

				if(app==null) {
					System.out.println("App not found");
					return false;
				}

				if(app.getOwner().equals(appHist.getSigner())) {
					System.out.println("App owner is the same as the signer");
					return false;
				}

				if((appHist.getCdd()==null || appHist.getRate()==null)){
					System.out.println("Cdd or rate is null");
					return false;
				}

				if(app.gettCdd()==null||app.gettRate()==null){
					app.settRate(Float.valueOf(appHist.getRate()));
					app.settCdd(appHist.getCdd());
				}else{
					app.settRate(
							(app.gettRate()*app.gettCdd()+appHist.getRate()*appHist.getCdd())
									/(app.gettCdd()+appHist.getCdd())
					);
					app.settCdd(app.gettCdd() + appHist.getCdd());
				}

				app.setLastTxId(appHist.getId());
				app.setLastTime(appHist.getTime());
				app.setLastHeight(appHist.getHeight());

				App app3 = app;

				IndexResponse result2 = esClient.index(i->i.index(IndicesNames.APP).id(appHist.getAid()).document(app3));
				if(result2==null || result2.result()==null){
					System.out.println("Failed to update app");
					return false;
				}
				System.out.println(result2.result());
				return CREATED.equals(result2.result().jsonValue()) || UPDATED.equals(result2.result().jsonValue());
		}
		return false;
	}

	public boolean parseCode(ElasticsearchClient esClient, CodeHistory codeHist) throws Exception {
		if(codeHist==null){
			System.out.println("Code hist is null");
			return false;
		}
		Code code;
		switch(codeHist.getOp()) {
			case PUBLISH:
				code = EsUtils.getById(esClient, IndicesNames.CODE, codeHist.getCodeId(), Code.class);
				if(code==null) {
					code = new Code();
					code.setId(codeHist.getId());
					code.setName(codeHist.getName());
					code.setVer(codeHist.getVer());
					code.setDid(codeHist.getDid());
					code.setDesc(codeHist.getDesc());
					code.setLangs(codeHist.getLangs());
					code.setHome(codeHist.getHome());
					code.setProtocols(codeHist.getProtocols());
					code.setWaiters(codeHist.getWaiters());

					code.setOwner(codeHist.getSigner());
					code.setBirthTime(codeHist.getTime());
					code.setBirthHeight(codeHist.getHeight());

					code.setLastTxId(codeHist.getId());
					code.setLastTime(codeHist.getTime());
					code.setLastHeight(codeHist.getHeight());

					code.setActive(true);
					code.setClosed(false);

					Code code1=code;
					IndexResponse result = esClient.index(i->i.index(IndicesNames.CODE).id(codeHist.getCodeId()).document(code1));
					if(result==null||result.result()==null){
						System.out.println("Failed to create code");
						return false;
					}
					if(!CREATED.equals(result.result().jsonValue()) && !UPDATED.equals(result.result().jsonValue())){
						System.out.println("Failed to create code");
						return false;
					}
					System.out.println(result.result());

					// Create news
					News.createNews(esClient, codeHist.getId(), codeHist.getSigner(), PUBLISH,
							Feip.FeipProtocol.CODE.getName(), codeHist.getId(), codeHist.getName(), codeHist.getDesc(),
							codeHist.getHeight(), codeHist.getTime());
					return true;
				}else {
					System.out.println("Code already exists");
					return false;
				}

			case STOP, RECOVER, CLOSE:
				if (codeHist.getCodeIds() == null || codeHist.getCodeIds().isEmpty()) {
					System.out.println("CodeIds is null or empty");
					return false;
				}

				EsUtils.MgetResult<Code> result = EsUtils.getMultiByIdList(esClient, IndicesNames.CODE, codeHist.getCodeIds(), Code.class);
				List<Code> codes = result.getResultList();

				List<Code> updatedCodes = new ArrayList<>();
				for (Code codeItem : codes) {
					if (Boolean.TRUE.equals(codeItem.isClosed())) {
						continue;
					}

					if (!codeItem.getOwner().equals(codeHist.getSigner())) {
						Freer resultCid = EsUtils.getById(esClient, IndicesNames.FREER, codeHist.getSigner(), Freer.class);
						if (resultCid.getMaster() == null || !resultCid.getMaster().equals(codeHist.getSigner())) {
							System.out.println("Code owner is not the same as the signer");
							continue;
						}
					}

					switch (codeHist.getOp()) {
						case STOP -> codeItem.setActive(false);
						case RECOVER -> codeItem.setActive(true);
						case CLOSE -> {
							codeItem.setClosed(true);
							codeItem.setActive(false);
						}
					}

					codeItem.setLastTxId(codeHist.getId());
					codeItem.setLastTime(codeHist.getTime());
					codeItem.setLastHeight(codeHist.getHeight());

					updatedCodes.add(codeItem);
				}

				if(updatedCodes.isEmpty()){
					System.out.println("No valid codes to be updated");
					return true;
				}

				BulkRequest.Builder br = new BulkRequest.Builder();
				for (Code updatedCode : updatedCodes) {
					br.operations(op -> op
							.index(idx -> idx
									.index(IndicesNames.CODE)
									.id(updatedCode.getId())
									.document(updatedCode)
							)
					);
				}
				BulkResponse result1 = esClient.bulk(br.build());
				if(result1.errors()){
					System.out.println("Failed to bulk update code");
					return false;
				} else System.out.println("Done");
				// Create news
				News.createNews(esClient, codeHist.getId(), codeHist.getSigner(), codeHist.getOp(),
						Feip.FeipProtocol.CODE.getName(), null, null,StringUtils.listToString(codeHist.getCodeIds()),
						codeHist.getHeight(), codeHist.getTime());
				return true;

			case UPDATE:
				code = EsUtils.getById(esClient, IndicesNames.CODE, codeHist.getCodeId(), Code.class);

				if(code==null) {
					System.out.println("Code not found");
					return false;
				}

				if(Boolean.TRUE.equals(code.isClosed())) {
					System.out.println("Code is closed");
					return false;
				}

				if(! code.getOwner().equals(codeHist.getSigner())) {
					System.out.println("Code owner is not the same as the signer");
					return false;
				}

				if(Boolean.FALSE.equals(code.isActive())) {
					System.out.println("Code is not active");
					return false;
				}

				code.setName(codeHist.getName());
				code.setVer(codeHist.getVer());
				code.setDid(codeHist.getDid());
				code.setDesc(codeHist.getDesc());
				code.setLangs(codeHist.getLangs());
				code.setHome(codeHist.getHome());
				code.setProtocols(codeHist.getProtocols());
				code.setWaiters(codeHist.getWaiters());

				code.setLastTxId(codeHist.getId());
				code.setLastTime(codeHist.getTime());
				code.setLastHeight(codeHist.getHeight());


				Code app2 = code;

				IndexResponse result3 = esClient.index(i->i.index(IndicesNames.CODE).id(codeHist.getCodeId()).document(app2));
				System.out.println(result3.result());
				return CREATED.equals(result3.result().jsonValue()) || UPDATED.equals(result3.result().jsonValue());
			case RATE:
				code = EsUtils.getById(esClient, IndicesNames.CODE, codeHist.getCodeId(), Code.class);

				if(code==null) {
					System.out.println("Code not found");
					return false;
				}

				if(code.getOwner().equals(codeHist.getSigner())) {
					System.out.println("Code owner is the same as the signer");
					return false;
				}

				if((codeHist.getCdd()==null || codeHist.getRate()==null)){
					System.out.println("Cdd or rate is null");
					return false;
				}

				if(code.gettCdd()==null||code.gettRate()==null){
					code.settRate(Float.valueOf(codeHist.getRate()));
					code.settCdd(codeHist.getCdd());
				}else{
					code.settRate(
							(code.gettRate()*code.gettCdd()+codeHist.getRate()*codeHist.getCdd())
									/(code.gettCdd()+codeHist.getCdd())
					);
					code.settCdd(code.gettCdd() + codeHist.getCdd());
				}

				code.settCdd(code.gettCdd()+codeHist.getCdd());
				code.setLastTxId(codeHist.getId());
				code.setLastTime(codeHist.getTime());
				code.setLastHeight(codeHist.getHeight());

				Code code3 = code;

				IndexResponse result4 = esClient.index(i->i.index(IndicesNames.CODE).id(codeHist.getCodeId()).document(code3));
				System.out.println(result4.result());
				return CREATED.equals(result4.result().jsonValue()) || UPDATED.equals(result4.result().jsonValue());
		}
		return false;
	}
}
