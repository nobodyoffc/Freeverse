package server.serviceManagers;

import config.ApiAccount;
import data.feipData.Service;

import java.io.BufferedReader;

public class TalkManager extends ServiceManager {

    public TalkManager(Service service, ApiAccount apipAccount, BufferedReader br, byte[] symKey) {
        super(service, apipAccount, br, symKey);
    }

    @Override
    protected Object inputParams(byte[] symKey, BufferedReader br) {
        // Talk services use pricing fields in Service directly, no special params needed
        return null;
    }

    @Override
    protected Object updateParams(Object serviceParams, BufferedReader br, byte[] symKey) {
        // Talk services use pricing fields in Service directly, no special params needed
        return serviceParams;
    }

}
