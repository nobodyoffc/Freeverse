package fapi.components.call;

/**
 * The per-{@code ssrc} sliding window of VOICE_SPEC §5: a receiver drops a
 * frame whose {@code seq} it has already seen within the last {@value #SIZE}.
 * Anything older than the window is dropped too, since it cannot be told
 * from a replay. Check it only after the frame authenticates, or a forged
 * frame could advance the window.
 */
public final class ReplayWindow {

    public static final int SIZE = 1024;

    private final long[] bits = new long[SIZE / 64];
    private long highest = -1;

    /** @return true the first time {@code seq} is offered within the window */
    public boolean accept(long seq) {
        if (seq < 0) return false;
        if (seq > highest) {
            long shift = seq - highest;
            if (highest < 0 || shift >= SIZE) {
                java.util.Arrays.fill(bits, 0);
            } else {
                for (long s = highest + 1; s <= seq; s++) clear(s);
            }
            highest = seq;
            set(seq);
            return true;
        }
        if (highest - seq >= SIZE) return false;
        if (isSet(seq)) return false;
        set(seq);
        return true;
    }

    private boolean isSet(long seq) {
        int i = (int) (seq % SIZE);
        return (bits[i >>> 6] & (1L << (i & 63))) != 0;
    }

    private void set(long seq) {
        int i = (int) (seq % SIZE);
        bits[i >>> 6] |= 1L << (i & 63);
    }

    private void clear(long seq) {
        int i = (int) (seq % SIZE);
        bits[i >>> 6] &= ~(1L << (i & 63));
    }
}
