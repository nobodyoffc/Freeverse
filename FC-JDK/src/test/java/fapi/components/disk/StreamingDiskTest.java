package fapi.components.disk;

import core.crypto.Hash;
import data.fcData.DiskItem;
import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import fapi.message.UnifiedCodec;
import fapi.message.UnifiedCodec.UnifiedRequest;
import fapi.message.UnifiedCodec.UnifiedResponse;
import fudp.message.*;
import fudp.util.Varint;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import utils.Hex;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for all streaming DISK modifications.
 * 
 * Tests cover:
 * - Phase 1: Incremental hash utilities (Hash.sha256x2FromStream, sha256x2CopyStream)
 * - Phase 2: Streaming store/retrieve (FapiDiskHandler.storeFromStream, storeFromBytes, getFileSize)
 * - Phase 5: Streaming encode (UnifiedCodec.encodeRequestHeaderOnly, encodeResponseHeaderOnly)
 * - Phase 4: Wire-format compatibility (streaming request/respond produce the same bytes)
 * - FapiResponse transient streaming fields
 */
class StreamingDiskTest {

    @TempDir
    Path tempDir;

    private FapiDiskHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FapiDiskHandler(tempDir, null, "test_data");
    }

    // ==================== Phase 1: Hash Streaming ====================

    @Test
    @DisplayName("sha256x2FromStream should match sha256x2(byte[])")
    void testSha256x2FromStreamMatchesByteArray() throws IOException {
        byte[] data = "Hello, streaming world!".getBytes(StandardCharsets.UTF_8);

        byte[] expected = Hash.sha256x2(data);
        byte[] actual = Hash.sha256x2FromStream(new ByteArrayInputStream(data));

        assertNotNull(actual);
        assertArrayEquals(expected, actual, "Stream hash should match byte[] hash");
    }

    @Test
    @DisplayName("sha256x2FromStream with large data should match")
    void testSha256x2FromStreamLargeData() throws IOException {
        // 2 MB of data
        byte[] data = new byte[2 * 1024 * 1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 251); // Prime modulus for variety
        }

        byte[] expected = Hash.sha256x2(data);
        byte[] actual = Hash.sha256x2FromStream(new ByteArrayInputStream(data));

        assertNotNull(actual);
        assertArrayEquals(expected, actual, "Stream hash of large data should match");
    }

    @Test
    @DisplayName("sha256x2FromStream with empty stream should produce valid hash")
    void testSha256x2FromStreamEmpty() throws IOException {
        byte[] expected = Hash.sha256x2(new byte[0]);
        byte[] actual = Hash.sha256x2FromStream(new ByteArrayInputStream(new byte[0]));

        assertNotNull(actual);
        assertArrayEquals(expected, actual);
    }

    @Test
    @DisplayName("sha256x2CopyStream should compute hash and copy data simultaneously")
    void testSha256x2CopyStream() throws IOException {
        byte[] data = "Copy and hash simultaneously!".getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream copyOutput = new ByteArrayOutputStream();
        byte[] hash = Hash.sha256x2CopyStream(new ByteArrayInputStream(data), copyOutput);

        assertNotNull(hash);
        assertArrayEquals(Hash.sha256x2(data), hash, "Hash should match");
        assertArrayEquals(data, copyOutput.toByteArray(), "Copied data should match original");
    }

    @Test
    @DisplayName("sha256x2CopyStream with null output should only compute hash")
    void testSha256x2CopyStreamNullOutput() throws IOException {
        byte[] data = "Hash only, no copy".getBytes(StandardCharsets.UTF_8);

        byte[] hash = Hash.sha256x2CopyStream(new ByteArrayInputStream(data), null);

        assertNotNull(hash);
        assertArrayEquals(Hash.sha256x2(data), hash);
    }

    @Test
    @DisplayName("sha256x2CopyStream with large data should work correctly")
    void testSha256x2CopyStreamLargeData() throws IOException {
        // 1 MB of data
        byte[] data = new byte[1024 * 1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 200);
        }

        ByteArrayOutputStream copyOutput = new ByteArrayOutputStream();
        byte[] hash = Hash.sha256x2CopyStream(new ByteArrayInputStream(data), copyOutput);

        assertNotNull(hash);
        assertArrayEquals(Hash.sha256x2(data), hash);
        assertArrayEquals(data, copyOutput.toByteArray());
    }

    // ==================== Phase 2: FapiDiskHandler Streaming Store ====================

    @Test
    @DisplayName("storeFromStream should produce same DID as store(byte[])")
    void testStoreFromStreamSameDid() throws IOException {
        byte[] data = "Streaming store test".getBytes(StandardCharsets.UTF_8);

        DiskItem byteResult = handler.store(data, false, 30);
        
        // Create a new handler with a different temp dir to avoid "already exists" path
        Path tempDir2 = tempDir.resolve("alt");
        Files.createDirectories(tempDir2);
        FapiDiskHandler handler2 = new FapiDiskHandler(tempDir2, null, "test_data");
        
        DiskItem streamResult = handler2.storeFromStream(
                new ByteArrayInputStream(data), data.length, false, 30);

        assertNotNull(byteResult);
        assertNotNull(streamResult);
        assertEquals(byteResult.getId(), streamResult.getId(),
                "Stream store and byte store should produce same DID");
    }

    @Test
    @DisplayName("storeFromBytes should produce same DID as store(byte[])")
    void testStoreFromBytesSameDid() throws IOException {
        byte[] data = "storeFromBytes test".getBytes(StandardCharsets.UTF_8);

        DiskItem byteResult = handler.store(data, false, 30);

        // Use different dir
        Path tempDir2 = tempDir.resolve("alt2");
        Files.createDirectories(tempDir2);
        FapiDiskHandler handler2 = new FapiDiskHandler(tempDir2, null, "test_data");

        DiskItem streamResult = handler2.storeFromBytes(data, false, 30);

        assertNotNull(byteResult);
        assertNotNull(streamResult);
        assertEquals(byteResult.getId(), streamResult.getId());
    }

    @Test
    @DisplayName("storeFromStream file should be retrievable with retrieve()")
    void testStoreFromStreamAndRetrieve() throws IOException {
        byte[] data = "Store stream, retrieve bytes".getBytes(StandardCharsets.UTF_8);

        DiskItem stored = handler.storeFromStream(
                new ByteArrayInputStream(data), data.length, false, 30);

        assertNotNull(stored);
        byte[] retrieved = handler.retrieve(stored.getId());
        assertNotNull(retrieved);
        assertArrayEquals(data, retrieved);
    }

    @Test
    @DisplayName("storeFromStream with large data should work")
    void testStoreFromStreamLargeData() throws IOException {
        // 2 MB data
        byte[] data = new byte[2 * 1024 * 1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }

        DiskItem stored = handler.storeFromStream(
                new ByteArrayInputStream(data), data.length, false, 30);

        assertNotNull(stored);
        assertEquals(data.length, stored.getSize());

        byte[] retrieved = handler.retrieve(stored.getId());
        assertArrayEquals(data, retrieved);
    }

    @Test
    @DisplayName("storeFromStream permanent should have null expire")
    void testStoreFromStreamPermanent() throws IOException {
        byte[] data = "Permanent stream store".getBytes(StandardCharsets.UTF_8);

        DiskItem stored = handler.storeFromStream(
                new ByteArrayInputStream(data), data.length, true, 30);

        assertNotNull(stored);
        assertNull(stored.getExpire(), "Permanent store should have null expire");
    }

    @Test
    @DisplayName("storeFromStream non-permanent should have non-null expire")
    void testStoreFromStreamNonPermanent() throws IOException {
        byte[] data = "Temporary stream store".getBytes(StandardCharsets.UTF_8);

        DiskItem stored = handler.storeFromStream(
                new ByteArrayInputStream(data), data.length, false, 30);

        assertNotNull(stored);
        assertNotNull(stored.getExpire(), "Non-permanent store should have expire");
    }

    @Test
    @DisplayName("storeFromStream null input should throw")
    void testStoreFromStreamNullInput() {
        assertThrows(IllegalArgumentException.class, () ->
                handler.storeFromStream(null, 0, false, 30));
    }

    @Test
    @DisplayName("storeFromBytes null/empty should throw")
    void testStoreFromBytesNullEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
                handler.storeFromBytes(null, false, 30));
        assertThrows(IllegalArgumentException.class, () ->
                handler.storeFromBytes(new byte[0], false, 30));
    }

    @Test
    @DisplayName("storeFromStream duplicate content should return existing")
    void testStoreFromStreamDuplicate() throws IOException {
        byte[] data = "Duplicate content for streaming".getBytes(StandardCharsets.UTF_8);

        DiskItem first = handler.storeFromStream(
                new ByteArrayInputStream(data), data.length, false, 30);
        DiskItem second = handler.storeFromStream(
                new ByteArrayInputStream(data), data.length, false, 60);

        assertEquals(first.getId(), second.getId());
    }

    // ==================== Phase 2: getFileSize / getFilePath ====================

    @Test
    @DisplayName("getFileSize should return correct size after store")
    void testGetFileSize() throws IOException {
        byte[] data = "File size test content".getBytes(StandardCharsets.UTF_8);

        DiskItem stored = handler.storeFromStream(
                new ByteArrayInputStream(data), data.length, false, 30);

        long size = handler.getFileSize(stored.getId());
        assertEquals(data.length, size);
    }

    @Test
    @DisplayName("getFileSize for non-existent DID should return -1")
    void testGetFileSizeNonExistent() {
        assertEquals(-1, handler.getFileSize("a".repeat(64)));
    }

    @Test
    @DisplayName("getFileSize for invalid DID should return -1")
    void testGetFileSizeInvalid() {
        assertEquals(-1, handler.getFileSize("short"));
        assertEquals(-1, handler.getFileSize(null));
    }

    @Test
    @DisplayName("getFilePath should return valid path after store")
    void testGetFilePath() throws IOException {
        byte[] data = "File path test".getBytes(StandardCharsets.UTF_8);

        DiskItem stored = handler.store(data, false, 30);
        Path filePath = handler.getFilePath(stored.getId());

        assertNotNull(filePath);
        assertTrue(Files.exists(filePath));
        assertArrayEquals(data, Files.readAllBytes(filePath));
    }

    @Test
    @DisplayName("getFilePath for non-existent DID should return null")
    void testGetFilePathNonExistent() {
        assertNull(handler.getFilePath("b".repeat(64)));
    }

    // ==================== Phase 5: UnifiedCodec Streaming Encode ====================

    @Test
    @DisplayName("encodeRequestHeaderOnly should produce valid header without binary data")
    void testEncodeRequestHeaderOnly() {
        FapiRequest request = FapiRequest.binaryOperation("disk.put",
                Map.of("dataLifeDays", 30), 1024 * 1024, "abcdef1234567890".repeat(4));

        byte[] headerOnly = UnifiedCodec.encodeRequestHeaderOnly(request);
        assertNotNull(headerOnly);
        assertTrue(headerOnly.length > 4);

        // Verify it's a valid header: first 4 bytes = header length, then JSON starts with '{'
        int headerLen = ByteBuffer.wrap(headerOnly, 0, 4).getInt();
        assertEquals(headerOnly.length - 4, headerLen, "Header length field should match actual JSON length");
        assertEquals('{', headerOnly[4], "JSON should start with '{'");

        // Should be decodable as a UnifiedRequest (with no binary data)
        UnifiedRequest decoded = UnifiedCodec.decodeRequest(headerOnly);
        assertNotNull(decoded.request());
        assertEquals("disk.put", decoded.request().getApi());
        assertNull(decoded.binaryData()); // No binary data appended
    }

    @Test
    @DisplayName("encodeResponseHeaderOnly should produce valid header without binary data")
    void testEncodeResponseHeaderOnly() {
        FapiResponse response = FapiResponse.success("req-123", Map.of("id", "abc"));
        response.setDataSize(5000L);

        byte[] headerOnly = UnifiedCodec.encodeResponseHeaderOnly(response);
        assertNotNull(headerOnly);
        assertTrue(headerOnly.length > 4);

        int headerLen = ByteBuffer.wrap(headerOnly, 0, 4).getInt();
        assertEquals(headerOnly.length - 4, headerLen);
        assertEquals('{', headerOnly[4]);

        // Should be decodable as a UnifiedResponse (with no binary data)
        UnifiedResponse decoded = UnifiedCodec.decodeResponse(headerOnly);
        assertNotNull(decoded.response());
        assertEquals(0, decoded.response().getCode()); // Success
        assertNull(decoded.binaryData());
    }

    @Test
    @DisplayName("header-only + binary concat should decode same as encodeRequest(req, binary)")
    void testHeaderOnlyPlusBinaryConcatMatchesFullEncode() {
        FapiRequest request = FapiRequest.binaryOperation("disk.put",
                Map.of("test", true), 5, null);
        byte[] binaryData = "ABCDE".getBytes(StandardCharsets.UTF_8);

        // Full encode
        byte[] fullEncoded = UnifiedCodec.encodeRequest(request, binaryData);

        // Header-only + binary concat
        // Note: encodeRequest sets dataSize, so we need to set it before encoding header
        request.setDataSize((long) binaryData.length);
        byte[] headerOnly = UnifiedCodec.encodeRequestHeaderOnly(request);
        byte[] concat = new byte[headerOnly.length + binaryData.length];
        System.arraycopy(headerOnly, 0, concat, 0, headerOnly.length);
        System.arraycopy(binaryData, 0, concat, headerOnly.length, binaryData.length);

        // Both should decode to the same result
        UnifiedRequest fullDecoded = UnifiedCodec.decodeRequest(fullEncoded);
        UnifiedRequest concatDecoded = UnifiedCodec.decodeRequest(concat);

        assertEquals(fullDecoded.request().getApi(), concatDecoded.request().getApi());
        assertArrayEquals(fullDecoded.binaryData(), concatDecoded.binaryData());
    }

    @Test
    @DisplayName("response header-only + binary concat should decode same as encodeResponse(resp, binary)")
    void testResponseHeaderOnlyPlusBinaryConcatMatchesFullEncode() {
        FapiResponse response = FapiResponse.success("req-456", Map.of("key", "value"));
        byte[] binaryData = "FileContentHere".getBytes(StandardCharsets.UTF_8);

        // Full encode
        byte[] fullEncoded = UnifiedCodec.encodeResponse(response, binaryData);

        // Header-only + binary concat
        response.setDataSize((long) binaryData.length);
        byte[] headerOnly = UnifiedCodec.encodeResponseHeaderOnly(response);
        byte[] concat = new byte[headerOnly.length + binaryData.length];
        System.arraycopy(headerOnly, 0, concat, 0, headerOnly.length);
        System.arraycopy(binaryData, 0, concat, headerOnly.length, binaryData.length);

        // Both should decode to the same result
        UnifiedResponse fullDecoded = UnifiedCodec.decodeResponse(fullEncoded);
        UnifiedResponse concatDecoded = UnifiedCodec.decodeResponse(concat);

        assertEquals(fullDecoded.response().getCode(), concatDecoded.response().getCode());
        assertArrayEquals(fullDecoded.binaryData(), concatDecoded.binaryData());
    }

    // ==================== Phase 4: Wire-format Compatibility ====================

    @Test
    @DisplayName("streaming request envelope should decode identically to non-streaming")
    void testStreamingRequestWireFormat() throws IOException {
        // Simulate what FudpNode.requestWithStream() produces:
        // type(1) + messageId(8) + flags(1) + payloadLen(varint) + serviceNameLen(varint) + serviceName + requestData

        long messageId = 12345L;
        String serviceName = "test-service";
        byte[] requestData = "request-payload-bytes".getBytes(StandardCharsets.UTF_8);

        // Non-streaming: build RequestMessage and encode via MessageCodec
        RequestMessage reqMsg = new RequestMessage(messageId, serviceName, requestData);
        byte[] standardEncoded = MessageCodec.encode(reqMsg);

        // Streaming simulation: build the same bytes manually
        byte[] serviceNameBytes = serviceName.getBytes(StandardCharsets.UTF_8);
        byte[] serviceNameLenVarint = Varint.encode(serviceNameBytes.length);
        long payloadLength = serviceNameLenVarint.length + serviceNameBytes.length + requestData.length;
        byte[] payloadLenVarint = Varint.encode(payloadLength);

        ByteArrayOutputStream envelope = new ByteArrayOutputStream();
        envelope.write(MessageType.REQUEST.getCode());
        ByteBuffer idBuf = ByteBuffer.allocate(8);
        idBuf.putLong(messageId);
        envelope.write(idBuf.array());
        envelope.write(0); // flags
        envelope.write(payloadLenVarint);
        envelope.write(serviceNameLenVarint);
        envelope.write(serviceNameBytes);
        envelope.write(requestData);
        byte[] streamingEncoded = envelope.toByteArray();

        // Both should produce identical bytes
        assertArrayEquals(standardEncoded, streamingEncoded,
                "Streaming envelope should produce identical bytes to MessageCodec.encode()");

        // Both should decode to the same message
        AppMessage decodedStandard = MessageCodec.decode(standardEncoded);
        AppMessage decodedStreaming = MessageCodec.decode(streamingEncoded);

        assertInstanceOf(RequestMessage.class, decodedStandard);
        assertInstanceOf(RequestMessage.class, decodedStreaming);

        RequestMessage reqStandard = (RequestMessage) decodedStandard;
        RequestMessage reqStreaming = (RequestMessage) decodedStreaming;

        assertEquals(reqStandard.getMessageId(), reqStreaming.getMessageId());
        assertEquals(reqStandard.getSid(), reqStreaming.getSid());
        assertArrayEquals(reqStandard.getData(), reqStreaming.getData());
    }

    @Test
    @DisplayName("streaming response envelope should decode identically to non-streaming")
    void testStreamingResponseWireFormat() throws IOException {
        // Simulate what FudpNode.respondWithStream() produces
        long requestId = 99999L;
        int statusCode = 0;
        byte[] responseData = "response-with-file-data".getBytes(StandardCharsets.UTF_8);

        // Non-streaming: build ResponseMessage and encode via MessageCodec
        ResponseMessage respMsg = new ResponseMessage(requestId, statusCode, responseData);
        byte[] standardEncoded = MessageCodec.encode(respMsg);

        // Streaming simulation: build the same bytes manually
        long payloadLength = 2 + responseData.length; // statusCode(2) + data
        byte[] payloadLenVarint = Varint.encode(payloadLength);

        ByteArrayOutputStream envelope = new ByteArrayOutputStream();
        envelope.write(MessageType.RESPONSE.getCode());
        ByteBuffer idBuf = ByteBuffer.allocate(8);
        idBuf.putLong(requestId);
        envelope.write(idBuf.array());
        envelope.write(0); // flags
        envelope.write(payloadLenVarint);
        envelope.write((statusCode >> 8) & 0xFF);
        envelope.write(statusCode & 0xFF);
        envelope.write(responseData);
        byte[] streamingEncoded = envelope.toByteArray();

        // Both should produce identical bytes
        assertArrayEquals(standardEncoded, streamingEncoded,
                "Streaming response envelope should produce identical bytes to MessageCodec.encode()");

        // Both should decode to the same message
        ResponseMessage decodedStandard = (ResponseMessage) MessageCodec.decode(standardEncoded);
        ResponseMessage decodedStreaming = (ResponseMessage) MessageCodec.decode(streamingEncoded);

        assertEquals(decodedStandard.getMessageId(), decodedStreaming.getMessageId());
        assertEquals(decodedStandard.getStatusCode(), decodedStreaming.getStatusCode());
        assertArrayEquals(decodedStandard.getData(), decodedStreaming.getData());
    }

    // ==================== FapiResponse Transient Fields ====================

    @Test
    @DisplayName("FapiResponse streamSourcePath should be transient (not in JSON)")
    void testStreamSourcePathTransient() throws IOException {
        FapiResponse response = FapiResponse.success("req-1", null);
        
        // Create a temp file
        Path testFile = tempDir.resolve("test.bin");
        Files.write(testFile, "test content".getBytes());
        
        response.setStreamSourcePath(testFile);
        response.setStreamSourceSize(12);

        assertTrue(response.hasStreamSource());
        assertEquals(testFile, response.getStreamSourcePath());
        assertEquals(12, response.getStreamSourceSize());

        // Serialize to JSON — transient fields should NOT appear
        String json = response.toJson();
        assertNotNull(json);
        assertFalse(json.contains("streamSourcePath"),
                "streamSourcePath should not be in JSON (transient)");
        assertFalse(json.contains("streamSourceSize"),
                "streamSourceSize should not be in JSON (transient)");
    }

    @Test
    @DisplayName("FapiResponse without stream source should return false for hasStreamSource")
    void testNoStreamSource() {
        FapiResponse response = FapiResponse.success("req-2", null);
        assertFalse(response.hasStreamSource());
        assertNull(response.getStreamSourcePath());
        assertEquals(0, response.getStreamSourceSize());
    }

    // ==================== End-to-End: Streaming Upload Simulation ====================

    @Test
    @DisplayName("end-to-end: streaming upload header + binary should decode correctly on server")
    void testEndToEndStreamingUpload() throws IOException {
        // Simulate client side: encodeRequestHeaderOnly + binary data
        byte[] fileContent = "This is the file content to upload via streaming".getBytes(StandardCharsets.UTF_8);
        
        FapiRequest request = FapiRequest.binaryOperation("disk.put",
                Map.of("dataLifeDays", 30), fileContent.length, null);
        
        byte[] headerBytes = UnifiedCodec.encodeRequestHeaderOnly(request);
        
        // Combine header + binary (simulates what goes through the wire)
        byte[] onTheWire = new byte[headerBytes.length + fileContent.length];
        System.arraycopy(headerBytes, 0, onTheWire, 0, headerBytes.length);
        System.arraycopy(fileContent, 0, onTheWire, headerBytes.length, fileContent.length);
        
        // Simulate server side: decode
        assertTrue(UnifiedCodec.isUnifiedProtocol(onTheWire));
        UnifiedRequest decoded = UnifiedCodec.decodeRequest(onTheWire);
        
        assertNotNull(decoded.request());
        assertEquals("disk.put", decoded.request().getApi());
        assertEquals(fileContent.length, decoded.request().getDataSize());
        assertNotNull(decoded.binaryData());
        assertArrayEquals(fileContent, decoded.binaryData());
    }

    @Test
    @DisplayName("end-to-end: streaming download header + binary should decode correctly on client")
    void testEndToEndStreamingDownload() throws IOException {
        // Simulate server side: encodeResponseHeaderOnly + file data
        byte[] fileContent = "Downloaded file content via streaming response".getBytes(StandardCharsets.UTF_8);
        
        FapiResponse response = FapiResponse.success("req-dl", Map.of("id", "abc123"));
        response.setDataSize((long) fileContent.length);
        
        byte[] headerBytes = UnifiedCodec.encodeResponseHeaderOnly(response);
        
        // Combine header + binary
        byte[] onTheWire = new byte[headerBytes.length + fileContent.length];
        System.arraycopy(headerBytes, 0, onTheWire, 0, headerBytes.length);
        System.arraycopy(fileContent, 0, onTheWire, headerBytes.length, fileContent.length);
        
        // Simulate client side: decode
        UnifiedResponse decoded = UnifiedCodec.decodeResponse(onTheWire);
        
        assertNotNull(decoded.response());
        assertTrue(decoded.response().isSuccess());
        assertEquals(fileContent.length, decoded.response().getDataSize());
        assertNotNull(decoded.binaryData());
        assertArrayEquals(fileContent, decoded.binaryData());
    }

    // ==================== Full Streaming Store and Retrieve Path ====================

    @Test
    @DisplayName("full path: storeFromStream -> getFilePath -> read via stream -> verify hash")
    void testFullStreamingPath() throws IOException {
        // Create test data
        byte[] data = new byte[100_000]; // 100 KB
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ((i * 7 + 3) % 256);
        }

        // Store via streaming
        DiskItem stored = handler.storeFromStream(
                new ByteArrayInputStream(data), data.length, false, 30);
        assertNotNull(stored);

        // Get file path (no byte[] loading)
        Path filePath = handler.getFilePath(stored.getId());
        assertNotNull(filePath);
        assertTrue(Files.exists(filePath));

        // Get file size (no byte[] loading)
        long fileSize = handler.getFileSize(stored.getId());
        assertEquals(data.length, fileSize);

        // Read file content via streaming (instead of Files.readAllBytes)
        byte[] hash;
        try (InputStream fileStream = Files.newInputStream(filePath)) {
            hash = Hash.sha256x2FromStream(fileStream);
        }
        assertNotNull(hash);
        
        // Verify the hash matches the DID
        String computedDid = Hex.toHex(hash);
        assertEquals(stored.getId(), computedDid,
                "Hash computed via streaming should match the stored DID");

        // Also verify against in-memory hash
        byte[] inMemoryHash = Hash.sha256x2(data);
        assertArrayEquals(inMemoryHash, hash);
    }
}
