package personal;

import data.feipData.*;
import utils.EsUtils;
import utils.EsUtils.MgetResult;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import constants.IndicesNames;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.google.gson.Gson;
import data.fchData.OpReturn;
import startFEIP.StartFEIP;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class PersonalParser {

	public enum Operation {
		PUBLISH("publish"),
		UPDATE("update"),
		STOP("stop"),
		RECOVER("recover"),
		CLOSE("close"),
		RATE("rate"),
		REGISTER("register"),
		UNREGISTER("unregister"),
		CREATE("create"),
		JOIN("join"),
		LEAVE("leave"),
		TRANSFER("transfer"),
		TAKE_OVER("take over"),
		AGREE_CONSENSUS("agree consensus"),
		INVITE("invite"),
		WITHDRAW_INVITATION("withdraw invitation"),
		DISMISS("dismiss"),
		APPOINT("appoint"),
		CANCEL_APPOINTMENT("cancel appointment"),
		DISBAND("disband"),
		ADD("add"),
		DELETE("delete"),
		SEND("send");

		private final String value;

		Operation(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

		public static Operation fromString(String text) {
			for (Operation op : Operation.values()) {
				if (op.value.equalsIgnoreCase(text)) {
					return op;
				}
			}
			throw new IllegalArgumentException("No constant with text " + text + " found");
		}
	}

	public boolean parseContact(ElasticsearchClient esClient, OpReturn opre, Feip feip) throws Exception {

		boolean isValid = false;

		Gson gson = new Gson();

		ContactOpData contactRaw = new ContactOpData();

		try {
			contactRaw = gson.fromJson(gson.toJson(feip.getData()), ContactOpData.class);
			if(contactRaw==null)return isValid;
		}catch(Exception e) {
			return isValid;
		}

		Contact contact = new Contact();

		long height;
		if(contactRaw.getOp()==null)return isValid;

		switch(contactRaw.getOp()) {

			case "add":
				contact.setId(opre.getId());

				if (contactRaw.getAlg() != null)contact.setAlg(contactRaw.getAlg());
				if (contactRaw.getCipher()==null)return false;
				contact.setCipher(contactRaw.getCipher());

				contact.setOwner(opre.getSigner());
				contact.setBirthTime(opre.getTime());
				contact.setBirthHeight(opre.getHeight());
				contact.setLastHeight(opre.getHeight());
				contact.setActive(true);

				Contact contact1 = contact;
				esClient.index(i->i.index(IndicesNames.CONTACT).id(contact1.getId()).document(contact1));
				isValid = true;
				break;
			case "delete":
				if(contactRaw.getContactIds() ==null)return isValid;
				height = opre.getHeight();

				MgetResult<Contact> result = EsUtils.getMultiByIdList(esClient, IndicesNames.CONTACT, contactRaw.getContactIds(), Contact.class);

				if(result.getResultList() == null || result.getResultList().isEmpty())return isValid;

				contact = result.getResultList().get(0);

				if(!contact.getOwner().equals(opre.getSigner()))return isValid;

				contact.setActive(false);
				contact.setLastHeight(height);

				Contact contact2 = contact;
				esClient.index(i->i.index(IndicesNames.CONTACT).id(contact2.getId()).document(contact2));

				isValid = true;
				break;
			case "update":
				if(contactRaw.getContactId() ==null)return isValid;
				height = opre.getHeight();

				contact = EsUtils.getById(esClient, IndicesNames.CONTACT, contactRaw.getContactId(), Contact.class);

				if(contact == null)return isValid;

				if(!contact.getOwner().equals(opre.getSigner()))return isValid;
				if(!contact.isActive())return isValid;

				if (contactRaw.getCipher()==null)return false;
				if (contactRaw.getAlg() != null)contact.setAlg(contactRaw.getAlg());
				contact.setCipher(contactRaw.getCipher());

				contact.setLastHeight(opre.getHeight());

				Contact contact5 = contact;
				esClient.index(i->i.index(IndicesNames.CONTACT).id(contact5.getId()).document(contact5));
				isValid = true;
				break;
			case "recover":
				if(contactRaw.getContactId() ==null)return isValid;
				height = opre.getHeight();

				ContactOpData contactRaw2 = contactRaw;

				GetResponse<Contact> result1 = esClient.get(g->g.index(IndicesNames.CONTACT).id(contactRaw2.getContactId()), Contact.class);

				if(!result1.found())return isValid;

				contact = result1.source();

				if(contact == null || !contact.getOwner().equals(opre.getSigner()))return isValid;

				contact.setActive(true);
				contact.setLastHeight(height);

				Contact contact3 = contact;
				esClient.index(i->i.index(IndicesNames.CONTACT).id(contact3.getId()).document(contact3));

				isValid = true;
				break;
			default:
				break;
		}
		return isValid;
	}

	public boolean parseMail(ElasticsearchClient esClient, OpReturn opre, Feip feip) throws Exception {


		Gson gson = new Gson();

		MailOpData mailRaw = new MailOpData();

		try {
			mailRaw = gson.fromJson(gson.toJson(feip.getData()), MailOpData.class);
			if(mailRaw==null)return false;
		}catch(com.google.gson.JsonSyntaxException e) {
			return false;
		}

		Mail mail = new Mail();

		long height;
		if(mailRaw.getOp()==null && mailRaw.getMsg()==null)return false;

		// For the old version mails.
		if(mailRaw.getMsg()!=null) {
			mail.setId(opre.getId());
            mail.setAlg(mailRaw.getAlg());
			mail.setCipherReci(mailRaw.getMsg());

			mail.setSender(opre.getSigner());
			mail.setRecipient(opre.getRecipient());
			mail.setBirthTime(opre.getTime());
			mail.setBirthHeight(opre.getHeight());
			mail.setLastHeight(opre.getHeight());
			mail.setActive(true);

			Mail mail1 = mail;
			esClient.index(i->i.index(IndicesNames.MAIL).id(mail1.getId()).document(mail1));

			return true;
		}
		//Version 4
		if(mailRaw.getOp()!=null) {
			switch(mailRaw.getOp()) {
				case "send":
					mail.setId(opre.getId());

					if(mailRaw.getCipher()==null
							&&mailRaw.getCipherReci()==null
							&&mailRaw.getCipherSend()==null)
						return false;

					if (mailRaw.getAlg() != null) {
						mail.setAlg(mailRaw.getAlg());
					}

					if(mailRaw.getCipher()!=null) {
						mail.setCipher(mailRaw.getCipher());
					}else  {
						if(mailRaw.getCipherSend()!=null)mail.setCipherSend(mailRaw.getCipherSend());
						if(mailRaw.getCipherReci()!=null) {
							mail.setCipherReci(mailRaw.getCipherReci());
						}
					}

					if(mailRaw.getTextId()!=null) {
						mail.setTextId(mailRaw.getTextId());
					}

					mail.setSender(opre.getSigner());
					mail.setRecipient(opre.getRecipient());
					mail.setBirthTime(opre.getTime());
					mail.setBirthHeight(opre.getHeight());
					mail.setLastHeight(opre.getHeight());
					mail.setActive(true);

					Mail mail0 = mail;
					esClient.index(i->i.index(IndicesNames.MAIL).id(mail0.getId()).document(mail0));

					break;
				case "delete","recover":
					if(mailRaw.getMailIds() ==null)return false;

					height = opre.getHeight();

					MgetResult<Mail> result = EsUtils.getMultiByIdList(esClient, IndicesNames.MAIL, mailRaw.getMailIds(), Mail.class);
					if(result.getResultList() == null || result.getResultList().isEmpty())return false;
					List<Mail> mailList = result.getResultList();

					Iterator<Mail> iterator = mailList.iterator();

					while(iterator.hasNext()) {
						Mail mail1 = iterator.next();
						if(mail1.getRecipient()!=null && !mail1.getRecipient().equals(opre.getSigner())){
							iterator.remove();
							continue;
						}
						if(mail1.getRecipient()==null && !mail1.getSender().equals(opre.getSigner())){
							iterator.remove();
							continue;
						}
						if("delete".equals(mailRaw.getOp()))mail1.setActive(false);
						else mail1.setActive(true);
						mail1.setLastHeight(height);
					}
					if(mailList.isEmpty())return false;

					BulkRequest.Builder br = new BulkRequest.Builder();
					for(Mail mail1 : mailList) {
						br.operations(op -> op
								.index(idx -> idx
										.index(IndicesNames.MAIL)
										.id(mail1.getId())
										.document(mail1)
								)
						);
					}
					esClient.bulk(br.build());
					break;

				default:
					break;
			}
		}
		return true;
	}

	public boolean parseSecret(ElasticsearchClient esClient, OpReturn opre, Feip feip) throws Exception {

		boolean isValid = false;

		Gson gson = new Gson();

		SecretOpData secretRaw = new SecretOpData();

		try {
			secretRaw = gson.fromJson(gson.toJson(feip.getData()), SecretOpData.class);
			if(secretRaw==null || secretRaw.getOp()==null)return false;
		}catch(com.google.gson.JsonSyntaxException e) {
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

				esClient.index(i->i.index(IndicesNames.SECRET).id(safe0.getId()).document(safe0));
				isValid = true;
				break;
			case "update":
				if(secretRaw.getSecretId() ==null)return isValid;
				height = opre.getHeight();

				secret = EsUtils.getById(esClient, IndicesNames.SECRET, secretRaw.getSecretId(), Secret.class);

				if(secret == null)return isValid;

				if(!secret.getOwner().equals(opre.getSigner()))return isValid;
				if(!secret.isActive())return isValid;

				if (secretRaw.getCipher()==null)return false;
				if (secretRaw.getAlg() != null)secret.setAlg(secretRaw.getAlg());
				secret.setCipher(secretRaw.getCipher());

				secret.setLastHeight(opre.getHeight());
				Secret safe1 = secret;

				esClient.index(i->i.index(IndicesNames.SECRET).id(safe1.getId()).document(safe1));
				isValid = true;
				break;
			case "delete":
				if(secretRaw.getSecretIds() ==null)return false;
				height = opre.getHeight();
				SecretOpData secretRaw1 = secretRaw;

				MgetResult<Secret> result = EsUtils.getMultiByIdList(esClient, IndicesNames.SECRET, secretRaw1.getSecretIds(), Secret.class);
				if(result==null || result.getResultList()==null || result.getResultList().isEmpty())return false;
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
				if(secretList.isEmpty())return false;

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
				esClient.bulk(br.build());
				isValid = true;
				break;
			case "recover":
				if(secretRaw.getSecretIds() ==null)return false;
				height = opre.getHeight();
				SecretOpData secretRaw2 = secretRaw;

				MgetResult<Secret> result1 = EsUtils.getMultiByIdList(esClient, IndicesNames.SECRET, secretRaw2.getSecretIds(), Secret.class);
				if(result1.getResultList() == null || result1.getResultList().isEmpty())return false;
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
				if(secretList1.isEmpty())return false;

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
				esClient.bulk(br1.build());
				isValid = true;
				break;
			default:
				break;
		}
		return isValid;
	}

	public BoxHistory makeBox(OpReturn opre, Feip feip) {

		Gson gson = new Gson();
		BoxOpData boxRaw = new BoxOpData();

		try {
			boxRaw = gson.fromJson(gson.toJson(feip.getData()), BoxOpData.class);
			if(boxRaw==null)return null;
		}catch(com.google.gson.JsonSyntaxException e) {
			return null;
		}

		BoxHistory boxHist = new BoxHistory();

		if(boxRaw.getOp()==null)return null;

		boxHist.setOp(boxRaw.getOp());

		switch(boxRaw.getOp()) {
			case "create":
				if(boxRaw.getName()==null) return null;
				if(boxRaw.getBid()!=null) return null;
                if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired * 100)
                    return null;
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

				break;
			case "update":
				if(boxRaw.getBid()==null) return null;
				if(boxRaw.getName()==null) return null;

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
				break;
			case "drop":

			case "recover":
				if(boxRaw.getBid()==null)return null;
				boxHist.setBid(boxRaw.getBid());
				boxHist.setId(opre.getId());
				boxHist.setHeight(opre.getHeight());
				boxHist.setIndex(opre.getTxIndex());
				boxHist.setTime(opre.getTime());
				boxHist.setSigner(opre.getSigner());
				break;

			default:
				return null;
		}
		return boxHist;
	}

	public boolean parseBox(ElasticsearchClient esClient, BoxHistory boxHist) throws ElasticsearchException, IOException {
		if(boxHist==null || boxHist.getOp()==null)return false;
		boolean isValid = false;
		Box box;
		switch(boxHist.getOp()) {
			case "create":
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
					esClient.index(i->i.index(IndicesNames.BOX).id(boxHist.getBid()).document(box1));
					isValid = true;
				}else {
					isValid = false;
				}
				break;

			case "drop":

				box = EsUtils.getById(esClient, IndicesNames.BOX, boxHist.getBid(), Box.class);

				if(box==null) {
					isValid = false;
					break;
				}

				if(! box.getOwner().equals(boxHist.getSigner())) {
					isValid = false;
					break;
				}

				if(Boolean.TRUE.equals(box.isActive())) {
					Box box2 = box;
					box2.setActive(false);
					box2.setLastTxId(boxHist.getId());
					box2.setLastTime(boxHist.getTime());
					box2.setLastHeight(boxHist.getHeight());
					esClient.index(i->i.index(IndicesNames.BOX).id(boxHist.getBid()).document(box2));
					isValid = true;
				}else isValid = false;

				break;

			case "recover":

				box = EsUtils.getById(esClient, IndicesNames.BOX, boxHist.getBid(), Box.class);

				if(box==null) {
					isValid = false;
					break;
				}

				if(! box.getOwner().equals(boxHist.getSigner())) {
					isValid = false;
					break;
				}

				if(Boolean.FALSE.equals(box.isActive())) {
					Box box2 = box;
					box2.setActive(true);
					box2.setLastTxId(boxHist.getId());
					box2.setLastTime(boxHist.getTime());
					box2.setLastHeight(boxHist.getHeight());
					esClient.index(i->i.index(IndicesNames.BOX).id(boxHist.getBid()).document(box2));
					isValid = true;
				}else isValid = false;

				break;

			case "update":
				box = EsUtils.getById(esClient, IndicesNames.BOX, boxHist.getBid(), Box.class);

				if(box==null) {
					isValid = false;
					break;
				}

				if(! box.getOwner().equals(boxHist.getSigner())) {
					isValid = false;
					break;
				}

				if(Boolean.FALSE.equals(box.isActive())) {
					isValid = false;
					break;
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

				esClient.index(i->i.index(IndicesNames.BOX).id(boxHist.getBid()).document(box2));
				isValid = true;
				break;
			default:
				return isValid;
		}
		return isValid;
	}

}
