package server;

import java.util.Arrays;
import java.util.List;

public class DiskApiNames {
    public static String[] diskAPIs;
    public static List<String> diskApiList;
    public static final String PUT = "put";
    public static final String GET = "get";
    public static final String CHECK = "check";
    public static final String LIST = "list";

    static {
        diskAPIs = new String[]{
                PUT, GET, CHECK, LIST, ApipApiNames.PING, ApipApiNames.SIGN_IN, ApipApiNames.SIGN_IN_ECC, ApipApiNames.WEBHOOK_POINT
        };
        diskApiList = Arrays.stream(diskAPIs).toList();
    }
}
