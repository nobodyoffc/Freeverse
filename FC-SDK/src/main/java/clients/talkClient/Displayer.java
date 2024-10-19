package clients.talkClient;

import static clients.talkClient.TalkTcpClient.displayMessageQueue;

import java.util.concurrent.atomic.AtomicBoolean;

public class Displayer extends Thread {
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean paused = new AtomicBoolean(false);

    public Displayer() {
    }

    @Override
    public void run() {
        while (running.get()) {
            if (!paused.get()) {
                synchronized (displayMessageQueue) {
                        System.out.println(displayMessageQueue.poll());
                    }
                }
            try {
                Thread.sleep(100); // Small delay to reduce CPU usage
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }


    public void pause() {
        paused.set(true);
    }

    public void resumeDisplay() {
        paused.set(false);
    }

    public void stopDisplay() {
        running.set(false);
    }
}
