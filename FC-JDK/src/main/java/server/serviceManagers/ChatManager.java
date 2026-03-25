package server.serviceManagers;

import data.feipData.Service;
import config.ApiAccount;

import java.io.BufferedReader;

public class ChatManager extends ServiceManager {

    public ChatManager(Service service, ApiAccount apipAccount, BufferedReader br, byte[] symKey) {
        super(service, apipAccount, br, symKey);
    }

    @Override
    protected Object inputParams(byte[] symKey, BufferedReader br) {
        // Chat services use pricing fields in Service directly, no special params needed
        return null;
    }

    @Override
    protected Object updateParams(Object serviceParams, BufferedReader br, byte[] symKey) {
        // Chat services use pricing fields in Service directly, no special params needed
        return serviceParams;
    }
}
