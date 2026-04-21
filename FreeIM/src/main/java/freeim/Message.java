package freeim;

/**
 * Simple chat message data model.
 */
public class Message {
    public String id;           // hex messageId from FUDP
    public String peerId;       // sender (if incoming) or recipient (if outgoing)
    public String text;         // message content
    public long timestamp;      // millis since epoch
    public boolean incoming;    // true = received, false = sent
    public boolean acked;       // delivery confirmed by peer

    public Message() {}

    public Message(String id, String peerId, String text, long timestamp, boolean incoming) {
        this.id = id;
        this.peerId = peerId;
        this.text = text;
        this.timestamp = timestamp;
        this.incoming = incoming;
        this.acked = false;
    }

    public String formatTime() {
        long hours = (timestamp / 3600000) % 24;
        long minutes = (timestamp / 60000) % 60;
        return String.format("%02d:%02d", hours, minutes);
    }

    public String format(String localFid) {
        String dir;
        if (incoming) {
            dir = peerId.length() > 4 ? peerId.substring(peerId.length() - 4) : peerId;
        } else {
            String suffix = localFid != null && localFid.length() > 4
                ? localFid.substring(localFid.length() - 4) : "";
            dir = "me(" + suffix + ")";
        }
        String ackMark = (!incoming && acked) ? " \u2713\u2713" : "";
        return String.format("[%s] %s: %s%s", formatTime(), dir, text, ackMark);
    }

    @Override
    public String toString() {
        return format(null);
    }
}
