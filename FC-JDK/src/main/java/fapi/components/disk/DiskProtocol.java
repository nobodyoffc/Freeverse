package fapi.components.disk;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Binary protocol for DISK operations.
 * Provides efficient transfer without Base64 overhead.
 * 
 * Protocol formats:
 * 
 * PUT/CARVE Request:
 * ┌──────────────────────────────────────────┐
 * │ Operation (1 byte): 0x01=PUT, 0x02=CARVE │
 * │ Metadata Length (4 bytes, big-endian)    │
 * │ Metadata JSON (UTF-8)                    │
 * │ File Content (raw bytes)                 │
 * └──────────────────────────────────────────┘
 * 
 * GET Request:
 * ┌──────────────────────────────────────────┐
 * │ Operation (1 byte): 0x03=GET             │
 * │ DID (64 bytes, hex string)               │
 * └──────────────────────────────────────────┘
 * 
 * GET Response:
 * ┌──────────────────────────────────────────┐
 * │ Status (1 byte): 0=success, 1=not found  │
 * │ Metadata Length (4 bytes, big-endian)    │
 * │ Metadata JSON (UTF-8)                    │
 * │ File Content (raw bytes)                 │
 * └──────────────────────────────────────────┘
 */
public class DiskProtocol {
    
    // Operation codes
    public static final byte OP_PUT = 0x01;
    public static final byte OP_CARVE = 0x02;
    public static final byte OP_GET = 0x03;
    
    // Response status codes
    public static final byte STATUS_SUCCESS = 0x00;
    public static final byte STATUS_NOT_FOUND = 0x01;
    public static final byte STATUS_ERROR = 0x02;
    public static final byte STATUS_INVALID_REQUEST = 0x03;
    
    // DID length (SHA256x2 = 64 hex characters)
    public static final int DID_LENGTH = 64;
    
    // ==================== Encode Methods ====================
    
