package fapi.components.disk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DiskProtocol binary encoder/decoder.
 */
class DiskProtocolTest {
    
    @Test
    @DisplayName("encodePutRequest and decodePutRequest should be symmetric")
    void testPutRequestRoundTrip() {
        byte[] content = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        String metadata = "{\"name\":\"test.txt\"}";
        
        // Encode
        byte[] encoded = DiskProtocol.encodePutRequest(DiskProtocol.OP_PUT, metadata, content);
        assertNotNull(encoded);
        assertTrue(encoded.length > 5); // At least header size
        
        // Decode
        DiskProtocol.PutRequest decoded = DiskProtocol.decodePutRequest(encoded);
        assertNotNull(decoded);
        assertEquals(DiskProtocol.OP_PUT, decoded.operation());
        assertEquals(metadata, decoded.metadata());
        assertArrayEquals(content, decoded.content());
        assertFalse(decoded.isPermanent());
    }
    
    @Test
    @DisplayName("CARVE operation should be marked as permanent")
    void testCarveIsPermanent() {
        byte[] content = "Permanent data".getBytes(StandardCharsets.UTF_8);
        
        byte[] encoded = DiskProtocol.encodePutRequest(DiskProtocol.OP_CARVE, null, content);
        DiskProtocol.PutRequest decoded = DiskProtocol.decodePutRequest(encoded);
        
        assertEquals(DiskProtocol.OP_CARVE, decoded.operation());
        assertTrue(decoded.isPermanent());
        assertNull(decoded.metadata());
        assertArrayEquals(content, decoded.content());
    }
    
    @Test
    @DisplayName("encodePutRequest with null metadata should work")
    void testPutRequestNullMetadata() {
        byte[] content = new byte[]{1, 2, 3, 4, 5};
        
        byte[] encoded = DiskProtocol.encodePutRequest(DiskProtocol.OP_PUT, null, content);
        DiskProtocol.PutRequest decoded = DiskProtocol.decodePutRequest(encoded);
        
        assertNull(decoded.metadata());
        assertArrayEquals(content, decoded.content());
    }
    
    @Test
    @DisplayName("encodePutRequest with empty metadata should work")
    void testPutRequestEmptyMetadata() {
        byte[] content = new byte[]{10, 20, 30};
        
        byte[] encoded = DiskProtocol.encodePutRequest(DiskProtocol.OP_PUT, "", content);
        DiskProtocol.PutRequest decoded = DiskProtocol.decodePutRequest(encoded);
        
        assertNull(decoded.metadata()); // Empty becomes null
        assertArrayEquals(content, decoded.content());
    }
    
    @Test
    @DisplayName("encodeGetRequest and decodeGetRequest should be symmetric")
    void testGetRequestRoundTrip() {
        String did = "a".repeat(64); // 64 hex characters
        
        byte[] encoded = DiskProtocol.encodeGetRequest(did);
        assertNotNull(encoded);
        assertEquals(65, encoded.length); // 1 byte operation + 64 bytes DID
        
        String decoded = DiskProtocol.decodeGetRequest(encoded);
        assertEquals(did, decoded);
    }
    
    @Test
    @DisplayName("encodeGetRequest should reject invalid DID length")
    void testGetRequestInvalidDid() {
        assertThrows(IllegalArgumentException.class, () -> 
            DiskProtocol.encodeGetRequest("too_short"));
        
        assertThrows(IllegalArgumentException.class, () -> 
            DiskProtocol.encodeGetRequest(null));
        
        assertThrows(IllegalArgumentException.class, () -> 
            DiskProtocol.encodeGetRequest("a".repeat(63))); // Too short
        
        assertThrows(IllegalArgumentException.class, () -> 
            DiskProtocol.encodeGetRequest("a".repeat(65))); // Too long
    }
    
    @Test
    @DisplayName("encodeGetResponse and decodeGetResponse should be symmetric - success case")
    void testGetResponseSuccessRoundTrip() {
        byte[] content = "File content here".getBytes(StandardCharsets.UTF_8);
        String metadata = "{\"did\":\"abc123\",\"size\":17}";
        
        byte[] encoded = DiskProtocol.encodeGetResponse(DiskProtocol.STATUS_SUCCESS, metadata, content);
        assertNotNull(encoded);
        
        DiskProtocol.GetResponse decoded = DiskProtocol.decodeGetResponse(encoded);
        assertNotNull(decoded);
        assertEquals(DiskProtocol.STATUS_SUCCESS, decoded.status());
        assertTrue(decoded.isSuccess());
        assertFalse(decoded.isNotFound());
        assertEquals(metadata, decoded.metadata());
        assertArrayEquals(content, decoded.content());
    }
    
