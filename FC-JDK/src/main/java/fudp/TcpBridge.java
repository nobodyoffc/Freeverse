package fudp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * FUDP over TCP (FUDP8): the same packets, each prefixed with its length as a
 * 2-byte big-endian number, on a TCP stream. For networks that let UDP out
 * but drop what comes back, which some mobile networks do for UDP from
 * abroad; TCP, and TCP 443 above all, gets through where UDP does not.
 * <p>
 * The bridge stands beside the UDP socket in {@link fudp.Protocol}: a peer
 * reached over TCP is known by an address like any other, and the protocol
 * sends to it and hears from it as it would over UDP. On a server, each
 * accepted connection's remote address is its peer's address. On a client,
 * a connection stands in for the UDP address the protocol already knows the
 * server by, so nothing above the socket changes.
 * <p>
 * Each connection has a writer thread and a bounded queue: a send never
 * blocks the protocol, and a connection that cannot keep up drops, as UDP
 * would. Received packets go to {@code inbound}, which queues them for the
 * protocol's one receive thread.
 */
public final class TcpBridge {

    /** Packets queued per connection before sends drop. */
    static final int QUEUE = 1024;
    static final int MAX_PACKET = 65535;
    static final int CONNECT_TIMEOUT_MS = 5_000;
    /** A server's limits (FUDP8 §7): connections per source address, and in all. */
    static final int PER_ADDRESS = 16, TOTAL = 4096;
    /** A connection that carries nothing for this long is closed. */
    static final int IDLE_MS = 5 * 60_000;

    private final BiConsumer<byte[], SocketAddress> inbound;
    private final Consumer<String> log;
    private final Map<SocketAddress, Link> links = new ConcurrentHashMap<>();
    private volatile ServerSocket server;
    private volatile boolean closed;

    /**
     * @param inbound gets each packet and the address it is from, on the
     *                connection's reader thread; it must hand it on quickly
     */
    public TcpBridge(BiConsumer<byte[], SocketAddress> inbound, Consumer<String> log) {
        this.inbound = inbound;
        this.log = log;
    }

    /** Server: accept FUDP over TCP on {@code port}, beside UDP. */
    public void listen(int port) throws IOException {
        ServerSocket s = new ServerSocket(port);
        server = s;
        Thread t = new Thread(() -> {
            while (!closed) {
                try {
                    Socket socket = s.accept();
                    if (!admit(socket)) {
                        socket.close();
                        continue;
                    }
                    socket.setTcpNoDelay(true);
                    socket.setSoTimeout(IDLE_MS);
                    SocketAddress from = socket.getRemoteSocketAddress();
                    start(from, socket);
                } catch (IOException e) {
                    if (!closed) log.accept("tcp accept: " + e.getMessage());
                }
            }
        }, "fudp-tcp-accept-" + port);
        t.setDaemon(true);
        t.start();
        log.accept("FUDP over TCP on port " + port);
    }

    /**
     * Client: reach the server at {@code host:port} over TCP, standing in
     * for {@code as}, the UDP address the protocol knows it by.
     */
    public void connect(String host, int port, SocketAddress as) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(IDLE_MS);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
        Link old = links.get(as);
        if (old != null) old.close();
        start(as, socket);
        log.accept("FUDP over TCP to " + host + ":" + port + " for " + as);
    }

    /** Within the limits: not too many connections from one address, nor in all. */
    private boolean admit(Socket socket) {
        if (links.size() >= TOTAL) return false;
        java.net.InetAddress ip = socket.getInetAddress();
        int same = 0;
        for (SocketAddress a : links.keySet()) {
            if (a instanceof InetSocketAddress i && ip.equals(i.getAddress())) same++;
        }
        return same < PER_ADDRESS;
    }

    /** Whether packets to {@code to} go over TCP. */
    public boolean owns(SocketAddress to) {
        return to != null && links.containsKey(to);
    }

    /** @return false if {@code to} has no connection, or its queue is full */
    public boolean send(SocketAddress to, byte[] packet) {
        Link l = to == null ? null : links.get(to);
        return l != null && packet.length <= MAX_PACKET && l.queue.offer(packet);
    }

    /** Close the connection standing in for {@code as}, if any. */
    public void disconnect(SocketAddress as) {
        Link l = links.remove(as);
        if (l != null) l.close();
    }

    public void close() {
        closed = true;
        ServerSocket s = server;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // closing anyway
            }
        }
        for (Link l : links.values()) l.close();
        links.clear();
    }

    private void start(SocketAddress as, Socket socket) throws IOException {
        Link l = new Link(as, socket);
        links.put(as, l);
        l.begin();
    }

    private final class Link {
        final SocketAddress as;
        final Socket socket;
        final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(QUEUE);
        volatile boolean down;

        Link(SocketAddress as, Socket socket) {
            this.as = as;
            this.socket = socket;
        }

        void begin() throws IOException {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            Thread reader = new Thread(() -> read(new DataInputStream(in)), "fudp-tcp-in");
            Thread writer = new Thread(() -> write(new DataOutputStream(out)), "fudp-tcp-out");
            reader.setDaemon(true);
            writer.setDaemon(true);
            reader.start();
            writer.start();
        }

        private void read(DataInputStream in) {
            try {
                while (!down) {
                    int n = in.readUnsignedShort();
                    byte[] packet = new byte[n];
                    in.readFully(packet);
                    inbound.accept(packet, as);
                }
            } catch (IOException e) {
                // the stream ended, or carried nothing for IDLE_MS
            } finally {
                close();
            }
        }

        private void write(DataOutputStream out) {
            try {
                while (!down) {
                    byte[] p = queue.poll(500, TimeUnit.MILLISECONDS);
                    if (p == null) continue;
                    out.writeShort(p.length);
                    out.write(p);
                    // Everything already queued goes out in the same flush.
                    while ((p = queue.poll()) != null) {
                        out.writeShort(p.length);
                        out.write(p);
                    }
                    out.flush();
                }
            } catch (IOException | InterruptedException e) {
                // the stream ended
            } finally {
                close();
            }
        }

        void close() {
            if (down) return;
            down = true;
            links.remove(as, this);
            try {
                socket.close();
            } catch (IOException ignored) {
                // closing anyway
            }
        }
    }
}
