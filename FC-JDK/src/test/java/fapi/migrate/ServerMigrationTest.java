package fapi.migrate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import data.fcData.DiskItem;
import fapi.components.disk.DiskMetaStore;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** A whole move: export on one host, rsync DISK by hand, import and check on the other. */
class ServerMigrationTest {

    static final String SID = "3b701e4efac842bb53ad2552be4dc4f1b7c76c6d68fc5fb632012a57295167c3";
    static final String FID = "FTxMo6wbWR9G9Poac84d4jHaVUNfViALVD";

    @TempDir
    Path tmp;
    Path oldConf, oldDb, oldDisk, newConf, newDb, newDisk;

    static String did(int i) {
        return String.format("%064x", i + 0xabcdef);
    }

    @BeforeEach
    void oldHost() throws Exception {
        Path old = tmp.resolve("old");
        oldConf = old.resolve("config");
        oldDb = old.resolve("db");
        oldDisk = old.resolve("diskData");
        Files.createDirectories(oldConf);
        Files.writeString(oldConf.resolve("config.json"), "{\"keys\":\"encrypted\"}");
        Files.writeString(oldConf.resolve(SID + "_settings.json"),
                "{\"serverType\":\"FAPI_No1_NrC7\",\"sid\":\"" + SID + "\",\"mainFid\":\"" + FID + "\","
                        + "\"dbDir\":\"" + oldDb + "/\",\"opReturnPath\":\"/nowhere/opreturn\","
                        + "\"settingMap\":{\"creditLimit\":10000.0,\"diskDir\":\"" + oldDisk + "\"}}");
        // other services' settings are not FAPI and are left alone
        Files.writeString(oldConf.resolve("FEIP_settings.json"), "{\"serverType\":\"FEIP\"}");

        Path balance = oldDb.resolve(FID + "_" + SID + "_fapi_balance");
        Files.createDirectories(balance);
        try (DB db = Iq80DBFactory.factory.open(balance.toFile(), new Options().createIfMissing(true))) {
            db.put("M/meta".getBytes(StandardCharsets.UTF_8),
                    "{\"bestHeight\":3193392,\"bestBlockId\":\"00000000abc\"}".getBytes(StandardCharsets.UTF_8));
            db.put("B/FUser".getBytes(StandardCharsets.UTF_8), "5000".getBytes(StandardCharsets.UTF_8));
        }
        Files.createDirectories(balance.resolve("wal"));
        Files.writeString(balance.resolve("wal").resolve("wal-1.log"), "wal");
        Files.writeString(oldDb.resolve("diskSyncState.json"), "{}");
        // another SID's store must not travel
        Files.createDirectories(oldDb.resolve(FID + "_other_fapi_balance"));
        Files.writeString(oldDb.resolve(FID + "_other_fapi_balance").resolve("x"), "other");

        try (DiskMetaStore meta = DiskMetaStore.open(oldDb.resolve(FID + "_" + SID + "_fapi_disk_meta"), 100_000)) {
            for (int i = 0; i < 5; i++) {
                meta.put(new DiskItem(did(i), 1000L + i, null, 4L));
                Path f = ServerMigration.didPath(oldDisk, did(i));
                Files.createDirectories(f.getParent());
                Files.writeString(f, "data");
            }
        }
        Path half = ServerMigration.didPath(oldDisk, did(9));
        Files.createDirectories(half.getParent());
        Files.writeString(half.resolveSibling(did(9) + ".tmp.1234abcd"), "half-written");

        Path neu = tmp.resolve("new");
        newConf = neu.resolve("config");
        newDb = neu.resolve("data/db");
        newDisk = neu.resolve("bigdisk");
    }

    ServerMigration.ExportResult export() throws IOException {
        return ServerMigration.export(ServerMigration.layout(oldConf, SID), tmp.resolve("bundle.zip"), true);
    }

    @Test
    void listsOnlyFapiServices() throws Exception {
        assertEquals(List.of(SID), ServerMigration.listFapiSids(oldConf));
    }

