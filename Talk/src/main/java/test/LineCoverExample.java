package test;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class LineCoverExample extends JPanel {
    private int previousY = 50;  // Starting position for the line
    private int currentY = 50;   // Current position for the line

    public LineCoverExample() {
        Timer timer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Update the currentY position to create the effect of movement
                previousY = currentY; // Save the old position
                currentY += 20; // Move down 20 pixels
                repaint(); // Repaint the panel to show the new line position
            }
        });
        timer.start(); // Start the timer
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g); // Clear the panel
        g.setColor(Color.RED); // Set color for the line
        g.drawLine(50, previousY, 250, previousY); // Draw the previous line
        g.setColor(Color.BLUE); // Set color for the new line
        g.drawLine(50, currentY, 250, currentY); // Draw the current line
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Line Cover Example");
        LineCoverExample panel = new LineCoverExample();
        frame.add(panel);
        frame.setSize(300, 300);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}
