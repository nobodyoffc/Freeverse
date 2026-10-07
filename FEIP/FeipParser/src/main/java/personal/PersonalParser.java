package personal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import constants.OpNames;
import data.feipData.*;
import utils.EsUtils;
import utils.EsUtils.MgetResult;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import constants.IndicesNames;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.google.gson.Gson;
import data.fchData.OpReturn;
import startFEIP.StartFEIP;
import co.elastic.clients.elasticsearch.core.IndexResponse;

import data.fcData.FcEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static constants.Values.CREATED;
import static constants.Values.UPDATED;

public class PersonalParser {

	private static final Logger log = LoggerFactory.getLogger(PersonalParser.class);

	/*
	 * Contact, Mail and Secret are parsed in two steps, like Box: make* turns the op into a history
	 * record without touching Elasticsearch, and parse* applies that record. FileParser stores the
	 * record of every accepted op in *_HISTORY, which is what lets PersonalRollbacker undo an update,
	 * delete or recover by replaying the records that survive a rollback.
	 */

	public ContactHistory makeContact(OpReturn opre, Feip feip) {
		ContactOpData contactRaw;
		try {
			Gson gson = new Gson();
			contactRaw = gson.fromJson(gson.toJsonTree(feip.getData()), ContactOpData.class);
		} catch (Exception e) {
			log.info("Bad contact data");
			return null;
		}
		if (contactRaw == null || contactRaw.getOp() == null) {
			log.info("Bad contact data");
			return null;
		}

		ContactHistory hist = new ContactHistory();
		hist.setOp(contactRaw.getOp());

		switch (contactRaw.getOp()) {
			case "add" -> {
				if (isBlank(contactRaw.getCipher())) {
					log.info("Cipher is empty");
					return null;
				}
				hist.setContactId(opre.getId());
				hist.setAlg(contactRaw.getAlg());
				hist.setCipher(contactRaw.getCipher());
			}
			case "update" -> {
				if (contactRaw.getContactId() == null) {
					log.info("Contact ID is null");
					return null;
				}
				if (isBlank(contactRaw.getCipher())) {
					log.info("Cipher is empty");
					return null;
				}
				hist.setContactId(contactRaw.getContactId());
				hist.setAlg(contactRaw.getAlg());
				hist.setCipher(contactRaw.getCipher());
			}
			case "delete", "recover" -> {
				if (contactRaw.getContactIds() == null || contactRaw.getContactIds().isEmpty()) {
					log.info("Contact IDs are empty");
					return null;
				}
				hist.setContactIds(contactRaw.getContactIds());
			}
			default -> {
				log.info("Invalid operation");
				return null;
			}
		}

		hist.setId(opre.getId());
		hist.setHeight(opre.getHeight());
		hist.setIndex(opre.getTxIndex());
		hist.setTime(opre.getTime());
		hist.setSigner(opre.getSigner());
		return hist;
	}

	public boolean parseContact(ElasticsearchClient esClient, ContactHistory hist) throws Exception {
		if (hist == null || hist.getOp() == null) return false;

		switch (hist.getOp()) {
			case "add" -> {
				Contact contact = new Contact();
				contact.setId(hist.getContactId());
				contact.setAlg(hist.getAlg());
				contact.setCipher(hist.getCipher());
				contact.setOwner(hist.getSigner());
				contact.setBirthTime(hist.getTime());
				contact.setBirthHeight(hist.getHeight());
				contact.setLastHeight(hist.getHeight());
				contact.setActive(true);
				return indexOne(esClient, IndicesNames.CONTACT, contact.getId(), contact);
			}
			case "update" -> {
				Contact contact = EsUtils.getById(esClient, IndicesNames.CONTACT, hist.getContactId(), Contact.class);
				if (contact == null) {
					log.info("Contact is null");
					return false;
				}
				if (!hist.getSigner().equals(contact.getOwner())) {
					log.info("Contact owner is not the signer");
					return false;
				}
				if (!Boolean.TRUE.equals(contact.getActive())) {
					log.info("Contact is not active");
					return false;
				}
				if (hist.getAlg() != null) contact.setAlg(hist.getAlg());
				contact.setCipher(hist.getCipher());
				contact.setLastHeight(hist.getHeight());
				return indexOne(esClient, IndicesNames.CONTACT, contact.getId(), contact);
			}
			case "delete", "recover" -> {
				MgetResult<Contact> result = EsUtils.getMultiByIdList(esClient, IndicesNames.CONTACT, hist.getContactIds(), Contact.class);
				List<Contact> contactList = result == null ? null : result.getResultList();
				if (contactList == null) {
					log.info("Contact list is empty");
					return false;
				}
				boolean active = "recover".equals(hist.getOp());
				List<Contact> changed = new ArrayList<>();
				for (Contact contact : contactList) {
					// Only the owner may delete or recover a contact.
					if (!hist.getSigner().equals(contact.getOwner())) continue;
					contact.setActive(active);
					contact.setLastHeight(hist.getHeight());
					changed.add(contact);
				}
				if (changed.isEmpty()) {
					log.info("No contact of the signer in the list");
					return false;
				}
				return indexAll(esClient, IndicesNames.CONTACT, changed);
			}
			default -> {
				log.info("Invalid operation");
				return false;
			}
		}
	}