    @Test
    void exportPacksStateButNotDiskAndLocksOldHost() throws Exception {
        ServerMigration.ExportResult r = export();
        assertEquals(5, r.disk().files());   // the .tmp file is not counted
        assertEquals(5, r.metaCount());

        try (ZipFile z = new ZipFile(r.bundle().toFile())) {
            List<String> names = z.stream().map(ZipEntry::getName).toList();
            assertTrue(names.contains("config/config.json"));
            assertTrue(names.contains("config/" + SID + "_settings.json"));
            assertTrue(names.contains("db/" + FID + "_" + SID + "_fapi_balance/wal/wal-1.log"));
            assertTrue(names.contains("db/diskSyncState.json"));
            assertTrue(names.stream().anyMatch(n -> n.startsWith("db/" + FID + "_" + SID + "_fapi_disk_meta/")));
            assertFalse(names.stream().anyMatch(n -> n.endsWith("/LOCK")));
            assertFalse(names.stream().anyMatch(n -> n.contains("_other_")));
            assertFalse(names.stream().anyMatch(n -> n.contains(did(0))), "DISK files stay out of the bundle");
            assertFalse(names.contains("config/FEIP_settings.json"));
        }

        String blocker = ServerMigration.startBlocker(oldDb + "/", FID, SID);
        assertNotNull(blocker);
        assertTrue(blocker.contains("exported"));
        IOException again = assertThrows(IOException.class, this::export);
        assertTrue(again.getMessage().contains("Already exported"));
    }

    @Test
    void exportRefusesWhileServerHasAStoreOpen() throws Exception {
        try (DiskMetaStore open = DiskMetaStore.open(oldDb.resolve(FID + "_" + SID + "_fapi_disk_meta"), 100_000)) {
            IOException e = assertThrows(IOException.class, this::export);
            assertTrue(e.getMessage().contains("stop the server"));
        }
        assertFalse(Files.exists(ServerMigration.layout(oldConf, SID).movedMarker()));
    }

    @Test
    void importBlocksUntilRsyncThenStarts() throws Exception {
        Path bundle = export().bundle();

        ServerMigration.ImportResult r = ServerMigration.importBundle(bundle, newConf, newDb, newDisk, false);
        assertEquals("encrypted", JsonParser.parseString(Files.readString(newConf.resolve("config.json")))
                .getAsJsonObject().get("keys").getAsString());
        assertEquals("wal", Files.readString(newDb.resolve(FID + "_" + SID + "_fapi_balance/wal/wal-1.log")));
        try (DB db = Iq80DBFactory.factory.open(newDb.resolve(FID + "_" + SID + "_fapi_balance").toFile(), new Options())) {
            assertEquals("5000", new String(db.get("B/FUser".getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8),
                    "the imported balance store opens and holds the balances");
        }

        JsonObject s = JsonParser.parseString(Files.readString(newConf.resolve(SID + "_settings.json"))).getAsJsonObject();
        assertEquals(newDb.toAbsolutePath() + "/", s.get("dbDir").getAsString());
        assertEquals(newDisk.toAbsolutePath().toString(), s.getAsJsonObject("settingMap").get("diskDir").getAsString());
        assertEquals(10000.0, s.getAsJsonObject("settingMap").get("creditLimit").getAsDouble());
        assertTrue(r.missingPaths().stream().anyMatch(p -> p.startsWith("opReturnPath")));

        // no DISK files yet: every item is missing and the server may not start
        assertEquals(5, r.diskCheck().missing());
        assertNotNull(ServerMigration.startBlocker(newDb.toString(), FID, SID));

        // "rsync", plus one stray file the old host had deleted metadata for
        copyTree(oldDisk, newDisk);
        Path stray = ServerMigration.didPath(newDisk, did(7));
        Files.createDirectories(stray.getParent());
        Files.writeString(stray, "stray");

        ServerMigration.DiskCheck c = ServerMigration.checkDisk(ServerMigration.layout(newConf, SID));
        assertEquals(0, c.missing());
        assertEquals(1, c.orphans());
        assertEquals(List.of(did(7)), c.orphanSample());
        assertNull(ServerMigration.startBlocker(newDb.toString(), FID, SID), "orphans alone do not block a start");
    }

    @Test
    void importRefusesDamagedBundle() throws Exception {
        Path bundle = export().bundle();
        Path bad = tmp.resolve("bad.zip");
        try (ZipFile in = new ZipFile(bundle.toFile()); ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(bad))) {
            for (Enumeration<? extends ZipEntry> e = in.entries(); e.hasMoreElements(); ) {
                ZipEntry z = e.nextElement();
                out.putNextEntry(new ZipEntry(z.getName()));
                byte[] b;
                try (InputStream is = in.getInputStream(z)) { b = is.readAllBytes(); }
                if (z.getName().endsWith("wal-1.log")) b = "wal+1000000".getBytes(StandardCharsets.UTF_8);
                out.write(b);
                out.closeEntry();
            }
        }
        IOException e = assertThrows(IOException.class,
                () -> ServerMigration.importBundle(bad, newConf, newDb, newDisk, false));
        assertTrue(e.getMessage().contains("checksum"));
        assertFalse(Files.exists(newConf.resolve(SID + "_settings.json")), "nothing extracted from a damaged bundle");
    }

