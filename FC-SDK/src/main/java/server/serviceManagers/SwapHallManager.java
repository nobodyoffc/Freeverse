package server.serviceManagers;

import clients.apipClient.ApipClient;
import feip.feipData.Service;
import feip.feipData.serviceParams.Params;
import feip.feipData.serviceParams.SwapParams;
import configure.ApiAccount;

import java.io.BufferedReader;

public class SwapHallManager extends ServiceManager {
    public SwapHallManager(Service service, ApiAccount apipAccount, BufferedReader br, byte[] symKey, Class<?> paramsClass) {
        super(service, apipAccount, br, symKey, paramsClass);
    }


//    public void publishService(byte[] symKey,BufferedReader br) {
//        if(Inputer.askIfYes(br,"Publish a new service? y/n")){
//            SwapManager swapManager = new SwapManager(apipAccount, SwapParams.class);
//            swapManager.publishService(symKey, br);
//            System.out.println("Wait for a few minutes and try to start again.");
//            System.exit(0);
//        }
//    }

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
