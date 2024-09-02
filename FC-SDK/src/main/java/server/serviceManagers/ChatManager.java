package server.serviceManagers;

import feip.feipData.Service;
import feip.feipData.serviceParams.ApipParams;
import feip.feipData.serviceParams.DiskParams;
import feip.feipData.serviceParams.Params;
import clients.apipClient.ApipClient;
import configure.ApiAccount;
import feip.feipData.serviceParams.TalkParams;

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
