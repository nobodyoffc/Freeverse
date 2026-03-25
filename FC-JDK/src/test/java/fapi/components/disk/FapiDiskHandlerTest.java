package fapi.components.disk;

import data.fcData.DiskItem;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FapiDiskHandler.
 * Uses a temporary directory for file storage (no Elasticsearch).
 */
class FapiDiskHandlerTest {
    
    @TempDir
    Path tempDir;
    
    private FapiDiskHandler handler;
    
    @BeforeEach
    void setUp() {
        // Create handler without Elasticsearch (null client)
        handler = new FapiDiskHandler(tempDir, null, "test_data");
    }
    
    @Test
    @DisplayName("store should create file with correct DID")
    void testStoreCreatesFile() throws IOException {
        byte[] content = "Hello, Disk!".getBytes(StandardCharsets.UTF_8);
        
        DiskItem result = handler.store(content, false, 30);
        
        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals(64, result.getId().length()); // SHA256x2 = 64 hex chars
        assertEquals(content.length, result.getSize());
        assertNotNull(result.getSince());
        assertNotNull(result.getExpire()); // Non-permanent has expiration
    }
    
    @Test
    @DisplayName("store permanent (carve) should have no expiration")
    void testStoreCarveNoExpiration() throws IOException {
        byte[] content = "Permanent content".getBytes(StandardCharsets.UTF_8);
        
        DiskItem result = handler.store(content, true, 30);
        
        assertNotNull(result);
        assertNull(result.getExpire()); // Permanent = no expiration
    }
    
    @Test
    @DisplayName("store and retrieve should be symmetric")
    void testStoreAndRetrieve() throws IOException {
        byte[] content = "Test content for store and retrieve".getBytes(StandardCharsets.UTF_8);
        
        DiskItem stored = handler.store(content, false, 30);
        byte[] retrieved = handler.retrieve(stored.getId());
        
        assertNotNull(retrieved);
        assertArrayEquals(content, retrieved);
    }
    
    @Test
    @DisplayName("retrieve non-existent DID should return null")
    void testRetrieveNonExistent() {
        String fakeDid = "a".repeat(64);
        
        byte[] result = handler.retrieve(fakeDid);
        
        assertNull(result);
    }
    
    @Test
    @DisplayName("retrieve with invalid DID format should return null")
    void testRetrieveInvalidDid() {
        assertNull(handler.retrieve("too_short"));
        assertNull(handler.retrieve(null));
        assertNull(handler.retrieve("not_hex_chars_here_but_correct_length_64_chars!!"));
    }
    
    @Test
    @DisplayName("exists should return true for stored files")
    void testExists() throws IOException {
        byte[] content = "Existence test".getBytes(StandardCharsets.UTF_8);
        
        DiskItem stored = handler.store(content, false, 30);
        
        assertTrue(handler.exists(stored.getId()));
        assertFalse(handler.exists("b".repeat(64)));
    }
    
    @Test
    @DisplayName("check should return combined result")
    void testCheck() throws IOException {
        byte[] content = "Check test".getBytes(StandardCharsets.UTF_8);
        
        DiskItem stored = handler.store(content, false, 30);
        
        FapiDiskHandler.DiskCheckResult result = handler.check(stored.getId());
        
        assertNotNull(result);
        assertTrue(result.exists());
        // Note: metadata may be null without ES client
    }
    
    @Test
    @DisplayName("delete should remove file")
    void testDelete() throws IOException {
        byte[] content = "Delete test".getBytes(StandardCharsets.UTF_8);
        
        DiskItem stored = handler.store(content, false, 30);
        assertTrue(handler.exists(stored.getId()));
        
        boolean deleted = handler.delete(stored.getId());
        
        assertTrue(deleted);
        assertFalse(handler.exists(stored.getId()));
    }
    
    @Test
    @DisplayName("delete non-existent file should return false")
    void testDeleteNonExistent() {
        String fakeDid = "c".repeat(64);
        
        boolean deleted = handler.delete(fakeDid);
        
        assertFalse(deleted);
    }
    
    @Test
    @DisplayName("storing same content twice should return same DID")
    void testContentAddressable() throws IOException {
        byte[] content = "Duplicate content".getBytes(StandardCharsets.UTF_8);
        
        DiskItem first = handler.store(content, false, 30);
        DiskItem second = handler.store(content, false, 60);
        
        assertEquals(first.getId(), second.getId());
    }
    
    @Test
    @DisplayName("different content should produce different DIDs")
    void testDifferentContentDifferentDid() throws IOException {
        DiskItem first = handler.store("Content A".getBytes(), false, 30);
        DiskItem second = handler.store("Content B".getBytes(), false, 30);
        
        assertNotEquals(first.getId(), second.getId());
    }
    
    @Test
    @DisplayName("binary content with all byte values should work")
    void testBinaryContent() throws IOException {
        byte[] binaryContent = new byte[256];
        for (int i = 0; i < 256; i++) {
            binaryContent[i] = (byte) i;
        }
        
        DiskItem stored = handler.store(binaryContent, true, 0);
        byte[] retrieved = handler.retrieve(stored.getId());
        
        assertArrayEquals(binaryContent, retrieved);
    }
    
    @Test
    @DisplayName("large file should be handled correctly")
    void testLargeFile() throws IOException {
        // 1 MB file
        byte[] largeContent = new byte[1024 * 1024];
        for (int i = 0; i < largeContent.length; i++) {
            largeContent[i] = (byte) (i % 256);
        }
        
        DiskItem stored = handler.store(largeContent, false, 30);
        assertEquals(largeContent.length, stored.getSize());
        
        byte[] retrieved = handler.retrieve(stored.getId());
        assertArrayEquals(largeContent, retrieved);
    }
    
    @Test
    @DisplayName("storage path should follow hierarchical structure")
    void testHierarchicalStorage() throws IOException {
        byte[] content = "Path test".getBytes(StandardCharsets.UTF_8);
        
        DiskItem stored = handler.store(content, false, 30);
        String did = stored.getId().toLowerCase();
        
        // Expected path: tempDir/ab/cd/ef/gh/did
        Path expectedPath = tempDir
                .resolve(did.substring(0, 2))
                .resolve(did.substring(2, 4))
                .resolve(did.substring(4, 6))
                .resolve(did.substring(6, 8))
                .resolve(did);
        
        assertTrue(Files.exists(expectedPath));
    }
    
    @Test
    @DisplayName("store with null content should throw exception")
    void testStoreNullContent() {
        assertThrows(IllegalArgumentException.class, () -> 
            handler.store(null, false, 30));
    }
    
    @Test
    @DisplayName("store with empty content should throw exception")
    void testStoreEmptyContent() {
        assertThrows(IllegalArgumentException.class, () -> 
            handler.store(new byte[0], false, 30));
    }
    
    @Test
    @DisplayName("getStorageRoot should return correct path")
    void testGetStorageRoot() {
        assertEquals(tempDir, handler.getStorageRoot());
    }
    
    @Test
    @DisplayName("getIndexName should return correct name")
    void testGetIndexName() {
        assertEquals("test_data", handler.getIndexName());
    }
}
