package fudp;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Helpers for {@link InetSocketAddress} where {@link InetSocketAddress#getAddress()} may be null
 * (unresolved hostname). Resolves via {@link InetAddress#getByName(String)} when needed.
 */
public final class InetSocketAddressUtil {

    private InetSocketAddressUtil() {
    }

    /**
     * @return numeric host string for verifier / matching, or null if resolution fails
     */
    public static String resolveHostAddress(InetSocketAddress inet) {
        if (inet == null) {
            return null;
        }
        InetAddress addr = inet.getAddress();
        if (addr != null) {
            return addr.getHostAddress();
        }
        try {
            return InetAddress.getByName(inet.getHostString()).getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Host part of a pending-request key ending with ":port" (handles {@code host:port},
     * {@code /ip:port}, {@code host/ip:port}, {@code [ipv6]:port}).
     */
    public static String hostPartFromSocketKey(String key, int port) {
        String suffix = ":" + port;
        if (key == null || !key.endsWith(suffix)) {
            return null;
        }
        String h = key.substring(0, key.length() - suffix.length());
        if (h.startsWith("/")) {
            h = h.substring(1);
        }
        if (h.startsWith("[")) {
            int close = h.indexOf(']');
            return close > 1 ? h.substring(1, close) : null;
        }
        int slash = h.indexOf('/');
        if (slash > 0) {
            return h.substring(0, slash);
        }
        return h;
    }
}
