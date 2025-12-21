package identity;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.*;
import com.google.gson.Gson;
import constants.IndicesNames;
import constants.OpNames;
import constants.Values;
import core.crypto.Hash;
import core.crypto.KeyTools;
import data.fcData.News;
import data.feipData.*;
import data.fchData.Freer;
import data.fchData.Nobody;
import data.fchData.OpReturn;
import startFEIP.StartFEIP;
import utils.EsUtils;

import java.io.IOException;
import java.util.*;

import static constants.FieldNames.LAST_HEIGHT;
import static constants.IndicesNames.*;
import static constants.Values.CREATED;
import static constants.Values.UPDATED;

public class IdentityParser {

	public CidHist makeCid(OpReturn opre, Feip feip) throws ElasticsearchException {

		Gson gson = new Gson();
		CidOpData cidRaw;
		try {
			cidRaw = gson.fromJson(gson.toJson(feip.getData()), CidOpData.class);
		}catch(Exception e) {
			System.out.println("Bad cid data");
			return null;
		}
		if(cidRaw==null){
			System.out.println("Cid is null");
			return null;
		}

		if(cidRaw.getOp()==null){
			System.out.println("OP is null");
			return null;
		}

		CidHist cidHist = new CidHist();

		cidHist.setSigner(opre.getSigner());
		cidHist.setSn(feip.getSn());
		cidHist.setVer(feip.getVer());
		cidHist.setHeight(opre.getHeight());
		cidHist.setId(opre.getId());
		cidHist.setIndex(opre.getTxIndex());
		cidHist.setTime(opre.getTime());
		if(cidRaw.getOp().equals("register")||cidRaw.getOp().equals("unregister")) {
			cidHist.setOp(cidRaw.getOp());
			if(cidRaw.getOp().equals("register")) {
				if(cidRaw.getName()==null
						||cidRaw.getName().equals("")
						||cidRaw.getName().contains(" ")
						||cidRaw.getName().contains("@")
						||cidRaw.getName().contains("/")
				){
					System.out.println("Name is invalid");
					return null;
				}
				cidHist.setName(cidRaw.getName());
			}
		}else {
			System.out.println("Op is invalid");
			return null;
		}

		return cidHist;
	}

	public CidHist makeNobody(OpReturn opre, Feip feip) {

		Gson gson = new Gson();
		NobodyOpData nobodyRaw;
		try {
			nobodyRaw = gson.fromJson(gson.toJson(feip.getData()), NobodyOpData.class);
		}catch(Exception e) {
			System.out.println("Bad nobody data");
			return null;
		}
		if(nobodyRaw==null){
			System.out.println("Nobody is null");
			return null;
		}
		if(nobodyRaw.getPrikey()==null){
			System.out.println("Prikey is null");
			return null;
		}
		if(! addrFromPrikey(nobodyRaw.getPrikey()).equals(opre.getSigner())){
			System.out.println("Prikey is not the signer");
			return null;
		}
		CidHist cidHist = new CidHist();

		cidHist.setSigner(opre.getSigner());
		cidHist.setSn(feip.getSn());
		cidHist.setVer(feip.getVer());
		cidHist.setHeight(opre.getHeight());
		cidHist.setId(opre.getId());
		cidHist.setIndex(opre.getTxIndex());
		cidHist.setTime(opre.getTime());
		cidHist.setPrikey(nobodyRaw.getPrikey());

		return cidHist;
	}