	public MailHistory makeMail(OpReturn opre, Feip feip) {
		MailOpData mailRaw;
		try {
			Gson gson = new Gson();
			mailRaw = gson.fromJson(gson.toJsonTree(feip.getData()), MailOpData.class);
		} catch (Exception e) {
			log.info("Bad mail data");
			return null;
		}
		if (mailRaw == null || mailRaw.getOp() == null) {
			log.info("Bad mail data");
			return null;
		}

		MailHistory hist = new MailHistory();
		hist.setOp(mailRaw.getOp());

		switch (mailRaw.getOp()) {
			case "send" -> {
				if (mailRaw.getCipher() == null) log.info("Cipher is null");
				hist.setMailId(opre.getId());
				hist.setAlg(mailRaw.getAlg());
				hist.setCipher(mailRaw.getCipher());
				hist.setRecipient(opre.getRecipient());
				hist.setPaid(opre.getPaid());
			}
			case "delete", "recover" -> {
				if (mailRaw.getMailIds() == null || mailRaw.getMailIds().isEmpty()) {
					log.info("Mail IDs are empty");
					return null;
				}
				hist.setMailIds(mailRaw.getMailIds());
			}
			default -> {
				log.info("Invalid operation");
				return null;
			}
		}

		hist.setId(opre.getId());
		hist.setHeight(opre.getHeight());
		hist.setIndex(opre.getTxIndex());
		hist.setTime(opre.getTime());
		hist.setSigner(opre.getSigner());
		return hist;
	}

	public boolean parseMail(ElasticsearchClient esClient, MailHistory hist) throws Exception {
		if (hist == null || hist.getOp() == null) return false;

		switch (hist.getOp()) {
			case "send" -> {
				Mail mail = new Mail();
				mail.setId(hist.getMailId());
				mail.setAlg(hist.getAlg());
				mail.setCipher(hist.getCipher());
				mail.setFrom(hist.getSigner());
				mail.setTo(hist.getRecipient());
				mail.setBirthTime(hist.getTime());
				mail.setBirthHeight(hist.getHeight());
				mail.setLastHeight(hist.getHeight());
				mail.setNoticeFee(hist.getPaid());
				mail.setActive(true);
				return indexOne(esClient, IndicesNames.MAIL, mail.getId(), mail);
			}
			case "delete", "recover" -> {
				MgetResult<Mail> result = EsUtils.getMultiByIdList(esClient, IndicesNames.MAIL, hist.getMailIds(), Mail.class);
				List<Mail> mailList = result == null ? null : result.getResultList();
				if (mailList == null) {
					log.info("Mail list is empty");
					return false;
				}
				boolean active = "recover".equals(hist.getOp());
				List<Mail> changed = new ArrayList<>();
				for (Mail mail : mailList) {
					// The recipient manages a mail; a mail without one is managed by its sender.
					String manager = mail.getTo() != null ? mail.getTo() : mail.getFrom();
					if (!hist.getSigner().equals(manager)) continue;
					mail.setActive(active);
					mail.setLastHeight(hist.getHeight());
					changed.add(mail);
				}
				if (changed.isEmpty()) {
					log.info("No mail of the signer in the list");
					return false;
				}
				return indexAll(esClient, IndicesNames.MAIL, changed);
			}
			default -> {
				log.info("Invalid operation");
				return false;
			}
		}
	}

