package publish;

import startFEIP.Reparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
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

    private static final Logger log = LoggerFactory.getLogger(PublishRollbacker.class);

    public boolean rollback(ElasticsearchClient esClient, long lastHeight) throws Exception {
        boolean error = false;
        error |= rollbackStatement(esClient, lastHeight);
        error |= rollbackText(esClient, lastHeight);
        error |= rollbackRemark(esClient, lastHeight);
        error |= rollbackSound(esClient, lastHeight);
        error |= rollbackImage(esClient, lastHeight);
        error |= rollbackVideo(esClient, lastHeight);
        return error;
    }

    private boolean rollbackText(ElasticsearchClient esClient, long lastHeight) throws Exception {
        boolean error = false;
        Map<String, ArrayList<String>> resultMap = getEffectedTexts(esClient, lastHeight);
        ArrayList<String> itemIdList = resultMap.get("itemIdList");
        ArrayList<String> histIdList = resultMap.get("histIdList");

        if (itemIdList == null || itemIdList.isEmpty()) return error;
        log.warn("If rolling back is interrupted, reparse all effected ids of index 'text': ");
        JsonUtils.printJson(itemIdList);

        List<TextHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.TEXT_HISTORY, TEXT_ID, TEXT_IDS, itemIdList, lastHeight, TextHistory.class);

        error |= deleteEffectedItems(esClient, IndicesNames.TEXT, itemIdList);
        if (histIdList != null && !histIdList.isEmpty())
            error |= deleteRolledHists(esClient, IndicesNames.TEXT_HISTORY, histIdList);

        error |= reparseText(esClient, reparseHistList);

        return error;
    }

    private Map<String, ArrayList<String>> getEffectedTexts(ElasticsearchClient esClient, long lastHeight) throws ElasticsearchException, IOException {
        List<Hit<TextHistory>> effectedHits = EsUtils.scanHitsAboveHeight(esClient, IndicesNames.TEXT_HISTORY, "height", lastHeight, TextHistory.class);

        Set<String> itemSet = new HashSet<>();
        ArrayList<String> histList = new ArrayList<>();

        for (Hit<TextHistory> hit : effectedHits) {
            TextHistory item = hit.source();
            if(item==null){
                log.info("Text hist is null");
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
                    if(item.getTextIds()==null || item.getTextIds().size()==0){
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

    private boolean reparseText(ElasticsearchClient esClient, List<TextHistory> reparseHistList) {
    	PublishParser parser = new PublishParser();
    	return Reparser.replay("text", reparseHistList, h -> parser.parseText(esClient, h));
    }

	public boolean rollbackStatement(ElasticsearchClient esClient, long lastHeight) throws Exception {
		List<String> indexList = new ArrayList<>();
		indexList.add(IndicesNames.STATEMENT);
		DeleteByQueryResponse response = esClient.deleteByQuery(d->d.index(indexList)
				.conflicts(co.elastic.clients.elasticsearch._types.Conflicts.Proceed)
				.query(q->q.range(r->r.field("birthHeight").gt(JsonData.of(lastHeight)))));
		if (response != null && response.failures() != null && !response.failures().isEmpty()) {
			log.error("Rollback: deleteByQuery reported {} failures on statement", response.failures().size());
			return true;
		}
		return false;
	}


    private boolean deleteEffectedItems(ElasticsearchClient esClient, String index, ArrayList<String> itemIdList) throws Exception {
        BulkResponse response = EsUtils.bulkDeleteList(esClient, index, itemIdList);
        if (response != null && response.errors()) {
            log.error("Rollback: bulk delete reported errors on index {}", index);
            return true;
        }
        return false;
    }

    private boolean deleteRolledHists(ElasticsearchClient esClient, String index, ArrayList<String> histIdList) throws Exception {
        BulkResponse response = EsUtils.bulkDeleteList(esClient, index, histIdList);
        if (response != null && response.errors()) {
            log.error("Rollback: bulk delete reported errors on index {}", index);
            return true;
        }
        return false;
    }


    private boolean rollbackRemark(ElasticsearchClient esClient, long lastHeight) throws Exception {
        boolean error = false;
        Map<String, ArrayList<String>> resultMap = getEffectedRemarks(esClient, lastHeight);
        ArrayList<String> itemIdList = resultMap.get("itemIdList");
        ArrayList<String> histIdList = resultMap.get("histIdList");

        if (itemIdList == null || itemIdList.isEmpty()) return error;
        log.warn("If rolling back is interrupted, reparse all effected ids of index 'remark': ");
        JsonUtils.printJson(itemIdList);

        List<RemarkHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.REMARK_HISTORY, REMARK_ID, REMARK_IDS, itemIdList, lastHeight, RemarkHistory.class);

        error |= deleteEffectedItems(esClient, IndicesNames.REMARK, itemIdList);
        if (histIdList != null && !histIdList.isEmpty())
            error |= deleteRolledHists(esClient, IndicesNames.REMARK_HISTORY, histIdList);

        error |= reparseRemark(esClient, reparseHistList);

        return error;
    }


    private Map<String, ArrayList<String>> getEffectedRemarks(ElasticsearchClient esClient, long lastHeight) throws ElasticsearchException, IOException {
        List<Hit<RemarkHistory>> effectedHits = EsUtils.scanHitsAboveHeight(esClient, IndicesNames.REMARK_HISTORY, "height", lastHeight, RemarkHistory.class);

        Set<String> itemSet = new HashSet<>();
        ArrayList<String> histList = new ArrayList<>();

        for (Hit<RemarkHistory> hit : effectedHits) {
            RemarkHistory item = hit.source();
            if(item==null){
                log.info("Remark hist is null");
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

    private boolean reparseRemark(ElasticsearchClient esClient, List<RemarkHistory> reparseHistList) {
    	PublishParser parser = new PublishParser();
    	return Reparser.replay("remark", reparseHistList, h -> parser.parseRemark(esClient, h));
    }

    private boolean rollbackSound(ElasticsearchClient esClient, long lastHeight) throws Exception {
        boolean error = false;
        Map<String, ArrayList<String>> resultMap = getEffectedSounds(esClient, lastHeight);
        ArrayList<String> itemIdList = resultMap.get("itemIdList");
        ArrayList<String> histIdList = resultMap.get("histIdList");

        if (itemIdList == null || itemIdList.isEmpty()) return error;
        log.warn("If rolling back is interrupted, reparse all effected ids of index 'sound': ");
        JsonUtils.printJson(itemIdList);

        List<SoundHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.SOUND_HISTORY, SOUND_ID, SOUND_IDS, itemIdList, lastHeight, SoundHistory.class);

        error |= deleteEffectedItems(esClient, IndicesNames.SOUND, itemIdList);
        if (histIdList != null && !histIdList.isEmpty())
            error |= deleteRolledHists(esClient, IndicesNames.SOUND_HISTORY, histIdList);

        error |= reparseSound(esClient, reparseHistList);

        return error;
    }

    private Map<String, ArrayList<String>> getEffectedSounds(ElasticsearchClient esClient, long lastHeight) throws ElasticsearchException, IOException {
        List<Hit<SoundHistory>> effectedHits = EsUtils.scanHitsAboveHeight(esClient, IndicesNames.SOUND_HISTORY, "height", lastHeight, SoundHistory.class);

        Set<String> itemSet = new HashSet<>();
        ArrayList<String> histList = new ArrayList<>();

        for (Hit<SoundHistory> hit : effectedHits) {
            SoundHistory item = hit.source();
            if(item==null){
                log.info("Sound hist is null");
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

    private boolean reparseSound(ElasticsearchClient esClient, List<SoundHistory> reparseHistList) {
    	PublishParser parser = new PublishParser();
    	return Reparser.replay("sound", reparseHistList, h -> parser.parseSound(esClient, h));
    }

    private boolean rollbackImage(ElasticsearchClient esClient, long lastHeight) throws Exception {
        boolean error = false;
        Map<String, ArrayList<String>> resultMap = getEffectedImages(esClient, lastHeight);
        ArrayList<String> itemIdList = resultMap.get("itemIdList");
        ArrayList<String> histIdList = resultMap.get("histIdList");

        if (itemIdList == null || itemIdList.isEmpty()) return error;
        log.warn("If rolling back is interrupted, reparse all effected ids of index 'image': ");
        JsonUtils.printJson(itemIdList);

        List<ImageHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.IMAGE_HISTORY, IMAGE_ID, IMAGE_IDS, itemIdList, lastHeight, ImageHistory.class);

        error |= deleteEffectedItems(esClient, IndicesNames.IMAGE, itemIdList);
        if (histIdList != null && !histIdList.isEmpty())
            error |= deleteRolledHists(esClient, IndicesNames.IMAGE_HISTORY, histIdList);

        error |= reparseImage(esClient, reparseHistList);

        return error;
    }

    private Map<String, ArrayList<String>> getEffectedImages(ElasticsearchClient esClient, long lastHeight) throws ElasticsearchException, IOException {
        List<Hit<ImageHistory>> effectedHits = EsUtils.scanHitsAboveHeight(esClient, IndicesNames.IMAGE_HISTORY, "height", lastHeight, ImageHistory.class);

        Set<String> itemSet = new HashSet<>();
        ArrayList<String> histList = new ArrayList<>();

        for (Hit<ImageHistory> hit : effectedHits) {
            ImageHistory item = hit.source();
            if(item==null){
                log.info("Image hist is null");
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

    private boolean reparseImage(ElasticsearchClient esClient, List<ImageHistory> reparseHistList) {
    	PublishParser parser = new PublishParser();
    	return Reparser.replay("image", reparseHistList, h -> parser.parseImage(esClient, h));
    }

    private boolean rollbackVideo(ElasticsearchClient esClient, long lastHeight) throws Exception {
        boolean error = false;
        Map<String, ArrayList<String>> resultMap = getEffectedVideos(esClient, lastHeight);
        ArrayList<String> itemIdList = resultMap.get("itemIdList");
        ArrayList<String> histIdList = resultMap.get("histIdList");

        if (itemIdList == null || itemIdList.isEmpty()) return error;
        log.warn("If rolling back is interrupted, reparse all effected ids of index 'video': ");
        JsonUtils.printJson(itemIdList);

        List<VideoHistory> reparseHistList = EsUtils.getHistsForReparse(esClient, IndicesNames.VIDEO_HISTORY, VIDEO_ID, VIDEO_IDS, itemIdList, lastHeight, VideoHistory.class);

        error |= deleteEffectedItems(esClient, IndicesNames.VIDEO, itemIdList);
        if (histIdList != null && !histIdList.isEmpty())
            error |= deleteRolledHists(esClient, IndicesNames.VIDEO_HISTORY, histIdList);

        error |= reparseVideo(esClient, reparseHistList);

        return error;
    }

    private Map<String, ArrayList<String>> getEffectedVideos(ElasticsearchClient esClient, long lastHeight) throws ElasticsearchException, IOException {
        List<Hit<VideoHistory>> effectedHits = EsUtils.scanHitsAboveHeight(esClient, IndicesNames.VIDEO_HISTORY, "height", lastHeight, VideoHistory.class);

        Set<String> itemSet = new HashSet<>();
        ArrayList<String> histList = new ArrayList<>();

        for (Hit<VideoHistory> hit : effectedHits) {
            VideoHistory item = hit.source();
            if(item==null){
                log.info("Video hist is null");
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

    private boolean reparseVideo(ElasticsearchClient esClient, List<VideoHistory> reparseHistList) {
    	PublishParser parser = new PublishParser();
    	return Reparser.replay("video", reparseHistList, h -> parser.parseVideo(esClient, h));
    }
}
