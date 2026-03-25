package server.serviceManagers;

import data.feipData.Service;
import data.feipData.serviceParams.DiskParams;
import config.ApiAccount;

import java.io.BufferedReader;

public class DiskManager extends ServiceManager {


    public DiskManager(Service service, ApiAccount apipAccount, BufferedReader br, byte[] symKey) {
        super(service, apipAccount, br, symKey);
    }

    @Override
    protected Object inputParams(byte[] symKey, BufferedReader br) {
        DiskParams diskParams = new DiskParams();
        diskParams.inputParams(br, symKey);
        return diskParams;
    }

    @Override
    protected Object updateParams(Object params, BufferedReader br, byte[] symKey) {
        DiskParams diskParams = (DiskParams) params;
        if(diskParams != null) {
            diskParams.updateParams(br, symKey);
        }
        return diskParams;
    }
}
