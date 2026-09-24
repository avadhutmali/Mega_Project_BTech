package com.idlegrid.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

@Component
public class UdpDiscoveryServer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UdpDiscoveryServer.class);
    private static final int DISCOVERY_PORT = 9091;
    private static final String DISCOVERY_REQ = "IDLEGRID_DISCOVER_MASTER";
    private static final String DISCOVERY_ACK_PREFIX = "IDLEGRID_MASTER_ACK:";

    @Value("${server.port:9090}")
    private int serverPort;

    @Override
    public void run(String... args) throws Exception {
        Thread thread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT)) {
                log.info("UDP Auto-Discovery Server listening on port {}", DISCOVERY_PORT);
                byte[] buffer = new byte[256];
                
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    
                    String request = new String(packet.getData(), 0, packet.getLength()).trim();
                    if (DISCOVERY_REQ.equals(request)) {
                        String ack = DISCOVERY_ACK_PREFIX + serverPort;
                        byte[] ackBytes = ack.getBytes();
                        DatagramPacket response = new DatagramPacket(
                                ackBytes, ackBytes.length, packet.getAddress(), packet.getPort()
                        );
                        socket.send(response);
                        log.info("Responded to discovery request from {}", packet.getAddress().getHostAddress());
                    }
                }
            } catch (Exception e) {
                log.error("UDP Discovery server failed: {}", e.getMessage());
            }
        });
        thread.setDaemon(true);
        thread.setName("udp-discovery");
        thread.start();
    }
}
