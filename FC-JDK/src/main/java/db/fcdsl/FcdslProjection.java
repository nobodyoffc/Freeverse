package db.fcdsl;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies a request's {@code fields} / {@code noFields} to items, as ES source filtering did:
 * with {@code fields}, only those top-level fields are kept; {@code noFields} are then removed.
 * Names not present in an item are ignored, as ES ignored them.
 */
public final class FcdslProjection {

    private static final Gson GSON = new Gson();

    private FcdslProjection() {}

    /** True when the request asks for no projection, so items can be returned as they are. */
    public static boolean isNone(FcdslQuery<?> q) {
        return (q.fields() == null || q.fields().isEmpty()) && (q.noFields() == null || q.noFields().isEmpty());
    }

    public static <T> List<JsonObject> project(FcdslQuery<T> q, List<T> items) {
        Set<String> keep = q.fields() == null || q.fields().isEmpty() ? null : new HashSet<>(q.fields());
        Set<String> drop = q.noFields() == null ? Set.of() : new HashSet<>(q.noFields());
        List<JsonObject> out = new ArrayList<>(items.size());
        for (T item : items) {
            JsonObject full = GSON.toJsonTree(item).getAsJsonObject();
            JsonObject o = new JsonObject();
            for (Map.Entry<String, JsonElement> e : full.entrySet()) {
                if (keep != null && !keep.contains(e.getKey())) continue;
                if (drop.contains(e.getKey())) continue;
                o.add(e.getKey(), e.getValue());
            }
            out.add(o);
        }
        return out;
    }
}
