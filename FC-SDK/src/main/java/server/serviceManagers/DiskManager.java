package server.serviceManagers;

import feip.feipData.Service;
import clients.ApipClient;
import feip.feipData.serviceParams.DiskParams;
import feip.feipData.serviceParams.Params;
import configure.ApiAccount;

import java.io.BufferedReader;

public class DiskManager extends ServiceManager {


    public DiskManager(Service service, ApiAccount apipAccount, BufferedReader br, byte[] symKey, Class<?> paramsClass) {
        super(service, apipAccount, br, symKey, paramsClass);
    }

    @Override
    protected Params inputParams(byte[] symKey, BufferedReader br) {
        DiskParams diskParams = new DiskParams();
        ApipClient apipClient =null;
        if(apipAccount!=null)apipClient = (ApipClient) apipAccount.getClient();
        diskParams.inputParams(br, symKey, apipClient);
        return diskParams;
    }

    @Override
    protected Params updateParams(Params params, BufferedReader br, byte[] symKey) {
        DiskParams diskParams = (DiskParams) params;
        diskParams.updateParams(br,symKey);
        return diskParams;
    }
}
