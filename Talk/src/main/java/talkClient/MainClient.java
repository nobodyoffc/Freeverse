package talkClient;

import clients.TalkClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainClient {
    public static void main(String[] args) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        try {
            System.out.println("Attempting to connect to localhost:8888...");
            TalkClient client = new TalkClient("http://localhost:8888", br);
            client.start();
            
            // Add a test message
            System.out.println("Sending test message...");
            boolean done = client.sendMessage("Hello, server! This is a test message.");
            if(done) System.out.println("Done.");
        } catch (Exception e) {
            System.err.println("Failed to connect to chat server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
