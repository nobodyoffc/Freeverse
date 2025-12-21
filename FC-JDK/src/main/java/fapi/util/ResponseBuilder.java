package fapi.util;

import constants.CodeMessage;
import config.Settings;
import fapi.message.FapiResponse;
import handlers.BalanceManager;
import handlers.Manager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * FAPI 响应构建工具
 * 用于构建成功和错误响应
 */
public class ResponseBuilder {
    private static final Logger log = LoggerFactory.getLogger(ResponseBuilder.class);
    
    /**
     * 构建成功响应
     * @param data 查询结果数据
     * @param got 返回数量
     * @param total 总数量
     * @param last 分页游标
     * @param settings Settings对象（用于获取bestHeight）
     * @param peerId 请求来源（用于填充权威余额，可为空）
     * @return 序列化后的JSON字节数组
     */
    public static byte[] buildSuccessResponse(Object data, Long got, Long total, List<String> last, Settings settings, String peerId) {
        FapiResponse response = new FapiResponse();
        response.setCode(CodeMessage.Code0Success);
        response.setMessage(CodeMessage.getMsg(CodeMessage.Code0Success));
        response.setData(data);
        response.setGot(got);
        response.setTotal(total);
        response.setLast(last);
        
        // 获取最佳区块高度（从 ES 或 NaSaRpcClient）
        Long bestHeight = settings.getBestHeight();
        response.setBestHeight(bestHeight);
        fillBalance(response, settings, peerId);
        
        return JsonUtils.toJson(response).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 兼容旧签名
     */
    public static byte[] buildSuccessResponse(Object data, Long got, Long total, List<String> last, Settings settings) {
        return buildSuccessResponse(data, got, total, last, settings, null);
    }
    
    /**
     * 构建错误响应
     * @param code 错误码（CodeMessage 常量）
     * @param customMessage 自定义错误消息（可选，如果为null则使用CodeMessage中的默认消息）
     * @param settings Settings对象（用于获取bestHeight）
     * @param peerId 请求来源（用于填充权威余额，可为空）
     * @return 序列化后的JSON字节数组
     */
    public static byte[] buildErrorResponse(int code, String customMessage, Settings settings, String peerId) {
        FapiResponse response = new FapiResponse();
        response.setCode(code);
        response.setMessage(customMessage != null ? customMessage : CodeMessage.getMsg(code));
        response.setData(null);
        response.setGot(0L);
        response.setTotal(0L);
        response.setLast(null);
        
        // 设置最佳区块高度
        if (settings != null) {
            Long bestHeight = settings.getBestHeight();
            response.setBestHeight(bestHeight);
            fillBalance(response, settings, peerId);
        }
        
        return JsonUtils.toJson(response).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 兼容旧签名
     */
    public static byte[] buildErrorResponse(int code, String customMessage, Settings settings) {
        return buildErrorResponse(code, customMessage, settings, null);
    }
    
    /**
     * 构建错误响应（使用默认消息）
     */
    public static byte[] buildErrorResponse(int code, Settings settings) {
        return buildErrorResponse(code, null, settings);
    }
    
    /**
     * 构建错误响应JSON字符串（用于日志等场景）
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

    private static void fillBalance(FapiResponse response, Settings settings, String peerId) {
        if (response == null || settings == null || peerId == null) {
            return;
        }
        boolean logBalanceReadError = Settings.DEFAULT_LOG_BALANCE_READ_ERROR;
        try {
            Object flag = settings.getSettingMap() != null ? settings.getSettingMap().get(Settings.LOG_BALANCE_READ_ERROR) : null;
            if (flag instanceof Boolean b) {
                logBalanceReadError = b;
            }
        } catch (Exception ignored) {
            // fall back to default
        }
        try {
            var manager = settings.getManager(Manager.ManagerType.BALANCE);
            if (manager instanceof BalanceManager balanceManager) {
                var view = balanceManager.getBalance(peerId);
                if (view != null) {
                    response.setBalance(view.getBalance());
                    // 当前实现没有序列号，保留为 null 以便后续扩展
                    response.setBalanceSeq(null);
                }
            }
        } catch (Exception e) {
            if (logBalanceReadError) {
                log.warn("Failed to fill balance for peer {}: {}", peerId, e.getMessage());
            }
        }
    }
}
