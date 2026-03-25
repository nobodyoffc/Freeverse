package fapi.security;

import fapi.FapiCode;

/**
 * 验证结果
 */
public class ValidationResult {
    
    private final boolean valid;
    private final int code;
    private final String message;
    
    private ValidationResult(boolean valid, int code, String message) {
        this.valid = valid;
        this.code = code;
        this.message = message;
    }
    
    /**
     * 创建成功结果
     */
    public static ValidationResult ok() {
        return new ValidationResult(true, FapiCode.SUCCESS, null);
    }
    
    /**
     * 创建失败结果
     */
    public static ValidationResult fail(int code, String message) {
        return new ValidationResult(false, code, message);
    }
    
    /**
     * 创建失败结果（使用默认消息）
     */
    public static ValidationResult fail(int code) {
        return new ValidationResult(false, code, FapiCode.getMessage(code));
    }
    
    /**
     * 创建失败结果（BAD_REQUEST）
     */
    public static ValidationResult fail(String message) {
        return new ValidationResult(false, FapiCode.BAD_REQUEST, message);
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
    
    @Override
    public String toString() {
        if (valid) {
            return "ValidationResult{valid=true}";
        } else {
            return "ValidationResult{valid=false, code=" + code + ", message='" + message + "'}";
        }
    }
}

