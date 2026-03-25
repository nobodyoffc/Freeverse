package fapi.settle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 分成分配器
 * <p>
 * 根据 orderViaShare 和 consumeViaShare 计算分成分配。
 * <p>
 * 分成规则：
 * - orderViaShare: 充值渠道分成比例（万分比）
 * - consumeViaShare: 消费渠道分成比例（万分比）
 * <p>
 * 精度处理：
 * - 内部精度为万分之一聪（1/10000 satoshi）
 * - 累计达到 1 聪后落地
 * - 余数按账号保留供下次累加
 */
public class ShareDistributor {
    private static final Logger log = LoggerFactory.getLogger(ShareDistributor.class);
    
    /** 万分比基数 */
    private static final long BPS_BASE = 10000;
    
    /** 内部精度放大倍数（万分之一聪） */
    private static final long PRECISION_MULTIPLIER = 10000;
    
    /** 最小落地金额（1 聪） */
    private static final long MIN_SETTLEMENT_AMOUNT = 1;
    
    /** 备用金预留（1 FCH = 100,000,000 satoshi） */
    private static final long RESERVE_FUND = 100_000_000L;
    
    private final long orderViaShareBps;
    private final long consumeViaShareBps;
    
    /** 累积余数（按账号累积，key=peerId） */
    private final Map<String, Long> accumulatedRemainders = new HashMap<>();
    
    /**
     * 构造函数
     * 
     * @param orderViaShareBps 充值渠道分成（万分比，0-10000）
     * @param consumeViaShareBps 消费渠道分成（万分比，0-10000）
     */
    public ShareDistributor(long orderViaShareBps, long consumeViaShareBps) {
        if (orderViaShareBps < 0 || orderViaShareBps > BPS_BASE) {
            throw new IllegalArgumentException("orderViaShareBps must be between 0 and 10000");
        }
        if (consumeViaShareBps < 0 || consumeViaShareBps > BPS_BASE) {
            throw new IllegalArgumentException("consumeViaShareBps must be between 0 and 10000");
        }
        this.orderViaShareBps = orderViaShareBps;
        this.consumeViaShareBps = consumeViaShareBps;
    }
    
    /**
     * 计算充值分成
     * <p>
     * 当收到充值时，按 orderViaShare 比例记账给渠道方。
     * 
     * @param amount 充值金额（聪）
     * @param channelId 渠道方ID
     * @return 分成金额（聪），已扣除余数累积
     */
    public long calculateOrderShare(long amount, String channelId) {
        if (orderViaShareBps == 0 || amount <= 0) {
            return 0;
        }
        
        return calculateShare(amount, orderViaShareBps, channelId);
    }
    
    /**
     * 计算消费分成
     * <p>
     * 当发生消费时，按 consumeViaShare 比例记账给渠道方。
     * 
     * @param amount 消费金额（聪）
     * @param channelId 渠道方ID
     * @return 分成金额（聪），已扣除余数累积
     */
    public long calculateConsumeShare(long amount, String channelId) {
        if (consumeViaShareBps == 0 || amount <= 0) {
            return 0;
        }
        
        return calculateShare(amount, consumeViaShareBps, channelId);
    }
    
    /**
     * 计算分成（通用）
     */
    private long calculateShare(long amount, long shareBps, String accountId) {
        // 放大到万分之一聪精度
        long highPrecisionAmount = amount * PRECISION_MULTIPLIER;
        
        // 计算分成（高精度）
        long highPrecisionShare = (highPrecisionAmount * shareBps) / BPS_BASE;
        
        // 加上之前累积的余数
        long accumulated = accumulatedRemainders.getOrDefault(accountId, 0L);
        long totalHighPrecision = highPrecisionShare + accumulated;
        
        // 计算可落地的整数聪
        long wholeSatoshi = totalHighPrecision / PRECISION_MULTIPLIER;
        
        // 计算新的余数
        long newRemainder = totalHighPrecision % PRECISION_MULTIPLIER;
        accumulatedRemainders.put(accountId, newRemainder);
        
        log.debug("Share calculation: amount={}, shareBps={}, share={}, accumulated={}, remainder={}", 
                amount, shareBps, wholeSatoshi, accumulated, newRemainder);
        
        return wholeSatoshi;
    }
    
