package personal;

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

	public boolean parseContact(ElasticsearchClient esClient, OpReturn opre, Feip feip) throws Exception {


		Gson gson = new Gson();

		ContactOpData contactRaw = new ContactOpData();

		try {
			contactRaw = gson.fromJson(gson.toJson(feip.getData()), ContactOpData.class);
			if(contactRaw==null){
				System.out.println("Bad contact data");
				return false;
			}
		}catch(Exception e) {
			System.out.println("Bad contact data");
			return false;
		}

		Contact contact = new Contact();

		long height;
		if(contactRaw.getOp()==null){
			System.out.println("OP is null");
			return false;
		}

		switch(contactRaw.getOp()) {

			case "add":
				contact.setId(opre.getId());

				if (contactRaw.getAlg() != null)contact.setAlg(contactRaw.getAlg());
				if (contactRaw.getCipher()==null){
					System.out.println("Cipher is null");
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
				System.out.println(result1.result());
				return CREATED.equals(result1.result().jsonValue()) || UPDATED.equals(result1.result().jsonValue());
			case "delete","recover":
				if(contactRaw.getContactIds() ==null){
					System.out.println("Contact IDs are null");
					return false;
				}

				height = opre.getHeight();

				MgetResult<Contact> result = EsUtils.getMultiByIdList(esClient, IndicesNames.CONTACT, contactRaw.getContactIds(), Contact.class);

				if(result.getResultList() == null || result.getResultList().isEmpty()){
					System.out.println("Contact list is empty");
					return false;
				}
				List<Contact> contactList = result.getResultList();

				for (Contact contact4 : contactList) {
					if (result.getResultList() == null || result.getResultList().isEmpty()){
						System.out.println("Contact list is empty");
						return false;
					}

					if ("delete".equals(contactRaw.getOp())) contact4.setActive(false);
					else contact4.setActive(true);

					contact4.setLastHeight(height);
				}
				if(contactList.isEmpty()){
					System.out.println("Contact list is empty");
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
				if(result2.errors())System.out.println("Failed");
				else System.out.println("Done");

				return true;

			case "update":
				if(contactRaw.getContactId() ==null){
					System.out.println("Contact ID is null");
					return false;
				}
				height = opre.getHeight();

				contact = EsUtils.getById(esClient, IndicesNames.CONTACT, contactRaw.getContactId(), Contact.class);

				if(contact == null){
					System.out.println("Contact is null");
					return false;
				}

				if(!contact.getOwner().equals(opre.getSigner())){
					System.out.println("Contact owner is not the signer");
					return false;
				}
				if(!contact.getActive()){
					System.out.println("Contact is not active");
					return false;
				}

				if (contactRaw.getCipher()==null){
					System.out.println("Cipher is null");
					return false;
				}
				if (contactRaw.getAlg() != null)contact.setAlg(contactRaw.getAlg());
				contact.setCipher(contactRaw.getCipher());

				contact.setLastHeight(opre.getHeight());

				Contact contact5 = contact;
				IndexResponse result3 = esClient.index(i->i.index(IndicesNames.CONTACT).id(contact5.getId()).document(contact5));
				System.out.println(result3.result());
				return CREATED.equals(result3.result().jsonValue()) || UPDATED.equals(result3.result().jsonValue());
			default:
				return false;
		}
	}

	public boolean parseMail(ElasticsearchClient esClient, OpReturn opre, Feip feip) throws Exception {


		Gson gson = new Gson();

		MailOpData mailRaw = new MailOpData();

		try {
			mailRaw = gson.fromJson(gson.toJson(feip.getData()), MailOpData.class);
			if(mailRaw==null){
				System.out.println("Bad mail data");
				return false;
			}
		}catch(com.google.gson.JsonSyntaxException e) {
			System.out.println("Bad mail data");
			return false;
		}

		Mail mail = new Mail();

		long height;
		if(mailRaw.getOp()==null ){
			System.out.println("OP is null");
			return false;
		}
		//Version 4
		if(mailRaw.getOp()!=null) {
			switch (mailRaw.getOp()) {
				case "send" -> {
					mail.setId(opre.getId());
					if (mailRaw.getCipher() == null)
						System.out.println("Cipher is null");
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
					System.out.println(result4.result());
					return CREATED.equals(result4.result().jsonValue()) || UPDATED.equals(result4.result().jsonValue());


				}
				case "delete", "recover" -> {
					if (mailRaw.getMailIds() == null){
						System.out.println("Mail IDs are null");
						return false;
					}
					height = opre.getHeight();
					MgetResult<Mail> result = EsUtils.getMultiByIdList(esClient, IndicesNames.MAIL, mailRaw.getMailIds(), Mail.class);
					if (result.getResultList() == null || result.getResultList().isEmpty()){
						System.out.println("Mail list is empty");
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
						System.out.println("Mail list is empty");
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
						System.out.println("Failed");
						return false;
					}else{
						System.out.println("Done");
						return true;
					}
				}
				default -> {
					System.out.println("Invalid operation");
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
			secretRaw = gson.fromJson(gson.toJson(feip.getData()), SecretOpData.class);
			if(secretRaw==null || secretRaw.getOp()==null){
				System.out.println("Bad secret data");
				return false;
			}
		}catch(com.google.gson.JsonSyntaxException e) {
			System.out.println("Bad secret data");
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
				System.out.println(result6.result());
				return CREATED.equals(result6.result().jsonValue()) || UPDATED.equals(result6.result().jsonValue());

			case "update":
				if(secretRaw.getSecretId() ==null){
					System.out.println("Secret ID is null");
					return false;
				}
				height = opre.getHeight();

				secret = EsUtils.getById(esClient, IndicesNames.SECRET, secretRaw.getSecretId(), Secret.class);

				if(secret == null){
					System.out.println("Secret is null");
					return false;
				}

				if(!secret.getOwner().equals(opre.getSigner())){
					System.out.println("Secret owner is not the signer");
					return false;
				}
				if(!secret.getActive()){
					System.out.println("Secret is not active");
					return false;
				}

				if (secretRaw.getCipher()==null){
					System.out.println("Cipher is null");
					return false;
				}
				if (secretRaw.getAlg() != null)secret.setAlg(secretRaw.getAlg());
				secret.setCipher(secretRaw.getCipher());

				secret.setLastHeight(opre.getHeight());
				Secret safe1 = secret;

				IndexResponse result7 = esClient.index(i->i.index(IndicesNames.SECRET).id(safe1.getId()).document(safe1));
				System.out.println(result7.result());
				return CREATED.equals(result7.result().jsonValue()) || UPDATED.equals(result7.result().jsonValue());

			case "delete":
				if(secretRaw.getSecretIds() ==null){
					System.out.println("Secret IDs are null");
					return false;
				}
				height = opre.getHeight();
				SecretOpData secretRaw1 = secretRaw;

				MgetResult<Secret> result = EsUtils.getMultiByIdList(esClient, IndicesNames.SECRET, secretRaw1.getSecretIds(), Secret.class);
				if(result==null || result.getResultList()==null || result.getResultList().isEmpty()){
					System.out.println("Secret list is empty");
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
					System.out.println("Secret list is empty");
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
				if(result8.errors())System.out.println("Failed");
				else System.out.println("Done");
				return true;
			case "recover":
				if(secretRaw.getSecretIds() ==null){
					System.out.println("Secret IDs are null");
					return false;
				}
				height = opre.getHeight();
				SecretOpData secretRaw2 = secretRaw;

				MgetResult<Secret> result1 = EsUtils.getMultiByIdList(esClient, IndicesNames.SECRET, secretRaw2.getSecretIds(), Secret.class);
				if(result1.getResultList() == null || result1.getResultList().isEmpty()){
					System.out.println("Secret list is empty");
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
					System.out.println("Secret list is empty");
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
				if(result9.errors())System.out.println("Failed");
				else System.out.println("Done");
				return true;
			default:
				System.out.println("Invalid operation");
				return false;
		}
	}

	public BoxHistory makeBox(OpReturn opre, Feip feip) {

		Gson gson = new Gson();
		BoxOpData boxRaw = new BoxOpData();

		try {
			boxRaw = gson.fromJson(gson.toJson(feip.getData()), BoxOpData.class);
			if(boxRaw==null){
				System.out.println("Bad box data");
				return null;
			}
		}catch(com.google.gson.JsonSyntaxException e) {
			System.out.println("Bad box data");
			return null;
		}

		BoxHistory boxHist = new BoxHistory();

		if(boxRaw.getOp()==null){
			System.out.println("OP is null");
			return null;
		}

		boxHist.setOp(boxRaw.getOp());

		switch(boxRaw.getOp()) {
			case "create":
				if(boxRaw.getName()==null){
					System.out.println("Name is null");
					return null;
				}
				if(boxRaw.getBid()!=null){
					System.out.println("Bid is not null");
					return null;
				}
                if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired){
					System.out.println("CDD is less than required");
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
					System.out.println("Bid is null");
					return null;
				}
				if(boxRaw.getName()==null){
					System.out.println("Name is null");
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
					System.out.println("Bids are null");
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
				System.out.println("Invalid operation");
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
					System.out.println(result10.result());
					return CREATED.equals(result10.result().jsonValue()) || UPDATED.equals(result10.result().jsonValue());

				}else {
					System.out.println("Box already exists");
					return false;
				}

			case OpNames.DROP,OpNames.RECOVER:

				MgetResult<Box> result = null;
				try {
					result = EsUtils.getMultiByIdList(esClient, IndicesNames.BOX, boxHist.getBids(), Box.class);
				} catch (Exception e) {
					System.out.println("ElasticSearch wrong.");
					return false;
				}
				if(result.getResultList() == null || result.getResultList().isEmpty()) {
					System.out.println("Box list is empty");
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
					System.out.println("Failed");
					return false;
				}else {
					System.out.println("Done");
					return true;
				}

			case OpNames.UPDATE:
				box = EsUtils.getById(esClient, IndicesNames.BOX, boxHist.getBid(), Box.class);

				if(box==null) {
					System.out.println("Box not found");
					return false;
				}

				if(! box.getOwner().equals(boxHist.getSigner())) {
					System.out.println("Box owner is not the signer");
					return false;
				}

				if(Boolean.FALSE.equals(box.isActive())) {
					System.out.println("Box is not active");
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
				System.out.println(result13.result());
				return CREATED.equals(result13.result().jsonValue()) || UPDATED.equals(result13.result().jsonValue());

			default:
				System.out.println("Invalid operation");
				return false;
		}
	}

}
