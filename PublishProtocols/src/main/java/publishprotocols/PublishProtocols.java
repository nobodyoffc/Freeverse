package publishprotocols;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import core.crypto.Hash;
import data.feipData.Feip;
import data.feipData.Feip.FeipProtocol;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * CLI: builds FEIP1 Protocol {@code publish} OP_RETURN JSON objects from protocol markdown.
 * Default: one minified JSON object per line; {@code --pretty} writes indented multi-line objects.
 */
public final class PublishProtocols {

    private static final int MAX_PROTOCOL_JSON_UTF8_BYTES = 4092;

    /** Compact serialization (default output). */
    private static final Gson GSON_COMPACT = new GsonBuilder().disableHtmlEscaping().create();

    private static final Gson GSON_PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public static void main(String[] args) {
        try {
            run(args);
        } catch (CliException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
    }

    public static void run(String[] args) throws IOException {
        Args a = Args.parse(args);
        List<Path> mdFiles = listMarkdownFiles(a.folder, a.recursive);
        if (mdFiles.isEmpty()) {
            throw new CliException("No .md files found under " + a.folder);
        }
        List<ParsedDoc> parsed = new ArrayList<>();
        for (Path path : mdFiles) {
            parsed.add(parseFile(path, a.lang));
        }
        parsed.sort(Comparator
                .comparingInt(ParsedDoc::sortSn)
                .thenComparing(ParsedDoc::sortVerNum)
                .thenComparing(p -> p.verRaw, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(p -> p.path.toString()));

        try (BufferedWriter w = Files.newBufferedWriter(a.output, StandardCharsets.UTF_8)) {
            for (ParsedDoc doc : parsed) {
                Feip feip = Feip.fromProtocolName(FeipProtocol.PROTOCOL);
                feip.setData(doc.publishData);
                String toWrite = a.pretty ? GSON_PRETTY.toJson(feip) : GSON_COMPACT.toJson(feip);
                int utf8Bytes = toWrite.getBytes(StandardCharsets.UTF_8).length;
                if (utf8Bytes > MAX_PROTOCOL_JSON_UTF8_BYTES) {
                    Object name = doc.publishData.get("name");
                    System.err.println("ALERT: Protocol JSON UTF-8 size " + utf8Bytes + " exceeds "
                            + MAX_PROTOCOL_JSON_UTF8_BYTES + " bytes - file: " + doc.path
                            + (name != null ? " (name: " + name + ")" : ""));
                }
                w.write(toWrite);
                w.newLine();
            }
        }
    }

    private static ParsedDoc parseFile(Path path, String lang) throws IOException {
        String markdown = Files.readString(path, StandardCharsets.UTF_8);
        Map<String, String> table = SummaryTableParser.parse(markdown);
        String title = getRequired(table, "Title", path);
        String type = getRequired(table, "Type", path);
        String snStr = getRequired(table, "SN", path);
        String ver = getRequiredEither(table, path, "Ver", "Version");
        int sn;
        try {
            sn = Integer.parseInt(snStr);
        } catch (NumberFormatException e) {
            throw new CliException("Invalid integer SN '" + snStr + "' in " + path);
        }
        String did = Hash.sha256x2(path.toFile());
        if (did == null || did.isEmpty()) {
            throw new CliException("Failed to compute did for " + path);
        }
        String desc = AbstractExtractor.extract(markdown);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("op", "publish");
        data.put("sn", snStr);
        data.put("name", title);
        data.put("type", type);
        data.put("ver", ver);
        data.put("did", did);
        if (!desc.isEmpty()) {
            data.put("desc", desc);
        }
        data.put("lang", lang);
        return new ParsedDoc(path, sn, ver, data);
    }

    private static String getRequired(Map<String, String> table, String key, Path path) {
        String v = table.get(key);
        if (v == null || v.isBlank()) {
            throw new CliException("Missing or empty table field '" + key + "' in " + path);
        }
        return v.trim();
    }

    /** Prefer {@code primaryKey}, else {@code alternateKey} (e.g. {@code Ver} vs {@code Version}). */
    private static String getRequiredEither(Map<String, String> table, Path path, String primaryKey, String alternateKey) {
        String v = table.get(primaryKey);
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        v = table.get(alternateKey);
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        throw new CliException("Missing or empty table field '" + primaryKey + "' or '" + alternateKey + "' in " + path);
    }

    private static List<Path> listMarkdownFiles(Path folder, boolean recursive) throws IOException {
        if (!Files.isDirectory(folder)) {
            throw new CliException("Not a directory: " + folder);
        }
        if (recursive) {
            try (Stream<Path> s = Files.walk(folder)) {
                return s.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                        .sorted()
                        .collect(Collectors.toList());
            }
        }
        try (Stream<Path> s = Files.list(folder)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static final class ParsedDoc {
        final Path path;
        final int sn;
        final String verRaw;
        final Map<String, Object> publishData;

        ParsedDoc(Path path, int sn, String verRaw, Map<String, Object> publishData) {
            this.path = path;
            this.sn = sn;
            this.verRaw = verRaw;
            this.publishData = publishData;
        }

        int sortSn() {
            return sn;
        }

        int sortVerNum() {
            try {
                return Integer.parseInt(verRaw.trim());
            } catch (NumberFormatException e) {
                return Integer.MAX_VALUE;
            }
        }
    }

    private static final class Args {
        final Path folder;
        final Path output;
        final boolean recursive;
        final String lang;
        final boolean pretty;

        Args(Path folder, Path output, boolean recursive, String lang, boolean pretty) {
            this.folder = folder;
            this.output = output;
            this.recursive = recursive;
            this.lang = lang;
            this.pretty = pretty;
        }

        static Args parse(String[] args) {
            Path folder = null;
            Path output = null;
            boolean recursive = false;
            boolean pretty = false;
            String lang = "en";
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--folder" -> {
                        if (++i >= args.length) {
                            throw new CliException("--folder requires a path");
                        }
                        folder = Path.of(args[i]);
                    }
                    case "--output" -> {
                        if (++i >= args.length) {
                            throw new CliException("--output requires a path");
                        }
                        output = Path.of(args[i]);
                    }
                    case "--recursive" -> recursive = true;
                    case "--pretty" -> pretty = true;
                    case "--lang" -> {
                        if (++i >= args.length) {
                            throw new CliException("--lang requires a value");
                        }
                        lang = args[i].trim();
                    }
                    case "--help", "-h" -> {
                        printUsage();
                        System.exit(0);
                    }
                    default -> throw new CliException("Unknown argument: " + arg);
                }
            }
            if (folder == null || output == null) {
                printUsage();
                throw new CliException("Required: --folder and --output");
            }
            return new Args(folder, output, recursive, lang, pretty);
        }
    }

    private static void printUsage() {
        System.out.println("""
                Usage: java -jar PublishProtocols-1.0-SNAPSHOT.jar \\
                  --folder <dir> --output <file> [--recursive] [--lang <code>] [--pretty]

                Default: one minified JSON object per line. --pretty: indented multi-line JSON per protocol.
                The 4092-byte alert uses UTF-8 size of the written JSON (compact or pretty).
                """);
    }

    private static final class CliException extends RuntimeException {
        CliException(String message) {
            super(message);
        }
    }
}
