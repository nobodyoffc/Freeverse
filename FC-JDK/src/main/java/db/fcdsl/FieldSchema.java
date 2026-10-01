package db.fcdsl;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The queryable fields of one item class: name → type, single or multi-valued, and a getter.
 * <p>
 * Only fields declared here may appear in a query, a sort or an index. A query naming any
 * other field is rejected as BAD_QUERY. The id field is always declared, as a KEYWORD.
 * <p>
 * Values are normalized to Long, Double, Boolean or String, so the matcher, the sort and the
 * key codec compare them the same way.
 */
public final class FieldSchema<T> {

    public static final class Field<T> {
        final String name;
        final FieldType type;
        final boolean multi;
        final Function<T, ?> getter;

        Field(String name, FieldType type, boolean multi, Function<T, ?> getter) {
            this.name = name;
            this.type = type;
            this.multi = multi;
            this.getter = getter;
        }

        public String name() { return name; }
        public FieldType type() { return type; }
        public boolean multi() { return multi; }
    }

    private final Class<T> itemClass;
    private final String idField;
    private final Function<T, String> idGetter;
    private final Map<String, Field<T>> fields;

    private FieldSchema(Class<T> itemClass, String idField, Function<T, String> idGetter,
                        Map<String, Field<T>> fields) {
        this.itemClass = itemClass;
        this.idField = idField;
        this.idGetter = idGetter;
        this.fields = Collections.unmodifiableMap(fields);
    }

    public static <T> Builder<T> builder(Class<T> itemClass, String idField, Function<T, String> idGetter) {
        return new Builder<>(itemClass, idField, idGetter);
    }

    public Class<T> itemClass() { return itemClass; }

    public String idField() { return idField; }

    public String idOf(T item) { return idGetter.apply(item); }

    public Field<T> field(String name) { return fields.get(name); }

    /** The field, or BAD_QUERY naming it. */
    public Field<T> require(String name) {
        Field<T> f = name == null ? null : fields.get(name);
        if (f == null) throw FcdslException.bad("Unknown field: " + name);
        return f;
    }

    /** The field's normalized values: empty when absent, one element for a single-valued field. */
    public List<Object> values(Field<T> field, T item) {
        Object raw = field.getter.apply(item);
        if (raw == null) return List.of();
        List<Object> out = new ArrayList<>();
        if (field.multi) {
            if (raw instanceof Collection<?> c) {
                for (Object o : c) addNormalized(out, field, o);
            } else if (raw.getClass().isArray()) {
                int n = Array.getLength(raw);
                for (int i = 0; i < n; i++) addNormalized(out, field, Array.get(raw, i));
            } else {
                addNormalized(out, field, raw);
            }
        } else {
            addNormalized(out, field, raw);
        }
        return out;
    }

    /** The single value of a single-valued field, or null. */
    public Object value(Field<T> field, T item) {
        List<Object> v = values(field, item);
        return v.isEmpty() ? null : v.get(0);
    }

    private static <T> void addNormalized(List<Object> out, Field<T> field, Object o) {
        if (o == null) return;
        Object n = normalizeStored(field, o);
        if (n != null) out.add(n);
    }

    private static <T> Object normalizeStored(Field<T> field, Object o) {
        switch (field.type) {
            case LONG:
                if (o instanceof Number num) return num.longValue();
                try { return Long.parseLong(o.toString().trim()); } catch (NumberFormatException e) { return null; }
            case DOUBLE:
                if (o instanceof Number num) return num.doubleValue();
                try { return Double.parseDouble(o.toString().trim()); } catch (NumberFormatException e) { return null; }
            case BOOLEAN:
                if (o instanceof Boolean b) return b;
                return Boolean.parseBoolean(o.toString().trim());
            default:
                return o.toString();
        }
    }

    /**
     * Read a value written in a query (a string) as the field's type.
     * A value that doesn't fit the type is BAD_QUERY, as ES rejects it.
     */
    public static Object parseQueryValue(Field<?> field, String s) {
        if (s == null) return null;
        String t = s.trim();
        try {
            switch (field.type) {
                case LONG:
                    try {
                        return Long.parseLong(t);
                    } catch (NumberFormatException e) {
                        double d = Double.parseDouble(t);
                        if (d != Math.rint(d) || Double.isInfinite(d)) throw e;
                        return (long) d;
                    }
                case DOUBLE:
                    return Double.parseDouble(t);
                case BOOLEAN:
                    // ES writes boolean sort values as 1 and 0.
                    if (t.equalsIgnoreCase("true") || t.equals("1")) return Boolean.TRUE;
                    if (t.equalsIgnoreCase("false") || t.equals("0")) return Boolean.FALSE;
                    throw new NumberFormatException(t);
                default:
                    return s;
            }
        } catch (NumberFormatException e) {
            throw FcdslException.bad("Value '" + s + "' is not a " + field.type + " for field " + field.name);
        }
    }

    /** How a value appears in a cursor, matching the strings ES returned as sort values. */
    static String formatCursorValue(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b ? "1" : "0";
        return v.toString();
    }

    /** Order of two normalized values of the same type. Strings compare by code point, as UTF-8 bytes do. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static int compare(Object a, Object b) {
        if (a instanceof String sa && b instanceof String sb) return compareCodePoints(sa, sb);
        return ((Comparable) a).compareTo(b);
    }

    static int compareCodePoints(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            int ca = a.codePointAt(i), cb = b.codePointAt(j);
            if (ca != cb) return Integer.compare(ca, cb);
            i += Character.charCount(ca);
            j += Character.charCount(cb);
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    public static final class Builder<T> {
        private final Class<T> itemClass;
        private final String idField;
        private final Function<T, String> idGetter;
        private final Map<String, Field<T>> fields = new LinkedHashMap<>();

        private Builder(Class<T> itemClass, String idField, Function<T, String> idGetter) {
            this.itemClass = itemClass;
            this.idField = idField;
            this.idGetter = idGetter;
            fields.put(idField, new Field<>(idField, FieldType.KEYWORD, false, idGetter));
        }

        public Builder<T> field(String name, FieldType type, Function<T, ?> getter) {
            return add(name, type, false, getter);
        }

        /** A field holding several values, e.g. a List. A query matches if any element matches. */
        public Builder<T> multiField(String name, FieldType type, Function<T, ?> getter) {
            return add(name, type, true, getter);
        }

        public Builder<T> longField(String name, Function<T, ?> getter) { return field(name, FieldType.LONG, getter); }

        public Builder<T> keyword(String name, Function<T, ?> getter) { return field(name, FieldType.KEYWORD, getter); }

        public Builder<T> keywords(String name, Function<T, ?> getter) { return multiField(name, FieldType.KEYWORD, getter); }

        private Builder<T> add(String name, FieldType type, boolean multi, Function<T, ?> getter) {
            if (name == null || name.isEmpty()) throw new IllegalArgumentException("Field name is empty");
            if (fields.containsKey(name)) throw new IllegalArgumentException("Field declared twice: " + name);
            fields.put(name, new Field<>(name, type, multi, getter));
            return this;
        }

        public FieldSchema<T> build() {
            return new FieldSchema<>(itemClass, idField, idGetter, new LinkedHashMap<>(fields));
        }
    }
}
