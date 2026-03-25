package publish;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import com.google.gson.Gson;
import constants.IndicesNames;
import data.fcData.News;
import data.fchData.Freer;
import data.fchData.OpReturn;
import data.feipData.*;
import startFEIP.StartFEIP;
import utils.EsUtils;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static constants.OpNames.*;
import static constants.Values.CREATED;
import static constants.Values.UPDATED;

public class PublishParser {

	public boolean parseStatement(ElasticsearchClient esClient, OpReturn opre, Feip feip) throws ElasticsearchException, IOException {

		Gson gson = new Gson();

		StatementOpData statementRaw = new StatementOpData();

		try {
			statementRaw = gson.fromJson(gson.toJson(feip.getData()), StatementOpData.class);
			if(statementRaw==null){
				System.out.println("Statement raw is null");
				return false;
			}
		}catch(com.google.gson.JsonSyntaxException e) {
			System.out.println("Statement raw is null");
			return false;
		}

		Statement statement = new Statement();

		statement.setId(opre.getId());

		if(statementRaw.getConfirm()==null){
			System.out.println("Confirm is null");
			return false;
		}

		if(!statementRaw.getConfirm().equals("This is a formal and irrevocable statement.")){
			System.out.println("Confirm is not a formal and irrevocable statement");
			return false;
		}

		if(statementRaw.getTitle()==null && statementRaw.getContent()==null){
			System.out.println("Title and content are null");
			return false;
		}

		if(statementRaw.getTitle()!=null) {
			statement.setTitle(statementRaw.getTitle());
		}

		if(statementRaw.getContent()!=null) {
			statement.setContent(statementRaw.getContent());
		}

		statement.setPublisher(opre.getSigner());
		statement.setBirthTime(opre.getTime());
		statement.setBirthHeight(opre.getHeight());

		IndexResponse result = esClient.index(i -> i.index(IndicesNames.STATEMENT).id(statement.getId()).document(statement));
		if(result==null||result.result()==null){
			System.out.println("Failed to create statement");
			return false;
		}
		if(!CREATED.equals(result.result().jsonValue()) && !UPDATED.equals(result.result().jsonValue())){
			System.out.println("Failed to create statement");
			return false;
		}
		System.out.println(result.result());
		// Create news record for statement
		News.createNews(esClient, opre.getId(), opre.getSigner(), Feip.FeipProtocol.STATEMENT.getName(),
				Feip.FeipProtocol.STATEMENT.getName(), opre.getId(), statement.getTitle(), statement.getContent(),
				opre.getHeight(), opre.getTime());

		return true;
	}



