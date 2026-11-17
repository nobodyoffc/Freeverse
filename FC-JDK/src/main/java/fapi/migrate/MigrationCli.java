package fapi.migrate;

import com.google.gson.JsonObject;
import ui.Inputer;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Console front end of {@link ServerMigration}, run instead of starting the server:
 * <pre>
 *   StartFapiServer --export [SID]          on the old host, server stopped
 *   StartFapiServer --import BUNDLE.zip     on the new host
 *   StartFapiServer --check-disk [SID]      on the new host, after the final rsync
 * </pre>
 * Paths are relative to the working directory, as the server's are ({@code config/}, {@code db/}).
 */
public final class MigrationCli {

    private MigrationCli() {}

    /** @return true when args asked for a migration command (which has then run) */
    public static boolean run(String[] args, BufferedReader br) {
        int i = indexOf(args, "--export");
        try {
            if (i >= 0) { export(arg(args, i + 1), br); return true; }
            i = indexOf(args, "--import");
            if (i >= 0) { importBundle(arg(args, i + 1), br); return true; }
            i = indexOf(args, "--check-disk");
            if (i >= 0) { checkDisk(arg(args, i + 1), br); return true; }
        } catch (IOException e) {
            System.out.println("\nStopped: " + e.getMessage());
            return true;
        }
        return false;
    }

    static Path workDir() {
        return Path.of(System.getProperty("user.dir"));
    }

    static Path confDir() {
        return workDir().resolve("config");
    }

    // ------------------------------------------------------------------ export

    static void export(String sidArg, BufferedReader br) throws IOException {
        System.out.println("\n=== Export FAPI migration bundle ===");
        String sid = chooseSid(sidArg, br);
        if (sid == null) return;
        ServerMigration.Layout l = ServerMigration.layout(confDir(), sid);
        System.out.println("Service SID : " + sid);
        System.out.println("Server FID  : " + l.mainFid());
        System.out.println("dbDir       : " + l.dbDir());
        System.out.println("DISK folder : " + l.diskDir() + "  (not in the bundle)");
        ServerMigration.DiskStats disk = ServerMigration.scanDisk(l.diskDir());
        if (disk.files() > 0) {
            System.out.println("\nDISK holds " + disk.files() + " files (" + human(disk.bytes()) + ").");
            System.out.println("Copy them to the new host BEFORE the move, while this server still serves:");
            System.out.println("  " + ServerMigration.rsyncCommands(l.diskDir(), null).get(0));
            System.out.println("Re-run it until it finishes with little left to send. Then stop the server and export.");
            if (!Inputer.askIfYes(br, "Has the bulk DISK copy finished?")) {
                System.out.println("Nothing exported. Run --export again when the copy is done.");
                return;
            }
        }
        System.out.println("\nThe server must be stopped. After export, this host will refuse to start the service again.");
        if (!Inputer.askIfYes(br, "Export now?")) return;

        String stamp = new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date());
        Path out = workDir().resolve("fapi-migration-" + sid.substring(0, Math.min(8, sid.length())) + "-" + stamp + ".zip");
        ServerMigration.ExportResult r = ServerMigration.export(l, out, true);

