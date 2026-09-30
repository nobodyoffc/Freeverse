package db.fcdsl;

import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.ReadOptions;
import org.iq80.leveldb.Snapshot;
import org.iq80.leveldb.WriteBatch;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * {@link SortedKv} over an open iq80 LevelDB. The DB is owned by the caller, who may share it
 * with other data: collections keep to their own key prefix.
 */
public final class LevelDbKv implements SortedKv {

    private final DB db;

    public LevelDbKv(DB db) {
        this.db = db;
    }

    @Override
    public byte[] get(byte[] key) {
        return db.get(key);
    }

    @Override
    public void write(List<Op> ops) {
        if (ops.isEmpty()) return;
        try (WriteBatch batch = db.createWriteBatch()) {
            for (Op op : ops) {
                if (op.value() == null) batch.delete(op.key());
                else batch.put(op.key(), op.value());
            }
            db.write(batch);
        } catch (IOException e) {
            throw new IllegalStateException("LevelDB batch write failed", e);
        }
    }

    @Override
    public Reader openReader() {
        Snapshot snapshot = db.getSnapshot();
        ReadOptions options = new ReadOptions().snapshot(snapshot);
        return new Reader() {
            @Override
            public byte[] get(byte[] key) {
                return db.get(key, options);
            }

            @Override
            public Cursor seek(byte[] key) {
                DBIterator it = db.iterator(options);
                it.seek(key);
                return new IteratorCursor(it);
            }

            @Override
            public void close() {
                try {
                    snapshot.close();
                } catch (IOException ignored) {
                    // a snapshot close only releases memory
                }
            }
        };
    }

    private static final class IteratorCursor implements Cursor {
        private final DBIterator it;
        private Map.Entry<byte[], byte[]> current;

        IteratorCursor(DBIterator it) {
            this.it = it;
            advance();
        }

        private void advance() {
            current = it.hasNext() ? it.next() : null;
        }

        @Override
        public boolean valid() { return current != null; }

        @Override
        public byte[] key() { return current.getKey(); }

        @Override
        public byte[] value() { return current.getValue(); }

        @Override
        public void next() { advance(); }

        @Override
        public void close() {
            try {
                it.close();
            } catch (IOException ignored) {
                // nothing to recover
            }
        }
    }
}
