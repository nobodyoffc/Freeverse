package fapi.chain;

import com.google.gson.Gson;

import java.util.Map;

/** Reads the channel ("via") FID a payer names in the OpReturn of a top-up. */
public final class Via {

    private static final Gson GSON = new Gson();

    private Via() {}

    /** The via FID in a JSON OpReturn such as {"via":"F..."}, or null. */
    public static String parse(String opReturnText) {
        if (opReturnText == null || opReturnText.isEmpty()) return null;
        try {
            Map<?, ?> json = GSON.fromJson(opReturnText, Map.class);
            if (json != null && json.get("via") instanceof String via && looksLikeFid(via)) return via;
        } catch (Exception e) {
            // not JSON: no via
        }
        return null;
    }

    static boolean looksLikeFid(String fid) {
        return fid.length() >= 26 && fid.length() <= 35
                && (fid.startsWith("F") || fid.startsWith("1") || fid.startsWith("3"));
    }
}