        System.out.println("\nBundle written: " + r.bundle());
        System.out.println("  files       : " + r.entries());
        System.out.println("  sha256      : " + r.bundleSha256());
        System.out.println("  DISK files  : " + r.disk().files() + " (" + human(r.disk().bytes()) + "), metadata items: "
                + (r.metaCount() < 0 ? "no DISK store" : r.metaCount()));
        System.out.println("  marker      : " + r.marker());
        if (r.chain() != null)
            System.out.println("  chain       : balances at block " + r.chain().height()
                    + "; a full server on the new host starts only when its parsers reach it");
        System.out.println("\nNext:");
        System.out.println("  1. Final DISK pass (the server is stopped now, so this is the last one):");
        List<String> cmds = ServerMigration.rsyncCommands(l.diskDir(), null);
        System.out.println("     " + cmds.get(1));
        System.out.println("     (replace NEW_HOST:path with the new host and its DISK folder)");
        System.out.println("  2. Copy the bundle to the new host and run there: StartFapiServer --import " + r.bundle().getFileName());
        System.out.println("  3. Keep the bundle private: it holds the encrypted keys and the balances.");
    }

    // ------------------------------------------------------------------ import

    static void importBundle(String bundleArg, BufferedReader br) throws IOException {
        System.out.println("\n=== Import FAPI migration bundle ===");
        if (bundleArg == null) bundleArg = Inputer.inputString(br, "Path of the bundle (.zip):");
        if (bundleArg == null || bundleArg.isBlank()) return;
        Path bundle = Path.of(bundleArg.trim());
        JsonObject m = ServerMigration.readManifest(bundle);
        JsonObject disk = m.getAsJsonObject("disk");
        String sid = m.get("sid").getAsString();
        System.out.println("Service SID : " + sid);
        System.out.println("Server FID  : " + m.get("mainFid").getAsString());
        System.out.println("Exported at : " + new Date(m.get("exportedAt").getAsLong()));
        System.out.println("DISK on old host: " + disk.get("files").getAsLong() + " files, "
                + human(disk.get("bytes").getAsLong()) + ", in " + disk.get("sourceDir").getAsString());

        List<String> others = ServerMigration.otherSettingsFiles(confDir(), sid);
        if (!others.isEmpty()) {
            System.out.println("\nWARNING: " + confDir() + " is used by other programs: " + String.join(", ", others));
            System.out.println("FchParser, FeipParser and other servers read config/config.json from their working folder too.");
            System.out.println("Importing here can replace that config.json with the FAPI one and break them.");
            System.out.println("Recommended: make a separate folder for the FAPI server, copy the bundle there, cd into it, and import.");
            if (!Inputer.askIfYes(br, "Import into this shared folder anyway?")) return;
        }

        if (m.has("chain")) {
            long h = m.getAsJsonObject("chain").get("bestHeight").getAsLong();
            System.out.println("\nThe balances were exported at block " + h + ".");
            System.out.println("If this is a full server (with BASE), FchParser and FeipParser on this host must finish");
            System.out.println("parsing before the FAPI server starts; it refuses to start below block " + h + ".");
        }

        String defDb = workDir().resolve("db").toString();
        String db = ask(br, "dbDir on this host", defDb);
        if (db == null) return;
        String source = disk.get("sourceDir").getAsString();
        String defDisk = Files.isDirectory(Path.of(source)) ? source : Path.of(db).resolve("disk").toString();
        String diskDir = ask(br, "DISK folder on this host (where rsync puts the files)", defDisk);
        if (diskDir == null) return;

        ServerMigration.ImportResult r;
        try {
            r = ServerMigration.importBundle(bundle, confDir(), Path.of(db), Path.of(diskDir), false);
        } catch (ServerMigration.ConfigConflictException e) {
            System.out.println("\n" + e.getMessage() + ".");
            System.out.println("If parsers or other servers run from this folder, that config.json is theirs: do not replace it.");
            System.out.println("Answer no, then import from a separate folder for the FAPI server.");
            System.out.println("The bundled config.json holds the key of this server's FID; without it the server cannot start.");
            if (!Inputer.askIfYes(br, "Replace it? The current one is kept as a .bak file.")) return;
            r = ServerMigration.importBundle(bundle, confDir(), Path.of(db), Path.of(diskDir), true);
        }

        System.out.println("\nImported " + r.entries() + " files.");
        if (r.configBackup() != null) System.out.println("Old config.json saved as " + r.configBackup());
        System.out.println("Settings now point at dbDir=" + r.layout().dbDir() + ", diskDir=" + r.layout().diskDir());
        if (!r.missingPaths().isEmpty()) {
            System.out.println("\nThese paths in the settings do not exist here; change them in Settings if the server needs them:");
            for (String p : r.missingPaths()) System.out.println("  " + p);
        }
        if (r.chain() != null)
            System.out.println("\nChain check pending: a full server starts only when this host's chain index reaches block "
                    + r.chain().height() + ".");
        printCheck(r.diskCheck(), r.layout());
    }

    // ------------------------------------------------------------------ check

    static void checkDisk(String sidArg, BufferedReader br) throws IOException {
        System.out.println("\n=== Check DISK files against metadata ===");
        String sid = chooseSid(sidArg, br);
        if (sid == null) return;
        ServerMigration.Layout l = ServerMigration.layout(confDir(), sid);
        System.out.println("DISK folder: " + l.diskDir());
        printCheck(ServerMigration.checkDisk(l), l);
    }

    static void printCheck(ServerMigration.DiskCheck c, ServerMigration.Layout l) {
        System.out.println("\nDISK check: " + c.metaItems() + " metadata items, " + c.files() + " files");
        System.out.println("  missing files (item, no file): " + c.missing());
        for (String d : c.missingSample()) System.out.println("    " + d);
        System.out.println("  orphan files  (file, no item): " + c.orphans());
        for (String d : c.orphanSample()) System.out.println("    " + d);
        if (c.missing() > 0) {
            System.out.println("\nThe server will not start until no file is missing.");
            System.out.println("Finish the rsync from the old host into " + l.diskDir() + "/, then run:");
            System.out.println("  StartFapiServer --check-disk " + l.sid());
        } else {
            System.out.println("\nOK: the server can start. Then publish the new apiUrl with 'Update services' (or change DNS).");
            if (c.orphans() > 0)
                System.out.println("Orphan files do no harm; they were left by deletes after the bulk copy. rsync --delete removes them.");
        }
    }

    // ------------------------------------------------------------------ helpers

    static String chooseSid(String sidArg, BufferedReader br) throws IOException {
        if (sidArg != null) return sidArg;
        List<String> sids = ServerMigration.listFapiSids(confDir());
        if (sids.isEmpty()) {
            System.out.println("No FAPI service settings in " + confDir());
            return null;
        }
        if (sids.size() == 1) return sids.get(0);
        for (int k = 0; k < sids.size(); k++) System.out.println("  " + (k + 1) + ". " + sids.get(k));
        String in = Inputer.inputString(br, "Choose the service (1-" + sids.size() + "):");
        try {
            int n = Integer.parseInt(in == null ? "" : in.trim());
            return n >= 1 && n <= sids.size() ? sids.get(n - 1) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String ask(BufferedReader br, String what, String def) {
        String v = Inputer.inputString(br, what, def);
        return v == null ? null : v.trim();
    }

    private static int indexOf(String[] args, String flag) {
        for (int i = 0; i < args.length; i++) if (flag.equals(args[i])) return i;
        return -1;
    }

    private static String arg(String[] args, int i) {
        return i < args.length && !args[i].startsWith("--") ? args[i] : null;
    }

    static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] u = {"KB", "MB", "GB", "TB"};
        double v = bytes;
        int k = -1;
        while (v >= 1024 && k < u.length - 1) { v /= 1024; k++; }
        return String.format("%.1f %s", v, u[k]);
    }
}
