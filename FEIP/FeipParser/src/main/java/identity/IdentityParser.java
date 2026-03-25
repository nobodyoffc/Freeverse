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

import static constants.FieldNames.CID;
import static constants.FieldNames.LAST_HEIGHT;
import static constants.IndicesNames.*;
import static constants.Values.CREATED;
import static constants.Values.NOOP;
import static constants.Values.UPDATED;

public class IdentityParser {

	private static boolean isValidEsResult(String jsonValue) {
		return CREATED.equals(jsonValue) || UPDATED.equals(jsonValue) || NOOP.equals(jsonValue);
	}

	public FreerHist makeCid(OpReturn opre, Feip feip) throws ElasticsearchException {

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

		FreerHist freerHist = new FreerHist();

		freerHist.setSigner(opre.getSigner());
		freerHist.setSn(feip.getSn());
		freerHist.setVer(feip.getVer());
		freerHist.setHeight(opre.getHeight());
		freerHist.setId(opre.getId());
		freerHist.setIndex(opre.getTxIndex());
		freerHist.setTime(opre.getTime());
		if(cidRaw.getOp().equals("register")||cidRaw.getOp().equals("unregister")) {
			freerHist.setOp(cidRaw.getOp());
			if(cidRaw.getOp().equals("register")) {
				if(cidRaw.getName()==null
						||cidRaw.getName().equals("")
						||cidRaw.getName().contains(" ")
						||cidRaw.getName().contains("@")
						||cidRaw.getName().contains("#")
						||cidRaw.getName().contains("/")
				){
					System.out.println("Name is invalid");
					return null;
				}
				freerHist.setName(cidRaw.getName());
			}
		}else {
			System.out.println("Op is invalid");
			return null;
		}

		return freerHist;
	}

	public FreerHist makeNobody(OpReturn opre, Feip feip) {

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
		FreerHist freerHist = new FreerHist();

		freerHist.setSigner(opre.getSigner());
		freerHist.setSn(feip.getSn());
		freerHist.setVer(feip.getVer());
		freerHist.setHeight(opre.getHeight());
		freerHist.setId(opre.getId());
		freerHist.setIndex(opre.getTxIndex());
		freerHist.setTime(opre.getTime());
		freerHist.setPrikey(nobodyRaw.getPrikey());

		return freerHist;
	}

