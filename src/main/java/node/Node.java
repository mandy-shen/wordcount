package node;

import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class Node {

    public static String leader = "gate";
    String hostname;
    String ip;
    DatagramSocket heartbeat;
    DatagramSocket chgleader;
    Random random;

    Receiver recv;
    Leader lead;

    public Node() {
        this.random = new Random();
        try {
            this.hostname = InetAddress.getLocalHost().getHostName();
            this.ip = InetAddress.getLocalHost().getHostAddress();
            heartbeat = new DatagramSocket(4445);
            chgleader = new DatagramSocket(2000);

            init();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void init() {
        recv = new Receiver();
    }

    class Receiver {

        public Receiver() {
            accept();
        }

        private void accept() {
            try {
                System.out.printf("%s=rec\n", hostname);
                if (!"gate".equals(hostname))
                    heartbeat.setSoTimeout(1000);

                byte[] rec = new byte[16];
                DatagramPacket recPkt = new DatagramPacket(rec, rec.length);
                while (true) {
                    heartbeat.receive(recPkt);
                    String msg = new String(recPkt.getData(), 0, recPkt.getLength());
                    System.out.printf("**** r%s_REC=%s\n", hostname, msg);

                    if (msg.contains(":")) {
                        String[] arg = msg.split(":");
                        if (arg[0].equals(leader)) {
                            leader = arg[1];
                            broadcast(chgleader,"ok", 2000);
                        } else {
                            broadcast(chgleader,"xx", 2000);
                        }
                    }
                }
            } catch (SocketTimeoutException e) {
                waitTobeLeader();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void waitTobeLeader() {
            try {
                System.out.printf("%s=candidate\n", hostname);
                broadcast(heartbeat,leader + ":" + hostname, 4445);

                byte[] rec = new byte[8];
                DatagramPacket recPkt = new DatagramPacket(rec, rec.length);
                chgleader.setSoTimeout(1000);
                chgleader.receive(recPkt);
                String msg = new String(recPkt.getData(), 0, recPkt.getLength());
                if ("ok".equals(msg))
                    lead = new Leader();
                else
                    accept();
            } catch (Exception e) {
                accept();
            }
        }
    }

    class Leader {
        public Timer timer;

        public Leader() {
            initTimer();
        }

        private void initTimer() {
            this.timer = new Timer();
            System.out.printf("%s=leader\n", hostname);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    broadcast(heartbeat, "lv-"+hostname, 4445);
                }
            }, 0, 100); // every 0.1s broadcast heartbeat
        }

    }

    private void broadcast(DatagramSocket socket, String msg, int port) {
        try {
            //System.out.printf("%s=start_broadcast\n", hostname);
            socket.setBroadcast(true);

            //System.out.printf("%s=start_broadcast_setting\n", hostname);
            byte[] buffer = msg.getBytes();
            InetAddress addr = InetAddress.getByName("255.255.255.255");

            //System.out.println("sent="+msg);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, addr, port);
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
