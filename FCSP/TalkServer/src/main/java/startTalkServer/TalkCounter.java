package startTalkServer;

import feip.feipData.serviceParams.Params;
import server.Counter;
import server.Settings;

public class TalkCounter extends Counter {


    public TalkCounter(Settings settings, byte[] counterPriKey, Params params) {
        super(settings, counterPriKey, params);
    }

    @Override
    public void localTask() {
    }
}
