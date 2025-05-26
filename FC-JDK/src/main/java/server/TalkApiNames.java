package server;

import java.util.Arrays;
import java.util.List;

public class TalkApiNames {

    public static final String ASK_KEY = "askKey";
    public static final String ASK_DATA = "askData";
    public static final String ASK_HAT = "askHat";
    public static final String SHARE_KEY = "shareKey";
    public static final String SHARE_DATA = "shareData";
    public static final String SHARE_HAT = "shareHat";
    public static final String EXIT = "exit";
    public static List<String> talkApiList;
    public static String[] talkAPIs;
    static {
        talkAPIs = new String[]{
                TalkApiNames.ASK_KEY, TalkApiNames.ASK_DATA, TalkApiNames.ASK_HAT, TalkApiNames.SHARE_KEY, TalkApiNames.SHARE_DATA, TalkApiNames.SHARE_HAT, TalkApiNames.EXIT
        };
        talkApiList = Arrays.stream(talkAPIs).toList();
    }
}
