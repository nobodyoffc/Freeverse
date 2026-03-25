package fapi.components.disk;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import core.crypto.Hash;
import data.fcData.DiskItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.DateUtils;
import utils.Hex;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Clean disk storage handler for FAPI.
 * Handles file storage with content-addressable naming (SHA256x2 hash as DID).
 * 
 * Features:
 * - Content-addressable: File name = SHA256x2(content)
 * - Hierarchical storage: {root}/{d[0:2]}/{d[2:4]}/{d[4:6]}/{d[6:8]}/{did}
 * - Atomic writes: Write to temp file, then rename
 * - Elasticsearch metadata storage
 * 
 * Note: This is a clean implementation, not reusing managers/DiskManager.
 */
public class FapiDiskHandler {
    
    private static final Logger log = LoggerFactory.getLogger(FapiDiskHandler.class);
    
    // DID format: 64 hex characters (SHA256x2)
    private static final Pattern DID_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");
    
    private final Path storageRoot;
    private final ElasticsearchClient esClient;
    private final String indexName;
    
    /**
     * Create a new FapiDiskHandler.
     * 
     * @param storageRoot Root directory for file storage
     * @param esClient Elasticsearch client for metadata
     * @param indexName Elasticsearch index name for DiskItem documents
     */
    public FapiDiskHandler(Path storageRoot, ElasticsearchClient esClient, String indexName) {
        this.storageRoot = storageRoot;
        this.esClient = esClient;
        this.indexName = indexName;
        
        // Ensure storage root exists
        try {
            Files.createDirectories(storageRoot);
            log.info("FapiDiskHandler initialized: storageRoot={}, index={}", storageRoot, indexName);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create storage root: " + storageRoot, e);
        }
    }
    
    /**
     * Store data and return metadata.
     * 
     * @param data File content
     * @param permanent If true, no expiration (carve); otherwise use dataLifeDays
     * @param dataLifeDays Expiration in days (only used if permanent=false)
     * @return DiskItem with did, size, since, expire
     * @throws IOException if storage fails
     */
    public DiskItem store(byte[] data, boolean permanent, long dataLifeDays) throws IOException {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }
        
        // Compute DID (SHA256x2 of content)
        String did = computeDid(data);
        
        // Build storage path
        Path filePath = buildPath(did);
        
        // Check if file already exists — skip re-write, just extend expire
        if (Files.exists(filePath)) {
            log.debug("File already exists: did={}", did);
            DiskItem existing = getMetadata(did);
            if (existing != null) {
                return extendExpire(existing, permanent, dataLifeDays);
            }
        }
        
        // Create parent directories
        Files.createDirectories(filePath.getParent());
        
        // Atomic write: write to temp file, then rename
        Path tempFile = filePath.resolveSibling(did + ".tmp." + UUID.randomUUID().toString().substring(0, 8));
        try {
            Files.write(tempFile, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tempFile, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Clean up temp file on failure
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
            }
            throw e;
        }
        
        // Create metadata
        long now = System.currentTimeMillis();
        Long expire = permanent ? null : now + DateUtils.dayToLong(dataLifeDays);
        DiskItem diskItem = new DiskItem(did, now, expire, data.length);
        
        // Index to Elasticsearch
        indexMetadata(diskItem);
        
