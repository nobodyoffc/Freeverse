package publish;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import com.google.gson.Gson;
import constants.IndicesNames;
import startFEIP.FeipConstants;
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

	private static final Logger log = LoggerFactory.getLogger(PublishParser.class);

	public boolean parseStatement(ElasticsearchClient esClient, OpReturn opre, Feip feip) throws ElasticsearchException, IOException {

		Gson gson = new Gson();

		StatementOpData statementRaw = new StatementOpData();

		try {
			statementRaw = gson.fromJson(gson.toJsonTree(feip.getData()), StatementOpData.class);
			if(statementRaw==null){
				log.info("Statement raw is null");
				return false;
			}
		}catch(com.google.gson.JsonSyntaxException e) {
			log.info("Statement raw is null");
			return false;
		}

		Statement statement = new Statement();

		statement.setId(opre.getId());

		if(statementRaw.getConfirm()==null){
			log.info("Confirm is null");
			return false;
		}

		if(!statementRaw.getConfirm().equals(FeipConstants.CONFIRM_STATEMENT)){
			log.info("Confirm is not a formal and irrevocable statement");
			return false;
		}

		if(statementRaw.getTitle()==null && statementRaw.getContent()==null){
			log.info("Title and content are null");
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
			log.info("Failed to create statement");
			return false;
		}
		if(!CREATED.equals(result.result().jsonValue()) && !UPDATED.equals(result.result().jsonValue())){
			log.info("Failed to create statement");
			return false;
		}
		log.info("{}", result.result());
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
			textRaw = gson.fromJson(gson.toJsonTree(feip.getData()), TextOpData.class);
			if(textRaw==null){
				log.info("Text raw is null");
				return null;
			}
		}catch(Exception e) {
			log.info("Failed to parse text");
			return null;
		}

		TextHistory textHist = new TextHistory();

		if(textRaw.getOp()==null){
			log.info("OP is null");
			return null;
		}

		textHist.setOp(textRaw.getOp());

		switch(textRaw.getOp()) {

			case PUBLISH:
				if(textRaw.getTitle()==null||"".equals(textRaw.getTitle())){
					log.info("Title is null or empty");
					return null;
				}
				if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired){
					log.info("Height is greater than CddCheckHeight and Cdd is less than CddRequired");
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
					log.info("TextId is null or title is null or empty");
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
					log.info("TextIds is null or empty");
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
					log.info("TextId is null");
					return null;
				}
				if (opre.getCdd() == null) {
					log.info("Cdd is null");
					return null;
				}

				if (opre.getCdd() < StartFEIP.CddRequired) {
					log.info("Cdd is less than CddRequired");
					return null;
				}

				if (textRaw.getRate() == null) {
					log.info("Rate is null");
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
				log.info("Invalid operation");
				return null;
		}
		return textHist;
	}


	public boolean parseText(ElasticsearchClient esClient, TextHistory textHist) throws Exception {

		if(textHist==null){
			log.info("Text hist is null");
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
						log.info("Failed to create text");
						return false;
					}
					if(!CREATED.equals(result1.result().jsonValue()) && !UPDATED.equals(result1.result().jsonValue())){
						log.info("Failed to create text");
						return false;
					}
					// Create news record for text publication
					News.createNews(esClient, textHist.getId(), textHist.getSigner(), PUBLISH,
							Feip.FeipProtocol.TEXT.getName(), textHist.getId(), textHist.getTitle(), null,
							textHist.getHeight(), textHist.getTime());

					return true;
				} else {
					log.info("Text already exists");
					return false;
				}
			}
			case UPDATE -> {
				text = EsUtils.getById(esClient, IndicesNames.TEXT, textHist.getTextId(), Text.class);
				if (text == null) {
					log.info("Text not found");
					return false;
				}
				if (Boolean.TRUE.equals(text.isDeleted())) {
					log.info("Text is deleted");
					return false;
				}
				if (!text.getPublisher().equals(textHist.getSigner())) {
					log.info("Text publisher is not the same as the signer");
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
					log.info("Failed to update text");
					return false;
				}
				if(!CREATED.equals(result2.result().jsonValue()) && !UPDATED.equals(result2.result().jsonValue())){
					log.info("Failed to update text");
					return false;
				}
				return true;
			}

			case DELETE, RECOVER -> {
				List<String> idList = new ArrayList<>();
				if (textHist.getTextIds() != null && textHist.getTextIds().size() > 0) {
					idList.addAll(textHist.getTextIds());
				} else {
					log.info("TextIds is null or empty");
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
						log.info("Failed to bulk update text");
						return false;
					}
					return true;
				}
				log.info("No text matched publisher/master filter");
				return false;
			}

			case RATE -> {
				text = EsUtils.getById(esClient, IndicesNames.TEXT, textHist.getTextId(), Text.class);
				if (text == null) {
					log.info("Text not found");
					return false;
				}
				if (text.getPublisher().equals(textHist.getSigner())) {
					log.info("Text publisher is the same as the signer");
					return false;
				}

				if((textHist.getCdd()==null || textHist.getRate()==null)){
					log.info("Cdd or rate is null");
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
			remarkRaw = gson.fromJson(gson.toJsonTree(feip.getData()), RemarkOpData.class);
			if(remarkRaw==null){
				log.info("Remark raw is null");
				return null;
			}
		}catch(Exception e) {
			log.info("Failed to parse remark");
			return null;
		}

		RemarkHistory remarkHist = new RemarkHistory();

		if(remarkRaw.getOp()==null){
			log.info("OP is null");
			return null;
		}

		remarkHist.setOp(remarkRaw.getOp());

		switch(remarkRaw.getOp()) {

			case PUBLISH:
				if(remarkRaw.getTitle()==null||"".equals(remarkRaw.getTitle())){
					log.info("Title is null or empty");
					return null;
				}
				if (opre.getHeight() > StartFEIP.CddCheckHeight) {
					Long cdd = opre.getCdd();
					if (cdd == null || cdd < StartFEIP.CddRequired) {
						log.info("Height is greater than CddCheckHeight and Cdd is null or less than CddRequired");
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
					log.info("RemarkId is null or title is null or empty");
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
					log.info("RemarkIds is null or empty");
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
					log.info("RemarkId is null");
					return null;
				}
				if (remarkRaw.getRate() == null) {
					log.info("Rate is null");
					return null;
				}
				Long remarkRateCdd = opre.getCdd();
				if (remarkRateCdd == null) {
					log.info("Cdd is null");
					return null;
				}
				if (remarkRateCdd < StartFEIP.CddRequired) {
					log.info("Cdd is less than CddRequired");
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
				log.info("Invalid operation");
				return null;
		}
		return remarkHist;
	}

	public boolean parseRemark(ElasticsearchClient esClient, RemarkHistory remarkHist) throws Exception {

		if(remarkHist==null){
			log.info("Remark hist is null");
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
						log.info("Failed to create remark");
						return false;
					}
					if(!CREATED.equals(result1.result().jsonValue()) && !UPDATED.equals(result1.result().jsonValue())){
						log.info("Failed to create remark");
						return false;
					}
					return true;
				} else {
					log.info("Remark already exists");
					return false;
				}
			}
			case UPDATE -> {
				remark = EsUtils.getById(esClient, IndicesNames.REMARK, remarkHist.getRemarkId(), Remark.class);
				if (remark == null) {
					log.info("Remark not found");
					return false;
				}
				if (Boolean.TRUE.equals(remark.isDeleted())) {
					log.info("Remark is deleted");
					return false;
				}
				if (!remark.getPublisher().equals(remarkHist.getSigner())) {
					log.info("Remark publisher is not the same as the signer");
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
					log.info("RemarkIds is null or empty");
					return false;
				}

				EsUtils.MgetResult<Remark> result = EsUtils.getMultiByIdList(esClient, IndicesNames.REMARK, idList, Remark.class);
				if (result == null) {
					log.info("Remark mget result is null");
					return false;
				}
				List<Remark> remarks = result.getResultList();
				if (remarks == null || remarks.isEmpty()) {
					log.info("Remarks is null or empty");
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
						log.info("Failed to bulk update remark");
						return false;
					}
					return true;
				}
				log.info("No remark matched publisher/master filter; delete/recover skipped");
				return false;
			}

			case RATE -> {
				remark = EsUtils.getById(esClient, IndicesNames.REMARK, remarkHist.getRemarkId(), Remark.class);
				if (remark == null) {
					log.info("Remark not found");
					return false;
				}
				if (remark.getPublisher().equals(remarkHist.getSigner())) {
					log.info("Remark publisher is the same as the signer");
					return false;
				}

				if((remarkHist.getCdd()==null || remarkHist.getRate()==null)){
					log.info("Cdd or rate is null");
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
				log.info("{}", result3.result());
				return CREATED.equals(result3.result().jsonValue()) || UPDATED.equals(result3.result().jsonValue());
			}
		}

		return false;
	}

	public ArtworkHistory makeArtwork(OpReturn opre, Feip feip) {

		Gson gson = new Gson();

		ArtworkOpData artworkRaw = new ArtworkOpData();
		try {
			artworkRaw = gson.fromJson(gson.toJsonTree(feip.getData()), ArtworkOpData.class);
			if(artworkRaw==null){
				log.info("Artwork raw is null");
				return null;
			}
		}catch(Exception e) {
			log.info("Failed to parse artwork");
			return null;
		}

		ArtworkHistory artworkHist = new ArtworkHistory();

		if(artworkRaw.getOp()==null){
			log.info("OP is null");
			return null;
		}

		artworkHist.setOp(artworkRaw.getOp());

		switch(artworkRaw.getOp()) {

			case PUBLISH:
				if(artworkRaw.getTitle()==null||"".equals(artworkRaw.getTitle())){
					log.info("Title is null or empty");
					return null;
				}
				if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired) {
					log.info("Height is greater than CddCheckHeight and Cdd is less than CddRequired");
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
					log.info("ArtworkId is null or empty");
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
					log.info("ArtworkId is null or empty");
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
					log.info("ArtworkId is null or empty");
					return null;
				}
				if(artworkRaw.getRate()==null) {
					log.info("Rate is null");
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
				log.info("Invalid operation");
				return null;
		}

		return artworkHist;
	}


	public SoundHistory makeSound(OpReturn opre, Feip feip) {

		Gson gson = new Gson();

		SoundOpData soundRaw = new SoundOpData();
		try {
			soundRaw = gson.fromJson(gson.toJsonTree(feip.getData()), SoundOpData.class);
			if(soundRaw==null){
				log.info("Sound raw is null");
				return null;
			}
		}catch(Exception e) {
			log.info("Failed to parse sound");
			return null;
		}

		SoundHistory soundHist = new SoundHistory();

		if(soundRaw.getOp()==null){
			log.info("OP is null");
			return null;
		}

		soundHist.setOp(soundRaw.getOp());

		switch(soundRaw.getOp()) {

			case PUBLISH:
				if(soundRaw.getTitle()==null||"".equals(soundRaw.getTitle())){
					log.info("Title is null or empty");
					return null;
				}
				if (opre.getHeight() > StartFEIP.CddCheckHeight) {
					Long cdd = opre.getCdd();
					if (cdd == null || cdd < StartFEIP.CddRequired) {
						log.info("Height is greater than CddCheckHeight and Cdd is null or less than CddRequired");
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
					log.info("SoundId is null or title is null or empty");
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
					log.info("SoundIds is null or empty");
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
					log.info("SoundId is null");
					return null;
				}
				if (soundRaw.getRate() == null) {
					log.info("Rate is null");
					return null;
				}
				Long rateCdd = opre.getCdd();
				if (rateCdd == null) {
					log.info("Cdd is null");
					return null;
				}
				if (rateCdd < StartFEIP.CddRequired) {
					log.info("Cdd is less than CddRequired");
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
				log.info("Invalid operation");
				return null;
		}
		return soundHist;
	}

	public boolean parseSound(ElasticsearchClient esClient, SoundHistory soundHist) throws Exception {

		if(soundHist==null){
			log.info("Sound hist is null");
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
						log.info("Failed to create sound");
						return false;
					}
					if(!CREATED.equals(result1.result().jsonValue()) && !UPDATED.equals(result1.result().jsonValue())){
						log.info("Failed to create sound");
						return false;
					}

					// Create news
					News.createNews(esClient, soundHist.getId(), soundHist.getSigner(), PUBLISH,
					Feip.FeipProtocol.SOUND.getName(), soundHist.getId(), soundHist.getTitle(), soundHist.getSummary(),
							soundHist.getHeight(), soundHist.getTime());

					return true;
				} else {
					log.info("Sound already exists");
					return false;
				}
			}
			case UPDATE -> {
				sound = EsUtils.getById(esClient, IndicesNames.SOUND, soundHist.getSoundId(), Sound.class);
				if (sound == null) {
					log.info("Sound not found");
					return false;
				}
				if (Boolean.TRUE.equals(sound.isDeleted())) {
					log.info("Sound is deleted");
					return false;
				}
				if (!sound.getPublisher().equals(soundHist.getSigner())) {
					log.info("Sound publisher is not the same as the signer");
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
				log.info("{}", result2.result());
				return CREATED.equals(result2.result().jsonValue()) || UPDATED.equals(result2.result().jsonValue());
			}

			case DELETE, RECOVER -> {
				List<String> idList = new ArrayList<>();
				if (soundHist.getSoundIds() != null && soundHist.getSoundIds().length > 0) {
					idList.addAll(Arrays.asList(soundHist.getSoundIds()));
				} else {
					log.info("SoundIds is null or empty");
					return false;
				}

				EsUtils.MgetResult<Sound> result = EsUtils.getMultiByIdList(esClient, IndicesNames.SOUND, idList, Sound.class);
				if (result == null) {
					log.info("Sound mget result is null");
					return false;
				}
				List<Sound> sounds = result.getResultList();
				if(sounds==null||sounds.isEmpty()){
					log.info("Sounds is null or empty");
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
						log.info("Failed to bulk update sound");
						return false;
					}
					return true;
				}
				log.info("No sound matched publisher/master filter");
				return false;
			}

			case RATE -> {
				sound = EsUtils.getById(esClient, IndicesNames.SOUND, soundHist.getSoundId(), Sound.class);
				if (sound == null) {
					log.info("Sound not found");
					return false;
				}
				if (sound.getPublisher().equals(soundHist.getSigner())) {
					log.info("Sound publisher is the same as the signer");
					return false;
				}

				if((soundHist.getCdd()==null || soundHist.getRate()==null)){
					log.info("Cdd or rate is null");
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
				log.info("{}", result3.result());
				return CREATED.equals(result3.result().jsonValue()) || UPDATED.equals(result3.result().jsonValue());
			}
		}
		return false;
	}

	public ImageHistory makeImage(OpReturn opre, Feip feip) {

		Gson gson = new Gson();

		ImageOpData imageRaw = new ImageOpData();
		try {
			imageRaw = gson.fromJson(gson.toJsonTree(feip.getData()), ImageOpData.class);
			if(imageRaw==null){
				log.info("Image raw is null");
				return null;
			}
		}catch(Exception e) {
			log.info("Failed to parse image");
			return null;
		}

		ImageHistory imageHist = new ImageHistory();

		if(imageRaw.getOp()==null){
			log.info("OP is null");
			return null;
		}

		imageHist.setOp(imageRaw.getOp());

		switch(imageRaw.getOp()) {

			case PUBLISH:
				if(imageRaw.getTitle()==null||"".equals(imageRaw.getTitle())){
					log.info("Title is null or empty");
					return null;
				}
				if (opre.getHeight() > StartFEIP.CddCheckHeight) {
					Long cdd = opre.getCdd();
					if (cdd == null || cdd < StartFEIP.CddRequired) {
						log.info("Height is greater than CddCheckHeight and Cdd is null or less than CddRequired");
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
					log.info("ImageId is null or title is null or empty");
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
					log.info("ImageIds is null or empty");
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
					log.info("ImageId is null");
					return null;
				}
				if (imageRaw.getRate() == null) {
					log.info("Rate is null");
					return null;
				}
				Long imageRateCdd = opre.getCdd();
				if (imageRateCdd == null) {
					log.info("Cdd is null");
					return null;
				}
				if (imageRateCdd < StartFEIP.CddRequired){
					log.info("Cdd is less than CddRequired");
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
				log.info("Invalid operation");
				return null;
		}
		return imageHist;
	}

	public boolean parseImage(ElasticsearchClient esClient, ImageHistory imageHist) throws Exception {

		if(imageHist==null){
			log.info("Image hist is null");
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
						log.info("Failed to create image");
						return false;
					}
					if(!CREATED.equals(result1.result().jsonValue()) && !UPDATED.equals(result1.result().jsonValue())){
						log.info("Failed to create image");
						return false;
					}

					// Create news record for image publication
					News.createNews(esClient, imageHist.getId(), imageHist.getSigner(), PUBLISH,
					Feip.FeipProtocol.IMAGE.getName(), imageHist.getId(), imageHist.getTitle(), imageHist.getSummary(),
							imageHist.getHeight(), imageHist.getTime());

					return true;
				} else {
					log.info("Image already exists");
					return false;
				}
			}
			case UPDATE -> {
				image = EsUtils.getById(esClient, IndicesNames.IMAGE, imageHist.getImageId(), Image.class);
				if (image == null) {
					log.info("Image not found");
					return false;
				}
				if (Boolean.TRUE.equals(image.isDeleted())) {
					log.info("Image is deleted");
					return false;
				}
				if (!image.getPublisher().equals(imageHist.getSigner())) {
					log.info("Image publisher is not the same as the signer");
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
				log.info("{}", result2.result());
				return CREATED.equals(result2.result().jsonValue()) || UPDATED.equals(result2.result().jsonValue());
			}

			case DELETE, RECOVER -> {
				List<String> idList = new ArrayList<>();
				if (imageHist.getImageIds() != null && imageHist.getImageIds().length > 0) {
					idList.addAll(Arrays.asList(imageHist.getImageIds()));
				} else {
					log.info("ImageIds is null or empty");
					return false;
				}

				EsUtils.MgetResult<Image> result = EsUtils.getMultiByIdList(esClient, IndicesNames.IMAGE, idList, Image.class);
				if (result == null) {
					log.info("Image mget result is null");
					return false;
				}
				List<Image> images = result.getResultList();
				if (images == null || images.isEmpty()) {
					log.info("Images is null or empty");
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
						log.info("Failed to bulk update image");
						return false;
					}
					return true;
				}
				log.info("No image matched publisher/master filter; delete/recover skipped");
				return false;
			}

			case RATE -> {
				image = EsUtils.getById(esClient, IndicesNames.IMAGE, imageHist.getImageId(), Image.class);
				if (image == null) {
					log.info("Image not found");
					return false;
				}
				if (image.getPublisher().equals(imageHist.getSigner())) {
					log.info("Image publisher is the same as the signer");
					return false;
				}

				if((imageHist.getCdd()==null || imageHist.getRate()==null)){
					log.info("Cdd or rate is null");
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
				log.info("{}", result3.result());
				return CREATED.equals(result3.result().jsonValue()) || UPDATED.equals(result3.result().jsonValue());
			}
		}
		return false;
	}

	public VideoHistory makeVideo(OpReturn opre, Feip feip) {

		Gson gson = new Gson();

		VideoOpData videoRaw = new VideoOpData();
		try {
			videoRaw = gson.fromJson(gson.toJsonTree(feip.getData()), VideoOpData.class);
			if(videoRaw==null){
				log.info("Video raw is null");
				return null;
			}
		}catch(Exception e) {
			log.info("Failed to parse video");
			return null;
		}
		VideoHistory videoHist = new VideoHistory();

		if(videoRaw.getOp()==null){
			log.info("OP is null");
			return null;
		}

		videoHist.setOp(videoRaw.getOp());

		switch(videoRaw.getOp()) {

			case PUBLISH:
				if(videoRaw.getTitle()==null||"".equals(videoRaw.getTitle())){
					log.info("Title is null or empty");
					return null;
				}
				if (opre.getHeight() > StartFEIP.CddCheckHeight) {
					Long cdd = opre.getCdd();
					if (cdd == null || cdd < StartFEIP.CddRequired) {
						log.info("Height is greater than CddCheckHeight and Cdd is null or less than CddRequired");
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
					log.info("VideoId is null or title is null or empty");
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
					log.info("VideoIds is null or empty");
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
					log.info("VideoId is null");
					return null;
				}
				if (videoRaw.getRate() == null) {
					log.info("Rate is null");
					return null;
				}
				Long videoRateCdd = opre.getCdd();
				if (videoRateCdd == null) {
					log.info("Cdd is null");
					return null;
				}
				if (videoRateCdd < StartFEIP.CddRequired){
					log.info("Cdd is less than CddRequired");
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
				log.info("Invalid operation");
				return null;
		}
		return videoHist;
	}

	public boolean parseVideo(ElasticsearchClient esClient, VideoHistory videoHist) throws Exception {

		if(videoHist==null){
			log.info("Video hist is null");
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
						log.info("Failed to create video");
						return false;
					}
					if(!CREATED.equals(result1.result().jsonValue()) && !UPDATED.equals(result1.result().jsonValue())){
						log.info("Failed to create video");
						return false;
					}

					// Create news record for video publication
					News.createNews(esClient, videoHist.getId(), videoHist.getSigner(), PUBLISH,
					Feip.FeipProtocol.VIDEO.getName(), videoHist.getId(), videoHist.getTitle(), videoHist.getSummary(),
							videoHist.getHeight(), videoHist.getTime());

					return true;
				} else {
					log.info("Video already exists");
					return false;
				}
			}
			case UPDATE -> {
				video = EsUtils.getById(esClient, IndicesNames.VIDEO, videoHist.getVideoId(), Video.class);
				if (video == null) {
					log.info("Video not found");
					return false;
				}
				if (Boolean.TRUE.equals(video.isDeleted())) {
					log.info("Video is deleted");
					return false;
				}
				if (!video.getPublisher().equals(videoHist.getSigner())) {
					log.info("Video publisher is not the same as the signer");
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
				log.info("{}", result2.result());
				return CREATED.equals(result2.result().jsonValue()) || UPDATED.equals(result2.result().jsonValue());
			}

			case DELETE, RECOVER -> {
				List<String> idList = new ArrayList<>();
				if (videoHist.getVideoIds() != null && videoHist.getVideoIds().length > 0) {
					idList.addAll(Arrays.asList(videoHist.getVideoIds()));
				} else {
					log.info("VideoIds is null or empty");
					return false;
				}

				EsUtils.MgetResult<Video> result = EsUtils.getMultiByIdList(esClient, IndicesNames.VIDEO, idList, Video.class);
				if (result == null) {
					log.info("Video mget result is null");
					return false;
				}
				List<Video> videos = result.getResultList();
				if (videos == null || videos.isEmpty()) {
					log.info("Videos is null or empty");
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
						log.info("Failed to bulk update video");
						return false;
					}
					return true;
				}
				log.info("No video matched publisher/master filter; delete/recover skipped");
				return false;
			}

			case RATE -> {
				video = EsUtils.getById(esClient, IndicesNames.VIDEO, videoHist.getVideoId(), Video.class);
				if (video == null) {
					log.info("Video not found");
					return false;
				}
				if (video.getPublisher().equals(videoHist.getSigner())) {
					log.info("Video publisher is the same as the signer");
					return false;
				}

				if((videoHist.getCdd()==null || videoHist.getRate()==null)){
					log.info("Cdd or rate is null");
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
				log.info("{}", result3.result());
				return CREATED.equals(result3.result().jsonValue()) || UPDATED.equals(result3.result().jsonValue());
			}
		}

		return false;
	}

}
