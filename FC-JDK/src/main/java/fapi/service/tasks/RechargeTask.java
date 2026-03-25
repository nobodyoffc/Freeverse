package fapi.service.tasks;

import fapi.FapiBalanceManager;
import fapi.FapiBalanceManager.CreditResult;
import fapi.FapiBalanceManager.CreditStatus;
import fapi.FapiBalanceManager.ResultCode;
import fapi.recharge.RechargeScanner.CashInfo;
import fapi.service.BlockEventDispatcher.BlockTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 充值扫描任务
 * <p>
 * 在新区块产生时扫描新的充值订单。
 */
public class RechargeTask implements BlockTask {
    private static final Logger log = LoggerFactory.getLogger(RechargeTask.class);
    
    public static final String TASK_NAME = "RechargeTask";
    public static final int PRIORITY = 10;  // 高优先级，先处理充值
    
    private final FapiBalanceManager balanceManager;
    private final String serviceAddress;
    private final int confirmations;
    private final Function<Long, List<CashInfo>> cashQueryFunction;
    private final Function<List<CashInfo>, Map<String, String>> viaQueryFunction;
    
    public RechargeTask(FapiBalanceManager balanceManager, 
                        String serviceAddress,
                        int confirmations,
                        Function<Long, List<CashInfo>> cashQueryFunction,
                        Function<List<CashInfo>, Map<String, String>> viaQueryFunction) {
        this.balanceManager = balanceManager;
        this.serviceAddress = serviceAddress;
        this.confirmations = confirmations;
        this.cashQueryFunction = cashQueryFunction;
        this.viaQueryFunction = viaQueryFunction;
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
            // 更新 FapiBalanceManager 的最佳区块信息
            balanceManager.updateBestBlock(currentHeight, null);
            
            // 从持久化存储读取 lastOrderScanHeight
            Long lastOrderScanHeight = balanceManager.getLastOrderScanHeight();
            
            // 确定查询起始高度
            long fromHeight = lastOrderScanHeight != null ? lastOrderScanHeight : 0;
            
            log.debug("RechargeTask: scanning from height {}, currentHeight={}", fromHeight, currentHeight);
            
            // 查询新的 Cash
            List<CashInfo> newCashes = cashQueryFunction.apply(fromHeight);
            
            if (newCashes == null || newCashes.isEmpty()) {
//                log.debug("RechargeTask: no new cashes found");
                
                // 如果没有新订单且 lastOrderScanHeight 为空，保存当前最佳高度
                if (lastOrderScanHeight == null && currentHeight > 0) {
                    balanceManager.setLastOrderScanHeight(currentHeight);
                    log.info("RechargeTask: initialized lastOrderScanHeight to {}", currentHeight);
                }
                return;
            }
            
            // 过滤出发送到服务地址的 Cash
            List<CashInfo> targetCashes = newCashes.stream()
                    .filter(cash -> serviceAddress.equals(cash.getOwner()))
                    .sorted((a, b) -> {
                        int cmp = Long.compare(a.getBirthHeight(), b.getBirthHeight());
                        return cmp != 0 ? cmp : a.getCashId().compareTo(b.getCashId());
                    })
                    .toList();
            
            // 批量查询 OpReturn 获取渠道信息
            Map<String, String> viaMap = viaQueryFunction != null 
                    ? viaQueryFunction.apply(targetCashes) 
                    : Map.of();
            
            int processed = 0;
            int skipped = newCashes.size() - targetCashes.size();
            int unconfirmed = 0;
            int withVia = 0;
            long lastProcessedHeight = fromHeight;
            
            for (CashInfo cash : targetCashes) {
                // 需要至少 N 个确认
                if (currentHeight > 0 && (currentHeight - cash.getBirthHeight() + 1) < confirmations) {
                    log.debug("Cash {} not yet confirmed (birthHeight={}, currentHeight={})", 
                            cash.getCashId(), cash.getBirthHeight(), currentHeight);
                    unconfirmed++;
                    continue;
                }
                
                // 获取渠道信息
                String via = viaMap.get(cash.getCashId());
                
                // 处理充值
                CreditResult result = balanceManager.credit(
                        cash.getIssuer(),
                        cash.getCashId(),
                        cash.getValue(),
                        "block:" + cash.getBirthHeight(),
                        CreditStatus.CONFIRMED,
                        cash.getBirthHeight(),
                        cash.getBlockId(),
                        via
                );
                
                if (result.getCode() == ResultCode.OK) {
                    log.info("Credit processed: cashId={}, user={}, amount={}, via={}", 
                            cash.getCashId(), cash.getIssuer(), cash.getValue(), via);
                    processed++;
                    if (via != null) withVia++;
                    lastProcessedHeight = cash.getBirthHeight();
                } else if (result.getCode() == ResultCode.ALREADY_EXISTS) {
                    log.debug("Credit already exists: cashId={}", cash.getCashId());
                    lastProcessedHeight = cash.getBirthHeight();
                } else {
                    log.warn("Credit failed: cashId={}, code={}", cash.getCashId(), result.getCode());
                }
            }
            
            // 保存最后处理的订单高度
            if (lastProcessedHeight > fromHeight || lastOrderScanHeight == null) {
                balanceManager.setLastOrderScanHeight(lastProcessedHeight);
                log.debug("Saved lastOrderScanHeight: {}", lastProcessedHeight);
            }
            
            if (processed > 0 || skipped > 0 || unconfirmed > 0) {
                log.info("RechargeTask completed: processed={}, withVia={}, skipped={}, unconfirmed={}, total={}", 
                        processed, withVia, skipped, unconfirmed, newCashes.size());
            }
            
        } catch (Exception e) {
            log.error("RechargeTask error", e);
        }
    }
}