        log.info("Stored file: did={}, size={}, permanent={}", did, data.length, permanent);
        return diskItem;
    }
    
    /**
     * Retrieve file content by DID.
     * 
     * @param did SHA256x2 hash (64 hex chars)
     * @return File content bytes, or null if not found
     */
    public byte[] retrieve(String did) {
        if (!isValidDid(did)) {
            log.warn("Invalid DID format: {}", did);
            return null;
        }
        
        Path filePath = buildPath(did);
        
        if (!Files.exists(filePath)) {
            log.debug("File not found: did={}", did);
            return null;
        }
        
        try {
            byte[] content = Files.readAllBytes(filePath);
            
            // Verify content hash matches DID
            String computedDid = computeDid(content);
            if (!did.equalsIgnoreCase(computedDid)) {
                log.error("Content hash mismatch: expected={}, computed={}", did, computedDid);
                return null;
            }
            
            return content;
        } catch (IOException e) {
            log.error("Failed to read file: did={}, error={}", did, e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if file exists and return metadata from Elasticsearch.
     * 
     * @param did SHA256x2 hash
     * @return DiskItem from ES, or null if not found
     */
    public DiskItem getMetadata(String did) {
        if (!isValidDid(did)) {
            return null;
        }
        
        if (esClient == null) {
            log.warn("Elasticsearch client not available");
            return null;
        }
        
        try {
            GetResponse<DiskItem> response = esClient.get(g -> g
                    .index(indexName)
                    .id(did), DiskItem.class);
            
            if (response.found() && response.source() != null) {
                return response.source();
            }
            return null;
        } catch (IOException e) {
            log.error("Failed to get metadata from ES: did={}, error={}", did, e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if file exists on disk.
     * 
     * @param did SHA256x2 hash
     * @return true if file exists
     */
    public boolean exists(String did) {
        if (!isValidDid(did)) {
            return false;
        }
        
        Path filePath = buildPath(did);
        return Files.exists(filePath);
    }
    
    /**
     * Check if file exists and return combined info (file + metadata).
     * 
     * @param did SHA256x2 hash
     * @return DiskCheckResult with exists flag and metadata
     */
    public DiskCheckResult check(String did) {
        if (!isValidDid(did)) {
            return new DiskCheckResult(false, null);
        }
        
        boolean fileExists = exists(did);
        DiskItem metadata = getMetadata(did);
        
        return new DiskCheckResult(fileExists, metadata);
    }
    
    /**
     * Delete file by DID.
     * 
     * @param did SHA256x2 hash
     * @return true if deletion successful
     */
    public boolean delete(String did) {
        if (!isValidDid(did)) {
            return false;
        }
        
        Path filePath = buildPath(did);
        
        try {
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("Deleted file: did={}", did);
            }
            return deleted;
        } catch (IOException e) {
            log.error("Failed to delete file: did={}, error={}", did, e.getMessage());
            return false;
        }
    }
    
    /**
     * Store data from an InputStream (streaming, avoids loading entire file into memory).
     * Writes to a temp file while computing SHA256x2 hash incrementally,
     * then renames to the content-addressed path.
     *
     * @param input       InputStream providing file content
     * @param size        Expected size of the data (used for metadata; -1 if unknown)
     * @param permanent   If true, no expiration (carve); otherwise use dataLifeDays
     * @param dataLifeDays Expiration in days (only used if permanent=false)
     * @return DiskItem with did, size, since, expire
     * @throws IOException if storage fails
     */
    public DiskItem storeFromStream(InputStream input, long size, boolean permanent, long dataLifeDays) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }

        // Create temp directory and temp file
        Path tempDir = storageRoot.resolve("temp");
        Files.createDirectories(tempDir);
        Path tempFile = Files.createTempFile(tempDir, "upload-", ".tmp");

        long actualSize;
        byte[] didBytes;

        try {
            // Write to temp file while computing hash in a single pass
            try (OutputStream out = Files.newOutputStream(tempFile, StandardOpenOption.TRUNCATE_EXISTING)) {
                didBytes = Hash.sha256x2CopyStream(input, out);
            }
            if (didBytes == null) {
                throw new IOException("Failed to compute hash during streaming store");
            }
            actualSize = Files.size(tempFile);
        } catch (IOException e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }

        String did = Hex.toHex(didBytes);

        // Build final storage path
        Path filePath = buildPath(did);

        // Check if file already exists — skip re-write, just extend expire
        if (Files.exists(filePath)) {
            Files.deleteIfExists(tempFile);
            log.debug("File already exists (streaming): did={}", did);
            DiskItem existing = getMetadata(did);
            if (existing != null) {
                return extendExpire(existing, permanent, dataLifeDays);
            }
            // Metadata unavailable (no ES), construct from known values
            long now = System.currentTimeMillis();
            Long expire = permanent ? null : now + DateUtils.dayToLong(dataLifeDays);
            DiskItem fallback = new DiskItem(did, now, expire, actualSize);
            indexMetadata(fallback);
            return fallback;
        }

        // Create parent directories and atomically move
        Files.createDirectories(filePath.getParent());
        try {
            Files.move(tempFile, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }

        // Create and index metadata
        long now = System.currentTimeMillis();
        Long expire = permanent ? null : now + DateUtils.dayToLong(dataLifeDays);
        DiskItem diskItem = new DiskItem(did, now, expire, actualSize);
        indexMetadata(diskItem);

        log.info("Stored file (streaming): did={}, size={}, permanent={}", did, actualSize, permanent);
        return diskItem;
    }

    /**
     * Store data from a byte array using the streaming path internally.
     * This wraps the byte[] in a ByteArrayInputStream and delegates to storeFromStream.
     *
     * @param data         File content
     * @param permanent    If true, no expiration (carve); otherwise use dataLifeDays
     * @param dataLifeDays Expiration in days (only used if permanent=false)
     * @return DiskItem with did, size, since, expire
     * @throws IOException if storage fails
     */
    public DiskItem storeFromBytes(byte[] data, boolean permanent, long dataLifeDays) throws IOException {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }
        return storeFromStream(new ByteArrayInputStream(data), data.length, permanent, dataLifeDays);
    }

    /**
     * Get the file size for a given DID without loading into memory.
     *
     * @param did SHA256x2 hash (64 hex chars)
     * @return File size in bytes, or -1 if not found
     */
    public long getFileSize(String did) {
        if (!isValidDid(did)) {
            return -1;
        }
        Path filePath = buildPath(did);
        if (!Files.exists(filePath)) {
            return -1;
        }
        try {
            return Files.size(filePath);
        } catch (IOException e) {
            log.error("Failed to get file size: did={}, error={}", did, e.getMessage());
            return -1;
        }
    }

    // ==================== Expire Extension ====================
    
    /**
     * Extend the expire time of an existing DiskItem.
     * <p>
     * Rules:
     * <ul>
     *   <li>If existing is already permanent (expire == null): keep unchanged</li>
     *   <li>If new request is permanent (carve): upgrade to permanent (expire = null)</li>
     *   <li>If new request is non-permanent (put): extend to max(existing.expire, now + dataLifeDays)</li>
     * </ul>
     *
     * @param existing     The existing DiskItem metadata
     * @param permanent    Whether the new request is permanent (carve)
     * @param dataLifeDays Expiration in days for non-permanent requests
     * @return The updated DiskItem (same object if unchanged, or updated with new expire)
     */
    public DiskItem extendExpire(DiskItem existing, boolean permanent, long dataLifeDays) {
        Long oldExpire = existing.getExpire();
        
        // Already permanent — nothing to do
        if (oldExpire == null) {
            log.debug("DID {} is already permanent, no expire change needed", existing.getId());
            return existing;
        }
        
        // New request is carve (permanent) — upgrade to permanent
        if (permanent) {
            existing.setExpire(null);
            indexMetadata(existing);
            log.info("DID {} upgraded to permanent (was expire={})", existing.getId(), oldExpire);
            return existing;
        }
        
        // Non-permanent: extend to max(existing.expire, now + dataLifeDays)
        long now = System.currentTimeMillis();
        long newExpire = now + DateUtils.dayToLong(dataLifeDays);
        if (newExpire > oldExpire) {
            existing.setExpire(newExpire);
            indexMetadata(existing);
            log.info("DID {} expire extended: {} -> {}", existing.getId(), oldExpire, newExpire);
        } else {
            log.debug("DID {} expire not extended: existing {} >= new {}", existing.getId(), oldExpire, newExpire);
        }
        
        return existing;
    }
    
    // ==================== Internal Helpers ====================
    
    /**
     * Compute DID from content: SHA256x2(data) as hex string.
     */
    private String computeDid(byte[] data) {
        byte[] hash = Hash.sha256x2(data);
        return Hex.toHex(hash);
    }
    
    /**
     * Build storage path: storageRoot/ab/cd/ef/gh/did
     * Uses first 8 characters of DID as 4-level subdirectory structure.
     */
    private Path buildPath(String did) {
        String normalizedDid = did.toLowerCase();
        return storageRoot
                .resolve(normalizedDid.substring(0, 2))
                .resolve(normalizedDid.substring(2, 4))
                .resolve(normalizedDid.substring(4, 6))
                .resolve(normalizedDid.substring(6, 8))
                .resolve(normalizedDid);
    }
    
    /**
     * Validate DID format: 64 hex characters.
     */
    private boolean isValidDid(String did) {
        return did != null && DID_PATTERN.matcher(did).matches();
    }
    
    /**
     * Index DiskItem metadata to Elasticsearch.
     */
    private void indexMetadata(DiskItem diskItem) {
        if (esClient == null) {
            log.warn("Elasticsearch client not available, skipping index");
            return;
        }
        
        try {
            esClient.index(i -> i
                    .index(indexName)
                    .id(diskItem.getId())
                    .document(diskItem));
            log.debug("Indexed metadata: did={}", diskItem.getId());
        } catch (IOException e) {
            log.error("Failed to index metadata: did={}, error={}", diskItem.getId(), e.getMessage(), e);
        }
    }
    
    // ==================== Getters ====================
    
    public Path getStorageRoot() {
        return storageRoot;
    }
    
    public String getIndexName() {
        return indexName;
    }

    /**
     * Get the local file path for a given DID.
     * This allows callers to read the file directly (e.g., for streaming)
     * without loading the entire content into memory.
     *
     * @param did SHA256x2 hash (64 hex chars)
     * @return The file path, or null if the DID is invalid or the file doesn't exist
     */
    public Path getFilePath(String did) {
        if (!isValidDid(did)) {
            log.warn("Invalid DID format: {}", did);
            return null;
        }

        Path filePath = buildPath(did);
        if (!Files.exists(filePath)) {
            log.debug("File not found: did={}", did);
            return null;
        }

        return filePath;
    }
    
    // ==================== Aggregation ====================

    /**
     * Query Elasticsearch for the total size (in bytes) of all stored DiskItems.
     *
     * @return total bytes stored, or 0 if unavailable
     */
    public long getTotalStorageSize() {
        if (esClient == null) return 0;
        try {
            var response = esClient.search(s -> s
                    .index(indexName)
                    .size(0)
                    .aggregations("totalSize", a -> a.sum(su -> su.field("size"))),
                    DiskItem.class);
            var agg = response.aggregations().get("totalSize");
            if (agg != null && agg.isSum()) {
                return (long) agg.sum().value();
            }
        } catch (Exception e) {
            log.error("Failed to query total storage size: {}", e.getMessage());
        }
        return 0;
    }

    // ==================== Result Classes ====================
    
    /**
     * Result of check operation.
     */
    public record DiskCheckResult(boolean exists, DiskItem metadata) {
    }
}
