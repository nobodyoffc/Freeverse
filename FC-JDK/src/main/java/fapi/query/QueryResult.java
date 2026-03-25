package fapi.query;

import java.util.List;

/**
 * Result container for FCDSL query execution.
 * Contains the query results along with pagination information.
 * 
 * @param <T> The type of entities returned by the query
 */
public class QueryResult<T> {
    private List<T> data;
    private Long got;
    private Long total;
    private List<String> last;
    
    public QueryResult() {
    }
    
    public QueryResult(List<T> data, Long got, Long total, List<String> last) {
        this.data = data;
        this.got = got;
        this.total = total;
        this.last = last;
    }
    
    /**
     * Create a QueryResult from query data
     */
    public static <T> QueryResult<T> of(List<T> data, Long total, List<String> last) {
        QueryResult<T> result = new QueryResult<>();
        result.data = data;
        result.got = data != null ? (long) data.size() : 0L;
        result.total = total;
        result.last = last;
        return result;
    }
    
    /**
     * Create an empty result
     */
    public static <T> QueryResult<T> empty() {
        return new QueryResult<>(null, 0L, 0L, null);
    }
    
    /**
     * Check if the result is empty
     */
    public boolean isEmpty() {
        return data == null || data.isEmpty();
    }
    
    // Getters and setters
    
    public List<T> getData() {
        return data;
    }
    
    public void setData(List<T> data) {
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
}

