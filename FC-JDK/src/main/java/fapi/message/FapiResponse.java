package fapi.message;

import data.fcData.FcObject;
import java.util.List;

/**
 * FAPI 响应包装类
 * 与 ReplyBody 格式兼容，用于 FUDP 响应
 */
public class FapiResponse extends FcObject {
    private Integer code;                 // 状态码（对应 CodeMessage）
    private String message;               // 错误消息
    private Object data;                  // 查询结果数据
    private Long got;                     // 返回数量
    private Long total;                   // 总数量
    private List<String> last;           // 分页游标
    private Long bestHeight;             // 最佳区块高度
    private Long balance;                // 权威余额（单位聪，可能为 null）
    private Long balanceSeq;             // 余额序列号（可选，可能为 null）

    public FapiResponse() {
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
}
