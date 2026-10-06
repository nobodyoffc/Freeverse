package Disk;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import config.Settings;
import data.fcData.DiskItem;
import data.feipData.ServiceType;
import data.feipData.serviceParams.DiskParams;
import fapi.components.DiskComponent;
import fapi.components.disk.DiskMetaStore;
import fapi.components.disk.FapiDiskHandler;
import fapi.migrate.EsIndexPages;
import initial.Initiator;

import java.io.IOException;
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
 * SHA256x2 storage + LevelDB metadata). On first start the metadata is copied once from
 * the Elasticsearch index that held it before.
 * Deliberately does <b>not</b> use {@code managers.DiskManager}.
 */
public final class DiskStore {

    private static final long DEFAULT_MAX_EXAMINED = 100_000;

    private static volatile FapiDiskHandler handler;
    private static volatile DiskMetaStore metaStore;
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

    public static DiskMetaStore metaStore() {
        handler();
        return metaStore;
    }

    public static long defaultDataLifeDays() {
        handler();
        return defaultDataLifeDays;
    }

    /** Close the metadata store so a redeployed webapp can open it again. */
    public static synchronized void close() {
        DiskMetaStore store = metaStore;
        handler = null;
        metaStore = null;
        if (store != null) {
            try {
                store.close();
            } catch (IOException e) {
                System.out.println("Failed to close DISK metadata store: " + e.getMessage());
            }
        }
    }

    private static FapiDiskHandler init() {
        Settings settings = Initiator.settings;

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

        metaStore = openMetaStore(settings);
        return new FapiDiskHandler(getStorageRoot(settings), metaStore);
    }

    /**
     * Open the metadata store, and on first start copy in the Elasticsearch index that held
     * the metadata before. A server without ES has nothing to copy.
     */
    private static DiskMetaStore openMetaStore(Settings settings) {
        long maxExamined = DEFAULT_MAX_EXAMINED;
        Map<String, Object> settingMap = settings.getSettingMap();
        if (settingMap != null && settingMap.get(DiskComponent.KEY_DISK_MAX_EXAMINED) instanceof Number n)
            maxExamined = n.longValue();

        Path dir = getMetaDir(settings);
        DiskMetaStore store;
        try {
            store = DiskMetaStore.open(dir, maxExamined);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot open DISK metadata store at " + dir + ": " + e.getMessage(), e);
        }
        if (store.isMigrated()) return store;
        if (!(settings.getClient(ServiceType.ES) instanceof ElasticsearchClient esClient)) {
            store.markMigrated();
            return store;
        }
        String indexName = Settings.addSidBriefToName(settings.getSid(), DISK);
        try {
            long n = store.migrateFrom(new EsIndexPages<>(esClient, indexName, DiskItem.class));
            System.out.println("DISK: migrated " + n + " metadata items from Elasticsearch index " + indexName + " to " + dir);
        } catch (Exception e) {
            // Not marked: the next start tries again. New files are recorded meanwhile.
            System.out.println("DISK: migration from Elasticsearch index " + indexName
                    + " failed; will retry at next start: " + e.getMessage());
        }
        return store;
    }

    /** Apart from the FAPI server's store, so both can run with one SID on one host. */
    private static Path getMetaDir(Settings settings) {
        String dbDir = settings.getDbDir();
        if (dbDir != null) {
            return Paths.get(dbDir, settings.getMainFid() + "_" + settings.getSid() + "_apip_disk_meta");
        }
        return Paths.get(System.getProperty("user.home"), ".apip", "disk_meta");
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
}
