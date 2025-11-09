package com.trdp.pd;

import com.trdp.network.UdpTransport;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpMessageType;
import com.trdp.protocol.TrdpPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PdSubscriber implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(PdSubscriber.class);
    
    private final UdpTransport transport;
    private final int comId;
    private final CopyOnWriteArrayList<PdDataListener> listeners;
    private final ExecutorService executor;
    private volatile boolean running;
    
    public PdSubscriber(int comId, String multicastGroup, int port) throws IOException {
        this.comId = comId;
        this.transport = new UdpTransport(port);
        this.listeners = new CopyOnWriteArrayList<>();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PD-Subscriber-" + comId);
            t.setDaemon(true);
            return t;
        });
        
        transport.joinMulticastGroup(InetAddress.getByName(multicastGroup));
        logger.info("PD Subscriber created for ComID {} on {}:{}", comId, multicastGroup, port);
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
            
            if (packet.getHeader().getMessageType() != TrdpMessageType.PD) {
                logger.warn("Received non-PD message, ignoring");
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
                listener.onDataReceived(comId, data, sequenceNumber);
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
