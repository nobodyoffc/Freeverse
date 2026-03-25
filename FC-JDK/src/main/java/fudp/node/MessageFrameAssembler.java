package fudp.node;

import fudp.util.Varint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reassembles complete FUDP messages from chunked stream data.
 * <p>
 * When Protocol.send() splits large data into multiple StreamFrames,
 * the receiver's Stream delivers data incrementally via poll().
 * This assembler buffers incoming chunks and extracts complete messages
 * using the MessageCodec framing format:
 * <pre>
 *   type(1 byte) + messageId(8 bytes) + flags(1 byte) + payloadLength(varint) + payload
 * </pre>
 * <p>
 * Thread-safety: Not thread-safe. Each instance should be used by a single thread
 * or externally synchronized.
 */
public class MessageFrameAssembler {
    
    private static final int FIXED_HEADER_SIZE = 10; // type(1) + messageId(8) + flags(1)
    
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    
    /**
     * Add received data chunk to the assembler buffer.
     *
     * @param data The data chunk from stream.poll()
     */
    public void addData(byte[] data) {
        if (data != null && data.length > 0) {
            buffer.write(data, 0, data.length);
        }
    }
    
    /**
     * Try to extract all complete messages from the buffer.
     *
     * @return A list of complete message byte arrays (may be empty if no complete message yet)
     */
    public List<byte[]> extractMessages() {
        List<byte[]> messages = new ArrayList<>();
        
        while (true) {
            byte[] msg = tryExtractOneMessage();
            if (msg == null) break;
            messages.add(msg);
        }
        
        return messages;
    }
    
    /**
     * Try to extract a single complete message from the buffer.
     *
     * @return The complete message bytes, or null if not enough data yet
     */
    private byte[] tryExtractOneMessage() {
        byte[] accumulated = buffer.toByteArray();
        
        // Need at least the fixed header to determine message structure
        if (accumulated.length < FIXED_HEADER_SIZE) {
            return null;
        }
        
        // Parse the varint payload length starting at offset FIXED_HEADER_SIZE
        try {
            Varint.DecodeResult varintResult = Varint.decode(accumulated, FIXED_HEADER_SIZE);
            int payloadLength = (int) varintResult.value;
            int varintSize = varintResult.bytesConsumed;
            int totalLength = FIXED_HEADER_SIZE + varintSize + payloadLength;
            
            // Check if we have enough data for the complete message
            if (accumulated.length < totalLength) {
                return null;
            }
            
            // Extract the complete message
            byte[] message = new byte[totalLength];
            System.arraycopy(accumulated, 0, message, 0, totalLength);
            
            // Keep the remainder in the buffer
            buffer.reset();
            if (accumulated.length > totalLength) {
                buffer.write(accumulated, totalLength, accumulated.length - totalLength);
            }
            
            return message;
            
        } catch (Exception e) {
            // If varint parsing fails, the data may be corrupted or incomplete.
            // Return null and wait for more data.
            return null;
        }
    }
    
    /**
     * Check if the buffer has any pending data.
     *
     * @return true if there is buffered data waiting for more chunks
     */
    public boolean hasPendingData() {
        return buffer.size() > 0;
    }
    
    /**
     * Get the current buffer size.
     *
     * @return The number of bytes currently buffered
     */
    public int getBufferSize() {
        return buffer.size();
    }
    
    /**
     * Reset the assembler, discarding any buffered data.
     */
    public void reset() {
        buffer.reset();
    }
}
