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

import static config.Settings.getFreeApipClient;
import static data.feipData.Service.ServiceType.APIP;

public class ClientGroup implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Service.ServiceType groupType;
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

    public ClientGroup(Service.ServiceType groupType) {
        this.groupType = groupType;
        this.accountIds = new ArrayList<>();
        this.clientMap = new HashMap<>();
        this.strategy = GroupStrategy.USE_FIRST; // default strategy
    }

    public static void main(String[] args) {
        ClientGroup clientGroup = new ClientGroup(Service.ServiceType.APIP);
        clientGroup.getAccountIds().add("account id 1");
        clientGroup.setStrategy(GroupStrategy.USE_ALL);
        Map<Service.ServiceType,ClientGroup>  map = new HashMap<>();
        map.put(Service.ServiceType.APIP,clientGroup);
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
        for (String accountId:accountIds) {
            ApiAccount apiAccount = configure.getApiAccountMap().get(accountId);
            if(apiAccount==null) {
                apiAccount = configure.getApiAccount(symKey, settings.getMainFid(), groupType, (ApipClient) settings.getClient(Service.ServiceType.APIP));
                if (apiAccount != null) {
                    this.accountIds.add(apiAccount.getId());
                    addClient(apiAccount.getId(), apiAccount.getClient());
                    addApiAccount(apiAccount);
                    return;
                }
                System.out.println("Failed to get ApiAccount "+accountId+". Check the apiAccount in config file.");
                System.exit(-1);
            }

            ApipClient apipClient;
            switch (groupType) {
                case ES, REDIS, NASA_RPC,APIP -> {}
                default -> {
                    apipClient = (ApipClient) settings.getClient(APIP);
                    if(apipClient==null) apipClient = getFreeApipClient();
                    apiAccount.setApipClient(apipClient);
                }
            }

            addApiAccount(apiAccount);
            Object client = apiAccount.connectApi(configure.getApiProviderMap().get(apiAccount.getProviderId()), symKey, br);
            if(client==null)break;
            apiAccount.setClient(client);
            addClient(apiAccount.getId(), client);
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
    public Service.ServiceType getGroupType() {
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

    public void setGroupType(Service.ServiceType groupType) {
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