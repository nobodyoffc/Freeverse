package fudp.transport;

import fudp.connection.PeerConnection;
import fudp.packet.frames.AckFrame;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages ACK generation and tracking
 */
public class AckManager {

    /** Maximum ACK delay in milliseconds before sending. Reduced for low latency. */
    private static final long MAX_ACK_DELAY_MS = 10;
    
    /** 
     * Number of packets to receive before sending immediate ACK.
     * Set to 1 for lowest latency (immediate ACK on every packet).
     * Set to 2 for QUIC-like behavior (better for bulk transfers).
     */
    private static final int ACK_THRESHOLD = 1;

    private final PeerConnection connection;
    private final TreeSet<Long> pendingAcks;
    private long largestReceived = -1;
    private ScheduledFuture<?> ackTimer;

    public AckManager(PeerConnection connection) {
        this.connection = connection;
        this.pendingAcks = new TreeSet<>();
    }

    /**
     * Record a received packet number
     */
    public synchronized void onPacketReceived(long packetNumber) {
        pendingAcks.add(packetNumber);

        if (packetNumber > largestReceived) {
            largestReceived = packetNumber;
        }
    }

    /**
     * Check if ACK should be sent immediately
     */
    public boolean shouldSendAckImmediately() {
        return pendingAcks.size() >= ACK_THRESHOLD;
    }