    @Test
    void importAsksBeforeReplacingADifferentConfig() throws Exception {
        Path bundle = export().bundle();
        Files.createDirectories(newConf);
        Files.writeString(newConf.resolve("config.json"), "{\"keys\":\"someone else\"}");
        assertThrows(ServerMigration.ConfigConflictException.class,
                () -> ServerMigration.importBundle(bundle, newConf, newDb, newDisk, false));

        ServerMigration.ImportResult r = ServerMigration.importBundle(bundle, newConf, newDb, newDisk, true);
        assertNotNull(r.configBackup());
        assertEquals("{\"keys\":\"someone else\"}", Files.readString(r.configBackup()));
    }

    @Test
    void importNeverOverwritesExistingStores() throws Exception {
        Path bundle = export().bundle();
        Files.createDirectories(newDb.resolve(FID + "_" + SID + "_fapi_balance"));
        IOException e = assertThrows(IOException.class,
                () -> ServerMigration.importBundle(bundle, newConf, newDb, newDisk, false));
        assertTrue(e.getMessage().contains("already exists"));
    }

    @Test
    void exportRecordsChainHeightAndImportWaitsForIt() throws Exception {
        ServerMigration.ExportResult r = export();
        assertEquals(new ServerMigration.ChainPoint(3193392, "00000000abc"), r.chain());

        ServerMigration.ImportResult in = ServerMigration.importBundle(r.bundle(), newConf, newDb, newDisk, false);
        assertEquals(3193392, in.chain().height());
        ServerMigration.ChainPoint need = ServerMigration.pendingChainCheck(newDb.toString(), FID, SID);
        assertEquals(3193392, need.height());

        ServerMigration.clearChainCheck(newDb.toString(), FID, SID);
        assertNull(ServerMigration.pendingChainCheck(newDb.toString(), FID, SID));
    }

    @Test
    void lightServerStartClearsTheChainCheck() throws Exception {
        ServerMigration.importBundle(export().bundle(), newConf, newDb, newDisk, false);
        config.Settings settings = org.mockito.Mockito.mock(config.Settings.class);
        org.mockito.Mockito.when(settings.getDbDir()).thenReturn(newDb.toString());
        org.mockito.Mockito.when(settings.getMainFid()).thenReturn(FID);
        org.mockito.Mockito.when(settings.getClient(data.feipData.ServiceType.ES)).thenReturn(null);
        assertNull(fapi.ServiceBootstrapAccess.checkImportedChainHeight(settings, SID));
        assertNull(ServerMigration.pendingChainCheck(newDb.toString(), FID, SID));
    }

    @Test
    void sharedFolderIsDetected() throws Exception {
        assertEquals(List.of("FEIP_settings.json"), ServerMigration.otherSettingsFiles(oldConf, SID));
    }

    static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                Path t = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(t);
                else if (!p.getFileName().toString().contains(".tmp.")) {
                    try (InputStream in = Files.newInputStream(p); OutputStream o = Files.newOutputStream(t)) {
                        in.transferTo(o);
                    }
                }
            }
        }
    }
}
