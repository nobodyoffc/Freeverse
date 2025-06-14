package publish;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import com.google.gson.Gson;
import constants.IndicesNames;
import data.fchData.Cid;
import data.fchData.OpReturn;
import data.feipData.*;
import startFEIP.StartFEIP;
import utils.EsUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static constants.OpNames.*;

public class PublishParser {

	public boolean parseStatement(ElasticsearchClient esClient, OpReturn opre, Feip feip) throws ElasticsearchException, IOException {

		boolean isValid = false;

		Gson gson = new Gson();

		StatementOpData statementRaw = new StatementOpData();

		try {
			statementRaw = gson.fromJson(gson.toJson(feip.getData()), StatementOpData.class);
			if(statementRaw==null)return isValid;
		}catch(com.google.gson.JsonSyntaxException e) {
			return isValid;
		}

		Statement statement = new Statement();

		statement.setId(opre.getId());

		if(statementRaw.getConfirm()==null)return isValid;

		if(!statementRaw.getConfirm().equals("This is a formal and irrevocable statement."))return isValid;

		if(statementRaw.getTitle()==null && statementRaw.getContent()==null)return isValid;

		if(statementRaw.getTitle()!=null) {
			statement.setTitle(statementRaw.getTitle());
		}

		if(statementRaw.getContent()!=null) {
			statement.setContent(statementRaw.getContent());
		}

		statement.setPublisher(opre.getSigner());
		statement.setBirthTime(opre.getTime());
		statement.setBirthHeight(opre.getHeight());

		esClient.index(i->i.index(IndicesNames.STATEMENT).id(statement.getId()).document(statement));
		isValid = true;

		return isValid;
	}


