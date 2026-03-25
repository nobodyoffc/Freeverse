package fapi.message;

import utils.JsonUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 统一二进制协议编解码器
 * <p>
 * 用于 FAPI 请求和响应的编解码，支持可选的二进制数据附加。
 * <p>
 * 协议格式：
 * <pre>
 * ┌───────────────────────────────────────────────────────┐
 * │ Header Length (4 bytes, big-endian)                   │
 * │ JSON Header (UTF-8, FapiRequest or FapiResponse)      │
 * │ Binary Data (remaining bytes, optional)               │
 * └───────────────────────────────────────────────────────┘
 * </pre>
 */
public class UnifiedCodec {
    
    // ==================== Record Classes ====================
    
    /**
     * 解码后的统一请求
     */
    public record UnifiedRequest(FapiRequest request, byte[] binaryData) {
        /**
         * 是否包含二进制数据
         */
        public boolean hasBinaryData() {
            return binaryData != null && binaryData.length > 0;
        }
    }
    
    /**
     * 解码后的统一响应
     */
    public record UnifiedResponse(FapiResponse response, byte[] binaryData) {
        /**
         * 是否包含二进制数据
         */
        public boolean hasBinaryData() {
            return binaryData != null && binaryData.length > 0;
        }
    }
    
    // ==================== Request Methods ====================
    
    /**
     * 编码请求（无二进制数据）
     * @param request FapiRequest 对象
     * @return 编码后的字节数组
     */
    public static byte[] encodeRequest(FapiRequest request) {
        return encodeRequest(request, null);
    }
    
    /**
     * 编码请求（带可选二进制数据）
     * @param request FapiRequest 对象
     * @param binaryData 可选的二进制数据
     * @return 编码后的字节数组
     */
    public static byte[] encodeRequest(FapiRequest request, byte[] binaryData) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        
        // 如果有二进制数据，设置 dataSize
        if (binaryData != null && binaryData.length > 0) {
            request.setDataSize((long) binaryData.length);
        }
        
