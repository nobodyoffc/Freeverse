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
                    try {
                        displayMessageQueue.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    String msg = displayMessageQueue.poll();
                        if(msg!=null)System.out.println(msg);
                    }
                }
            try {
                Thread.sleep(1000); // Small delay to reduce CPU usage
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
