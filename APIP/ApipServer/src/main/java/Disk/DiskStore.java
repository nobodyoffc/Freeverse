package Disk;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import config.Settings;
import data.fcData.DiskItem;
import data.feipData.ServiceType;
import data.feipData.serviceParams.DiskParams;
import fapi.components.disk.FapiDiskHandler;
import initial.Initiator;

import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static constants.FieldNames.DISK_DIR;
import static constants.Values.DISK;

/**
 * Shared, lazily-initialized DISK storage for the ApipServer disk endpoints.
 *
 * <p>Mirrors {@code fapi.components.DiskComponent} initialization but for the HTTP
 * servlet context: it builds a single {@link FapiDiskHandler} (content-addressable
 * SHA256x2 storage + Elasticsearch metadata) and ensures the metadata index exists.
 * Deliberately does <b>not</b> use {@code managers.DiskManager}.
 */
public final class DiskStore {

    private static volatile FapiDiskHandler handler;
    private static volatile String indexName;
    private static volatile long defaultDataLifeDays = 30;

    private DiskStore() {
    }

    /** Returns the shared disk handler, initializing it on first use. */
    public static FapiDiskHandler handler() {
        FapiDiskHandler h = handler;
        if (h == null) {
            synchronized (DiskStore.class) {
                h = handler;
                if (h == null) {
                    h = init();
                    handler = h;
                }
            }
        }
        return h;
    }

    public static String indexName() {
        handler();
        return indexName;
    }

    public static long defaultDataLifeDays() {
        handler();
        return defaultDataLifeDays;
    }

    private static FapiDiskHandler init() {
        Settings settings = Initiator.settings;

        ElasticsearchClient esClient = (ElasticsearchClient) settings.getClient(ServiceType.ES);
        if (esClient == null) {
            throw new IllegalStateException("ElasticsearchClient is required for the DISK APIs");
        }

        indexName = Settings.addSidBriefToName(settings.getSid(), DISK);

        // Read default data-life-days from service params, if present.
        if (settings.getService() != null && settings.getService().getParams() != null) {
            DiskParams params = DiskParams.fromObject(settings.getService().getParams());
            if (params != null && params.getDataLifeDays() != null) {
                try {
                    defaultDataLifeDays = Long.parseLong(params.getDataLifeDays());
                } catch (NumberFormatException ignored) {
                }
            }
        }

        FapiDiskHandler diskHandler = new FapiDiskHandler(getStorageRoot(settings), esClient, indexName);

        checkOrCreateIndex(esClient, indexName);

        return diskHandler;
    }

    private static Path getStorageRoot(Settings settings) {
        Map<String, Object> settingMap = settings.getSettingMap();
        if (settingMap != null && settingMap.get(DISK_DIR) != null) {
            return Paths.get(settingMap.get(DISK_DIR).toString());
        }
        String dbDir = settings.getDbDir();
        if (dbDir != null) {
            return Paths.get(dbDir, "disk");
        }
        return Paths.get(System.getProperty("user.home"), ".apip", "disk");
    }

    private static void checkOrCreateIndex(ElasticsearchClient esClient, String indexName) {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(indexName)).value();
            if (!exists) {
                esClient.indices().create(c -> c
                        .index(indexName)
                        .withJson(new StringReader(DiskItem.MAPPINGS)));
            }
        } catch (Exception e) {
            // Index creation is best-effort; queries fall back gracefully if it is missing.
            System.out.println("Failed to check/create DISK index " + indexName + ": " + e.getMessage());
        }
    }
}
