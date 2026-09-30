package fapi.components.dock;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import data.fcData.DockItem;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Every DOCK item of an Elasticsearch index, page by page, for the one-time migration.
 * Uses the scroll API, which needs no sortable field. A missing index has no pages.
 * Failures surface as runtime exceptions from {@link #hasNext()} / {@link #next()}.
 */
public final class EsDockPages implements Iterator<List<DockItem>> {

    private static final int PAGE = 500;
    private static final String KEEP_ALIVE = "5m";

    private final ElasticsearchClient es;
    private final String index;
    private String scrollId;
    private List<DockItem> pending;
    private boolean started;
    private boolean done;

    public EsDockPages(ElasticsearchClient es, String index) {
        this.es = es;
        this.index = index;
    }

    @Override
    public boolean hasNext() {
        if (pending == null && !done) fetch();
        return pending != null;
    }

    @Override
    public List<DockItem> next() {
        if (!hasNext()) throw new NoSuchElementException();
        List<DockItem> page = pending;
        pending = null;
        return page;
    }

    private void fetch() {
        try {
            ResponseBody<DockItem> r;
            if (!started) {
                started = true;
                if (!es.indices().exists(e -> e.index(index)).value()) {
                    done = true;
                    return;
                }
                SearchResponse<DockItem> first = es.search(s -> s
                        .index(index)
                        .size(PAGE)
                        .scroll(t -> t.time(KEEP_ALIVE))
                        .sort(o -> o.doc(d -> d.order(SortOrder.Asc))), DockItem.class);
                r = first;
            } else {
                ScrollResponse<DockItem> more = es.scroll(s -> s.scrollId(scrollId).scroll(t -> t.time(KEEP_ALIVE)),
                        DockItem.class);
                r = more;
            }
            scrollId = r.scrollId();
            List<DockItem> page = new ArrayList<>();
            for (Hit<DockItem> h : r.hits().hits()) {
                DockItem item = h.source();
                if (item == null) continue;
                if (item.getId() == null || item.getId().isEmpty()) item.setId(h.id());
                page.add(item);
            }
            if (page.isEmpty()) {
                finish();
            } else {
                pending = page;
            }
        } catch (Exception e) {
            finish();
            throw new IllegalStateException("Reading DOCK index " + index + " failed: " + e.getMessage(), e);
        }
    }

    private void finish() {
        done = true;
        if (scrollId != null) {
            String id = scrollId;
            scrollId = null;
            try {
                es.clearScroll(c -> c.scrollId(id));
            } catch (Exception ignored) {
                // the scroll expires on its own
            }
        }
    }
}