	private String addrFromPrikey(String priKey) {

		return KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(priKey));
	}

	public FreerHist makeMaster(OpReturn opre, Feip feip) {

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

		if(masterRaw.getCipherPriKey()==null){
			System.out.println("Prikey cipher is null");
			return null;
		}

		if(masterRaw.getMaster()==null){
			System.out.println("Master FID is null");
			return null;
		}

		if(!KeyTools.isGoodFid(masterRaw.getMaster())){
			System.out.println("Master is not a good fid");
			return null;
		}

		FreerHist freerHist = new FreerHist();

		freerHist.setSigner(opre.getSigner());
		freerHist.setSn(feip.getSn());
		freerHist.setVer(feip.getVer());
		freerHist.setHeight(opre.getHeight());
		freerHist.setId(opre.getId());
		freerHist.setIndex(opre.getTxIndex());
		freerHist.setTime(opre.getTime());
		freerHist.setMaster(masterRaw.getMaster());
		freerHist.setCipherPrikey(masterRaw.getCipherPriKey());
		freerHist.setAlg(masterRaw.getAlg());

		return freerHist;
	}

	public FreerHist makeHome(OpReturn opre, Feip feip) {

		Gson gson = new Gson();
		HomeOpData homeRaw;
		try {
			homeRaw = gson.fromJson(gson.toJson(feip.getData()), HomeOpData.class);
		}catch(Exception e) {
			System.out.println("Bad home data");
			return null;
		}
		if(homeRaw ==null){
			System.out.println("home is null");
			return null;
		}

		if(homeRaw.getOp()==null){
			System.out.println("OP is null");
			return null;
		}

		if(!(homeRaw.getOp().equals("register") || homeRaw.getOp().equals("unregister"))){
			System.out.println("Op is invalid");
			return null;
		}

		if(homeRaw.getOp().equals("register") && homeRaw.getHome()==null){
			System.out.println("Home is absent in register.");
			return null;
		}

		FreerHist freerHist = new FreerHist();

		freerHist.setSigner(opre.getSigner());
		freerHist.setSn(feip.getSn());
		freerHist.setVer(feip.getVer());
		freerHist.setHeight(opre.getHeight());
		freerHist.setId(opre.getId());
		freerHist.setIndex(opre.getTxIndex());
		freerHist.setTime(opre.getTime());

		freerHist.setOp(homeRaw.getOp());
		freerHist.setHome(homeRaw.getHome());

		return freerHist;
	}

	public FreerHist makeNoticeFee(OpReturn opre, Feip feip) {

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

		FreerHist freerHist = new FreerHist();

		freerHist.setSigner(opre.getSigner());
		freerHist.setSn(feip.getSn());
		freerHist.setVer(feip.getVer());
		freerHist.setHeight(opre.getHeight());
		freerHist.setId(opre.getId());
		freerHist.setIndex(opre.getTxIndex());
		freerHist.setTime(opre.getTime());

		freerHist.setNoticeFee(noticeFeeRaw.getNoticeFee());

		return freerHist;
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

	public boolean parseCidInfo(ElasticsearchClient esClient, FreerHist freerHist) throws ElasticsearchException, IOException, InterruptedException {

		if(freerHist.getSn().equals("3"))return parseCid(esClient, freerHist);
		if(freerHist.getSn().equals("4"))return parseNobody(esClient, freerHist);
		if(freerHist.getSn().equals("6"))return parseMaster(esClient, freerHist);
		if(freerHist.getSn().equals("9"))return parseHome(esClient, freerHist);
		if(freerHist.getSn().equals("10"))return parseNoticeFee(esClient, freerHist);

		return false;
	}

	private boolean parseNobody(ElasticsearchClient esClient, FreerHist freerHist) throws ElasticsearchException, IOException {
		if(freerHist ==null){
			System.out.println("The freer history is null");
			return false;
		}
		GetResponse<Freer> resultGetCid;
		try {
			GetResponse<Nobody> result = esClient.get(g -> g.index(NOBODY).id(freerHist.getSigner()), Nobody.class);
			if(result.found()){
				System.out.println("Nobody is found");
				return false;
			}

			resultGetCid = esClient.get(g->g.index(FREER).id(freerHist.getSigner()), Freer.class);
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
		cid.setPrikey(freerHist.getPrikey());
		cid.setLastHeight(freerHist.getHeight());
		IndexResponse result = esClient.index(i -> i.index(FREER).id(cid.getId()).document(cid));
		System.out.println(IndicesNames.FREER + ": " + result.result());
		if(!isValidEsResult(result.result().jsonValue())){
			System.out.println("Failed to update cid");
			return false;
		}

		Nobody nobody = new Nobody();
		nobody.setId(freerHist.getSigner());
		nobody.setLeakTime(freerHist.getTime());
		nobody.setLeakHeight(freerHist.getHeight());
		nobody.setPrikey(freerHist.getPrikey());
		nobody.setLeakTxId(freerHist.getId());
		nobody.setLeakTxIndex(freerHist.getIndex());
		IndexResponse result1 = esClient.index(i->i.index(NOBODY).id(freerHist.getSigner()).document(nobody));
		System.out.println(IndicesNames.NOBODY + ": " + result1.result());
		if(!isValidEsResult(result1.result().jsonValue())){
			System.out.println("Failed to index nobody");
			return false;
		}
		// Create News for Nobody operation
		News.createNews(esClient, freerHist.getId(), freerHist.getSigner(), OpNames.ANNOUNCE, Feip.FeipProtocol.NOBODY.getName(),
				freerHist.getSigner(), cid.getCid(), freerHist.getPrikey(), freerHist.getHeight(), freerHist.getTime());

		return true;
	}

	private boolean parseCid(ElasticsearchClient esClient, FreerHist freerHist) throws ElasticsearchException, IOException, InterruptedException {

		if(freerHist ==null){
			System.out.println("Cid is null");
			return false;
		}
		if(freerHist.getOp().equals("register")) {

			//Rule 1
			int suffixLength = 4;
			String cidStr = freerHist.getName()+"_"+ freerHist.getSigner().substring(34-suffixLength);

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
					Freer cid = null;
					GetResponse<Freer> resultGetCid = esClient.get(g->g.index(FREER).id(freerHist.getSigner()), Freer.class);

					if(resultGetCid.found()) cid = resultGetCid.source();

					int nameCount = 0;
					List<String> existingCids = (cid != null) ? cid.getUsedCids() : null;
					if(existingCids != null) {
						nameCount = existingCids.size();
					}
					if(nameCount>=4){
						System.out.println("Name count is greater than 4");
						return false;
					}

					Set<String> usedCidSet = new HashSet<String>();

					boolean isFirstCid = (existingCids == null || existingCids.isEmpty());

					for(int i=0; i<nameCount;i++) {
						usedCidSet.add(existingCids.get(i));
					}
					usedCidSet.add(cidStr1);

					if(usedCidSet.size()>4){
						System.out.println("Used cid set size is greater than 4");
						return false;
					}

					Freer freerToIndex = (cid != null) ? cid : new Freer();
					freerToIndex.setId(freerHist.getSigner());
					freerToIndex.setCid(cidStr1);
					freerToIndex.setUsedCids(usedCidSet.stream().toList());
					if(isFirstCid) freerToIndex.setNameTime(freerHist.getTime());
					freerToIndex.setLastHeight(freerHist.getHeight());

					//rule 3
					IndexResponse result2 = esClient.index(i -> i.index(FREER).id(freerToIndex.getId()).document(freerToIndex));
					System.out.println(result2.result());
					return isValidEsResult(result2.result().jsonValue());

				}else if(resultCidSearch.hits().hits().size()==1 &&
						resultCidSearch.hits().hits().get(0).source().getId().equals(freerHist.getSigner())) {

					//rule 4,5
					Freer cidToUpdate = resultCidSearch.hits().hits().get(0).source();
					if(cidToUpdate==null){
						System.out.println("Cid is null");
						return false;
					}
					cidToUpdate.setCid(cidStr1);
					cidToUpdate.setLastHeight(freerHist.getHeight());

					//rule 3
					IndexResponse result3 = esClient.index(i -> i.index(FREER).id(cidToUpdate.getId()).document(cidToUpdate));
					System.out.println(result3.result());
					return isValidEsResult(result3.result().jsonValue());
				}

				//rule 2
				suffixLength ++;
				cidStr = freerHist.getName()+"_"+ freerHist.getSigner().substring(34-suffixLength);
                //esClient.esClient.get(g->g.index(Indices.CidIndex).id(opre.getSigner()), Cid.class);
			}

		}else if(freerHist.getOp().equals("unregister")) {

			GetResponse<Freer> result = esClient.get(g -> g.index(FREER).id(freerHist.getSigner()), Freer.class);

			if(result.found()){
				Freer cid = result.source();
				if(cid==null){
					System.out.println("Cid is null");
					return false;
				}
				if(!"".equals(cid.getCid())){
					cid.setCid("");
					cid.setLastHeight(freerHist.getHeight());

					//rule 6
					IndexResponse result4 = esClient.index(i -> i.index(FREER).id(cid.getId()).document(cid));
					System.out.println(result4.result());
					return isValidEsResult(result4.result().jsonValue());
				}
			}
		}

		System.out.println("Bad cid operation");
		return false;
	}


	private boolean parseMaster(ElasticsearchClient esClient, FreerHist freerHist) throws ElasticsearchException, IOException {

		if(freerHist ==null){
			System.out.println("Cid is null");
			return false;
		}
		GetResponse<Freer> resultGetCid = esClient.get(g->g.index(FREER).id(freerHist.getSigner()), Freer.class);

		if(resultGetCid.found()) {
			Freer cid  = resultGetCid.source();
			if(cid==null){
				System.out.println("Cid is null");
				return false;
			}
			if(cid.getMaster()==null || cid.getMaster().isBlank()) {
				cid.setMaster(freerHist.getMaster());
				cid.setLastHeight(freerHist.getHeight());
				IndexResponse result5 = esClient.index(i -> i.index(FREER).id(cid.getId()).document(cid));
				System.out.println(result5.result());
				return isValidEsResult(result5.result().jsonValue());
			}
			System.out.println("Master had been set.");
			return false;
		}else {
			Freer newFreer = new Freer();
			newFreer.setId(freerHist.getSigner());
			newFreer.setMaster(freerHist.getMaster());
			newFreer.setLastHeight(freerHist.getHeight());
			IndexResponse result6 = esClient.index(i -> i.index(FREER).id(newFreer.getId()).document(newFreer));
			System.out.println(result6.result());
			return isValidEsResult(result6.result().jsonValue());
		}
	}

	private boolean parseHome(ElasticsearchClient esClient, FreerHist freerHist) throws ElasticsearchException, IOException {

		if(freerHist ==null){
			System.out.println("Cid is null");
			return false;
		}

		GetResponse<Freer> resultGetCid = esClient.get(g->g.index(FREER).id(freerHist.getSigner()), Freer.class);

		if(freerHist.getOp().equals("register")) {
			if(resultGetCid.found()) {
				Freer cid = resultGetCid.source();
				if(cid == null){
					System.out.println("Cid is null");
					return false;
				}
				cid.setHome(freerHist.getHome());
				cid.setLastHeight(freerHist.getHeight());
				IndexResponse result7 = esClient.index(i -> i.index(FREER).id(cid.getId()).document(cid));
				System.out.println(result7.result());
				return isValidEsResult(result7.result().jsonValue());
			}else {
				Freer newFreer = new Freer();
				newFreer.setId(freerHist.getSigner());
				newFreer.setHome(freerHist.getHome());
				newFreer.setLastHeight(freerHist.getHeight());
				IndexResponse result8 = esClient.index(i -> i.index(FREER).id(newFreer.getId()).document(newFreer));
				System.out.println(result8.result());
				return isValidEsResult(result8.result().jsonValue());
			}
		}else if(freerHist.getOp().equals("unregister")) {
			if(resultGetCid.found()) {
				Freer cid  = resultGetCid.source();
				if(cid==null){
					System.out.println("Cid is null");
					return false;
				}
				if(cid.getHome() ==null || cid.getHome().isEmpty()) {
					System.out.println("Links is null");
					return false;
				}else {
					cid.setHome(null);
					cid.setLastHeight(freerHist.getHeight());
					IndexResponse result9 = esClient.index(i -> i.index(FREER).id(cid.getId()).document(cid));
					System.out.println(result9.result());
					return isValidEsResult(result9.result().jsonValue());
				}
			}
		}


		System.out.println("Bad links operation");
		return false;
	}

	private boolean parseNoticeFee(ElasticsearchClient esClient, FreerHist freerHist) throws ElasticsearchException, IOException {
		if(freerHist ==null){
			System.out.println("Cid is null");
			return false;
		}

		GetResponse<Freer> resultGetCid = esClient.get(g->g.index(FREER).id(freerHist.getSigner()), Freer.class);

		if(resultGetCid.found()) {
			Freer cid = resultGetCid.source();
			if(cid == null){
				System.out.println("Cid is null");
				return false;
			}
			cid.setNoticeFee(freerHist.getNoticeFee());
			cid.setLastHeight(freerHist.getHeight());
			IndexResponse result10 = esClient.index(i -> i.index(FREER).id(cid.getId()).document(cid));
			System.out.println(result10.result());
			return isValidEsResult(result10.result().jsonValue());
		}else {
			Freer newFreer = new Freer();
			newFreer.setId(freerHist.getSigner());
			newFreer.setNoticeFee(freerHist.getNoticeFee());
			newFreer.setLastHeight(freerHist.getHeight());
			IndexResponse result11 = esClient.index(i -> i.index(FREER).id(newFreer.getId()).document(newFreer));
			System.out.println(result11.result());
			return isValidEsResult(result11.result().jsonValue());
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
		long newReputation;
		if(cid.getReputation()==null)
			newReputation = repuHist.getReputation();
		else
			newReputation = cid.getReputation()+repuHist.getReputation();

		long newHot;
		if(cid.getHot()==null)
			newHot = repuHist.getHot();
		else
			newHot = cid.getHot()+ repuHist.getHot();

		cid.setReputation(newReputation);
		cid.setHot(newHot);
		cid.setLastHeight(repuHist.getHeight());
		cid.reCalcWeight();

		IndexResponse result12 = esClient.index(i -> i.index(FREER).id(cid.getId()).document(cid));
		System.out.println(result12.result());
		return isValidEsResult(result12.result().jsonValue());
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
				return isValidEsResult(result13.result().jsonValue());

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
