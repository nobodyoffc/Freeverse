package fapi.chain;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import config.Settings;
import data.feipData.ServiceType;
import fapi.client.FapiClient;

/** Picks the chain source a server's settings provide. */
public final class ChainSources {

    private ChainSources() {}

    /**
     * The local Elasticsearch when there is one (a full server), else the configured upstream
     * FAPI client (a light server), else null: the server then runs without the chain.
     */
    public static ChainSource fromSettings(Settings settings) {
        if (settings == null) return null;
        if (settings.getClient(ServiceType.ES) instanceof ElasticsearchClient es) return new EsChainSource(es);
        for (ServiceType type : new ServiceType[]{ServiceType.FAPI, ServiceType.FAPI_No1_NrC7}) {
            if (settings.getClient(type) instanceof FapiClient) {
                return new UpstreamChainSource(() -> (FapiClient) settings.getClient(type), type.name());
            }
        }
        return null;
    }
}
