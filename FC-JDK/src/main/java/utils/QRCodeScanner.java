package utils;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A utility class for scanning QR codes using a camera.
 * This class uses the Webcam Capture API to access the camera
 * and ZXing for QR code detection and decoding.
 */
public class QRCodeScanner {
    private static final Map<DecodeHintType, Object> HINTS = new HashMap<>();
    private static final MultiFormatReader READER = new MultiFormatReader();
    
    private Object webcam; // Using Object instead of Webcam to avoid direct dependency
    private boolean running = false;
    private ScheduledExecutorService executor;
    private QRCodeCallback callback;
    private boolean webcamLibraryAvailable = false;
    private Class<?> webcamClass;
    
    static {
        HINTS.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        HINTS.put(DecodeHintType.POSSIBLE_FORMATS, BarcodeFormat.QR_CODE);
        
        // Set system properties for webcam-capture's native libraries
        String osName = System.getProperty("os.name").toLowerCase();
        System.out.println("Detected OS: " + osName);
        
        // Set BridJ library path explicitly for different OS types
        if (osName.contains("mac")) {
            System.setProperty("bridj.library.path", System.getProperty("user.home") + "/.bridj/lib");
            // On Mac sometimes we need this to avoid "No webcam found" errors
            System.setProperty("webcam.debug", "true");
            System.setProperty("bridj.debug", "true");
        } else if (osName.contains("linux")) {
            // Optional: Set specific properties for Linux if needed
            System.setProperty("webcam.debug", "true");
        }
    }

    
    
    /**
     * Callback interface for QR code detection results
     */
    public interface QRCodeCallback {
        void onQRCodeDetected(String text);
        void onError(Exception e);
    }
    
    /**
     * Initialize the QR code scanner with the default camera
     */
    public QRCodeScanner() {
        try {
            // Check if required webcam classes are available
            try {
                // Try loading the webcam class dynamically to avoid direct dependency issues
                webcamClass = Class.forName("com.github.sarxos.webcam.Webcam");
                webcamLibraryAvailable = true;
                
                // Use reflection to get the webcam instance
                System.out.println("Initializing camera...");
                System.out.println("Working directory: " + System.getProperty("user.dir"));
                
                // Get webcams method
                Object webcams = webcamClass.getMethod("getWebcams").invoke(null);
                
                // Log available webcams
                System.out.println("Available webcams:");
                if (webcams instanceof List) {
                    List<?> webcamList = (List<?>) webcams;
                    for (Object cam : webcamList) {
                        System.out.println(" * " + cam.getClass().getMethod("getName").invoke(cam));
                    }
                }
                
                // Get default webcam
                webcam = webcamClass.getMethod("getDefault").invoke(null);
                
                if (webcam != null) {
                    System.out.println("Webcam detected: " + webcamClass.getMethod("getName").invoke(webcam));
                    
                    // Get view sizes
                    Object[] dimensions = (Object[]) webcamClass.getMethod("getViewSizes").invoke(webcam);
                    for (Object dimension : dimensions) {
                        if (dimension instanceof Dimension) {
                            Dimension dim = (Dimension) dimension;
                            System.out.println("Available resolution: " + dim.width + "x" + dim.height);
                        }
                    }
                    
                    // Set view size - using a common size that should be available
                    try {
                        // Try to find a suitable resolution class
                        Class<?> resolutionClass = Class.forName("com.github.sarxos.webcam.WebcamResolution");
                        Object hdResolution = resolutionClass.getField("HD").get(null);
                        Dimension hdSize = (Dimension) hdResolution.getClass().getMethod("getSize").invoke(hdResolution);
                        
                        webcamClass.getMethod("setViewSize", Dimension.class).invoke(webcam, hdSize);
                    } catch (Exception e) {
                        // If HD resolution fails, try setting a common resolution directly
                        if (dimensions.length > 0 && dimensions[0] instanceof Dimension) {
                            webcamClass.getMethod("setViewSize", Dimension.class).invoke(webcam, dimensions[0]);
                        }
                    }
                    
                    System.out.println("Using webcam: " + webcamClass.getMethod("getName").invoke(webcam));
                    Dimension viewSize = (Dimension) webcamClass.getMethod("getViewSize").invoke(webcam);
                    System.out.println("Selected resolution: " + viewSize.width + "x" + viewSize.height);
                } else {
                    System.err.println("No webcam detected! The application will still work with file-based QR code detection.");
                }
            } catch (ClassNotFoundException e) {
                System.err.println("Webcam library not available: " + e.getMessage());
                webcamLibraryAvailable = false;
                webcam = null;
            } catch (Exception e) {
                System.err.println("Error initializing webcam: " + e.getMessage());
                e.printStackTrace();
                webcam = null;
            }
        } catch (Exception e) {
            System.err.println("Critical error initializing webcam system: " + e.getMessage());
            e.printStackTrace();
            webcam = null;
        }
    }
    
