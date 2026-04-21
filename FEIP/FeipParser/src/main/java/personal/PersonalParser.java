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

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import static constants.Values.CREATED;
import static constants.Values.UPDATED;

public class PersonalParser {

	private static final Logger log = LoggerFactory.getLogger(PersonalParser.class);

	public boolean parseContact(ElasticsearchClient esClient, OpReturn opre, Feip feip) throws Exception {


		Gson gson = new Gson();

		ContactOpData contactRaw = new ContactOpData();

		try {
			contactRaw = gson.fromJson(gson.toJsonTree(feip.getData()), ContactOpData.class);
			if(contactRaw==null){
				log.info("Bad contact data");
				return false;
			}
		}catch(Exception e) {
			log.info("Bad contact data");
			return false;
		}

		Contact contact = new Contact();

		long height;
		if(contactRaw.getOp()==null){
			log.info("OP is null");
			return false;
		}

		switch(contactRaw.getOp()) {

			case "add":
				contact.setId(opre.getId());

				if (contactRaw.getAlg() != null)contact.setAlg(contactRaw.getAlg());
				if (contactRaw.getCipher()==null){
					log.info("Cipher is null");
					return false;
				}
				contact.setCipher(contactRaw.getCipher());

				contact.setOwner(opre.getSigner());
				contact.setBirthTime(opre.getTime());
				contact.setBirthHeight(opre.getHeight());
				contact.setLastHeight(opre.getHeight());
				contact.setActive(true);

				Contact contact1 = contact;
				IndexResponse result1 = esClient.index(i->i.index(IndicesNames.CONTACT).id(contact1.getId()).document(contact1));
				log.info("{}", result1.result());
				return CREATED.equals(result1.result().jsonValue()) || UPDATED.equals(result1.result().jsonValue());
			case "delete","recover":
				if(contactRaw.getContactIds() ==null){
					log.info("Contact IDs are null");
					return false;
				}

				height = opre.getHeight();

				MgetResult<Contact> result = EsUtils.getMultiByIdList(esClient, IndicesNames.CONTACT, contactRaw.getContactIds(), Contact.class);

				if(result.getResultList() == null || result.getResultList().isEmpty()){
					log.info("Contact list is empty");
					return false;
				}
				List<Contact> contactList = result.getResultList();

				for (Contact contact4 : contactList) {
					if (result.getResultList() == null || result.getResultList().isEmpty()){
						log.info("Contact list is empty");
						return false;
					}

					if ("delete".equals(contactRaw.getOp())) contact4.setActive(false);
					else contact4.setActive(true);

					contact4.setLastHeight(height);
				}
				if(contactList.isEmpty()){
					log.info("Contact list is empty");
					return false;
				}

				BulkRequest.Builder br = new BulkRequest.Builder();
				for(Contact contact5 : contactList) {
					br.operations(op -> op
							.index(idx -> idx
									.index(IndicesNames.CONTACT)
									.id(contact5.getId())
									.document(contact5)
							)
					);
				}
				BulkResponse result2 = esClient.bulk(br.build());
				if(result2.errors()){
					log.info("Failed to bulk update contact");
					return false;
				}
				log.info("Done");
				return true;

			case "update":
				if(contactRaw.getContactId() ==null){
					log.info("Contact ID is null");
					return false;
				}
				height = opre.getHeight();

				contact = EsUtils.getById(esClient, IndicesNames.CONTACT, contactRaw.getContactId(), Contact.class);

				if(contact == null){
					log.info("Contact is null");
					return false;
				}

				if(!contact.getOwner().equals(opre.getSigner())){
					log.info("Contact owner is not the signer");
					return false;
				}
				if(!contact.getActive()){
					log.info("Contact is not active");
					return false;
				}

				if (contactRaw.getCipher()==null){
					log.info("Cipher is null");
					return false;
				}
				if (contactRaw.getAlg() != null)contact.setAlg(contactRaw.getAlg());
				contact.setCipher(contactRaw.getCipher());

				contact.setLastHeight(opre.getHeight());

				Contact contact5 = contact;
				IndexResponse result3 = esClient.index(i->i.index(IndicesNames.CONTACT).id(contact5.getId()).document(contact5));
				log.info("{}", result3.result());
				return CREATED.equals(result3.result().jsonValue()) || UPDATED.equals(result3.result().jsonValue());
			default:
				return false;
		}
	}

