package utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class QRCodeUtils {
    public static final int DEFAULT_SIZE = 400;
    public static void main(String[] args) {
        String text = "Hello, QR Code in Terminal!";
        generateQRCode(text, 100);
        String longText = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";
        generateQRCode(longText, 300);
    }

    public static void generateQRCode(String text) {
        generateQRCode(text, DEFAULT_SIZE);
    }

    public static void generateQRCode(String text, int size) {
        List<String> chunks = splitTextByByteSize(text, size);
        int totalChunks = chunks.size();
        
        for (int i = 0; i < chunks.size(); i++) {
            System.out.println("QR Code " + (i + 1) + " of " + totalChunks + ":");
            generateSingleQRCode(chunks.get(i));
        }
    }

    private static void generateSingleQRCode(String text) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 21, 21);

            // Print QR Code using half-height blocks
            int height = bitMatrix.getHeight();
            int width = bitMatrix.getWidth();

            // Process two rows at a time
            for (int y = 0; y < height - 1; y += 2) {
                for (int x = 0; x < width; x++) {
                    boolean top = bitMatrix.get(x, y);
                    boolean bottom = bitMatrix.get(x, y + 1);

                    if (top && bottom) {
                        System.out.print("█"); // Full block
                    } else if (top) {
                        System.out.print("▀"); // Upper half block
                    } else if (bottom) {
                        System.out.print("▄"); // Lower half block
                    } else {
                        System.out.print(" "); // Space
                    }
                }
                System.out.println();
            }

            // Handle last row if height is odd
            if (height % 2 != 0) {
                for (int x = 0; x < width; x++) {
                    System.out.print(bitMatrix.get(x, height - 1) ? "▀" : " ");
                }
                System.out.println();
            }

        } catch (WriterException e) {
            System.err.println("Error generating QR code: " + e.getMessage());
        }
    }

    private static List<String> splitTextByByteSize(String text, int maxByteSize) {
        List<String> chunks = new ArrayList<>();
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        
        if (textBytes.length <= maxByteSize) {
            chunks.add(text);
            return chunks;
        }

        int startIndex = 0;
        while (startIndex < text.length()) {
            int endIndex = startIndex;
            int byteCount = 0;
            
            while (endIndex < text.length()) {
                int charByteSize = text.substring(endIndex, endIndex + 1)
                    .getBytes(StandardCharsets.UTF_8).length;
                    
                if (byteCount + charByteSize > maxByteSize) {
                    break;
                }
                
                byteCount += charByteSize;
                endIndex++;
            }
            
            chunks.add(text.substring(startIndex, endIndex));
            startIndex = endIndex;
        }
        
        return chunks;
    }
} 