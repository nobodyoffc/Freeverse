package initial;

import data.feipData.Service.ServiceType;
import server.FcWebServerInitiator;


public class Initiator extends FcWebServerInitiator {

    @Override
    protected void setServiceName(){
        this.serverName = ServiceType.APIP.name();
    }
}
