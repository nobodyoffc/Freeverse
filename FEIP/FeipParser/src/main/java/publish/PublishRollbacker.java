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

import static constants.FieldNames.*;
import static constants.OpNames.*;

public class PublishRollbacker {

    public boolean rollback(ElasticsearchClient esClient, long lastHeight) throws Exception {
        return rollbackStatement(esClient, lastHeight)
                || rollbackText(esClient, lastHeight)
                || rollbackRemark(esClient, lastHeight)
                || rollbackSound(esClient, lastHeight)
                || rollbackImage(esClient, lastHeight)
                || rollbackVideo(esClient, lastHeight);
    }

    private boolean rollbackText(ElasticsearchClient esClient, long lastHeight) throws Exception {
        boolean error = false;
        Map<String, ArrayList<String>> resultMap = getEffectedTexts(esClient, lastHeight);
        ArrayList<String> itemIdList = resultMap.get("itemIdList");
        ArrayList<String> histIdList = resultMap.get("histIdList");

        if (itemIdList == null || itemIdList.isEmpty()) return error;
        System.out.println("If rolling back is interrupted, reparse all effected ids of index 'text': ");
        JsonUtils.printJson(itemIdList);
        deleteEffectedItems(esClient, IndicesNames.TEXT, itemIdList);
        if (histIdList == null || histIdList.isEmpty()) return error;
        deleteRolledHists(esClient, IndicesNames.TEXT_HISTORY, histIdList);

        List<TextHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.TEXT_HISTORY, TEXT_ID,TEXT_IDS , itemIdList, TextHistory.class);

        reparseText(esClient, reparseHistList);

