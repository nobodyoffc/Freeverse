package fudp.node;

import fudp.congestion.CongestionControl;
import fudp.congestion.RttEstimator;
import fudp.connection.ConnectionState;
import fudp.connection.PeerConnection;

import java.util.*;

/**
 * Aggregated node-level statistics for performance monitoring.
 * Provides RTT, packet loss, throughput, and connection metrics.
 */
public class NodeStats {

    // Connection stats
    private int totalConnections;
    private int establishedConnections;
    private int establishingConnections;
    private int closingConnections;

    // Packet stats (aggregated)
    private long totalPacketsSent;
    private long totalPacketsReceived;
    private long totalBytesOut;
    private long totalBytesIn;

    // Loss stats
    private long totalRetransmits;
    private long totalSuspectedLost;      // 疑似丢失（触发重传）
    private long totalAckedAfterLost;     // 误判（后来收到ACK）
    private long totalEffectiveLost;      // 有效丢失 = 疑似丢失 - 误判
    private double averageLossRate;
    private double averageRetransmitRate;

    // RTT stats (aggregated from all connections)
    private long minRttMs;
    private long maxRttMs;
    private long avgSmoothedRttMs;

    // Congestion control stats
    private long totalCongestionWindow;
    private long totalBytesInFlight;

    // Per-peer detailed stats
    private final List<PeerStats> peerStatsList;

    public NodeStats() {
        this.peerStatsList = new ArrayList<>();
        this.minRttMs = Long.MAX_VALUE;
        this.maxRttMs = 0;
    }

    /**
     * Build stats from all connections.
     */
    public static NodeStats fromConnections(Collection<PeerConnection> connections) {
        NodeStats stats = new NodeStats();

        long totalSmoothedRtt = 0;
        int rttCount = 0;

        for (PeerConnection conn : connections) {
            stats.totalConnections++;

            // Count by state
            switch (conn.getState()) {
                case ESTABLISHED -> stats.establishedConnections++;
                case ESTABLISHING -> stats.establishingConnections++;
                case CLOSING -> stats.closingConnections++;
                case IDLE, CLOSED -> { /* not counted as active */ }
            }

            // Aggregate packet stats
            stats.totalPacketsSent += conn.getPacketsSent();
            stats.totalPacketsReceived += conn.getPacketsReceived();
            stats.totalBytesOut += conn.getBytesOut();
            stats.totalBytesIn += conn.getBytesIn();

            // Aggregate retransmit/loss stats
            stats.totalRetransmits += conn.getRetransmitCount();
            stats.totalSuspectedLost += conn.getSuspectedLostCount();
            stats.totalAckedAfterLost += conn.getAckedAfterSuspectedLost();
            stats.totalEffectiveLost += conn.getLostPacketCount();

            // RTT stats
            RttEstimator rtt = conn.getRttEstimator();
            if (rtt != null) {
                long smoothed = rtt.getSmoothedRtt();
                long minRtt = rtt.getMinRtt();

                if (minRtt < stats.minRttMs) {
                    stats.minRttMs = minRtt;
                }
                if (smoothed > stats.maxRttMs) {
                    stats.maxRttMs = smoothed;
                }
                totalSmoothedRtt += smoothed;
                rttCount++;
            }

            // Congestion control stats
            CongestionControl cc = conn.getCongestionControl();
            if (cc != null) {
                stats.totalCongestionWindow += cc.getCongestionWindow();
                stats.totalBytesInFlight += cc.getBytesInFlight();
            }

            // Build per-peer stats
            stats.peerStatsList.add(PeerStats.from(conn));
        }

        // Calculate averages
        if (rttCount > 0) {
            stats.avgSmoothedRttMs = totalSmoothedRtt / rttCount;
        }

        if (stats.minRttMs == Long.MAX_VALUE) {
            stats.minRttMs = 0;
        }

        // Calculate average loss rate and retransmit rate
        if (stats.totalPacketsSent > 0) {
            stats.averageLossRate = (double) stats.totalEffectiveLost / stats.totalPacketsSent;
            stats.averageRetransmitRate = (double) stats.totalRetransmits / stats.totalPacketsSent;
        }

        return stats;
    }

    // Getters

    public int getTotalConnections() {
        return totalConnections;
    }

    public int getEstablishedConnections() {
        return establishedConnections;
    }

    public int getEstablishingConnections() {
        return establishingConnections;
    }

    public int getClosingConnections() {
        return closingConnections;
    }

    public long getTotalPacketsSent() {
        return totalPacketsSent;
    }

    public long getTotalPacketsReceived() {
        return totalPacketsReceived;
    }

    public long getTotalBytesOut() {
        return totalBytesOut;
    }

    public long getTotalBytesIn() {
        return totalBytesIn;
    }

    public long getTotalRetransmits() {
        return totalRetransmits;
    }

    public long getTotalSuspectedLost() {
        return totalSuspectedLost;
    }

    public long getTotalAckedAfterLost() {
        return totalAckedAfterLost;
    }

    public long getTotalEffectiveLost() {
        return totalEffectiveLost;
    }

    public double getAverageLossRate() {
        return averageLossRate;
    }

    public double getAverageRetransmitRate() {
        return averageRetransmitRate;
    }

    /**
     * Get average loss rate as percentage string.
     */
    public String getLossRatePercent() {
        return String.format("%.2f%%", averageLossRate * 100);
    }

    /**
     * Get average retransmit rate as percentage string.
     */
    public String getRetransmitRatePercent() {
        return String.format("%.2f%%", averageRetransmitRate * 100);
    }

    public long getMinRttMs() {
        return minRttMs;
    }