	public SecretHistory makeSecret(OpReturn opre, Feip feip) {
		SecretOpData secretRaw;
		try {
			Gson gson = new Gson();
			secretRaw = gson.fromJson(gson.toJsonTree(feip.getData()), SecretOpData.class);
		} catch (Exception e) {
			log.info("Bad secret data");
			return null;
		}
		if (secretRaw == null || secretRaw.getOp() == null) {
			log.info("Bad secret data");
			return null;
		}

		SecretHistory hist = new SecretHistory();
		hist.setOp(secretRaw.getOp());

		switch (secretRaw.getOp()) {
			case "add" -> {
				// Early secrets carried the ciphertext in 'msg'.
				String cipher = !isBlank(secretRaw.getCipher()) ? secretRaw.getCipher() : secretRaw.getMsg();
				if (isBlank(cipher)) {
					log.info("Cipher is empty");
					return null;
				}
				hist.setSecretId(opre.getId());
				hist.setAlg(secretRaw.getAlg());
				hist.setCipher(cipher);
			}
			case "update" -> {
				if (secretRaw.getSecretId() == null) {
					log.info("Secret ID is null");
					return null;
				}
				if (isBlank(secretRaw.getCipher())) {
					log.info("Cipher is empty");
					return null;
				}
				hist.setSecretId(secretRaw.getSecretId());
				hist.setAlg(secretRaw.getAlg());
				hist.setCipher(secretRaw.getCipher());
			}
			case "delete", "recover" -> {
				if (secretRaw.getSecretIds() == null || secretRaw.getSecretIds().isEmpty()) {
					log.info("Secret IDs are empty");
					return null;
				}
				hist.setSecretIds(secretRaw.getSecretIds());
			}
			default -> {
				log.info("Invalid operation");
				return null;
			}
		}

		hist.setId(opre.getId());
		hist.setHeight(opre.getHeight());
		hist.setIndex(opre.getTxIndex());
		hist.setTime(opre.getTime());
		hist.setSigner(opre.getSigner());
		return hist;
	}

	public boolean parseSecret(ElasticsearchClient esClient, SecretHistory hist) throws Exception {
		if (hist == null || hist.getOp() == null) return false;

		switch (hist.getOp()) {
			case "add" -> {
				Secret secret = new Secret();
				secret.setId(hist.getSecretId());
				secret.setAlg(hist.getAlg());
				secret.setCipher(hist.getCipher());
				secret.setOwner(hist.getSigner());
				secret.setBirthTime(hist.getTime());
				secret.setBirthHeight(hist.getHeight());
				secret.setLastHeight(hist.getHeight());
				secret.setActive(true);
				return indexOne(esClient, IndicesNames.SECRET, secret.getId(), secret);
			}
			case "update" -> {
				Secret secret = EsUtils.getById(esClient, IndicesNames.SECRET, hist.getSecretId(), Secret.class);
				if (secret == null) {
					log.info("Secret is null");
					return false;
				}
				if (!hist.getSigner().equals(secret.getOwner())) {
					log.info("Secret owner is not the signer");
					return false;
				}
				if (!Boolean.TRUE.equals(secret.getActive())) {
					log.info("Secret is not active");
					return false;
				}
				if (hist.getAlg() != null) secret.setAlg(hist.getAlg());
				secret.setCipher(hist.getCipher());
				secret.setLastHeight(hist.getHeight());
				return indexOne(esClient, IndicesNames.SECRET, secret.getId(), secret);
			}
			case "delete", "recover" -> {
				MgetResult<Secret> result = EsUtils.getMultiByIdList(esClient, IndicesNames.SECRET, hist.getSecretIds(), Secret.class);
				List<Secret> secretList = result == null ? null : result.getResultList();
				if (secretList == null) {
					log.info("Secret list is empty");
					return false;
				}
				boolean active = "recover".equals(hist.getOp());
				List<Secret> changed = new ArrayList<>();
				for (Secret secret : secretList) {
					if (!hist.getSigner().equals(secret.getOwner())) continue;
					secret.setActive(active);
					secret.setLastHeight(hist.getHeight());
					changed.add(secret);
				}
				if (changed.isEmpty()) {
					log.info("No secret of the signer in the list");
					return false;
				}
				return indexAll(esClient, IndicesNames.SECRET, changed);
			}
			default -> {
				log.info("Invalid operation");
				return false;
			}
		}
	}

	private static boolean isBlank(String s) {
		return s == null || s.isEmpty();
	}

