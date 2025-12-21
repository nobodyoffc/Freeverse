package news;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.JsonData;
import constants.IndicesNames;

import java.util.ArrayList;
import java.util.List;

import static constants.FieldNames.HEIGHT;

public class NewsRollbacker {

    /**
     * Rollback news entries above a certain height
     * News has no its own parser because it is always created in other's parser
     * But its rollbacker is needed to clean up news entries during blockchain reorganizations
     */
    public boolean rollback(ElasticsearchClient esClient, long lastHeight) throws Exception {
        return rollbackNews(esClient, lastHeight);
    }

    private boolean rollbackNews(ElasticsearchClient esClient, long lastHeight) throws Exception {
        List<String> indexList = new ArrayList<>();
        indexList.add(IndicesNames.NEWS);

        // Delete all news entries with height greater than lastHeight
        // Set conflicts=proceed to ignore version conflicts during deletion
        esClient.deleteByQuery(d -> d
                .index(indexList)
                .conflicts(co.elastic.clients.elasticsearch._types.Conflicts.Proceed)
                .query(q -> q
                        .range(r -> r
                                .field(HEIGHT)
                                .gt(JsonData.of(lastHeight)))));

        System.out.println("Rolled back news entries above height: " + lastHeight);
        return false;
    }
}