	public boolean parseMail(ElasticsearchClient esClient, OpReturn opre, Feip feip) throws Exception {


		Gson gson = new Gson();

		MailOpData mailRaw = new MailOpData();

		try {
			mailRaw = gson.fromJson(gson.toJsonTree(feip.getData()), MailOpData.class);
			if(mailRaw==null){
				log.info("Bad mail data");
				return false;
			}
		}catch(com.google.gson.JsonSyntaxException e) {
			log.info("Bad mail data");
			return false;
		}

		Mail mail = new Mail();

		long height;
		if(mailRaw.getOp()==null ){
			log.info("OP is null");
			return false;
		}
		//Version 4
		if(mailRaw.getOp()!=null) {
			switch (mailRaw.getOp()) {
				case "send" -> {
					mail.setId(opre.getId());
					if (mailRaw.getCipher() == null)
						log.info("Cipher is null");
					if (mailRaw.getAlg() != null) {
						mail.setAlg(mailRaw.getAlg());
					}
					if (mailRaw.getCipher() != null) {
						mail.setCipher(mailRaw.getCipher());
					}
					mail.setFrom(opre.getSigner());
					mail.setTo(opre.getRecipient());
					mail.setBirthTime(opre.getTime());
					mail.setBirthHeight(opre.getHeight());
					mail.setLastHeight(opre.getHeight());
					mail.setNoticeFee(opre.getPaid());
					mail.setActive(true);
					IndexResponse result4 = esClient.index(i -> i.index(IndicesNames.MAIL).id(mail.getId()).document(mail));
					log.info("{}", result4.result());
					return CREATED.equals(result4.result().jsonValue()) || UPDATED.equals(result4.result().jsonValue());


				}
				case "delete", "recover" -> {
					if (mailRaw.getMailIds() == null){
						log.info("Mail IDs are null");
						return false;
					}
					height = opre.getHeight();
					MgetResult<Mail> result = EsUtils.getMultiByIdList(esClient, IndicesNames.MAIL, mailRaw.getMailIds(), Mail.class);
					if (result.getResultList() == null || result.getResultList().isEmpty()){
						log.info("Mail list is empty");
						return false;
					}
					List<Mail> mailList = result.getResultList();
					Iterator<Mail> iterator = mailList.iterator();
					while (iterator.hasNext()) {
						Mail mail1 = iterator.next();
						if (mail1.getTo() != null && !mail1.getTo().equals(opre.getSigner())) {
							iterator.remove();
							continue;
						}
						if (mail1.getTo() == null && !mail1.getFrom().equals(opre.getSigner())) {
							iterator.remove();
							continue;
						}
						if ("delete".equals(mailRaw.getOp())) mail1.setActive(false);
						else mail1.setActive(true);
						mail1.setLastHeight(height);
					}
					if (mailList.isEmpty()){
						log.info("Mail list is empty");
						return false;
					}
					BulkRequest.Builder br = new BulkRequest.Builder();
					for (Mail mail1 : mailList) {
						br.operations(op -> op
								.index(idx -> idx
										.index(IndicesNames.MAIL)
										.id(mail1.getId())
										.document(mail1)
								)
						);
					}
					BulkResponse result5 = esClient.bulk(br.build());
					if(result5.errors()){
						log.info("Failed");
						return false;
					}else{
						log.info("Done");
						return true;
					}
				}
				default -> {
					log.info("Invalid operation");
					return false;
				}
			}
		}
		return false;
	}

