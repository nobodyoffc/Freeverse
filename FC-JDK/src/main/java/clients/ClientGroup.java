package clients;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;

import config.Settings;
import com.google.gson.Gson;
import config.ApiAccount;
import config.Configure;
import data.feipData.Service;
import config.ApiProvider;
import data.feipData.ServiceType;
import fapi.client.FapiClient;
import fudp.node.FudpNode;
import core.crypto.KeyTools;
import fudp.message.PongMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Hex;

import java.util.concurrent.TimeUnit;

import static config.Settings.getDefaultApipClient;
import static config.Settings.getDefaultFapiClient;

public class ClientGroup implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(ClientGroup.class);

    private ServiceType groupType;
    private List<String> accountIds;
    private GroupStrategy strategy;
    private transient int roundRobinIndex = 0;
    private transient Map<String, Object> clientMap;
    private transient Map<String, ApiAccount> apiAccountMap;

    public enum GroupStrategy {
        USE_FIRST,           // Use the first available client
        USE_ANY_VALID,       // Use any valid client
        USE_ALL,            // Use all clients
        USE_ONE_RANDOM,     // Use a random client
        USE_ONE_ROUND_ROBIN; // Use clients in round-robin fashion

        @Override
        public String toString() {
            return name();
        }
    }

    public ClientGroup(ServiceType groupType) {
        this.groupType = groupType;
        this.accountIds = new ArrayList<>();
        this.clientMap = new HashMap<>();
        this.strategy = GroupStrategy.USE_FIRST; // default strategy
    }

    public static void main(String[] args) {
        ClientGroup clientGroup = new ClientGroup(ServiceType.APIP);
        clientGroup.getAccountIds().add("account id 1");
        clientGroup.setStrategy(GroupStrategy.USE_ALL);
        Map<ServiceType,ClientGroup>  map = new HashMap<>();
        map.put(ServiceType.APIP,clientGroup);
        System.out.println(new Gson().toJson(map));
    }

    public void addClient(String accountId, Object client) {
        if (!accountIds.contains(accountId)) {
            accountIds.add(accountId);
        }
        if(clientMap==null)clientMap = new HashMap<>();
        clientMap.put(accountId, client);
    }

    public void addToFirstClient(String accountId, Object client) {
        accountIds.add(0,accountId);
        if(clientMap==null)clientMap = new HashMap<>();
        clientMap.put(accountId, client);
    }

    public void addApiAccount(ApiAccount apiAccount) {
        if (apiAccountMap == null) apiAccountMap = new HashMap<>();
        apiAccountMap.put(apiAccount.getId(), apiAccount);
    }

    public void connectAllClients(Configure configure, Settings settings, byte[] symKey, BufferedReader br) {
        FapiClient defaultFapiClient = null;
        ApipClient apipClientForDefault = null;
        if (!(groupType == ServiceType.ES || groupType == ServiceType.REDIS || groupType == ServiceType.NASA_RPC || groupType == ServiceType.APIP || groupType.isFapi()) ) {
            defaultFapiClient = getDefaultFapiClient(settings);
            apipClientForDefault = (ApipClient) settings.getClient(ServiceType.APIP);
            if (defaultFapiClient == null && apipClientForDefault == null) {
                apipClientForDefault = getDefaultApipClient();
            }
        }

        for (int i = 0; i < accountIds.size(); i++) {
            String accountId = accountIds.get(i);
            ApiAccount apiAccount = configure.getApiAccountMap().get(accountId);
            if(apiAccount==null) {
                apiAccount = configure.getApiAccount(symKey, settings.getMainFid(), groupType, apipClientForDefault, defaultFapiClient);
                if (apiAccount != null) {
                    this.accountIds.add(apiAccount.getId());
                    addClient(apiAccount.getId(), apiAccount.getClient());
                    addApiAccount(apiAccount);
                    return;
                }
                System.out.println("Failed to get ApiAccount "+accountId+". Check the apiAccount in config file.");
                System.exit(-1);
            }


            ApipClient apipClient = null;
            FapiClient fapiClient = null;

            switch (groupType) {
                case ES, REDIS, NASA_RPC,APIP,FAPI,FAPI_No1_NrC7 -> {}
                default -> {
                    fapiClient = getDefaultFapiClient(settings);
                    apipClient = getDefaultApipClient();

                    if(fapiClient==null && apipClient ==null ){
                        System.out.println("Failed to get default FapiClient and ApipClient");
                        return;
                    }

                    apiAccount.setFapiClient(fapiClient);
                    apiAccount.setApipClient(apipClient);

                }
            }

            addApiAccount(apiAccount);
            Object client = apiAccount.connectApi(configure.getApiProviderMap().get(apiAccount.getProviderId()), symKey, settings.getFudpNode(), br);
            if(client==null)break;
            apiAccount.setClient(client);
            addClient(apiAccount.getId(), client);
        }

        if (groupType.isFapi() && accountIds.isEmpty()) {
            System.out.println("No FAPI accounts loaded; handled later.");
        }
    }

    private boolean connectFapiAccount(ApiAccount apiAccount, Configure configure, Settings settings) {
        ApiProvider provider = configure.getApiProviderMap().get(apiAccount.getProviderId());
        if (provider == null) {
            System.out.println("No ApiProvider found for FAPI account " + apiAccount.getId());
            return false;
        }
        FapiClient.Endpoint endpoint = FapiClient.parseEndpoint(provider.getApiUrl());
        if (endpoint == null) {
            System.out.println("Bad FAPI endpoint: " + provider.getApiUrl());
            return false;
        }
        FudpNode fudpNode = (FudpNode) settings.initNode("FUDP");
        if (fudpNode == null) {
            System.out.println("FUDP node not ready, skip FAPI connect.");
            return false;
        }
        // Fast path: provider already has peer pubkey and service info; just ping for connectivity
        // Since ApiProvider extends Service, check if provider has essential fields populated
        if (provider.getDealerPubkey() != null && provider.getId() != null) {
            log.debug("connectFapiAccount: trying fast path for {} (pubkey present, id present)", provider.getApiUrl());
            String peerId = KeyTools.pubkeyToFchAddr(Hex.fromHex(provider.getDealerPubkey()));
            fudpNode.addPeer(peerId, Hex.fromHex(provider.getDealerPubkey()), endpoint.host(), endpoint.port());
            try {
                PongMessage pong = fudpNode.pingAwaitPong(peerId, false, FapiClient.DEFAULT_PING_TIMEOUT_MS)
                        .get(FapiClient.DEFAULT_PING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (pong != null) {
                    log.debug("connectFapiAccount: fast path succeeded for {}", provider.getApiUrl());
                    // ApiProvider IS a Service now, use it directly
                    apiAccount.setService(provider);
                    apiAccount.setClient(new FapiClient(fudpNode, peerId, provider.getId(), 30, settings));
                    return true;
                } else {
                    log.debug("connectFapiAccount: fast path got null pong from {}, falling back to discovery", provider.getApiUrl());
                }
            } catch (Exception e) {
                log.debug("connectFapiAccount: fast path failed for {} ({}), falling back to discovery", provider.getApiUrl(), e.getMessage());
            }
        } else {
            log.debug("connectFapiAccount: skipping fast path for {} (pubkey={}, id={})", 
                provider.getApiUrl(), 
                provider.getDealerPubkey() != null ? "present" : "null",
                provider.getId() != null ? "present" : "null");
        }
        log.debug("connectFapiAccount: starting full discovery for {}", provider.getApiUrl());
        try {
            FapiClient.DiscoveryResult discovery = FapiClient.discoverViaHelloAndPing(
                    fudpNode,
                    endpoint.host(),
                    endpoint.port(),
                    FapiClient.DEFAULT_HELLO_TIMEOUT_MS,
                    FapiClient.DEFAULT_PING_TIMEOUT_MS
            );
            log.debug("connectFapiAccount: discovery completed for {}, peerId={}, services={}", 
                provider.getApiUrl(), 
                discovery.getPeerId(),
                discovery.getServices() != null ? discovery.getServices().size() : 0);
            if (discovery.getServices() == null || discovery.getServices().isEmpty()) {
                System.out.println("No services from FAPI endpoint " + provider.getApiUrl());
                return false;
            }
            Service service = discovery.getServices().stream()
                    .filter(s -> provider.getId().equals(s.getId()))
                    .findFirst()
                    .orElse(discovery.getServices().get(0));
            log.debug("connectFapiAccount: selected service SID={}, name={}", service.getId(), service.getStdName());
            FapiClient client = new FapiClient(fudpNode, discovery.getPeerId(), service.getId(), 30, settings);
            apiAccount.setService(service);
            apiAccount.setClient(client);
            
            // Update provider with full service info for future fast-path reconnects
            if (provider.getStdName() == null) {
                log.debug("connectFapiAccount: updating provider with service info for future fast-path");
                provider.fromFcService(service);
                provider.setDealerPubkey(Hex.toHex(discovery.getPublicKey()));
                Configure.saveConfig();
            }
            
            log.info("connectFapiAccount: successfully connected to FAPI endpoint {}, peerId={}, SID={}", 
                provider.getApiUrl(), discovery.getPeerId(), service.getId());
            return true;
        } catch (Exception e) {
            log.error("connectFapiAccount: failed to connect FAPI endpoint {}", provider.getApiUrl(), e);
            System.out.println("Failed to connect FAPI endpoint " + provider.getApiUrl() + ": " + 
                (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            return false;
        } catch (Throwable e) {
            log.error("connectFapiAccount: unexpected error connecting to {}", provider.getApiUrl(), e);
            throw new RuntimeException(e);
        }
    }

    public String getAccountId() {
        if (accountIds.isEmpty()) {
            return null;
        }

        switch (strategy) {
            case USE_FIRST, USE_ALL -> {
                return accountIds.get(0);
            }
            case USE_ANY_VALID -> {
                // Return first valid client found
                for (String accountId : accountIds) {
                    Object client = clientMap.get(accountId);
                    if (client!=null) {
                        return accountId;
                    }
                }
                return null;
            }
            case USE_ONE_RANDOM -> {
                return accountIds.get(new Random().nextInt(accountIds.size()));
            }
            case USE_ONE_ROUND_ROBIN -> {
                return accountIds.get(roundRobinIndex++ % accountIds.size());
            }
            default -> {
                return null;
            }
        }
    }

    public Object getClient() {
        if (accountIds.isEmpty()) {
            return null;
        }

        switch (strategy) {
            case USE_FIRST -> {
                return clientMap==null ?null:clientMap.get(accountIds.get(0));
            }
            case USE_ANY_VALID -> {
                // Return first valid client found
                for (String accountId : accountIds) {
                    Object client = clientMap.get(accountId);
                    if (client!=null) {
                        return client;
                    }
                }
                return null;
            }
            case USE_ALL -> {
                return clientMap;
            }
            case USE_ONE_RANDOM -> {
                String randomId = accountIds.get(new Random().nextInt(accountIds.size()));
                return clientMap.get(randomId);
            }
            case USE_ONE_ROUND_ROBIN -> {
                String nextId = accountIds.get(roundRobinIndex++ % accountIds.size());
                return clientMap.get(nextId);
            }
            default -> {
                return null;
            }
        }
    }

    // Helper method to check if a client is valid/active
//    private boolean isClientValid(Object client) {
//        // Implement client validation logic
//
//        return client != null; // Basic validation, enhance as needed
//    }

    private void initTransientFields() {
        if (clientMap == null) {
            clientMap = new HashMap<>();
        }
    }


    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initTransientFields();
    }

    // Getters and setters
    public ServiceType getGroupType() {
        return groupType;
    }

    public List<String> getAccountIds() {
        return accountIds;
    }

    public Map<String, Object> getClientMap() {
        return clientMap;
    }

    public GroupStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(GroupStrategy strategy) {
        this.strategy = strategy;
    }

    public void setGroupType(ServiceType groupType) {
        this.groupType = groupType;
    }

    public void setAccountIds(List<String> accountIds) {
        this.accountIds = accountIds;
    }

    public void setClientMap(Map<String, Object> clientMap) {
        this.clientMap = clientMap;
    }

    public int getRoundRobinIndex() {
        return roundRobinIndex;
    }

    public void setRoundRobinIndex(int roundRobinIndex) {
        this.roundRobinIndex = roundRobinIndex;
    }
}
