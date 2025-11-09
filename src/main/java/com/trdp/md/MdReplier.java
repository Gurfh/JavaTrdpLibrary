package com.trdp.md;

import com.trdp.network.UdpTransport;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpHeader;
import com.trdp.protocol.TrdpMessageType;
import com.trdp.protocol.TrdpPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MdReplier implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MdReplier.class);
    
    private final UdpTransport transport;
    private final MdRequestHandler handler;
    private final ExecutorService executor;
    private volatile boolean running;
    
    public MdReplier(int port, MdRequestHandler handler) throws IOException {
        this.transport = new UdpTransport(port);
        this.handler = handler;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MD-Replier");
            t.setDaemon(true);
            return t;
        });
        
        logger.info("MD Replier created on port {}", port);
    }
    
    public void start() {
        if (running) {
            logger.warn("MD Replier already running");
            return;
        }
        
        running = true;
        executor.submit(this::receiveLoop);
        logger.info("MD Replier started");
    }
    
    private void receiveLoop() {
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        
        while (running) {
            try {
                int length = transport.receive(buffer, TrdpConstants.DEFAULT_MD_TIMEOUT_MS);
                if (length > 0) {
                    processRequest(buffer, length);
                }
            } catch (IOException e) {
                if (running) {
                    logger.error("Error receiving MD request", e);
                }
            }
        }
    }
    
    private void processRequest(byte[] buffer, int length) {
        try {
            byte[] packetData = new byte[length];
            System.arraycopy(buffer, 0, packetData, 0, length);
            
            TrdpPacket requestPacket = TrdpPacket.decode(packetData);
            
            if (requestPacket.getHeader().getMessageType() != TrdpMessageType.MD_REQUEST) {
                logger.warn("Received non-request MD message, ignoring");
                return;
            }
            
            byte[] replyData = handler.handleRequest(requestPacket.getHeader().getComId(), 
                                                     requestPacket.getPayload());
            
            if (replyData != null) {
                sendReply(requestPacket, replyData);
            }
        } catch (Exception e) {
            logger.error("Error processing MD request", e);
        }
    }
    
    private void sendReply(TrdpPacket requestPacket, byte[] replyData) throws IOException {
        TrdpHeader replyHeader = new TrdpHeader();
        replyHeader.setSequenceCounter(requestPacket.getHeader().getSequenceCounter());
        replyHeader.setMessageType(TrdpMessageType.MD_REPLY);
        replyHeader.setComId(requestPacket.getHeader().getReplyComId());
        replyHeader.setEtbTopoCnt(0);
        replyHeader.setOpTrnTopoCnt(0);
        
        TrdpPacket replyPacket = new TrdpPacket(replyHeader, replyData);
        byte[] encodedPacket = replyPacket.encode();
        
        int replyIp = requestPacket.getHeader().getReplyIpAddress();
        InetAddress replyAddress = InetAddress.getByAddress(new byte[] {
            (byte)((replyIp >> 24) & 0xFF),
            (byte)((replyIp >> 16) & 0xFF),
            (byte)((replyIp >> 8) & 0xFF),
            (byte)(replyIp & 0xFF)
        });
        
        transport.send(encodedPacket, replyAddress, TrdpConstants.DEFAULT_MD_PORT);
        logger.debug("Sent MD reply: ComID={}, SeqNo={}", 
                   replyHeader.getComId(), replyHeader.getSequenceCounter());
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
        logger.info("MD Replier closed");
    }
}
