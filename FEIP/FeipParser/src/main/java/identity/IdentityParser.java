package identity;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.google.gson.Gson;
import core.crypto.KeyTools;
import data.feipData.*;
import core.fch.Weight;
import data.fchData.Cid;
import data.fchData.Nobody;
import data.fchData.OpReturn;
import startFEIP.StartFEIP;
import java.io.IOException;
import java.util.*;

import static constants.FieldNames.LAST_HEIGHT;
import static constants.IndicesNames.*;

public class IdentityParser {

	public CidHist makeCid(OpReturn opre, Feip feip) throws ElasticsearchException {

		Gson gson = new Gson();
		CidOpData cidRaw;
		try {
			cidRaw = gson.fromJson(gson.toJson(feip.getData()), CidOpData.class);
		}catch(Exception e) {
			return null;
		}
		if(cidRaw==null)return null;

		if(cidRaw.getOp()==null)return null;

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
				)return null;
				cidHist.setName(cidRaw.getName());
			}
		}else return null;

		return cidHist;
	}

	public CidHist makeNobody(OpReturn opre, Feip feip) {

		Gson gson = new Gson();
		NobodyOpData nobodyRaw;
		try {
			nobodyRaw = gson.fromJson(gson.toJson(feip.getData()), NobodyOpData.class);
		}catch(Exception e) {
			return null;
		}
		if(nobodyRaw==null)return null;
		if(! addrFromPriKey(nobodyRaw.getPrikey()).equals(opre.getSigner()))return null;

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

	private String addrFromPriKey(String priKey) {

		return KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(priKey));
	}

	public CidHist makeMaster(OpReturn opre, Feip feip) {

		Gson gson = new Gson();
		MasterOpData masterRaw;
		try {
			masterRaw = gson.fromJson(gson.toJson(feip.getData()), MasterOpData.class);
		}catch(Exception e) {
			return null;
		}
		if(masterRaw==null)return null;
		if(masterRaw.getPromise()==null)return null;
		if(!masterRaw.getPromise().equals("The master owns all my rights."))return null;

		if(!KeyTools.isGoodFid(masterRaw.getMaster()))return null;

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
			return null;
		}
		if(homepageRaw ==null)return null;

		if(homepageRaw.getHomepages()== null || homepageRaw.getHomepages().get(0) == null || homepageRaw.getHomepages().get(0).isBlank())return null;

		if(homepageRaw.getOp()==null)return null;

		if(!(homepageRaw.getOp().equals("register") || homepageRaw.getOp().equals("unregister"))) return null;

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
			return null;
		}
		if(noticeFeeRaw ==null)return null;

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
			return null;
		}
		if(reputationRaw ==null)return null;
		if(reputationRaw.getRate()==null)return null;

		RepuHist repuHist = new RepuHist();

		repuHist.setHeight(opre.getHeight());
		repuHist.setId(opre.getId());
		repuHist.setIndex(opre.getTxIndex());
		repuHist.setTime(opre.getTime());

		repuHist.setRater(opre.getSigner());
		repuHist.setRatee(opre.getRecipient());

		repuHist.setHot(opre.getCdd());

		if(reputationRaw.getRate().equals("good"))repuHist.setReputation(opre.getCdd());
		if(reputationRaw.getRate().equals("bad"))repuHist.setReputation(opre.getCdd()*(-1));
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
		if(cidHist==null)return false;
		boolean isValid = false;
		GetResponse<Cid> resultGetCid;
		try {
			resultGetCid = esClient.get(g->g.index(CID).id(cidHist.getSigner()), Cid.class);
			if(resultGetCid==null)return false;
		}catch(Exception e) {
			return false;
		}

		if(resultGetCid.found()) {
			Cid cid  = resultGetCid.source();
			if(cid==null)return false;
			if(cid.getPrikey()==null) {
				cid.setPrikey(cidHist.getPrikey());
				cid.setLastHeight(cidHist.getHeight());
				esClient.index(i->i.index(CID).id(cidHist.getSigner()).document(cid));

				Nobody nobody = new Nobody();
				nobody.setId(cidHist.getSigner());
				nobody.setDeathTime(cidHist.getTime());
				nobody.setDeathHeight(cidHist.getHeight());
				nobody.setPrikey(cidHist.getPrikey());
				nobody.setDeathTxId(cidHist.getId());
				nobody.setDeathTxIndex(cidHist.getIndex());
				esClient.index(i->i.index(NOBODY).id(cidHist.getSigner()).document(nobody));
				isValid = true;
			}
		}
		return isValid;
	}

	private boolean parseCid(ElasticsearchClient esClient, CidHist cidHist) throws ElasticsearchException, IOException, InterruptedException {

		boolean isValid = false;
		if(cidHist==null)return false;
		if(cidHist.getOp().equals("register")) {

			//Rule 1
			int suffixLength = 4;
			String cidStr = cidHist.getName()+"_"+cidHist.getSigner().substring(34-suffixLength);

			while(true) {
				String cidStr1 = cidStr;
				SearchResponse<Cid> resultCidSearch = esClient.search(s->s
								.query(q->q.term(t->t.field("usedCids").value(cidStr1)))
								.index(CID)
						, Cid.class);


				if(resultCidSearch.hits().total().value()==0) {
					Cid cid = new Cid();
					GetResponse<Cid> resultGetCid = esClient.get(g->g.index(CID).id(cidHist.getSigner()), Cid.class);

					if(resultGetCid.found()) cid = resultGetCid.source();

					//rule 7

					cid.setCid(cidStr1);
					cid.setId(cidHist.getSigner());

					int nameCount = 0;
					if(cid.getUsedCids()!=null) {
						nameCount = cid.getUsedCids().size();
					}
					if(nameCount>=4)return isValid;

					Set<String> usedCidSet = new HashSet<String>();

					if(cid.getUsedCids()==null || cid.getUsedCids().size()==0)
						cid.setNameTime(cidHist.getTime());

					for(int i=0; i<nameCount;i++) {
						usedCidSet.add(cid.getUsedCids().get(i));
					}
					usedCidSet.add(cidStr1);

					if(usedCidSet.size()>4)return isValid;
					cid.setUsedCids(usedCidSet.stream().toList());

					cid.setLastHeight(cidHist.getHeight());

					Cid cid1 = cid;
					//rule 3
					esClient.index(i->i.index(CID).id(cidHist.getSigner()).document(cid1));

					isValid = true;

					break;
				}else if(resultCidSearch.hits().hits().size()==1 &&
						resultCidSearch.hits().hits().get(0).source().getId().equals(cidHist.getSigner())) {

					//rule 4,5
					Cid cid = resultCidSearch.hits().hits().get(0).source();
					cid.setCid(cidStr1);
					cid.setLastHeight(cidHist.getHeight());

					//rule 3
					esClient.index(i->i.index(CID).id(cid.getId()).document(cid));
					isValid = true;

					break;
				}

				//rule 2
				suffixLength ++;
				cidStr = cidHist.getName()+"_"+cidHist.getSigner().substring(34-suffixLength);
                //esClient.esClient.get(g->g.index(Indices.CidIndex).id(opre.getSigner()), Cid.class);
			}

		}else if(cidHist.getOp().equals("unregister")) {

			GetResponse<Cid> result = esClient.get(g -> g.index(CID).id(cidHist.getSigner()), Cid.class);

			if(result.found()){
				Cid cid = result.source();
				if(!"".equals(cid.getCid())){
					Map<String,Object> updata = new HashMap<String,Object>();
					updata.put(CID, "");
					updata.put(LAST_HEIGHT,cidHist.getHeight());

					//rule 6
					esClient.update(u->u.index(CID).id(cidHist.getSigner()).doc(updata), Cid.class);

					isValid = true;
				}
			}
		}

		return isValid;
	}


	private boolean parseMaster(ElasticsearchClient esClient, CidHist cidHist) throws ElasticsearchException, IOException {

		boolean isValid = false;
		if(cidHist==null)return false;
		GetResponse<Cid> resultGetCid = esClient.get(g->g.index(CID).id(cidHist.getSigner()), Cid.class);

		if(resultGetCid.found()) {
			Cid cid  = resultGetCid.source();
			if(cid.getMaster()==null || cid.getMaster().isBlank()) {
				cid.setMaster(cidHist.getMaster());
				cid.setLastHeight(cidHist.getHeight());
				esClient.index(i->i.index(CID).id(cidHist.getSigner()).document(cid));
				isValid = true;
			}
		}else {
			Cid cid = new Cid();
			cid.setId(cidHist.getSigner());
			cid.setMaster(cidHist.getMaster());
			cid.setLastHeight(cidHist.getHeight());
			esClient.index(i->i.index(CID).id(cidHist.getSigner()).document(cid));
			isValid = true;
		}
		return isValid;
	}

	private boolean parseHomepage(ElasticsearchClient esClient, CidHist cidHist) throws ElasticsearchException, IOException {

		boolean isValid = false;
		if(cidHist==null)return false;
		GetResponse<Cid> resultGetCid = esClient.get(g->g.index(CID).id(cidHist.getSigner()), Cid.class);

		if(cidHist.getOp().equals("register")) {
			if(resultGetCid.found()) {
				Cid cid  = resultGetCid.source();

				cid.setHomepages(cidHist.getHomepages());
				cid.setLastHeight(cidHist.getHeight());
				esClient.index(i->i.index(CID).id(cidHist.getSigner()).document(cid));
				isValid = true;

			}else {
				Cid cid = new Cid();
				cid.setId(cidHist.getSigner());
				cid.setHomepages(cidHist.getHomepages());
				cid.setLastHeight(cidHist.getHeight());
				esClient.index(i->i.index(CID).id(cidHist.getSigner()).document(cid));
				isValid = true;
			}
		}else if(cidHist.getOp().equals("unregister")) {
			if(resultGetCid.found()) {
				Cid cid  = resultGetCid.source();
				if(cid.getHomepages() ==null || cid.getHomepages().get(0).isBlank()) {
					isValid = false;
				}else {
					cid.setHomepages(null);
					cid.setLastHeight(cidHist.getHeight());
					esClient.index(i->i.index(CID).id(cidHist.getSigner()).document(cid));
					isValid = true;
				}
			}
		}


		return isValid;
	}

	private boolean parseNoticeFee(ElasticsearchClient esClient, CidHist cidHist) throws ElasticsearchException, IOException {
		if(cidHist==null)return false;
		boolean isValid;
		GetResponse<Cid> resultGetCid = esClient.get(g->g.index(CID).id(cidHist.getSigner()), Cid.class);

		if(resultGetCid.found()) {
			Cid cid  = resultGetCid.source();

			cid.setNoticeFee(cidHist.getNoticeFee());
			cid.setLastHeight(cidHist.getHeight());
			esClient.index(i->i.index(CID).id(cidHist.getSigner()).document(cid));
			isValid = true;

		}else {
			Cid cid = new Cid();
			cid.setId(cidHist.getSigner());
			cid.setNoticeFee(cidHist.getNoticeFee());
			cid.setLastHeight(cidHist.getHeight());
			esClient.index(i->i.index(CID).id(cidHist.getSigner()).document(cid));
			isValid = true;
		}
		return isValid;
	}

	public boolean parseReputation(ElasticsearchClient esClient, RepuHist repuHist) throws ElasticsearchException, IOException {
		if(repuHist==null)return false;
		boolean isValid = false;
		GetResponse<Cid> resultGetCid = esClient.get(g->g.index(CID).id(repuHist.getRatee()), Cid.class);
		Cid cid;
		if(resultGetCid.found()) {
			cid  = resultGetCid.source();
			if(cid==null){
				isValid =false;
				return isValid;
			}
			if(cid.getReputation()==null)cid.setReputation(repuHist.getReputation());
			else cid.setReputation(cid.getReputation()+repuHist.getReputation());
			if(cid.getHot()==null)cid.setHot(repuHist.getHot());
			else cid.setHot(cid.getHot()+ repuHist.getHot());
			cid.setLastHeight(repuHist.getHeight());
			isValid = true;
		}else{
			cid = new Cid();
			cid.setId(repuHist.getRatee());
			cid.setReputation(repuHist.getReputation());
			cid.setHot(repuHist.getHot());
			cid.setLastHeight(repuHist.getHeight());
			isValid = true;
		}
		GetResponse<Cid> resultAddr = esClient.get(g -> g.index(CID).id(repuHist.getRatee()), Cid.class);
		Cid addr;
		if(resultAddr!=null && resultAddr.source()!=null) {
			addr = resultAddr.source();
			addr.setWeight(addr.getWeight()+(repuHist.getReputation()* Weight.reputationPercentInWeight)/100);
			esClient.index(i -> i.index(CID).id(repuHist.getRatee()).document(addr));
		}
		esClient.index(i -> i.index(CID).id(repuHist.getRatee()).document(cid));
		return isValid;
	}
}
