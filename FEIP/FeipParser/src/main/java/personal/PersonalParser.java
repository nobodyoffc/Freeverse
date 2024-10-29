package personal;

import clients.esClient.EsTools;
import co.elastic.clients.elasticsearch.core.GetResponse;
import constants.IndicesNames;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.google.gson.Gson;
import fch.fchData.OpReturn;
import feip.feipData.*;
import startFEIP.StartFEIP;

import java.io.IOException;

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

	public boolean parseContact(ElasticsearchClient esClient, OpReturn opre, FcInfo feip) throws ElasticsearchException, IOException {

		boolean isValid = false;

		Gson gson = new Gson();

		ContactData contactRaw = new ContactData();

		try {
			contactRaw = gson.fromJson(gson.toJson(feip.getData()), ContactData.class);
			if(contactRaw==null)return isValid;
		}catch(Exception e) {
			return isValid;
		}

		Contact contact = new Contact();

		long height;
		if(contactRaw.getOp()==null)return isValid;
		switch(contactRaw.getOp()) {

			case "add":
				contact.setContactId(opre.getTxId());

            	if (contactRaw.getAlg() != null)contact.setAlg(contactRaw.getAlg());
				if (contactRaw.getCipher()==null)return false;
				contact.setCipher(contactRaw.getCipher());

				contact.setOwner(opre.getSigner());
				contact.setBirthTime(opre.getTime());
				contact.setBirthHeight(opre.getHeight());
				contact.setLastHeight(opre.getHeight());
				contact.setActive(true);

				Contact contact1 = contact;
				esClient.index(i->i.index(IndicesNames.CONTACT).id(contact1.getContactId()).document(contact1));
				isValid = true;
				break;
			case "delete":
				if(contactRaw.getContactId() ==null)return isValid;
				height = opre.getHeight();
				ContactData contactRaw1 = contactRaw;

				GetResponse<Contact> result = esClient.get(g->g.index(IndicesNames.CONTACT).id(contactRaw1.getContactId()), Contact.class);

				if(!result.found())return isValid;

				contact = result.source();

				if(!contact.getOwner().equals(opre.getSigner()))return isValid;

				contact.setActive(false);
				contact.setLastHeight(height);

				Contact contact2 = contact;
				esClient.index(i->i.index(IndicesNames.CONTACT).id(contact2.getContactId()).document(contact2));

				isValid = true;
				break;
			case "recover":
				if(contactRaw.getContactId() ==null)return isValid;
				height = opre.getHeight();

				ContactData contactRaw2 = contactRaw;

				GetResponse<Contact> result1 = esClient.get(g->g.index(IndicesNames.CONTACT).id(contactRaw2.getContactId()), Contact.class);

				if(!result1.found())return isValid;

				contact = result1.source();

				if(!contact.getOwner().equals(opre.getSigner()))return isValid;

				contact.setActive(true);
				contact.setLastHeight(height);

				Contact contact3 = contact;
				esClient.index(i->i.index(IndicesNames.CONTACT).id(contact3.getContactId()).document(contact3));

				isValid = true;
				break;
			default:
				break;
		}
		return isValid;
	}

	public boolean parseMail(ElasticsearchClient esClient, OpReturn opre, FcInfo feip) throws ElasticsearchException, IOException {


		Gson gson = new Gson();

		MailData mailRaw = new MailData();

		boolean isValid = false;
		try {
			mailRaw = gson.fromJson(gson.toJson(feip.getData()), MailData.class);
			if(mailRaw==null)return false;
		}catch(com.google.gson.JsonSyntaxException e) {
			return false;
		}

		Mail mail = new Mail();

		long height;
		if(mailRaw.getOp()==null && mailRaw.getMsg()==null)return false;

		// For the old version mails.
		if(mailRaw.getMsg()!=null) {
			mail.setMailId(opre.getTxId());
            mail.setAlg(mailRaw.getAlg());
			mail.setCipherReci(mailRaw.getMsg());

			mail.setSender(opre.getSigner());
			mail.setRecipient(opre.getRecipient());
			mail.setBirthTime(opre.getTime());
			mail.setBirthHeight(opre.getHeight());
			mail.setLastHeight(opre.getHeight());
			mail.setActive(true);

			Mail mail1 = mail;
			esClient.index(i->i.index(IndicesNames.MAIL).id(mail1.getMailId()).document(mail1));

			return true;
		}
		//Version 4
		if(mailRaw.getOp()!=null) {
			switch(mailRaw.getOp()) {
				case "send":
					mail.setMailId(opre.getTxId());

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
					esClient.index(i->i.index(IndicesNames.MAIL).id(mail0.getMailId()).document(mail0));

					break;
				case "delete":
					if(mailRaw.getMailId() ==null)return false;
					height = opre.getHeight();
					MailData mailRaw1 = mailRaw;

					GetResponse<Mail> result = esClient.get(g->g.index(IndicesNames.MAIL).id(mailRaw1.getMailId()), Mail.class);

					if(!result.found())return false;

					mail = result.source();

					if(!mail.getRecipient().equals(opre.getSigner()))return false;

					mail.setActive(false);
					mail.setLastHeight(height);

					Mail mail2 = mail;
					esClient.index(i->i.index(IndicesNames.MAIL).id(mail2.getMailId()).document(mail2));

					isValid = true;
					break;
				case "recover":
					if(mailRaw.getMailId() ==null)return false;
					height = opre.getHeight();
					MailData mailRaw2 = mailRaw;

					GetResponse<Mail> result1 = esClient.get(g->g.index(IndicesNames.MAIL).id(mailRaw2.getMailId()), Mail.class);

					if(!result1.found())return isValid;

					mail = result1.source();

					if(!mail.getRecipient().equals(opre.getSigner()))return false;

					mail.setActive(true);
					mail.setLastHeight(height);

					Mail mail3 = mail;
					esClient.index(i->i.index(IndicesNames.MAIL).id(mail3.getMailId()).document(mail3));

					break;
				default:
					break;
			}
		}
		return true;
	}

	public boolean parseSecret(ElasticsearchClient esClient, OpReturn opre, FcInfo feip) throws ElasticsearchException, IOException {

		boolean isValid = false;

		Gson gson = new Gson();

		SecretData secretRaw = new SecretData();

		try {
			secretRaw = gson.fromJson(gson.toJson(feip.getData()), SecretData.class);
			if(secretRaw==null || secretRaw.getOp()==null)return false;
		}catch(com.google.gson.JsonSyntaxException e) {
			return false;
		}

		Secret secret = new Secret();

		long height;
		switch(secretRaw.getOp()) {

			case "add":
				secret.setSecretId(opre.getTxId());

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

				esClient.index(i->i.index(IndicesNames.SECRET).id(safe0.getSecretId()).document(safe0));
				isValid = true;
				break;

			case "delete":
				if(secretRaw.getSecretId() ==null)return false;
				height = opre.getHeight();
				SecretData safeRaw1 = secretRaw;

				GetResponse<Secret> result = esClient.get(g->g.index(IndicesNames.SECRET).id(safeRaw1.getSecretId()), Secret.class);

				if(!result.found())return isValid;

				secret = result.source();

				if(!secret.getOwner().equals(opre.getSigner()))return isValid;

				secret.setActive(false);
				secret.setLastHeight(height);

				Secret safe2 = secret;
				esClient.index(i->i.index(IndicesNames.SECRET).id(safe2.getSecretId()).document(safe2));

				isValid = true;
				break;
			case "recover":
				if(secretRaw.getSecretId() ==null)return isValid;
				height = opre.getHeight();
				SecretData safeRaw2 = secretRaw;

				GetResponse<Secret> result1 = esClient.get(g->g.index(IndicesNames.SECRET).id(safeRaw2.getSecretId()), Secret.class);

				if(!result1.found())return isValid;

				secret = result1.source();

				if(!secret.getOwner().equals(opre.getSigner()))return isValid;

				secret.setActive(true);
				secret.setLastHeight(height);

				Secret safe3 = secret;
				esClient.index(i->i.index(IndicesNames.SECRET).id(safe3.getSecretId()).document(safe3));

				isValid = true;
				break;
			default:
				break;
		}
		return isValid;
	}

	public BoxHistory makeBox(OpReturn opre, FcInfo feip) {

		Gson gson = new Gson();
		BoxData boxRaw = new BoxData();

		try {
			boxRaw = gson.fromJson(gson.toJson(feip.getData()), BoxData.class);
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
				boxHist.setTxId(opre.getTxId());
				boxHist.setBid(opre.getTxId());
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

				boxHist.setTxId(opre.getTxId());
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
				boxHist.setTxId(opre.getTxId());
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
				box = EsTools.getById(esClient, IndicesNames.BOX, boxHist.getBid(), Box.class);
				if(box==null) {
					box = new Box();
					box.setBid(boxHist.getTxId());
					if(boxHist.getName()!=null)box.setName(boxHist.getName());
					if(boxHist.getDesc()!=null)box.setDesc(boxHist.getDesc());
					if(boxHist.getContain()!=null)box.setContain(boxHist.getContain());
					if(boxHist.getCipher()!=null)box.setCipher(boxHist.getCipher());
					if(boxHist.getAlg()!=null)box.setAlg(boxHist.getAlg());

					box.setOwner(boxHist.getSigner());
					box.setBirthTime(boxHist.getTime());
					box.setBirthHeight(boxHist.getHeight());

					box.setLastTxId(boxHist.getTxId());
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

				box = EsTools.getById(esClient, IndicesNames.BOX, boxHist.getBid(), Box.class);

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
					box2.setLastTxId(boxHist.getTxId());
					box2.setLastTime(boxHist.getTime());
					box2.setLastHeight(boxHist.getHeight());
					esClient.index(i->i.index(IndicesNames.BOX).id(boxHist.getBid()).document(box2));
					isValid = true;
				}else isValid = false;

				break;

			case "recover":

				box = EsTools.getById(esClient, IndicesNames.BOX, boxHist.getBid(), Box.class);

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
					box2.setLastTxId(boxHist.getTxId());
					box2.setLastTime(boxHist.getTime());
					box2.setLastHeight(boxHist.getHeight());
					esClient.index(i->i.index(IndicesNames.BOX).id(boxHist.getBid()).document(box2));
					isValid = true;
				}else isValid = false;

				break;

			case "update":
				box = EsTools.getById(esClient, IndicesNames.BOX, boxHist.getBid(), Box.class);

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

				box.setLastTxId(boxHist.getTxId());
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
