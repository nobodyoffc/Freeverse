package server.serviceManagers;

import data.feipData.Service;
import data.feipData.serviceParams.SwapParams;
import config.ApiAccount;

import java.io.BufferedReader;

public class SwapHallManager extends ServiceManager {
    public SwapHallManager(Service service, ApiAccount apipAccount, BufferedReader br, byte[] symKey) {
        super(service, apipAccount, br, symKey);
    }

    @Override
    public Object inputParams(byte[] symKey, BufferedReader br) {
        SwapParams swapParams = new SwapParams();
        swapParams.inputParams(br, symKey);
        return swapParams;
    }

    @Override
    public Object updateParams(Object serviceParams, BufferedReader br, byte[] symKey) {
        if(serviceParams instanceof SwapParams swapParams) {
            swapParams.updateParams(br, symKey);
            return swapParams;
        }
        return serviceParams;
    }
}
