package fapi.migrate;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import data.fcData.FcObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Every document of an Elasticsearch index, page by page, for a one-time migration off ES.
 * Uses the scroll API, which needs no sortable field. A missing index has no pages. A document
 * without an id in its source takes the ES document id.
 * Failures surface as runtime exceptions from {@link #hasNext()} / {@link #next()}.
 */
public final class EsIndexPages<T extends FcObject> implements Iterator<List<T>> {

    private static final int PAGE = 500;
    private static final String KEEP_ALIVE = "5m";

    private final ElasticsearchClient es;
    private final String index;
    private final Class<T> type;
    private String scrollId;
    private List<T> pending;
    private boolean started;
    private boolean done;

    public EsIndexPages(ElasticsearchClient es, String index, Class<T> type) {
        this.es = es;
        this.index = index;
        this.type = type;
    }

    @Override
    public boolean hasNext() {
        if (pending == null && !done) fetch();
        return pending != null;
    }

    @Override
    public List<T> next() {
        if (!hasNext()) throw new NoSuchElementException();
        List<T> page = pending;
        pending = null;
        return page;
    }

    private void fetch() {
        try {
            ResponseBody<T> r;
            if (!started) {
                started = true;
                if (!es.indices().exists(e -> e.index(index)).value()) {
                    done = true;
                    return;
                }
                SearchResponse<T> first = es.search(s -> s
                        .index(index)
                        .size(PAGE)
                        .scroll(t -> t.time(KEEP_ALIVE))
                        .sort(o -> o.doc(d -> d.order(SortOrder.Asc))), type);
                r = first;
            } else {
                ScrollResponse<T> more = es.scroll(s -> s.scrollId(scrollId).scroll(t -> t.time(KEEP_ALIVE)),
                        type);
                r = more;
            }
            scrollId = r.scrollId();
            List<T> page = new ArrayList<>();
            for (Hit<T> h : r.hits().hits()) {
                T item = h.source();
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
            throw new IllegalStateException("Reading index " + index + " failed: " + e.getMessage(), e);
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