	private static boolean indexOne(ElasticsearchClient esClient, String index, String id, Object doc) throws IOException {
		IndexResponse response = esClient.index(i -> i.index(index).id(id).document(doc));
		log.info("{}", response.result());
		return CREATED.equals(response.result().jsonValue()) || UPDATED.equals(response.result().jsonValue());
	}

	private static <T extends FcEntity> boolean indexAll(ElasticsearchClient esClient, String index, List<T> docs) throws IOException {
		BulkRequest.Builder br = new BulkRequest.Builder();
		for (T doc : docs) {
			br.operations(op -> op.index(idx -> idx.index(index).id(doc.getId()).document(doc)));
		}
		BulkResponse response = esClient.bulk(br.build());
		if (response.errors()) {
			log.info("Failed to bulk write {}", index);
			return false;
		}
		log.info("Done");
		return true;
	}

	public BoxHistory makeBox(OpReturn opre, Feip feip) {

		Gson gson = new Gson();
		BoxOpData boxRaw = new BoxOpData();

		try {
			boxRaw = gson.fromJson(gson.toJsonTree(feip.getData()), BoxOpData.class);
			if(boxRaw==null){
				log.info("Bad box data");
				return null;
			}
		}catch(com.google.gson.JsonSyntaxException e) {
			log.info("Bad box data");
			return null;
		}

		BoxHistory boxHist = new BoxHistory();

		if(boxRaw.getOp()==null){
			log.info("OP is null");
			return null;
		}

		boxHist.setOp(boxRaw.getOp());

		switch(boxRaw.getOp()) {
			case "create":
				if(boxRaw.getName()==null){
					log.info("Name is null");
					return null;
				}
				if(boxRaw.getBid()!=null){
					log.info("Bid is not null");
					return null;
				}
                if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired){
					log.info("CDD is less than required");
					return null;
				}
				boxHist.setId(opre.getId());
				boxHist.setBid(opre.getId());
				boxHist.setHeight(opre.getHeight());
				boxHist.setIndex(opre.getTxIndex());
				boxHist.setTime(opre.getTime());
				boxHist.setSigner(opre.getSigner());

				if(boxRaw.getName()!=null)boxHist.setName(boxRaw.getName());
				if(boxRaw.getDesc()!=null)boxHist.setDesc(boxRaw.getDesc());
				if(boxRaw.getContain()!=null)boxHist.setContain(boxRaw.getContain());
				if(boxRaw.getCipher()!=null)boxHist.setCipher(boxRaw.getCipher());
				if(boxRaw.getAlg()!=null)boxHist.setAlg(boxRaw.getAlg());
				return boxHist;
			case "update":
				if(boxRaw.getBid()==null){
					log.info("Bid is null");
					return null;
				}
				if(boxRaw.getName()==null){
					log.info("Name is null");
					return null;
				}

				boxHist.setId(opre.getId());
				boxHist.setBid(boxRaw.getBid());
				boxHist.setHeight(opre.getHeight());
				boxHist.setIndex(opre.getTxIndex());
				boxHist.setTime(opre.getTime());
				boxHist.setSigner(opre.getSigner());

				if(boxRaw.getName()!=null)boxHist.setName(boxRaw.getName());
				if(boxRaw.getDesc()!=null)boxHist.setDesc(boxRaw.getDesc());
				if(boxRaw.getContain()!=null)boxHist.setContain(boxRaw.getContain());
				if(boxRaw.getCipher()!=null)boxHist.setCipher(boxRaw.getCipher());
				if(boxRaw.getAlg()!=null)boxHist.setAlg(boxRaw.getAlg());
				return boxHist;
			case "drop":

			case "recover":
				if(boxRaw.getBids()==null){
					log.info("Bids are null");
					return null;
				}
				boxHist.setBids(boxRaw.getBids());
				boxHist.setId(opre.getId());
				boxHist.setHeight(opre.getHeight());
				boxHist.setIndex(opre.getTxIndex());
				boxHist.setTime(opre.getTime());
				boxHist.setSigner(opre.getSigner());
				return boxHist;
			default:
				log.info("Invalid operation");
				return null;
		}
	}

	public boolean parseBox(ElasticsearchClient esClient, BoxHistory boxHist) throws ElasticsearchException, IOException {
		if(boxHist==null || boxHist.getOp()==null)return false;
		Box box;
		switch(boxHist.getOp()) {
			case OpNames.CREATE:
				box = EsUtils.getById(esClient, IndicesNames.BOX, boxHist.getBid(), Box.class);
				if(box==null) {
					box = new Box();
					box.setId(boxHist.getId());
					if(boxHist.getName()!=null)box.setName(boxHist.getName());
					if(boxHist.getDesc()!=null)box.setDesc(boxHist.getDesc());
					if(boxHist.getContain()!=null)box.setContain(boxHist.getContain());
					if(boxHist.getCipher()!=null)box.setCipher(boxHist.getCipher());
					if(boxHist.getAlg()!=null)box.setAlg(boxHist.getAlg());

					box.setOwner(boxHist.getSigner());
					box.setBirthTime(boxHist.getTime());
					box.setBirthHeight(boxHist.getHeight());

					box.setLastTxId(boxHist.getId());
					box.setLastTime(boxHist.getTime());
					box.setLastHeight(boxHist.getHeight());

					box.setActive(true);

					Box box1=box;
					IndexResponse result10 = esClient.index(i->i.index(IndicesNames.BOX).id(boxHist.getBid()).document(box1));
					log.info("{}", result10.result());
					return CREATED.equals(result10.result().jsonValue()) || UPDATED.equals(result10.result().jsonValue());

				}else {
					log.info("Box already exists");
					return false;
				}

			case OpNames.DROP,OpNames.RECOVER:

				MgetResult<Box> result = null;
				try {
					result = EsUtils.getMultiByIdList(esClient, IndicesNames.BOX, boxHist.getBids(), Box.class);
				} catch (Exception e) {
					log.info("ElasticSearch wrong.");
					return false;
				}
				if(result.getResultList() == null || result.getResultList().isEmpty()) {
					log.info("Box list is empty");
					return false;
				}
				List<Box> boxList = result.getResultList();
				BulkRequest.Builder br = new BulkRequest.Builder();
				for(Box box1: boxList) {
					if(! box1.getOwner().equals(boxHist.getSigner())) {
						continue;
					}
					if(boxHist.getOp().equals(OpNames.DROP) && Boolean.FALSE.equals(box1.isActive())) {
						continue;
					}
					if(boxHist.getOp().equals(OpNames.RECOVER) && Boolean.TRUE.equals(box1.isActive())) {
						continue;
					}
					if(boxHist.getOp().equals(OpNames.DROP)) box1.setActive(false);
					else box1.setActive(true);
					box1.setLastTxId(boxHist.getId());
					box1.setLastTime(boxHist.getTime());
					box1.setLastHeight(boxHist.getHeight());
					br.operations(op -> op.index(idx -> idx.index(IndicesNames.BOX).id(box1.getId()).document(box1)));
				}
				BulkResponse result11 = esClient.bulk(br.build());
				if(result11.errors()){
					log.info("Failed");
					return false;
				}else {
					log.info("Done");
					return true;
				}

			case OpNames.UPDATE:
				box = EsUtils.getById(esClient, IndicesNames.BOX, boxHist.getBid(), Box.class);

				if(box==null) {
					log.info("Box not found");
					return false;
				}

				if(! box.getOwner().equals(boxHist.getSigner())) {
					log.info("Box owner is not the signer");
					return false;
				}

				if(Boolean.FALSE.equals(box.isActive())) {
					log.info("Box is not active");
					return false;
				}

				if(boxHist.getName()!=null)box.setName(boxHist.getName());
				if(boxHist.getDesc()!=null)box.setDesc(boxHist.getDesc());
				if(boxHist.getContain()!=null)box.setContain(boxHist.getContain());
				if(boxHist.getCipher()!=null)box.setCipher(boxHist.getCipher());
				if(boxHist.getAlg()!=null)box.setAlg(boxHist.getAlg());

				box.setLastTxId(boxHist.getId());
				box.setLastTime(boxHist.getTime());
				box.setLastHeight(boxHist.getHeight());


				Box box2 = box;

				IndexResponse result13 = esClient.index(i->i.index(IndicesNames.BOX).id(boxHist.getBid()).document(box2));
				log.info("{}", result13.result());
				return CREATED.equals(result13.result().jsonValue()) || UPDATED.equals(result13.result().jsonValue());

			default:
				log.info("Invalid operation");
				return false;
		}
	}

}
