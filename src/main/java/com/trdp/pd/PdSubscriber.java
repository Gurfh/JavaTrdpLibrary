package com.trdp.pd;

import com.trdp.network.UdpTransport;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpMessageType;
import com.trdp.protocol.TrdpPacket;
import com.trdp.protocol.TrdpPdHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PdSubscriber implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(PdSubscriber.class);
    
    private final UdpTransport transport;
    private final int comId;
    private final CopyOnWriteArrayList<PdDataListener> listeners;
    private final ExecutorService executor;
    private final AtomicInteger requestSequenceCounter;
    private volatile boolean running;
    
    private int etbTopoCnt = 0;
    private int opTrnTopoCnt = 0;
    
    public PdSubscriber(int comId, String address, int port) throws IOException {
        this.comId = comId;
        this.transport = new UdpTransport(port); // Binds to 0.0.0.0:port
        this.listeners = new CopyOnWriteArrayList<>();
        this.requestSequenceCounter = new AtomicInteger(0);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PD-Subscriber-" + comId);
            t.setDaemon(true);
            return t;
        });
        
        InetAddress inetAddress = InetAddress.getByName(address);
        
        // Only join if it is actually a multicast address
        if (inetAddress.isMulticastAddress()) {
            transport.joinMulticastGroup(inetAddress);
            logger.info("PD Subscriber joined multicast group {} on port {}", address, port);
        } else {
            logger.info("PD Subscriber listening for Unicast on port {}", port);
        }
    }
    
    public void setTopologyCounters(int etbTopoCnt, int opTrnTopoCnt) {
        this.etbTopoCnt = etbTopoCnt;
        this.opTrnTopoCnt = opTrnTopoCnt;
    }

    /**
     * Sends a PD Request to a Publisher from this subscriber's socket.
     * This ensures the reply (sent to source port) is received by this subscriber.
     *
     * @param requestComId The ComID to request.
     * @param destinationIp The IP address of the Publisher.
     * @param destinationPort The port of the Publisher.
     * @param replyComId The ComID to be used in the reply (0 to use the requested ComID).
     * @param replyIpAddress The IP address where the reply should be sent.
     * @throws IOException If sending fails.
     */
    public void request(int requestComId, String destinationIp, int destinationPort, int replyComId, String replyIpAddress) throws IOException {
        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(requestSequenceCounter.getAndIncrement());
        header.setMessageType(TrdpMessageType.PD_REQUEST);
        header.setComId(requestComId);
        header.setEtbTopoCnt(etbTopoCnt);
        header.setOpTrnTopoCnt(opTrnTopoCnt);
        
        header.setReplyComId(replyComId);
        header.setReplyIpAddress(ipToInt(replyIpAddress));
        
        header.setDatasetLength(0);

        TrdpPacket packet = new TrdpPacket(header, new byte[0]);
        byte[] encodedPacket = packet.encode();

        InetAddress destAddr = InetAddress.getByName(destinationIp);
        transport.send(encodedPacket, destAddr, destinationPort);
        
        logger.debug("Sent PD Request from Subscriber: ComID={}, Dest={}:{}", requestComId, destinationIp, destinationPort);
    }
    
    private int ipToInt(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return 0;
        }
        try {
            InetAddress inet = InetAddress.getByName(ipAddress);
            return ByteBuffer.wrap(inet.getAddress()).getInt();
        } catch (Exception e) {
            logger.warn("Invalid IP address format: {}", ipAddress);
            return 0;
        }
    }
    
    public void start() {
        if (running) {
            logger.warn("PD Subscriber already running for ComID {}", comId);
            return;
        }
        
        running = true;
        executor.submit(this::receiveLoop);
        logger.info("PD Subscriber started for ComID {}", comId);
    }
    
    private void receiveLoop() {
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        
        while (running) {
            try {
                int length = transport.receive(buffer, TrdpConstants.DEFAULT_PD_TIMEOUT_MS);
                if (length > 0) {
                    processReceivedData(buffer, length);
                }
            } catch (IOException e) {
                if (running) {
                    logger.error("Error receiving PD data for ComID {}", comId, e);
                }
            }
        }
    }
    
    private void processReceivedData(byte[] buffer, int length) {
        try {
            byte[] packetData = new byte[length];
            System.arraycopy(buffer, 0, packetData, 0, length);
            
            TrdpPacket packet = TrdpPacket.decode(packetData);
            TrdpMessageType type = packet.getHeader().getMessageType();

            // Accept both PD (Push) and PD_REPLY (Pull)
            if (type != TrdpMessageType.PD && type != TrdpMessageType.PD_REPLY) {
                logger.debug("Received message type {}, ignoring in PD Subscriber", type);
                return;
            }
            
            if (packet.getHeader().getComId() == comId) {
                notifyListeners(packet.getPayload(), packet.getHeader().getSequenceCounter());
            }
        } catch (Exception e) {
            logger.error("Error processing received PD packet", e);
        }
    }
    
    private void notifyListeners(byte[] data, int sequenceNumber) {
        for (PdDataListener listener : listeners) {
            try {
                listener.onDataReceived(comId, Arrays.copyOf(data, data.length), sequenceNumber);
            } catch (Exception e) {
                logger.error("Error in PD listener callback", e);
            }
        }
    }
    
    public void addListener(PdDataListener listener) {
        listeners.add(listener);
        logger.debug("Added listener to PD Subscriber for ComID {}", comId);
    }
    
    public void removeListener(PdDataListener listener) {
        listeners.remove(listener);
        logger.debug("Removed listener from PD Subscriber for ComID {}", comId);
    }
    
    @Override
    public void close() {
        running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        transport.close();
        logger.info("PD Subscriber closed for ComID {}", comId);
    }
}
