package fapi.util;

import constants.CodeMessage;
import data.fchData.Block;
import fapi.FapiBalanceManager;
import fapi.message.FapiResponse;
import fapi.service.FapiServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * FAPI 响应构建工具
 * <p>
 * 用于构建成功和错误响应，统一填充余额和区块高度信息。
 * 所有方法都使用 FapiServer 作为上下文，确保余额信息被正确填充。
 */
public class ResponseBuilder {
    private static final Logger log = LoggerFactory.getLogger(ResponseBuilder.class);
    
    private ResponseBuilder() {
        // 工具类，禁止实例化
    }
    
    /**
     * 构建成功响应
     * 
     * @param data 查询结果数据
     * @param got 返回数量
     * @param total 总数量
     * @param last 分页游标
     * @param server FapiServer对象（用于获取bestHeight和余额）
     * @param peerId 请求来源（用于填充权威余额）
     * @return 序列化后的JSON字节数组
     */
    public static byte[] buildSuccessResponse(Object data, Long got, Long total, List<String> last, 
                                              FapiServer server, String peerId) {
        FapiResponse response = new FapiResponse();
        response.setCode(CodeMessage.Code0Success);
        response.setMessage(CodeMessage.getMsg(CodeMessage.Code0Success));
        response.setData(data);
        response.setGot(got);
        response.setTotal(total);
        response.setLast(last);
        
        fillServerInfo(response, server, peerId);
        
        return JsonUtils.toJson(response).getBytes(StandardCharsets.UTF_8);
    }
    
    /**
     * 构建成功响应（简化版，无分页信息）
     */
    public static byte[] buildSuccessResponse(Object data, FapiServer server, String peerId) {
        return buildSuccessResponse(data, null, null, null, server, peerId);
    }
    
    /**
     * 构建错误响应
     * 
     * @param code 错误码（CodeMessage 常量）
     * @param customMessage 自定义错误消息（可选，如果为null则使用CodeMessage中的默认消息）
     * @param server FapiServer对象（用于获取bestHeight和余额）
     * @param peerId 请求来源（用于填充权威余额）
     * @return 序列化后的JSON字节数组
     */
    public static byte[] buildErrorResponse(int code, String customMessage, FapiServer server, String peerId) {
        FapiResponse response = new FapiResponse();
        response.setCode(code);
        response.setMessage(customMessage != null ? customMessage : CodeMessage.getMsg(code));
        response.setData(null);
        response.setGot(0L);
        response.setTotal(0L);
        response.setLast(null);
        
        fillServerInfo(response, server, peerId);
        
        return JsonUtils.toJson(response).getBytes(StandardCharsets.UTF_8);
    }
    
    /**
     * 构建错误响应（使用默认消息）
     */
    public static byte[] buildErrorResponse(int code, FapiServer server, String peerId) {
        return buildErrorResponse(code, null, server, peerId);
    }
    
    /**
     * 构建错误响应JSON字符串（用于日志等场景，不包含余额信息）
     */
    public static String buildErrorJson(int code, String customMessage) {
        FapiResponse response = new FapiResponse();
        response.setCode(code);
        response.setMessage(customMessage != null ? customMessage : CodeMessage.getMsg(code));
        response.setData(null);
        response.setGot(0L);
        response.setTotal(0L);
        response.setLast(null);
        return JsonUtils.toJson(response);
    }

    /**
     * 填充服务器信息（区块高度和余额）
     */
    private static void fillServerInfo(FapiResponse response, FapiServer server, String peerId) {
        if (response == null || server == null) {
            return;
        }
        
        // 填充最佳区块高度
        if (server.getSettings() != null) {
            Block bestBlock = server.getSettings().getBestBlock();
            response.setBestHeight(bestBlock.getHeight());
            response.setBestBlockId(bestBlock.getId());
        }
        
        // 填充余额信息
        if (peerId != null) {
            fillBalance(response, server, peerId);
        }
    }
    
    /**
     * 使用 FapiServer 的余额管理器填充余额信息
     */
    private static void fillBalance(FapiResponse response, FapiServer server, String peerId) {
        try {
            FapiBalanceManager balanceManager = server.getBalanceManager();
            if (balanceManager != null) {
                FapiBalanceManager.BalanceView view = balanceManager.getBalance(peerId);
                if (view != null) {
                    response.setBalance(view.getBalance());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fill balance for peer {}: {}", peerId, e.getMessage());
        }
    }
}
