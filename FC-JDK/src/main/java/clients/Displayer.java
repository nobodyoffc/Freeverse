package clients;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import utils.DateUtils;

public class Displayer extends Thread {
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<String> displayMessageQueue = new ConcurrentLinkedQueue<>();
    private final TalkClient talkClient;

    public Displayer(TalkClient talkClient) {
        this.talkClient = talkClient;
    }

    @Override
    public void run() {
        while (running.get()) {
            String msg = null;
            synchronized (displayMessageQueue) {
                while ((displayMessageQueue.isEmpty() || paused.get()) && running.get()) {
                    try {
                        displayMessageQueue.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (!paused.get()) {  // Only poll if not paused
                    msg = displayMessageQueue.poll();
                }
            }
            if (msg != null) {
                System.out.println(msg);
                try {
                    Thread.sleep(50);  // Add a short 50ms delay between messages
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    public void displayMessage(String message) {
        synchronized (displayMessageQueue) {
            displayMessageQueue.add(message);
            displayMessageQueue.notify();
        }
    }

    public void displayAppNotice(String message) {
        String time = DateUtils.now("yy-MM-dd HH:mm:ss");
        displayMessage("[APP] " + time + " " + message);
    }

    public void pause() {
        paused.set(true);
    }

    public void resumeDisplay() {
        synchronized (displayMessageQueue) {
            paused.set(false);
            displayMessageQueue.notify();  // Wake up the waiting thread
        }
    }

    public void stopDisplay() {
        running.set(false);
    }
}
