package server.serviceManagers;

import clients.ApipClient;
import config.ApiAccount;
import data.feipData.Service;
import data.feipData.serviceParams.TalkParams;
import data.feipData.serviceParams.Params;

import java.io.BufferedReader;

public class TalkManager extends ServiceManager {

    public TalkManager(Service service, ApiAccount apipAccount, BufferedReader br, byte[] symKey, Class<?> paramsClass) {
        super(service, apipAccount, br, symKey, paramsClass);
    }

    @Override
    protected Params inputParams(byte[] symKey, BufferedReader br) {
        TalkParams talkParams = new TalkParams();
        talkParams.inputParams(br, symKey,(ApipClient) apipAccount.getClient());
        return talkParams;
    }

    @Override
    protected Params updateParams(Params serviceParams, BufferedReader br, byte[] symKey) {
        TalkParams talkParams = (TalkParams)serviceParams;
        talkParams.updateParams(br, symKey,(ApipClient) apipAccount.getClient());
        return talkParams;
    }

}
