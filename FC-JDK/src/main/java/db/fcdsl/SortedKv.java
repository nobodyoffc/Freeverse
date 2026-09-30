package db.fcdsl;

import java.io.Closeable;
import java.util.List;

/**
 * The ordered key-value store the engine runs on. Keys are ordered as unsigned bytes and are
 * read forward only, which is all LevelDB 0.12 offers. Kept small so that other stores (an
 * Android LevelDB binding, an in-memory map) can back the engine.
 */
public interface SortedKv {

    /** One write of a batch: a put, or a delete when value is null. */
    final class Op {
        final byte[] key;
        final byte[] value;

        private Op(byte[] key, byte[] value) {
            this.key = key;
            this.value = value;
        }

        public static Op put(byte[] key, byte[] value) { return new Op(key, value); }

        public static Op delete(byte[] key) { return new Op(key, null); }

        public byte[] key() { return key; }

        public byte[] value() { return value; }
    }

    byte[] get(byte[] key);

    /** Apply all ops atomically. */
    void write(List<Op> ops);

    /** A consistent view of the store as it is now. Close it when done. */
    Reader openReader();

    interface Reader extends Closeable {
        byte[] get(byte[] key);

        /** A cursor at the first key at or after {@code key}. */
        Cursor seek(byte[] key);

        @Override
        void close();
    }

    interface Cursor extends Closeable {
        boolean valid();

        byte[] key();

        byte[] value();

        void next();

        @Override
        void close();
    }
}
