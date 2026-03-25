package data.fcData;

import java.util.ArrayList;
import java.util.List;

/**
 * DOCK Item - Represents data stored for asynchronous delivery to recipients.
 * <p>
 * DOCK is a store-and-forward service that temporarily holds data for recipients
 * who may not be online at the time of sending.
 * <p>
 * Data is stored directly in Elasticsearch as Base64 (max 64KB default).
 * Large data should use the DISK service instead.
 * <p>
 * Recipients can be:
 * - FID (individual user)
 * - Team ID
 * - Group ID
 * - Room ID
 * <p>
 * Charging model:
 * - Sender pays: ingress fee + storage fee (size * days)
 * - Recipient pays: egress fee when retrieving
 * - Items expire based on TTL
 */
public class DockItem extends FcObject {
    
    /* id: Unique identifier for this dock item (sha256 hash) */

    /** FID of the sender */
    private String sender;
    
    /** 
     * List of intended recipients - can be FID, team ID, group ID, or room ID.
     * Any entity whose ID is in this list can retrieve the item.
     */
    private List<String> recipients;
    
    /** Size of the data in bytes */
    private Long size;
    
    /** Block height when this item was created */
    private Long createHeight;
    
    /** Block height when this item expires */
    private Long expireHeight;
    
    /** Timestamp when this item was created (milliseconds) */
    private Long createTime;
    
    /** Maximum storing days requested by sender */
    private Integer maxDays;
    
    /** Storage fee charged to sender (in satoshi) */
    private Long storageFee;
    
    /** Ingress fee charged to sender (in satoshi) */
    private Long ingressFee;
    
    /** Type of data stored (e.g., "IM", "HAT", "SYMKEY", "INVITATION"). Server-agnostic. */
    private String dataType;
    
    /** Binary data encoded as Base64, stored directly in ES */
    private String dataBase64;
    
    // Elasticsearch mapping
    public static final String MAPPINGS = "{\"mappings\":{\"properties\":{" +
            "\"id\":{\"type\":\"keyword\"}," +
            "\"sender\":{\"type\":\"keyword\"}," +
            "\"recipients\":{\"type\":\"keyword\"}," +
            "\"size\":{\"type\":\"long\"}," +
            "\"createHeight\":{\"type\":\"long\"}," +
            "\"expireHeight\":{\"type\":\"long\"}," +
            "\"createTime\":{\"type\":\"long\"}," +
            "\"maxDays\":{\"type\":\"integer\"}," +
            "\"storageFee\":{\"type\":\"long\"}," +
            "\"ingressFee\":{\"type\":\"long\"}," +
            "\"dataType\":{\"type\":\"keyword\"}," +
            "\"dataBase64\":{\"type\":\"text\",\"index\":false}" +
            "}}}";
    
    public DockItem() {
        this.recipients = new ArrayList<>();
    }
    
    public DockItem(String dockId, String sender, List<String> recipients, 
                    Long size, Long createHeight, Long expireHeight) {
        this.id = dockId;
        this.sender = sender;
        this.recipients = recipients != null ? new ArrayList<>(recipients) : new ArrayList<>();
        this.size = size;
        this.createHeight = createHeight;
        this.expireHeight = expireHeight;
        this.createTime = System.currentTimeMillis();
    }
    
    /**
     * Check if an ID is a valid recipient.
     * The ID can be a FID, team ID, group ID, or room ID.
     * 
     * @param id The ID to check (FID, team, group, or room)
     * @return true if the ID is in the recipients list
     */
    public boolean isRecipient(String id) {
        if (id == null || recipients == null) {
            return false;
        }
        return recipients.contains(id);
    }
    
    /**
     * Check if this item has expired based on the given current height.
     */
    public boolean isExpired(long currentHeight) {
        return expireHeight != null && currentHeight >= expireHeight;
    }
    
    /**
     * Calculate remaining days based on current height and blocks per day.
     * @param currentHeight Current block height
     * @param blocksPerDay Number of blocks per day (typically ~1440 for FCH)
     */
    public int getRemainingDays(long currentHeight, int blocksPerDay) {
        if (expireHeight == null || currentHeight >= expireHeight) {
            return 0;
        }
        long remainingBlocks = expireHeight - currentHeight;
        return (int) Math.ceil((double) remainingBlocks / blocksPerDay);
    }

    // Getters and Setters
    
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = recipients;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public Long getCreateHeight() {
        return createHeight;
    }

    public void setCreateHeight(Long createHeight) {
        this.createHeight = createHeight;
    }

    public Long getExpireHeight() {
        return expireHeight;
    }

    public void setExpireHeight(Long expireHeight) {
        this.expireHeight = expireHeight;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public Integer getMaxDays() {
        return maxDays;
    }

    public void setMaxDays(Integer maxDays) {
        this.maxDays = maxDays;
    }

    public Long getStorageFee() {
        return storageFee;
    }

    public void setStorageFee(Long storageFee) {
        this.storageFee = storageFee;
    }

    public Long getIngressFee() {
        return ingressFee;
    }

    public void setIngressFee(Long ingressFee) {
        this.ingressFee = ingressFee;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getDataBase64() {
        return dataBase64;
    }

    public void setDataBase64(String dataBase64) {
        this.dataBase64 = dataBase64;
    }
}
