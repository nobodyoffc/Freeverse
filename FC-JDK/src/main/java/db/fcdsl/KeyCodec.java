package db.fcdsl;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Order-preserving encoding of field values in keys: comparing two encoded keys as unsigned
 * bytes gives the same order as comparing the values.
 * <p>
 * Each component starts with a tag: {@code 0x01} for a value, {@code 0x02} for a missing one,
 * so missing values sort last. A descending component has its value bytes complemented (not its
 * tag, so missing values stay last). LevelDB 0.12 can only iterate forward, so this is how a
 * descending sort is read from an index.
 * <ul>
 *   <li>LONG: 8 bytes big-endian with the sign bit flipped.</li>
 *   <li>DOUBLE: 8 bytes of IEEE bits, transformed so byte order is numeric order.</li>
 *   <li>BOOLEAN: one byte, 0 or 1.</li>
 *   <li>KEYWORD / TEXT: UTF-8, with {@code 0x00} escaped as {@code 0x00 0xFF} and ended by
 *       {@code 0x00 0x01}. A prefix therefore sorts before the longer string, and components
 *       can be read back one by one.</li>
 * </ul>
 */
final class KeyCodec {

    static final byte PRESENT = 0x01;
    static final byte MISSING = 0x02;

    private KeyCodec() {}

    static void encode(ByteArrayOutputStream out, FieldType type, Object value, boolean desc) {
        if (value == null) {
            out.write(MISSING);
            return;
        }
        out.write(PRESENT);
        byte[] body = body(type, value);
        if (desc) for (int i = 0; i < body.length; i++) body[i] = (byte) ~body[i];
        out.write(body, 0, body.length);
    }

    static byte[] encode(FieldType type, Object value, boolean desc) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        encode(out, type, value, desc);
        return out.toByteArray();
    }

    private static byte[] body(FieldType type, Object value) {
        switch (type) {
            case LONG:
                return longBytes(((Number) value).longValue() ^ Long.MIN_VALUE);
            case DOUBLE: {
                double d = ((Number) value).doubleValue();
                if (d == 0.0) d = 0.0; // fold -0.0 into 0.0
                long bits = Double.doubleToLongBits(d);
                bits = bits < 0 ? ~bits : bits ^ Long.MIN_VALUE;
                return longBytes(bits);
            }
            case BOOLEAN:
                return new byte[]{(byte) (((Boolean) value) ? 1 : 0)};
            default: {
                byte[] utf8 = value.toString().getBytes(StandardCharsets.UTF_8);
                ByteArrayOutputStream b = new ByteArrayOutputStream(utf8.length + 2);
                for (byte x : utf8) {
                    b.write(x);
                    if (x == 0) b.write(0xFF);
                }
                b.write(0x00);
                b.write(0x01);
                return b.toByteArray();
            }
        }
    }

    private static byte[] longBytes(long v) {
        byte[] b = new byte[8];
        for (int i = 7; i >= 0; i--) {
            b[i] = (byte) v;
            v >>>= 8;
        }
        return b;
    }

    /** Reads components back from a key, left to right. */
    static final class Reader {
        private final byte[] key;
        private int pos;

        Reader(byte[] key, int pos) {
            this.key = key;
            this.pos = pos;
        }

        int position() { return pos; }

        Object read(FieldType type, boolean desc) {
            byte tag = key[pos++];
            if (tag == MISSING) return null;
            switch (type) {
                case LONG:
                    return readLong(desc) ^ Long.MIN_VALUE;
                case DOUBLE: {
                    long bits = readLong(desc);
                    bits = bits < 0 ? bits ^ Long.MIN_VALUE : ~bits;
                    return Double.longBitsToDouble(bits);
                }
                case BOOLEAN:
                    return (byte) (desc ? ~key[pos++] : key[pos++]) != 0;
                default: {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    while (true) {
                        byte x = get(desc);
                        if (x == 0) {
                            byte next = get(desc);
                            if (next == 0x01) break;
                            b.write(0); // escaped 0x00 0xFF
                        } else {
                            b.write(x);
                        }
                    }
                    return b.toString(StandardCharsets.UTF_8);
                }
            }
        }

        private byte get(boolean desc) {
            byte x = key[pos++];
            return desc ? (byte) ~x : x;
        }

        private long readLong(boolean desc) {
            long v = 0;
            for (int i = 0; i < 8; i++) v = (v << 8) | (get(desc) & 0xFF);
            return v;
        }
    }

    static int compareUnsigned(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int c = (a[i] & 0xFF) - (b[i] & 0xFF);
            if (c != 0) return c;
        }
        return a.length - b.length;
    }

    static boolean startsWith(byte[] key, byte[] prefix) {
        if (key.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (key[i] != prefix[i]) return false;
        return true;
    }

    static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int o = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, o, p.length);
            o += p.length;
        }
        return out;
    }
}
