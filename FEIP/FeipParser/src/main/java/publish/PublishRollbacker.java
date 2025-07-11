package publish;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import constants.IndicesNames;
import data.feipData.*;
import utils.EsUtils;
import utils.JsonUtils;

import java.io.IOException;
import java.util.*;

import static constants.OpNames.PUBLISH;

public class PublishRollbacker {

    public boolean rollback(ElasticsearchClient esClient, long lastHeight) throws Exception {
        return rollbackEssay(esClient, lastHeight)
				|| rollbackStatement(esClient, lastHeight)
                || rollbackEssay(esClient, lastHeight)
                || rollbackReport(esClient, lastHeight)
                || rollbackPaper(esClient, lastHeight)
                || rollbackBook(esClient, lastHeight)
                || rollbackRemark(esClient, lastHeight)
                || rollbackArtwork(esClient, lastHeight);
    }

	public boolean rollbackStatement(ElasticsearchClient esClient, long lastHeight) throws Exception {
		List<String> indexList = new ArrayList<>();
		indexList.add(IndicesNames.STATEMENT);
		esClient.deleteByQuery(d->d.index(indexList).query(q->q.range(r->r.field("birthHeight").gt(JsonData.of(lastHeight)))));
		return false;
	}

    private boolean rollbackEssay(ElasticsearchClient esClient, long lastHeight) throws Exception {
        boolean error = false;
        Map<String, ArrayList<String>> resultMap = getEffectedEssays(esClient, lastHeight);
        ArrayList<String> itemIdList = resultMap.get("itemIdList");
        ArrayList<String> histIdList = resultMap.get("histIdList");

        if (itemIdList == null || itemIdList.isEmpty()) return error;
        System.out.println("If rolling back is interrupted, reparse all effected ids of index 'essay': ");
        JsonUtils.printJson(itemIdList);
        deleteEffectedItems(esClient, IndicesNames.ESSAY, itemIdList);
        if (histIdList == null || histIdList.isEmpty()) return error;
        deleteRolledHists(esClient, IndicesNames.ESSAY_HISTORY, histIdList);

        List<EssayHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.ESSAY_HISTORY, "essayId", itemIdList, EssayHistory.class);

        reparseEssay(esClient, reparseHistList);

