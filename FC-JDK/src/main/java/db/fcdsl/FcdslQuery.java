package db.fcdsl;

import data.apipData.Equals;
import data.apipData.FcQuery;
import data.apipData.Fcdsl;
import data.apipData.Match;
import data.apipData.Part;
import data.apipData.Range;
import data.apipData.Sort;
import data.apipData.Terms;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An FCDSL request checked against a {@link FieldSchema} and made ready to test items.
 * <p>
 * Semantics follow the ES translation in {@code fapi.query.FcdslQueryExecutor}, so a
 * query means the same here as it did against Elasticsearch:
 * <ul>
 *   <li>{@code query} and {@code filter} must both hold; within each, every operator must hold.</li>
 *   <li>{@code except}: none of its operators may hold.</li>
 *   <li>{@code terms} / {@code equals}: any listed field has any listed value; {@code unequals}: none does.</li>
 *   <li>{@code part}: any listed field contains the value ({@code *} and {@code ?} are wildcards).</li>
 *   <li>{@code match}: any listed field shares a token with the value (case-insensitive;
 *       see {@link #tokens}). On a number or boolean field, it means equality.</li>
 *   <li>{@code range}: any listed field has a value within all given bounds.</li>
 *   <li>{@code exists}: every listed field has a value; {@code unexists}: none has.</li>
 *   <li>On a multi-valued field, a value test holds if any element passes.</li>
 *   <li>Values of terms, equals, unequals, part and match are URL-decoded, as before.</li>
 * </ul>
 * The sort always ends with the id ascending, so the order is total and a cursor is unique.
 * Missing values sort last in both directions, as in ES.
 */
public final class FcdslQuery<T> {

    /** One sort key of the effective sort. */
    public static final class SortKey<T> {
        final FieldSchema.Field<T> field;
        final boolean desc;

        SortKey(FieldSchema.Field<T> field, boolean desc) {
            this.field = field;
            this.desc = desc;
        }

        public String field() { return field.name(); }
        public boolean desc() { return desc; }
    }

    /** One tested operator. */
    abstract static class Cond<T> {
        abstract boolean test(FieldSchema<T> schema, T item);
    }

    /** terms / equals, or their negation (unequals). */
    static final class TermsCond<T> extends Cond<T> {
        final Map<FieldSchema.Field<T>, Set<Object>> values;

        TermsCond(Map<FieldSchema.Field<T>, Set<Object>> values) {
            this.values = values;
        }

        @Override
        boolean test(FieldSchema<T> schema, T item) {
            for (Map.Entry<FieldSchema.Field<T>, Set<Object>> e : values.entrySet()) {
                for (Object v : schema.values(e.getKey(), item)) {
                    if (e.getValue().contains(v)) return true;
                }
            }
            return false;
        }

        /** The field, when this tests exactly one field: usable as an index equality. */
        FieldSchema.Field<T> singleField() {
            return values.size() == 1 ? values.keySet().iterator().next() : null;
        }
    }

    static final class PartCond<T> extends Cond<T> {
        final List<FieldSchema.Field<T>> fields;
        final Pattern pattern;

        PartCond(List<FieldSchema.Field<T>> fields, Pattern pattern) {
            this.fields = fields;
            this.pattern = pattern;
        }

        @Override
        boolean test(FieldSchema<T> schema, T item) {
            for (FieldSchema.Field<T> f : fields) {
                for (Object v : schema.values(f, item)) {
                    if (pattern.matcher(FieldSchema.formatCursorValue(v)).matches()) return true;
                }
            }
            return false;
        }
    }

    static final class MatchCond<T> extends Cond<T> {
        final List<FieldSchema.Field<T>> fields;
        final Set<String> tokens;
        final Map<FieldSchema.Field<T>, Object> exact;

        MatchCond(List<FieldSchema.Field<T>> fields, Set<String> tokens, Map<FieldSchema.Field<T>, Object> exact) {
            this.fields = fields;
            this.tokens = tokens;
            this.exact = exact;
        }

        @Override
        boolean test(FieldSchema<T> schema, T item) {
            for (FieldSchema.Field<T> f : fields) {
                Object want = exact.get(f);
                for (Object v : schema.values(f, item)) {
                    if (want != null) {
                        if (want.equals(v)) return true;
                    } else {
                        for (String t : FcdslQuery.tokens(v.toString())) {
                            if (tokens.contains(t)) return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    /** Bounds on one field. A null bound is open. */
    static final class Bounds {
        final Object lower;
        final boolean lowerInclusive;
        final Object upper;
        final boolean upperInclusive;

        Bounds(Object lower, boolean lowerInclusive, Object upper, boolean upperInclusive) {
            this.lower = lower;
            this.lowerInclusive = lowerInclusive;
            this.upper = upper;
            this.upperInclusive = upperInclusive;
        }

        boolean contains(Object v) {
            if (lower != null) {
                int c = FieldSchema.compare(v, lower);
                if (c < 0 || (c == 0 && !lowerInclusive)) return false;
            }
            if (upper != null) {
                int c = FieldSchema.compare(v, upper);
                if (c > 0 || (c == 0 && !upperInclusive)) return false;
            }
            return true;
        }

        boolean aboveUpper(Object v) {
            if (upper == null) return false;
            int c = FieldSchema.compare(v, upper);
            return c > 0 || (c == 0 && !upperInclusive);
        }

        boolean belowLower(Object v) {
            if (lower == null) return false;
            int c = FieldSchema.compare(v, lower);
            return c < 0 || (c == 0 && !lowerInclusive);
        }
    }

    static final class RangeCond<T> extends Cond<T> {
        final Map<FieldSchema.Field<T>, Bounds> bounds;

        RangeCond(Map<FieldSchema.Field<T>, Bounds> bounds) {
            this.bounds = bounds;
        }

        @Override
        boolean test(FieldSchema<T> schema, T item) {
            for (Map.Entry<FieldSchema.Field<T>, Bounds> e : bounds.entrySet()) {
                for (Object v : schema.values(e.getKey(), item)) {
                    if (e.getValue().contains(v)) return true;
                }
            }
            return false;
        }

        FieldSchema.Field<T> singleField() {
            return bounds.size() == 1 ? bounds.keySet().iterator().next() : null;
        }
    }

    /** exists: all fields have a value. unexists: no field has one. */
    static final class ExistsCond<T> extends Cond<T> {
        final List<FieldSchema.Field<T>> fields;
        final boolean exists;

        ExistsCond(List<FieldSchema.Field<T>> fields, boolean exists) {
            this.fields = fields;
            this.exists = exists;
        }

        @Override
        boolean test(FieldSchema<T> schema, T item) {
            for (FieldSchema.Field<T> f : fields) {
                boolean has = !schema.values(f, item).isEmpty();
                if (has != exists) return false;
            }
            return true;
        }
    }

    final FieldSchema<T> schema;
    /** Every one must hold. Also holds the negations built from unequals. */
    final List<Cond<T>> must = new ArrayList<>();
    /** None may hold. */
    final List<Cond<T>> mustNot = new ArrayList<>();
    /** Only these ids, when not null. */
    final Set<String> ids;
    final List<SortKey<T>> sort;
    /** Parsed cursor, one value per sort key, or null. */
    final List<Object> after;
    final int size;
    final List<String> fields;
    final List<String> noFields;

    private FcdslQuery(FieldSchema<T> schema, Set<String> ids, List<SortKey<T>> sort, List<Object> after,
                       int size, List<String> fields, List<String> noFields) {
        this.schema = schema;
        this.ids = ids;
        this.sort = sort;
        this.after = after;
        this.size = size;
        this.fields = fields;
        this.noFields = noFields;
    }

    /**
     * Check and compile a request.
     *
     * @param fcdsl        the client's FCDSL; may be null
     * @param extra        conditions the server adds, all of which must hold (e.g. "not expired",
     *                     "addressed to these recipients"); may be null
     * @param defaultSort  used when the request has no sort; may be null (then by id)
     * @param defaultSize  used when the request has no valid size
     * @param maxSize      a larger size is cut down to this
     * @throws FcdslException BAD_QUERY for unknown fields, values that don't fit a field's type,
     *                        a malformed cursor, or a sort on a multi-valued field
     */
    public static <T> FcdslQuery<T> compile(FieldSchema<T> schema, Fcdsl fcdsl, List<? extends FcQuery> extra,
                                            List<Sort> defaultSort, int defaultSize, int maxSize) {
        Fcdsl f = fcdsl != null ? fcdsl : new Fcdsl();

        Set<String> ids = null;
        if (f.getIds() != null && !f.getIds().isEmpty()) {
            ids = new LinkedHashSet<>();
            for (String id : f.getIds()) if (id != null) ids.add(id);
        }

        List<Sort> sortList = f.getSort() != null && !f.getSort().isEmpty() ? f.getSort() : defaultSort;
        List<SortKey<T>> sort = new ArrayList<>();
        boolean hasId = false;
        if (sortList != null) {
            for (Sort s : sortList) {
                if (s == null) continue;
                FieldSchema.Field<T> field = schema.require(s.getField());
                if (field.multi()) throw FcdslException.bad("Cannot sort on multi-valued field: " + field.name());
                String order = s.getOrder();
                boolean desc;
                if (order == null || order.equalsIgnoreCase("desc")) desc = true;
                else if (order.equalsIgnoreCase("asc")) desc = false;
                else throw FcdslException.bad("Sort order must be asc or desc: " + order);
                sort.add(new SortKey<>(field, desc));
                if (field.name().equals(schema.idField())) {
                    hasId = true;
                    break; // ids are unique: later keys can never decide
                }
            }
        }
        if (!hasId) sort.add(new SortKey<>(schema.require(schema.idField()), false));

        List<Object> after = null;
        if (f.getAfter() != null && !f.getAfter().isEmpty()) {
            if (f.getAfter().size() != sort.size()) {
                throw FcdslException.bad("Cursor has " + f.getAfter().size() + " values but the sort has "
                        + sort.size() + " keys");
            }
            after = new ArrayList<>();
            for (int i = 0; i < sort.size(); i++) {
                after.add(FieldSchema.parseQueryValue(sort.get(i).field, f.getAfter().get(i)));
            }
        }

        FcdslQuery<T> q = new FcdslQuery<>(schema, ids, Collections.unmodifiableList(sort), after,
                parseSize(f.getSize(), defaultSize, maxSize), f.getFields(), f.getNoFields());

        if (f.getQuery() != null) q.addAll(f.getQuery(), q.must);
        if (f.getFilter() != null) q.addAll(f.getFilter(), q.must);
        if (extra != null) for (FcQuery e : extra) if (e != null) q.addAll(e, q.must);
        if (f.getExcept() != null) q.addAll(f.getExcept(), q.mustNot);
        return q;
    }

    static int parseSize(String sizeStr, int defaultSize, int maxSize) {
        int size = 0;
        if (sizeStr != null) {
            try {
                size = Integer.parseInt(sizeStr.trim());
            } catch (NumberFormatException ignored) {
                // an invalid size falls back to the default, as it always has
            }
        }
        if (size <= 0) size = defaultSize;
        return Math.min(size, maxSize);
    }

    /** Add each operator of one FcQuery to the target list. unequals goes in negated. */
    private void addAll(FcQuery fq, List<Cond<T>> target) {
        Terms terms = fq.getTerms();
        if (terms != null && terms.getFields() != null && terms.getValues() != null) {
            target.add(termsCond(terms.getFields(), terms.getValues()));
        }
        Equals equals = fq.getEquals();
        if (equals != null && equals.getFields() != null && equals.getValues() != null) {
            target.add(termsCond(equals.getFields(), equals.getValues()));
        }
        Equals unequals = fq.getUnequals();
        if (unequals != null && unequals.getFields() != null && unequals.getValues() != null) {
            TermsCond<T> c = termsCond(unequals.getFields(), unequals.getValues());
            // except.unequals means "must not (none equal)", i.e. some field equals
            if (target == mustNot) must.add(c);
            else mustNot.add(c);
        }
        Part part = fq.getPart();
        if (part != null && part.getFields() != null && part.getValue() != null) {
            target.add(partCond(part));
        }
        Match match = fq.getMatch();
        if (match != null && match.getFields() != null && match.getFields().length > 0 && match.getValue() != null) {
            target.add(matchCond(match));
        }
        Range range = fq.getRange();
        if (range != null && range.getFields() != null && range.getFields().length > 0) {
            RangeCond<T> c = rangeCond(range);
            if (c != null) target.add(c);
        }
        if (fq.getExists() != null && fq.getExists().length > 0) {
            List<FieldSchema.Field<T>> fs = fieldList(fq.getExists());
            if (!fs.isEmpty()) target.add(new ExistsCond<>(fs, true));
        }
        if (fq.getUnexists() != null && fq.getUnexists().length > 0) {
            List<FieldSchema.Field<T>> fs = fieldList(fq.getUnexists());
            if (!fs.isEmpty()) target.add(new ExistsCond<>(fs, false));
        }
    }

    private List<FieldSchema.Field<T>> fieldList(String[] names) {
        List<FieldSchema.Field<T>> out = new ArrayList<>();
        for (String n : names) {
            if (n == null || n.isBlank()) continue;
            out.add(schema.require(n));
        }
        return out;
    }

    private static String decode(String v) {
        try {
            return URLDecoder.decode(v, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw FcdslException.bad("Malformed URL encoding in value: " + v);
        }
    }

    private TermsCond<T> termsCond(String[] fieldNames, String[] rawValues) {
        List<String> decoded = new ArrayList<>();
        for (String v : rawValues) {
            if (v == null || v.isBlank()) continue;
            decoded.add(decode(v));
        }
        Map<FieldSchema.Field<T>, Set<Object>> values = new LinkedHashMap<>();
        for (FieldSchema.Field<T> field : fieldList(fieldNames)) {
            Set<Object> set = new HashSet<>();
            for (String v : decoded) set.add(FieldSchema.parseQueryValue(field, v));
            values.put(field, set);
        }
        return new TermsCond<>(values);
    }

    private PartCond<T> partCond(Part part) {
        String value = decode(part.getValue());
        StringBuilder regex = new StringBuilder(".*");
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '*' || c == '?') {
                if (literal.length() > 0) {
                    regex.append(Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }
                regex.append(c == '*' ? ".*" : ".");
            } else {
                literal.append(c);
            }
        }
        if (literal.length() > 0) regex.append(Pattern.quote(literal.toString()));
        regex.append(".*");
        int flags = Pattern.DOTALL;
        if (Boolean.parseBoolean(part.getIsCaseInsensitive())) flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        return new PartCond<>(fieldList(part.getFields()), Pattern.compile(regex.toString(), flags));
    }

    private MatchCond<T> matchCond(Match match) {
        String value = decode(match.getValue());
        List<FieldSchema.Field<T>> fs = fieldList(match.getFields());
        Map<FieldSchema.Field<T>, Object> exact = new LinkedHashMap<>();
        for (FieldSchema.Field<T> f : fs) {
            if (f.type() == FieldType.LONG || f.type() == FieldType.DOUBLE || f.type() == FieldType.BOOLEAN) {
                exact.put(f, FieldSchema.parseQueryValue(f, value));
            }
        }
        return new MatchCond<>(fs, new HashSet<>(tokens(value)), exact);
    }

    private RangeCond<T> rangeCond(Range range) {
        Map<FieldSchema.Field<T>, Bounds> bounds = new LinkedHashMap<>();
        for (FieldSchema.Field<T> f : fieldList(range.getFields())) {
            Object lower = null, upper = null;
            boolean lowerInc = false, upperInc = false;
            // gte/lte win over gt/lt only when tighter; with both given, keep the tighter of each pair
            if (range.getGt() != null) lower = FieldSchema.parseQueryValue(f, range.getGt());
            if (range.getGte() != null) {
                Object v = FieldSchema.parseQueryValue(f, range.getGte());
                if (lower == null || FieldSchema.compare(v, lower) > 0) {
                    lower = v;
                    lowerInc = true;
                }
            }
            if (range.getLt() != null) upper = FieldSchema.parseQueryValue(f, range.getLt());
            if (range.getLte() != null) {
                Object v = FieldSchema.parseQueryValue(f, range.getLte());
                if (upper == null || FieldSchema.compare(v, upper) < 0) {
                    upper = v;
                    upperInc = true;
                }
            }
            if (lower == null && upper == null) continue;
            bounds.put(f, new Bounds(lower, lowerInc, upper, upperInc));
        }
        return bounds.isEmpty() ? null : new RangeCond<>(bounds);
    }

    /**
     * The tokens {@code match} compares: runs of letters and digits, lower-cased, with each
     * CJK ideograph a token of its own (as the ES standard analyzer does).
     */
    static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isIdeographic(cp)) {
                if (cur.length() > 0) {
                    out.add(cur.toString().toLowerCase(Locale.ROOT));
                    cur.setLength(0);
                }
                out.add(new String(Character.toChars(cp)));
            } else if (Character.isLetterOrDigit(cp)) {
                cur.appendCodePoint(cp);
            } else if (cur.length() > 0) {
                out.add(cur.toString().toLowerCase(Locale.ROOT));
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) out.add(cur.toString().toLowerCase(Locale.ROOT));
        return out;
    }

    // ==================== testing and ordering ====================

    /** Whether the item matches every condition (ids, must, must not). */
    public boolean matches(T item) {
        if (ids != null && !ids.contains(schema.idOf(item))) return false;
        for (Cond<T> c : must) if (!c.test(schema, item)) return false;
        for (Cond<T> c : mustNot) if (c.test(schema, item)) return false;
        return true;
    }

    /** The item's values for the effective sort. */
    public List<Object> sortValues(T item) {
        List<Object> out = new ArrayList<>(sort.size());
        for (SortKey<T> k : sort) out.add(schema.value(k.field, item));
        return out;
    }

    /** Compare two sort tuples in the effective order. Missing values come last in either direction. */
    int compareTuples(List<Object> a, List<Object> b) {
        for (int i = 0; i < sort.size(); i++) {
            Object x = a.get(i), y = b.get(i);
            int c;
            if (x == null || y == null) {
                c = x == null ? (y == null ? 0 : 1) : -1;
            } else {
                c = FieldSchema.compare(x, y);
                if (sort.get(i).desc) c = -c;
            }
            if (c != 0) return c;
        }
        return 0;
    }

    Comparator<T> itemComparator() {
        return (x, y) -> compareTuples(sortValues(x), sortValues(y));
    }

    /** Whether the item comes strictly after the request's cursor (true when there is none). */
    boolean afterCursor(T item) {
        return after == null || compareTuples(sortValues(item), after) > 0;
    }

    /** The cursor to return for the last item of a page, as strings like ES's sort values. */
    public List<String> cursorOf(T item) {
        List<String> out = new ArrayList<>(sort.size());
        for (Object v : sortValues(item)) out.add(FieldSchema.formatCursorValue(v));
        return out;
    }

    public List<SortKey<T>> sort() { return sort; }

    public int size() { return size; }

    public List<String> fields() { return fields; }

    public List<String> noFields() { return noFields; }

    /** True when nothing restricts the result: every item matches. */
    boolean matchesAll() {
        return ids == null && must.isEmpty() && mustNot.isEmpty();
    }
}