	private String addrFromPrikey(String priKey) {

		return KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(priKey));
	}

	public CidHist makeMaster(OpReturn opre, Feip feip) {

		Gson gson = new Gson();
		MasterOpData masterRaw;
		try {
			masterRaw = gson.fromJson(gson.toJson(feip.getData()), MasterOpData.class);
		}catch(Exception e) {
			System.out.println("Bad master data");
			return null;
		}
		if(masterRaw==null){
			System.out.println("Master is null");
			return null;
		}
		if(masterRaw.getPromise()==null){
			System.out.println("Promise is null");
			return null;
		}
		if(!masterRaw.getPromise().equals("The master owns all my rights.")){
			System.out.println("Promise is not the master owns all my rights.");
			return null;
		}

		if(!KeyTools.isGoodFid(masterRaw.getMaster())){
			System.out.println("Master is not a good fid");
			return null;
		}

		CidHist cidHist = new CidHist();

		cidHist.setSigner(opre.getSigner());
		cidHist.setSn(feip.getSn());
		cidHist.setVer(feip.getVer());
		cidHist.setHeight(opre.getHeight());
		cidHist.setId(opre.getId());
		cidHist.setIndex(opre.getTxIndex());
		cidHist.setTime(opre.getTime());
		cidHist.setMaster(masterRaw.getMaster());
		cidHist.setCipherPrikey(masterRaw.getCipherPriKey());
		cidHist.setAlg(masterRaw.getAlg());

		return cidHist;
	}

	public CidHist makeHomepage(OpReturn opre, Feip feip) {

		Gson gson = new Gson();
		HomepageOpData homepageRaw;
		try {
			homepageRaw = gson.fromJson(gson.toJson(feip.getData()), HomepageOpData.class);
		}catch(Exception e) {
			System.out.println("Bad homepage data");
			return null;
		}
		if(homepageRaw ==null){
			System.out.println("Homepage is null");
			return null;
		}

		if(homepageRaw.getHomepages()== null || homepageRaw.getHomepages().get(0) == null || homepageRaw.getHomepages().get(0).isBlank()){
			System.out.println("Homepage is null");
			return null;
		}

		if(homepageRaw.getOp()==null){
			System.out.println("OP is null");
			return null;
		}

		if(!(homepageRaw.getOp().equals("register") || homepageRaw.getOp().equals("unregister"))){
			System.out.println("Op is invalid");
			return null;
		}

		CidHist cidHist = new CidHist();

		cidHist.setSigner(opre.getSigner());
		cidHist.setSn(feip.getSn());
		cidHist.setVer(feip.getVer());
		cidHist.setHeight(opre.getHeight());
		cidHist.setId(opre.getId());
		cidHist.setIndex(opre.getTxIndex());
		cidHist.setTime(opre.getTime());

		cidHist.setOp(homepageRaw.getOp());
		cidHist.setHomepages(homepageRaw.getHomepages());

		return cidHist;
	}

	public CidHist makeNoticeFee(OpReturn opre, Feip feip) {

		Gson gson = new Gson();
		NoticeFeeOpData noticeFeeRaw;
		try {
			noticeFeeRaw = gson.fromJson(gson.toJson(feip.getData()), NoticeFeeOpData.class);
		}catch(Exception e) {
			System.out.println("Bad notice fee data");
			return null;
		}
		if(noticeFeeRaw ==null){
			System.out.println("Notice fee is null");
			return null;
		}

		CidHist cidHist = new CidHist();

		cidHist.setSigner(opre.getSigner());
		cidHist.setSn(feip.getSn());
		cidHist.setVer(feip.getVer());
		cidHist.setHeight(opre.getHeight());
		cidHist.setId(opre.getId());
		cidHist.setIndex(opre.getTxIndex());
		cidHist.setTime(opre.getTime());

		cidHist.setNoticeFee(noticeFeeRaw.getNoticeFee());

		return cidHist;
	}

	public RepuHist makeReputation(OpReturn opre, Feip feip) {
		if (opre.getCdd() < StartFEIP.CddRequired) return null;
		Gson gson = new Gson();
		ReputationOpData reputationRaw;
		try {
			reputationRaw = gson.fromJson(gson.toJson(feip.getData()), ReputationOpData.class);
		}catch(Exception e) {
			System.out.println("Bad reputation data");
			return null;
		}
		if(reputationRaw ==null){
			System.out.println("Reputation is null");
			return null;
		}
		if(reputationRaw.getRate()==null){
			System.out.println("Rate is null");
			return null;
		}

		RepuHist repuHist = new RepuHist();

		repuHist.setHeight(opre.getHeight());
		repuHist.setId(opre.getId());
		repuHist.setIndex(opre.getTxIndex());
		repuHist.setTime(opre.getTime());

		repuHist.setRater(opre.getSigner());
		repuHist.setRatee(opre.getRecipient());

		repuHist.setHot(opre.getCdd());


		if(reputationRaw.getRate().equals(Values.GOOD))repuHist.setReputation(opre.getCdd());
		if(reputationRaw.getRate().equals(Values.BAD))repuHist.setReputation(opre.getCdd()*(-1));

		repuHist.setRate(reputationRaw.getRate());
		repuHist.setCause(reputationRaw.getCause());
		return repuHist;
	}

	public boolean parseCidInfo(ElasticsearchClient esClient, CidHist cidHist) throws ElasticsearchException, IOException, InterruptedException {

		if(cidHist.getSn().equals("3"))return parseCid(esClient, cidHist);
		if(cidHist.getSn().equals("4"))return parseNobody(esClient, cidHist);
		if(cidHist.getSn().equals("6"))return parseMaster(esClient, cidHist);
		if(cidHist.getSn().equals("9"))return parseHomepage(esClient, cidHist);
		if(cidHist.getSn().equals("10"))return parseNoticeFee(esClient, cidHist);

		return false;
	}

	private boolean parseNobody(ElasticsearchClient esClient, CidHist cidHist) throws ElasticsearchException, IOException {
		if(cidHist==null){
			System.out.println("Cid is null");
			return false;
		}
		GetResponse<Freer> resultGetCid;
		try {
			GetResponse<Nobody> result = esClient.get(g -> g.index(NOBODY).id(cidHist.getSigner()), Nobody.class);
			if(result.found()){
				System.out.println("Nobody is found");
				return false;
			}

			resultGetCid = esClient.get(g->g.index(FREER).id(cidHist.getSigner()), Freer.class);
			if(resultGetCid==null){
				System.out.println("Cid is null");
				return false;
			}
		}catch(Exception e) {
			System.out.println("Error getting cid");
			return false;
		}

		if(!resultGetCid.found()) {
			System.out.println("Cid is not found");
			return false;
		}

		Freer cid  = resultGetCid.source();
		if(cid==null){
			System.out.println("Cid is null");
			return false;
		}
		cid.setPrikey(cidHist.getPrikey());
		cid.setLastHeight(cidHist.getHeight());
		IndexResponse result = esClient.index(i->i.index(FREER).id(cidHist.getSigner()).document(cid));
		System.out.println(IndicesNames.FREER + ": " + result.result());
		if(!result.result().jsonValue().equals("created") && !result.result().jsonValue().equals(UPDATED)){
			System.out.println("Failed to index cid");
			return false;
		}

		Nobody nobody = new Nobody();
		nobody.setId(cidHist.getSigner());
		nobody.setLeakTime(cidHist.getTime());
		nobody.setLeakHeight(cidHist.getHeight());
		nobody.setPrikey(cidHist.getPrikey());
		nobody.setLeakTxId(cidHist.getId());
		nobody.setLeakTxIndex(cidHist.getIndex());
		IndexResponse result1 = esClient.index(i->i.index(NOBODY).id(cidHist.getSigner()).document(nobody));
		System.out.println(IndicesNames.NOBODY + ": " + result1.result());
		if(!result1.result().jsonValue().equals("created") && !result1.result().jsonValue().equals(UPDATED)){
			System.out.println("Failed to index nobody");
			return false;
		}
		// Create News for Nobody operation
		News.createNews(esClient, cidHist.getId(), cidHist.getSigner(), OpNames.ANNOUNCE, Feip.FeipProtocol.NOBODY.getName(),
				cidHist.getSigner(), cid.getCid(), cidHist.getPrikey(), cidHist.getHeight(), cidHist.getTime());

		return true;
	}

	private boolean parseCid(ElasticsearchClient esClient, CidHist cidHist) throws ElasticsearchException, IOException, InterruptedException {

		if(cidHist==null){
			System.out.println("Cid is null");
			return false;
		}
		if(cidHist.getOp().equals("register")) {

			//Rule 1
			int suffixLength = 4;
			String cidStr = cidHist.getName()+"_"+cidHist.getSigner().substring(34-suffixLength);

			while(true) {
				String cidStr1 = cidStr;
				SearchResponse<Freer> resultCidSearch = esClient.search(s->s
								.query(q->q.term(t->t.field("usedCids").value(cidStr1)))
								.index(FREER)
						, Freer.class);

				if(resultCidSearch==null||resultCidSearch.hits()==null||resultCidSearch.hits().total()==null){
					System.out.println("Result cid search is null");
					return false;
				}

				if(resultCidSearch.hits().total().value()==0) {
					Freer cid = new Freer();
					GetResponse<Freer> resultGetCid = esClient.get(g->g.index(FREER).id(cidHist.getSigner()), Freer.class);

					if(resultGetCid.found()) cid = resultGetCid.source();

					//rule 7
					if(cid==null){
						System.out.println("Cid is null");
						return false;
					}
					cid.setCid(cidStr1);
					cid.setId(cidHist.getSigner());

					int nameCount = 0;
					if(cid.getUsedCids()!=null) {
						nameCount = cid.getUsedCids().size();
					}
					if(nameCount>=4){
						System.out.println("Name count is greater than 4");
						return false;
					}

					Set<String> usedCidSet = new HashSet<String>();

					if(cid.getUsedCids()==null || cid.getUsedCids().size()==0)
						cid.setNameTime(cidHist.getTime());

					for(int i=0; i<nameCount;i++) {
						usedCidSet.add(cid.getUsedCids().get(i));
					}
					usedCidSet.add(cidStr1);

					if(usedCidSet.size()>4){
						System.out.println("Used cid set size is greater than 4");
						return false;
					}
					cid.setUsedCids(usedCidSet.stream().toList());

					cid.setLastHeight(cidHist.getHeight());

					Freer cid1 = cid;
					//rule 3
					IndexResponse result2 = esClient.index(i->i.index(FREER).id(cidHist.getSigner()).document(cid1));
					System.out.println(result2.result());
					return CREATED.equals(result2.result().jsonValue()) || UPDATED.equals(result2.result().jsonValue());

				}else if(resultCidSearch.hits().hits().size()==1 &&
						resultCidSearch.hits().hits().get(0).source().getId().equals(cidHist.getSigner())) {

					//rule 4,5
					Freer cid = resultCidSearch.hits().hits().get(0).source();
					if(cid==null){
						System.out.println("Cid is null");
						return false;
					}
					cid.setCid(cidStr1);
					cid.setLastHeight(cidHist.getHeight());

					//rule 3
					IndexResponse result3 = esClient.index(i->i.index(FREER).id(cid.getId()).document(cid));
					System.out.println(result3.result());
					return CREATED.equals(result3.result().jsonValue()) || UPDATED.equals(result3.result().jsonValue());
				}

				//rule 2
				suffixLength ++;
				cidStr = cidHist.getName()+"_"+cidHist.getSigner().substring(34-suffixLength);
                //esClient.esClient.get(g->g.index(Indices.CidIndex).id(opre.getSigner()), Cid.class);
			}

		}else if(cidHist.getOp().equals("unregister")) {

			GetResponse<Freer> result = esClient.get(g -> g.index(FREER).id(cidHist.getSigner()), Freer.class);

			if(result.found()){
				Freer cid = result.source();
				if(cid==null){
					System.out.println("Cid is null");
					return false;
				}
				if(!"".equals(cid.getCid())){
					Map<String,Object> updata = new HashMap<String,Object>();
					updata.put(FREER, "");
					updata.put(LAST_HEIGHT,cidHist.getHeight());

					//rule 6
					UpdateResponse<Freer> result4 = esClient.update(u -> u.index(FREER).id(cidHist.getSigner()).doc(updata), Freer.class);
					System.out.println(result4.result());
					return CREATED.equals(result4.result().jsonValue()) || UPDATED.equals(result4.result().jsonValue());
				}
			}
		}

		System.out.println("Bad cid operation");
		return false;
	}


	private boolean parseMaster(ElasticsearchClient esClient, CidHist cidHist) throws ElasticsearchException, IOException {

		if(cidHist==null){
			System.out.println("Cid is null");
			return false;
		}
		GetResponse<Freer> resultGetCid = esClient.get(g->g.index(FREER).id(cidHist.getSigner()), Freer.class);

		if(resultGetCid.found()) {
			Freer cid  = resultGetCid.source();
			if(cid==null){
				System.out.println("Cid is null");
				return false;
			}
			if(cid.getMaster()==null || cid.getMaster().isBlank()) {
				cid.setMaster(cidHist.getMaster());
				cid.setLastHeight(cidHist.getHeight());
				IndexResponse result5 = esClient.index(i->i.index(FREER).id(cidHist.getSigner()).document(cid));
				System.out.println(result5.result());
				return CREATED.equals(result5.result().jsonValue()) || UPDATED.equals(result5.result().jsonValue());
			}
			System.out.println("Master had been set.");
			return false;
		}else {
			Freer cid = new Freer();
			cid.setId(cidHist.getSigner());
			cid.setMaster(cidHist.getMaster());
			cid.setLastHeight(cidHist.getHeight());
			IndexResponse result6 = esClient.index(i->i.index(FREER).id(cidHist.getSigner()).document(cid));
			System.out.println(result6.result());
			return CREATED.equals(result6.result().jsonValue()) || UPDATED.equals(result6.result().jsonValue());
		}
	}

	private boolean parseHomepage(ElasticsearchClient esClient, CidHist cidHist) throws ElasticsearchException, IOException {

		if(cidHist==null){
			System.out.println("Cid is null");
			return false;
		}

		GetResponse<Freer> resultGetCid = esClient.get(g->g.index(FREER).id(cidHist.getSigner()), Freer.class);

		if(cidHist.getOp().equals("register")) {
			if(resultGetCid.found()) {
				Freer cid  = resultGetCid.source();
				if(cid==null){
					System.out.println("Cid is null");
					return false;
				}
				cid.setHomepages(cidHist.getHomepages());
				cid.setLastHeight(cidHist.getHeight());
				IndexResponse result7 = esClient.index(i->i.index(FREER).id(cidHist.getSigner()).document(cid));
				System.out.println(result7.result());
				return CREATED.equals(result7.result().jsonValue()) || UPDATED.equals(result7.result().jsonValue());

			}else {
				Freer cid = new Freer();
				cid.setId(cidHist.getSigner());
				cid.setHomepages(cidHist.getHomepages());
				cid.setLastHeight(cidHist.getHeight());
				IndexResponse result8 = esClient.index(i->i.index(FREER).id(cidHist.getSigner()).document(cid));
				System.out.println(result8.result());
				return CREATED.equals(result8.result().jsonValue()) || UPDATED.equals(result8.result().jsonValue());
			}
		}else if(cidHist.getOp().equals("unregister")) {
			if(resultGetCid.found()) {
				Freer cid  = resultGetCid.source();
				if(cid==null){
					System.out.println("Cid is null");
					return false;
				}
				if(cid.getHomepages() ==null || cid.getHomepages().get(0).isBlank()) {
					System.out.println("Homepages is null");
					return false;
				}else {
					cid.setHomepages(null);
					cid.setLastHeight(cidHist.getHeight());
					IndexResponse result9 = esClient.index(i->i.index(FREER).id(cidHist.getSigner()).document(cid));
					System.out.println(result9.result());
					return CREATED.equals(result9.result().jsonValue()) || UPDATED.equals(result9.result().jsonValue());
				}
			}
		}


		System.out.println("Bad homepage operation");
		return false;
	}

	private boolean parseNoticeFee(ElasticsearchClient esClient, CidHist cidHist) throws ElasticsearchException, IOException {
		if(cidHist==null){
			System.out.println("Cid is null");
			return false;
		}
		GetResponse<Freer> resultGetCid = esClient.get(g->g.index(FREER).id(cidHist.getSigner()), Freer.class);

		if(resultGetCid.found()) {
			Freer cid  = resultGetCid.source();
			if(cid==null){
				System.out.println("Cid is null");
				return false;
			}

			cid.setNoticeFee(cidHist.getNoticeFee());
			cid.setLastHeight(cidHist.getHeight());
			IndexResponse result10 = esClient.index(i->i.index(FREER).id(cidHist.getSigner()).document(cid));
			System.out.println(result10.result());
			return CREATED.equals(result10.result().jsonValue()) || UPDATED.equals(result10.result().jsonValue());

		}else {
			Freer cid = new Freer();
			cid.setId(cidHist.getSigner());
			cid.setNoticeFee(cidHist.getNoticeFee());
			cid.setLastHeight(cidHist.getHeight());
			IndexResponse result11 = esClient.index(i->i.index(FREER).id(cidHist.getSigner()).document(cid));
			System.out.println(result11.result());
			return CREATED.equals(result11.result().jsonValue()) || UPDATED.equals(result11.result().jsonValue());
		}

	}

	public boolean parseReputation(ElasticsearchClient esClient, RepuHist repuHist) throws ElasticsearchException, IOException {
		if(repuHist==null){
			System.out.println("Reputation is null");
			return false;
		}
		GetResponse<Freer> resultGetCid = esClient.get(g->g.index(FREER).id(repuHist.getRatee()), Freer.class);
		Freer cid;
		if(!resultGetCid.found() || resultGetCid.source()==null) {
			System.out.println("Cid is not found");
			return false;
		}
		cid = resultGetCid.source();
		if(cid==null){
			System.out.println("Cid is null");
			return false;
		}
		if(cid.getReputation()==null)
			cid.setReputation(repuHist.getReputation());
		else
			cid.setReputation(cid.getReputation()+repuHist.getReputation());

		if(cid.getHot()==null)
			cid.setHot(repuHist.getHot());
		else
			cid.setHot(cid.getHot()+ repuHist.getHot());

		cid.setLastHeight(repuHist.getHeight());

		cid.reCalcWeight();

		IndexResponse result12 = esClient.index(i -> i.index(FREER).id(repuHist.getRatee()).document(cid));
		System.out.println(result12.result());
		return CREATED.equals(result12.result().jsonValue()) || UPDATED.equals(result12.result().jsonValue());
	}

	public boolean parseNid(ElasticsearchClient esClient, OpReturn opre, Feip feip) throws Exception {

		Gson gson = new Gson();

		NidOpData nidRaw = new NidOpData();

		try {
			nidRaw = gson.fromJson(gson.toJson(feip.getData()), NidOpData.class);
			if(nidRaw==null){
				System.out.println("Nid raw is null");
				return false;
			}
		}catch(com.google.gson.JsonSyntaxException e) {
			System.out.println("Error parsing nid raw");
			return false;
		}

		Nid nid = new Nid();

		long height;
		if(nidRaw.getOp()==null){
			System.out.println("Op is null");
			return false;
		}
		switch(nidRaw.getOp()) {

			case "add":
				if(nidRaw.getName()==null){
					System.out.println("Name is null");
					return false;
				}
				if(nidRaw.getOid()==null){
					System.out.println("Oid is null");
					return false;
				}

				nid.setId(Hash.sha256x2(nidRaw.getName()+opre.getSigner()));
				nid.setName(nidRaw.getName());
				nid.setDesc(nidRaw.getDesc());
				nid.setOid(nidRaw.getOid());

				nid.setNamer(opre.getSigner());
				nid.setNid(nid.getName()+"@"+nid.getNamer());
				nid.setBirthTime(opre.getTime());
				nid.setBirthHeight(opre.getHeight());
				nid.setLastTime(opre.getTime());
				nid.setLastHeight(opre.getHeight());
				nid.setActive(true);

				Nid nid0 = nid;

				IndexResponse result13 = esClient.index(i->i.index(IndicesNames.NID).id(nid0.getId()).document(nid0));
				System.out.println(result13.result());
				return CREATED.equals(result13.result().jsonValue()) || UPDATED.equals(result13.result().jsonValue());

			case "stop","recover":
				if(nidRaw.getNames()==null || nidRaw.getNames().isEmpty()){
					System.out.println("Names is null");
					return false;
				}
				List<String> nameIds = new ArrayList<>();
				for(String name:nidRaw.getNames()){
					String nameId = Hash.sha256x2(name+opre.getSigner());
					nameIds.add(nameId);
				}
				height = opre.getHeight();

				EsUtils.MgetResult<Nid> result = EsUtils.getMultiByIdList(esClient, IndicesNames.NID, nameIds, Nid.class);
				if(result==null||result.getResultList()==null||result.getResultList().isEmpty()){
					System.out.println("Result is null");
					return false;
				}
				BulkRequest.Builder br = new BulkRequest.Builder();
				for(Nid nid1:result.getResultList()){
					if(!nid1.getNamer().equals(opre.getSigner()))continue;
					if(nidRaw.getOp().equals("stop")) nid1.setActive(false);
					else if(nidRaw.getOp().equals("recover")) nid1.setActive(true);
					nid1.setLastTime(opre.getTime());
					nid1.setLastHeight(height);

					br.operations(op -> op
							.index(idx -> idx
									.index(IndicesNames.NID)
									.id(nid1.getId())
									.document(nid1)
							)
					);
				}
				BulkResponse result14 = esClient.bulk(br.build());
				if(result14.errors()){
					System.out.println("Failed");
					return false;
				}
				System.out.println("Done");
				return true;

			default:		
				System.out.println("Invalid operation");
				return false;
		}
	}
}
