package startManager;

import feip.feipData.serviceParams.Params;
import fcData.DiskItem;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import constants.FieldNames;
import utils.FileUtils;
import server.Counter;
import appTools.Settings;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import static constants.Strings.DATA;

public class DiskCounter extends Counter {


    public DiskCounter(Settings settings, byte[] counterPriKey, Params params) {
        super(settings, counterPriKey, params);
    }

    @Override
    public void localTask() {
        deleteExpiredFiles(sid);
    }

    private static void deleteExpiredFiles(String sid) {
        Date date = new Date();
        SearchResponse<DiskItem> result;
        try {
            result = StartDiskManager.esClient.search(s -> s.index(Settings.addSidBriefToName(sid,DATA)).query(q -> q.range(r -> r.field(FieldNames.EXPIRE).lt(JsonData.of(date)))), DiskItem.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if(result==null || result.hits().hits().isEmpty()) return;

        for(Hit<DiskItem> hit:result.hits().hits()){
            DiskItem source = hit.source();
            if(source==null)continue;
            String did = source.getDid();
            String subDir = FileUtils.getSubPathForDisk(did);
            File file = new File(StartDiskManager.STORAGE_DIR+subDir,did);
            if(file.exists())file.delete();
        }
    }
}
