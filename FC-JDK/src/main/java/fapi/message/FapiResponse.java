package fapi.message;

import data.fcData.FcObject;
import fapi.FapiCode;
import utils.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * FAPI统一响应结构
 * 
 * ID设计说明：
 * - id: 响应自身的唯一ID（服务端生成），用于响应审计和日志
 * - requestId: 对应请求的ID（回传），便于客户端匹配请求-响应
 * 
 * 注意：
 * - 时间戳已在FUDP协议层包含（用于防重放），应用层不需要重复
 * - 认证已在FUDP层完成（公私钥身份验证），应用层无需额外认证
 * - code 与 FUDP 层 ResponseMessage.statusCode 保持一致
 */
public class FapiResponse extends FcObject {
    
    /* 响应ID（服务端生成，用于审计和日志） */

    /** 对应请求的ID（回传，便于客户端匹配） */
    private String requestId;
    
    /** 响应码 (0=成功，与FUDP层一致) */
    private Integer code;
    
    /** 响应消息 */
    private String message;
    
    /** 响应数据 */
    private Object data;
    
    /** 返回数量（查询类响应） */
    private Long got;
    
    /** 总数量（查询类响应） */
    private Long total;
    
    /** 分页游标（查询类响应） */
    private List<String> last;
    
    /** 最新区块高度 */
    private Long bestHeight;
    private String bestBlockId;
    
    /** 权威余额（单位聪） */
    private Long balance;
    
    /** 余额序列号 */
    private Long balanceSeq;
    
    /** 附加二进制数据的大小（字节），用于 GET 响应等，便于客户端预分配缓冲区 */
    private Long dataSize;
    
    /** 本次请求实际收取的费用（单位：聪） */
    private Long charged;
    
    /**
     * Transient: Server-side file path for streaming responses (not serialized to JSON).
     * When set, FapiServer will stream the file content from this path instead of
     * holding the entire file in a byte[] array.
     */
    private transient java.nio.file.Path streamSourcePath;
    
    /**
     * Transient: Size of the stream source file (for computing charges and message framing).
     */
    private transient long streamSourceSize;

    public FapiResponse() {
    }
    
    // ==================== 静态工厂方法 ====================
    
    /**
     * 创建成功响应
     */
    public static FapiResponse success(String requestId, Object data) {
        FapiResponse resp = new FapiResponse();
        resp.setId(generateResponseId());
        resp.setRequestId(requestId);
        resp.setCode(FapiCode.SUCCESS);
        resp.setMessage("Success");
        resp.setData(data);
        return resp;
    }
    
    /**
     * 创建成功响应（带分页信息）
     */
    public static FapiResponse success(String requestId, Object data, Long got, Long total, List<String> last) {
        FapiResponse resp = success(requestId, data);
        resp.setGot(got);
        resp.setTotal(total);
        resp.setLast(last);
        return resp;
    }
    
    /**
     * 创建错误响应
     */
    public static FapiResponse error(String requestId, int code, String message) {
        FapiResponse resp = new FapiResponse();
        resp.setId(generateResponseId());
        resp.setRequestId(requestId);
        resp.setCode(code);
        resp.setMessage(message);
        return resp;
    }
    
    /**
     * 创建错误响应（使用默认错误消息）
     */
    public static FapiResponse error(String requestId, int code) {
        return error(requestId, code, FapiCode.getMessage(code));
    }
    
    /**
     * 生成响应ID
     */
    private static String generateResponseId() {
        return "resp-" + System.currentTimeMillis() + "-" + 
               Integer.toHexString(ThreadLocalRandom.current().nextInt());
    }
    
    // ==================== Getters and Setters ====================

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public Long getGot() {
        return got;
    }

    public void setGot(Long got) {
        this.got = got;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public List<String> getLast() {
        return last;
    }

    public void setLast(List<String> last) {
        this.last = last;
    }

    public Long getBestHeight() {
        return bestHeight;
    }

    public void setBestHeight(Long bestHeight) {
        this.bestHeight = bestHeight;
    }

    public Long getBalance() {
        return balance;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
    }

    public Long getBalanceSeq() {
        return balanceSeq;
    }

    public void setBalanceSeq(Long balanceSeq) {
        this.balanceSeq = balanceSeq;
    }
    
    // ==================== 便捷方法 ====================
    
    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return code != null && FapiCode.isSuccess(code);
    }
    
    /**
     * 判断是否为客户端错误
     */
    public boolean isClientError() {
        return code != null && FapiCode.isClientError(code);
    }
    
    /**
     * 判断是否为服务端错误
     */
    public boolean isServerError() {
        return code != null && FapiCode.isServerError(code);
    }
    
    /**
     * 转换为 JSON 字符串
     */
    public String toJson() {
        return JsonUtils.toJson(this);
    }
    
    /**
     * 转换为 JSON 字节数组
     */
    public byte[] toJsonBytes() {
        String json = toJson();
        return json != null ? json.getBytes(StandardCharsets.UTF_8) : new byte[0];
    }

    public String getBestBlockId() {
        return bestBlockId;
    }

    public void setBestBlockId(String bestBlockId) {
        this.bestBlockId = bestBlockId;
    }
    
    public Long getDataSize() {
        return dataSize;
    }
    
    public void setDataSize(Long dataSize) {
        this.dataSize = dataSize;
    }
    
    /**
     * 是否包含二进制数据
     * @return true 如果 dataSize > 0
     */
    public boolean hasBinaryData() {
        return dataSize != null && dataSize > 0;
    }
    
    public Long getCharged() {
        return charged;
    }
    
    public void setCharged(Long charged) {
        this.charged = charged;
    }
    
    // ==================== Streaming support (transient) ====================
    
    /**
     * Get the server-side file path for streaming response.
     * This is transient and not serialized to JSON.
     */
    public java.nio.file.Path getStreamSourcePath() {
        return streamSourcePath;
    }
    
    public void setStreamSourcePath(java.nio.file.Path streamSourcePath) {
        this.streamSourcePath = streamSourcePath;
    }
    
    public long getStreamSourceSize() {
        return streamSourceSize;
    }
    
    public void setStreamSourceSize(long streamSourceSize) {
        this.streamSourceSize = streamSourceSize;
    }
    
    /**
     * Check if this response has a streaming source (file-based binary data).
     */
    public boolean hasStreamSource() {
        return streamSourcePath != null && streamSourceSize > 0;
    }
}
