package server.serviceManagers;

import clients.ApipClient;
import data.feipData.Service;
import data.feipData.serviceParams.Params;
import data.feipData.serviceParams.SwapParams;
import config.ApiAccount;

import java.io.BufferedReader;

public class SwapHallManager extends ServiceManager {
    public SwapHallManager(Service service, ApiAccount apipAccount, BufferedReader br, byte[] symKey, Class<?> paramsClass) {
        super(service, apipAccount, br, symKey, paramsClass);
    }

    @Override
    public Params inputParams(byte[] symKey, BufferedReader br) {
        SwapParams swapParams = new SwapParams();
        swapParams.inputParams(br, symKey,(ApipClient) apipAccount.getClient());
        return swapParams;
    }

    @Override
    public Params updateParams(Params serviceParams, BufferedReader br, byte[] symKey) {
        serviceParams.updateParams(br,symKey,(ApipClient) apipAccount.getClient());
        return serviceParams;
    }
}
