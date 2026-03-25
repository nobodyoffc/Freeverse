package fapi.service.tasks;

import fapi.FapiBalanceManager;
import fapi.FapiBalanceManager.ResultCode;
import fapi.FapiBalanceManager.SettleResult;
import fapi.service.BlockEventDispatcher.BlockTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 周期结算任务
 * <p>
 * 根据区块高度检查是否达到结算周期，达到则执行自动结算。
 */
public class SettleTask implements BlockTask {
    private static final Logger log = LoggerFactory.getLogger(SettleTask.class);
    
    public static final String TASK_NAME = "SettleTask";
    public static final int PRIORITY = 50;  // 中优先级，在充值扫描之后
    
    private final FapiBalanceManager balanceManager;
    private final long settleCycle;
    private final Map<String, Long> stakeholders;
    
    public SettleTask(FapiBalanceManager balanceManager, 
                      long settleCycle, 
                      Map<String, Long> stakeholders) {
        this.balanceManager = balanceManager;
        this.settleCycle = settleCycle;
        this.stakeholders = stakeholders;
    }
    
    @Override
    public String getName() {
        return TASK_NAME;
    }
    
    @Override
    public int getPriority() {
        return PRIORITY;
    }
    
    @Override
    public void execute(long currentHeight, long previousHeight) {
        try {
            Long lastSettleHeight = balanceManager.getLastSettleHeight();
            
            if (lastSettleHeight == null) {
                // 首次运行，初始化为当前高度
                balanceManager.setLastSettleHeight(currentHeight);
                log.info("SettleTask: initialized lastSettleHeight to {}", currentHeight);
                return;
            }
            
            // 检查是否达到结算周期
            long blocksSinceLastSettle = currentHeight - lastSettleHeight;
            
            if (blocksSinceLastSettle >= settleCycle) {
                log.info("Settlement triggered: lastSettleHeight={}, currentHeight={}, blocksSince={}, cycle={}", 
                        lastSettleHeight, currentHeight, blocksSinceLastSettle, settleCycle);
                
                SettleResult result = balanceManager.periodicSettle(
                        lastSettleHeight, currentHeight, stakeholders);
                
                if (result.isSuccess()) {
                    // 更新上次结算高度
                    balanceManager.setLastSettleHeight(currentHeight);
                    log.info("Settlement completed successfully: cycleId={}, distributed={} sat", 
                            result.getRecord().getCycleId(), result.getRecord().getTotalDistributed());
                } else if (result.getCode() == ResultCode.ALREADY_EXISTS) {
                    // 已结算过，更新高度
                    balanceManager.setLastSettleHeight(currentHeight);
                    log.debug("Settlement already exists for this period");
                } else {
                    log.warn("Settlement failed: code={}, message={}", result.getCode(), result.getMessage());
                }
            }
//            else {
//                log.debug("SettleTask: not yet time for settlement. blocksSince={}, cycle={}",
//                        blocksSinceLastSettle, settleCycle);
//            }
            
        } catch (Exception e) {
            log.error("SettleTask error", e);
        }
    }
    
    /**
     * 获取结算周期
     */
    public long getSettleCycle() {
        return settleCycle;
    }
    
    /**
     * 获取股东配置
     */
    public Map<String, Long> getStakeholders() {
        return stakeholders;
    }
}

