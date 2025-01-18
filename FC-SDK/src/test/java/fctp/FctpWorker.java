package fctp;

import tools.BytesTools;

import java.io.IOException;
import java.net.*;
import java.util.*;

@SuppressWarnings("unused")
public class FctpWorker {
    public static final int RESEND_RTT_COUNT = 10;
    private final String hisHost;
    private final Integer myPort;
    private final Integer hisPort;
    private Integer rtt;
    private static Integer resendTime;
    private final boolean isReceiptRequired;
    private final boolean isOrdered;
    private final Map<BytesTools.ByteArrayAsKey, MsgUnit> sentMap = new HashMap<>();
    private final Queue<MsgUnit> receivedQueue = new PriorityQueue<>();
    private final Queue<MsgUnit> sendTaskQueue = new PriorityQueue<>();
    public static Boolean isRunning;


    public FctpWorker(String hisHost, Integer myPort, Integer hisPort, boolean isReceiptRequired, boolean isOrdered) {
        this.hisHost = hisHost;
        this.myPort = myPort;
        this.hisPort = hisPort;
        this.isReceiptRequired = isReceiptRequired;
        this.isOrdered = isOrdered;
    }

    public void start(){
        SendThread sendThread = new SendThread(hisHost,hisPort,sendTaskQueue,sentMap,receivedQueue);
        sendThread.start();

        ReceiveThread receiveThread = new ReceiveThread(myPort,receivedQueue,sentMap);
        receiveThread.start();
        isRunning = true;
    }

    public synchronized MsgUnit receiveOne(){
        return this.receivedQueue.poll();
    }

    public synchronized void sendOne(MsgUnit msgUnit){
        synchronized (sendTaskQueue) {
            this.sendTaskQueue.add(msgUnit);
            sendTaskQueue.notify();
        }
    }


    public static class SendThread extends Thread {
        private static final int RESEND_COUNT = 3;
        private final String hisHost;
        private final int hisPort;
        private final Queue<MsgUnit> sendTaskQueue;
        private final Map<BytesTools.ByteArrayAsKey, MsgUnit> sentMap;
        private Queue<MsgUnit> receivedQueue;
        public SendThread(String host, int port, Queue<MsgUnit> sendTaskQueue, Map<BytesTools.ByteArrayAsKey, MsgUnit> sentMap, Queue<MsgUnit> receivedQueue) {
            this.hisHost = host;
            this.hisPort = port;
            this.sendTaskQueue = sendTaskQueue;
            this.sentMap = sentMap;
            this.receivedQueue = receivedQueue;
        }

        @Override
        public void run() {

            // Process the message
            try {
                isRunning=true;
                send();
            } catch (InterruptedException | UnknownHostException e) {
                throw new RuntimeException(e);
            }
            // Add your message handling logic here
        }

