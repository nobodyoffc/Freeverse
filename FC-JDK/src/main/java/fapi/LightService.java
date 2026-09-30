package fapi;

import data.feipData.ApiGroupType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a light server's on-chain Service may declare. The apps pick services by their
 * components, so the declaration must say exactly what the server runs:
 * <ul>
 *   <li>only CALL, DISK, DOCK, ROAD and MAP: BASE needs the local chain;</li>
 *   <li>ROAD always with MAP: ROAD delivers only to devices in its own MAP;</li>
 *   <li>components by their full names ({@code DOCK@No1_NrC7}), which the apps and the server
 *       match; a bare {@code DOCK} is written out in full.</li>
 * </ul>
 * Other entries (not FAPI components) are left as they are.
 */
public final class LightService {

    /** The components a light server can run, by their full names. */
    public static final List<String> RUNNABLE = List.of(ApiGroupType.CALL_NO1_NRC7, ApiGroupType.DISK_NO1_NRC7,
            ApiGroupType.DOCK_NO1_NRC7, ApiGroupType.ROAD_NO1_NRC7, ApiGroupType.MAP_NO1_NRC7);

    private LightService() {}

    /**
     * The components a light server should declare, given what was typed.
     *
     * @param notes receives one line for each change made, for the user to read
     */
    public static List<String> components(List<String> typed, List<String> notes) {
        Set<String> out = new LinkedHashSet<>();
        if (typed != null) {
            for (String t : typed) {
                if (t == null || t.isBlank()) continue;
                String name = t.trim();
                if (isComponent(name, ApiGroupType.BASE_NO1_NRC7)) {
                    notes.add("Dropped " + name + ": a light server has no local chain to serve BASE from.");
                    continue;
                }
                String full = runnable(name);
                if (full != null && !full.equals(name)) notes.add("Wrote " + name + " as " + full + ".");
                out.add(full != null ? full : name);
            }
        }
        if (out.contains(ApiGroupType.ROAD_NO1_NRC7) && !out.contains(ApiGroupType.MAP_NO1_NRC7)) {
            out.add(ApiGroupType.MAP_NO1_NRC7);
            notes.add("Added " + ApiGroupType.MAP_NO1_NRC7 + ": ROAD delivers only to devices in its own MAP.");
        }
        boolean any = false;
        for (String c : out) any |= RUNNABLE.contains(c);
        if (!any) {
            notes.add("None of CALL, DISK, DOCK, ROAD or MAP is declared: a light server with this Service won't start.");
        }
        return new ArrayList<>(out);
    }

    /** The services list with the upstream's SID added, if it isn't there yet. */
    public static List<String> withUpstream(List<String> services, String upstreamSid) {
        List<String> out = services == null ? new ArrayList<>() : new ArrayList<>(services);
        if (upstreamSid != null && !upstreamSid.isBlank() && !out.contains(upstreamSid)) out.add(upstreamSid);
        return out;
    }

    /** The full name of a runnable component typed as {@code DOCK} or {@code dock@no1_nrc7}, or null. */
    static String runnable(String typed) {
        for (String full : RUNNABLE) if (isComponent(typed, full)) return full;
        return null;
    }

    private static boolean isComponent(String typed, String full) {
        String bare = full.substring(0, full.indexOf('@'));
        return typed.equalsIgnoreCase(full) || typed.equalsIgnoreCase(bare);
    }
}
