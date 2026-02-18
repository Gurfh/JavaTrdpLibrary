package com.trdp.md;

import com.trdp.network.TcpTransport;
import com.trdp.network.UdpTransport;
import com.trdp.network.ReceivedPacket;

import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpMdHeader;
import com.trdp.protocol.TrdpMessageType;
import com.trdp.protocol.TrdpPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch; // Import CountDownLatch
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class MdRequester implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MdRequester.class);
    private static final int MAX_TCP_CONNECTIONS = 16;

    private final UdpTransport udpTransport;
    private final ConcurrentHashMap<String, TcpTransport> tcpConnections;
    private final AtomicInteger sequenceCounter;
    
    // Map SessionID (UUID) to Future, as per IEC 61375-2-3 A.7.8.1
    private final Map<UUID, CompletableFuture<MdReply>> pendingSessions;
    
    private volatile boolean running;
    private final CountDownLatch listenerReadyLatch = new CountDownLatch(1);
    
    private int actualEtbTopoCnt = 0;
    private int actualOpTrnTopoCnt = 0;

    public MdRequester(int localPort) throws IOException {
        this.udpTransport = new UdpTransport(localPort);
        this.tcpConnections = new ConcurrentHashMap<>();
        this.sequenceCounter = new AtomicInteger(0);
        this.pendingSessions = new ConcurrentHashMap<>();
        this.running = true;
        
        startUdpReplyListener();
        
        try {
            if (!listenerReadyLatch.await(5, TimeUnit.SECONDS)) {
                // Handle timeout: close transport and throw exception
                running = false;
                udpTransport.close();
                throw new IOException("MD Requester listener thread failed to start in time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Handle interruption: close transport and throw exception
            running = false;
            udpTransport.close();
            throw new IOException("Interrupted while waiting for listener to start", e);
        }
        
        logger.info("MD Requester created on port {}", localPort);
    }

    public void setTopologyCounters(int etbTopoCnt, int opTrnTopoCnt) {
        this.actualEtbTopoCnt = etbTopoCnt;
        this.actualOpTrnTopoCnt = opTrnTopoCnt;
    }
    
    public CompletableFuture<MdReply> sendRequest(int comId, byte[] data, 
                                                   String destinationAddress, int destinationPort) {
        return sendRequest(comId, data, destinationAddress, destinationPort, TransportProtocol.UDP);
    }

    public CompletableFuture<MdReply> sendRequest(int comId, byte[] data,
                                                   String destinationAddress, int destinationPort,
                                                   TransportProtocol protocol) {
        return sendRequest(comId, data, destinationAddress, destinationPort, protocol, null, null);
    }

    public CompletableFuture<MdReply> sendRequest(int comId, byte[] data,
                                                   String destinationAddress, int destinationPort,
                                                   TransportProtocol protocol,
                                                   String sourceUri, String destinationUri) {
        
        if (data.length > TrdpConstants.TRDP_MAX_MD_DATA_SIZE) {
            CompletableFuture<MdReply> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Data size exceeds maximum MD data size"));
            return future;
        }
        
        UUID sessionId = UUID.randomUUID();
        int seqNo = sequenceCounter.getAndIncrement();
        CompletableFuture<MdReply> future = new CompletableFuture<>();
        
        try {
            TrdpMdHeader header = new TrdpMdHeader();
            header.setSequenceCounter(seqNo);
            header.setMessageType(TrdpMessageType.MD_REQUEST);
            header.setComId(comId);
            header.setSessionId(sessionId);
            header.setSourceUri(sourceUri);
            header.setDestinationUri(destinationUri);
            header.setReplyTimeout(TrdpConstants.DEFAULT_MD_TIMEOUT_MS * 1000); // Microseconds
            
            // Set topology counters
            header.setEtbTopoCnt(actualEtbTopoCnt);
            header.setOpTrnTopoCnt(actualOpTrnTopoCnt);
            
            TrdpPacket packet = new TrdpPacket(header, data);
            byte[] encodedPacket = packet.encode();
            
            pendingSessions.put(sessionId, future);
            
            if (protocol == TransportProtocol.UDP) {
                udpTransport.send(encodedPacket, InetAddress.getByName(destinationAddress), destinationPort);
            } else {
                TcpTransport tcpTransport = getOrCreateTcpConnection(destinationAddress, destinationPort);
                tcpTransport.send(encodedPacket);
            }

            logger.debug("Sent MD request: ComID={}, SessionID={}, Dest={}:{}", 
                       comId, sessionId, destinationAddress, destinationPort);
            
            future.orTimeout(TrdpConstants.DEFAULT_MD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                  .whenComplete((reply, ex) -> {
                      if (ex != null) {
                          pendingSessions.remove(sessionId);
                          if (ex instanceof TimeoutException) {
                              logger.warn("MD request timeout: SessionID={}", sessionId);
                          }
                      }
                  });
            
        } catch (IOException e) {
            future.completeExceptionally(e);
            pendingSessions.remove(sessionId);
        }
        
        return future;
    }
    
    private void startUdpReplyListener() {
        Thread listener = new Thread(() -> {
            byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
            listenerReadyLatch.countDown();
            
            while (running) {
                try {
                    ReceivedPacket packet = udpTransport.receiveWithSource(buffer, TrdpConstants.DEFAULT_MD_TIMEOUT_MS);
                    if (packet != null) {
                        processReplyPacket(packet.getData(), packet.getLength(), 
                                         packet.getSourceAddress(), packet.getSourcePort());
                    }
                } catch (IOException e) {
                    if (running) logger.error("Error receiving MD reply", e);
                }
            }
        }, "MD-Requester-Listener");
        listener.setDaemon(true);
        listener.start();
    }

    private void startTcpReplyListener(TcpTransport tcpTransport) {
        Thread listener = new Thread(() -> {
            try {
                tcpTransport.setSoTimeout(TrdpConstants.DEFAULT_MD_TIMEOUT_MS);
                DataInputStream in = new DataInputStream(tcpTransport.getInputStream());

                while (running && !tcpTransport.isClosed()) {
                    try {
                        // 1. Read MD header (fixed size) to determine payload length
                        byte[] headerBytes = new byte[TrdpConstants.TRDP_MD_HEADER_SIZE];
                        in.readFully(headerBytes);

                        // 2. Decode header to get payload length
                        TrdpMdHeader header;
                        try {
                            header = TrdpMdHeader.decode(headerBytes);
                        } catch (Exception e) {
                            logger.warn("Invalid TRDP header on TCP reply stream: {}", e.getMessage());
                            break; // Stream sync lost
                        }

                        // 3. Read payload if present
                        int datasetLen = header.getDatasetLength();
                        byte[] payload = new byte[0];
                        if (datasetLen > 0) {
                            if (datasetLen > TrdpConstants.TRDP_MAX_MD_DATA_SIZE) {
                                logger.warn("Oversized payload declared in TCP reply: {}", datasetLen);
                                break;
                            }
                            payload = new byte[datasetLen];
                            in.readFully(payload);
                        }

                        // 4. Reconstruct packet and process
                        TrdpPacket packet = new TrdpPacket(header, payload);
                        byte[] encoded = packet.encode();
                        processReplyPacket(encoded, encoded.length, null, 0);

                    } catch (SocketTimeoutException e) {
                        // Timeout is normal, loop back to check running flag
                    } catch (EOFException e) {
                        break; // Connection closed by peer
                    }
                }
            } catch (IOException e) {
                if (running) logger.error("Error in TCP reply listener", e);
            }
        }, "MD-Requester-TCP-Listener");
        listener.setDaemon(true);
        listener.start();
    }
    
    private void processReplyPacket(byte[] buffer, int length, InetAddress sourceAddress, int sourcePort) {
        try {
            byte[] packetData = new byte[length];
            System.arraycopy(buffer, 0, packetData, 0, length);
            
            TrdpPacket packet = TrdpPacket.decode(packetData);
            
            // Check if it is an MD Header
            if (!(packet.getHeader() instanceof TrdpMdHeader)) {
                return;
            }
            
            TrdpMdHeader header = (TrdpMdHeader) packet.getHeader();
            logger.debug("Received MD reply: ComID={}, SeqNo={}", header.getComId(), header.getSequenceCounter());
            
            // Check Topology (IEC 61375-2-3 A.7.8.1)
            if (!checkTopology(header)) {
                 logger.warn("Discarding MD Reply due to Topology mismatch. Local ETB: {}, Rx ETB: {}", 
                             actualEtbTopoCnt, header.getEtbTopoCnt());
                 return;
            }

            UUID sessionId = header.getSessionIdAsUuid();
            TrdpMessageType type = header.getMessageType();

            // Find matching session
            CompletableFuture<MdReply> future = pendingSessions.get(sessionId);
            if (future == null) {
                logger.debug("Received reply for unknown or expired SessionID: {}", sessionId);
                return;
            }

            if (type == TrdpMessageType.MD_REPLY || type == TrdpMessageType.MD_REPLY_CONFIRM) {
                
                MdReply reply = new MdReply(header.getComId(), packet.getPayload(), header.getSequenceCounter());
                
                if (type == TrdpMessageType.MD_REPLY_CONFIRM) {
                    // Mq -> Send Mc (Confirmation)
                    sendConfirmation(header, sourceAddress, sourcePort);
                }
                
                // Complete the user's future (Mp or Mq received)
                future.complete(reply);
                pendingSessions.remove(sessionId);
                
            } else if (type == TrdpMessageType.MD_ERROR) {
                // Me -> Exception
                future.completeExceptionally(new RuntimeException("TRDP Error received. Status: " + header.getReplyStatus()));
                pendingSessions.remove(sessionId);
            }

        } catch (Exception e) {
            logger.error("Error processing MD reply packet", e);
        }
    }
    
    // Validate that the reply comes from a device with the same topology view
    private boolean checkTopology(TrdpMdHeader header) {
        boolean etbOk = (actualEtbTopoCnt == 0) || (header.getEtbTopoCnt() == 0) || (header.getEtbTopoCnt() == actualEtbTopoCnt);
        boolean opTrnOk = (actualOpTrnTopoCnt == 0) || (header.getOpTrnTopoCnt() == 0) || (header.getOpTrnTopoCnt() == actualOpTrnTopoCnt);
        return etbOk && opTrnOk;
    }
    
    private void sendConfirmation(TrdpMdHeader replyHeader, InetAddress destAddress, int destPort) {
        try {
            TrdpMdHeader confirmHeader = new TrdpMdHeader();
            confirmHeader.setMessageType(TrdpMessageType.MD_CONFIRM);
            confirmHeader.setSessionId(replyHeader.getSessionId());
            confirmHeader.setComId(replyHeader.getComId());
            confirmHeader.setSequenceCounter(replyHeader.getSequenceCounter());
            // Mirror URIs for routing if needed, standard says confirm uses source URI from reply
            confirmHeader.setDestinationUri(replyHeader.getSourceUriString());
            
            TrdpPacket confirmPacket = new TrdpPacket(confirmHeader, new byte[0]);
            
            if (destAddress != null) {
                udpTransport.send(confirmPacket.encode(), destAddress, destPort);
                logger.debug("Sent MD Confirmation (Mc) for SessionID: {}", replyHeader.getSessionIdAsUuid());
            } else {
                // For TCP, we would need the socket context. 
                // In this implementation, TCP confirmation is sent on the open connection if tracked.
                logger.warn("Cannot send Confirmation via UDP for TCP session context (Not fully implemented for TCP)");
            }
        } catch (IOException e) {
            logger.error("Failed to send MD confirmation", e);
        }
    }
    
    private synchronized TcpTransport getOrCreateTcpConnection(String host, int port) throws IOException {
        String key = host + ":" + port;

        // Evict closed/stale connections
        tcpConnections.entrySet().removeIf(entry -> entry.getValue().isClosed());

        // Reuse existing healthy connection
        TcpTransport existing = tcpConnections.get(key);
        if (existing != null && !existing.isClosed()) {
            return existing;
        }
        tcpConnections.remove(key);

        // Enforce pool capacity
        if (tcpConnections.size() >= MAX_TCP_CONNECTIONS) {
            throw new IOException("TCP connection pool exhausted (max " + MAX_TCP_CONNECTIONS + " connections)");
        }

        // Create new connection
        TcpTransport newTransport = new TcpTransport(host, port);
        tcpConnections.put(key, newTransport);
        startTcpReplyListener(newTransport);
        logger.debug("TCP connection pool: added {} ({}/{})", key, tcpConnections.size(), MAX_TCP_CONNECTIONS);
        return newTransport;
    }

    @Override
    public void close() {
        running = false;
        
        // 1. Cancel sessions
        pendingSessions.values().forEach(f -> f.cancel(true));
        pendingSessions.clear();
        
        // 2. Close all cached TCP connections
        tcpConnections.values().forEach(transport -> {
            try { 
                transport.close(); 
            } catch (IOException e) { 
                logger.error("Error closing TCP connection", e); 
            }
        });
        
        // 3. Clear the map references (Important fix)
        tcpConnections.clear();
        
        // 4. Close UDP transport
        udpTransport.close();
        
        logger.info("MD Requester closed");
    }
}
