package server.serviceManagers;

import data.feipData.Service;
import config.ApiAccount;

import java.io.BufferedReader;

public class ApipManager extends ServiceManager {

    public ApipManager(Service service, ApiAccount apipAccount, BufferedReader br, byte[] symKey) {
        super(service, apipAccount, br, symKey);
    }

    @Override
    protected Object inputParams(byte[] symKey, BufferedReader br) {
        // APIP services use pricing fields in Service directly, no special params needed
        return null;
    }

    @Override
    protected Object updateParams(Object serviceParams, BufferedReader br, byte[] symKey) {
        // APIP services use pricing fields in Service directly, no special params needed
        return serviceParams;
    }
}