        return error;
    }

    private Map<String, ArrayList<String>> getEffectedTexts(ElasticsearchClient esClient, long lastHeight) throws ElasticsearchException, IOException {
        SearchResponse<TextHistory> resultSearch = esClient.search(s -> s
                .index(IndicesNames.TEXT_HISTORY)
                .query(q -> q
                        .range(r -> r
                                .field("height")
                                .gt(JsonData.of(lastHeight)))), TextHistory.class);

        Set<String> itemSet = new HashSet<>();
        ArrayList<String> histList = new ArrayList<>();

        for (Hit<TextHistory> hit : resultSearch.hits().hits()) {
            TextHistory item = hit.source();
            if(item==null){
                System.out.println("Text hist is null");
                continue;
            }

            String op = item.getOp();
            switch (op) {
                case PUBLISH -> {
                    if(item.getId()==null){
                        continue;
                    }
                    itemSet.add(item.getId());
                }
                case DELETE, RECOVER -> {
                    if(item.getTextIds()==null || item.getTextIds().length==0){
                        continue;
                    }
                    for(String textId: item.getTextIds()){
                        if(textId==null){
                            continue;
                        }
                        itemSet.add(textId);
                    }
                }
                default -> {
                    if(item.getTextId()==null)
                        continue;
                    
                    itemSet.add(item.getTextId());
                }
            }
            histList.add(hit.id());
        }

        ArrayList<String> itemList = new ArrayList<>(itemSet);

        Map<String, ArrayList<String>> resultMap = new HashMap<>();
        resultMap.put("itemIdList", itemList);
        resultMap.put("histIdList", histList);

        return resultMap;
    }

    private void reparseText(ElasticsearchClient esClient, List<TextHistory> reparseHistList) throws Exception {
        if (reparseHistList == null) return;
        for (TextHistory textHist : reparseHistList) {
            new PublishParser().parseText(esClient, textHist);
        }
    }

	public boolean rollbackStatement(ElasticsearchClient esClient, long lastHeight) throws Exception {
		List<String> indexList = new ArrayList<>();
		indexList.add(IndicesNames.STATEMENT);
		esClient.deleteByQuery(d->d.index(indexList).query(q->q.range(r->r.field("birthHeight").gt(JsonData.of(lastHeight)))));
		return false;
	}


    private void deleteEffectedItems(ElasticsearchClient esClient, String index, ArrayList<String> itemIdList) throws Exception {
        EsUtils.bulkDeleteList(esClient, index, itemIdList);
    }

    private void deleteRolledHists(ElasticsearchClient esClient, String index, ArrayList<String> histIdList) throws Exception {
        EsUtils.bulkDeleteList(esClient, index, histIdList);
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

        List<RemarkHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.REMARK_HISTORY, REMARK_ID,REMARK_IDS , itemIdList, RemarkHistory.class);

        reparseRemark(esClient, reparseHistList);

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
            if(item==null){
                System.out.println("Remark hist is null");
                continue;
            }
            String op = item.getOp();
            switch (op) {
                case PUBLISH -> {
                    if(item.getId()==null){
                        continue;
                    }
                    itemSet.add(item.getId());
                }
                case DELETE, RECOVER -> {
                    if(item.getRemarkIds()==null || item.getRemarkIds().length==0){
                        continue;
                    }
                    for(String remarkId: item.getRemarkIds()){
                        if(remarkId==null){
                            continue;
                        }
                        itemSet.add(remarkId);
                    }
                }
                default -> {
                    if(item.getRemarkId()==null)
                        continue;
                    itemSet.add(item.getRemarkId());
                }
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

    private boolean rollbackSound(ElasticsearchClient esClient, long lastHeight) throws Exception {
        boolean error = false;
        Map<String, ArrayList<String>> resultMap = getEffectedSounds(esClient, lastHeight);
        ArrayList<String> itemIdList = resultMap.get("itemIdList");
        ArrayList<String> histIdList = resultMap.get("histIdList");

        if (itemIdList == null || itemIdList.isEmpty()) return error;
        System.out.println("If rolling back is interrupted, reparse all effected ids of index 'sound': ");
        JsonUtils.printJson(itemIdList);
        deleteEffectedItems(esClient, IndicesNames.SOUND, itemIdList);
        if (histIdList == null || histIdList.isEmpty()) return error;
        deleteRolledHists(esClient, IndicesNames.SOUND_HISTORY, histIdList);

        List<SoundHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.SOUND_HISTORY, SOUND_ID, SOUND_IDS, itemIdList, SoundHistory.class);

        reparseSound(esClient, reparseHistList);

        return error;
    }

    private Map<String, ArrayList<String>> getEffectedSounds(ElasticsearchClient esClient, long lastHeight) throws ElasticsearchException, IOException {
        SearchResponse<SoundHistory> resultSearch = esClient.search(s -> s
                .index(IndicesNames.SOUND_HISTORY)
                .query(q -> q
                        .range(r -> r
                                .field("height")
                                .gt(JsonData.of(lastHeight)))), SoundHistory.class);

        Set<String> itemSet = new HashSet<>();
        ArrayList<String> histList = new ArrayList<>();

        for (Hit<SoundHistory> hit : resultSearch.hits().hits()) {
            SoundHistory item = hit.source();
            if(item==null){
                System.out.println("Sound hist is null");
                continue;
            }
            String op = item.getOp();
            switch (op) {
                case PUBLISH -> {
                    if(item.getId()==null){
                        continue;
                    }
                    itemSet.add(item.getId());
                }
                case DELETE, RECOVER -> {
                    if(item.getSoundIds()==null || item.getSoundIds().length==0){
                        continue;
                    }
                    for(String soundId: item.getSoundIds()){
                        if(soundId==null){
                            continue;
                        }
                        itemSet.add(soundId);
                    }
                }
                default -> {
                    if(item.getSoundId()==null)
                        continue;
                    itemSet.add(item.getSoundId());
                }
            }
            histList.add(hit.id());
        }

        ArrayList<String> itemList = new ArrayList<>(itemSet);

        Map<String, ArrayList<String>> resultMap = new HashMap<>();
        resultMap.put("itemIdList", itemList);
        resultMap.put("histIdList", histList);

        return resultMap;
    }

    private void reparseSound(ElasticsearchClient esClient, List<SoundHistory> reparseHistList) throws Exception {
        if (reparseHistList == null) return;
        for (SoundHistory soundHist : reparseHistList) {
            new PublishParser().parseSound(esClient, soundHist);
        }
    }

    private boolean rollbackImage(ElasticsearchClient esClient, long lastHeight) throws Exception {
        boolean error = false;
        Map<String, ArrayList<String>> resultMap = getEffectedImages(esClient, lastHeight);
        ArrayList<String> itemIdList = resultMap.get("itemIdList");
        ArrayList<String> histIdList = resultMap.get("histIdList");

        if (itemIdList == null || itemIdList.isEmpty()) return error;
        System.out.println("If rolling back is interrupted, reparse all effected ids of index 'image': ");
        JsonUtils.printJson(itemIdList);
        deleteEffectedItems(esClient, IndicesNames.IMAGE, itemIdList);
        if (histIdList == null || histIdList.isEmpty()) return error;
        deleteRolledHists(esClient, IndicesNames.IMAGE_HISTORY, histIdList);

        List<ImageHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.IMAGE_HISTORY, IMAGE_ID,IMAGE_IDS , itemIdList, ImageHistory.class);

        reparseImage(esClient, reparseHistList);

        return error;
    }

    private Map<String, ArrayList<String>> getEffectedImages(ElasticsearchClient esClient, long lastHeight) throws ElasticsearchException, IOException {
        SearchResponse<ImageHistory> resultSearch = esClient.search(s -> s
                .index(IndicesNames.IMAGE_HISTORY)
                .query(q -> q
                        .range(r -> r
                                .field("height")
                                .gt(JsonData.of(lastHeight)))), ImageHistory.class);

        Set<String> itemSet = new HashSet<>();
        ArrayList<String> histList = new ArrayList<>();

        for (Hit<ImageHistory> hit : resultSearch.hits().hits()) {
            ImageHistory item = hit.source();
            if(item==null){
                System.out.println("Image hist is null");
                continue;
            }
            String op = item.getOp();
            switch (op) {
                case PUBLISH -> {
                    if(item.getId()==null){
                        continue;
                    }
                    itemSet.add(item.getId());
                }   
                case DELETE, RECOVER -> {
                    if(item.getImageIds()==null || item.getImageIds().length==0){
                        continue;
                    }
                    for(String imageId: item.getImageIds()){
                        if(imageId==null){
                            continue;
                        }
                        itemSet.add(imageId);
                    }
                }
                default -> {
                    if(item.getImageId()==null)
                        continue;
                    itemSet.add(item.getImageId());
                }
            }
            histList.add(hit.id());
        }

        ArrayList<String> itemList = new ArrayList<>(itemSet);

        Map<String, ArrayList<String>> resultMap = new HashMap<>();
        resultMap.put("itemIdList", itemList);
        resultMap.put("histIdList", histList);

        return resultMap;
    }

    private void reparseImage(ElasticsearchClient esClient, List<ImageHistory> reparseHistList) throws Exception {
        if (reparseHistList == null) return;
        for (ImageHistory imageHist : reparseHistList) {
            new PublishParser().parseImage(esClient, imageHist);
        }
    }

    private boolean rollbackVideo(ElasticsearchClient esClient, long lastHeight) throws Exception {
        boolean error = false;
        Map<String, ArrayList<String>> resultMap = getEffectedVideos(esClient, lastHeight);
        ArrayList<String> itemIdList = resultMap.get("itemIdList");
        ArrayList<String> histIdList = resultMap.get("histIdList");

        if (itemIdList == null || itemIdList.isEmpty()) return error;
        System.out.println("If rolling back is interrupted, reparse all effected ids of index 'video': ");
        JsonUtils.printJson(itemIdList);
        deleteEffectedItems(esClient, IndicesNames.VIDEO, itemIdList);
        if (histIdList == null || histIdList.isEmpty()) return error;
        deleteRolledHists(esClient, IndicesNames.VIDEO_HISTORY, histIdList);

        List<VideoHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.VIDEO_HISTORY, VIDEO_ID,VIDEO_IDS , itemIdList, VideoHistory.class);

        reparseVideo(esClient, reparseHistList);

        return error;
    }

    private Map<String, ArrayList<String>> getEffectedVideos(ElasticsearchClient esClient, long lastHeight) throws ElasticsearchException, IOException {
        SearchResponse<VideoHistory> resultSearch = esClient.search(s -> s
                .index(IndicesNames.VIDEO_HISTORY)
                .query(q -> q
                        .range(r -> r
                                .field("height")
                                .gt(JsonData.of(lastHeight)))), VideoHistory.class);

        Set<String> itemSet = new HashSet<>();
        ArrayList<String> histList = new ArrayList<>();

        for (Hit<VideoHistory> hit : resultSearch.hits().hits()) {
            VideoHistory item = hit.source();
            if(item==null){
                System.out.println("Video hist is null");
                continue;
            }

            String op = item.getOp();
            switch (op) {
                case PUBLISH -> {
                    if(item.getId()==null){
                        continue;
                    }
                    itemSet.add(item.getId());
                }
            
                case DELETE, RECOVER -> {
                    if(item.getVideoIds()==null || item.getVideoIds().length==0){
                        continue;
                    }
                    for(String videoId: item.getVideoIds()){
                        if(videoId==null){
                            continue;
                        }
                        itemSet.add(videoId);
                    }
                }
                default -> {
                    if(item.getVideoId()==null)
                        continue;
                    itemSet.add(item.getVideoId());
                }
            }
            histList.add(hit.id());
        }

        ArrayList<String> itemList = new ArrayList<>(itemSet);

        Map<String, ArrayList<String>> resultMap = new HashMap<>();
        resultMap.put("itemIdList", itemList);
        resultMap.put("histIdList", histList);

        return resultMap;
    }

    private void reparseVideo(ElasticsearchClient esClient, List<VideoHistory> reparseHistList) throws Exception {
        if (reparseHistList == null) return;
        for (VideoHistory videoHist : reparseHistList) {
            new PublishParser().parseVideo(esClient, videoHist);
        }
    }
}
