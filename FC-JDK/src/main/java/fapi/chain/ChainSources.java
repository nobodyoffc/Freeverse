package fapi.chain;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import config.Settings;
import data.feipData.ServiceType;
import fapi.client.FapiClient;

import java.util.function.Supplier;

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
        FapiClient upstream = settings.getUpstreamFapiClient();
        if (upstream == null) return null;
        String name = upstream.getServerUrl() != null ? upstream.getServerUrl() : upstream.getServiceSid();
        return new UpstreamChainSource(new PayingClient(settings), name);
    }

    /**
     * The settings' upstream client, made able to pay: the upstream bills this server, so the
     * client must top up its balance there on its own. Rebuilt when the module reconnects.
     */
    static final class PayingClient implements Supplier<FapiClient> {
        private final Settings settings;
        private FapiClient base;
        private FapiClient paying;

        PayingClient(Settings settings) {
            this.settings = settings;
        }

        @Override
        public synchronized FapiClient get() {
            FapiClient current = settings.getUpstreamFapiClient();
            if (current == null) return null;
            if (current != base) {
                base = current;
                paying = current.getAutoRechargeManager() != null ? current : current.withSettings(settings);
            }
            return paying;
        }
    }
}