	public EssayHistory makeEssay(OpReturn opre, Feip feip) {

		Gson gson = new Gson();

		EssayOpData essayRaw = new EssayOpData();
		try {
			essayRaw = gson.fromJson(gson.toJson(feip.getData()), EssayOpData.class);
			if(essayRaw==null)return null;
		}catch(Exception e) {
			e.printStackTrace();
			try {
				TimeUnit.SECONDS.sleep(5);
			} catch (InterruptedException ex) {
				throw new RuntimeException(ex);
			}
			return null;
		}

		EssayHistory essayHist = new EssayHistory();

		if(essayRaw.getOp()==null)return null;

		essayHist.setOp(essayRaw.getOp());

		switch(essayRaw.getOp()) {

			case PUBLISH:
				if(essayRaw.getTitle()==null||"".equals(essayRaw.getTitle())) return null;
            	if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired * 100) return null;
				essayHist.setId(opre.getId());

				essayHist.setEssayId(opre.getId());
				essayHist.setHeight(opre.getHeight());
				essayHist.setIndex(opre.getTxIndex());
				essayHist.setTime(opre.getTime());
				essayHist.setSigner(opre.getSigner());

				if(essayRaw.getDid()!=null)essayHist.setDid(essayRaw.getDid());
				if(essayRaw.getTitle()!=null)essayHist.setTitle(essayRaw.getTitle());
				if(essayRaw.getLang()!=null)essayHist.setLang(essayRaw.getLang());
				if(essayRaw.getAuthors()!=null)essayHist.setAuthors(essayRaw.getAuthors());

				break;

			case UPDATE:
				if(essayRaw.getEssayId()==null|| essayRaw.getTitle()==null||"".equals(essayRaw.getTitle()))
					return null;
				essayHist.setId(opre.getId());
				essayHist.setHeight(opre.getHeight());
				essayHist.setIndex(opre.getTxIndex());
				essayHist.setTime(opre.getTime());
				essayHist.setSigner(opre.getSigner());

				essayHist.setEssayId(essayRaw.getEssayId());

				if(essayRaw.getDid()!=null)essayHist.setDid(essayRaw.getDid());
				if(essayRaw.getTitle()!=null)essayHist.setTitle(essayRaw.getTitle());
				if(essayRaw.getLang()!=null)essayHist.setLang(essayRaw.getLang());
				if(essayRaw.getAuthors()!=null)essayHist.setAuthors(essayRaw.getAuthors());

				break;
			case RECOVER:
			case DELETE:
				if (essayRaw.getEssayIds() == null || essayRaw.getEssayIds().length == 0) {
					return null;
				}
				essayHist.setEssayIds(essayRaw.getEssayIds());
				essayHist.setId(opre.getId());
				essayHist.setHeight(opre.getHeight());
				essayHist.setIndex(opre.getTxIndex());
				essayHist.setTime(opre.getTime());
				essayHist.setSigner(opre.getSigner());

				break;

			case RATE:
				if(essayRaw.getEssayId()==null)return null;
				if (opre.getCdd() < StartFEIP.CddRequired) return null;
				essayHist.setEssayId(essayRaw.getEssayId());
				essayHist.setRate(essayRaw.getRate());
				essayHist.setCdd(opre.getCdd());

				essayHist.setId(opre.getId());
				essayHist.setHeight(opre.getHeight());
				essayHist.setIndex(opre.getTxIndex());
				essayHist.setTime(opre.getTime());
				essayHist.setSigner(opre.getSigner());
				break;
			default:
				return null;
		}
		return essayHist;
	}



	public boolean parseEssay(ElasticsearchClient esClient, EssayHistory essayHist) throws Exception {

		boolean isValid = false;
		if(essayHist==null)return false;
		Essay essay;
		switch (essayHist.getOp()) {
			case PUBLISH -> {
				essay = EsUtils.getById(esClient, IndicesNames.ESSAY, essayHist.getEssayId(), Essay.class);
				if (essay == null) {
					essay = new Essay();

					essay.setId(essayHist.getEssayId());
					essay.setVer("1");
					essay.setDid(essayHist.getDid());

					essay.setLang(essayHist.getLang());
					essay.setTitle(essayHist.getTitle());
					essay.setAuthors(essayHist.getAuthors());

					essay.setPublisher(essayHist.getSigner());

					essay.setBirthTime(essayHist.getTime());
					essay.setBirthHeight(essayHist.getHeight());
					essay.setLastTxId(essayHist.getId());
					essay.setLastTime(essayHist.getTime());
					essay.setLastHeight(essayHist.getHeight());

					essay.setDeleted(false);

					Essay essay1 = essay;

					esClient.index(i -> i.index(IndicesNames.ESSAY).id(essayHist.getEssayId()).document(essay1));
					isValid = true;
				} else {
					isValid = false;
				}
			}
			case UPDATE -> {
				essay = EsUtils.getById(esClient, IndicesNames.ESSAY, essayHist.getEssayId(), Essay.class);
				if (essay == null) {
					isValid = false;
					break;
				}
				if (Boolean.TRUE.equals(essay.isDeleted())) {
					isValid = false;
					break;
				}
				if (!essay.getPublisher().equals(essayHist.getSigner())) {
					isValid = false;
					break;
				}
				essay.setVer(String.valueOf(Integer.parseInt(essay.getVer())+1));
				essay.setDid(essayHist.getDid());
				essay.setTitle(essayHist.getTitle());
				essay.setLang(essayHist.getLang());
				essay.setAuthors(essayHist.getAuthors());

				essay.setLastTxId(essayHist.getId());
				essay.setLastTime(essayHist.getTime());
				essay.setLastHeight(essayHist.getHeight());
				Essay essay2 = essay;
				esClient.index(i -> i.index(IndicesNames.ESSAY).id(essayHist.getEssayId()).document(essay2));
				isValid = true;
			}

			case DELETE, RECOVER -> {
				List<String> idList = new ArrayList<>();
				if (essayHist.getEssayIds() != null && essayHist.getEssayIds().length > 0) {
					idList.addAll(Arrays.asList(essayHist.getEssayIds()));
				} else {
					isValid = false;
					break;
				}

				EsUtils.MgetResult<Essay> result = EsUtils.getMultiByIdList(esClient, IndicesNames.ESSAY, idList, Essay.class);
				List<Essay> essays = result.getResultList();

				List<Essay> updatedEssays = new ArrayList<>();
				for (Essay essayItem : essays) {

					if (!essayItem.getPublisher().equals(essayHist.getSigner())) {
						Cid resultCid = EsUtils.getById(esClient, IndicesNames.CID, essayHist.getSigner(), Cid.class);
						if (resultCid ==null || resultCid.getMaster() == null || !resultCid.getMaster().equals(essayHist.getSigner())) {
							continue;
						}
					}

					switch (essayHist.getOp()) {
						case DELETE:
							essayItem.setDeleted(true);
							break;
						case RECOVER:
							essayItem.setDeleted(false);
							break;
					}

					essayItem.setLastTxId(essayHist.getId());
					essayItem.setLastTime(essayHist.getTime());
					essayItem.setLastHeight(essayHist.getHeight());

					updatedEssays.add(essayItem);
				}

				if (!updatedEssays.isEmpty()) {
					BulkRequest.Builder br = new BulkRequest.Builder();
					for (Essay updatedEssay : updatedEssays) {
						br.operations(op -> op
								.index(idx -> idx
										.index(IndicesNames.ESSAY)
										.id(updatedEssay.getId())
										.document(updatedEssay)
								)
						);
					}
					esClient.bulk(br.build());
					isValid = true;
				}
			}

			case RATE -> {
				essay = EsUtils.getById(esClient, IndicesNames.ESSAY, essayHist.getEssayId(), Essay.class);
				if (essay == null) {
					isValid = false;
					break;
				}
				if (essay.getPublisher().equals(essayHist.getSigner())) {
					isValid = false;
					break;
				}

				if((essayHist.getCdd()==null || essayHist.getRate()==null)){
					isValid=false;
					break;
				}

				if(essay.gettCdd()==null||essay.gettRate()==null){
					essay.settRate(Float.valueOf(essayHist.getRate()));
					essay.settCdd(essayHist.getCdd());
				}else{
					essay.settRate(
							(essay.gettRate()*essay.gettCdd()+essayHist.getRate()*essayHist.getCdd())
									/(essay.gettCdd()+essayHist.getCdd())
					);
					essay.settCdd(essay.gettCdd() + essayHist.getCdd());
				}

				essay.setLastTxId(essayHist.getId());
				essay.setLastTime(essayHist.getTime());
				essay.setLastHeight(essayHist.getHeight());
				Essay essay3 = essay;
				esClient.index(i -> i.index(IndicesNames.ESSAY).id(essayHist.getEssayId()).document(essay3));
				isValid = true;
			}
		}

		return isValid;
	}

	public ReportHistory makeReport(OpReturn opre, Feip feip) {

		Gson gson = new Gson();

		ReportOpData reportRaw = new ReportOpData();
		try {
			reportRaw = gson.fromJson(gson.toJson(feip.getData()), ReportOpData.class);
			if(reportRaw==null)return null;
		}catch(Exception e) {
			e.printStackTrace();
			try {
				TimeUnit.SECONDS.sleep(5);
			} catch (InterruptedException ex) {
				throw new RuntimeException(ex);
			}
			return null;
		}

		ReportHistory reportHist = new ReportHistory();

		if(reportRaw.getOp()==null)return null;

		reportHist.setOp(reportRaw.getOp());

		switch(reportRaw.getOp()) {

			case PUBLISH:
				if(reportRaw.getTitle()==null||"".equals(reportRaw.getTitle())) return null;
            	if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired * 100) return null;
				reportHist.setId(opre.getId());

				reportHist.setReportId(opre.getId());
				reportHist.setHeight(opre.getHeight());
				reportHist.setIndex(opre.getTxIndex());
				reportHist.setTime(opre.getTime());
				reportHist.setSigner(opre.getSigner());

				if(reportRaw.getDid()!=null)reportHist.setDid(reportRaw.getDid());
				if(reportRaw.getTitle()!=null)reportHist.setTitle(reportRaw.getTitle());
				if(reportRaw.getLang()!=null)reportHist.setLang(reportRaw.getLang());
				if(reportRaw.getAuthors()!=null)reportHist.setAuthors(reportRaw.getAuthors());
				if(reportRaw.getSummary()!=null)reportHist.setSummary(reportRaw.getSummary());

				break;

			case UPDATE:
				if(reportRaw.getReportId()==null|| reportRaw.getTitle()==null||"".equals(reportRaw.getTitle()))
					return null;
				reportHist.setId(opre.getId());
				reportHist.setHeight(opre.getHeight());
				reportHist.setIndex(opre.getTxIndex());
				reportHist.setTime(opre.getTime());
				reportHist.setSigner(opre.getSigner());

				reportHist.setReportId(reportRaw.getReportId());

				if(reportRaw.getDid()!=null)reportHist.setDid(reportRaw.getDid());
				if(reportRaw.getTitle()!=null)reportHist.setTitle(reportRaw.getTitle());
				if(reportRaw.getLang()!=null)reportHist.setLang(reportRaw.getLang());
				if(reportRaw.getAuthors()!=null)reportHist.setAuthors(reportRaw.getAuthors());
				if(reportRaw.getSummary()!=null)reportHist.setSummary(reportRaw.getSummary());

				break;
			case RECOVER:
			case DELETE:
				if (reportRaw.getReportIds() == null || reportRaw.getReportIds().length == 0) {
					return null;
				}
				reportHist.setReportIds(reportRaw.getReportIds());
				reportHist.setId(opre.getId());
				reportHist.setHeight(opre.getHeight());
				reportHist.setIndex(opre.getTxIndex());
				reportHist.setTime(opre.getTime());
				reportHist.setSigner(opre.getSigner());

				break;

			case RATE:
				if(reportRaw.getReportId()==null)return null;
				if (opre.getCdd() < StartFEIP.CddRequired) return null;
				reportHist.setReportId(reportRaw.getReportId());
				reportHist.setRate(reportRaw.getRate());
				reportHist.setCdd(opre.getCdd());

				reportHist.setId(opre.getId());
				reportHist.setHeight(opre.getHeight());
				reportHist.setIndex(opre.getTxIndex());
				reportHist.setTime(opre.getTime());
				reportHist.setSigner(opre.getSigner());
				break;
			default:
				return null;
		}
		return reportHist;
	}

	public boolean parseReport(ElasticsearchClient esClient, ReportHistory reportHist) throws Exception {

		boolean isValid = false;
		if(reportHist==null)return false;
		Report report;
		switch (reportHist.getOp()) {
			case PUBLISH -> {
				report = EsUtils.getById(esClient, IndicesNames.REPORT, reportHist.getReportId(), Report.class);
				if (report == null) {
					report = new Report();

					report.setId(reportHist.getReportId());
					report.setVer("1");
					report.setDid(reportHist.getDid());

					report.setLang(reportHist.getLang());
					report.setTitle(reportHist.getTitle());
					report.setAuthors(reportHist.getAuthors());
					report.setSummary(reportHist.getSummary());

					report.setPublisher(reportHist.getSigner());

					report.setBirthTime(reportHist.getTime());
					report.setBirthHeight(reportHist.getHeight());
					report.setLastTxId(reportHist.getId());
					report.setLastTime(reportHist.getTime());
					report.setLastHeight(reportHist.getHeight());

					report.setDeleted(false);

					Report report1 = report;

					esClient.index(i -> i.index(IndicesNames.REPORT).id(reportHist.getReportId()).document(report1));
					isValid = true;
				} else {
					isValid = false;
				}
			}
			case UPDATE -> {
				report = EsUtils.getById(esClient, IndicesNames.REPORT, reportHist.getReportId(), Report.class);
				if (report == null) {
					isValid = false;
					break;
				}
				if (Boolean.TRUE.equals(report.isDeleted())) {
					isValid = false;
					break;
				}
				if (!report.getPublisher().equals(reportHist.getSigner())) {
					isValid = false;
					break;
				}
				report.setVer(String.valueOf(Integer.parseInt(report.getVer())+1));
				report.setDid(reportHist.getDid());
				report.setTitle(reportHist.getTitle());
				report.setLang(reportHist.getLang());
				report.setAuthors(reportHist.getAuthors());
				report.setSummary(reportHist.getSummary());

				report.setLastTxId(reportHist.getId());
				report.setLastTime(reportHist.getTime());
				report.setLastHeight(reportHist.getHeight());
				Report report2 = report;
				esClient.index(i -> i.index(IndicesNames.REPORT).id(reportHist.getReportId()).document(report2));
				isValid = true;
			}

			case DELETE, RECOVER -> {
				List<String> idList = new ArrayList<>();
				if (reportHist.getReportIds() != null && reportHist.getReportIds().length > 0) {
					idList.addAll(Arrays.asList(reportHist.getReportIds()));
				} else {
					isValid = false;
					break;
				}

				EsUtils.MgetResult<Report> result = EsUtils.getMultiByIdList(esClient, IndicesNames.REPORT, idList, Report.class);
				List<Report> reports = result.getResultList();

				List<Report> updatedReports = new ArrayList<>();
				for (Report reportItem : reports) {

					if (!reportItem.getPublisher().equals(reportHist.getSigner())) {
						Cid resultCid = EsUtils.getById(esClient, IndicesNames.CID, reportHist.getSigner(), Cid.class);
						if (resultCid ==null || resultCid.getMaster() == null || !resultCid.getMaster().equals(reportHist.getSigner())) {
							continue;
						}
					}

					switch (reportHist.getOp()) {
						case DELETE:
							reportItem.setDeleted(true);
							break;
						case RECOVER:
							reportItem.setDeleted(false);
							break;
					}

					reportItem.setLastTxId(reportHist.getId());
					reportItem.setLastTime(reportHist.getTime());
					reportItem.setLastHeight(reportHist.getHeight());

					updatedReports.add(reportItem);
				}

				if (!updatedReports.isEmpty()) {
					BulkRequest.Builder br = new BulkRequest.Builder();
					for (Report updatedReport : updatedReports) {
						br.operations(op -> op
								.index(idx -> idx
										.index(IndicesNames.REPORT)
										.id(updatedReport.getId())
										.document(updatedReport)
								)
						);
					}
					esClient.bulk(br.build());
					isValid = true;
				}
			}

			case RATE -> {
				report = EsUtils.getById(esClient, IndicesNames.REPORT, reportHist.getReportId(), Report.class);
				if (report == null) {
					isValid = false;
					break;
				}
				if (report.getPublisher().equals(reportHist.getSigner())) {
					isValid = false;
					break;
				}

				if((reportHist.getCdd()==null || reportHist.getRate()==null)){
					isValid=false;
					break;
				}

				if(report.gettCdd()==null||report.gettRate()==null){
					report.settRate(Float.valueOf(reportHist.getRate()));
					report.settCdd(reportHist.getCdd());
				}else{
					report.settRate(
							(report.gettRate()*report.gettCdd()+reportHist.getRate()*reportHist.getCdd())
									/(report.gettCdd()+reportHist.getCdd())
					);
					report.settCdd(report.gettCdd() + reportHist.getCdd());
				}

				report.setLastTxId(reportHist.getId());
				report.setLastTime(reportHist.getTime());
				report.setLastHeight(reportHist.getHeight());
				Report report3 = report;
				esClient.index(i -> i.index(IndicesNames.REPORT).id(reportHist.getReportId()).document(report3));
				isValid = true;
			}
		}

		return isValid;
	}

	public BookHistory makeBook(OpReturn opre, Feip feip) {

		Gson gson = new Gson();

		BookOpData bookRaw = new BookOpData();
		try {
			bookRaw = gson.fromJson(gson.toJson(feip.getData()), BookOpData.class);
			if(bookRaw==null)return null;
		}catch(Exception e) {
			e.printStackTrace();
			try {
				TimeUnit.SECONDS.sleep(5);
			} catch (InterruptedException ex) {
				throw new RuntimeException(ex);
			}
			return null;
		}

		BookHistory bookHist = new BookHistory();

		if(bookRaw.getOp()==null)return null;

		bookHist.setOp(bookRaw.getOp());

		switch(bookRaw.getOp()) {

			case PUBLISH:
				if(bookRaw.getTitle()==null||"".equals(bookRaw.getTitle())) return null;
            	if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired * 100) return null;
				bookHist.setId(opre.getId());

				bookHist.setBookId(opre.getId());
				bookHist.setHeight(opre.getHeight());
				bookHist.setIndex(opre.getTxIndex());
				bookHist.setTime(opre.getTime());
				bookHist.setSigner(opre.getSigner());

				if(bookRaw.getDid()!=null)bookHist.setDid(bookRaw.getDid());
				if(bookRaw.getTitle()!=null)bookHist.setTitle(bookRaw.getTitle());
				if(bookRaw.getLang()!=null)bookHist.setLang(bookRaw.getLang());
				if(bookRaw.getAuthors()!=null)bookHist.setAuthors(bookRaw.getAuthors());
				if(bookRaw.getSummary()!=null)bookHist.setSummary(bookRaw.getSummary());

				break;

			case UPDATE:
				if(bookRaw.getBookId()==null|| bookRaw.getTitle()==null||"".equals(bookRaw.getTitle()))
					return null;
				bookHist.setId(opre.getId());
				bookHist.setHeight(opre.getHeight());
				bookHist.setIndex(opre.getTxIndex());
				bookHist.setTime(opre.getTime());
				bookHist.setSigner(opre.getSigner());

				bookHist.setBookId(bookRaw.getBookId());

				if(bookRaw.getDid()!=null)bookHist.setDid(bookRaw.getDid());
				if(bookRaw.getTitle()!=null)bookHist.setTitle(bookRaw.getTitle());
				if(bookRaw.getLang()!=null)bookHist.setLang(bookRaw.getLang());
				if(bookRaw.getAuthors()!=null)bookHist.setAuthors(bookRaw.getAuthors());
				if(bookRaw.getSummary()!=null)bookHist.setSummary(bookRaw.getSummary());

				break;
			case RECOVER:
			case DELETE:
				if (bookRaw.getBookIds() == null || bookRaw.getBookIds().length == 0) {
					return null;
				}
				bookHist.setBookIds(bookRaw.getBookIds());
				bookHist.setId(opre.getId());
				bookHist.setHeight(opre.getHeight());
				bookHist.setIndex(opre.getTxIndex());
				bookHist.setTime(opre.getTime());
				bookHist.setSigner(opre.getSigner());

				break;

			case RATE:
				if(bookRaw.getBookId()==null)return null;
				if (opre.getCdd() < StartFEIP.CddRequired) return null;
				bookHist.setBookId(bookRaw.getBookId());
				bookHist.setRate(bookRaw.getRate());
				bookHist.setCdd(opre.getCdd());

				bookHist.setId(opre.getId());
				bookHist.setHeight(opre.getHeight());
				bookHist.setIndex(opre.getTxIndex());
				bookHist.setTime(opre.getTime());
				bookHist.setSigner(opre.getSigner());
				break;
			default:
				return null;
		}
		return bookHist;
	}

	public boolean parseBook(ElasticsearchClient esClient, BookHistory bookHist) throws Exception {

		boolean isValid = false;
		if(bookHist==null)return false;
		Book book;
		switch (bookHist.getOp()) {
			case PUBLISH -> {
				book = EsUtils.getById(esClient, IndicesNames.BOOK, bookHist.getBookId(), Book.class);
				if (book == null) {
					book = new Book();

					book.setId(bookHist.getBookId());
					book.setVer("1");
					book.setDid(bookHist.getDid());

					book.setLang(bookHist.getLang());
					book.setTitle(bookHist.getTitle());
					book.setAuthors(bookHist.getAuthors());
					book.setSummary(bookHist.getSummary());

					book.setPublisher(bookHist.getSigner());

					book.setBirthTime(bookHist.getTime());
					book.setBirthHeight(bookHist.getHeight());
					book.setLastTxId(bookHist.getId());
					book.setLastTime(bookHist.getTime());
					book.setLastHeight(bookHist.getHeight());

					book.setDeleted(false);

					Book book1 = book;

					esClient.index(i -> i.index(IndicesNames.BOOK).id(bookHist.getBookId()).document(book1));
					isValid = true;
				} else {
					isValid = false;
				}
			}
			case UPDATE -> {
				book = EsUtils.getById(esClient, IndicesNames.BOOK, bookHist.getBookId(), Book.class);
				if (book == null) {
					isValid = false;
					break;
				}
				if (Boolean.TRUE.equals(book.isDeleted())) {
					isValid = false;
					break;
				}
				if (!book.getPublisher().equals(bookHist.getSigner())) {
					isValid = false;
					break;
				}
				book.setVer(String.valueOf(Integer.parseInt(book.getVer())+1));
				book.setDid(bookHist.getDid());
				book.setTitle(bookHist.getTitle());
				book.setLang(bookHist.getLang());
				book.setAuthors(bookHist.getAuthors());
				book.setSummary(bookHist.getSummary());

				book.setLastTxId(bookHist.getId());
				book.setLastTime(bookHist.getTime());
				book.setLastHeight(bookHist.getHeight());
				Book book2 = book;
				esClient.index(i -> i.index(IndicesNames.BOOK).id(bookHist.getBookId()).document(book2));
				isValid = true;
			}

			case DELETE, RECOVER -> {
				List<String> idList = new ArrayList<>();
				if (bookHist.getBookIds() != null && bookHist.getBookIds().length > 0) {
					idList.addAll(Arrays.asList(bookHist.getBookIds()));
				} else {
					isValid = false;
					break;
				}

				EsUtils.MgetResult<Book> result = EsUtils.getMultiByIdList(esClient, IndicesNames.BOOK, idList, Book.class);
				List<Book> books = result.getResultList();

				List<Book> updatedBooks = new ArrayList<>();
				for (Book bookItem : books) {

					if (!bookItem.getPublisher().equals(bookHist.getSigner())) {
						Cid resultCid = EsUtils.getById(esClient, IndicesNames.CID, bookHist.getSigner(), Cid.class);
						if (resultCid ==null || resultCid.getMaster() == null || !resultCid.getMaster().equals(bookHist.getSigner())) {
							continue;
						}
					}

					switch (bookHist.getOp()) {
						case DELETE:
							bookItem.setDeleted(true);
							break;
						case RECOVER:
							bookItem.setDeleted(false);
							break;
					}

					bookItem.setLastTxId(bookHist.getId());
					bookItem.setLastTime(bookHist.getTime());
					bookItem.setLastHeight(bookHist.getHeight());

					updatedBooks.add(bookItem);
				}

				if (!updatedBooks.isEmpty()) {
					BulkRequest.Builder br = new BulkRequest.Builder();
					for (Book updatedBook : updatedBooks) {
						br.operations(op -> op
								.index(idx -> idx
										.index(IndicesNames.BOOK)
										.id(updatedBook.getId())
										.document(updatedBook)
								)
						);
					}
					esClient.bulk(br.build());
					isValid = true;
				}
			}

			case RATE -> {
				book = EsUtils.getById(esClient, IndicesNames.BOOK, bookHist.getBookId(), Book.class);
				if (book == null) {
					isValid = false;
					break;
				}
				if (book.getPublisher().equals(bookHist.getSigner())) {
					isValid = false;
					break;
				}

				if((bookHist.getCdd()==null || bookHist.getRate()==null)){
					isValid=false;
					break;
				}

				if(book.gettCdd()==null||book.gettRate()==null){
					book.settRate(Float.valueOf(bookHist.getRate()));
					book.settCdd(bookHist.getCdd());
				}else{
					book.settRate(
							(book.gettRate()*book.gettCdd()+bookHist.getRate()*bookHist.getCdd())
									/(book.gettCdd()+bookHist.getCdd())
					);
					book.settCdd(book.gettCdd() + bookHist.getCdd());
				}

				book.setLastTxId(bookHist.getId());
				book.setLastTime(bookHist.getTime());
				book.setLastHeight(bookHist.getHeight());
				Book book3 = book;
				esClient.index(i -> i.index(IndicesNames.BOOK).id(bookHist.getBookId()).document(book3));
				isValid = true;
			}
		}

		return isValid;
	}

	public PaperHistory makePaper(OpReturn opre, Feip feip) {

		Gson gson = new Gson();

		PaperOpData paperRaw = new PaperOpData();
		try {
			paperRaw = gson.fromJson(gson.toJson(feip.getData()), PaperOpData.class);
			if(paperRaw==null)return null;
		}catch(Exception e) {
			e.printStackTrace();
			try {
				TimeUnit.SECONDS.sleep(5);
			} catch (InterruptedException ex) {
				throw new RuntimeException(ex);
			}
			return null;
		}

		PaperHistory paperHist = new PaperHistory();

		if(paperRaw.getOp()==null)return null;

		paperHist.setOp(paperRaw.getOp());

		switch(paperRaw.getOp()) {

			case PUBLISH:
				if(paperRaw.getTitle()==null||"".equals(paperRaw.getTitle())) return null;
            	if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired * 100) return null;
				paperHist.setId(opre.getId());

				paperHist.setPaperId(opre.getId());
				paperHist.setHeight(opre.getHeight());
				paperHist.setIndex(opre.getTxIndex());
				paperHist.setTime(opre.getTime());
				paperHist.setSigner(opre.getSigner());

				if(paperRaw.getDid()!=null)paperHist.setDid(paperRaw.getDid());
				if(paperRaw.getTitle()!=null)paperHist.setTitle(paperRaw.getTitle());
				if(paperRaw.getLang()!=null)paperHist.setLang(paperRaw.getLang());
				if(paperRaw.getAuthors()!=null)paperHist.setAuthors(paperRaw.getAuthors());
				if(paperRaw.getSummary()!=null)paperHist.setSummary(paperRaw.getSummary());
				if(paperRaw.getKeywords()!=null)paperHist.setKeywords(paperRaw.getKeywords());

				break;

			case UPDATE:
				if(paperRaw.getPaperId()==null|| paperRaw.getTitle()==null||"".equals(paperRaw.getTitle()))
					return null;
				paperHist.setId(opre.getId());
				paperHist.setHeight(opre.getHeight());
				paperHist.setIndex(opre.getTxIndex());
				paperHist.setTime(opre.getTime());
				paperHist.setSigner(opre.getSigner());

				paperHist.setPaperId(paperRaw.getPaperId());

				if(paperRaw.getDid()!=null)paperHist.setDid(paperRaw.getDid());
				if(paperRaw.getTitle()!=null)paperHist.setTitle(paperRaw.getTitle());
				if(paperRaw.getLang()!=null)paperHist.setLang(paperRaw.getLang());
				if(paperRaw.getAuthors()!=null)paperHist.setAuthors(paperRaw.getAuthors());
				if(paperRaw.getSummary()!=null)paperHist.setSummary(paperRaw.getSummary());
				if(paperRaw.getKeywords()!=null)paperHist.setKeywords(paperRaw.getKeywords());

				break;
			case RECOVER:
			case DELETE:
				if (paperRaw.getPaperIds() == null || paperRaw.getPaperIds().length == 0) {
					return null;
				}
				paperHist.setPaperIds(paperRaw.getPaperIds());
				paperHist.setId(opre.getId());
				paperHist.setHeight(opre.getHeight());
				paperHist.setIndex(opre.getTxIndex());
				paperHist.setTime(opre.getTime());
				paperHist.setSigner(opre.getSigner());

				break;

			case RATE:
				if(paperRaw.getPaperId()==null)return null;
				if (opre.getCdd() < StartFEIP.CddRequired) return null;
				paperHist.setPaperId(paperRaw.getPaperId());
				paperHist.setRate(paperRaw.getRate());
				paperHist.setCdd(opre.getCdd());

				paperHist.setId(opre.getId());
				paperHist.setHeight(opre.getHeight());
				paperHist.setIndex(opre.getTxIndex());
				paperHist.setTime(opre.getTime());
				paperHist.setSigner(opre.getSigner());
				break;
			default:
				return null;
		}
		return paperHist;
	}

	public boolean parsePaper(ElasticsearchClient esClient, PaperHistory paperHist) throws Exception {

		boolean isValid = false;
		if(paperHist==null)return false;
		Paper paper;
		switch (paperHist.getOp()) {
			case PUBLISH -> {
				paper = EsUtils.getById(esClient, IndicesNames.PAPER, paperHist.getPaperId(), Paper.class);
				if (paper == null) {
					paper = new Paper();

					paper.setId(paperHist.getPaperId());
					paper.setVer("1");
					paper.setDid(paperHist.getDid());

					paper.setLang(paperHist.getLang());
					paper.setTitle(paperHist.getTitle());
					paper.setAuthors(paperHist.getAuthors());
					paper.setSummary(paperHist.getSummary());
					paper.setKeywords(paperHist.getKeywords());

					paper.setPublisher(paperHist.getSigner());

					paper.setBirthTime(paperHist.getTime());
					paper.setBirthHeight(paperHist.getHeight());
					paper.setLastTxId(paperHist.getId());
					paper.setLastTime(paperHist.getTime());
					paper.setLastHeight(paperHist.getHeight());

					paper.setDeleted(false);

					Paper paper1 = paper;

					esClient.index(i -> i.index(IndicesNames.PAPER).id(paperHist.getPaperId()).document(paper1));
					isValid = true;
				} else {
					isValid = false;
				}
			}
			case UPDATE -> {
				paper = EsUtils.getById(esClient, IndicesNames.PAPER, paperHist.getPaperId(), Paper.class);
				if (paper == null) {
					isValid = false;
					break;
				}
				if (Boolean.TRUE.equals(paper.isDeleted())) {
					isValid = false;
					break;
				}
				if (!paper.getPublisher().equals(paperHist.getSigner())) {
					isValid = false;
					break;
				}
				paper.setVer(String.valueOf(Integer.parseInt(paper.getVer())+1));
				paper.setDid(paperHist.getDid());
				paper.setTitle(paperHist.getTitle());
				paper.setLang(paperHist.getLang());
				paper.setAuthors(paperHist.getAuthors());
				paper.setSummary(paperHist.getSummary());
				paper.setKeywords(paperHist.getKeywords());

				paper.setLastTxId(paperHist.getId());
				paper.setLastTime(paperHist.getTime());
				paper.setLastHeight(paperHist.getHeight());
				Paper paper2 = paper;
				esClient.index(i -> i.index(IndicesNames.PAPER).id(paperHist.getPaperId()).document(paper2));
				isValid = true;
			}

			case DELETE, RECOVER -> {
				List<String> idList = new ArrayList<>();
				if (paperHist.getPaperIds() != null && paperHist.getPaperIds().length > 0) {
					idList.addAll(Arrays.asList(paperHist.getPaperIds()));
				} else {
					isValid = false;
					break;
				}

				EsUtils.MgetResult<Paper> result = EsUtils.getMultiByIdList(esClient, IndicesNames.PAPER, idList, Paper.class);
				List<Paper> papers = result.getResultList();

				List<Paper> updatedPapers = new ArrayList<>();
				for (Paper paperItem : papers) {

					if (!paperItem.getPublisher().equals(paperHist.getSigner())) {
						Cid resultCid = EsUtils.getById(esClient, IndicesNames.CID, paperHist.getSigner(), Cid.class);
						if (resultCid ==null || resultCid.getMaster() == null || !resultCid.getMaster().equals(paperHist.getSigner())) {
							continue;
						}
					}

					switch (paperHist.getOp()) {
						case DELETE:
							paperItem.setDeleted(true);
							break;
						case RECOVER:
							paperItem.setDeleted(false);
							break;
					}

					paperItem.setLastTxId(paperHist.getId());
					paperItem.setLastTime(paperHist.getTime());
					paperItem.setLastHeight(paperHist.getHeight());

					updatedPapers.add(paperItem);
				}

				if (!updatedPapers.isEmpty()) {
					BulkRequest.Builder br = new BulkRequest.Builder();
					for (Paper updatedPaper : updatedPapers) {
						br.operations(op -> op
								.index(idx -> idx
										.index(IndicesNames.PAPER)
										.id(updatedPaper.getId())
										.document(updatedPaper)
								)
						);
					}
					esClient.bulk(br.build());
					isValid = true;
				}
			}

			case RATE -> {
				paper = EsUtils.getById(esClient, IndicesNames.PAPER, paperHist.getPaperId(), Paper.class);
				if (paper == null) {
					isValid = false;
					break;
				}
				if (paper.getPublisher().equals(paperHist.getSigner())) {
					isValid = false;
					break;
				}

				if((paperHist.getCdd()==null || paperHist.getRate()==null)){
					isValid=false;
					break;
				}

				if(paper.gettCdd()==null||paper.gettRate()==null){
					paper.settRate(Float.valueOf(paperHist.getRate()));
					paper.settCdd(paperHist.getCdd());
				}else{
					paper.settRate(
							(paper.gettRate()*paper.gettCdd()+paperHist.getRate()*paperHist.getCdd())
									/(paper.gettCdd()+paperHist.getCdd())
					);
					paper.settCdd(paper.gettCdd() + paperHist.getCdd());
				}

				paper.setLastTxId(paperHist.getId());
				paper.setLastTime(paperHist.getTime());
				paper.setLastHeight(paperHist.getHeight());
				Paper paper3 = paper;
				esClient.index(i -> i.index(IndicesNames.PAPER).id(paperHist.getPaperId()).document(paper3));
				isValid = true;
			}
		}

		return isValid;
	}

	public RemarkHistory makeRemark(OpReturn opre, Feip feip) {

		Gson gson = new Gson();

		RemarkOpData remarkRaw = new RemarkOpData();
		try {
			remarkRaw = gson.fromJson(gson.toJson(feip.getData()), RemarkOpData.class);
			if(remarkRaw==null)return null;
		}catch(Exception e) {
			e.printStackTrace();
			try {
				TimeUnit.SECONDS.sleep(5);
			} catch (InterruptedException ex) {
				throw new RuntimeException(ex);
			}
			return null;
		}

		RemarkHistory remarkHist = new RemarkHistory();

		if(remarkRaw.getOp()==null)return null;

		remarkHist.setOp(remarkRaw.getOp());

		switch(remarkRaw.getOp()) {

			case PUBLISH:
				if(remarkRaw.getTitle()==null||"".equals(remarkRaw.getTitle())) return null;
            	if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired * 100) return null;
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
				if(remarkRaw.getSummary()!=null)remarkHist.setSummary(remarkRaw.getSummary());
				if(remarkRaw.getOnDid()!=null)remarkHist.setOnDid(remarkRaw.getOnDid());

				break;

			case UPDATE:
				if(remarkRaw.getRemarkId()==null|| remarkRaw.getTitle()==null||"".equals(remarkRaw.getTitle()))
					return null;
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
				if(remarkRaw.getSummary()!=null)remarkHist.setSummary(remarkRaw.getSummary());
				if(remarkRaw.getOnDid()!=null)remarkHist.setOnDid(remarkRaw.getOnDid());

				break;
			case RECOVER:
			case DELETE:
				if (remarkRaw.getRemarkIds() == null || remarkRaw.getRemarkIds().length == 0) {
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
				if(remarkRaw.getRemarkId()==null)return null;
				if (opre.getCdd() < StartFEIP.CddRequired) return null;
				remarkHist.setRemarkId(remarkRaw.getRemarkId());
				remarkHist.setRate(remarkRaw.getRate());
				remarkHist.setCdd(opre.getCdd());

				remarkHist.setId(opre.getId());
				remarkHist.setHeight(opre.getHeight());
				remarkHist.setIndex(opre.getTxIndex());
				remarkHist.setTime(opre.getTime());
				remarkHist.setSigner(opre.getSigner());
				break;
			default:
				return null;
		}
		return remarkHist;
	}

	public boolean parseRemark(ElasticsearchClient esClient, RemarkHistory remarkHist) throws Exception {

		boolean isValid = false;
		if(remarkHist==null)return false;
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

					esClient.index(i -> i.index(IndicesNames.REMARK).id(remarkHist.getRemarkId()).document(remark1));
					isValid = true;
				} else {
					isValid = false;
				}
			}
			case UPDATE -> {
				remark = EsUtils.getById(esClient, IndicesNames.REMARK, remarkHist.getRemarkId(), Remark.class);
				if (remark == null) {
					isValid = false;
					break;
				}
				if (Boolean.TRUE.equals(remark.isDeleted())) {
					isValid = false;
					break;
				}
				if (!remark.getPublisher().equals(remarkHist.getSigner())) {
					isValid = false;
					break;
				}
				remark.setVer(String.valueOf(Integer.parseInt(remark.getVer())+1));
				remark.setDid(remarkHist.getDid());
				remark.setTitle(remarkHist.getTitle());
				remark.setLang(remarkHist.getLang());
				remark.setAuthors(remarkHist.getAuthors());
				remark.setSummary(remarkHist.getSummary());
				remark.setOnDid(remarkHist.getOnDid());

				remark.setLastTxId(remarkHist.getId());
				remark.setLastTime(remarkHist.getTime());
				remark.setLastHeight(remarkHist.getHeight());
				Remark remark2 = remark;
				esClient.index(i -> i.index(IndicesNames.REMARK).id(remarkHist.getRemarkId()).document(remark2));
				isValid = true;
			}

			case DELETE, RECOVER -> {
				List<String> idList = new ArrayList<>();
				if (remarkHist.getRemarkIds() != null && remarkHist.getRemarkIds().length > 0) {
					idList.addAll(Arrays.asList(remarkHist.getRemarkIds()));
				} else {
					isValid = false;
					break;
				}

				EsUtils.MgetResult<Remark> result = EsUtils.getMultiByIdList(esClient, IndicesNames.REMARK, idList, Remark.class);
				List<Remark> remarks = result.getResultList();

				List<Remark> updatedRemarks = new ArrayList<>();
				for (Remark remarkItem : remarks) {

					if (!remarkItem.getPublisher().equals(remarkHist.getSigner())) {
						Cid resultCid = EsUtils.getById(esClient, IndicesNames.CID, remarkHist.getSigner(), Cid.class);
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
					esClient.bulk(br.build());
					isValid = true;
				}
			}

			case RATE -> {
				remark = EsUtils.getById(esClient, IndicesNames.REMARK, remarkHist.getRemarkId(), Remark.class);
				if (remark == null) {
					isValid = false;
					break;
				}
				if (remark.getPublisher().equals(remarkHist.getSigner())) {
					isValid = false;
					break;
				}

				if((remarkHist.getCdd()==null || remarkHist.getRate()==null)){
					isValid=false;
					break;
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
				esClient.index(i -> i.index(IndicesNames.REMARK).id(remarkHist.getRemarkId()).document(remark3));
				isValid = true;
			}
		}

		return isValid;
	}

	public ArtworkHistory makeArtwork(OpReturn opre, Feip feip) {

		Gson gson = new Gson();

		ArtworkOpData artworkRaw = new ArtworkOpData();
		try {
			artworkRaw = gson.fromJson(gson.toJson(feip.getData()), ArtworkOpData.class);
			if(artworkRaw==null)return null;
		}catch(Exception e) {
			e.printStackTrace();
			try {
				TimeUnit.SECONDS.sleep(5);
			} catch (InterruptedException ex) {
				throw new RuntimeException(ex);
			}
			return null;
		}

		ArtworkHistory artworkHist = new ArtworkHistory();

		if(artworkRaw.getOp()==null)return null;

		artworkHist.setOp(artworkRaw.getOp());

		switch(artworkRaw.getOp()) {

			case PUBLISH:
				if(artworkRaw.getTitle()==null||"".equals(artworkRaw.getTitle())) return null;
            	if (opre.getHeight() > StartFEIP.CddCheckHeight && opre.getCdd() < StartFEIP.CddRequired * 100) return null;
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
				if(artworkRaw.getSummary()!=null)artworkHist.setSummary(artworkRaw.getSummary());

				break;
			case UPDATE:
				if(artworkRaw.getArtworkId()==null||"".equals(artworkRaw.getArtworkId())) return null;
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
				if(artworkRaw.getSummary()!=null)artworkHist.setSummary(artworkRaw.getSummary());

				break;
			case DELETE:
			case RECOVER:
				if(artworkRaw.getArtworkId()==null||"".equals(artworkRaw.getArtworkId())) return null;
				artworkHist.setId(opre.getId());

				artworkHist.setArtworkId(artworkRaw.getArtworkId());
				artworkHist.setHeight(opre.getHeight());
				artworkHist.setIndex(opre.getTxIndex());
				artworkHist.setTime(opre.getTime());
				artworkHist.setSigner(opre.getSigner());

				break;
			case RATE:
				if(artworkRaw.getArtworkId()==null||"".equals(artworkRaw.getArtworkId())) return null;
				if(artworkRaw.getRate()==null) return null;
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
				return null;
		}

		return artworkHist;
	}

	public boolean parseArtwork(ElasticsearchClient esClient, ArtworkHistory artworkHist) throws Exception {

		boolean isValid = false;
		if(artworkHist==null)return false;
		Artwork artwork;
		switch (artworkHist.getOp()) {
			case PUBLISH -> {
				artwork = EsUtils.getById(esClient, IndicesNames.ARTWORK, artworkHist.getArtworkId(), Artwork.class);
				if (artwork == null) {
					artwork = new Artwork();

					artwork.setId(artworkHist.getArtworkId());
					artwork.setVer("1");
					artwork.setDid(artworkHist.getDid());

					artwork.setLang(artworkHist.getLang());
					artwork.setTitle(artworkHist.getTitle());
					artwork.setAuthors(artworkHist.getAuthors());
					artwork.setSummary(artworkHist.getSummary());

					artwork.setPublisher(artworkHist.getSigner());

					artwork.setBirthTime(artworkHist.getTime());
					artwork.setBirthHeight(artworkHist.getHeight());
					artwork.setLastTxId(artworkHist.getId());
					artwork.setLastTime(artworkHist.getTime());
					artwork.setLastHeight(artworkHist.getHeight());

					artwork.setDeleted(false);

					Artwork artwork1 = artwork;

					esClient.index(i -> i.index(IndicesNames.ARTWORK).id(artworkHist.getArtworkId()).document(artwork1));
					isValid = true;
				} else {
					isValid = false;
				}
			}
			case UPDATE -> {
				artwork = EsUtils.getById(esClient, IndicesNames.ARTWORK, artworkHist.getArtworkId(), Artwork.class);
				if (artwork == null) {
					isValid = false;
					break;
				}
				if (Boolean.TRUE.equals(artwork.isDeleted())) {
					isValid = false;
					break;
				}
				if (!artwork.getPublisher().equals(artworkHist.getSigner())) {
					isValid = false;
					break;
				}
				artwork.setVer(String.valueOf(Integer.parseInt(artwork.getVer())+1));
				artwork.setDid(artworkHist.getDid());
				artwork.setTitle(artworkHist.getTitle());
				artwork.setLang(artworkHist.getLang());
				artwork.setAuthors(artworkHist.getAuthors());
				artwork.setSummary(artworkHist.getSummary());

				artwork.setLastTxId(artworkHist.getId());
				artwork.setLastTime(artworkHist.getTime());
				artwork.setLastHeight(artworkHist.getHeight());
				Artwork artwork2 = artwork;
				esClient.index(i -> i.index(IndicesNames.ARTWORK).id(artworkHist.getArtworkId()).document(artwork2));
				isValid = true;
			}
			case DELETE -> {
				artwork = EsUtils.getById(esClient, IndicesNames.ARTWORK, artworkHist.getArtworkId(), Artwork.class);
				if (artwork == null) {
					isValid = false;
					break;
				}
				if (Boolean.TRUE.equals(artwork.isDeleted())) {
					isValid = false;
					break;
				}
				if (!artwork.getPublisher().equals(artworkHist.getSigner())) {
					isValid = false;
					break;
				}
				artwork.setDeleted(true);
				artwork.setLastTxId(artworkHist.getId());
				artwork.setLastTime(artworkHist.getTime());
				artwork.setLastHeight(artworkHist.getHeight());
				Artwork artwork3 = artwork;
				esClient.index(i -> i.index(IndicesNames.ARTWORK).id(artworkHist.getArtworkId()).document(artwork3));
				isValid = true;
			}
			case RECOVER -> {
				artwork = EsUtils.getById(esClient, IndicesNames.ARTWORK, artworkHist.getArtworkId(), Artwork.class);
				if (artwork == null) {
					isValid = false;
					break;
				}
				if (!Boolean.TRUE.equals(artwork.isDeleted())) {
					isValid = false;
					break;
				}
				if (!artwork.getPublisher().equals(artworkHist.getSigner())) {
					isValid = false;
					break;
				}
				artwork.setDeleted(false);
				artwork.setLastTxId(artworkHist.getId());
				artwork.setLastTime(artworkHist.getTime());
				artwork.setLastHeight(artworkHist.getHeight());
				Artwork artwork4 = artwork;
				esClient.index(i -> i.index(IndicesNames.ARTWORK).id(artworkHist.getArtworkId()).document(artwork4));
				isValid = true;
			}
			case RATE -> {
				artwork = EsUtils.getById(esClient, IndicesNames.ARTWORK, artworkHist.getArtworkId(), Artwork.class);
				if (artwork == null) {
					isValid = false;
					break;
				}
				if (Boolean.TRUE.equals(artwork.isDeleted())) {
					isValid = false;
					break;
				}
				artwork.settCdd(artwork.gettCdd() + artworkHist.getCdd());
				artwork.settRate((artwork.gettRate() * artwork.gettCdd() + artworkHist.getRate() * artworkHist.getCdd()) / (artwork.gettCdd() + artworkHist.getCdd()));
				artwork.setLastTxId(artworkHist.getId());
				artwork.setLastTime(artworkHist.getTime());
				artwork.setLastHeight(artworkHist.getHeight());
				Artwork artwork5 = artwork;
				esClient.index(i -> i.index(IndicesNames.ARTWORK).id(artworkHist.getArtworkId()).document(artwork5));
				isValid = true;
			}
		}

		return isValid;
	}

}
