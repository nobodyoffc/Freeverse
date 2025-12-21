package fudp.metrics;

/**
 * Listener for transport metering events. Implemented by upper layers (e.g., FAPI economics).
 */
public interface MeterListener {
    void onMeter(MeterRecord record);
}
