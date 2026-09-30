package db.fcdsl;

import java.util.List;

/**
 * One page of a query.
 * <p>
 * The page is shorter than the requested size only when no more items match: clients stop
 * paging on a short page. {@code last} is the cursor of the last item (null on an empty page),
 * in the form ES returned sort values, so cursors saved before a migration still work.
 * {@code total} is the count of all matching items when it was cheap to know, else null.
 */
public final class FcdslResult<T> {
    private final List<T> items;
    private final List<String> last;
    private final Long total;
    private final long examined;

    FcdslResult(List<T> items, List<String> last, Long total, long examined) {
        this.items = items;
        this.last = last;
        this.total = total;
        this.examined = examined;
    }

    public List<T> getItems() { return items; }

    public List<String> getLast() { return last; }

    public Long getTotal() { return total; }

    /** How many stored entries the query read. For tests and logs. */
    public long getExamined() { return examined; }
}
