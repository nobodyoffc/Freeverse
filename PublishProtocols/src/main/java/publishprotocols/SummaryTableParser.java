package publishprotocols;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the Summary pipe table from protocol markdown: first table under {@code ## Summary},
 * else the first pipe table before {@code ## Contents}, else the first pipe table in the file.
 */
final class SummaryTableParser {

    private static final Pattern HEADING_SUMMARY = Pattern.compile("(?m)^##\\s+Summary\\s*$");
    private static final Pattern HEADING_ANY = Pattern.compile("(?m)^##\\s+");
    private static final Pattern HEADING_CONTENTS = Pattern.compile("(?m)^##\\s+Contents\\s*$");

    private SummaryTableParser() {
    }

    static Map<String, String> parse(String markdown) {
        String block = sectionAfterHeading(markdown, HEADING_SUMMARY);
        if (block != null) {
            Map<String, String> fromSummary = firstPipeTable(block);
            if (!fromSummary.isEmpty()) {
                return fromSummary;
            }
        }
        String beforeContents = sectionBeforeHeading(markdown, HEADING_CONTENTS);
        String scan = beforeContents != null ? beforeContents : markdown;
        return firstPipeTable(scan);
    }

    private static String sectionAfterHeading(String text, Pattern heading) {
        Matcher m = heading.matcher(text);
        if (!m.find()) {
            return null;
        }
        int start = m.end();
        Matcher next = HEADING_ANY.matcher(text);
        next.region(start, text.length());
        int end = next.find() ? next.start() : text.length();
        return text.substring(start, end);
    }

    private static String sectionBeforeHeading(String text, Pattern heading) {
        Matcher m = heading.matcher(text);
        if (!m.find()) {
            return null;
        }
        return text.substring(0, m.start());
    }

    static Map<String, String> firstPipeTable(String text) {
        String[] lines = text.split("\\R");
        int i = 0;
        while (i < lines.length) {
            while (i < lines.length && lines[i].trim().isEmpty()) {
                i++;
            }
            if (i >= lines.length) {
                break;
            }
            if (!isPipeTableLine(lines[i])) {
                i++;
                continue;
            }
            Map<String, String> map = new LinkedHashMap<>();
            while (i < lines.length && isPipeTableLine(lines[i])) {
                addTableRow(map, lines[i]);
                i++;
            }
            if (!map.isEmpty()) {
                return map;
            }
        }
        return new LinkedHashMap<>();
    }

    private static boolean isPipeTableLine(String line) {
        String t = line.trim();
        return t.startsWith("|") && t.length() > 1 && t.chars().filter(ch -> ch == '|').count() >= 2;
    }

    private static void addTableRow(Map<String, String> map, String line) {
        String[] parts = line.trim().split("\\|", -1);
        if (parts.length < 3) {
            return;
        }
        String key = parts[1].trim();
        String value = parts[2].trim();
        if (key.isEmpty()) {
            return;
        }
        if (isSeparatorRow(key, value)) {
            return;
        }
        if ("Field".equalsIgnoreCase(key) && "Content".equalsIgnoreCase(value) && parts.length <= 4) {
            return;
        }
        map.put(key, value);
    }

    private static boolean isSeparatorRow(String key, String value) {
        return key.chars().allMatch(ch -> ch == '-' || ch == ':' || Character.isWhitespace(ch))
                && (value.isEmpty() || value.chars().allMatch(ch -> ch == '-' || ch == ':' || Character.isWhitespace(ch)));
    }
}
