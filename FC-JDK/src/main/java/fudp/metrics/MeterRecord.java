package fudp.metrics;

import fudp.message.MessageType;

/**
 * Immutable metering record emitted by the transport layer.
 * Only contains transport facts; upper layers decide on economics or policy.
 */
public class MeterRecord {
    private final String peerId;
    private final Long streamId;
    private final MessageType messageType;
    private final MeterDirection direction;
    private final long payloadBytes;
    private final long sendTimestampMillis;
    private final long receiveTimestampMillis;
    private final Long rttMicros;
    private final int retransmitCount;
    private final Double lossRateHint;

    private MeterRecord(Builder builder) {
        this.peerId = builder.peerId;
        this.streamId = builder.streamId;
        this.messageType = builder.messageType;
        this.direction = builder.direction;
        this.payloadBytes = builder.payloadBytes;
        this.sendTimestampMillis = builder.sendTimestampMillis;
        this.receiveTimestampMillis = builder.receiveTimestampMillis;
        this.rttMicros = builder.rttMicros;
        this.retransmitCount = builder.retransmitCount;
        this.lossRateHint = builder.lossRateHint;
    }

    public String getPeerId() {
        return peerId;
    }

    public Long getStreamId() {
        return streamId;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public MeterDirection getDirection() {
        return direction;
    }

    public long getPayloadBytes() {
        return payloadBytes;
    }

    public long getSendTimestampMillis() {
        return sendTimestampMillis;
    }

    public long getReceiveTimestampMillis() {
        return receiveTimestampMillis;
    }

    public Long getRttMicros() {
        return rttMicros;
    }

    public int getRetransmitCount() {
        return retransmitCount;
    }

    public Double getLossRateHint() {
        return lossRateHint;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String peerId;
        private Long streamId;
        private MessageType messageType;
        private MeterDirection direction;
        private long payloadBytes;
        private long sendTimestampMillis;
        private long receiveTimestampMillis;
        private Long rttMicros;
        private int retransmitCount;
        private Double lossRateHint;

        public Builder peerId(String peerId) {
            this.peerId = peerId;
            return this;
        }

        public Builder streamId(Long streamId) {
            this.streamId = streamId;
            return this;
        }

        public Builder messageType(MessageType messageType) {
            this.messageType = messageType;
            return this;
        }

        public Builder direction(MeterDirection direction) {
            this.direction = direction;
            return this;
        }

        public Builder payloadBytes(long payloadBytes) {
            this.payloadBytes = payloadBytes;
            return this;
        }

        public Builder sendTimestampMillis(long sendTimestampMillis) {
            this.sendTimestampMillis = sendTimestampMillis;
            return this;
        }

        public Builder receiveTimestampMillis(long receiveTimestampMillis) {
            this.receiveTimestampMillis = receiveTimestampMillis;
            return this;
        }

        public Builder rttMicros(Long rttMicros) {
            this.rttMicros = rttMicros;
            return this;
        }

        public Builder retransmitCount(int retransmitCount) {
            this.retransmitCount = retransmitCount;
            return this;
        }

        public Builder lossRateHint(Double lossRateHint) {
            this.lossRateHint = lossRateHint;
            return this;
        }

        public MeterRecord build() {
            return new MeterRecord(this);
        }
    }
}
