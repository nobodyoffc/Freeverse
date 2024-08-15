package startAPIP;

import feip.feipData.Service;
import feip.feipData.serviceParams.ApipParams;
import feip.feipData.serviceParams.Params;
import configure.ApiAccount;
import server.serviceManagers.ServiceManager;

import java.io.BufferedReader;

public class ApipManager extends ServiceManager {

    public ApipManager(Service service, ApiAccount apipAccount, BufferedReader br, byte[] symKey, Class<ApipParams> paramsClass) {
        super(service, apipAccount, br, symKey, paramsClass);
    }

    @Override
    protected Params inputParams(byte[] symKey, BufferedReader br) {
        ApipParams apipParams = new ApipParams();
        apipParams.inputParams(br, symKey,null);
        return apipParams;
    }

    @Override
    protected void updateParams(Params serviceParams, BufferedReader br, byte[] symKey) {
        ApipParams apipParams = (ApipParams)serviceParams;
        apipParams.updateParams(br,symKey,null);
    }
}