        return error;
    }

    private Map<String, ArrayList<String>> getEffectedEssays(ElasticsearchClient esClient, long height) throws ElasticsearchException, IOException {
        SearchResponse<EssayHistory> resultSearch = esClient.search(s -> s
                .index(IndicesNames.ESSAY_HISTORY)
                .query(q -> q
                        .range(r -> r
                                .field("height")
                                .gt(JsonData.of(height)))), EssayHistory.class);

        Set<String> itemSet = new HashSet<>();
        ArrayList<String> histList = new ArrayList<>();

        for (Hit<EssayHistory> hit : resultSearch.hits().hits()) {
            EssayHistory item = hit.source();
            if (item.getOp().equals(PUBLISH)) {
                itemSet.add(item.getId());
            } else {
                itemSet.add(item.getEssayId());
            }
            histList.add(hit.id());
        }

        ArrayList<String> itemList = new ArrayList<>(itemSet);

        Map<String, ArrayList<String>> resultMap = new HashMap<>();
        resultMap.put("itemIdList", itemList);
        resultMap.put("histIdList", histList);

        return resultMap;
    }

    private void deleteEffectedItems(ElasticsearchClient esClient, String index, ArrayList<String> itemIdList) throws Exception {
        EsUtils.bulkDeleteList(esClient, index, itemIdList);
    }

    private void deleteRolledHists(ElasticsearchClient esClient, String index, ArrayList<String> histIdList) throws Exception {
        EsUtils.bulkDeleteList(esClient, index, histIdList);
    }

    private void reparseEssay(ElasticsearchClient esClient, List<EssayHistory> reparseHistList) throws Exception {
        if (reparseHistList == null) return;
        for (EssayHistory essayHist : reparseHistList) {
            new PublishParser().parseEssay(esClient, essayHist);
        }
    }

    private boolean rollbackReport(ElasticsearchClient esClient, long lastHeight) throws Exception {
        boolean error = false;
        Map<String, ArrayList<String>> resultMap = getEffectedReports(esClient, lastHeight);
        ArrayList<String> itemIdList = resultMap.get("itemIdList");
        ArrayList<String> histIdList = resultMap.get("histIdList");

        if (itemIdList == null || itemIdList.isEmpty()) return error;
        System.out.println("If rolling back is interrupted, reparse all effected ids of index 'report': ");
        JsonUtils.printJson(itemIdList);
        deleteEffectedItems(esClient, IndicesNames.REPORT, itemIdList);
        if (histIdList == null || histIdList.isEmpty()) return error;
        deleteRolledHists(esClient, IndicesNames.REPORT_HISTORY, histIdList);

        List<ReportHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.REPORT_HISTORY, "reportId", itemIdList, ReportHistory.class);

        reparseReport(esClient, reparseHistList);

        return error;
    }

    private Map<String, ArrayList<String>> getEffectedReports(ElasticsearchClient esClient, long lastHeight) throws ElasticsearchException, IOException {
        SearchResponse<ReportHistory> resultSearch = esClient.search(s -> s
                .index(IndicesNames.REPORT_HISTORY)
                .query(q -> q
                        .range(r -> r
                                .field("height")
                                .gt(JsonData.of(lastHeight)))), ReportHistory.class);

        Set<String> itemSet = new HashSet<>();
        ArrayList<String> histList = new ArrayList<>();

        for (Hit<ReportHistory> hit : resultSearch.hits().hits()) {
            ReportHistory item = hit.source();
            if (item.getOp().equals(PUBLISH)) {
                itemSet.add(item.getId());
            } else {
                itemSet.add(item.getReportId());
            }
            histList.add(hit.id());
        }

        ArrayList<String> itemList = new ArrayList<>(itemSet);

        Map<String, ArrayList<String>> resultMap = new HashMap<>();
        resultMap.put("itemIdList", itemList);
        resultMap.put("histIdList", histList);

        return resultMap;
    }

    private void reparseReport(ElasticsearchClient esClient, List<ReportHistory> reparseHistList) throws Exception {
        if (reparseHistList == null) return;
        for (ReportHistory reportHist : reparseHistList) {
            new PublishParser().parseReport(esClient, reportHist);
        }
    }

    private boolean rollbackPaper(ElasticsearchClient esClient, long lastHeight) throws Exception {
        boolean error = false;
        Map<String, ArrayList<String>> resultMap = getEffectedPapers(esClient, lastHeight);
        ArrayList<String> itemIdList = resultMap.get("itemIdList");
        ArrayList<String> histIdList = resultMap.get("histIdList");

        if (itemIdList == null || itemIdList.isEmpty()) return error;
        System.out.println("If rolling back is interrupted, reparse all effected ids of index 'paper': ");
        JsonUtils.printJson(itemIdList);
        deleteEffectedItems(esClient, IndicesNames.PAPER, itemIdList);
        if (histIdList == null || histIdList.isEmpty()) return error;
        deleteRolledHists(esClient, IndicesNames.PAPER_HISTORY, histIdList);

        List<PaperHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.PAPER_HISTORY, "paperId", itemIdList, PaperHistory.class);

        reparsePaper(esClient, reparseHistList);

        return error;
    }

    private Map<String, ArrayList<String>> getEffectedPapers(ElasticsearchClient esClient, long lastHeight) throws ElasticsearchException, IOException {
        SearchResponse<PaperHistory> resultSearch = esClient.search(s -> s
                .index(IndicesNames.PAPER_HISTORY)
                .query(q -> q
                        .range(r -> r
                                .field("height")
                                .gt(JsonData.of(lastHeight)))), PaperHistory.class);

        Set<String> itemSet = new HashSet<>();
        ArrayList<String> histList = new ArrayList<>();

        for (Hit<PaperHistory> hit : resultSearch.hits().hits()) {
            PaperHistory item = hit.source();
            if (item.getOp().equals(PUBLISH)) {
                itemSet.add(item.getId());
            } else {
                itemSet.add(item.getPaperId());
            }
            histList.add(hit.id());
        }

        ArrayList<String> itemList = new ArrayList<>(itemSet);

        Map<String, ArrayList<String>> resultMap = new HashMap<>();
        resultMap.put("itemIdList", itemList);
        resultMap.put("histIdList", histList);

        return resultMap;
    }

    private void reparsePaper(ElasticsearchClient esClient, List<PaperHistory> reparseHistList) throws Exception {
        if (reparseHistList == null) return;
        for (PaperHistory paperHist : reparseHistList) {
            new PublishParser().parsePaper(esClient, paperHist);
        }
    }

    private boolean rollbackBook(ElasticsearchClient esClient, long lastHeight) throws Exception {
        boolean error = false;
        Map<String, ArrayList<String>> resultMap = getEffectedBooks(esClient, lastHeight);
        ArrayList<String> itemIdList = resultMap.get("itemIdList");
        ArrayList<String> histIdList = resultMap.get("histIdList");

        if (itemIdList == null || itemIdList.isEmpty()) return error;
        System.out.println("If rolling back is interrupted, reparse all effected ids of index 'book': ");
        JsonUtils.printJson(itemIdList);
        deleteEffectedItems(esClient, IndicesNames.BOOK, itemIdList);
        if (histIdList == null || histIdList.isEmpty()) return error;
        deleteRolledHists(esClient, IndicesNames.BOOK_HISTORY, histIdList);

        List<BookHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.BOOK_HISTORY, "bookId", itemIdList, BookHistory.class);

        reparseBook(esClient, reparseHistList);

        return error;
    }

    private Map<String, ArrayList<String>> getEffectedBooks(ElasticsearchClient esClient, long lastHeight) throws ElasticsearchException, IOException {
        SearchResponse<BookHistory> resultSearch = esClient.search(s -> s
                .index(IndicesNames.BOOK_HISTORY)
                .query(q -> q
                        .range(r -> r
                                .field("height")
                                .gt(JsonData.of(lastHeight)))), BookHistory.class);

        Set<String> itemSet = new HashSet<>();
        ArrayList<String> histList = new ArrayList<>();

        for (Hit<BookHistory> hit : resultSearch.hits().hits()) {
            BookHistory item = hit.source();
            if (item.getOp().equals(PUBLISH)) {
                itemSet.add(item.getId());
            } else {
                itemSet.add(item.getBookId());
            }
            histList.add(hit.id());
        }

        ArrayList<String> itemList = new ArrayList<>(itemSet);

        Map<String, ArrayList<String>> resultMap = new HashMap<>();
        resultMap.put("itemIdList", itemList);
        resultMap.put("histIdList", histList);

        return resultMap;
    }

    private void reparseBook(ElasticsearchClient esClient, List<BookHistory> reparseHistList) throws Exception {
        if (reparseHistList == null) return;
        for (BookHistory bookHist : reparseHistList) {
            new PublishParser().parseBook(esClient, bookHist);
        }
    }

    private boolean rollbackRemark(ElasticsearchClient esClient, long lastHeight) throws Exception {
        boolean error = false;
        Map<String, ArrayList<String>> resultMap = getEffectedRemarks(esClient, lastHeight);
        ArrayList<String> itemIdList = resultMap.get("itemIdList");
        ArrayList<String> histIdList = resultMap.get("histIdList");

        if (itemIdList == null || itemIdList.isEmpty()) return error;
        System.out.println("If rolling back is interrupted, reparse all effected ids of index 'remark': ");
        JsonUtils.printJson(itemIdList);
        deleteEffectedItems(esClient, IndicesNames.REMARK, itemIdList);
        if (histIdList == null || histIdList.isEmpty()) return error;
        deleteRolledHists(esClient, IndicesNames.REMARK_HISTORY, histIdList);

        List<RemarkHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.REMARK_HISTORY, "remarkId", itemIdList, RemarkHistory.class);

        reparseRemark(esClient, reparseHistList);

        return error;
    }

    private boolean rollbackArtwork(ElasticsearchClient esClient, long lastHeight) throws Exception {
        boolean error = false;
        Map<String, ArrayList<String>> resultMap = getEffectedArtworks(esClient, lastHeight);
        ArrayList<String> itemIdList = resultMap.get("itemIdList");
        ArrayList<String> histIdList = resultMap.get("histIdList");

        if (itemIdList == null || itemIdList.isEmpty()) return error;
        System.out.println("If rolling back is interrupted, reparse all effected ids of index 'artwork': ");
        JsonUtils.printJson(itemIdList);
        deleteEffectedItems(esClient, IndicesNames.ARTWORK, itemIdList);
        if (histIdList == null || histIdList.isEmpty()) return error;
        deleteRolledHists(esClient, IndicesNames.ARTWORK_HISTORY, histIdList);

        List<ArtworkHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.ARTWORK_HISTORY, "artworkId", itemIdList, ArtworkHistory.class);

        reparseArtwork(esClient, reparseHistList);

        return error;
    }

    private Map<String, ArrayList<String>> getEffectedRemarks(ElasticsearchClient esClient, long lastHeight) throws ElasticsearchException, IOException {
        SearchResponse<RemarkHistory> resultSearch = esClient.search(s -> s
                .index(IndicesNames.REMARK_HISTORY)
                .query(q -> q
                        .range(r -> r
                                .field("height")
                                .gt(JsonData.of(lastHeight)))), RemarkHistory.class);

        Set<String> itemSet = new HashSet<>();
        ArrayList<String> histList = new ArrayList<>();

        for (Hit<RemarkHistory> hit : resultSearch.hits().hits()) {
            RemarkHistory item = hit.source();
            if (item.getOp().equals(PUBLISH)) {
                itemSet.add(item.getId());
            } else {
                itemSet.add(item.getRemarkId());
            }
            histList.add(hit.id());
        }

        ArrayList<String> itemList = new ArrayList<>(itemSet);

        Map<String, ArrayList<String>> resultMap = new HashMap<>();
        resultMap.put("itemIdList", itemList);
        resultMap.put("histIdList", histList);

        return resultMap;
    }

    private void reparseRemark(ElasticsearchClient esClient, List<RemarkHistory> reparseHistList) throws Exception {
        if (reparseHistList == null) return;
        for (RemarkHistory remarkHist : reparseHistList) {
            new PublishParser().parseRemark(esClient, remarkHist);
        }
    }

    private Map<String, ArrayList<String>> getEffectedArtworks(ElasticsearchClient esClient, long lastHeight) throws ElasticsearchException, IOException {
        SearchResponse<ArtworkHistory> resultSearch = esClient.search(s -> s
                .index(IndicesNames.ARTWORK_HISTORY)
                .query(q -> q
                        .range(r -> r
                                .field("height")
                                .gt(JsonData.of(lastHeight)))), ArtworkHistory.class);

        Set<String> itemSet = new HashSet<>();
        ArrayList<String> histList = new ArrayList<>();

        for (Hit<ArtworkHistory> hit : resultSearch.hits().hits()) {
            ArtworkHistory item = hit.source();
            if (item.getOp().equals(PUBLISH)) {
                itemSet.add(item.getId());
            } else {
                itemSet.add(item.getArtworkId());
            }
            histList.add(hit.id());
        }

        ArrayList<String> itemList = new ArrayList<>(itemSet);

        Map<String, ArrayList<String>> resultMap = new HashMap<>();
        resultMap.put("itemIdList", itemList);
        resultMap.put("histIdList", histList);

        return resultMap;
    }

    private void reparseArtwork(ElasticsearchClient esClient, List<ArtworkHistory> reparseHistList) throws Exception {
        if (reparseHistList == null) return;
        for (ArtworkHistory artworkHist : reparseHistList) {
            new PublishParser().parseArtwork(esClient, artworkHist);
        }
    }
}