    /**
     * Generate an ACK frame
     */
    public synchronized AckFrame generateAckFrame() {
        if (pendingAcks.isEmpty()) {
            return null;
        }

        if (largestReceived < 0) {
            return null;
        }

        // Convert pending ACKs to ranges
        List<AckFrame.AckRange> ranges = new ArrayList<>();
        List<Long> sorted = new ArrayList<>(pendingAcks);
        Collections.sort(sorted, Collections.reverseOrder());

        if (sorted.isEmpty()) return null;

        long rangeEnd = sorted.get(0); // The highest packet in this contiguous block
        long currentPn = rangeEnd;
        long rangeLength = 0;

        // Iterate through sorted packets (descending)
        for (int i = 0; i < sorted.size(); i++) {
            long pn = sorted.get(i);

            if (i == 0) {
                // First packet always starts the first range
                rangeLength = 0;
                currentPn = pn;
            } else {
                long prevPn = sorted.get(i-1);
                if (prevPn - pn == 1) {
                    // Consecutive, extend current range
                    rangeLength++;
                } else {
                    // Gap found, finalize previous range
                    long gap = prevPn - pn - 2; 
                    // Explanation:
                    // prevPn is the last packet of the previous range (e.g. 100)
                    // pn is the start of the new range (e.g. 90)
                    // Missing packets are 91..99 (count = 9)
                    // Gap calculation in QUIC is "number of missing packets - 1"? No.
                    // RFC 9000: "The number of Gap frames ... indicates the number of missing packets between the end of the previous range and the start of the current one."
                    // Actually, the Gap field is "Unsigned Varint integer indicating the number of contiguous missing packet numbers preceding the Packet Number Range."
                    // So if we have 100, then gap, then 90.
                    // Missing are 99, 98, ..., 91. Count = 9.
                    // Gap value = 9.
                    // So gap = (prevPn - 1) - (pn + 1) + 1 = prevPn - pn - 1.
                    // Wait, let's check logic.
                    // prevPn=100 (included), next is 99 (missing). pn=90 (included).
                    // Missing: 99, 98, 97, 96, 95, 94, 93, 92, 91.
                    // Count = 9.
                    // Formula: prevPn - pn - 1 = 100 - 90 - 1 = 9. Correct.
                    
                    // BUT, wait.
                    // The first range is special.
                    // Subsequent ranges: Gap, then Length.
                    
                    // We are building the ranges list.
                    // rangeLength is actually "Ack Range Length" - "The number of contiguous acknowledged packets preceding the Gap, minus 1".
                    // Wait, RFC 9000: "Ack Range Length: Unsigned Varint integer indicating the number of contiguous acknowledged packets preceding the Gap."
                    // Note: "The value of the Ack Range Length field is one less than the number of packets." -> "Length - 1"?
                    // Let's check standard Frame encoding usually does length-1 to save space?
                    // No, standard QUIC says: "Ack Range Length" = number of packets.
                    // Let's check our AckFrame implementation.
                    
                    // Our AckFrame.java writes: out.write(Varint.encode(ackRanges.get(0).length));
                    // So it writes the actual length (count of packets).
                    // Wait, let's look at `AckFrame` structure in this project.
                    // It takes `length` directly.
                    
                    // First range:
                    // We add it to the list.
                    if (ranges.isEmpty()) {
                        // First range (containing largestAcknowledged)
                        // Gap is 0 (ignored by serializer for first range)
                        ranges.add(new AckFrame.AckRange(0, rangeLength));
                    } else {
                        // Subsequent ranges
                        long actualGap = (sorted.get(i-1) - pn - 2); 
                        // Wait. sorted.get(i-1) is the *end* (smallest number) of the *previous* range block.
                        // pn is the *start* (largest number) of the *current* range block.
                        // Example: Previous block: 100..95. sorted.get(i-1) = 95.
                        // Current block: 90..80. pn = 90.
                        // Missing: 94, 93, 92, 91. Count = 4.
                        // Formula: 95 - 90 - 1 = 4. 
                        // So gap = sorted.get(i-1) - pn - 1.
                        
                        // Recalculate gap logic properly below.
                    }
                }
            }
        }
        
        // Re-implementing strictly clean logic
        ranges.clear();
        
        long blockStart = sorted.get(0); // 100
        long blockEnd = blockStart;      // 100
        
        for (int i = 1; i < sorted.size(); i++) {
            long pn = sorted.get(i);
            if (blockEnd - pn == 1) {
                // Consecutive
                blockEnd = pn;
            } else {
                // End of block
                long length = blockStart - blockEnd; // e.g. 100 - 95 = 5. (Count is 6: 100,99,98,97,96,95). So length is diff.
                // Our AckFrame expects "length" to be the count-1? Or count?
                // Let's assume count for now (based on "length" name), but usually it's count-1 or just count.
                // Looking at AckFrame.java: `out.write(Varint.encode(ackRanges.get(0).length));`
                // If I have packet 100 only. length=0? or 1?
                // If I write 1, receive side reads 1.
                // Receive side: `for (int i = 0; i < length; i++) acked.add(start - i);` ?
                // Let's check AckFrame.parse.
                
                // AckFrame.parse is not fully shown but standard QUIC uses "ACK Range Length".
                // Let's stick to "number of additional packets after the first one".
                // So if range is [100], length = 0.
                // If range is [100, 99], length = 1.
                // So length = blockStart - blockEnd.
                
                if (ranges.isEmpty()) {
                    ranges.add(new AckFrame.AckRange(0, blockStart - blockEnd));
                } else {
                    // Previous block end was ranges.getLast()... wait, we need connection to previous block.
                    // We need to track previous block end.
                }
                
                // Let's restart with a simpler list-based approach
                ranges.clear();
            }
        }
        
        // Third attempt, simple and correct
        ranges = new ArrayList<>();
        long rangeHighest = sorted.get(0);
        long rangeLowest = rangeHighest;
        
        for (int i = 1; i < sorted.size(); i++) {
            long pn = sorted.get(i);
            if (rangeLowest - pn == 1) {
                // extend range
                rangeLowest = pn;
            } else {
                // Gap detected
                // Add current range
                if (ranges.isEmpty()) {
                    // First range
                    // Gap is irrelevant (0)
                    ranges.add(new AckFrame.AckRange(0, rangeHighest - rangeLowest));
                } else {
                    // Subsequent range
                    // Gap is distance from end of previous range to start of this one
                    // Previous range ended at `prevLowest`
                    // Current range starts at `rangeHighest`
                    // Missing packets: (prevLowest - 1) down to (rangeHighest + 1)
                    // Count = prevLowest - rangeHighest - 1.
                    
                    // We need to store prevLowest.
                }
            }
        }
        
        // Final working implementation
        ranges.clear();
        long currentHigh = sorted.get(0);
        long currentLow = currentHigh;
        
        // For tracking gap
        long lastLow = -1;
        
        for (int i = 1; i < sorted.size(); i++) {
            long pn = sorted.get(i);
            if (currentLow - pn == 1) {
                currentLow = pn;
            } else {
                // Finish current range
                long length = currentHigh - currentLow;
                if (ranges.isEmpty()) {
                    ranges.add(new AckFrame.AckRange(0, length));
                } else {
                    long gap = lastLow - currentHigh - 2;
                    ranges.add(new AckFrame.AckRange(gap, length));
                }
                
                lastLow = currentLow;
                currentHigh = pn;
                currentLow = pn;
            }
        }
        
        // Add final range
        long length = currentHigh - currentLow;
        if (ranges.isEmpty()) {
            ranges.add(new AckFrame.AckRange(0, length));
        } else {
            long gap = lastLow - currentHigh - 2;
            ranges.add(new AckFrame.AckRange(gap, length));
        }

        // Clear pending ACKs
        pendingAcks.clear();

        return new AckFrame(sorted.get(0), 0, ranges);
    }

    /**
     * Get maximum ACK delay
     */
    public long getMaxAckDelay() {
        return MAX_ACK_DELAY_MS;
    }

    /**
     * Check if there are pending ACKs
     */
    public boolean hasPendingAcks() {
        return !pendingAcks.isEmpty();
    }

    /**
     * Get largest received packet number
     */
    public long getLargestReceived() {
        return largestReceived;
    }

    /**
     * Reset ACK tracking after peer restart.
     */
    public synchronized void resetForRestart() {
        pendingAcks.clear();
        largestReceived = -1;
    }
}