        String json = JsonUtils.toJson(request);
        return encode(json, binaryData);
    }
    
    /**
     * 解码请求
     * @param data 原始字节数组
     * @return UnifiedRequest 对象
     * @throws IllegalArgumentException 如果数据格式无效
     */
    public static UnifiedRequest decodeRequest(byte[] data) {
        if (data == null || data.length < 4) {
            throw new IllegalArgumentException("Invalid request data: too short");
        }
        
        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
            
            // Header length (4 bytes)
            int headerLength = dis.readInt();
            if (headerLength < 0 || headerLength > data.length - 4) {
                throw new IllegalArgumentException("Invalid header length: " + headerLength);
            }
            
            // JSON header
            byte[] jsonBytes = new byte[headerLength];
            dis.readFully(jsonBytes);
            String json = new String(jsonBytes, StandardCharsets.UTF_8);
            
            FapiRequest request = JsonUtils.fromJson(json, FapiRequest.class);
            if (request == null) {
                throw new IllegalArgumentException("Failed to parse FapiRequest from JSON");
            }
            
            // Binary data (remaining bytes)
            int binaryLength = data.length - 4 - headerLength;
            byte[] binaryData = null;
            if (binaryLength > 0) {
                binaryData = new byte[binaryLength];
                dis.readFully(binaryData);
            }
            
            return new UnifiedRequest(request, binaryData);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to decode request", e);
        }
    }
    
    // ==================== Response Methods ====================
    
    /**
     * 编码响应（无二进制数据）
     * @param response FapiResponse 对象
     * @return 编码后的字节数组
     */
    public static byte[] encodeResponse(FapiResponse response) {
        return encodeResponse(response, null);
    }
    
    /**
     * 编码响应（带可选二进制数据）
     * @param response FapiResponse 对象
     * @param binaryData 可选的二进制数据
     * @return 编码后的字节数组
     */
    public static byte[] encodeResponse(FapiResponse response, byte[] binaryData) {
        if (response == null) {
            throw new IllegalArgumentException("response cannot be null");
        }
        
        // 如果有二进制数据，设置 dataSize
        if (binaryData != null && binaryData.length > 0) {
            response.setDataSize((long) binaryData.length);
        }
        
        String json = JsonUtils.toJson(response);
        return encode(json, binaryData);
    }
    
    /**
     * 从 UnifiedResponse 编码响应
     * @param unified UnifiedResponse 对象
     * @return 编码后的字节数组
     */
    public static byte[] encodeResponse(UnifiedResponse unified) {
        if (unified == null || unified.response() == null) {
            throw new IllegalArgumentException("unified response cannot be null");
        }
        return encodeResponse(unified.response(), unified.binaryData());
    }
    
    /**
     * 解码响应
     * @param data 原始字节数组
     * @return UnifiedResponse 对象
     * @throws IllegalArgumentException 如果数据格式无效
     */
    public static UnifiedResponse decodeResponse(byte[] data) {
        if (data == null || data.length < 4) {
            throw new IllegalArgumentException("Invalid response data: too short");
        }
        
        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
            
            // Header length (4 bytes)
            int headerLength = dis.readInt();
            if (headerLength < 0 || headerLength > data.length - 4) {
                throw new IllegalArgumentException("Invalid header length: " + headerLength);
            }
            
            // JSON header
            byte[] jsonBytes = new byte[headerLength];
            dis.readFully(jsonBytes);
            String json = new String(jsonBytes, StandardCharsets.UTF_8);
            
            FapiResponse response = JsonUtils.fromJson(json, FapiResponse.class);
            if (response == null) {
                throw new IllegalArgumentException("Failed to parse FapiResponse from JSON");
            }
            
            // Binary data (remaining bytes)
            int binaryLength = data.length - 4 - headerLength;
            byte[] binaryData = null;
            if (binaryLength > 0) {
                binaryData = new byte[binaryLength];
                dis.readFully(binaryData);
            }
            
            return new UnifiedResponse(response, binaryData);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to decode response", e);
        }
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * 通用编码方法
     * @param json JSON 字符串
     * @param binaryData 可选的二进制数据
     * @return 编码后的字节数组
     */
    private static byte[] encode(String json, byte[] binaryData) {
        try {
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            
            // Header length (4 bytes, big-endian)
            dos.writeInt(jsonBytes.length);
            
            // JSON header
            dos.write(jsonBytes);
            
            // Binary data (optional)
            if (binaryData != null && binaryData.length > 0) {
                dos.write(binaryData);
            }
            
            dos.flush();
            return baos.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode", e);
        }
    }
    
    // ==================== Streaming Encode Methods ====================
    
    /**
     * Encode only the header portion of a request (4-byte length + JSON).
     * The binary data is NOT included — it should be streamed separately.
     * This is used for streaming uploads where the file content is sent via InputStream.
     *
     * @param request FapiRequest object (dataSize should be set to the binary data size)
     * @return Encoded header bytes: [headerLen(4)][JSON]
     */
    public static byte[] encodeRequestHeaderOnly(FapiRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        String json = JsonUtils.toJson(request);
        return encodeHeaderOnly(json);
    }
    
    /**
     * Encode only the header portion of a response (4-byte length + JSON).
     * The binary data is NOT included — it should be streamed separately.
     * This is used for streaming downloads where the file content is sent via InputStream.
     *
     * @param response FapiResponse object (dataSize should be set to the binary data size)
     * @return Encoded header bytes: [headerLen(4)][JSON]
     */
    public static byte[] encodeResponseHeaderOnly(FapiResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("response cannot be null");
        }
        String json = JsonUtils.toJson(response);
        return encodeHeaderOnly(json);
    }
    
    /**
     * Encode only the header: [headerLen(4)][JSON bytes].
     * No binary data is appended.
     */
    private static byte[] encodeHeaderOnly(String json) {
        try {
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream baos = new ByteArrayOutputStream(4 + jsonBytes.length);
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(jsonBytes.length);
            dos.write(jsonBytes);
            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode header", e);
        }
    }
    
    /**
     * 检查数据是否为统一协议格式
     * <p>
     * 通过检查前4字节是否为合理的 header length 来判断
     * @param data 原始字节数组
     * @return true 如果数据符合统一协议格式
     */
    public static boolean isUnifiedProtocol(byte[] data) {
        if (data == null || data.length < 5) {
            return false;
        }
        
        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
            int headerLength = dis.readInt();
            
            // Header length should be positive and reasonable
            if (headerLength <= 0 || headerLength > data.length - 4) {
                return false;
            }
            
            // Check if the JSON starts with '{'
            if (data.length > 4 && data[4] == '{') {
                return true;
            }
            
            return false;
            
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * 快速获取 header length（不完全解码）
     * @param data 原始字节数组
     * @return header length，如果无效返回 -1
     */
    public static int peekHeaderLength(byte[] data) {
        if (data == null || data.length < 4) {
            return -1;
        }
        
        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
            return dis.readInt();
        } catch (IOException e) {
            return -1;
        }
    }
}
