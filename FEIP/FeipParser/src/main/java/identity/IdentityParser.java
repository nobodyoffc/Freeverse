package identity;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.json.JsonData;
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
import startFEIP.FeipConstants;
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

	private static final Logger log = LoggerFactory.getLogger(IdentityParser.class);

	private static boolean isValidEsResult(String jsonValue) {
		return CREATED.equals(jsonValue) || UPDATED.equals(jsonValue) || NOOP.equals(jsonValue);
	}

	@SuppressWarnings("unchecked")
	private boolean partialUpdateFreer(ElasticsearchClient esClient, String id, Map<String, Object> fields) throws IOException {
		if (id != null) fields.put("id", id);
		UpdateResponse<Map> result = esClient.update(u -> u
				.index(FREER)
				.id(id)
				.doc(fields)
				.docAsUpsert(true),
				Map.class);
		log.info(FREER + ": " + result.result());
		return isValidEsResult(result.result().jsonValue());
	}

	public FreerHist makeCid(OpReturn opre, Feip feip) throws ElasticsearchException {

		Gson gson = new Gson();
		CidOpData cidRaw;
		try {
			cidRaw = gson.fromJson(gson.toJsonTree(feip.getData()), CidOpData.class);
		}catch(Exception e) {
			log.info("Bad cid data");
			return null;
		}
		if(cidRaw==null){
			log.info("Cid is null");
			return null;
		}

		if(cidRaw.getOp()==null){
			log.info("OP is null");
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
						||!FeipConstants.isWithinLimit(cidRaw.getName(), FeipConstants.MAX_NAME_LENGTH)
				){
					log.info("Name is invalid");
					return null;
				}
				freerHist.setName(cidRaw.getName());
			}
		}else {
			log.info("Op is invalid");
			return null;
		}

		return freerHist;
	}

	public FreerHist makeNobody(OpReturn opre, Feip feip) {

		Gson gson = new Gson();
		NobodyOpData nobodyRaw;
		try {
			nobodyRaw = gson.fromJson(gson.toJsonTree(feip.getData()), NobodyOpData.class);
		}catch(Exception e) {
			log.info("Bad nobody data");
			return null;
		}
		if(nobodyRaw==null){
			log.info("Nobody is null");
			return null;
		}
		if(nobodyRaw.getPrikey()==null){
			log.info("Prikey is null");
			return null;
		}
		if(! addrFromPrikey(nobodyRaw.getPrikey()).equals(opre.getSigner())){
			log.info("Prikey is not the signer");
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
			masterRaw = gson.fromJson(gson.toJsonTree(feip.getData()), MasterOpData.class);
		}catch(Exception e) {
			log.info("Bad master data");
			return null;
		}
		if(masterRaw==null){
			log.info("Master is null");
			return null;
		}
		if(masterRaw.getPromise()==null){
			log.info("Promise is null");
			return null;
		}
		if(!masterRaw.getPromise().equals(FeipConstants.PROMISE_MASTER)){
			log.info("Promise is not the master owns all my rights.");
			return null;
		}

		if(masterRaw.getCipherPriKey()==null){
			log.info("Prikey cipher is null");
			return null;
		}

		if(masterRaw.getMaster()==null){
			log.info("Master FID is null");
			return null;
		}

		if(!KeyTools.isGoodFid(masterRaw.getMaster())){
			log.info("Master is not a good fid");
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
			homeRaw = gson.fromJson(gson.toJsonTree(feip.getData()), HomeOpData.class);
		}catch(Exception e) {
			log.info("Bad home data");
			return null;
		}
		if(homeRaw ==null){
			log.info("home is null");
			return null;
		}

		if(homeRaw.getOp()==null){
			log.info("OP is null");
			return null;
		}

		if(!(homeRaw.getOp().equals("register") || homeRaw.getOp().equals("unregister"))){
			log.info("Op is invalid");
			return null;
		}

		if(homeRaw.getOp().equals("register") && homeRaw.getHome()==null){
			log.info("Home is absent in register.");
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
			noticeFeeRaw = gson.fromJson(gson.toJsonTree(feip.getData()), NoticeFeeOpData.class);
		}catch(Exception e) {
			log.info("Bad notice fee data");
			return null;
		}
		if(noticeFeeRaw ==null){
			log.info("Notice fee is null");
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
			reputationRaw = gson.fromJson(gson.toJsonTree(feip.getData()), ReputationOpData.class);
		}catch(Exception e) {
			log.info("Bad reputation data");
			return null;
		}
		if(reputationRaw ==null){
			log.info("Reputation is null");
			return null;
		}
		if(reputationRaw.getRate()==null){
			log.info("Rate is null");
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
			log.info("The freer history is null");
			return false;
		}
		GetResponse<Freer> resultGetCid;
		try {
			GetResponse<Nobody> result = esClient.get(g -> g.index(NOBODY).id(freerHist.getSigner()), Nobody.class);
			if(result.found()){
				log.info("Nobody is found");
				return false;
			}

			resultGetCid = esClient.get(g->g.index(FREER).id(freerHist.getSigner()), Freer.class);
			if(resultGetCid==null){
				log.info("Cid is null");
				return false;
			}
		}catch(Exception e) {
			log.info("Error getting cid");
			return false;
		}

		if(!resultGetCid.found()) {
			log.info("Cid is not found");
			return false;
		}

		Freer cid  = resultGetCid.source();
		if(cid==null){
			log.info("Cid is null");
			return false;
		}
		Map<String, Object> doc = new HashMap<>();
		doc.put("prikey", freerHist.getPrikey());
		doc.put("lastHeight", freerHist.getHeight());
		if(!partialUpdateFreer(esClient, freerHist.getSigner(), doc)){
			log.info("Failed to update cid");
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
		log.info(IndicesNames.NOBODY + ": " + result1.result());
		if(!isValidEsResult(result1.result().jsonValue())){
			log.info("Failed to index nobody");
			return false;
		}
		// Create News for Nobody operation
		News.createNews(esClient, freerHist.getId(), freerHist.getSigner(), OpNames.ANNOUNCE, Feip.FeipProtocol.NOBODY.getName(),
				freerHist.getSigner(), cid.getCid(), freerHist.getPrikey(), freerHist.getHeight(), freerHist.getTime());

		return true;
	}

	private static final int MAX_CID_SUFFIX_ITERATIONS = 50;

	private boolean parseCid(ElasticsearchClient esClient, FreerHist freerHist) throws ElasticsearchException, IOException, InterruptedException {

		if(freerHist ==null){
			log.info("Cid is null");
			return false;
		}
		if(freerHist.getOp().equals("register")) {

			//Rule 1
			int suffixLength = FeipConstants.INITIAL_CID_SUFFIX_LENGTH;
			String signer = freerHist.getSigner();
			if (signer == null || signer.length() < suffixLength) {
				log.info("Signer address too short");
				return false;
			}
			String cidStr = freerHist.getName()+"_"+ signer.substring(signer.length()-suffixLength);

			for (int iteration = 0; iteration < MAX_CID_SUFFIX_ITERATIONS; iteration++) {
				String cidStr1 = cidStr;
				SearchResponse<Freer> resultCidSearch = esClient.search(s->s
								.query(q->q.term(t->t.field("usedCids").value(cidStr1)))
								.index(FREER)
						, Freer.class);

				if(resultCidSearch==null||resultCidSearch.hits()==null||resultCidSearch.hits().total()==null){
					log.info("Result cid search is null");
					return false;
				}

				if(resultCidSearch.hits().total().value()==0) {
					Freer cid = null;
					GetResponse<Freer> resultGetCid = esClient.get(g->g.index(FREER).id(signer), Freer.class);

					if(resultGetCid.found()) cid = resultGetCid.source();

					int nameCount = 0;
					List<String> existingCids = (cid != null) ? cid.getUsedCids() : null;
					if(existingCids != null) {
						nameCount = existingCids.size();
					}
					if(nameCount>=FeipConstants.MAX_CID_COUNT){
						log.info("Name count is greater than " + FeipConstants.MAX_CID_COUNT);
						return false;
					}

					Set<String> usedCidSet = new HashSet<String>();

					boolean isFirstCid = (existingCids == null || existingCids.isEmpty());

					for(int i=0; i<nameCount;i++) {
						usedCidSet.add(existingCids.get(i));
					}
					usedCidSet.add(cidStr1);

					if(usedCidSet.size()>FeipConstants.MAX_CID_COUNT){
						log.info("Used cid set size is greater than " + FeipConstants.MAX_CID_COUNT);
						return false;
					}

					//rule 3
					Map<String, Object> doc = new HashMap<>();
					doc.put("cid", cidStr1);
					doc.put("usedCids", usedCidSet.stream().toList());
					if(isFirstCid) doc.put("nameTime", freerHist.getTime());
					doc.put("lastHeight", freerHist.getHeight());
					return partialUpdateFreer(esClient, signer, doc);

				}else if(resultCidSearch.hits().hits().size()==1 &&
						resultCidSearch.hits().hits().get(0).id().equals(signer)) {

					//rule 4,5
					Freer cidToUpdate = resultCidSearch.hits().hits().get(0).source();
					if(cidToUpdate==null){
						log.info("Cid is null");
						return false;
					}

					//rule 3
					Map<String, Object> doc2 = new HashMap<>();
					doc2.put("cid", cidStr1);
					doc2.put("lastHeight", freerHist.getHeight());
					return partialUpdateFreer(esClient, resultCidSearch.hits().hits().get(0).id(), doc2);
				}

				//rule 2
				suffixLength ++;
				if (suffixLength > signer.length()) {
					log.info("CID suffix exhausted entire signer address, no unique CID found");
					return false;
				}
				cidStr = freerHist.getName()+"_"+ signer.substring(signer.length()-suffixLength);
			}
			log.info("CID registration exceeded max iterations (" + MAX_CID_SUFFIX_ITERATIONS + ")");
			return false;

		}else if(freerHist.getOp().equals("unregister")) {

			GetResponse<Freer> result = esClient.get(g -> g.index(FREER).id(freerHist.getSigner()), Freer.class);

			if(result.found()){
				Freer cid = result.source();
				if(cid==null){
					log.info("Cid is null");
					return false;
				}
				if(!"".equals(cid.getCid())){
					//rule 6
					Map<String, Object> doc3 = new HashMap<>();
					doc3.put("cid", "");
					doc3.put("lastHeight", freerHist.getHeight());
					return partialUpdateFreer(esClient, freerHist.getSigner(), doc3);
				}
			}
		}

		log.info("Bad cid operation");
		return false;
	}


	private boolean parseMaster(ElasticsearchClient esClient, FreerHist freerHist) throws ElasticsearchException, IOException {

		if(freerHist ==null){
			log.info("Cid is null");
			return false;
		}
		GetResponse<Freer> resultGetCid = esClient.get(g->g.index(FREER).id(freerHist.getSigner()), Freer.class);

		if(resultGetCid.found()) {
			Freer cid  = resultGetCid.source();
			if(cid==null){
				log.info("Cid is null");
				return false;
			}
			if(cid.getMaster()==null || cid.getMaster().isBlank()) {
				Map<String, Object> doc = new HashMap<>();
				doc.put("master", freerHist.getMaster());
				doc.put("lastHeight", freerHist.getHeight());
				return partialUpdateFreer(esClient, freerHist.getSigner(), doc);
			}
			log.info("Master had been set.");
			return false;
		}else {
			Map<String, Object> doc = new HashMap<>();
			doc.put("master", freerHist.getMaster());
			doc.put("lastHeight", freerHist.getHeight());
			return partialUpdateFreer(esClient, freerHist.getSigner(), doc);
		}
	}

	private boolean parseHome(ElasticsearchClient esClient, FreerHist freerHist) throws ElasticsearchException, IOException {

		if(freerHist ==null){
			log.info("Cid is null");
			return false;
		}

		if(freerHist.getOp().equals("register")) {
			// Use scripted update to REPLACE the home map entirely (doc merge would merge map keys)
			Map<String, Object> upsertDoc = new HashMap<>();
			upsertDoc.put("home", freerHist.getHome());
			upsertDoc.put("lastHeight", freerHist.getHeight());

			Map<String, JsonData> params = new HashMap<>();
			params.put("home", JsonData.of(freerHist.getHome()));
			params.put("lastHeight", JsonData.of(freerHist.getHeight()));

			@SuppressWarnings("unchecked")
			UpdateResponse<Map> result = esClient.update(u -> u
					.index(FREER)
					.id(freerHist.getSigner())
					.script(s -> s
							.inline(i -> i
									.source("ctx._source.home = params.home; ctx._source.lastHeight = params.lastHeight")
									.params(params)))
					.upsert(upsertDoc),
					Map.class);
			log.info(FREER + ": " + result.result());
			return isValidEsResult(result.result().jsonValue());
		}else if(freerHist.getOp().equals("unregister")) {
			// Keep GET for business rule: home must exist to unregister
			GetResponse<Freer> resultGetFreer = esClient.get(g->g.index(FREER).id(freerHist.getSigner()), Freer.class);
			if(resultGetFreer.found()) {
				Freer freer  = resultGetFreer.source();
				if(freer==null){
					log.info("Freer is null");
					return false;
				}
				if(freer.getHome() ==null || freer.getHome().isEmpty()) {
					log.info("Home is null");
					return false;
				}else {
					// Use scripted update to set home to null (full replacement)
					Map<String, JsonData> params = new HashMap<>();
					params.put("lastHeight", JsonData.of(freerHist.getHeight()));

					@SuppressWarnings("unchecked")
					UpdateResponse<Map> result = esClient.update(u -> u
							.index(FREER)
							.id(freerHist.getSigner())
							.script(s -> s
									.inline(i -> i
											.source("ctx._source.home = null; ctx._source.lastHeight = params.lastHeight")
											.params(params))),
							Map.class);
					log.info(FREER + ": " + result.result());
					return isValidEsResult(result.result().jsonValue());
				}
			}
		}

		log.info("Bad home operation");
		return false;
	}

	private boolean parseNoticeFee(ElasticsearchClient esClient, FreerHist freerHist) throws ElasticsearchException, IOException {
		if(freerHist ==null){
			log.info("Cid is null");
			return false;
		}

		// Use partial update - no need to read first, only modifies noticeFee and lastHeight
		Map<String, Object> doc = new HashMap<>();
		doc.put("noticeFee", freerHist.getNoticeFee());
		doc.put("lastHeight", freerHist.getHeight());
		return partialUpdateFreer(esClient, freerHist.getSigner(), doc);
	}

	public boolean parseReputation(ElasticsearchClient esClient, RepuHist repuHist) throws ElasticsearchException, IOException {
		if(repuHist==null){
			log.info("Reputation is null");
			return false;
		}
		GetResponse<Freer> resultGetCid = esClient.get(g->g.index(FREER).id(repuHist.getRatee()), Freer.class);
		Freer cid;
		if(!resultGetCid.found() || resultGetCid.source()==null) {
			log.info("Cid is not found");
			return false;
		}
		cid = resultGetCid.source();
		if(cid==null){
			log.info("Cid is null");
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

		long weight = core.fch.Weight.calcWeight(
				cid.getCd() != null ? cid.getCd() : 0,
				cid.getCdd() != null ? cid.getCdd() : 0,
				newReputation);

		Map<String, Object> doc = new HashMap<>();
		doc.put("reputation", newReputation);
		doc.put("hot", newHot);
		doc.put("weight", weight);
		doc.put("lastHeight", repuHist.getHeight());
		return partialUpdateFreer(esClient, repuHist.getRatee(), doc);
	}

	public boolean parseNid(ElasticsearchClient esClient, OpReturn opre, Feip feip) throws Exception {

		Gson gson = new Gson();

		NidOpData nidRaw = new NidOpData();

		try {
			nidRaw = gson.fromJson(gson.toJsonTree(feip.getData()), NidOpData.class);
			if(nidRaw==null){
				log.info("Nid raw is null");
				return false;
			}
		}catch(com.google.gson.JsonSyntaxException e) {
			log.info("Error parsing nid raw");
			return false;
		}

		Nid nid = new Nid();

		long height;
		if(nidRaw.getOp()==null){
			log.info("Op is null");
			return false;
		}
		switch(nidRaw.getOp()) {

			case "add":
				if(nidRaw.getName()==null){
					log.info("Name is null");
					return false;
				}
				if(!FeipConstants.isWithinLimit(nidRaw.getName(), FeipConstants.MAX_NAME_LENGTH)){
					log.info("Name exceeds max length");
					return false;
				}
				if(nidRaw.getOid()==null){
					log.info("Oid is null");
					return false;
				}

				nid.setId(Hash.sha256x2(nidRaw.getName()+opre.getSigner()));
				nid.setName(nidRaw.getName());
				nid.setDesc(nidRaw.getDesc() != null && nidRaw.getDesc().length() > FeipConstants.MAX_DESC_LENGTH
						? nidRaw.getDesc().substring(0, FeipConstants.MAX_DESC_LENGTH) : nidRaw.getDesc());
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
				log.info("{}", result13.result());
				return isValidEsResult(result13.result().jsonValue());

			case "stop","recover":
				if(nidRaw.getNames()==null || nidRaw.getNames().isEmpty()){
					log.info("Names is null");
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
					log.info("Result is null");
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
					log.info("Failed");
					return false;
				}
				log.info("Done");
				return true;

			default:		
				log.info("Invalid operation");
				return false;
		}
	}
}