        private void send() throws InterruptedException, UnknownHostException {
            InetAddress ip;
                ip = InetAddress.getByName(hisHost);

            while(isRunning) {
                //Check resend
                long now = System.currentTimeMillis();
                for(BytesTools.ByteArrayAsKey id:this.sentMap.keySet()){
                    MsgUnit msgUnit = sentMap.get(id);
                    synchronized (sentMap) {
                        if(msgUnit.getSendCount() > RESEND_COUNT){
                            sentMap.remove(id);
                        }else if(msgUnit.getTime() - now > resendTime){
                            synchronized (sendTaskQueue) {
                                this.sendTaskQueue.add(msgUnit);
                            }
                        this.sentMap.remove(id);
                        }
                    }
                }

                //Send
                while (!this.sendTaskQueue.isEmpty()) {
                    MsgUnit msgUnit;
                    synchronized (sendTaskQueue) {
                        msgUnit = sendTaskQueue.poll();
                    }
                    byte[] bytes = msgUnit.toBytes();

                    //TODO check size

                    DatagramPacket datagramPacket = new DatagramPacket(bytes, bytes.length, ip, hisPort);

                    try (DatagramSocket datagramSocket = new DatagramSocket(this.hisPort)) {
                        datagramSocket.send(datagramPacket);
                        msgUnit.sendCountAddOne();
                        synchronized (sentMap) {
                            sentMap.put(new BytesTools.ByteArrayAsKey(msgUnit.makeIdBytes()), msgUnit);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                synchronized (sendTaskQueue) {
                    sendTaskQueue.wait();
                }
            }
        }
    }

    public static class ReceiveThread extends Thread {
        private byte[] data;
        private final int myPort;
        private final Map<BytesTools.ByteArrayAsKey, MsgUnit> sentMap;
        private final Queue<MsgUnit> receivedQueue;

        public ReceiveThread(int myPort, Queue<MsgUnit> receivedQueue, Map<BytesTools.ByteArrayAsKey, MsgUnit> sentMap) {
            this.myPort = myPort;
            this.sentMap = sentMap;
            this.receivedQueue = receivedQueue;
        }


        @Override
        public void run() {
            // Process the message
            System.out.println("Processing message: " + Arrays.toString(data));
            try(DatagramSocket datagramSocket = new DatagramSocket(myPort)){
                byte[] buf = new byte[4096];
                while(isRunning) {
                    DatagramPacket datagramPacket = new DatagramPacket(buf, 4096);

                    datagramSocket.receive(datagramPacket);

                    System.out.println("Got data from " + datagramPacket.getAddress().getHostName());
                    int length = datagramPacket.getLength();
                    this.data = new byte[length];
                    System.arraycopy(datagramPacket.getData(), 0, this.data, 0, length);

                    MsgUnit msgUnit = MsgUnit.fromBytes(this.data);

                    if(MsgUnit.DataType.RECEIPT.equals(msgUnit.getDataType())){
                        BytesTools.ByteArrayAsKey key = new BytesTools.ByteArrayAsKey((byte[]) msgUnit.getData());
                        MsgUnit srcMsgUnit = this.sentMap.get(key);
                        if(srcMsgUnit !=null) {
                            synchronized (sentMap) {
                                sentMap.remove(key);
                            }
                        }
                        continue;
                    }

                    synchronized (receivedQueue) {
                        receivedQueue.add(msgUnit);
                    }
                }
            } catch (IOException e) {
                System.out.println("Failed to receive data.");
            }
            // Add your message handling logic here
        }
    }
//
//    public static void main(String[] args) throws UnknownHostException {
//        FctpWorker worker1 = new FctpWorker("127.0.0.1", 4455, 4466, false, false);
//        FctpWorker worker2 = new FctpWorker("127.0.0.1", 4466, 4455, false, false);
//
//        // Thread for worker1 sending data and worker2 receiving it
//        Thread worker1Sender = new Thread(() -> {
//            try {
//                System.out.println("Worker 1 sending...");
//                worker1.send("ping".getBytes());
//            } catch (UnknownHostException e) {
//                e.printStackTrace();
//            }
//        });
//
//        Thread worker2Receiver = new Thread(() -> {
//            try {
//                byte[] reply = worker2.receive();
//                System.out.println("Worker 2 received: " + new String(reply));
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        });
//
//        // Thread for worker2 sending data and worker1 receiving it
//        Thread worker2Sender = new Thread(() -> {
//            try {
//                System.out.println("Worker 2 sending...");
//                worker2.send("ping".getBytes());
//            } catch (UnknownHostException e) {
//                e.printStackTrace();
//            }
//        });
//
//        Thread worker1Receiver = new Thread(() -> {
//            try {
//                byte[] reply = worker1.receive();
//                System.out.println("Worker 1 received: " + new String(reply));
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        });
//
//        // Start threads for worker1 -> worker2 communication
//        worker1Sender.start();
//        worker2Receiver.start();
//
//        // Wait for the first communication to finish
//        try {
//            worker1Sender.join();
//            worker2Receiver.join();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//
//        // Start threads for worker2 -> worker1 communication
//        worker2Sender.start();
//        worker1Receiver.start();
//
//        // Wait for the second communication to finish
//        try {
//            worker2Sender.join();
//            worker1Receiver.join();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//    }
//
//
//    public void ping() throws UnknownHostException {
//        while (true) {
//            long startTime = System.currentTimeMillis();
//            if(!send("ping".getBytes()))continue;
//            byte[] received = receive();
//            if(received!=null && "pong".equals(new String(received))){
//                this.rtt = Math.toIntExact(System.currentTimeMillis() - startTime);
//                resendTime = RESEND_RTT_COUNT * rtt;
//                break;
//            }
//        }
//    }
//
//
//    public boolean send(byte[] bytes) throws UnknownHostException {
//        InetAddress ip= InetAddress.getByName(hisHost);
//        DatagramPacket datagramPacket = new DatagramPacket(bytes,bytes.length,ip, hisPort);
//        try(DatagramSocket datagramSocket = new DatagramSocket()){
//            datagramSocket.send(datagramPacket);
//        } catch (IOException e) {
//            System.out.println("Error:"+e.getMessage());
//            return false;
//        }
//        return true;
//    }
//
//    public byte[] receive(){
//        try(DatagramSocket datagramSocket = new DatagramSocket(myPort)){
//            byte[] buf = new byte[4096];
//            DatagramPacket datagramPacket = new DatagramPacket(buf,4096);
//            datagramSocket.receive(datagramPacket);
//            System.out.println("Got data from "+datagramPacket.getAddress().getHostName());
//            int length = datagramPacket.getLength();
//            byte[] data = new byte[length];
//            System.arraycopy(datagramPacket.getData(),0,data,0,length);
//            return data;
//        } catch (IOException e) {
//            System.out.println("Failed to receive data.");
//        }
//        return null;
//    }

}
