package fapi.components.call;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * The byte encodings of VOICE_SPEC §4. A quoted literal such as
 * {@code "FreerCall v1 p2p"} is its UTF-8 bytes with no prefix; {@code str(x)}
 * is a 2-byte big-endian length then UTF-8; integers are big-endian.
 */
final class CallBytes {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    static CallBytes of(String literal) {
        return new CallBytes().literal(literal);
    }

    CallBytes literal(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.write(b, 0, b.length);
        return this;
    }

    CallBytes str(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        if (b.length > 0xFFFF) throw new IllegalArgumentException("string too long for str(): " + b.length);
        out.write(b.length >>> 8);
        out.write(b.length);
        out.write(b, 0, b.length);
        return this;
    }

    CallBytes bytes(byte[] b) {
        out.write(b, 0, b.length);
        return this;
    }

    CallBytes u8(int v) {
        out.write(v);
        return this;
    }

    CallBytes u32(long v) {
        for (int i = 3; i >= 0; i--) out.write((int) (v >>> (8 * i)));
        return this;
    }

    CallBytes u64(long v) {
        for (int i = 7; i >= 0; i--) out.write((int) (v >>> (8 * i)));
        return this;
    }

    byte[] toBytes() {
        return out.toByteArray();
    }
}
