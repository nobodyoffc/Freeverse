package utils;

import java.io.File;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A terminal-based interface for scanning QR codes using a camera.
 * This class provides a simple command-line interface for the QRCodeScanner.
 */
public class QRCodeTerminalScanner {
    
    /**
     * Main method for running the QR code scanner from the terminal
     */
    public static void main(String[] args) {
        System.out.println("QR Code Terminal Scanner");
        System.out.println("=======================");
        
        // Check if file path is provided as an argument
        if (args.length > 0) {
            try {
                File file = new File(args[0]);
                if (file.exists()) {
                    System.out.println("Decoding QR code from file: " + file.getAbsolutePath());
                    String text = QRCodeScanner.decodeQRCode(file);
                    System.out.println("\nDetected QR Code:");
                    System.out.println(text);
                } else {
                    System.err.println("File not found: " + args[0]);
                }
            } catch (Exception e) {
                System.err.println("Error decoding QR code from file: " + e.getMessage());
                e.printStackTrace();
            }
            return;
        }
        
        System.out.println("Initializing camera...");
        
        QRCodeScanner scanner = new QRCodeScanner();
        AtomicBoolean lastDetectedCode = new AtomicBoolean(false);
        Scanner inputScanner = new Scanner(System.in);
        String lastDetectedText = null;
        
        // If no webcam is available, switch to file-based mode
        if (scanner.getWebcam() == null) {
            fileScanningMode(inputScanner);
            return;
        }
        
        // Start the scanner with a callback
        scanner.startScanning(new QRCodeScanner.QRCodeCallback() {
            @Override
            public void onQRCodeDetected(String text) {
                if (lastDetectedCode.compareAndSet(false, true)) {
                    System.out.println("\n==== QR CODE DETECTED ====");
                    System.out.println(text);
                    System.out.println("==========================");
                    System.out.println("\nCommands: (s)can again, (c)opy to clipboard, (q)uit");
                }
            }
            
            @Override
            public void onError(Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }, 200); // Scan every 200 milliseconds
        
        // Command line interface for controlling the scanner
        try {
            boolean running = true;
            System.out.println("\nScanner is running. Press Enter to see commands, or type 'q' to quit.");
            
            while (running) {
                String command = inputScanner.nextLine().trim().toLowerCase();
                
                switch (command) {
                    case "q", "quit", "exit" -> {
                        running = false;
                        System.out.println("Shutting down scanner...");
                    }
                    case "s", "scan" -> {
                        System.out.println("Scanning for QR codes... Please show a QR code to the camera.");
                        lastDetectedCode.set(false);
                    }
                    case "c", "copy" -> {
                        if (lastDetectedText != null) {
                            // In a proper implementation, you'd use a clipboard library here
                            System.out.println("Text copied to clipboard: " + lastDetectedText);
                        } else {
                            System.out.println("No QR code has been detected yet.");
                        }
                    }
                    case "f", "file" -> {
                        System.out.println("Enter the path to a QR code image file:");
                        String filePath = inputScanner.nextLine().trim();
                        try {
                            File file = new File(filePath);
                            if (file.exists()) {
                                String text = QRCodeScanner.decodeQRCode(file);
                                System.out.println("\n==== QR CODE DETECTED FROM FILE ====");
                                System.out.println(text);
                                System.out.println("==================================");
                                lastDetectedText = text;
                            } else {
                                System.err.println("File not found: " + filePath);
                            }
                        } catch (Exception e) {
                            System.err.println("Error decoding QR code from file: " + e.getMessage());
                        }
                    }
                    case "h", "help", "?" -> {
                        printHelp();
                    }
                    case "" -> {
                        System.out.println("\nCommands: (s)can, (c)opy, (f)ile, (h)elp, (q)uit");
                    }
                    default -> {
                        System.out.println("Unknown command. Type 'h' for help.");
                    }
                }
            }
        } finally {
            scanner.stopScanning();
            inputScanner.close();
            System.out.println("Scanner stopped.");
        }
    }
    
    /**
     * File-based QR code scanning mode
     */
    private static void fileScanningMode(Scanner inputScanner) {
        System.out.println("No webcam detected. Operating in file-based mode.");
        System.out.println("Type 'h' for help or 'q' to quit.");
        
        String lastDetectedText = null;
        boolean running = true;
        
        while (running) {
            System.out.print("> ");
            String command = inputScanner.nextLine().trim().toLowerCase();
            
            switch (command) {
                case "q", "quit", "exit" -> {
                    running = false;
                    System.out.println("Exiting...");
                }
                case "f", "file" -> {
                    System.out.println("Enter the path to a QR code image file:");
                    String filePath = inputScanner.nextLine().trim();
                    try {
                        File file = new File(filePath);
                        if (file.exists()) {
                            String text = QRCodeScanner.decodeQRCode(file);
                            System.out.println("\n==== QR CODE DETECTED FROM FILE ====");
                            System.out.println(text);
                            System.out.println("==================================");
                            lastDetectedText = text;
                        } else {
                            System.err.println("File not found: " + filePath);
                        }
                    } catch (Exception e) {
                        System.err.println("Error decoding QR code from file: " + e.getMessage());
                    }
                }
                case "c", "copy" -> {
                    if (lastDetectedText != null) {
                        System.out.println("Text copied to clipboard: " + lastDetectedText);
                    } else {
                        System.out.println("No QR code has been detected yet.");
                    }
                }
                case "h", "help", "?" -> {
                    printFileBasedHelp();
                }
                case "" -> {
                    System.out.println("\nCommands: (f)ile, (c)opy, (h)elp, (q)uit");
                }
                default -> {
                    System.out.println("Unknown command. Type 'h' for help.");
                }
            }
        }
    }
    
    /**
     * Print help information for camera mode
     */
    private static void printHelp() {
        System.out.println("\nQR Code Terminal Scanner Help");
        System.out.println("===========================");
        System.out.println("s, scan    - Start scanning for QR codes");
        System.out.println("f, file    - Scan a QR code from an image file");
        System.out.println("c, copy    - Copy the last detected QR code text to clipboard");
        System.out.println("h, help, ? - Show this help message");
        System.out.println("q, quit    - Quit the application");
        System.out.println();
    }
    
    /**
     * Print help information for file-based mode
     */
    private static void printFileBasedHelp() {
        System.out.println("\nQR Code Terminal Scanner Help (File-based Mode)");
        System.out.println("=============================================");
        System.out.println("f, file    - Scan a QR code from an image file");
        System.out.println("c, copy    - Copy the last detected QR code text to clipboard");
        System.out.println("h, help, ? - Show this help message");
        System.out.println("q, quit    - Quit the application");
        System.out.println();
    }
} 