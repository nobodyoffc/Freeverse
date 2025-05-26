package initial;

import data.feipData.Service;
import server.FcWebServerInitiator;

public class Initiator extends FcWebServerInitiator {

    @Override
    protected void setServiceName() {
        this.serverName = Service.ServiceType.DISK.name();
    }
}
