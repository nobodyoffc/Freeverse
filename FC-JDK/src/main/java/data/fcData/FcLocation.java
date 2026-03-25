package data.fcData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Parses and manipulates location expressions as defined in FVEP3.
 * <p>
 * Operators:
 * <ul>
 *   <li>{@code /} Slash — parent contains child (left to right)</li>
 *   <li>{@code @} At — child is within parent (left to right)</li>
 *   <li>{@code #} Sharp — data fragment within an entity</li>
 *   <li>{@code \} Escape — next character is literal</li>
 *   <li>{@code ()} Typed ID — disambiguates txid-based OIDs</li>
 * </ul>
 * Precedence (highest first): {@code \} > {@code #} > {@code ()} > {@code @} > {@code /}
 */
public class FcLocation {

    private final List<Segment> segments; // root (outermost) to leaf (innermost)

    private FcLocation(List<Segment> segments) {
        this.segments = segments;
    }

    /**
     * A single segment in a location path.
     */
    public static class Segment {
        private final String name;
        private final String typedIdType; // nullable — e.g. "codeId", "SID"
        private final String fragment;    // nullable — the part after #
        private final boolean nid;        // true if this segment is a NID (contains @)

        public Segment(String name, String typedIdType, String fragment, boolean nid) {
            this.name = name;
            this.typedIdType = typedIdType;
            this.fragment = fragment;
            this.nid = nid;
        }

        public String getName() { return name; }
        public String getTypedIdType() { return typedIdType; }
        public String getFragment() { return fragment; }
        public boolean isNid() { return nid; }

        /**
         * Returns the full reference string for this segment (Slash-form safe).
         * For NID segments, the internal {@code @} is preserved unescaped
         * because {@code @} has higher precedence than {@code /}.
         */
        public String toRefString() {
            StringBuilder sb = new StringBuilder();
            if (typedIdType != null) {
                sb.append('(').append(typedIdType).append(')');
            }
            if (nid) {
                int atIdx = name.indexOf('@');
                if (atIdx >= 0) {
                    sb.append(escapeCharsExceptAt(name.substring(0, atIdx)));
                    sb.append('@');
                    sb.append(escapeCharsExceptAt(name.substring(atIdx + 1)));
                } else {
                    sb.append(escapeSpecialChars(name));
                }
            } else {
                sb.append(escapeSpecialChars(name));
            }
            if (fragment != null) {
                sb.append('#').append(escapeSpecialChars(fragment));
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return toRefString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Segment s = (Segment) o;
            return nid == s.nid
                    && Objects.equals(name, s.name)
                    && Objects.equals(typedIdType, s.typedIdType)
                    && Objects.equals(fragment, s.fragment);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, typedIdType, fragment, nid);
        }
    }

    /**
     * Parse a location expression string into an FcLocation.
     * Implements the FVEP3 parsing algorithm:
     * 1. Resolve escape, 2. Resolve #, 3. Resolve (), 4. Resolve @, 5. Resolve /
     *
     * @param location the location expression string
     * @return parsed FcLocation with segments ordered root to leaf
     * @throws IllegalArgumentException if the expression is invalid
     */
    public static FcLocation parse(String location) {
        if (location == null || location.isEmpty()) {
            throw new IllegalArgumentException("Location expression must not be null or empty");
        }

        // Step 1: Resolve escapes — produce a list of characters with escape flags
        List<CharEntry> chars = resolveEscapes(location);

        // Step 2-5: Split by / (lowest precedence), then within each /-segment
        // resolve @ (next lowest), and within each @-group resolve () and #.
        List<List<CharEntry>> slashParts = splitByChar(chars, '/');

        List<Segment> allSegments = new ArrayList<>();

        for (List<CharEntry> slashPart : slashParts) {
            // Within each slash-part, resolve @ to find NID groupings
            List<List<CharEntry>> atParts = splitByChar(slashPart, '@');

            if (atParts.size() == 1) {
                // No @ in this slash-part — simple entity reference
                allSegments.add(parseSegment(atParts.get(0), false));
            } else {
                // Has @ — this forms a NID or at-chain
                // @ means left is within right, so the at-parts are in child→parent order
                // We need to reverse them to get root→leaf (parent→child) order
                // But if it's a NID (exactly 2 parts), keep it as one segment
                if (atParts.size() == 2) {
                    // NID: objectName@subjectId — treat as single segment
                    String objectName = charsToString(atParts.get(0));
                    String subjectId = charsToString(atParts.get(1));
                    // Check for typed ID on the object part
                    String[] typedObj = extractTypedId(atParts.get(0));
                    String[] fragmentObj = extractFragment(typedObj != null ? typedObj[1] : objectName);

                    String nidName = fragmentObj[0] + "@" + subjectId;
                    allSegments.add(new Segment(
                            nidName,
                            typedObj != null ? typedObj[0] : null,
                            fragmentObj[1],
                            true
                    ));
                } else {
                    // Multi-level @ chain — reverse to get root→leaf
                    List<Segment> atSegments = new ArrayList<>();
                    for (List<CharEntry> atPart : atParts) {
                        atSegments.add(parseSegment(atPart, false));
                    }
                    Collections.reverse(atSegments);
                    allSegments.addAll(atSegments);
                }
            }
        }

        if (allSegments.isEmpty()) {
            throw new IllegalArgumentException("Location expression must contain at least one entity reference");
        }

        return new FcLocation(allSegments);
    }

    /**
     * Returns segments in root-to-leaf order.
     */
    public List<Segment> getSegments() {
        return Collections.unmodifiableList(segments);
    }

    /**
     * Returns the leaf (innermost) segment.
     */
    public Segment getLeaf() {
        return segments.get(segments.size() - 1);
    }

    /**
     * Returns the root (outermost) segment.
     */
    public Segment getRoot() {
        return segments.get(0);
    }

    /**
     * Convert to the canonical Slash form (root→leaf).
     */
    public String toSlashForm() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) sb.append('/');
            sb.append(segments.get(i).toRefString());
        }
        return sb.toString();
    }

    /**
     * Convert to the At form (leaf→root).
     * Note: NIDs within the expression will have their internal @ escaped.
     */
    public String toAtForm() {
        StringBuilder sb = new StringBuilder();
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (i < segments.size() - 1) sb.append('@');
            Segment seg = segments.get(i);
            if (seg.nid && i > 0) {
                // In At form, a NID's internal @ must be escaped to avoid ambiguity
                String ref = seg.toRefString().replace("@", "\\@");
                sb.append(ref);
            } else {
                sb.append(seg.toRefString());
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toSlashForm();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FcLocation that = (FcLocation) o;
        return Objects.equals(segments, that.segments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(segments);
    }

    // ---- Internal parsing helpers ----

    private static class CharEntry {
        final char ch;
        final boolean escaped;

        CharEntry(char ch, boolean escaped) {
            this.ch = ch;
            this.escaped = escaped;
        }
    }

    private static List<CharEntry> resolveEscapes(String input) {
        List<CharEntry> result = new ArrayList<>();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < input.length()) {
                char next = input.charAt(i + 1);
                if (next == '@' || next == '/' || next == '#' || next == '\\' || next == '(' || next == ')') {
                    result.add(new CharEntry(next, true));
                    i++; // skip next
                    continue;
                }
            }
            result.add(new CharEntry(c, false));
        }
        return result;
    }

    private static List<List<CharEntry>> splitByChar(List<CharEntry> chars, char delimiter) {
        List<List<CharEntry>> parts = new ArrayList<>();
        List<CharEntry> current = new ArrayList<>();
        int parenDepth = 0;

        for (CharEntry ce : chars) {
            if (!ce.escaped && ce.ch == '(') {
                parenDepth++;
                current.add(ce);
            } else if (!ce.escaped && ce.ch == ')') {
                parenDepth--;
                current.add(ce);
            } else if (!ce.escaped && ce.ch == delimiter && parenDepth == 0) {
                parts.add(current);
                current = new ArrayList<>();
            } else {
                current.add(ce);
            }
        }
        parts.add(current);
        return parts;
    }

    private static Segment parseSegment(List<CharEntry> chars, boolean isNid) {
        String raw = charsToString(chars);

        // Extract typed ID
        String typedIdType = null;
        String remaining = raw;
        String[] typed = extractTypedId(chars);
        if (typed != null) {
            typedIdType = typed[0];
            remaining = typed[1];
        }

        // Extract fragment
        String fragment = null;
        String[] frag = extractFragment(remaining);
        remaining = frag[0];
        fragment = frag[1];

        return new Segment(remaining, typedIdType, fragment, isNid);
    }

    /**
     * Extract typed ID prefix from char entries.
     * @return [type, remainingValue] or null if no typed ID
     */
    private static String[] extractTypedId(List<CharEntry> chars) {
        if (chars.isEmpty()) return null;
        CharEntry first = chars.get(0);
        if (first.escaped || first.ch != '(') return null;

        int closeIdx = -1;
        for (int i = 1; i < chars.size(); i++) {
            CharEntry ce = chars.get(i);
            if (!ce.escaped && ce.ch == ')') {
                closeIdx = i;
                break;
            }
        }
        if (closeIdx < 0) return null;

        StringBuilder type = new StringBuilder();
        for (int i = 1; i < closeIdx; i++) {
            type.append(chars.get(i).ch);
        }

        StringBuilder value = new StringBuilder();
        for (int i = closeIdx + 1; i < chars.size(); i++) {
            value.append(chars.get(i).ch);
        }

        return new String[]{type.toString(), value.toString()};
    }

    /**
     * Extract fragment from a string value.
     * @return [entityName, fragment] where fragment may be null
     */
    private static String[] extractFragment(String value) {
        // Find unescaped #
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '#') {
                if (i > 0 && value.charAt(i - 1) == '\\') continue;
                return new String[]{value.substring(0, i), value.substring(i + 1)};
            }
        }
        return new String[]{value, null};
    }

    private static String charsToString(List<CharEntry> chars) {
        StringBuilder sb = new StringBuilder();
        for (CharEntry ce : chars) {
            sb.append(ce.ch);
        }
        return sb.toString();
    }

    private static String escapeSpecialChars(String input) {
        if (input == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '@' || c == '/' || c == '#' || c == '\\' || c == '(' || c == ')') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String escapeCharsExceptAt(String input) {
        if (input == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '/' || c == '#' || c == '\\' || c == '(' || c == ')') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
