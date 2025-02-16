package appTools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import feip.feipData.Service;

public class ServiceGroup {
    private String groupAlias;
    private Service.ServiceType serviceType;
    private List<String> accountIds;
    private Map<String, Object> clientMap; // accountId -> client mapping
    private ServiceGroupStrategy strategy;

    public enum ServiceGroupStrategy {
        USE_FIRST,
        USE_ANY_VALID,
        USE_ALL,
        USE_ONE_RANDOM,
        USE_ONE_ROUND_ROBIN
    }

    public ServiceGroup(String groupAlias, Service.ServiceType serviceType) {
        this.groupAlias = groupAlias;
        this.serviceType = serviceType;
        this.accountIds = new ArrayList<>();
        this.clientMap = new HashMap<>();
        this.strategy = ServiceGroupStrategy.USE_ANY_VALID; // default strategy
    }

    // Add methods to manage accounts and clients
    public void addAccount(String accountId, Object client) {
        if (!accountIds.contains(accountId)) {
            accountIds.add(accountId);
        }
        clientMap.put(accountId, client);
    }

    public Object getClient() {
        if (accountIds.isEmpty()) {
            return null;
        }

        switch (strategy) {
            case USE_FIRST:
                return clientMap.get(accountIds.get(0));
            case USE_ANY_VALID:
                    for(String accountId : accountIds){
                    Object client = clientMap.get(accountId);
                    if(client != null){
                        return client;
                    }
                }
            case USE_ALL:
                return clientMap; // Return all clients
            case USE_ONE_RANDOM:
                String randomId = accountIds.get(new Random().nextInt(accountIds.size()));
                return clientMap.get(randomId);
            case USE_ONE_ROUND_ROBIN:
                // Implement round-robin selection
                String nextId = accountIds.get(roundRobinIndex++ % accountIds.size());
                return clientMap.get(nextId);
            default:
                return null;
        }
    }

    // Getters and setters
    private int roundRobinIndex = 0;

    public String getGroupAlias() {
        return groupAlias;
    }

    public Service.ServiceType getServiceType() {
        return serviceType;
    }

    public List<String> getAccountIds() {
        return accountIds;
    }

    public Map<String, Object> getClientMap() {
        return clientMap;
    }

    public ServiceGroupStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(ServiceGroupStrategy strategy) {
        this.strategy = strategy;
    }
} 