    public long getMaxRttMs() {
        return maxRttMs;
    }

    public long getAvgSmoothedRttMs() {
        return avgSmoothedRttMs;
    }

    public long getTotalCongestionWindow() {
        return totalCongestionWindow;
    }

    public long getTotalBytesInFlight() {
        return totalBytesInFlight;
    }

    public List<PeerStats> getPeerStatsList() {
        return Collections.unmodifiableList(peerStatsList);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Node Statistics ===\n");
        sb.append(String.format("Connections: %d total (%d established, %d establishing, %d closing)\n",
                totalConnections, establishedConnections, establishingConnections, closingConnections));
        sb.append(String.format("Packets: %d sent, %d received\n", totalPacketsSent, totalPacketsReceived));
        sb.append(String.format("Bytes: %s out, %s in\n", formatBytes(totalBytesOut), formatBytes(totalBytesIn)));
        sb.append(String.format("Retransmits: %d (%s retransmit rate)\n", 
                totalRetransmits, getRetransmitRatePercent()));
        sb.append(String.format("Loss: %d suspected, %d recovered, %d effective lost (%s loss rate)\n",
                totalSuspectedLost, totalAckedAfterLost, totalEffectiveLost, getLossRatePercent()));
        sb.append(String.format("RTT: min=%dms, avg=%dms, max=%dms\n", minRttMs, avgSmoothedRttMs, maxRttMs));
        sb.append(String.format("Congestion: cwnd=%s, in-flight=%s\n",
                formatBytes(totalCongestionWindow), formatBytes(totalBytesInFlight)));
        return sb.toString();
    }

    /**
     * Format bytes to human-readable string.
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Per-peer statistics.
     */
    public static class PeerStats {
        private final String peerId;
        private final ConnectionState state;
        private final long packetsSent;
        private final long packetsReceived;
        private final long bytesOut;
        private final long bytesIn;
        private final long retransmitCount;
        private final double retransmitRate;
        private final long suspectedLostCount;
        private final long ackedAfterSuspectedLost;
        private final long effectiveLostCount;
        private final double lossRate;
        private final long smoothedRttMs;
        private final long minRttMs;
        private final long rttVarianceMs;
        private final long rtoMs;
        private final long congestionWindow;
        private final long bytesInFlight;
        private final CongestionControl.State ccState;

        private PeerStats(PeerConnection conn) {
            this.peerId = conn.getPeerId();
            this.state = conn.getState();
            this.packetsSent = conn.getPacketsSent();
            this.packetsReceived = conn.getPacketsReceived();
            this.bytesOut = conn.getBytesOut();
            this.bytesIn = conn.getBytesIn();
            this.retransmitCount = conn.getRetransmitCount();
            this.retransmitRate = conn.getRetransmitRate();
            this.suspectedLostCount = conn.getSuspectedLostCount();
            this.ackedAfterSuspectedLost = conn.getAckedAfterSuspectedLost();
            this.effectiveLostCount = conn.getLostPacketCount();
            this.lossRate = conn.getLossRate();

            RttEstimator rtt = conn.getRttEstimator();
            if (rtt != null) {
                this.smoothedRttMs = rtt.getSmoothedRtt();
                this.minRttMs = rtt.getMinRtt();
                this.rttVarianceMs = rtt.getRttVariance();
                this.rtoMs = rtt.getRto();
            } else {
                this.smoothedRttMs = 0;
                this.minRttMs = 0;
                this.rttVarianceMs = 0;
                this.rtoMs = 0;
            }

            CongestionControl cc = conn.getCongestionControl();
            if (cc != null) {
                this.congestionWindow = cc.getCongestionWindow();
                this.bytesInFlight = cc.getBytesInFlight();
                this.ccState = cc.getState();
            } else {
                this.congestionWindow = 0;
                this.bytesInFlight = 0;
                this.ccState = null;
            }
        }

        public static PeerStats from(PeerConnection conn) {
            return new PeerStats(conn);
        }

        // Getters
        public String getPeerId() { return peerId; }
        public ConnectionState getState() { return state; }
        public long getPacketsSent() { return packetsSent; }
        public long getPacketsReceived() { return packetsReceived; }
        public long getBytesOut() { return bytesOut; }
        public long getBytesIn() { return bytesIn; }
        public long getRetransmitCount() { return retransmitCount; }
        public double getRetransmitRate() { return retransmitRate; }
        public String getRetransmitRatePercent() { return String.format("%.2f%%", retransmitRate * 100); }
        public long getSuspectedLostCount() { return suspectedLostCount; }
        public long getAckedAfterSuspectedLost() { return ackedAfterSuspectedLost; }
        public long getLostPacketCount() { return effectiveLostCount; }
        public double getLossRate() { return lossRate; }
        public String getLossRatePercent() { return String.format("%.2f%%", lossRate * 100); }
        public long getSmoothedRttMs() { return smoothedRttMs; }
        public long getMinRttMs() { return minRttMs; }
        public long getRttVarianceMs() { return rttVarianceMs; }
        public long getRtoMs() { return rtoMs; }
        public long getCongestionWindow() { return congestionWindow; }
        public long getBytesInFlight() { return bytesInFlight; }
        public CongestionControl.State getCcState() { return ccState; }

        @Override
        public String toString() {
            return String.format("Peer[%s] state=%s, sent=%d, recv=%d, retx=%s, loss=%s, rtt=%dms(min=%dms), cc=%s",
                    peerId, state,
                    packetsSent, packetsReceived, getRetransmitRatePercent(), getLossRatePercent(),
                    smoothedRttMs, minRttMs, ccState);
        }
    }
}

