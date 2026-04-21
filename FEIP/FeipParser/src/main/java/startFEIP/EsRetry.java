package startFEIP;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Retry wrapper for Elasticsearch operations with exponential backoff.
 */
public class EsRetry {

    private static final Logger log = LoggerFactory.getLogger(EsRetry.class);
    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 1000;

    private EsRetry() {}

    /**
     * Execute a bulk request with retry on transient failures.
     */
    public static BulkResponse bulkWithRetry(ElasticsearchClient esClient, BulkRequest request) throws IOException {
        return executeWithRetry(() -> esClient.bulk(request));
    }

    /**
     * Generic retry wrapper for any ES operation that may throw IOException.
     * Retries up to MAX_RETRIES times with exponential backoff (1s, 2s, 4s).
     */
    public static <T> T executeWithRetry(EsOperation<T> operation) throws IOException {
        IOException lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return operation.execute();
            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    long delay = BASE_DELAY_MS * (1L << attempt);
                    log.warn("ES operation failed (attempt {}/{}), retrying in {}ms: {}",
                            attempt + 1, MAX_RETRIES + 1, delay, e.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Retry interrupted", ie);
                    }
                }
            }
        }
        log.error("ES operation failed after {} attempts", MAX_RETRIES + 1, lastException);
        throw lastException;
    }

    @FunctionalInterface
    public interface EsOperation<T> {
        T execute() throws IOException;
    }
}
