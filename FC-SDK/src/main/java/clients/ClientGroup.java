package clients;

import configure.ServiceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientGroup {
    private static final Logger log = LoggerFactory.getLogger(ClientGroup.class);
    
    private final ClientGroupType groupType;
    private final Map<String, Client> clientMap; // apiAccountId -> Client
    private int currentClientIndex;
    private final List<String> clientIds;

    public ClientGroup(ClientGroupType groupType) {
        this.groupType = groupType;
        this.clientMap = new HashMap<>();
        this.clientIds = new ArrayList<>();
        this.currentClientIndex = 0;
    }

    public void addClient(String apiAccountId, Client client) {
        if (client == null || apiAccountId == null) return;
        
        ServiceType expectedType = groupType.getServiceType();
        if (client instanceof ApipClient && expectedType != ServiceType.APIP ||
            client instanceof DiskClient && expectedType != ServiceType.DISK) {
            log.error("Client type doesn't match group type {}", groupType);
            return;
        }

        clientMap.put(apiAccountId, client);
        if (!clientIds.contains(apiAccountId)) {
            clientIds.add(apiAccountId);
        }
    }

    public void removeClient(String apiAccountId) {
        clientMap.remove(apiAccountId);
        clientIds.remove(apiAccountId);
    }

    public <T> T executeRequest(ClientRequest<T> request) {
        if (clientIds.isEmpty()) {
            log.error("No clients available in group {}", groupType);
            return null;
        }

        int attempts = 0;
        while (attempts < clientIds.size()) {
            String currentId = clientIds.get(currentClientIndex);
            Client currentClient = clientMap.get(currentId);
            
            try {
                T result = request.execute(currentClient);
                if (result != null) {
                    return result;
                }
            } catch (Exception e) {
                log.error("Request failed for client {}: {}", currentId, e.getMessage());
            }

            currentClientIndex = (currentClientIndex + 1) % clientIds.size();
            attempts++;
        }

        log.error("All clients failed to execute request in group {}", groupType);
        return null;
    }

    public void broadcastRequest(ClientAction action) {
        for (Map.Entry<String, Client> entry : clientMap.entrySet()) {
            try {
                action.execute(entry.getValue());
            } catch (Exception e) {
                log.error("Failed to execute action on client {}: {}", 
                    entry.getKey(), e.getMessage());
            }
        }
    }

    @FunctionalInterface
    public interface ClientRequest<T> {
        T execute(Client client);
    }

    @FunctionalInterface
    public interface ClientAction {
        void execute(Client client);
    }

    // Getters
    public ClientGroupType getGroupType() {
        return groupType;
    }

    public List<String> getClientIds() {
        return new ArrayList<>(clientIds);
    }

    public enum ClientGroupType {
        DISK_GROUP,
        APIP_GROUP,
        TALK_GROUP;

        public ServiceType getServiceType() {
            return switch (this) {
                case DISK_GROUP -> ServiceType.DISK;
                case APIP_GROUP -> ServiceType.APIP;
                case TALK_GROUP -> ServiceType.TALK;
            };
        }
    }
}