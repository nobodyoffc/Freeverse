package initial;

import data.feipData.ServiceType;
import server.FcWebServerInitiator;

public class Initiator extends FcWebServerInitiator {

    @Override
    protected void setServiceName() {
        this.serverName = ServiceType.DISK.name();
    }
}
