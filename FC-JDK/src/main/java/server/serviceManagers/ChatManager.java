package server.serviceManagers;

import data.feipData.Service;
import data.feipData.serviceParams.DiskParams;
import data.feipData.serviceParams.Params;
import clients.ApipClient;
import config.ApiAccount;
import data.feipData.serviceParams.TalkParams;

import java.io.BufferedReader;

public class ChatManager extends ServiceManager {

    public ChatManager(Service service, ApiAccount apipAccount, BufferedReader br, byte[] symKey, Class<?> paramsClass) {
        super(service, apipAccount, br, symKey, paramsClass);
    }

    @Override
    protected Params inputParams(byte[] symKey, BufferedReader br) {
        DiskParams diskParams = new DiskParams();
        diskParams.inputParams(br, symKey,(ApipClient) apipAccount.getClient());
        return diskParams;
    }

    @Override
    protected Params updateParams(Params serviceParams, BufferedReader br, byte[] symKey) {
        TalkParams talkParams = (TalkParams)serviceParams;
        talkParams.updateParams(br,symKey,null);
        return talkParams;
    }
}
