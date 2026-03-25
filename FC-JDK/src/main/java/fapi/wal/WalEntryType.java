package fapi.wal;

/**
 * WAL 条目类型枚举
 */
public enum WalEntryType {
    /** 余额更新 */
    BALANCE_UPDATE,
    
    /** 充值记录 */
    CREDIT,
    
    /** 扣费记录 */
    CHARGE,
    
    /** 结算记录 */
    SETTLE,
    
    /** 调整记录（回滚等） */
    ADJUST,
    
    /** 元数据更新 */
    META_UPDATE,
    
    /** 快照标记 */
    SNAPSHOT_MARKER,
    
    /** 检查点（用于截断） */
    CHECKPOINT,
    
    /** 渠道余额更新 */
    CHANNEL_UPDATE
}

