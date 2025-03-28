package construct;

import fch.fchData.Cid;
import utils.EsUtils;
import constants.IndicesNames;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;

import com.google.gson.Gson;
import fch.fchData.OpReturn;
import feip.feipData.*;
import startFEIP.StartFEIP;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ConstructParser {

	public ProtocolHistory makeProtocol(OpReturn opre, Feip feip) {
		
		Gson gson = new Gson();

		ProtocolOpData protocolRaw = new ProtocolOpData();
		try {
			protocolRaw = gson.fromJson(gson.toJson(feip.getData()), ProtocolOpData.class);
			if(protocolRaw==null)return null;
		}catch(Exception e) {
			e.printStackTrace();
			try {
				TimeUnit.SECONDS.sleep(5);
			} catch (InterruptedException ex) {
				throw new RuntimeException(ex);
			}
			return null;
		}

		ProtocolHistory protocolHist = new ProtocolHistory();

		if(protocolRaw.getOp()==null)return null;

		protocolHist.setOp(protocolRaw.getOp());

		switch(protocolRaw.getOp()) {

			case "publish":
				if(protocolRaw.getSn()==null|| protocolRaw.getName()==null||"".equals(protocolRaw.getName())) return null;
            	if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired * 100) return null;
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
				if(protocolRaw.getFileUrls()!=null)protocolHist.setFileUrls(protocolRaw.getFileUrls());
				if(protocolRaw.getPreDid()!=null)protocolHist.setPrePid(protocolRaw.getPreDid());
				if(protocolRaw.getWaiters()!=null)protocolHist.setWaiters(protocolRaw.getWaiters());

				break;

			case "update":

				if(protocolRaw.getPid()==null|| protocolRaw.getSn()==null|| protocolRaw.getName()==null||"".equals(protocolRaw.getName()))
					return null;
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
				if(protocolRaw.getFileUrls()!=null)protocolHist.setFileUrls(protocolRaw.getFileUrls());
				if(protocolRaw.getPreDid()!=null)protocolHist.setPrePid(protocolRaw.getPreDid());
				if(protocolRaw.getWaiters()!=null)protocolHist.setWaiters(protocolRaw.getWaiters());

				break;
			case "stop":
			case "recover":
			case "close":
				if (protocolRaw.getPids() == null || protocolRaw.getPids().length == 0) {
					return null;
				}
				protocolHist.setPids(protocolRaw.getPids());
				protocolHist.setId(opre.getId());
				protocolHist.setHeight(opre.getHeight());
				protocolHist.setIndex(opre.getTxIndex());
				protocolHist.setTime(opre.getTime());
				protocolHist.setSigner(opre.getSigner());
				
				if (protocolRaw.getOp().equals("close")) {
					protocolHist.setCloseStatement(protocolRaw.getCloseStatement());
				}
				break;

			case "rate":
				if(protocolRaw.getPid()==null)return null;
				if (opre.getCdd() < StartFEIP.CddRequired) return null;
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
				return null;
		}
		return protocolHist;
	}

	public ServiceHistory makeService(OpReturn opre, Feip feip) {
		
		Gson gson = new Gson();
		ServiceOpData serviceRaw = new ServiceOpData();

		try {
			serviceRaw = gson.fromJson(gson.toJson(feip.getData()), ServiceOpData.class);
			if(serviceRaw==null)return null;
		}catch(Exception e) {
			return null;
		}

		ServiceHistory serviceHist = new ServiceHistory();

		if(serviceRaw.getOp()==null)return null;

		serviceHist.setOp(serviceRaw.getOp());

		switch(serviceRaw.getOp()) {
			case "publish":
				if(serviceRaw.getStdName()==null||"".equals(serviceRaw.getStdName()))return null;
				if(serviceRaw.getSid()!=null) return null;
            	if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired * 100) return null;
				serviceHist.setId(opre.getId());
				serviceHist.setSid(opre.getId());
				serviceHist.setHeight(opre.getHeight());
				serviceHist.setIndex(opre.getTxIndex());
				serviceHist.setTime(opre.getTime());
				serviceHist.setSigner(opre.getSigner());

				if(serviceRaw.getStdName()!=null)serviceHist.setStdName(serviceRaw.getStdName());
				if(serviceRaw.getLocalNames()!=null)serviceHist.setLocalNames(serviceRaw.getLocalNames());
				if(serviceRaw.getDesc()!=null)serviceHist.setDesc(serviceRaw.getDesc());
				if(serviceRaw.getTypes()!=null)serviceHist.setTypes(serviceRaw.getTypes());
				if(serviceRaw.getUrls()!=null)serviceHist.setUrls(serviceRaw.getUrls());
				if(serviceRaw.getWaiters()!=null)serviceHist.setWaiters(serviceRaw.getWaiters());
				if(serviceRaw.getProtocols()!=null)serviceHist.setProtocols(serviceRaw.getProtocols());
				if(serviceRaw.getServices()!=null)serviceHist.setServices(serviceRaw.getServices());
				if(serviceRaw.getCodes()!=null)serviceHist.setCodes(serviceRaw.getCodes());
				if(serviceRaw.getVer()!=null)serviceHist.setVer(serviceRaw.getVer());
				if(serviceRaw.getParams()!=null) {
					serviceHist.setParams(serviceRaw.getParams());
				}
				break;
			case "update":
				if(serviceRaw.getSid()==null) return null;
				if(serviceRaw.getStdName()==null||"".equals(serviceRaw.getStdName())) return null;

				serviceHist.setId(opre.getId());
				serviceHist.setSid(serviceRaw.getSid());
				serviceHist.setHeight(opre.getHeight());
				serviceHist.setIndex(opre.getTxIndex());
				serviceHist.setTime(opre.getTime());
				serviceHist.setSigner(opre.getSigner());

				if(serviceRaw.getStdName()!=null)serviceHist.setStdName(serviceRaw.getStdName());
				if(serviceRaw.getLocalNames()!=null)serviceHist.setLocalNames(serviceRaw.getLocalNames());
				if(serviceRaw.getDesc()!=null)serviceHist.setDesc(serviceRaw.getDesc());
				if(serviceRaw.getTypes()!=null)serviceHist.setTypes(serviceRaw.getTypes());
				if(serviceRaw.getUrls()!=null)serviceHist.setUrls(serviceRaw.getUrls());
				if(serviceRaw.getWaiters()!=null)serviceHist.setWaiters(serviceRaw.getWaiters());
				if(serviceRaw.getProtocols()!=null)serviceHist.setProtocols(serviceRaw.getProtocols());
				if(serviceRaw.getServices()!=null)serviceHist.setServices(serviceRaw.getServices());
				if(serviceRaw.getCodes()!=null)serviceHist.setCodes(serviceRaw.getCodes());
				if(serviceRaw.getVer()!=null)serviceHist.setVer(serviceRaw.getVer());
				if(serviceRaw.getParams()!=null) {
					serviceHist.setParams(serviceRaw.getParams());
				}
				break;
			case "stop":
			case "recover":
			case "close":
				if(serviceRaw.getSids()==null||serviceRaw.getSids().isEmpty()){
					return null;
				}
				serviceHist.setSids(serviceRaw.getSids());
				serviceHist.setId(opre.getId());
				serviceHist.setHeight(opre.getHeight());
				serviceHist.setIndex(opre.getTxIndex());
				serviceHist.setTime(opre.getTime());
				serviceHist.setSigner(opre.getSigner());
				
				if (serviceRaw.getOp().equals("close")) {
					serviceHist.setCloseStatement(serviceRaw.getCloseStatement());
				}
				break;
			case "rate":
				if(serviceRaw.getSid()==null)return null;
				if(serviceRaw.getRate()<0 ||serviceRaw.getRate()>5)return null;
            	if (opre.getCdd() < StartFEIP.CddRequired) return null;
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
				return null;
		}
		return serviceHist;
	}

	public AppHistory makeApp(OpReturn opre, Feip feip) {
		
		Gson gson = new Gson();

		AppOpData appRaw = new AppOpData();

		try {
			appRaw = gson.fromJson(gson.toJson(feip.getData()), AppOpData.class);
			if(appRaw==null)return null;
		}catch(Exception e) {
			return null;
		}

		AppHistory appHist = new AppHistory();

		if(appRaw.getOp()==null)return null;
		appHist.setOp(appRaw.getOp());

		switch(appRaw.getOp()) {

			case "publish":
				if(appRaw.getStdName()==null||"".equals(appRaw.getStdName())) return null;
				if(appRaw.getAid()!=null) return null;
            if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired * 100) return null;
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
				if(appRaw.getUrls()!=null)appHist.setUrls(appRaw.getUrls());
				if(appRaw.getDownloads()!=null)appHist.setDownloads(appRaw.getDownloads());
				if(appRaw.getWaiters()!=null)appHist.setWaiters(appRaw.getWaiters());
				if(appRaw.getProtocols()!=null)appHist.setProtocols(appRaw.getProtocols());
				if(appRaw.getServices() !=null)appHist.setServices(appRaw.getServices());
				if(appRaw.getCodes() !=null)appHist.setCodes(appRaw.getCodes());

				break;

			case "update":
				if(appRaw.getAid()==null) return null;
				if(appRaw.getStdName()==null||"".equals(appRaw.getStdName())) return null;

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
				if(appRaw.getUrls()!=null)appHist.setUrls(appRaw.getUrls());
				if(appRaw.getDownloads()!=null)appHist.setDownloads(appRaw.getDownloads());
				if(appRaw.getWaiters()!=null)appHist.setWaiters(appRaw.getWaiters());
				if(appRaw.getProtocols()!=null)appHist.setProtocols(appRaw.getProtocols());
				if(appRaw.getServices() !=null)appHist.setServices(appRaw.getServices());
				if(appRaw.getCodes() !=null)appHist.setCodes(appRaw.getCodes());

				break;

			case "stop":
			case "recover":
			case "close":
				if(appRaw.getAids()==null||appRaw.getAids().isEmpty()){
					return null;
				}
				appHist.setAids(appRaw.getAids());

				appHist.setId(opre.getId());
				appHist.setHeight(opre.getHeight());
				appHist.setIndex(opre.getTxIndex());
				appHist.setTime(opre.getTime());
				appHist.setSigner(opre.getSigner());
				
				if (appRaw.getOp().equals("close")) {
					appHist.setCloseStatement(appRaw.getCloseStatement());
				}
				break;
			case "rate":
				if(appRaw.getAid()==null)return null;
				if(appRaw.getRate()<0 ||appRaw.getRate()>5)return null;
            if (opre.getCdd() < StartFEIP.CddRequired) return null;
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
				return null;
		}
		return appHist;
	}

	public CodeHistory makeCode(OpReturn opre, Feip feip) {
		
		Gson gson = new Gson();
		CodeOpData codeRaw = new CodeOpData();

		try {
			codeRaw = gson.fromJson(gson.toJson(feip.getData()), CodeOpData.class);
			if(codeRaw==null)return null;
		}catch(Exception e) {
			return null;
		}

		CodeHistory codeHist = new CodeHistory();

		if(codeRaw.getOp()==null)return null;

		codeHist.setOp(codeRaw.getOp());

		switch(codeRaw.getOp()) {
			case "publish":
				if(codeRaw.getName()==null||"".equals(codeRaw.getName())) return null;
				if(codeRaw.getCodeId()!=null) return null;
            if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired * 100) return null;
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
				if(codeRaw.getUrls()!=null)codeHist.setUrls(codeRaw.getUrls());
				if(codeRaw.getLangs()!=null)codeHist.setLangs(codeRaw.getLangs());
				if(codeRaw.getProtocols()!=null)codeHist.setProtocols(codeRaw.getProtocols());
				if(codeRaw.getWaiters()!=null)codeHist.setWaiters(codeRaw.getWaiters());
				break;
			case "update":
				if(codeRaw.getCodeId()==null) return null;
				if(codeRaw.getName()==null||"".equals(codeRaw.getName()))return null;

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
				if(codeRaw.getUrls()!=null)codeHist.setUrls(codeRaw.getUrls());
				if(codeRaw.getLangs()!=null)codeHist.setLangs(codeRaw.getLangs());
				if(codeRaw.getProtocols()!=null)codeHist.setProtocols(codeRaw.getProtocols());
				if(codeRaw.getWaiters()!=null)codeHist.setWaiters(codeRaw.getWaiters());
				break;
			case "stop":
			case "recover":
			case "close":
				if (codeRaw.getCodeIds() == null || codeRaw.getCodeIds().length == 0) {
					return null;
				}
				codeHist.setCodeIds(codeRaw.getCodeIds());

				codeHist.setId(opre.getId());
				codeHist.setHeight(opre.getHeight());
				codeHist.setIndex(opre.getTxIndex());
				codeHist.setTime(opre.getTime());
				codeHist.setSigner(opre.getSigner());
				
				if (codeRaw.getOp().equals("close")) {
					codeHist.setCloseStatement(codeRaw.getCloseStatement());
				}
				break;
			case "rate":
				if(codeRaw.getCodeId()==null) return null;
				if(codeRaw.getRate()<0 ||codeRaw.getRate()>5)return null;
            if (opre.getCdd() < StartFEIP.CddRequired) return null;
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
				return null;
		}
		return codeHist;
	}

	public boolean parseProtocol(ElasticsearchClient esClient, ProtocolHistory protocolHist) throws Exception {
		
		boolean isValid = false;
		if(protocolHist==null)return false;
		Protocol protocol;
		switch (protocolHist.getOp()) {
			case "publish" -> {
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
					protocol.setFileUrls(protocolHist.getFileUrls());

					protocol.setTitle(protocolHist.getType() + protocolHist.getSn() + "V" + protocolHist.getVer() + "_" + protocolHist.getName() + "(" + protocolHist.getLang() + ")");
					protocol.setOwner(protocolHist.getSigner());

					protocol.setBirthTxId(protocolHist.getId());
					protocol.setBirthTime(protocolHist.getTime());
					protocol.setBirthHeight(protocolHist.getHeight());

					protocol.setLastTxId(protocolHist.getId());
					protocol.setLastTime(protocolHist.getTime());
					protocol.setLastHeight(protocolHist.getHeight());

					protocol.setActive(true);
					protocol.setClosed(false);

					Protocol protocol1 = protocol;

					esClient.index(i -> i.index(IndicesNames.PROTOCOL).id(protocolHist.getPid()).document(protocol1));
					isValid = true;
				} else {
					isValid = false;
				}
			}
			case "update" -> {
				protocol = EsUtils.getById(esClient, IndicesNames.PROTOCOL, protocolHist.getPid(), Protocol.class);
				if (protocol == null) {
					isValid = false;
					break;
				}
				if (Boolean.TRUE.equals(protocol.isClosed())) {
					isValid = false;
					break;
				}
				if (!protocol.getOwner().equals(protocolHist.getSigner())) {
					isValid = false;
					break;
				}
				if (Boolean.FALSE.equals(protocol.isActive())) {
					isValid = false;
					break;
				}
				protocol.setType(protocolHist.getType());
				protocol.setSn(protocolHist.getSn());
				protocol.setVer(protocolHist.getVer());
				protocol.setDid(protocolHist.getDid());
				protocol.setName(protocolHist.getName());
				protocol.setLang(protocolHist.getLang());
				protocol.setDesc(protocolHist.getDesc());
				protocol.setPrePid(protocolHist.getPrePid());
				protocol.setFileUrls(protocolHist.getFileUrls());
				protocol.setTitle(protocolHist.getType() + protocolHist.getSn() + "V" + protocolHist.getVer() + "_" + protocolHist.getName() + "(" + protocolHist.getLang() + ")");
				protocol.setLastTxId(protocolHist.getId());
				protocol.setLastTime(protocolHist.getTime());
				protocol.setLastHeight(protocolHist.getHeight());
				Protocol protocol2 = protocol;
				esClient.index(i -> i.index(IndicesNames.PROTOCOL).id(protocolHist.getPid()).document(protocol2));
				isValid = true;
			}
			case "stop", "recover", "close" -> {
				List<String> idList = new ArrayList<>();
				if (protocolHist.getPids() != null && protocolHist.getPids().length > 0) {
					idList.addAll(Arrays.asList(protocolHist.getPids()));
				} else {
					isValid = false;
					break;
				}

				EsUtils.MgetResult<Protocol> result = EsUtils.getMultiByIdList(esClient, IndicesNames.PROTOCOL, idList, Protocol.class);
				List<Protocol> protocols = result.getResultList();

				List<Protocol> updatedProtocols = new ArrayList<>();
				for (Protocol protocolItem : protocols) {
					if (Boolean.TRUE.equals(protocolItem.isClosed())) {
						continue;
					}

					if (!protocolItem.getOwner().equals(protocolHist.getSigner())) {
						Cid resultCid = EsUtils.getById(esClient, IndicesNames.CID, protocolHist.getSigner(), Cid.class);
						if (resultCid.getMaster() == null || !resultCid.getMaster().equals(protocolHist.getSigner())) {
							continue;
						}
					}

					switch (protocolHist.getOp()) {
						case "stop":
							protocolItem.setActive(false);
							break;
						case "recover":
							protocolItem.setActive(true);
							break;
						case "close":
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
					esClient.bulk(br.build());
					isValid = true;
				}
			}
			
			case "rate" -> {
				protocol = EsUtils.getById(esClient, IndicesNames.PROTOCOL, protocolHist.getPid(), Protocol.class);
				if (protocol == null) {
					isValid = false;
					break;
				}
				if (protocol.getOwner().equals(protocolHist.getSigner())) {
					isValid = false;
					break;
				}

				if((protocolHist.getCdd()==null || protocolHist.getRate()==null)){
					isValid=false;
					break;
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
				esClient.index(i -> i.index(IndicesNames.PROTOCOL).id(protocolHist.getPid()).document(protocol3));
				isValid = true;
			}
		}

		return isValid;
	}



	public boolean parseService(ElasticsearchClient esClient, ServiceHistory serviceHist) throws Exception {
		
		boolean isValid = false;
		Service service;
		if(serviceHist==null||serviceHist.getOp()==null)return false;
		switch(serviceHist.getOp()) {
			case "publish":
				service = EsUtils.getById(esClient, IndicesNames.SERVICE, serviceHist.getSid(), Service.class);
				if(service==null) {
					service = new Service();
					service.setId(serviceHist.getId());
					service.setStdName(serviceHist.getStdName());
					service.setLocalNames(serviceHist.getLocalNames());
					service.setDesc(serviceHist.getDesc());
					service.setTypes(serviceHist.getTypes());
					service.setVer(serviceHist.getVer());
					service.setUrls(serviceHist.getUrls());
					service.setWaiters(serviceHist.getWaiters());
					service.setProtocols(serviceHist.getProtocols());
					service.setCodes(serviceHist.getCodes());
					service.setServices(serviceHist.getServices());
					service.setParams(serviceHist.getParams());
					service.setOwner(serviceHist.getSigner());


					service.setLastTxId(serviceHist.getId());
					service.setLastTime(serviceHist.getTime());
					service.setLastHeight(serviceHist.getHeight());

					service.setBirthTime(serviceHist.getTime());
					service.setBirthHeight(serviceHist.getHeight());

					service.setActive(true);
					service.setClosed(false);

					Service service1 = service;
					esClient.index(i->i.index(IndicesNames.SERVICE).id(serviceHist.getSid()).document(service1));
					isValid = true;
				}else {
					isValid=false;
				}
				break;
				case "stop", "recover", "close" : {
					if(serviceHist.getSids()==null||serviceHist.getSids().isEmpty()){
						isValid = false;
						break;
					}
					
					EsUtils.MgetResult<Service> result = EsUtils.getMultiByIdList(esClient, IndicesNames.SERVICE, serviceHist.getSids(), Service.class);
					List<Service> services = result.getResultList();
				
					List<Service> updatedServices = new ArrayList<>();
					for (Service serviceItem : services) {
						if (Boolean.TRUE.equals(serviceItem.isClosed())) {
							continue;
						}
				
						if (!serviceItem.getOwner().equals(serviceHist.getSigner())) {
							Cid resultCid = EsUtils.getById(esClient, IndicesNames.CID, serviceHist.getSigner(), Cid.class);
							if (resultCid.getMaster() == null || !resultCid.getMaster().equals(serviceHist.getSigner())) {
								continue;
							}
						}
				
						switch (serviceHist.getOp()) {
							case "stop":
								serviceItem.setActive(false);
								break;
							case "recover":
								serviceItem.setActive(true);
								break;
							case "close":
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
						esClient.bulk(br.build());
						isValid = true;
					}
				}

			case "update":
				service = EsUtils.getById(esClient, IndicesNames.SERVICE, serviceHist.getSid(), Service.class);

				if(service==null) {
					isValid = false;
					break;
				}

				if(Boolean.TRUE.equals(service.isClosed())) {
					isValid = false;
					break;
				}

				if(! (service.getOwner().equals(serviceHist.getSigner()))) {
					isValid = false;
					break;
				}

				service.setStdName(serviceHist.getStdName());
				service.setLocalNames(serviceHist.getLocalNames());
				service.setDesc(serviceHist.getDesc());
				service.setTypes(serviceHist.getTypes());
				service.setVer(serviceHist.getVer());
				service.setUrls(serviceHist.getUrls());
				service.setWaiters(serviceHist.getWaiters());
				service.setProtocols(serviceHist.getProtocols());
				service.setCodes(serviceHist.getCodes());
				service.setServices(serviceHist.getServices());
				service.setParams(serviceHist.getParams());

				service.setLastTxId(serviceHist.getId());
				service.setLastTime(serviceHist.getTime());
				service.setLastHeight(serviceHist.getHeight());

				Service service4 = service;

				esClient.index(i->i.index(IndicesNames.SERVICE).id(serviceHist.getSid()).document(service4));
				isValid = true;
				break;

			case "rate":
				service = EsUtils.getById(esClient, IndicesNames.SERVICE, serviceHist.getSid(), Service.class);

				if(service==null) {
					isValid = false;
					break;
				}

				if(service.getOwner().equals(serviceHist.getSigner())) {
					isValid = false;
					break;
				}

				if((serviceHist.getCdd()==null || serviceHist.getRate()==null)){
					isValid=false;
					break;
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

				esClient.index(i->i.index(IndicesNames.SERVICE).id(serviceHist.getSid()).document(service5));
				isValid = true;

				break;
		}
		return isValid;
	}

	public boolean parseApp(ElasticsearchClient esClient, AppHistory appHist) throws Exception {
		if(appHist==null||appHist.getOp()==null)return false;
		boolean isValid = false;
		App app;
		switch(appHist.getOp()) {
			case "publish":
				app = EsUtils.getById(esClient, IndicesNames.APP, appHist.getAid(), App.class);
				if(app==null) {
					app = new App();
					app.setId(appHist.getId());
					app.setStdName(appHist.getStdName());
					app.setLocalNames(appHist.getLocalNames());
					app.setDesc(appHist.getDesc());
					app.setTypes(appHist.getTypes());
					app.setVer(appHist.getVer());
					app.setUrls(appHist.getUrls());
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
					esClient.index(i->i.index(IndicesNames.APP).id(appHist.getAid()).document(app1));
					isValid = true;
				}else {
					isValid = false;
				}
				break;

				case "stop", "recover", "close":
				if(appHist.getAids()==null||appHist.getAids().isEmpty()){
					isValid = false;
					break;
				}
			
				EsUtils.MgetResult<App> result = EsUtils.getMultiByIdList(esClient, IndicesNames.APP, appHist.getAids(), App.class);
				List<App> apps = result.getResultList();
			
				List<App> updatedApps = new ArrayList<>();
				for (App appItem : apps) {
					if (Boolean.TRUE.equals(appItem.isClosed())) {
						continue;
					}
			
					if (!appItem.getOwner().equals(appHist.getSigner())) {
						Cid resultCid = EsUtils.getById(esClient, IndicesNames.CID, appHist.getSigner(), Cid.class);
						if (resultCid.getMaster() == null || !resultCid.getMaster().equals(appHist.getSigner())) {
							continue;
						}
					}
			
					switch (appHist.getOp()) {
						case "stop":
							appItem.setActive(false);
							break;
						case "recover":
							appItem.setActive(true);
							break;
						case "close":
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
					esClient.bulk(br.build());
					isValid = true;
				}
				break;
			case "update":
				app = EsUtils.getById(esClient, IndicesNames.APP, appHist.getAid(), App.class);

				if(app==null) {
					isValid = false;
					break;
				}

				if(Boolean.TRUE.equals(app.isClosed())) {
					isValid = false;
					break;
				}

				if(! app.getOwner().equals(appHist.getSigner())) {
					isValid = false;
					break;
				}

				if(Boolean.FALSE.equals(app.isActive())) {
					isValid = false;
					break;
				}

				app.setStdName(appHist.getStdName());
				app.setLocalNames(appHist.getLocalNames());
				app.setDesc(appHist.getDesc());
				app.setTypes(appHist.getTypes());
				app.setVer(appHist.getVer());
				app.setUrls(appHist.getUrls());
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

				esClient.index(i->i.index(IndicesNames.APP).id(appHist.getAid()).document(app2));
				isValid = true;
				break;

			case "rate":
				app = EsUtils.getById(esClient, IndicesNames.APP, appHist.getAid(), App.class);

				if(app==null) {
					isValid = false;
					break;
				}

				if(app.getOwner().equals(appHist.getSigner())) {
					isValid = false;
					break;
				}

				if((appHist.getCdd()==null || appHist.getRate()==null)){
					isValid=false;
					break;
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

				esClient.index(i->i.index(IndicesNames.APP).id(appHist.getAid()).document(app3));
				isValid = true;
				break;
		}
		return isValid;
	}

	public boolean parseCode(ElasticsearchClient esClient, CodeHistory codeHist) throws Exception {
		if(codeHist==null||codeHist.getOp()==null)return false;
		boolean isValid = false;
		Code code;
		switch(codeHist.getOp()) {
			case "publish":
				code = EsUtils.getById(esClient, IndicesNames.CODE, codeHist.getCodeId(), Code.class);
				if(code==null) {
					code = new Code();
					code.setId(codeHist.getId());
					code.setName(codeHist.getName());
					code.setVer(codeHist.getVer());
					code.setDid(codeHist.getDid());
					code.setDesc(codeHist.getDesc());
					code.setLangs(codeHist.getLangs());
					code.setUrls(codeHist.getUrls());
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
					esClient.index(i->i.index(IndicesNames.CODE).id(codeHist.getCodeId()).document(code1));
					isValid = true;
				}else {
					isValid = false;
				}
				break;
				case "stop", "recover", "close":
				if (codeHist.getCodeIds() == null || codeHist.getCodeIds().length == 0) {
					isValid = false;
					break;
				}
			
				EsUtils.MgetResult<Code> result = EsUtils.getMultiByIdList(esClient, IndicesNames.CODE, Arrays.asList(codeHist.getCodeIds()), Code.class);
				List<Code> codes = result.getResultList();
			
				List<Code> updatedCodes = new ArrayList<>();
				for (Code codeItem : codes) {
					if (Boolean.TRUE.equals(codeItem.isClosed())) {
						continue;
					}
			
					if (!codeItem.getOwner().equals(codeHist.getSigner())) {
						Cid resultCid = EsUtils.getById(esClient, IndicesNames.CID, codeHist.getSigner(), Cid.class);
						if (resultCid.getMaster() == null || !resultCid.getMaster().equals(codeHist.getSigner())) {
							continue;
						}
					}
			
					switch (codeHist.getOp()) {
						case "stop":
							codeItem.setActive(false);
							break;
						case "recover":
							codeItem.setActive(true);
							break;
						case "close":
							codeItem.setClosed(true);
							codeItem.setActive(false);
							break;
					}
			
					codeItem.setLastTxId(codeHist.getId());
					codeItem.setLastTime(codeHist.getTime());
					codeItem.setLastHeight(codeHist.getHeight());
			
					updatedCodes.add(codeItem);
				}
			
				if (!updatedCodes.isEmpty()) {
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
					esClient.bulk(br.build());
					isValid = true;
				}
				break;

				case "update":
				code = EsUtils.getById(esClient, IndicesNames.CODE, codeHist.getCodeId(), Code.class);

				if(code==null) {
					isValid = false;
					break;
				}

				if(Boolean.TRUE.equals(code.isClosed())) {
					isValid = false;
					break;
				}

				if(! code.getOwner().equals(codeHist.getSigner())) {
					isValid = false;
					break;
				}

				if(Boolean.FALSE.equals(code.isActive())) {
					isValid = false;
					break;
				}

				code.setName(codeHist.getName());
				code.setVer(codeHist.getVer());
				code.setDid(codeHist.getDid());
				code.setDesc(codeHist.getDesc());
				code.setLangs(codeHist.getLangs());
				code.setUrls(codeHist.getUrls());
				code.setProtocols(codeHist.getProtocols());
				code.setWaiters(codeHist.getWaiters());

				code.setLastTxId(codeHist.getId());
				code.setLastTime(codeHist.getTime());
				code.setLastHeight(codeHist.getHeight());


				Code app2 = code;

				esClient.index(i->i.index(IndicesNames.CODE).id(codeHist.getCodeId()).document(app2));
				isValid = true;
				break;
			case "rate":
				code = EsUtils.getById(esClient, IndicesNames.CODE, codeHist.getCodeId(), Code.class);

				if(code==null) {
					isValid = false;
					break;
				}

				if(code.getOwner().equals(codeHist.getSigner())) {
					isValid = false;
					break;
				}

				if((codeHist.getCdd()==null || codeHist.getRate()==null)){
					isValid=false;
					break;
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

				esClient.index(i->i.index(IndicesNames.CODE).id(codeHist.getCodeId()).document(code3));
				isValid = true;
				break;
		}
		return isValid;
	}
}