    @Test
    @DisplayName("encodeGetResponse for NOT_FOUND should work without content")
    void testGetResponseNotFound() {
        String metadata = "{\"error\":\"File not found\"}";
        
        byte[] encoded = DiskProtocol.encodeGetResponse(DiskProtocol.STATUS_NOT_FOUND, metadata, null);
        DiskProtocol.GetResponse decoded = DiskProtocol.decodeGetResponse(encoded);
        
        assertEquals(DiskProtocol.STATUS_NOT_FOUND, decoded.status());
        assertFalse(decoded.isSuccess());
        assertTrue(decoded.isNotFound());
        assertEquals(metadata, decoded.metadata());
        assertNull(decoded.content());
    }
    
    @Test
    @DisplayName("getOperation should extract operation code from request")
    void testGetOperation() {
        byte[] putRequest = DiskProtocol.encodePutRequest(DiskProtocol.OP_PUT, null, new byte[]{1});
        byte[] carveRequest = DiskProtocol.encodePutRequest(DiskProtocol.OP_CARVE, null, new byte[]{1});
        byte[] getRequest = DiskProtocol.encodeGetRequest("a".repeat(64));
        
        assertEquals(DiskProtocol.OP_PUT, DiskProtocol.getOperation(putRequest));
        assertEquals(DiskProtocol.OP_CARVE, DiskProtocol.getOperation(carveRequest));
        assertEquals(DiskProtocol.OP_GET, DiskProtocol.getOperation(getRequest));
    }
    
    @Test
    @DisplayName("isDiskProtocol should correctly identify disk protocol requests")
    void testIsDiskProtocol() {
        assertTrue(DiskProtocol.isDiskProtocol(new byte[]{DiskProtocol.OP_PUT}));
        assertTrue(DiskProtocol.isDiskProtocol(new byte[]{DiskProtocol.OP_CARVE}));
        assertTrue(DiskProtocol.isDiskProtocol(new byte[]{DiskProtocol.OP_GET}));
        
        assertFalse(DiskProtocol.isDiskProtocol(new byte[]{0x00}));
        assertFalse(DiskProtocol.isDiskProtocol(new byte[]{0x10}));
        assertFalse(DiskProtocol.isDiskProtocol(null));
        assertFalse(DiskProtocol.isDiskProtocol(new byte[]{}));
    }
    
    @Test
    @DisplayName("Large content should be handled correctly")
    void testLargeContent() {
        // 1 MB content
        byte[] largeContent = new byte[1024 * 1024];
        for (int i = 0; i < largeContent.length; i++) {
            largeContent[i] = (byte) (i % 256);
        }
        
        byte[] encoded = DiskProtocol.encodePutRequest(DiskProtocol.OP_PUT, null, largeContent);
        DiskProtocol.PutRequest decoded = DiskProtocol.decodePutRequest(encoded);
        
        assertArrayEquals(largeContent, decoded.content());
    }
    
    @Test
    @DisplayName("Binary content with all byte values should work")
    void testBinaryContent() {
        // All possible byte values
        byte[] binaryContent = new byte[256];
        for (int i = 0; i < 256; i++) {
            binaryContent[i] = (byte) i;
        }
        
        byte[] encoded = DiskProtocol.encodePutRequest(DiskProtocol.OP_CARVE, null, binaryContent);
        DiskProtocol.PutRequest decoded = DiskProtocol.decodePutRequest(encoded);
        
        assertArrayEquals(binaryContent, decoded.content());
    }
    
    @Test
    @DisplayName("UTF-8 metadata with special characters should work")
    void testUtf8Metadata() {
        byte[] content = new byte[]{1, 2, 3};
        String metadata = "{\"name\":\"文件名.txt\",\"desc\":\"这是一个测试文件 🎉\"}";
        
        byte[] encoded = DiskProtocol.encodePutRequest(DiskProtocol.OP_PUT, metadata, content);
        DiskProtocol.PutRequest decoded = DiskProtocol.decodePutRequest(encoded);
        
        assertEquals(metadata, decoded.metadata());
    }
    
    @Test
    @DisplayName("decodePutRequest should reject invalid data")
    void testDecodePutRequestInvalid() {
        assertThrows(IllegalArgumentException.class, () -> 
            DiskProtocol.decodePutRequest(null));
        
        assertThrows(IllegalArgumentException.class, () -> 
            DiskProtocol.decodePutRequest(new byte[]{1, 2, 3})); // Too short
        
        assertThrows(IllegalArgumentException.class, () -> 
            DiskProtocol.decodePutRequest(new byte[]{0x10, 0, 0, 0, 0})); // Invalid operation
    }
}
