package fapi.message;

import data.apipData.Fcdsl;
import data.fcData.FcObject;

/**
 * FAPI统一请求结构
 * 
 * 设计原则：
 * - 统一使用 api 字段，格式为 "component.method"
 * - 查询类请求使用 fcdsl 字段（标准查询语法）
 * - 其他操作使用 params 字段（灵活参数）
 * - 两者互斥：fcdsl 用于查询，params 用于其他操作
 * 
 * 注意：
 * - 无需nonce字段，防重放由FUDP层处理（ReplayProtection + 加密）
 * - 无需sign字段，认证由FUDP层处理（公私钥签名）
 */
public class FapiRequest extends FcObject {
    
    /* 请求ID（客户端生成，用于追踪和幂等控制） */

    /** API名称: "component.method" (如 "base.search", "disk.put") */
    private String api;
    
    /** 服务ID（可选，用于多服务场景） */
    private String sid;
    
    /** 消费渠道FID（可选，用于渠道分成） */
    private String via;
    
    /** 查询参数（用于 search/getByIds 等查询类API） */
    private Fcdsl fcdsl;
    
    /** 操作参数（用于 put/get/carve 等非查询API） */
    private Object params;
    
    /** 附加二进制数据的大小（字节），用于 PUT/CARVE 等上传操作 */
    private Long dataSize;
    
    /** 附加二进制数据的 SHA256 哈希（可选，用于完整性校验） */
    private String dataHash;
    
    /** 最大费用限制（单位：聪），超过此限制服务端应拒绝请求。null 或 0 表示无限制 */
    private Long maxCost;
    
    public FapiRequest() {
    }
    
    /**
     * 从API名称提取组件名
     * @return 组件名（大写），如 "BASE"、"DISK"；格式错误返回 null
     */
    public String getComponentName() {
        if (api == null || !api.contains(".")) {
            return null;
        }
        return api.split("\\.", 2)[0].toUpperCase();
    }
    
    /**
     * 从API名称提取方法名
     * @return 方法名，如 "search"、"getByIds"；格式错误返回 null
     */
    public String getMethodName() {
        if (api == null || !api.contains(".")) {
            return null;
        }
        return api.split("\\.", 2)[1];
    }
    
    /**
     * 是否为查询类请求
     */
    public boolean isQuery() {
        return fcdsl != null;
    }
    
    /**
     * 是否包含二进制数据
     * @return true 如果 dataSize > 0
     */
    public boolean hasBinaryData() {
        return dataSize != null && dataSize > 0;
    }
    
    // ==================== Getters and Setters ====================

    public String getApi() {
        return api;
    }
    
    public void setApi(String api) {
        this.api = api;
    }
    
    public String getSid() {
        return sid;
    }
    
    public void setSid(String sid) {
        this.sid = sid;
    }
    
    public String getVia() {
        return via;
    }
    
    public void setVia(String via) {
        this.via = via;
    }
    
    public Fcdsl getFcdsl() {
        return fcdsl;
    }
    
    public void setFcdsl(Fcdsl fcdsl) {
        this.fcdsl = fcdsl;
    }
    
    public Object getParams() {
        return params;
    }
    
    public void setParams(Object params) {
        this.params = params;
    }
    
    public Long getDataSize() {
        return dataSize;
    }
    
    public void setDataSize(Long dataSize) {
        this.dataSize = dataSize;
    }
    
    public String getDataHash() {
        return dataHash;
    }
    
    public void setDataHash(String dataHash) {
        this.dataHash = dataHash;
    }
    
    public Long getMaxCost() {
        return maxCost;
    }
    
    public void setMaxCost(Long maxCost) {
        this.maxCost = maxCost;
    }
    
    /**
     * 检查是否设置了费用限制
     * @return true 如果 maxCost > 0
     */
    public boolean hasMaxCost() {
        return maxCost != null && maxCost > 0;
    }
    
    /**
     * 链式设置最大费用限制
     * @param maxCost 最大费用（聪）
     * @return this
     */
    public FapiRequest withMaxCost(Long maxCost) {
        this.maxCost = maxCost;
        return this;
    }
    
    /**
     * 链式设置消费渠道
     * @param via 渠道 FID
     * @return this
     */
    public FapiRequest withVia(String via) {
        this.via = via;
        return this;
    }
    
    // ==================== 便捷构建方法 ====================
    
    /**
     * 创建查询类请求
     */
    public static FapiRequest query(String api, Fcdsl fcdsl) {
        FapiRequest request = new FapiRequest();
        request.setId(generateRequestId());
        request.setApi(api);
        request.setFcdsl(fcdsl);
        return request;
    }
    
    /**
     * 创建操作类请求
     */
    public static FapiRequest operation(String api, Object params) {
        FapiRequest request = new FapiRequest();
        request.setId(generateRequestId());
        request.setApi(api);
        request.setParams(params);
        return request;
    }
    
    /**
     * 创建简单请求（无参数）
     */
    public static FapiRequest simple(String api) {
        FapiRequest request = new FapiRequest();
        request.setId(generateRequestId());
        request.setApi(api);
        return request;
    }
    
    /**
     * 创建二进制操作请求（用于 PUT/CARVE 等上传操作）
     * @param api API名称，如 "disk.put"
     * @param params 操作参数
     * @param dataSize 二进制数据大小（字节）
     * @param dataHash 二进制数据的 SHA256 哈希（可选）
     */
    public static FapiRequest binaryOperation(String api, Object params, long dataSize, String dataHash) {
        FapiRequest request = new FapiRequest();
        request.setId(generateRequestId());
        request.setApi(api);
        request.setParams(params);
        request.setDataSize(dataSize);
        request.setDataHash(dataHash);
        return request;
    }
    
    /**
     * 生成请求ID
     */
    private static String generateRequestId() {
        return "req-" + System.currentTimeMillis() + "-" + 
               Integer.toHexString(java.util.concurrent.ThreadLocalRandom.current().nextInt());
    }
}