    /**
     * Get the webcam instance
     * 
     * @return the webcam instance or null if no webcam is available
     */
    public Object getWebcam() {
        return webcam;
    }
    
    /**
     * Decode QR code from a file
     * 
     * @param file the image file containing a QR code
     * @return the decoded text or null if no QR code was found
     * @throws IOException if the file cannot be read
     * @throws NotFoundException if no QR code is detected
     */
    public static String decodeQRCode(File file) throws IOException, NotFoundException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("Could not decode image");
        }
        return decodeQRCode(image);
    }
    
    /**
     * Decode QR code from a BufferedImage
     * 
     * @param image the image containing a QR code
     * @return the decoded text or null if no QR code was found
     * @throws NotFoundException if no QR code is detected
     */
    public static String decodeQRCode(BufferedImage image) throws NotFoundException {
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        
        Result result = READER.decode(bitmap, HINTS);
        return result.getText();
    }
    
    /**
     * Scan a single QR code using the webcam and return the decoded text
     * 
     * @return the decoded text from the QR code, or null if no QR code was found
     * @throws IOException if there's an error accessing the webcam
     * @throws IllegalStateException if no webcam is available
     */
    public String scanQRCode() throws IOException {
        if (webcam == null || !webcamLibraryAvailable) {
            throw new IllegalStateException("No webcam available. You can still use file-based QR code detection.");
        }
        
        try {
            boolean isOpen = (boolean) webcamClass.getMethod("isOpen").invoke(webcam);
            boolean wasOpen = isOpen;
            
            try {
                if (!wasOpen) {
                    webcamClass.getMethod("open").invoke(webcam);
                }
                
                // Capture an image from the webcam
                BufferedImage image = (BufferedImage) webcamClass.getMethod("getImage").invoke(webcam);
                if (image == null) {
                    throw new IOException("Failed to capture image from webcam");
                }
                
                // Try to decode QR code from the image
                try {
                    return decodeQRCode(image);
                } catch (NotFoundException e) {
                    // No QR code found in this frame
                    return null;
                }
            } finally {
                // Only close the webcam if it wasn't already open when this method was called
                if (!wasOpen) {
                    isOpen = (boolean) webcamClass.getMethod("isOpen").invoke(webcam);
                    if (isOpen) {
                        webcamClass.getMethod("close").invoke(webcam);
                    }
                }
            }
        } catch (Exception e) {
            throw new IOException("Error accessing webcam: " + e.getMessage(), e);
        }
    }

    
    /**
     * Scan multiple QR codes one by one and concatenate their contents
     * without any separators. After each scan, prompts the user if they want to continue.
     * 
     * @param reader BufferedReader to read user input for confirmation
     * @return the concatenated text from all QR codes
     * @throws IOException if there's an error accessing the webcam or reading input
     * @throws IllegalStateException if no webcam is available
     * @throws InterruptedException if the scanning process is interrupted
     */
    public String scanQRCodeList(BufferedReader reader) 
            throws IOException, InterruptedException {
        if (webcam == null || !webcamLibraryAvailable) {
            throw new IllegalStateException("No webcam available. You can still use file-based QR code detection.");
        }
        
        try {
            // Open the webcam once for all scans
            boolean isOpen = (boolean) webcamClass.getMethod("isOpen").invoke(webcam);
            boolean wasOpen = isOpen;
            
            if (!wasOpen) {
                webcamClass.getMethod("open").invoke(webcam);
            }
            
            StringBuilder result = new StringBuilder();
            boolean continueScan = true;
            int scanCount = 0;
            
            try {
                while (continueScan) {
                    scanCount++;
                    System.out.println("Please position QR code #" + scanCount + " for scanning...");
                    
                    String qrText = null;
                    System.out.println("Scanning... (Press Ctrl+C to cancel)");
                    
                    // Keep trying until we get a QR code
                    while (qrText == null) {
                        try {
                            // Capture an image from the webcam
                            BufferedImage image = (BufferedImage) webcamClass.getMethod("getImage").invoke(webcam);
                            if (image == null) {
                                Thread.sleep(100);
                                continue;
                            }
                            
                            // Try to decode QR code from the image
                            try {
                                qrText = decodeQRCode(image);
                            } catch (NotFoundException e) {
                                // No QR code found in this frame, try again
                                Thread.sleep(100); // Short delay before next capture
                            }
                        } catch (Exception e) {
                            System.err.println("Error capturing image: " + e.getMessage());
                            Thread.sleep(500);
                        }
                    }
                    
                    // Successfully scanned a QR code
                    System.out.println("Successfully scanned QR code #" + scanCount);
                    result.append(qrText);
                    
                    // Ask user if they want to continue scanning
                    System.out.print("Do you want to scan another QR code? (y/n): ");
                    String answer = reader.readLine().trim().toLowerCase();
                    continueScan = answer.equals("y") || answer.equals("yes");
                }
                
                return result.toString();
            } finally {
                // Only close the webcam if it wasn't already open when this method was called
                if (!wasOpen) {
                    isOpen = (boolean) webcamClass.getMethod("isOpen").invoke(webcam);
                    if (isOpen) {
                        webcamClass.getMethod("close").invoke(webcam);
                    }
                }
            }
        } catch (Exception e) {
            throw new IOException("Error with webcam operations: " + e.getMessage(), e);
        }
    }
    
    /**
     * Start scanning for QR codes with the camera
     * 
     * @param callback the callback to receive QR code detection results
     * @param intervalMillis the interval between scans in milliseconds
     */
    public void startScanning(QRCodeCallback callback, long intervalMillis) {
        if (running || webcam == null || !webcamLibraryAvailable) {
            if (callback != null && (webcam == null || !webcamLibraryAvailable)) {
                callback.onError(new IllegalStateException("No webcam available. You can still use file-based QR code detection."));
            }
            return;
        }
        
        this.callback = callback;
        
        try {
            boolean isOpen = (boolean) webcamClass.getMethod("isOpen").invoke(webcam);
            if (!isOpen) {
                System.out.println("Opening webcam...");
                webcamClass.getMethod("open").invoke(webcam);
                System.out.println("Webcam opened successfully");
            }
            
            running = true;
            executor = Executors.newSingleThreadScheduledExecutor();
            
            Method getImageMethod = webcamClass.getMethod("getImage");
            
            executor.scheduleAtFixedRate(() -> {
                try {
                    BufferedImage image = (BufferedImage) getImageMethod.invoke(webcam);
                    if (image != null) {
                        try {
                            String text = decodeQRCode(image);
                            if (callback != null) {
                                callback.onQRCodeDetected(text);
                            }
                        } catch (NotFoundException e) {
                            // No QR code found in this frame - that's normal
                        }
                    }
                } catch (Exception e) {
                    if (callback != null) {
                        callback.onError(e);
                    }
                }
            }, 0, intervalMillis, TimeUnit.MILLISECONDS);
            
            System.out.println("QR code scanner started");
        } catch (Exception e) {
            if (callback != null) {
                callback.onError(e);
            }
            System.err.println("Error starting scanner: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Stop scanning for QR codes
     */
    public void stopScanning() {
        running = false;
        
        if (executor != null) {
            try {
                executor.shutdown();
                executor.awaitTermination(1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                executor = null;
            }
        }
        
        if (webcam != null && webcamLibraryAvailable) {
            try {
                boolean isOpen = (boolean) webcamClass.getMethod("isOpen").invoke(webcam);
                if (isOpen) {
                    webcamClass.getMethod("close").invoke(webcam);
                }
            } catch (Exception e) {
                System.err.println("Error closing webcam: " + e.getMessage());
            }
        }
        
        System.out.println("QR code scanner stopped");
    }
    
    /**
     * Main method for testing the QR code scanner
     */
    public static void main(String[] args) {
        QRCodeScanner scanner = new QRCodeScanner();
        
        if (args.length > 0) {
            // If a file path is provided, decode QR code from that file
            try {
                File file = new File(args[0]);
                if (file.exists()) {
                    System.out.println("Decoding QR code from file: " + file.getAbsolutePath());
                    String text = decodeQRCode(file);
                    System.out.println("\nDetected QR Code:");
                    System.out.println(text);
                } else {
                    System.err.println("File not found: " + args[0]);
                }
            } catch (Exception e) {
                System.err.println("Error decoding QR code from file: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("No webcam available or file path provided. To decode a QR code from a file, please provide the file path as an argument.");
            System.out.println("Example: java utils.QRCodeScanner /path/to/qrcode.png");
        }
    }
} 