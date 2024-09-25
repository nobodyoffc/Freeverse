package test;

class WorkerThread implements Runnable {
    private byte[] message;

    public WorkerThread(byte[] message) {
        this.message = message;
    }

    @Override
    public void run() {
        // Process the message
        System.out.println("Processing message: " + message);

        // Add your message handling logic here
    }
}
