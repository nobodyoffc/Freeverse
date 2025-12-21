package server;

import constants.ApipApiNames;
import org.bitcoinj.core.Ping;

import java.util.Arrays;
import java.util.List;

public class DiskApiNames {
    public static final String GET_SERVICE = "getService";
    public static final String PING = "ping";
    public static final String SIGN_IN = "signIn";
    public static String[] diskAPIs;
    public static List<String> diskApiList;
    public static final String PUT = "put";
    public static final String GET = "get";
    public static final String CHECK = "check";
    public static final String LIST = "list";
    public static final String COPY = "copy";
    public static final String PASTE = "paste";
    public static final String CARVE = "carve";
    public static final String NEW_ORDER = "newOrder";
    public static final String VER_1 = "v1";


    static {
        diskAPIs = new String[]{
                PUT, GET, CHECK, LIST, COPY, PASTE, PING, SIGN_IN, NEW_ORDER
        };
        diskApiList = Arrays.stream(diskAPIs).toList();
    }
}
