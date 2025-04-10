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

//    @Override
//    public void localTask() {
//        deleteExpiredFiles(sid);
//    }


}
