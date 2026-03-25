package fapi.monitor;

/**
 * 组件健康状态
 */
public class ComponentHealth {
    
    /** 组件名称 */
    private String name;
    
    /** 组件状态 */
    private String state;
    
    /** 是否健康 */
    private boolean healthy;
    
    /** API数量 */
    private int apiCount;
    
    // ==================== Getters and Setters ====================
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getState() {
        return state;
    }
    
    public void setState(String state) {
        this.state = state;
    }
    
    public boolean isHealthy() {
        return healthy;
    }
    
    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }
    
    public int getApiCount() {
        return apiCount;
    }
    
    public void setApiCount(int apiCount) {
        this.apiCount = apiCount;
    }
}

