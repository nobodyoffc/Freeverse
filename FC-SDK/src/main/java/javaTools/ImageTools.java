package javaTools;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

public class ImageTools {

    public static boolean savePng(byte[] imageData, String outputPath) {
        if (imageData == null || outputPath == null) {
            System.err.println("Invalid input: imageData is null or outputPath is null.");
            return false;
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image == null) {
                System.err.println("Conversion to BufferedImage failed, possibly due to incorrect data format.");
                return false;
            }
            File outputFile = new File(outputPath);
            return ImageIO.write(image, "png", outputFile);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void displayPng(byte[] imageData) {
        if (imageData == null) {
            System.err.println("Invalid input: imageData is null.");
            return;
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image == null) {
                System.err.println("Conversion to BufferedImage failed, possibly due to incorrect data format.");
                return;
            }
            JFrame frame = new JFrame();
            frame.setLayout(new FlowLayout());
            frame.setSize(image.getWidth(), image.getHeight());
            JLabel label = new JLabel(new ImageIcon(image));
            frame.add(label);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.pack(); // Adjusts frame to the size of its components
            frame.setVisible(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
//    public static void displayPng(byte[] imageData) {
//        try {
//            // Convert byte array into BufferedImage
//            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
//
//            // Display the image in a JFrame
//            JFrame frame = new JFrame();
//            frame.setLayout(new FlowLayout());
//            frame.setSize(500, 500);
//            JLabel label = new JLabel(new ImageIcon(image));
//            frame.add(label);
//            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//            frame.setVisible(true);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    public static void savePng(byte[] imageData, String name) {
//        try {
//            // Convert byte array into BufferedImage
//            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
//
//            // Write the image to a file
//            File outputFile = new File(name+".png");
//            ImageIO.write(image, "png", outputFile);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
}
