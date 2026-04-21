package publishprotocols;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AbstractExtractor {

    private static final int MAX_DESC_LENGTH = 4000;
    private static final Pattern HEADING_ABSTRACT = Pattern.compile("(?m)^##\\s+Abstract\\s*$");
    private static final Pattern HEADING_ANY = Pattern.compile("(?m)^##\\s+");

    private AbstractExtractor() {
    }

    static String extract(String markdown) {
        Matcher m = HEADING_ABSTRACT.matcher(markdown);
        if (!m.find()) {
            return "";
        }
        int start = m.end();
        Matcher next = HEADING_ANY.matcher(markdown);
        next.region(start, markdown.length());
        int end = next.find() ? next.start() : markdown.length();
        String raw = markdown.substring(start, end);
        return normalize(raw);
    }

    private static String normalize(String raw) {
        StringBuilder sb = new StringBuilder();
        boolean inFence = false;
        for (String line : raw.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("```")) {
                inFence = !inFence;
                continue;
            }
            if (inFence) {
                continue;
            }
            if (t.startsWith("#")) {
                continue;
            }
            if (t.isEmpty()) {
                if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(' ');
                }
                continue;
            }
            if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != ' ') {
                sb.append(' ');
            }
            sb.append(t);
        }
        String s = sb.toString().trim().replaceAll("\\s+", " ");
        s = stripBoldAndInlineCode(s);
        if (s.length() > MAX_DESC_LENGTH) {
            return s.substring(0, MAX_DESC_LENGTH);
        }
        return s;
    }

    /** Remove markdown bold {@code **} and inline code {@code `} markers from plain-text abstract. */
    private static String stripBoldAndInlineCode(String s) {
        if (s.isEmpty()) {
            return s;
        }
        String t = s.replace("**", "").replace("`", "");
        return t.trim().replaceAll("\\s+", " ");
    }
}