	public TextHistory makeText(OpReturn opre, Feip feip) {

		Gson gson = new Gson();

		TextOpData textRaw = new TextOpData();
		try {
			textRaw = gson.fromJson(gson.toJson(feip.getData()), TextOpData.class);
			if(textRaw==null){
				System.out.println("Text raw is null");
				return null;
			}
		}catch(Exception e) {
			System.out.println("Failed to parse text");
			return null;
		}

		TextHistory textHist = new TextHistory();

		if(textRaw.getOp()==null){
			System.out.println("OP is null");
			return null;
		}

		textHist.setOp(textRaw.getOp());

		switch(textRaw.getOp()) {

			case PUBLISH:
				if(textRaw.getTitle()==null||"".equals(textRaw.getTitle())){
					System.out.println("Title is null or empty");
					return null;
				}
				if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired){
					System.out.println("Height is greater than CddCheckHeight and Cdd is less than CddRequired");
					return null;
				}
				textHist.setId(opre.getId());

				textHist.setTextId(opre.getId());
				textHist.setHeight(opre.getHeight());
				textHist.setIndex(opre.getTxIndex());
				textHist.setTime(opre.getTime());
				textHist.setSigner(opre.getSigner());

				if(textRaw.getType()!=null)textHist.setType(textRaw.getType());
				if(textRaw.getDid()!=null)textHist.setDid(textRaw.getDid());
				if(textRaw.getTitle()!=null)textHist.setTitle(textRaw.getTitle());
				if(textRaw.getLang()!=null)textHist.setLang(textRaw.getLang());
				if(textRaw.getAuthors()!=null)textHist.setAuthors(textRaw.getAuthors());
				if(textRaw.getFormat()!=null)textHist.setFormat(textRaw.getFormat());
				if(textRaw.getSummary()!=null)textHist.setSummary(textRaw.getSummary());
				break;

			case UPDATE:
				if(textRaw.getTextId()==null|| textRaw.getTitle()==null||"".equals(textRaw.getTitle())){
					System.out.println("TextId is null or title is null or empty");
					return null;
				}
				textHist.setId(opre.getId());
				textHist.setHeight(opre.getHeight());
				textHist.setIndex(opre.getTxIndex());
				textHist.setTime(opre.getTime());
				textHist.setSigner(opre.getSigner());

				textHist.setTextId(textRaw.getTextId());

				if(textRaw.getType()!=null)textHist.setType(textRaw.getType());
				if(textRaw.getDid()!=null)textHist.setDid(textRaw.getDid());
				if(textRaw.getTitle()!=null)textHist.setTitle(textRaw.getTitle());
				if(textRaw.getLang()!=null)textHist.setLang(textRaw.getLang());
				if(textRaw.getAuthors()!=null)textHist.setAuthors(textRaw.getAuthors());
				if(textRaw.getFormat()!=null)textHist.setFormat(textRaw.getFormat());
				if(textRaw.getSummary()!=null)textHist.setSummary(textRaw.getSummary());
				break;
			case RECOVER:
			case DELETE:
				if (textRaw.getTextIds() == null || textRaw.getTextIds().size() == 0) {
					System.out.println("TextIds is null or empty");
					return null;
				}
				textHist.setTextIds(textRaw.getTextIds());
				textHist.setId(opre.getId());
				textHist.setHeight(opre.getHeight());
				textHist.setIndex(opre.getTxIndex());
				textHist.setTime(opre.getTime());
				textHist.setSigner(opre.getSigner());

				break;

			case RATE:
				if(textRaw.getTextId()==null){
					System.out.println("TextId is null");
					return null;
				}
				if (opre.getCdd() == null) {
					System.out.println("Cdd is null");
					return null;
				}

				if (opre.getCdd() < StartFEIP.CddRequired) {
					System.out.println("Cdd is less than CddRequired");
					return null;
				}

				if (textRaw.getRate() == null) {
					System.out.println("Rate is null");
					return null;
				}
				textHist.setTextId(textRaw.getTextId());
				textHist.setRate(textRaw.getRate());
				textHist.setCdd(opre.getCdd());

				textHist.setId(opre.getId());
				textHist.setHeight(opre.getHeight());
				textHist.setIndex(opre.getTxIndex());
				textHist.setTime(opre.getTime());
				textHist.setSigner(opre.getSigner());
				break;
			default:
				System.out.println("Invalid operation");
				return null;
		}
		return textHist;
	}


	public boolean parseText(ElasticsearchClient esClient, TextHistory textHist) throws Exception {

		if(textHist==null){
			System.out.println("Text hist is null");
			return false;
		}
		Text text;
		switch (textHist.getOp()) {
			case PUBLISH -> {
				text = EsUtils.getById(esClient, IndicesNames.TEXT, textHist.getTextId(), Text.class);
				if (text == null) {
					text = new Text();

					text.setId(textHist.getTextId());
					text.setVer(textHist.getVer());
					text.setType(textHist.getType());
					text.setDid(textHist.getDid());

					text.setLang(textHist.getLang());
					text.setTitle(textHist.getTitle());
					text.setAuthors(textHist.getAuthors());
					text.setFormat(textHist.getFormat());
					text.setSummary(textHist.getSummary());
					text.setPublisher(textHist.getSigner());

					text.setBirthTime(textHist.getTime());
					text.setBirthHeight(textHist.getHeight());
					text.setLastTxId(textHist.getId());
					text.setLastTime(textHist.getTime());
					text.setLastHeight(textHist.getHeight());

					text.setDeleted(false);

					Text text1 = text;

					IndexResponse result1 = esClient.index(i -> i.index(IndicesNames.TEXT).id(textHist.getTextId()).document(text1));
					if(result1==null||result1.result()==null){
						System.out.println("Failed to create text");
						return false;
					}
					if(!CREATED.equals(result1.result().jsonValue()) && !UPDATED.equals(result1.result().jsonValue())){
						System.out.println("Failed to create text");
						return false;
					}
					// Create news record for text publication
					News.createNews(esClient, textHist.getId(), textHist.getSigner(), PUBLISH,
							Feip.FeipProtocol.TEXT.getName(), textHist.getId(), textHist.getTitle(), null,
							textHist.getHeight(), textHist.getTime());

					return true;
				} else {
					System.out.println("Text already exists");
					return false;
				}
			}
			case UPDATE -> {
				text = EsUtils.getById(esClient, IndicesNames.TEXT, textHist.getTextId(), Text.class);
				if (text == null) {
					System.out.println("Text not found");
					return false;
				}
				if (Boolean.TRUE.equals(text.isDeleted())) {
					System.out.println("Text is deleted");
					return false;
				}
				if (!text.getPublisher().equals(textHist.getSigner())) {
					System.out.println("Text publisher is not the same as the signer");
					return false;
				}
				text.setVer(String.valueOf(Integer.parseInt(text.getVer())+1));
				text.setType(textHist.getType());
				text.setDid(textHist.getDid());
				text.setTitle(textHist.getTitle());
				text.setLang(textHist.getLang());
				text.setAuthors(textHist.getAuthors());
				text.setFormat(textHist.getFormat());
				text.setSummary(textHist.getSummary());
				text.setLastTxId(textHist.getId());
				text.setLastTime(textHist.getTime());
				text.setLastHeight(textHist.getHeight());
				Text text2 = text;
				IndexResponse result2 = esClient.index(i -> i.index(IndicesNames.TEXT).id(textHist.getTextId()).document(text2));
				if(result2==null||result2.result()==null){
					System.out.println("Failed to update text");
					return false;
				}
				if(!CREATED.equals(result2.result().jsonValue()) && !UPDATED.equals(result2.result().jsonValue())){
					System.out.println("Failed to update text");
					return false;
				}
				return true;
			}

			case DELETE, RECOVER -> {
				List<String> idList = new ArrayList<>();
				if (textHist.getTextIds() != null && textHist.getTextIds().size() > 0) {
					idList.addAll(textHist.getTextIds());
				} else {
					System.out.println("TextIds is null or empty");
					return false;
				}

				EsUtils.MgetResult<Text> result = EsUtils.getMultiByIdList(esClient, IndicesNames.TEXT, idList, Text.class);
				List<Text> texts = result.getResultList();

				List<Text> updatedTexts = new ArrayList<>();
				for (Text textItem : texts) {

					if (!textItem.getPublisher().equals(textHist.getSigner())) {
						Freer resultCid = EsUtils.getById(esClient, IndicesNames.FREER, textHist.getSigner(), Freer.class);
						if (resultCid ==null || resultCid.getMaster() == null || !resultCid.getMaster().equals(textHist.getSigner())) {
							continue;
						}
					}

					switch (textHist.getOp()) {
						case DELETE:
							textItem.setDeleted(true);
							break;
						case RECOVER:
							textItem.setDeleted(false);
							break;
						default:continue;
					}

					textItem.setLastTxId(textHist.getId());
					textItem.setLastTime(textHist.getTime());
					textItem.setLastHeight(textHist.getHeight());

					updatedTexts.add(textItem);
				}

				if (!updatedTexts.isEmpty()) {
					BulkRequest.Builder br = new BulkRequest.Builder();
					for (Text updatedText : updatedTexts) {
						br.operations(op -> op
								.index(idx -> idx
										.index(IndicesNames.TEXT)
										.id(updatedText.getId())
										.document(updatedText)
								)
						);
					}
					BulkResponse result3 = esClient.bulk(br.build());
					if(result3.errors()){
						System.out.println("Failed to bulk update text");
						return false;
					}
					return true;
				}
				System.out.println("No text matched publisher/master filter");
				return false;
			}

			case RATE -> {
				text = EsUtils.getById(esClient, IndicesNames.TEXT, textHist.getTextId(), Text.class);
				if (text == null) {
					System.out.println("Text not found");
					return false;
				}
				if (text.getPublisher().equals(textHist.getSigner())) {
					System.out.println("Text publisher is the same as the signer");
					return false;
				}

				if((textHist.getCdd()==null || textHist.getRate()==null)){
					System.out.println("Cdd or rate is null");
					return false;
				}

				if(text.gettCdd()==null||text.gettRate()==null){
					text.settRate(Float.valueOf(textHist.getRate()));
					text.settCdd(textHist.getCdd());
				}else{
					text.settRate(
							(text.gettRate()*text.gettCdd()+textHist.getRate()*textHist.getCdd())
									/(text.gettCdd()+textHist.getCdd())
					);
					text.settCdd(text.gettCdd() + textHist.getCdd());
				}

				text.setLastTxId(textHist.getId());
				text.setLastTime(textHist.getTime());
				text.setLastHeight(textHist.getHeight());
				Text text3 = text;
				IndexResponse result3 = esClient.index(i -> i.index(IndicesNames.TEXT).id(textHist.getTextId()).document(text3));
				return CREATED.equals(result3.result().jsonValue()) || UPDATED.equals(result3.result().jsonValue());
			}
		}
		return false;
	}

	public RemarkHistory makeRemark(OpReturn opre, Feip feip) {

		Gson gson = new Gson();

		RemarkOpData remarkRaw = new RemarkOpData();
		try {
			remarkRaw = gson.fromJson(gson.toJson(feip.getData()), RemarkOpData.class);
			if(remarkRaw==null){
				System.out.println("Remark raw is null");
				return null;
			}
		}catch(Exception e) {
			System.out.println("Failed to parse remark");
			return null;
		}

		RemarkHistory remarkHist = new RemarkHistory();

		if(remarkRaw.getOp()==null){
			System.out.println("OP is null");
			return null;
		}

		remarkHist.setOp(remarkRaw.getOp());

		switch(remarkRaw.getOp()) {

			case PUBLISH:
				if(remarkRaw.getTitle()==null||"".equals(remarkRaw.getTitle())){
					System.out.println("Title is null or empty");
					return null;
				}
				if (opre.getHeight() > StartFEIP.CddCheckHeight) {
					Long cdd = opre.getCdd();
					if (cdd == null || cdd < StartFEIP.CddRequired) {
						System.out.println("Height is greater than CddCheckHeight and Cdd is null or less than CddRequired");
						return null;
					}
				}
				remarkHist.setId(opre.getId());

				remarkHist.setRemarkId(opre.getId());
				remarkHist.setHeight(opre.getHeight());
				remarkHist.setIndex(opre.getTxIndex());
				remarkHist.setTime(opre.getTime());
				remarkHist.setSigner(opre.getSigner());

				if(remarkRaw.getDid()!=null)remarkHist.setDid(remarkRaw.getDid());
				if(remarkRaw.getTitle()!=null)remarkHist.setTitle(remarkRaw.getTitle());
				if(remarkRaw.getLang()!=null)remarkHist.setLang(remarkRaw.getLang());
				if(remarkRaw.getAuthors()!=null)remarkHist.setAuthors(remarkRaw.getAuthors());
				if(remarkRaw.getFormat()!=null)remarkHist.setFormat(remarkRaw.getFormat());
				if(remarkRaw.getSummary()!=null)remarkHist.setSummary(remarkRaw.getSummary());
				if(remarkRaw.getOnDid()!=null)remarkHist.setOnDid(remarkRaw.getOnDid());

				break;

			case UPDATE:
				if(remarkRaw.getRemarkId()==null|| remarkRaw.getTitle()==null||"".equals(remarkRaw.getTitle())){
					System.out.println("RemarkId is null or title is null or empty");
					return null;
				}
				remarkHist.setId(opre.getId());
				remarkHist.setHeight(opre.getHeight());
				remarkHist.setIndex(opre.getTxIndex());
				remarkHist.setTime(opre.getTime());
				remarkHist.setSigner(opre.getSigner());

				remarkHist.setRemarkId(remarkRaw.getRemarkId());

				if(remarkRaw.getDid()!=null)remarkHist.setDid(remarkRaw.getDid());
				if(remarkRaw.getTitle()!=null)remarkHist.setTitle(remarkRaw.getTitle());
				if(remarkRaw.getLang()!=null)remarkHist.setLang(remarkRaw.getLang());
				if(remarkRaw.getAuthors()!=null)remarkHist.setAuthors(remarkRaw.getAuthors());
				if(remarkRaw.getFormat()!=null)remarkHist.setFormat(remarkRaw.getFormat());
				if(remarkRaw.getSummary()!=null)remarkHist.setSummary(remarkRaw.getSummary());
				if(remarkRaw.getOnDid()!=null)remarkHist.setOnDid(remarkRaw.getOnDid());

				break;
			case RECOVER:
			case DELETE:
				if (remarkRaw.getRemarkIds() == null || remarkRaw.getRemarkIds().length == 0) {
					System.out.println("RemarkIds is null or empty");
					return null;
				}
				remarkHist.setRemarkIds(remarkRaw.getRemarkIds());
				remarkHist.setId(opre.getId());
				remarkHist.setHeight(opre.getHeight());
				remarkHist.setIndex(opre.getTxIndex());
				remarkHist.setTime(opre.getTime());
				remarkHist.setSigner(opre.getSigner());

				break;

			case RATE:
				if(remarkRaw.getRemarkId()==null){
					System.out.println("RemarkId is null");
					return null;
				}
				if (remarkRaw.getRate() == null) {
					System.out.println("Rate is null");
					return null;
				}
				Long remarkRateCdd = opre.getCdd();
				if (remarkRateCdd == null) {
					System.out.println("Cdd is null");
					return null;
				}
				if (remarkRateCdd < StartFEIP.CddRequired) {
					System.out.println("Cdd is less than CddRequired");
					return null;
				}
				remarkHist.setRemarkId(remarkRaw.getRemarkId());
				remarkHist.setRate(remarkRaw.getRate());
				remarkHist.setCdd(remarkRateCdd);

				remarkHist.setId(opre.getId());
				remarkHist.setHeight(opre.getHeight());
				remarkHist.setIndex(opre.getTxIndex());
				remarkHist.setTime(opre.getTime());
				remarkHist.setSigner(opre.getSigner());
				break;
			default:
				System.out.println("Invalid operation");
				return null;
		}
		return remarkHist;
	}

	public boolean parseRemark(ElasticsearchClient esClient, RemarkHistory remarkHist) throws Exception {

		if(remarkHist==null){
			System.out.println("Remark hist is null");
			return false;
		}
		Remark remark;
		switch (remarkHist.getOp()) {
			case PUBLISH -> {
				remark = EsUtils.getById(esClient, IndicesNames.REMARK, remarkHist.getRemarkId(), Remark.class);
				if (remark == null) {
					remark = new Remark();

					remark.setId(remarkHist.getRemarkId());
					remark.setVer("1");
					remark.setDid(remarkHist.getDid());

					remark.setLang(remarkHist.getLang());
					remark.setTitle(remarkHist.getTitle());
					remark.setAuthors(remarkHist.getAuthors());
					remark.setFormat(remarkHist.getFormat());
					remark.setSummary(remarkHist.getSummary());
					remark.setOnDid(remarkHist.getOnDid());

					remark.setPublisher(remarkHist.getSigner());

					remark.setBirthTime(remarkHist.getTime());
					remark.setBirthHeight(remarkHist.getHeight());
					remark.setLastTxId(remarkHist.getId());
					remark.setLastTime(remarkHist.getTime());
					remark.setLastHeight(remarkHist.getHeight());

					remark.setDeleted(false);

					Remark remark1 = remark;

					IndexResponse result1 = esClient.index(i -> i.index(IndicesNames.REMARK).id(remarkHist.getRemarkId()).document(remark1));
					if(result1==null||result1.result()==null){
						System.out.println("Failed to create remark");
						return false;
					}
					if(!CREATED.equals(result1.result().jsonValue()) && !UPDATED.equals(result1.result().jsonValue())){
						System.out.println("Failed to create remark");
						return false;
					}
					return true;
				} else {
					System.out.println("Remark already exists");
					return false;
				}
			}
			case UPDATE -> {
				remark = EsUtils.getById(esClient, IndicesNames.REMARK, remarkHist.getRemarkId(), Remark.class);
				if (remark == null) {
					System.out.println("Remark not found");
					return false;
				}
				if (Boolean.TRUE.equals(remark.isDeleted())) {
					System.out.println("Remark is deleted");
					return false;
				}
				if (!remark.getPublisher().equals(remarkHist.getSigner())) {
					System.out.println("Remark publisher is not the same as the signer");
					return false;
				}
				remark.setVer(String.valueOf(Integer.parseInt(remark.getVer())+1));
				remark.setDid(remarkHist.getDid());
				remark.setTitle(remarkHist.getTitle());
				remark.setLang(remarkHist.getLang());
				remark.setAuthors(remarkHist.getAuthors());
				remark.setFormat(remarkHist.getFormat());
				remark.setSummary(remarkHist.getSummary());
				remark.setOnDid(remarkHist.getOnDid());

				remark.setLastTxId(remarkHist.getId());
				remark.setLastTime(remarkHist.getTime());
				remark.setLastHeight(remarkHist.getHeight());
				Remark remark2 = remark;
				IndexResponse result2 = esClient.index(i -> i.index(IndicesNames.REMARK).id(remarkHist.getRemarkId()).document(remark2));
				return CREATED.equals(result2.result().jsonValue()) || UPDATED.equals(result2.result().jsonValue());
			}

			case DELETE, RECOVER -> {
				List<String> idList = new ArrayList<>();
				if (remarkHist.getRemarkIds() != null && remarkHist.getRemarkIds().length > 0) {
					idList.addAll(Arrays.asList(remarkHist.getRemarkIds()));
				} else {
					System.out.println("RemarkIds is null or empty");
					return false;
				}

				EsUtils.MgetResult<Remark> result = EsUtils.getMultiByIdList(esClient, IndicesNames.REMARK, idList, Remark.class);
				if (result == null) {
					System.out.println("Remark mget result is null");
					return false;
				}
				List<Remark> remarks = result.getResultList();
				if (remarks == null || remarks.isEmpty()) {
					System.out.println("Remarks is null or empty");
					return false;
				}

				List<Remark> updatedRemarks = new ArrayList<>();
				for (Remark remarkItem : remarks) {

					if (!remarkItem.getPublisher().equals(remarkHist.getSigner())) {
						Freer resultCid = EsUtils.getById(esClient, IndicesNames.FREER, remarkHist.getSigner(), Freer.class);
						if (resultCid ==null || resultCid.getMaster() == null || !resultCid.getMaster().equals(remarkHist.getSigner())) {
							continue;
						}
					}

					switch (remarkHist.getOp()) {
						case DELETE:
							remarkItem.setDeleted(true);
							break;
						case RECOVER:
							remarkItem.setDeleted(false);
							break;
					}

					remarkItem.setLastTxId(remarkHist.getId());
					remarkItem.setLastTime(remarkHist.getTime());
					remarkItem.setLastHeight(remarkHist.getHeight());

					updatedRemarks.add(remarkItem);
				}

				if (!updatedRemarks.isEmpty()) {
					BulkRequest.Builder br = new BulkRequest.Builder();
					for (Remark updatedRemark : updatedRemarks) {
						br.operations(op -> op
								.index(idx -> idx
										.index(IndicesNames.REMARK)
										.id(updatedRemark.getId())
										.document(updatedRemark)
								)
						);
					}
					BulkResponse result3 = esClient.bulk(br.build());
					if(result3.errors()){
						System.out.println("Failed to bulk update remark");
						return false;
					}
					return true;
				}
				System.out.println("No remark matched publisher/master filter; delete/recover skipped");
				return false;
			}

			case RATE -> {
				remark = EsUtils.getById(esClient, IndicesNames.REMARK, remarkHist.getRemarkId(), Remark.class);
				if (remark == null) {
					System.out.println("Remark not found");
					return false;
				}
				if (remark.getPublisher().equals(remarkHist.getSigner())) {
					System.out.println("Remark publisher is the same as the signer");
					return false;
				}

				if((remarkHist.getCdd()==null || remarkHist.getRate()==null)){
					System.out.println("Cdd or rate is null");
					return false;
				}

				if(remark.gettCdd()==null||remark.gettRate()==null){
					remark.settRate(Float.valueOf(remarkHist.getRate()));
					remark.settCdd(remarkHist.getCdd());
				}else{
					remark.settRate(
							(remark.gettRate()*remark.gettCdd()+remarkHist.getRate()*remarkHist.getCdd())
									/(remark.gettCdd()+remarkHist.getCdd())
					);
					remark.settCdd(remark.gettCdd() + remarkHist.getCdd());
				}

				remark.setLastTxId(remarkHist.getId());
				remark.setLastTime(remarkHist.getTime());
				remark.setLastHeight(remarkHist.getHeight());
				Remark remark3 = remark;
				IndexResponse result3 = esClient.index(i -> i.index(IndicesNames.REMARK).id(remarkHist.getRemarkId()).document(remark3));
				System.out.println(result3.result());
				return CREATED.equals(result3.result().jsonValue()) || UPDATED.equals(result3.result().jsonValue());
			}
		}

		return false;
	}

	public ArtworkHistory makeArtwork(OpReturn opre, Feip feip) {

		Gson gson = new Gson();

		ArtworkOpData artworkRaw = new ArtworkOpData();
		try {
			artworkRaw = gson.fromJson(gson.toJson(feip.getData()), ArtworkOpData.class);
			if(artworkRaw==null){
				System.out.println("Artwork raw is null");
				return null;
			}
		}catch(Exception e) {
			System.out.println("Failed to parse artwork");
			return null;
		}

		ArtworkHistory artworkHist = new ArtworkHistory();

		if(artworkRaw.getOp()==null){
			System.out.println("OP is null");
			return null;
		}

		artworkHist.setOp(artworkRaw.getOp());

		switch(artworkRaw.getOp()) {

			case PUBLISH:
				if(artworkRaw.getTitle()==null||"".equals(artworkRaw.getTitle())){
					System.out.println("Title is null or empty");
					return null;
				}
				if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired) {
					System.out.println("Height is greater than CddCheckHeight and Cdd is less than CddRequired");
					return null;
				}
				artworkHist.setId(opre.getId());

				artworkHist.setArtworkId(opre.getId());
				artworkHist.setHeight(opre.getHeight());
				artworkHist.setIndex(opre.getTxIndex());
				artworkHist.setTime(opre.getTime());
				artworkHist.setSigner(opre.getSigner());

				if(artworkRaw.getDid()!=null)artworkHist.setDid(artworkRaw.getDid());
				if(artworkRaw.getTitle()!=null)artworkHist.setTitle(artworkRaw.getTitle());
				if(artworkRaw.getLang()!=null)artworkHist.setLang(artworkRaw.getLang());
				if(artworkRaw.getAuthors()!=null)artworkHist.setAuthors(artworkRaw.getAuthors());
				if(artworkRaw.getFormat()!=null)artworkHist.setFormat(artworkRaw.getFormat());
				if(artworkRaw.getSummary()!=null)artworkHist.setSummary(artworkRaw.getSummary());

				break;
			case UPDATE:
				if(artworkRaw.getArtworkId()==null||"".equals(artworkRaw.getArtworkId())){
					System.out.println("ArtworkId is null or empty");
					return null;
				}
				artworkHist.setId(opre.getId());

				artworkHist.setArtworkId(artworkRaw.getArtworkId());
				artworkHist.setHeight(opre.getHeight());
				artworkHist.setIndex(opre.getTxIndex());
				artworkHist.setTime(opre.getTime());
				artworkHist.setSigner(opre.getSigner());

				if(artworkRaw.getDid()!=null)artworkHist.setDid(artworkRaw.getDid());
				if(artworkRaw.getTitle()!=null)artworkHist.setTitle(artworkRaw.getTitle());
				if(artworkRaw.getLang()!=null)artworkHist.setLang(artworkRaw.getLang());
				if(artworkRaw.getAuthors()!=null)artworkHist.setAuthors(artworkRaw.getAuthors());
				if(artworkRaw.getFormat()!=null)artworkHist.setFormat(artworkRaw.getFormat());
				if(artworkRaw.getSummary()!=null)artworkHist.setSummary(artworkRaw.getSummary());

				break;
			case DELETE:
			case RECOVER:
				if(artworkRaw.getArtworkId()==null||"".equals(artworkRaw.getArtworkId())){
					System.out.println("ArtworkId is null or empty");
					return null;
				}
				artworkHist.setId(opre.getId());

				artworkHist.setArtworkId(artworkRaw.getArtworkId());
				artworkHist.setHeight(opre.getHeight());
				artworkHist.setIndex(opre.getTxIndex());
				artworkHist.setTime(opre.getTime());
				artworkHist.setSigner(opre.getSigner());

				break;
			case RATE:
				if(artworkRaw.getArtworkId()==null||"".equals(artworkRaw.getArtworkId())){
					System.out.println("ArtworkId is null or empty");
					return null;
				}
				if(artworkRaw.getRate()==null) {
					System.out.println("Rate is null");
					return null;
				}
				artworkHist.setId(opre.getId());

				artworkHist.setArtworkId(artworkRaw.getArtworkId());
				artworkHist.setHeight(opre.getHeight());
				artworkHist.setIndex(opre.getTxIndex());
				artworkHist.setTime(opre.getTime());
				artworkHist.setSigner(opre.getSigner());
				artworkHist.setRate(artworkRaw.getRate());
				artworkHist.setCdd(opre.getCdd());

				break;
			default:
				System.out.println("Invalid operation");
				return null;
		}

		return artworkHist;
	}


	public SoundHistory makeSound(OpReturn opre, Feip feip) {

		Gson gson = new Gson();

		SoundOpData soundRaw = new SoundOpData();
		try {
			soundRaw = gson.fromJson(gson.toJson(feip.getData()), SoundOpData.class);
			if(soundRaw==null){
				System.out.println("Sound raw is null");
				return null;
			}
		}catch(Exception e) {
			System.out.println("Failed to parse sound");
			return null;
		}

		SoundHistory soundHist = new SoundHistory();

		if(soundRaw.getOp()==null){
			System.out.println("OP is null");
			return null;
		}

		soundHist.setOp(soundRaw.getOp());

		switch(soundRaw.getOp()) {

			case PUBLISH:
				if(soundRaw.getTitle()==null||"".equals(soundRaw.getTitle())){
					System.out.println("Title is null or empty");
					return null;
				}
				if (opre.getHeight() > StartFEIP.CddCheckHeight) {
					Long cdd = opre.getCdd();
					if (cdd == null || cdd < StartFEIP.CddRequired) {
						System.out.println("Height is greater than CddCheckHeight and Cdd is null or less than CddRequired");
						return null;
					}
				}
				soundHist.setId(opre.getId());

				soundHist.setSoundId(opre.getId());
				soundHist.setHeight(opre.getHeight());
				soundHist.setIndex(opre.getTxIndex());
				soundHist.setTime(opre.getTime());
				soundHist.setSigner(opre.getSigner());

				if(soundRaw.getDid()!=null)soundHist.setDid(soundRaw.getDid());
				if(soundRaw.getTitle()!=null)soundHist.setTitle(soundRaw.getTitle());
				if(soundRaw.getLang()!=null)soundHist.setLang(soundRaw.getLang());
				if(soundRaw.getAuthors()!=null)soundHist.setAuthors(soundRaw.getAuthors());
				if(soundRaw.getFormat()!=null)soundHist.setFormat(soundRaw.getFormat());
				if(soundRaw.getSummary()!=null)soundHist.setSummary(soundRaw.getSummary());

				break;

			case UPDATE:
				if(soundRaw.getSoundId()==null|| soundRaw.getTitle()==null||"".equals(soundRaw.getTitle())){
					System.out.println("SoundId is null or title is null or empty");
					return null;
				}
				soundHist.setId(opre.getId());
				soundHist.setHeight(opre.getHeight());
				soundHist.setIndex(opre.getTxIndex());
				soundHist.setTime(opre.getTime());
				soundHist.setSigner(opre.getSigner());

				soundHist.setSoundId(soundRaw.getSoundId());

				if(soundRaw.getDid()!=null)soundHist.setDid(soundRaw.getDid());
				if(soundRaw.getTitle()!=null)soundHist.setTitle(soundRaw.getTitle());
				if(soundRaw.getLang()!=null)soundHist.setLang(soundRaw.getLang());
				if(soundRaw.getAuthors()!=null)soundHist.setAuthors(soundRaw.getAuthors());
				if(soundRaw.getFormat()!=null)soundHist.setFormat(soundRaw.getFormat());
				if(soundRaw.getSummary()!=null)soundHist.setSummary(soundRaw.getSummary());

				break;
			case RECOVER:
			case DELETE:
				if (soundRaw.getSoundIds() == null || soundRaw.getSoundIds().length == 0) {
					System.out.println("SoundIds is null or empty");
					return null;
				}
				soundHist.setSoundIds(soundRaw.getSoundIds());
				soundHist.setId(opre.getId());
				soundHist.setHeight(opre.getHeight());
				soundHist.setIndex(opre.getTxIndex());
				soundHist.setTime(opre.getTime());
				soundHist.setSigner(opre.getSigner());

				break;

			case RATE:
				if(soundRaw.getSoundId()==null){
					System.out.println("SoundId is null");
					return null;
				}
				if (soundRaw.getRate() == null) {
					System.out.println("Rate is null");
					return null;
				}
				Long rateCdd = opre.getCdd();
				if (rateCdd == null) {
					System.out.println("Cdd is null");
					return null;
				}
				if (rateCdd < StartFEIP.CddRequired) {
					System.out.println("Cdd is less than CddRequired");
					return null;
				}
				soundHist.setSoundId(soundRaw.getSoundId());
				soundHist.setRate(soundRaw.getRate());
				soundHist.setCdd(rateCdd);

				soundHist.setId(opre.getId());
				soundHist.setHeight(opre.getHeight());
				soundHist.setIndex(opre.getTxIndex());
				soundHist.setTime(opre.getTime());
				soundHist.setSigner(opre.getSigner());
				break;
			default:
				System.out.println("Invalid operation");
				return null;
		}
		return soundHist;
	}

	public boolean parseSound(ElasticsearchClient esClient, SoundHistory soundHist) throws Exception {

		if(soundHist==null){
			System.out.println("Sound hist is null");
			return false;
		}
		Sound sound;
		switch (soundHist.getOp()) {
			case PUBLISH -> {
				sound = EsUtils.getById(esClient, IndicesNames.SOUND, soundHist.getSoundId(), Sound.class);
				if (sound == null) {
					sound = new Sound();

					sound.setId(soundHist.getSoundId());
					sound.setVer("1");
					sound.setDid(soundHist.getDid());

					sound.setLang(soundHist.getLang());
					sound.setTitle(soundHist.getTitle());
					sound.setAuthors(soundHist.getAuthors());
					sound.setFormat(soundHist.getFormat());
					sound.setSummary(soundHist.getSummary());

					sound.setPublisher(soundHist.getSigner());

					sound.setBirthTime(soundHist.getTime());
					sound.setBirthHeight(soundHist.getHeight());
					sound.setLastTxId(soundHist.getId());
					sound.setLastTime(soundHist.getTime());
					sound.setLastHeight(soundHist.getHeight());

					sound.setDeleted(false);

					Sound sound1 = sound;

					IndexResponse result1 = esClient.index(i -> i.index(IndicesNames.SOUND).id(soundHist.getSoundId()).document(sound1));
					if(result1==null||result1.result()==null){
						System.out.println("Failed to create sound");
						return false;
					}
					if(!CREATED.equals(result1.result().jsonValue()) && !UPDATED.equals(result1.result().jsonValue())){
						System.out.println("Failed to create sound");
						return false;
					}

					// Create news
					News.createNews(esClient, soundHist.getId(), soundHist.getSigner(), PUBLISH,
					Feip.FeipProtocol.SOUND.getName(), soundHist.getId(), soundHist.getTitle(), soundHist.getSummary(),
							soundHist.getHeight(), soundHist.getTime());

					return true;
				} else {
					System.out.println("Sound already exists");
					return false;
				}
			}
			case UPDATE -> {
				sound = EsUtils.getById(esClient, IndicesNames.SOUND, soundHist.getSoundId(), Sound.class);
				if (sound == null) {
					System.out.println("Sound not found");
					return false;
				}
				if (Boolean.TRUE.equals(sound.isDeleted())) {
					System.out.println("Sound is deleted");
					return false;
				}
				if (!sound.getPublisher().equals(soundHist.getSigner())) {
					System.out.println("Sound publisher is not the same as the signer");
					return false;
				}
				sound.setVer(String.valueOf(Integer.parseInt(sound.getVer())+1));
				sound.setDid(soundHist.getDid());
				sound.setTitle(soundHist.getTitle());
				sound.setLang(soundHist.getLang());
				sound.setAuthors(soundHist.getAuthors());
				sound.setFormat(soundHist.getFormat());
				sound.setSummary(soundHist.getSummary());
				sound.setLastTxId(soundHist.getId());
				sound.setLastTime(soundHist.getTime());
				sound.setLastHeight(soundHist.getHeight());
				Sound sound2 = sound;
				IndexResponse result2 = esClient.index(i -> i.index(IndicesNames.SOUND).id(soundHist.getSoundId()).document(sound2));
				System.out.println(result2.result());
				return CREATED.equals(result2.result().jsonValue()) || UPDATED.equals(result2.result().jsonValue());
			}

			case DELETE, RECOVER -> {
				List<String> idList = new ArrayList<>();
				if (soundHist.getSoundIds() != null && soundHist.getSoundIds().length > 0) {
					idList.addAll(Arrays.asList(soundHist.getSoundIds()));
				} else {
					System.out.println("SoundIds is null or empty");
					return false;
				}

				EsUtils.MgetResult<Sound> result = EsUtils.getMultiByIdList(esClient, IndicesNames.SOUND, idList, Sound.class);
				if (result == null) {
					System.out.println("Sound mget result is null");
					return false;
				}
				List<Sound> sounds = result.getResultList();
				if(sounds==null||sounds.isEmpty()){
					System.out.println("Sounds is null or empty");
					return false;
				}

				List<Sound> updatedSounds = new ArrayList<>();
				for (Sound soundItem : sounds) {

					if (!soundItem.getPublisher().equals(soundHist.getSigner())) {
						Freer resultCid = EsUtils.getById(esClient, IndicesNames.FREER, soundHist.getSigner(), Freer.class);
						if (resultCid ==null || resultCid.getMaster() == null || !resultCid.getMaster().equals(soundHist.getSigner())) {
							continue;
						}
					}

					switch (soundHist.getOp()) {
						case DELETE:
							soundItem.setDeleted(true);
							break;
						case RECOVER:
							soundItem.setDeleted(false);
							break;
					}

					soundItem.setLastTxId(soundHist.getId());
					soundItem.setLastTime(soundHist.getTime());
					soundItem.setLastHeight(soundHist.getHeight());

					updatedSounds.add(soundItem);
				}


				if (!updatedSounds.isEmpty()) {
					BulkRequest.Builder br = new BulkRequest.Builder();
					for (Sound updatedSound : updatedSounds) {
						br.operations(op -> op
								.index(idx -> idx
										.index(IndicesNames.SOUND)
										.id(updatedSound.getId())
										.document(updatedSound)
								)
						);
					}
					BulkResponse result3 = esClient.bulk(br.build());
					if(result3.errors()){
						System.out.println("Failed to bulk update sound");
						return false;
					}
					return true;
				}
				System.out.println("No sound matched publisher/master filter");
				return false;
			}

			case RATE -> {
				sound = EsUtils.getById(esClient, IndicesNames.SOUND, soundHist.getSoundId(), Sound.class);
				if (sound == null) {
					System.out.println("Sound not found");
					return false;
				}
				if (sound.getPublisher().equals(soundHist.getSigner())) {
					System.out.println("Sound publisher is the same as the signer");
					return false;
				}

				if((soundHist.getCdd()==null || soundHist.getRate()==null)){
					System.out.println("Cdd or rate is null");
					return false;
				}

				if(sound.gettCdd()==null||sound.gettRate()==null){
					sound.settRate(Float.valueOf(soundHist.getRate()));
					sound.settCdd(soundHist.getCdd());
				}else{
					sound.settRate(
							(sound.gettRate()*sound.gettCdd()+soundHist.getRate()*soundHist.getCdd())
									/(sound.gettCdd()+soundHist.getCdd())
					);
					sound.settCdd(sound.gettCdd() + soundHist.getCdd());
				}

				sound.setLastTxId(soundHist.getId());
				sound.setLastTime(soundHist.getTime());
				sound.setLastHeight(soundHist.getHeight());
				Sound sound3 = sound;
				IndexResponse result3 = esClient.index(i -> i.index(IndicesNames.SOUND).id(soundHist.getSoundId()).document(sound3));
				System.out.println(result3.result());
				return CREATED.equals(result3.result().jsonValue()) || UPDATED.equals(result3.result().jsonValue());
			}
		}
		return false;
	}

	public ImageHistory makeImage(OpReturn opre, Feip feip) {

		Gson gson = new Gson();

		ImageOpData imageRaw = new ImageOpData();
		try {
			imageRaw = gson.fromJson(gson.toJson(feip.getData()), ImageOpData.class);
			if(imageRaw==null){
				System.out.println("Image raw is null");
				return null;
			}
		}catch(Exception e) {
			System.out.println("Failed to parse image");
			return null;
		}

		ImageHistory imageHist = new ImageHistory();

		if(imageRaw.getOp()==null){
			System.out.println("OP is null");
			return null;
		}

		imageHist.setOp(imageRaw.getOp());

		switch(imageRaw.getOp()) {

			case PUBLISH:
				if(imageRaw.getTitle()==null||"".equals(imageRaw.getTitle())){
					System.out.println("Title is null or empty");
					return null;
				}
				if (opre.getHeight() > StartFEIP.CddCheckHeight) {
					Long cdd = opre.getCdd();
					if (cdd == null || cdd < StartFEIP.CddRequired) {
						System.out.println("Height is greater than CddCheckHeight and Cdd is null or less than CddRequired");
						return null;
					}
				}
				imageHist.setId(opre.getId());

				imageHist.setImageId(opre.getId());
				imageHist.setHeight(opre.getHeight());
				imageHist.setIndex(opre.getTxIndex());
				imageHist.setTime(opre.getTime());
				imageHist.setSigner(opre.getSigner());

				if(imageRaw.getDid()!=null)imageHist.setDid(imageRaw.getDid());
				if(imageRaw.getTitle()!=null)imageHist.setTitle(imageRaw.getTitle());
				if(imageRaw.getLang()!=null)imageHist.setLang(imageRaw.getLang());
				if(imageRaw.getAuthors()!=null)imageHist.setAuthors(imageRaw.getAuthors());
				if(imageRaw.getFormat()!=null)imageHist.setFormat(imageRaw.getFormat());
				if(imageRaw.getSummary()!=null)imageHist.setSummary(imageRaw.getSummary());

				break;

			case UPDATE:
				if(imageRaw.getImageId()==null|| imageRaw.getTitle()==null||"".equals(imageRaw.getTitle())){
					System.out.println("ImageId is null or title is null or empty");
					return null;
				}
				imageHist.setId(opre.getId());
				imageHist.setHeight(opre.getHeight());
				imageHist.setIndex(opre.getTxIndex());
				imageHist.setTime(opre.getTime());
				imageHist.setSigner(opre.getSigner());

				imageHist.setImageId(imageRaw.getImageId());

				if(imageRaw.getDid()!=null)imageHist.setDid(imageRaw.getDid());
				if(imageRaw.getTitle()!=null)imageHist.setTitle(imageRaw.getTitle());
				if(imageRaw.getLang()!=null)imageHist.setLang(imageRaw.getLang());
				if(imageRaw.getAuthors()!=null)imageHist.setAuthors(imageRaw.getAuthors());
				if(imageRaw.getFormat()!=null)imageHist.setFormat(imageRaw.getFormat());
				if(imageRaw.getSummary()!=null)imageHist.setSummary(imageRaw.getSummary());

				break;
			case RECOVER:
			case DELETE:
				if (imageRaw.getImageIds() == null || imageRaw.getImageIds().length == 0) {
					System.out.println("ImageIds is null or empty");
					return null;
				}
				imageHist.setImageIds(imageRaw.getImageIds());
				imageHist.setId(opre.getId());
				imageHist.setHeight(opre.getHeight());
				imageHist.setIndex(opre.getTxIndex());
				imageHist.setTime(opre.getTime());
				imageHist.setSigner(opre.getSigner());

				break;

			case RATE:
				if(imageRaw.getImageId()==null){
					System.out.println("ImageId is null");
					return null;
				}
				if (imageRaw.getRate() == null) {
					System.out.println("Rate is null");
					return null;
				}
				Long imageRateCdd = opre.getCdd();
				if (imageRateCdd == null) {
					System.out.println("Cdd is null");
					return null;
				}
				if (imageRateCdd < StartFEIP.CddRequired){
					System.out.println("Cdd is less than CddRequired");
					return null;
				}
				imageHist.setImageId(imageRaw.getImageId());
				imageHist.setRate(imageRaw.getRate());
				imageHist.setCdd(imageRateCdd);

				imageHist.setId(opre.getId());
				imageHist.setHeight(opre.getHeight());
				imageHist.setIndex(opre.getTxIndex());
				imageHist.setTime(opre.getTime());
				imageHist.setSigner(opre.getSigner());
				break;
			default:
				System.out.println("Invalid operation");
				return null;
		}
		return imageHist;
	}

	public boolean parseImage(ElasticsearchClient esClient, ImageHistory imageHist) throws Exception {

		if(imageHist==null){
			System.out.println("Image hist is null");
			return false;
		}
		Image image;
		switch (imageHist.getOp()) {
			case PUBLISH -> {
				image = EsUtils.getById(esClient, IndicesNames.IMAGE, imageHist.getImageId(), Image.class);
				if (image == null) {
					image = new Image();

					image.setId(imageHist.getImageId());
					image.setVer("1");
					image.setDid(imageHist.getDid());

					image.setLang(imageHist.getLang());
					image.setTitle(imageHist.getTitle());
					image.setAuthors(imageHist.getAuthors());
					image.setFormat(imageHist.getFormat());
					image.setSummary(imageHist.getSummary());

					image.setPublisher(imageHist.getSigner());

					image.setBirthTime(imageHist.getTime());
					image.setBirthHeight(imageHist.getHeight());
					image.setLastTxId(imageHist.getId());
					image.setLastTime(imageHist.getTime());
					image.setLastHeight(imageHist.getHeight());

					image.setDeleted(false);

					Image image1 = image;

					IndexResponse result1 = esClient.index(i -> i.index(IndicesNames.IMAGE).id(imageHist.getImageId()).document(image1));
					if(result1==null||result1.result()==null){
						System.out.println("Failed to create image");
						return false;
					}
					if(!CREATED.equals(result1.result().jsonValue()) && !UPDATED.equals(result1.result().jsonValue())){
						System.out.println("Failed to create image");
						return false;
					}

					// Create news record for image publication
					News.createNews(esClient, imageHist.getId(), imageHist.getSigner(), PUBLISH,
					Feip.FeipProtocol.IMAGE.getName(), imageHist.getId(), imageHist.getTitle(), imageHist.getSummary(),
							imageHist.getHeight(), imageHist.getTime());

					return true;
				} else {
					System.out.println("Image already exists");
					return false;
				}
			}
			case UPDATE -> {
				image = EsUtils.getById(esClient, IndicesNames.IMAGE, imageHist.getImageId(), Image.class);
				if (image == null) {
					System.out.println("Image not found");
					return false;
				}
				if (Boolean.TRUE.equals(image.isDeleted())) {
					System.out.println("Image is deleted");
					return false;
				}
				if (!image.getPublisher().equals(imageHist.getSigner())) {
					System.out.println("Image publisher is not the same as the signer");
					return false;
				}
				image.setVer(String.valueOf(Integer.parseInt(image.getVer())+1));
				image.setDid(imageHist.getDid());
				image.setTitle(imageHist.getTitle());
				image.setLang(imageHist.getLang());
				image.setAuthors(imageHist.getAuthors());
				image.setFormat(imageHist.getFormat());
				image.setSummary(imageHist.getSummary());
				image.setLastTxId(imageHist.getId());
				image.setLastTime(imageHist.getTime());
				image.setLastHeight(imageHist.getHeight());
				Image image2 = image;
				IndexResponse result2 = esClient.index(i -> i.index(IndicesNames.IMAGE).id(imageHist.getImageId()).document(image2));
				System.out.println(result2.result());
				return CREATED.equals(result2.result().jsonValue()) || UPDATED.equals(result2.result().jsonValue());
			}

			case DELETE, RECOVER -> {
				List<String> idList = new ArrayList<>();
				if (imageHist.getImageIds() != null && imageHist.getImageIds().length > 0) {
					idList.addAll(Arrays.asList(imageHist.getImageIds()));
				} else {
					System.out.println("ImageIds is null or empty");
					return false;
				}

				EsUtils.MgetResult<Image> result = EsUtils.getMultiByIdList(esClient, IndicesNames.IMAGE, idList, Image.class);
				if (result == null) {
					System.out.println("Image mget result is null");
					return false;
				}
				List<Image> images = result.getResultList();
				if (images == null || images.isEmpty()) {
					System.out.println("Images is null or empty");
					return false;
				}

				List<Image> updatedImages = new ArrayList<>();
				for (Image imageItem : images) {

					if (!imageItem.getPublisher().equals(imageHist.getSigner())) {
						Freer resultCid = EsUtils.getById(esClient, IndicesNames.FREER, imageHist.getSigner(), Freer.class);
						if (resultCid ==null || resultCid.getMaster() == null || !resultCid.getMaster().equals(imageHist.getSigner())) {
							continue;
						}
					}

					switch (imageHist.getOp()) {
						case DELETE:
							imageItem.setDeleted(true);
							break;
						case RECOVER:
							imageItem.setDeleted(false);
							break;
					}

					imageItem.setLastTxId(imageHist.getId());
					imageItem.setLastTime(imageHist.getTime());
					imageItem.setLastHeight(imageHist.getHeight());

					updatedImages.add(imageItem);
				}

				if (!updatedImages.isEmpty()) {
					BulkRequest.Builder br = new BulkRequest.Builder();
					for (Image updatedImage : updatedImages) {
						br.operations(op -> op
								.index(idx -> idx
										.index(IndicesNames.IMAGE)
										.id(updatedImage.getId())
										.document(updatedImage)
								)
						);
					}
					BulkResponse result3 = esClient.bulk(br.build());
					if(result3.errors()){
						System.out.println("Failed to bulk update image");
						return false;
					}
					return true;
				}
				System.out.println("No image matched publisher/master filter; delete/recover skipped");
				return false;
			}

			case RATE -> {
				image = EsUtils.getById(esClient, IndicesNames.IMAGE, imageHist.getImageId(), Image.class);
				if (image == null) {
					System.out.println("Image not found");
					return false;
				}
				if (image.getPublisher().equals(imageHist.getSigner())) {
					System.out.println("Image publisher is the same as the signer");
					return false;
				}

				if((imageHist.getCdd()==null || imageHist.getRate()==null)){
					System.out.println("Cdd or rate is null");
					return false;
				}

				if(image.gettCdd()==null||image.gettRate()==null){
					image.settRate(Float.valueOf(imageHist.getRate()));
					image.settCdd(imageHist.getCdd());
				}else{
					image.settRate(
							(image.gettRate()*image.gettCdd()+imageHist.getRate()*imageHist.getCdd())
									/(image.gettCdd()+imageHist.getCdd())
					);
					image.settCdd(image.gettCdd() + imageHist.getCdd());
				}

				image.setLastTxId(imageHist.getId());
				image.setLastTime(imageHist.getTime());
				image.setLastHeight(imageHist.getHeight());
				Image image3 = image;
				IndexResponse result3 = esClient.index(i -> i.index(IndicesNames.IMAGE).id(imageHist.getImageId()).document(image3));
				System.out.println(result3.result());
				return CREATED.equals(result3.result().jsonValue()) || UPDATED.equals(result3.result().jsonValue());
			}
		}
		return false;
	}

	public VideoHistory makeVideo(OpReturn opre, Feip feip) {

		Gson gson = new Gson();

		VideoOpData videoRaw = new VideoOpData();
		try {
			videoRaw = gson.fromJson(gson.toJson(feip.getData()), VideoOpData.class);
			if(videoRaw==null){
				System.out.println("Video raw is null");
				return null;
			}
		}catch(Exception e) {
			System.out.println("Failed to parse video");
			return null;
		}
		VideoHistory videoHist = new VideoHistory();

		if(videoRaw.getOp()==null){
			System.out.println("OP is null");
			return null;
		}

		videoHist.setOp(videoRaw.getOp());

		switch(videoRaw.getOp()) {

			case PUBLISH:
				if(videoRaw.getTitle()==null||"".equals(videoRaw.getTitle())){
					System.out.println("Title is null or empty");
					return null;
				}
				if (opre.getHeight() > StartFEIP.CddCheckHeight) {
					Long cdd = opre.getCdd();
					if (cdd == null || cdd < StartFEIP.CddRequired) {
						System.out.println("Height is greater than CddCheckHeight and Cdd is null or less than CddRequired");
						return null;
					}
				}
				videoHist.setId(opre.getId());

				videoHist.setVideoId(opre.getId());
				videoHist.setHeight(opre.getHeight());
				videoHist.setIndex(opre.getTxIndex());
				videoHist.setTime(opre.getTime());
				videoHist.setSigner(opre.getSigner());

				if(videoRaw.getDid()!=null)videoHist.setDid(videoRaw.getDid());
				if(videoRaw.getTitle()!=null)videoHist.setTitle(videoRaw.getTitle());
				if(videoRaw.getLang()!=null)videoHist.setLang(videoRaw.getLang());
				if(videoRaw.getAuthors()!=null)videoHist.setAuthors(videoRaw.getAuthors());
				if(videoRaw.getFormat()!=null)videoHist.setFormat(videoRaw.getFormat());
				if(videoRaw.getSummary()!=null)videoHist.setSummary(videoRaw.getSummary());

				break;

			case UPDATE:
				if(videoRaw.getVideoId()==null|| videoRaw.getTitle()==null||"".equals(videoRaw.getTitle())){
					System.out.println("VideoId is null or title is null or empty");
					return null;
				}
				videoHist.setId(opre.getId());
				videoHist.setHeight(opre.getHeight());
				videoHist.setIndex(opre.getTxIndex());
				videoHist.setTime(opre.getTime());
				videoHist.setSigner(opre.getSigner());

				videoHist.setVideoId(videoRaw.getVideoId());

				if(videoRaw.getDid()!=null)videoHist.setDid(videoRaw.getDid());
				if(videoRaw.getTitle()!=null)videoHist.setTitle(videoRaw.getTitle());
				if(videoRaw.getLang()!=null)videoHist.setLang(videoRaw.getLang());
				if(videoRaw.getAuthors()!=null)videoHist.setAuthors(videoRaw.getAuthors());
				if(videoRaw.getFormat()!=null)videoHist.setFormat(videoRaw.getFormat());
				if(videoRaw.getSummary()!=null)videoHist.setSummary(videoRaw.getSummary());

				break;
			case RECOVER:
			case DELETE:
				if (videoRaw.getVideoIds() == null || videoRaw.getVideoIds().length == 0) {
					System.out.println("VideoIds is null or empty");
					return null;
				}
				videoHist.setVideoIds(videoRaw.getVideoIds());
				videoHist.setId(opre.getId());
				videoHist.setHeight(opre.getHeight());
				videoHist.setIndex(opre.getTxIndex());
				videoHist.setTime(opre.getTime());
				videoHist.setSigner(opre.getSigner());

				break;

			case RATE:
				if(videoRaw.getVideoId()==null){
					System.out.println("VideoId is null");
					return null;
				}
				if (videoRaw.getRate() == null) {
					System.out.println("Rate is null");
					return null;
				}
				Long videoRateCdd = opre.getCdd();
				if (videoRateCdd == null) {
					System.out.println("Cdd is null");
					return null;
				}
				if (videoRateCdd < StartFEIP.CddRequired){
					System.out.println("Cdd is less than CddRequired");
					return null;
				}
				videoHist.setVideoId(videoRaw.getVideoId());
				videoHist.setRate(videoRaw.getRate());
				videoHist.setCdd(videoRateCdd);

				videoHist.setId(opre.getId());
				videoHist.setHeight(opre.getHeight());
				videoHist.setIndex(opre.getTxIndex());
				videoHist.setTime(opre.getTime());
				videoHist.setSigner(opre.getSigner());
				break;
			default:
				System.out.println("Invalid operation");
				return null;
		}
		return videoHist;
	}

	public boolean parseVideo(ElasticsearchClient esClient, VideoHistory videoHist) throws Exception {

		if(videoHist==null){
			System.out.println("Video hist is null");
			return false;
		}
		Video video;
		switch (videoHist.getOp()) {
			case PUBLISH -> {
				video = EsUtils.getById(esClient, IndicesNames.VIDEO, videoHist.getVideoId(), Video.class);
				if (video == null) {
					video = new Video();

					video.setId(videoHist.getVideoId());
					video.setVer("1");
					video.setDid(videoHist.getDid());

					video.setLang(videoHist.getLang());
					video.setTitle(videoHist.getTitle());
					video.setAuthors(videoHist.getAuthors());
					video.setFormat(videoHist.getFormat());
					video.setSummary(videoHist.getSummary());

					video.setPublisher(videoHist.getSigner());

					video.setBirthTime(videoHist.getTime());
					video.setBirthHeight(videoHist.getHeight());
					video.setLastTxId(videoHist.getId());
					video.setLastTime(videoHist.getTime());
					video.setLastHeight(videoHist.getHeight());

					video.setDeleted(false);

					Video video1 = video;

					IndexResponse result1 = esClient.index(i -> i.index(IndicesNames.VIDEO).id(videoHist.getVideoId()).document(video1));
					if(result1==null||result1.result()==null){
						System.out.println("Failed to create video");
						return false;
					}
					if(!CREATED.equals(result1.result().jsonValue()) && !UPDATED.equals(result1.result().jsonValue())){
						System.out.println("Failed to create video");
						return false;
					}

					// Create news record for video publication
					News.createNews(esClient, videoHist.getId(), videoHist.getSigner(), PUBLISH,
					Feip.FeipProtocol.VIDEO.getName(), videoHist.getId(), videoHist.getTitle(), videoHist.getSummary(),
							videoHist.getHeight(), videoHist.getTime());

					return true;
				} else {
					System.out.println("Video already exists");
					return false;
				}
			}
			case UPDATE -> {
				video = EsUtils.getById(esClient, IndicesNames.VIDEO, videoHist.getVideoId(), Video.class);
				if (video == null) {
					System.out.println("Video not found");
					return false;
				}
				if (Boolean.TRUE.equals(video.isDeleted())) {
					System.out.println("Video is deleted");
					return false;
				}
				if (!video.getPublisher().equals(videoHist.getSigner())) {
					System.out.println("Video publisher is not the same as the signer");
					return false;
				}
				video.setVer(String.valueOf(Integer.parseInt(video.getVer())+1));
				video.setDid(videoHist.getDid());
				video.setTitle(videoHist.getTitle());
				video.setLang(videoHist.getLang());
				video.setAuthors(videoHist.getAuthors());
				video.setFormat(videoHist.getFormat());
				video.setSummary(videoHist.getSummary());
				video.setLastTxId(videoHist.getId());
				video.setLastTime(videoHist.getTime());
				video.setLastHeight(videoHist.getHeight());
				Video video2 = video;
				IndexResponse result2 = esClient.index(i -> i.index(IndicesNames.VIDEO).id(videoHist.getVideoId()).document(video2));
				System.out.println(result2.result());
				return CREATED.equals(result2.result().jsonValue()) || UPDATED.equals(result2.result().jsonValue());
			}

			case DELETE, RECOVER -> {
				List<String> idList = new ArrayList<>();
				if (videoHist.getVideoIds() != null && videoHist.getVideoIds().length > 0) {
					idList.addAll(Arrays.asList(videoHist.getVideoIds()));
				} else {
					System.out.println("VideoIds is null or empty");
					return false;
				}

				EsUtils.MgetResult<Video> result = EsUtils.getMultiByIdList(esClient, IndicesNames.VIDEO, idList, Video.class);
				if (result == null) {
					System.out.println("Video mget result is null");
					return false;
				}
				List<Video> videos = result.getResultList();
				if (videos == null || videos.isEmpty()) {
					System.out.println("Videos is null or empty");
					return false;
				}

				List<Video> updatedVideos = new ArrayList<>();
				for (Video videoItem : videos) {

					if (!videoItem.getPublisher().equals(videoHist.getSigner())) {
						Freer resultCid = EsUtils.getById(esClient, IndicesNames.FREER, videoHist.getSigner(), Freer.class);
						if (resultCid ==null || resultCid.getMaster() == null || !resultCid.getMaster().equals(videoHist.getSigner())) {
							continue;
						}
					}

					switch (videoHist.getOp()) {
						case DELETE:
							videoItem.setDeleted(true);
							break;
						case RECOVER:
							videoItem.setDeleted(false);
							break;
					}

					videoItem.setLastTxId(videoHist.getId());
					videoItem.setLastTime(videoHist.getTime());
					videoItem.setLastHeight(videoHist.getHeight());

					updatedVideos.add(videoItem);
				}

				if (!updatedVideos.isEmpty()) {
					BulkRequest.Builder br = new BulkRequest.Builder();
					for (Video updatedVideo : updatedVideos) {
						br.operations(op -> op
								.index(idx -> idx
										.index(IndicesNames.VIDEO)
										.id(updatedVideo.getId())
										.document(updatedVideo)
								)
						);
					}
					BulkResponse result3 = esClient.bulk(br.build());
					if(result3.errors()){
						System.out.println("Failed to bulk update video");
						return false;
					}
					return true;
				}
				System.out.println("No video matched publisher/master filter; delete/recover skipped");
				return false;
			}

			case RATE -> {
				video = EsUtils.getById(esClient, IndicesNames.VIDEO, videoHist.getVideoId(), Video.class);
				if (video == null) {
					System.out.println("Video not found");
					return false;
				}
				if (video.getPublisher().equals(videoHist.getSigner())) {
					System.out.println("Video publisher is the same as the signer");
					return false;
				}

				if((videoHist.getCdd()==null || videoHist.getRate()==null)){
					System.out.println("Cdd or rate is null");
					return false;
				}

				if(video.gettCdd()==null||video.gettRate()==null){
					video.settRate(Float.valueOf(videoHist.getRate()));
					video.settCdd(videoHist.getCdd());
				}else{
					video.settRate(
							(video.gettRate()*video.gettCdd()+videoHist.getRate()*videoHist.getCdd())
									/(video.gettCdd()+videoHist.getCdd())
					);
					video.settCdd(video.gettCdd() + videoHist.getCdd());
				}

				video.setLastTxId(videoHist.getId());
				video.setLastTime(videoHist.getTime());
				video.setLastHeight(videoHist.getHeight());
				Video video3 = video;
				IndexResponse result3 = esClient.index(i -> i.index(IndicesNames.VIDEO).id(videoHist.getVideoId()).document(video3));
				System.out.println(result3.result());
				return CREATED.equals(result3.result().jsonValue()) || UPDATED.equals(result3.result().jsonValue());
			}
		}

		return false;
	}

}
