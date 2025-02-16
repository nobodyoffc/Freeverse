package initial;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import feip.feipData.Service;
import feip.feipData.Service.ServiceType;
import handlers.Handler.HandlerType;
import server.FcWebServerInitiator;


public class Initiator extends FcWebServerInitiator {

    @Override
    protected void setServiceName(){
        this.serverName = ServiceType.APIP.name();
    }

    @Override
    protected void setModules() {
        this.modules = new Object[]{
                Service.ServiceType.REDIS,
                HandlerType.SESSION,
                Service.ServiceType.NASA_RPC,
                Service.ServiceType.ES,
                HandlerType.MEMPOOL,
                HandlerType.CASH,
                HandlerType.ACCOUNT,
                HandlerType.WEBHOOK,
                HandlerType.NONCE
        };
    }

    @Override
    protected void setRunningModules() {
        this.runningModules= new Object[]{
                HandlerType.MEMPOOL,
                HandlerType.ACCOUNT,
                HandlerType.WEBHOOK,
                HandlerType.NONCE
        };
    }
}
