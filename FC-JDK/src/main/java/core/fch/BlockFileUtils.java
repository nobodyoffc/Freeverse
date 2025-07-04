package core.fch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import constants.IndicesNames;
import data.fchData.Block;
import java.io.File;
import java.io.IOException;

public class BlockFileUtils {

    public static int getFileOrder(String currentFile) {
        String s = String.copyValueOf(currentFile.toCharArray(), 3, 5);
        return Integer.parseInt(s);
    }

    public static String getLastBlockFileName(String blockFilePath) {
        for (int i = 0; ; i++) {
            String blockFileName = getFileNameWithOrder(i);
            File file = new File(blockFilePath + blockFileName);
            if (!file.exists()) {
                if (i > 0) {
                    return getFileNameWithOrder(i - 1);
                }
            }
        }
    }

    public static String getFileNameWithOrder(int i) {
        return "blk" + String.format("%05d", i) + ".dat";
    }

    public static String getNextFile(String currentFile) {
        return getFileNameWithOrder(getFileOrder(currentFile) + 1);
    }

    public static Block getBlockByHeight(ElasticsearchClient esClient, long height) throws IOException {
        if (esClient == null) {
            System.out.println("Failed to check rollback. Start a ES client first.");
            return null;
        }
        SearchResponse<Block> result = esClient.search(s -> s
                .index(IndicesNames.BLOCK)
                .query(q -> q
                        .term(t -> t.field("height").value(height))), Block.class);
        if(!result.hits().hits().isEmpty())return result.hits().hits().get(0).source();
        else return null;
    }
}
