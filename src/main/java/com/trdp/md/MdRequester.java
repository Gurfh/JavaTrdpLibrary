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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MdRequester implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MdRequester.class);
    
    private final UdpTransport transport;
    private final AtomicInteger sequenceCounter;
    private final ConcurrentHashMap<Integer, CompletableFuture<MdReply>> pendingRequests;
    private volatile boolean running;
    
    public MdRequester(int localPort) throws IOException {
        this.transport = new UdpTransport(localPort);
        this.sequenceCounter = new AtomicInteger(0);
        this.pendingRequests = new ConcurrentHashMap<>();
        this.running = true;
        
        startReplyListener();
        logger.info("MD Requester created on port {}", localPort);
    }
    
    public CompletableFuture<MdReply> sendRequest(int comId, byte[] data, 
                                                   String destinationAddress, int destinationPort) {
        return sendRequest(comId, data, destinationAddress, destinationPort, comId);
    }
    
    public CompletableFuture<MdReply> sendRequest(int comId, byte[] data, 
                                                   String destinationAddress, int destinationPort, int replyComId) {
        if (data.length > TrdpConstants.TRDP_MAX_MD_DATA_SIZE) {
            CompletableFuture<MdReply> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Data size exceeds maximum MD data size"));
            return future;
        }
        
        int seqNo = sequenceCounter.getAndIncrement();
        CompletableFuture<MdReply> future = new CompletableFuture<>();
        
        try {
            InetAddress localAddress = InetAddress.getLocalHost();
            byte[] addrBytes = localAddress.getAddress();
            int replyIp = ((addrBytes[0] & 0xFF) << 24) |
                         ((addrBytes[1] & 0xFF) << 16) |
                         ((addrBytes[2] & 0xFF) << 8) |
                         (addrBytes[3] & 0xFF);
            
            TrdpHeader header = new TrdpHeader();
            header.setSequenceCounter(seqNo);
            header.setMessageType(TrdpMessageType.MD_REQUEST);
            header.setComId(comId);
            header.setEtbTopoCnt(0);
            header.setOpTrnTopoCnt(0);
            header.setReplyComId(replyComId);
            header.setReplyIpAddress(replyIp);
            
            TrdpPacket packet = new TrdpPacket(header, data);
            byte[] encodedPacket = packet.encode();
            
            pendingRequests.put(seqNo, future);
            
            transport.send(encodedPacket, InetAddress.getByName(destinationAddress), destinationPort);
            logger.debug("Sent MD request: ComID={}, SeqNo={}, ReplyComID={}, ReplyIP={}, Size={}", 
                       comId, seqNo, replyComId, localAddress.getHostAddress(), data.length);
            
            future.orTimeout(TrdpConstants.DEFAULT_MD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                  .whenComplete((reply, ex) -> {
                      if (ex != null) {
                          pendingRequests.remove(seqNo);
                          logger.warn("MD request timeout: ComID={}, SeqNo={}", comId, seqNo);
                      }
                  });
            
        } catch (IOException e) {
            future.completeExceptionally(e);
            pendingRequests.remove(seqNo);
        }
        
        return future;
    }
    
    private void startReplyListener() {
        Thread listener = new Thread(() -> {
            byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
            
            while (running) {
                try {
                    int length = transport.receive(buffer, TrdpConstants.DEFAULT_MD_TIMEOUT_MS);
                    if (length > 0) {
                        processReply(buffer, length);
                    }
                } catch (IOException e) {
                    if (running) {
                        logger.error("Error receiving MD reply", e);
                    }
                }
            }
        }, "MD-Requester-Listener");
        listener.setDaemon(true);
        listener.start();
    }
    
    private void processReply(byte[] buffer, int length) {
        try {
            byte[] packetData = new byte[length];
            System.arraycopy(buffer, 0, packetData, 0, length);
            
            TrdpPacket packet = TrdpPacket.decode(packetData);
            
            if (packet.getHeader().getMessageType() == TrdpMessageType.MD_REPLY) {
                int seqNo = packet.getHeader().getSequenceCounter();
                CompletableFuture<MdReply> future = pendingRequests.remove(seqNo);
                
                if (future != null) {
                    MdReply reply = new MdReply(packet.getHeader().getComId(), 
                                               packet.getPayload(), seqNo);
                    future.complete(reply);
                    logger.debug("Received MD reply: ComID={}, SeqNo={}", 
                               packet.getHeader().getComId(), seqNo);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing MD reply", e);
        }
    }
    
    @Override
    public void close() {
        running = false;
        pendingRequests.values().forEach(f -> f.cancel(true));
        pendingRequests.clear();
        transport.close();
        logger.info("MD Requester closed");
    }
}
