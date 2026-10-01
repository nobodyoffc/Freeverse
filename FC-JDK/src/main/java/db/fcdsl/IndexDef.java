package db.fcdsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A secondary index: equality components first, then sort components, then (always, implicitly)
 * the item id ascending, so every entry is unique.
 * <p>
 * An equality component may be multi-valued: an item gets one entry per element (DOCK's
 * {@code recipients}). A query uses the index when every equality component is pinned by a
 * {@code terms} or {@code equals} on that field alone. It is read in order, without sorting, when
 * the query's sort is exactly the sort components followed by id ascending; a {@code range} on
 * the first sort component then bounds the read.
 * <p>
 * Example: {@code IndexDef.named("rcptTime").eq("recipients").asc("createTime")}.
 */
public final class IndexDef {

    static final class Comp {
        final String field;
        final boolean desc;

        Comp(String field, boolean desc) {
            this.field = field;
            this.desc = desc;
        }
    }

    final String name;
    final List<String> eq;
    final List<Comp> sort;

    private IndexDef(String name, List<String> eq, List<Comp> sort) {
        this.name = name;
        this.eq = Collections.unmodifiableList(eq);
        this.sort = Collections.unmodifiableList(sort);
    }

    public static Builder named(String name) {
        if (name == null || name.isEmpty() || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Bad index name: " + name);
        }
        return new Builder(name);
    }

    public String name() { return name; }

    /** Changes whenever the index's layout changes; a stored index with another signature is rebuilt. */
    String signature() {
        StringBuilder b = new StringBuilder("v1");
        for (String f : eq) b.append("|eq:").append(f);
        for (Comp c : sort) b.append('|').append(c.desc ? "desc:" : "asc:").append(c.field);
        return b.toString();
    }

    public static final class Builder {
        private final String name;
        private final List<String> eq = new ArrayList<>();
        private final List<Comp> sort = new ArrayList<>();

        private Builder(String name) {
            this.name = name;
        }

        /** An equality component. Must come before any sort component. */
        public Builder eq(String field) {
            if (!sort.isEmpty()) throw new IllegalStateException("Equality components come before sort components");
            eq.add(field);
            return this;
        }

        public Builder asc(String field) {
            sort.add(new Comp(field, false));
            return this;
        }

        public Builder desc(String field) {
            sort.add(new Comp(field, true));
            return this;
        }

        public IndexDef build() {
            return new IndexDef(name, new ArrayList<>(eq), new ArrayList<>(sort));
        }
    }
}