	public boolean parseSecret(ElasticsearchClient esClient, OpReturn opre, Feip feip) throws Exception {

		Gson gson = new Gson();

		SecretOpData secretRaw = new SecretOpData();

		try {
			secretRaw = gson.fromJson(gson.toJsonTree(feip.getData()), SecretOpData.class);
			if(secretRaw==null || secretRaw.getOp()==null){
				log.info("Bad secret data");
				return false;
			}
		}catch(com.google.gson.JsonSyntaxException e) {
			log.info("Bad secret data");
			return false;
		}

		Secret secret = new Secret();

		long height;
		switch(secretRaw.getOp()) {

			case "add":
				secret.setId(opre.getId());

            if (secretRaw.getAlg() != null) {
                secret.setAlg(secretRaw.getAlg());
			}

				if(secretRaw.getCipher()!=null) {
					secret.setCipher(secretRaw.getCipher());
				}else if(secretRaw.getMsg()!=null) {
					secret.setCipher(secretRaw.getMsg());
				}else return false;

				secret.setOwner(opre.getSigner());
				secret.setBirthTime(opre.getTime());
				secret.setBirthHeight(opre.getHeight());
				secret.setLastHeight(opre.getHeight());
				secret.setActive(true);

				Secret safe0 = secret;

				IndexResponse result6 = esClient.index(i->i.index(IndicesNames.SECRET).id(safe0.getId()).document(safe0));
				log.info("{}", result6.result());
				return CREATED.equals(result6.result().jsonValue()) || UPDATED.equals(result6.result().jsonValue());

			case "update":
				if(secretRaw.getSecretId() ==null){
					log.info("Secret ID is null");
					return false;
				}
				height = opre.getHeight();

				secret = EsUtils.getById(esClient, IndicesNames.SECRET, secretRaw.getSecretId(), Secret.class);

				if(secret == null){
					log.info("Secret is null");
					return false;
				}

				if(!secret.getOwner().equals(opre.getSigner())){
					log.info("Secret owner is not the signer");
					return false;
				}
				if(!secret.getActive()){
					log.info("Secret is not active");
					return false;
				}

				if (secretRaw.getCipher()==null){
					log.info("Cipher is null");
					return false;
				}
				if (secretRaw.getAlg() != null)secret.setAlg(secretRaw.getAlg());
				secret.setCipher(secretRaw.getCipher());

				secret.setLastHeight(opre.getHeight());
				Secret safe1 = secret;

				IndexResponse result7 = esClient.index(i->i.index(IndicesNames.SECRET).id(safe1.getId()).document(safe1));
				log.info("{}", result7.result());
				return CREATED.equals(result7.result().jsonValue()) || UPDATED.equals(result7.result().jsonValue());

			case "delete":
				if(secretRaw.getSecretIds() ==null){
					log.info("Secret IDs are null");
					return false;
				}
				height = opre.getHeight();
				SecretOpData secretRaw1 = secretRaw;

				MgetResult<Secret> result = EsUtils.getMultiByIdList(esClient, IndicesNames.SECRET, secretRaw1.getSecretIds(), Secret.class);
				if(result==null || result.getResultList()==null || result.getResultList().isEmpty()){
					log.info("Secret list is empty");
					return false;
				}
				List<Secret> secretList = result.getResultList();

				Iterator<Secret> iterator = secretList.iterator();
				while(iterator.hasNext()) {
					Secret secret1 = iterator.next();
					if(!secret1.getOwner().equals(opre.getSigner())){
						iterator.remove();
						continue;
					}
					secret1.setActive(false);
					secret1.setLastHeight(height);
				}
				if(secretList.isEmpty()){
					log.info("Secret list is empty");
					return false;
				}

				BulkRequest.Builder br = new BulkRequest.Builder();
				for(Secret secret1 : secretList) {
					br.operations(op -> op
							.index(idx -> idx
									.index(IndicesNames.SECRET)
									.id(secret1.getId())
									.document(secret1)
							)
					);
				}
				BulkResponse result8 = esClient.bulk(br.build());
				if(result8.errors()){
					log.info("Failed to bulk update secret");
					return false;
				}
				log.info("Done");
				return true;
			case "recover":
				if(secretRaw.getSecretIds() ==null){
					log.info("Secret IDs are null");
					return false;
				}
				height = opre.getHeight();
				SecretOpData secretRaw2 = secretRaw;

				MgetResult<Secret> result1 = EsUtils.getMultiByIdList(esClient, IndicesNames.SECRET, secretRaw2.getSecretIds(), Secret.class);
				if(result1.getResultList() == null || result1.getResultList().isEmpty()){
					log.info("Secret list is empty");
					return false;
				}
				List<Secret> secretList1 = result1.getResultList();

				Iterator<Secret> iterator1 = secretList1.iterator();
				while(iterator1.hasNext()) {
					Secret secret1 = iterator1.next();
					if(!secret1.getOwner().equals(opre.getSigner())){
						iterator1.remove();
						continue;
					}
					secret1.setActive(true);
					secret1.setLastHeight(height);
				}
				if(secretList1.isEmpty()){
					log.info("Secret list is empty");
					return false;
				}

				BulkRequest.Builder br1 = new BulkRequest.Builder();
				for(Secret secret1 : secretList1) {
					br1.operations(op -> op
							.index(idx -> idx
									.index(IndicesNames.SECRET)
									.id(secret1.getId())
									.document(secret1)
							)
					);
				}
				BulkResponse result9 = esClient.bulk(br1.build());
				if(result9.errors()){
					log.info("Failed to bulk recover secret");
					return false;
				}
				log.info("Done");
				return true;
			default:
				log.info("Invalid operation");
				return false;
		}
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
