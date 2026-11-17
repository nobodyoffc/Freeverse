package fapi.migrate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import data.fcData.DiskItem;
import fapi.components.disk.DiskMetaStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Moves one FAPI service (one SID) from one host to another.
 * <p>
 * The bundle holds everything small: config.json, the SID's settings, and the SID's LevelDB
 * stores (balances with their WAL and snapshots, DOCK, DISK metadata) plus the shared
 * map and DISK-sync state files. The DISK file folder is NOT in the bundle; it is copied with
 * rsync, and {@link #checkDisk} confirms afterwards that files and metadata agree.
 * <p>
 * Two marker files in dbDir stop the server from starting while a move is half done:
 * {@code <FID>_<SID>_MOVED.json} on the old host after export, and
 * {@code <FID>_<SID>_DISK_CHECK_PENDING.json} on the new host until the DISK check passes.
 */
public final class ServerMigration {

    public static final int FORMAT = 1;
    public static final String MANIFEST = "migration-manifest.json";
    public static final String CONFIG_JSON = "config.json";
    public static final String MOVED_SUFFIX = "_MOVED.json";
    public static final String DISK_PENDING_SUFFIX = "_DISK_CHECK_PENDING.json";
    public static final String CHAIN_PENDING_SUFFIX = "_CHAIN_CHECK_PENDING.json";
    public static final String DISK_DIR_KEY = constants.FieldNames.DISK_DIR;

    static final List<String> STORE_SUFFIXES = List.of("_fapi_balance", "_fapi_dock", "_fapi_disk_meta");
    static final List<String> SHARED_FILES = List.of("map_entries.json", "diskSyncState.json");

    private static final Pattern DID = Pattern.compile("[0-9a-f]{64}");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int SAMPLE = 10;

    private ServerMigration() {}

    // ------------------------------------------------------------------ source layout

    /** Where one SID keeps its state, read from its settings file. */
    public record Layout(String sid, String mainFid, Path confDir, Path dbDir, Path diskDir) {
        public Path settingsFile() { return confDir.resolve(settingsFileName(sid)); }
        public Path store(String suffix) { return dbDir.resolve(mainFid + "_" + sid + suffix); }
        public Path movedMarker() { return dbDir.resolve(mainFid + "_" + sid + MOVED_SUFFIX); }
        public Path diskPendingMarker() { return dbDir.resolve(mainFid + "_" + sid + DISK_PENDING_SUFFIX); }
        public Path chainPendingMarker() { return dbDir.resolve(mainFid + "_" + sid + CHAIN_PENDING_SUFFIX); }
    }

    public static String settingsFileName(String sid) {
        return sid + "_settings.json";
    }

    /** SIDs in confDir whose settings belong to a FAPI server. */
    public static List<String> listFapiSids(Path confDir) throws IOException {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(confDir)) return out;
        try (Stream<Path> files = Files.list(confDir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                String name = p.getFileName().toString();
                if (!name.endsWith("_settings.json")) continue;
                try {
                    JsonObject s = readJson(p);
                    if (str(s, "serverType") != null && str(s, "serverType").startsWith("FAPI") && str(s, "sid") != null)
                        out.add(str(s, "sid"));
                } catch (Exception ignored) {
                    // not a settings file we can read
                }
            }
        }
        return out;
    }

    public static Layout layout(Path confDir, String sid) throws IOException {
        Path file = confDir.resolve(settingsFileName(sid));
        if (!Files.exists(file)) throw new IOException("No settings file " + file);
        JsonObject s = readJson(file);
        String fid = str(s, "mainFid");
        if (fid == null) throw new IOException("Settings " + file + " has no mainFid");
        String dbDir = str(s, "dbDir");
        Path db = dbDir != null ? Path.of(dbDir) : confDir.toAbsolutePath().getParent().resolve("db");
        Path disk = db.resolve("disk");
        JsonObject map = s.has("settingMap") && s.get("settingMap").isJsonObject() ? s.getAsJsonObject("settingMap") : null;
        if (map != null && str(map, DISK_DIR_KEY) != null) disk = Path.of(str(map, DISK_DIR_KEY));
        return new Layout(sid, fid, confDir, db, disk);
    }

    // ------------------------------------------------------------------ start guard

    /**
     * Why this server must not start, or null when it may. Called by ServiceBootstrap before any
     * store is opened.
     */
    public static String startBlocker(String dbDir, String mainFid, String sid) {
        if (dbDir == null || mainFid == null || sid == null) return null;
        Path db = Path.of(dbDir);
        Path moved = db.resolve(mainFid + "_" + sid + MOVED_SUFFIX);
        if (Files.exists(moved)) {
            return "This service was exported to another host (" + moved + ").\n"
                    + "Starting it here too would split its balances. If the move was abandoned, delete that file and start again.";
        }
        Path pending = db.resolve(mainFid + "_" + sid + DISK_PENDING_SUFFIX);
        if (Files.exists(pending)) {
            return "The DISK files do not match the imported metadata yet (" + pending + ").\n"
                    + "Finish the rsync, then run: StartFapiServer --check-disk " + sid
                    + "\nTo start anyway, delete that file.";
        }
        return null;
    }

    // ------------------------------------------------------------------ export

    public record DiskStats(long files, long bytes) {}

    public record ExportResult(Path bundle, String bundleSha256, int entries, DiskStats disk, long metaCount, Path marker,
                               ChainPoint chain) {}

    /**
     * Pack the SID's state into {@code out}. Refuses while any store is open (the server is
     * running) or when the service was already exported from here.
     *
     * @param writeMarker write the MOVED marker so this host refuses to start the service again
     */
    public static ExportResult export(Layout src, Path out, boolean writeMarker) throws IOException {
        if (Files.exists(src.movedMarker()))
            throw new IOException("Already exported from this host: " + src.movedMarker());
        for (String suffix : STORE_SUFFIXES) ensureClosed(src.store(suffix));

        Path configJson = src.confDir().resolve(CONFIG_JSON);
        if (!Files.exists(configJson)) throw new IOException("No " + configJson);

        DiskStats disk = scanDisk(src.diskDir());
        long metaCount = -1;
        Path metaDir = src.store("_fapi_disk_meta");
        if (Files.isDirectory(metaDir)) {
            try (DiskMetaStore meta = DiskMetaStore.open(metaDir, Long.MAX_VALUE)) {
                metaCount = meta.count();
            }
        }

        ChainPoint chain = readBalanceChainPoint(src.store("_fapi_balance"));

        List<Map<String, Object>> files = new ArrayList<>();
        Files.createDirectories(out.toAbsolutePath().getParent());
        Path tmp = out.resolveSibling(out.getFileName() + ".part");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tmp))) {
            addFile(zip, "config/" + CONFIG_JSON, configJson, files);
            addFile(zip, "config/" + settingsFileName(src.sid()), src.settingsFile(), files);
            for (String suffix : STORE_SUFFIXES) {
                Path store = src.store(suffix);
                if (!Files.isDirectory(store)) continue;
                try (Stream<Path> walk = Files.walk(store)) {
                    for (Path p : (Iterable<Path>) walk.sorted()::iterator) {
                        if (!Files.isRegularFile(p) || p.getFileName().toString().equals("LOCK")) continue;
                        String rel = src.dbDir().relativize(p).toString().replace('\\', '/');
                        addFile(zip, "db/" + rel, p, files);
                    }
                }
            }
            for (String shared : SHARED_FILES) {
                Path p = src.dbDir().resolve(shared);
                if (Files.isRegularFile(p)) addFile(zip, "db/" + shared, p, files);
            }

            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("format", FORMAT);
            manifest.put("sid", src.sid());
            manifest.put("mainFid", src.mainFid());
            manifest.put("exportedAt", System.currentTimeMillis());
            manifest.put("sourceDbDir", src.dbDir().toString());
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("sourceDir", src.diskDir().toString());
            d.put("files", disk.files());
            d.put("bytes", disk.bytes());
            d.put("metaItems", metaCount);
            manifest.put("disk", d);
            if (chain != null) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("bestHeight", chain.height());
                c.put("bestBlockId", chain.blockId());
                manifest.put("chain", c);
            }
            manifest.put("files", files);
            zip.putNextEntry(new ZipEntry(MANIFEST));
            zip.write(GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        String bundleSha = sha256(out);

        Path marker = null;
        if (writeMarker) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("movedAt", System.currentTimeMillis());
            m.put("bundle", out.toAbsolutePath().toString());
            m.put("bundleSha256", bundleSha);
            marker = src.movedMarker();
            Files.writeString(marker, GSON.toJson(m));
        }
        return new ExportResult(out, bundleSha, files.size(), disk, metaCount, marker, chain);
    }

    /** The last block the balance store has seen. */
    public record ChainPoint(long height, String blockId) {}

    /**
     * Read the balance store's meta entry ({@code M/meta}, written by FapiBalanceManager on every
     * best-block update). Null when there is no store or it has seen no block.
     */
    static ChainPoint readBalanceChainPoint(Path store) throws IOException {
        if (!Files.isRegularFile(store.resolve("CURRENT"))) return null;   // not a LevelDB store
        try (org.iq80.leveldb.DB db = org.iq80.leveldb.impl.Iq80DBFactory.factory.open(store.toFile(),
                new org.iq80.leveldb.Options().createIfMissing(false))) {
            byte[] v = db.get("M/meta".getBytes(StandardCharsets.UTF_8));
            if (v == null) return null;
            JsonObject m = JsonParser.parseString(new String(v, StandardCharsets.UTF_8)).getAsJsonObject();
            long h = m.has("bestHeight") ? m.get("bestHeight").getAsLong() : 0;
            if (h <= 0) return null;
            return new ChainPoint(h, str(m, "bestBlockId"));
        }
    }

    /**
     * The block height the local chain index must reach before this imported service may start,
     * or null when no check is pending. A full server that started below it would read the lag
     * as a chain rollback and reverse recent recharges.
     */
    public static ChainPoint pendingChainCheck(String dbDir, String mainFid, String sid) {
        if (dbDir == null || mainFid == null || sid == null) return null;
        Path p = Path.of(dbDir).resolve(mainFid + "_" + sid + CHAIN_PENDING_SUFFIX);
        if (!Files.exists(p)) return null;
        try {
            JsonObject m = readJson(p);
            return new ChainPoint(m.get("bestHeight").getAsLong(), str(m, "bestBlockId"));
        } catch (Exception e) {
            return new ChainPoint(Long.MAX_VALUE, null);   // unreadable: keep blocking, the file says why
        }
    }

    public static void clearChainCheck(String dbDir, String mainFid, String sid) throws IOException {
        Files.deleteIfExists(Path.of(dbDir).resolve(mainFid + "_" + sid + CHAIN_PENDING_SUFFIX));
    }

    /** Throws when a LevelDB store is held open by a running server. */
    static void ensureClosed(Path store) throws IOException {
        Path lock = store.resolve("LOCK");
        if (!Files.exists(lock)) return;
        try (FileChannel ch = FileChannel.open(lock, StandardOpenOption.WRITE)) {
            FileLock l = ch.tryLock();
            if (l == null) throw new IOException(store + " is open; stop the server first");
            l.release();
        } catch (OverlappingFileLockException e) {
            throw new IOException(store + " is open; stop the server first");
        }
    }

    private static void addFile(ZipOutputStream zip, String name, Path file, List<Map<String, Object>> files) throws IOException {
        MessageDigest md = sha();
        long size = 0;
        zip.putNextEntry(new ZipEntry(name));
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
                zip.write(buf, 0, n);
                size += n;
            }
        }
        zip.closeEntry();
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("path", name);
        f.put("size", size);
        f.put("sha256", HexFormat.of().formatHex(md.digest()));
        files.add(f);
    }

    /** Count the stored DISK files; temp files of unfinished writes are skipped. */
    public static DiskStats scanDisk(Path diskDir) throws IOException {
        if (!Files.isDirectory(diskDir)) return new DiskStats(0, 0);
        long[] c = {0, 0};
        try (Stream<Path> walk = Files.walk(diskDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> DID.matcher(p.getFileName().toString()).matches())
                    .forEach(p -> {
                        c[0]++;
                        try { c[1] += Files.size(p); } catch (IOException ignored) { }
                    });
        }
        return new DiskStats(c[0], c[1]);
    }

    // ------------------------------------------------------------------ import

    public static final class ConfigConflictException extends IOException {
        public ConfigConflictException(String msg) { super(msg); }
    }

    public record ImportResult(Layout layout, int entries, Path configBackup, DiskCheck diskCheck, List<String> missingPaths,
                               ChainPoint chain) {}

    /** Settings files in confDir that belong to other programs or services than this SID. */
    public static List<String> otherSettingsFiles(Path confDir, String sid) throws IOException {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(confDir)) return out;
        try (Stream<Path> files = Files.list(confDir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                String n = p.getFileName().toString();
                if (n.endsWith("_settings.json") && !n.equals(settingsFileName(sid))) out.add(n);
            }
        }
        java.util.Collections.sort(out);
        return out;
    }

    /** Read and check the manifest of a bundle without extracting it. */
    public static JsonObject readManifest(Path bundle) throws IOException {
        try (ZipFile zip = new ZipFile(bundle.toFile())) {
            ZipEntry e = zip.getEntry(MANIFEST);
            if (e == null) throw new IOException("Not a FAPI migration bundle: no " + MANIFEST);
            try (InputStream in = zip.getInputStream(e)) {
                JsonObject m = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (!m.has("format") || m.get("format").getAsInt() != FORMAT)
                    throw new IOException("Unsupported bundle format " + m.get("format"));
                return m;
            }
        }
    }

    /**
     * Unpack a bundle onto this host.
     *
     * @param confDir        where config.json and the settings go (the server's {@code config/})
     * @param dbDir          the new dbDir; written into the settings
     * @param diskDir        where the DISK files are (or will be) on this host; written into the settings
     * @param replaceConfig  replace a different config.json already here (it is backed up first)
     */
    public static ImportResult importBundle(Path bundle, Path confDir, Path dbDir, Path diskDir, boolean replaceConfig) throws IOException {
        JsonObject manifest = readManifest(bundle);
        String sid = manifest.get("sid").getAsString();
        String fid = manifest.get("mainFid").getAsString();
        Layout dst = new Layout(sid, fid, confDir, dbDir, diskDir);

        Map<String, JsonObject> expected = new LinkedHashMap<>();
        for (JsonElement el : manifest.getAsJsonArray("files")) {
            JsonObject f = el.getAsJsonObject();
            String path = f.get("path").getAsString();
            checkEntryName(path);
            expected.put(path, f);
        }

        try (ZipFile zip = new ZipFile(bundle.toFile())) {
            // Pass 1: every listed file is there and intact, and nothing would be overwritten.
            for (Map.Entry<String, JsonObject> x : expected.entrySet()) {
                ZipEntry e = zip.getEntry(x.getKey());
                if (e == null) throw new IOException("Bundle is missing " + x.getKey());
                MessageDigest md = sha();
                try (InputStream in = zip.getInputStream(e)) {
                    byte[] buf = new byte[1 << 16];
                    int n;
                    while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
                }
                if (!HexFormat.of().formatHex(md.digest()).equals(x.getValue().get("sha256").getAsString()))
                    throw new IOException("Bundle is damaged: checksum mismatch on " + x.getKey());
            }
            for (String suffix : STORE_SUFFIXES) {
                Path store = dst.store(suffix);
                boolean inBundle = expected.keySet().stream().anyMatch(p -> p.startsWith("db/" + store.getFileName() + "/"));
                if (inBundle && Files.exists(store))
                    throw new IOException(store + " already exists here; move it away before importing");
            }
            if (Files.exists(dst.movedMarker()))
                throw new IOException("This host has a MOVED marker for the service: " + dst.movedMarker()
                        + ". Delete it first if you are moving the service back here.");

            Path configJson = confDir.resolve(CONFIG_JSON);
            byte[] bundledConfig;
            try (InputStream in = zip.getInputStream(zip.getEntry("config/" + CONFIG_JSON))) {
                bundledConfig = in.readAllBytes();
            }
            Path configBackup = null;
            boolean writeConfig = true;
            if (Files.exists(configJson)) {
                if (java.util.Arrays.equals(Files.readAllBytes(configJson), bundledConfig)) {
                    writeConfig = false;
                } else if (!replaceConfig) {
                    throw new ConfigConflictException(configJson + " already exists and differs from the bundled one");
                } else {
                    configBackup = backupPath(configJson);
                    Files.copy(configJson, configBackup);
                }
            }

            // Pass 2: extract.
            Files.createDirectories(confDir);
            Files.createDirectories(dbDir);
            Files.createDirectories(diskDir);   // the rsync target
            for (String name : expected.keySet()) {
                Path target;
                if (name.startsWith("config/")) {
                    if (name.equals("config/" + CONFIG_JSON) && !writeConfig) continue;
                    target = confDir.resolve(name.substring("config/".length()));
                } else {
                    target = dbDir.resolve(name.substring("db/".length()));
                }
                if (name.equals("config/" + settingsFileName(sid)) && Files.exists(target))
                    Files.copy(target, backupPath(target));
                Files.createDirectories(target.getParent());
                try (InputStream in = zip.getInputStream(zip.getEntry(name));
                     OutputStream os = Files.newOutputStream(target)) {
                    in.transferTo(os);
                }
            }

            // Point the settings at this host's folders.
            Path settings = dst.settingsFile();
            JsonObject s = readJson(settings);
            String db = dbDir.toAbsolutePath().toString();
            s.addProperty("dbDir", db.endsWith("/") ? db : db + "/");
            JsonObject map = s.has("settingMap") && s.get("settingMap").isJsonObject() ? s.getAsJsonObject("settingMap") : new JsonObject();
            map.addProperty(DISK_DIR_KEY, diskDir.toAbsolutePath().toString());
            s.add("settingMap", map);
            Files.writeString(settings, GSON.toJson(s));

            ChainPoint chain = null;
            if (manifest.has("chain") && manifest.get("chain").isJsonObject()) {
                JsonObject c = manifest.getAsJsonObject("chain");
                chain = new ChainPoint(c.get("bestHeight").getAsLong(), str(c, "bestBlockId"));
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("bestHeight", chain.height());
                m.put("bestBlockId", chain.blockId());
                m.put("why", "The balances were exported at this block. A full server may start only when its "
                        + "Elasticsearch has reached it, or it would read the lag as a rollback.");
                Files.writeString(dst.chainPendingMarker(), GSON.toJson(m));
            }

            DiskCheck check = checkDisk(dst);
            return new ImportResult(dst, expected.size(), configBackup, check, missingAbsolutePaths(s), chain);
        }
    }

    private static void checkEntryName(String name) throws IOException {
        if (!(name.startsWith("config/") || name.startsWith("db/")) || name.contains("..") || name.contains("\\")
                || name.startsWith("/"))
            throw new IOException("Unsafe path in bundle: " + name);
    }

    private static Path backupPath(Path p) {
        return p.resolveSibling(p.getFileName() + ".bak-" + System.currentTimeMillis());
    }

    /** Absolute paths left in the settings that do not exist on this host. */
    static List<String> missingAbsolutePaths(JsonElement e) {
        List<String> out = new ArrayList<>();
        collectPaths(e, out);
        return out;
    }

    private static void collectPaths(JsonElement e, List<String> out) {
        if (e == null || e.isJsonNull()) return;
        if (e.isJsonObject()) for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) {
            JsonElement v = x.getValue();
            if (v.isJsonPrimitive() && ((JsonPrimitive) v).isString()) {
                String s = v.getAsString();
                if (s.startsWith("/") && !s.contains("\n") && s.length() < 1024 && !Files.exists(Path.of(s)))
                    out.add(x.getKey() + " = " + s);
            } else collectPaths(v, out);
        }
        else if (e.isJsonArray()) for (JsonElement v : e.getAsJsonArray()) collectPaths(v, out);
    }

    // ------------------------------------------------------------------ DISK check

    /**
     * Files against metadata. {@code missing}: items whose file is not there (disk.get would
     * fail). {@code orphans}: files with no item (never listed or synced, wasted space).
     */
    public record DiskCheck(long metaItems, long files, long missing, long orphans,
                            List<String> missingSample, List<String> orphanSample) {
        public boolean clean() { return missing == 0 && orphans == 0; }
    }

    /**
     * Compare the DISK files with the metadata store, and set or clear the pending marker:
     * set while anything is missing, cleared once nothing is. Orphans alone do not block a start.
     */
    public static DiskCheck checkDisk(Layout l) throws IOException {
        Path metaDir = l.store("_fapi_disk_meta");
        DiskCheck c;
        if (!Files.isDirectory(metaDir)) {
            c = new DiskCheck(0, scanDisk(l.diskDir()).files(), 0, 0, List.of(), List.of());
        } else {
            ensureClosed(metaDir);
            try (DiskMetaStore meta = DiskMetaStore.open(metaDir, Long.MAX_VALUE)) {
                c = checkDisk(meta, l.diskDir());
            }
        }
        if (c.missing() > 0) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("checkedAt", System.currentTimeMillis());
            m.put("diskDir", l.diskDir().toString());
            m.put("missing", c.missing());
            m.put("missingSample", c.missingSample());
            Files.createDirectories(l.dbDir());
            Files.writeString(l.diskPendingMarker(), GSON.toJson(m));
        } else {
            Files.deleteIfExists(l.diskPendingMarker());
        }
        return c;
    }

    static DiskCheck checkDisk(DiskMetaStore meta, Path diskDir) throws IOException {
        long[] n = {0, 0};
        List<String> missingSample = new ArrayList<>();
        meta.forEach((DiskItem d) -> {
            n[0]++;
            String did = d.getId() == null ? "" : d.getId().toLowerCase();
            if (!DID.matcher(did).matches() || !Files.isRegularFile(didPath(diskDir, did))) {
                n[1]++;
                if (missingSample.size() < SAMPLE) missingSample.add(d.getId());
            }
        });
        long[] f = {0, 0};
        List<String> orphanSample = new ArrayList<>();
        if (Files.isDirectory(diskDir)) {
            try (Stream<Path> walk = Files.walk(diskDir)) {
                for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                    String name = p.getFileName().toString();
                    if (!DID.matcher(name).matches()) continue;
                    f[0]++;
                    if (meta.get(name) == null) {
                        f[1]++;
                        if (orphanSample.size() < SAMPLE) orphanSample.add(name);
                    }
                }
            }
        }
        return new DiskCheck(n[0], f[0], n[1], f[1], missingSample, orphanSample);
    }

    /** The layout FapiDiskHandler uses: four levels of two hex characters, then the DID. */
    static Path didPath(Path root, String did) {
        return root.resolve(did.substring(0, 2)).resolve(did.substring(2, 4))
                .resolve(did.substring(4, 6)).resolve(did.substring(6, 8)).resolve(did);
    }

    // ------------------------------------------------------------------ helpers

    /** The rsync commands for the DISK folder, with this host's path filled in. */
    public static List<String> rsyncCommands(Path diskDir, String target) {
        String src = diskDir.toAbsolutePath() + "/";
        String dst = target == null ? "NEW_HOST:" + src : (target.endsWith("/") ? target : target + "/");
        return List.of(
                "rsync -aH --partial --info=progress2 --exclude='*.tmp.*' " + src + " " + dst,
                "rsync -aH --delete --exclude='*.tmp.*' " + src + " " + dst);
    }

    private static JsonObject readJson(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? null : e.getAsString();
    }

    private static MessageDigest sha() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256(Path file) throws IOException {
        MessageDigest md = sha();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }
}