    /**
     * 周期结算分配
     * <p>
     * 计算周期内的总收入分配：
     * 1. 先扣除备用金
     * 2. 分配充值分成（给渠道方）
     * 3. 分配消费分成（给渠道方）
     * 4. 剩余按股份比例分配利润
     * 
     * @param totalIncome 周期总收入（聪）
     * @param totalSpend 周期总支出（聪）
     * @param channelId 渠道方ID（用于分成）
     * @param stakeholders 股份持有者及其股份（万分比）
     * @return 分配结果
     */
    public DistributionResult distribute(long totalIncome, long totalSpend,
                                          String channelId, Map<String, Long> stakeholders) {
        DistributionResult result = new DistributionResult();
        result.totalIncome = totalIncome;
        result.totalSpend = totalSpend;
        result.netIncome = totalIncome - totalSpend;
        
        // 1. 预留备用金
        long availableForDistribution = Math.max(0, result.netIncome - RESERVE_FUND);
        result.reserveFund = Math.min(RESERVE_FUND, result.netIncome);
        
        if (availableForDistribution <= 0) {
            log.info("No funds available for distribution after reserve");
            return result;
        }
        
        // 2. 计算并分配充值分成
        long orderShare = calculateOrderShare(totalIncome, channelId);
        if (orderShare > 0 && orderShare <= availableForDistribution) {
            result.distributions.put(channelId + ":order", orderShare);
            availableForDistribution -= orderShare;
            result.orderShareTotal = orderShare;
        }
        
        // 3. 计算并分配消费分成
        long consumeShare = calculateConsumeShare(totalSpend, channelId);
        if (consumeShare > 0 && consumeShare <= availableForDistribution) {
            Long existing = result.distributions.get(channelId + ":consume");
            result.distributions.put(channelId + ":consume", 
                    (existing != null ? existing : 0) + consumeShare);
            availableForDistribution -= consumeShare;
            result.consumeShareTotal = consumeShare;
        }
        
        // 4. 剩余按股份比例分配利润
        if (stakeholders != null && !stakeholders.isEmpty() && availableForDistribution > 0) {
            long totalShares = stakeholders.values().stream().mapToLong(Long::longValue).sum();
            
            for (Map.Entry<String, Long> entry : stakeholders.entrySet()) {
                String stakeholderId = entry.getKey();
                long share = entry.getValue();
                
                // 计算利润分配
                long profitShare = (availableForDistribution * share) / totalShares;
                if (profitShare >= MIN_SETTLEMENT_AMOUNT) {
                    result.distributions.put(stakeholderId + ":profit", profitShare);
                    result.profitShareTotal += profitShare;
                }
            }
        }
        
        // 计算总分配
        result.totalDistributed = result.distributions.values().stream()
                .mapToLong(Long::longValue).sum();
        
        log.info("Distribution completed: income={}, spend={}, distributed={}, reserve={}", 
                totalIncome, totalSpend, result.totalDistributed, result.reserveFund);
        
        return result;
    }
    
    /**
     * 获取累积余数
     */
    public long getAccumulatedRemainder(String accountId) {
        return accumulatedRemainders.getOrDefault(accountId, 0L);
    }
    
    /**
     * 清除累积余数
     */
    public void clearAccumulatedRemainder(String accountId) {
        accumulatedRemainders.remove(accountId);
    }
    
    /**
     * 清除所有累积余数
     */
    public void clearAllRemainders() {
        accumulatedRemainders.clear();
    }
    
    /**
     * 获取 orderViaShare（万分比）
     */
    public long getOrderViaShareBps() {
        return orderViaShareBps;
    }
    
    /**
     * 获取 consumeViaShare（万分比）
     */
    public long getConsumeViaShareBps() {
        return consumeViaShareBps;
    }
    
    /**
     * 分配结果
     */
    public static class DistributionResult {
        public long totalIncome;
        public long totalSpend;
        public long netIncome;
        public long reserveFund;
        public long orderShareTotal;
        public long consumeShareTotal;
        public long profitShareTotal;
        public long totalDistributed;
        public Map<String, Long> distributions = new HashMap<>();
        
        @Override
        public String toString() {
            return "DistributionResult{" +
                    "totalIncome=" + totalIncome +
                    ", totalSpend=" + totalSpend +
                    ", netIncome=" + netIncome +
                    ", reserveFund=" + reserveFund +
                    ", orderShare=" + orderShareTotal +
                    ", consumeShare=" + consumeShareTotal +
                    ", profitShare=" + profitShareTotal +
                    ", totalDistributed=" + totalDistributed +
                    ", distributions=" + distributions +
                    '}';
        }
    }
}