    /**
     * Encode PUT/CARVE request.
     * 
     * @param operation OP_PUT or OP_CARVE
     * @param metadata Optional metadata JSON (can be null or empty)
     * @param content File content
     * @return Encoded binary request
     */
    public static byte[] encodePutRequest(byte operation, String metadata, byte[] content) {
        if (operation != OP_PUT && operation != OP_CARVE) {
            throw new IllegalArgumentException("Invalid operation: " + operation);
        }
        if (content == null) {
            throw new IllegalArgumentException("Content cannot be null");
        }
        
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            
            // Operation (1 byte)
            dos.writeByte(operation);
            
            // Metadata
            byte[] metadataBytes = (metadata != null && !metadata.isEmpty()) 
                    ? metadata.getBytes(StandardCharsets.UTF_8) 
                    : new byte[0];
            
            // Metadata length (4 bytes, big-endian)
            dos.writeInt(metadataBytes.length);
            
            // Metadata content
            if (metadataBytes.length > 0) {
                dos.write(metadataBytes);
            }
            
            // File content
            dos.write(content);
            
            dos.flush();
            return baos.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode PUT request", e);
        }
    }
    
    /**
     * Decode PUT/CARVE request.
     * 
     * @param data Raw binary data
     * @return PutRequest with operation, metadata, and content
     * @throws IllegalArgumentException if data is invalid
     */
    public static PutRequest decodePutRequest(byte[] data) {
        if (data == null || data.length < 5) {
            throw new IllegalArgumentException("Invalid request data: too short");
        }
        
        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
            
            // Operation (1 byte)
            byte operation = dis.readByte();
            if (operation != OP_PUT && operation != OP_CARVE) {
                throw new IllegalArgumentException("Invalid operation: " + operation);
            }
            
            // Metadata length (4 bytes)
            int metadataLength = dis.readInt();
            if (metadataLength < 0 || metadataLength > data.length - 5) {
                throw new IllegalArgumentException("Invalid metadata length: " + metadataLength);
            }
            
            // Metadata content
            String metadata = null;
            if (metadataLength > 0) {
                byte[] metadataBytes = new byte[metadataLength];
                dis.readFully(metadataBytes);
                metadata = new String(metadataBytes, StandardCharsets.UTF_8);
            }
            
            // Remaining bytes are file content
            int contentLength = data.length - 5 - metadataLength;
            byte[] content = new byte[contentLength];
            if (contentLength > 0) {
                dis.readFully(content);
            }
            
            return new PutRequest(operation, metadata, content);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to decode PUT request", e);
        }
    }
    
    /**
     * Encode GET request.
     * 
     * @param did Data ID (64 hex characters)
     * @return Encoded binary request
     */
    public static byte[] encodeGetRequest(String did) {
        if (did == null || did.length() != DID_LENGTH) {
            throw new IllegalArgumentException("Invalid DID: must be 64 hex characters");
        }
        
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            
            // Operation (1 byte)
            dos.writeByte(OP_GET);
            
            // DID as ASCII bytes (64 bytes)
            dos.write(did.getBytes(StandardCharsets.US_ASCII));
            
            dos.flush();
            return baos.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode GET request", e);
        }
    }
    
    /**
     * Decode GET request.
     * 
     * @param data Raw binary data
     * @return DID string (64 hex characters)
     * @throws IllegalArgumentException if data is invalid
     */
    public static String decodeGetRequest(byte[] data) {
        if (data == null || data.length != 1 + DID_LENGTH) {
            throw new IllegalArgumentException("Invalid GET request: expected " + (1 + DID_LENGTH) + " bytes, got " + (data != null ? data.length : 0));
        }
        
        byte operation = data[0];
        if (operation != OP_GET) {
            throw new IllegalArgumentException("Invalid operation for GET request: " + operation);
        }
        
        byte[] didBytes = new byte[DID_LENGTH];
        System.arraycopy(data, 1, didBytes, 0, DID_LENGTH);
        return new String(didBytes, StandardCharsets.US_ASCII);
    }
    
    /**
     * Encode GET response with file content.
     * 
     * @param status Response status (STATUS_SUCCESS, STATUS_NOT_FOUND, etc.)
     * @param metadata Metadata JSON (can be null for errors)
     * @param content File content (null if error)
     * @return Encoded binary response
     */
    public static byte[] encodeGetResponse(byte status, String metadata, byte[] content) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            
            // Status (1 byte)
            dos.writeByte(status);
            
            // Metadata
            byte[] metadataBytes = (metadata != null && !metadata.isEmpty()) 
                    ? metadata.getBytes(StandardCharsets.UTF_8) 
                    : new byte[0];
            
            // Metadata length (4 bytes)
            dos.writeInt(metadataBytes.length);
            
            // Metadata content
            if (metadataBytes.length > 0) {
                dos.write(metadataBytes);
            }
            
            // File content (only if success and content exists)
            if (status == STATUS_SUCCESS && content != null && content.length > 0) {
                dos.write(content);
            }
            
            dos.flush();
            return baos.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode GET response", e);
        }
    }
    
    /**
     * Decode GET response.
     * 
     * @param data Raw binary data
     * @return GetResponse with status, metadata, and content
     * @throws IllegalArgumentException if data is invalid
     */
    public static GetResponse decodeGetResponse(byte[] data) {
        if (data == null || data.length < 5) {
            throw new IllegalArgumentException("Invalid response data: too short");
        }
        
        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
            
            // Status (1 byte)
            byte status = dis.readByte();
            
            // Metadata length (4 bytes)
            int metadataLength = dis.readInt();
            if (metadataLength < 0 || metadataLength > data.length - 5) {
                throw new IllegalArgumentException("Invalid metadata length: " + metadataLength);
            }
            
            // Metadata content
            String metadata = null;
            if (metadataLength > 0) {
                byte[] metadataBytes = new byte[metadataLength];
                dis.readFully(metadataBytes);
                metadata = new String(metadataBytes, StandardCharsets.UTF_8);
            }
            
            // Remaining bytes are file content
            int contentLength = data.length - 5 - metadataLength;
            byte[] content = null;
            if (contentLength > 0) {
                content = new byte[contentLength];
                dis.readFully(content);
            }
            
            return new GetResponse(status, metadata, content);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to decode GET response", e);
        }
    }
    
    /**
     * Get operation code from request data.
     * 
     * @param data Raw binary data
     * @return Operation code (OP_PUT, OP_CARVE, OP_GET)
     */
    public static byte getOperation(byte[] data) {
        if (data == null || data.length < 1) {
            throw new IllegalArgumentException("Invalid request data: empty");
        }
        return data[0];
    }
    
    /**
     * Check if request is a binary disk protocol request.
     * 
     * @param data Raw binary data
     * @return true if this is a disk protocol request
     */
    public static boolean isDiskProtocol(byte[] data) {
        if (data == null || data.length < 1) {
            return false;
        }
        byte op = data[0];
        return op == OP_PUT || op == OP_CARVE || op == OP_GET;
    }
    
    // ==================== Record Classes ====================
    
    /**
     * Parsed PUT/CARVE request.
     */
    public record PutRequest(byte operation, String metadata, byte[] content) {
        public boolean isPermanent() {
            return operation == OP_CARVE;
        }
    }
    
    /**
     * Parsed GET response.
     */
    public record GetResponse(byte status, String metadata, byte[] content) {
        public boolean isSuccess() {
            return status == STATUS_SUCCESS;
        }
        
        public boolean isNotFound() {
            return status == STATUS_NOT_FOUND;
        }
    }
}
