package com.trdp.md;

import com.trdp.network.ReceivedPacket; // Import ReceivedPacket
import com.trdp.network.UdpTransport;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpHeader;
import com.trdp.protocol.TrdpMdHeader;
import com.trdp.protocol.TrdpMessageType;
import com.trdp.protocol.TrdpPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MdReplier implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MdReplier.class);
    
    private final UdpTransport udpTransport;
    private final ServerSocket tcpListener;
    private final MdRequestHandler handler;
    private final ExecutorService executor;
    private volatile boolean running;
    
    public MdReplier(int port, MdRequestHandler handler) throws IOException {
        this.udpTransport = new UdpTransport(port);
        this.tcpListener = new ServerSocket(port);
        this.handler = handler;
        this.executor = Executors.newCachedThreadPool();
        
        logger.info("MD Replier created on port {}", port);
    }
    
    public void start() {
        if (running) {
            logger.warn("MD Replier already running");
            return;
        }
        
        running = true;
        executor.submit(this::udpReceiveLoop);
        executor.submit(this::tcpReceiveLoop);
        logger.info("MD Replier started");
    }
    
    private void udpReceiveLoop() {
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        
        while (running) {
            try {
                ReceivedPacket received = udpTransport.receiveWithSource(buffer, TrdpConstants.DEFAULT_MD_TIMEOUT_MS);
                
                if (received != null) {
                    processUdpRequest(received.getData(), received.getLength(),
                                      received.getSourceAddress(), received.getSourcePort());
                }
            } catch (IOException e) {
                if (running) {
                    logger.error("Error receiving UDP MD request", e);
                }
            }
        }
    }

    private void tcpReceiveLoop() {
        while (running) {
            try {
                Socket clientSocket = tcpListener.accept();
                logger.debug("Accepted TCP connection from {}", clientSocket.getRemoteSocketAddress());
                executor.submit(() -> handleTcpConnection(clientSocket));
            } catch (IOException e) {
                if (running) {
                    logger.error("Error accepting TCP connection", e);
                }
            }
        }
    }
    
    private void handleTcpConnection(Socket clientSocket) {
        try (clientSocket) {
            byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
            InputStream in = clientSocket.getInputStream();

            while (running && !clientSocket.isClosed()) {
                int bytesRead = in.read(buffer);
                if (bytesRead > 0) {
                    processTcpRequest(buffer, bytesRead, clientSocket);
                } else {
                    break; // Client closed connection
                }
            }
        } catch (IOException e) {
            logger.error("Error handling TCP connection", e);
        }
    }

    private void processUdpRequest(byte[] buffer, int length, InetAddress sourceAddress, int sourcePort) {
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
                // Get the reply IP from the TRDP header (as per spec)
                int replyIp = requestPacket.getHeader().getReplyIpAddress();
                InetAddress replyAddress = InetAddress.getByAddress(new byte[] {
                    (byte)((replyIp >> 24) & 0xFF),
                    (byte)((replyIp >> 16) & 0xFF),
                    (byte)((replyIp >> 8) & 0xFF),
                    (byte)(replyIp & 0xFF)
                });

                // Get the reply port from the UDP packet source
                int replyPort = sourcePort;

                sendReply(requestPacket, replyData, replyAddress, replyPort);
            }
        } catch (Exception e) {
            logger.error("Error processing MD request", e);
        }
    }
    
    private void processTcpRequest(byte[] buffer, int length, Socket clientSocket) {
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
                sendReply(requestPacket, replyData, clientSocket);
            }
        } catch (Exception e) {
            logger.error("Error processing TCP MD request", e);
        }
    }

    private void sendReply(TrdpPacket requestPacket, byte[] replyData, 
                           InetAddress replyAddress, int replyPort) throws IOException {
        
        TrdpMdHeader replyHeader = new TrdpMdHeader();
        replyHeader.setSequenceCounter(requestPacket.getHeader().getSequenceCounter());
        replyHeader.setMessageType(TrdpMessageType.MD_REPLY);
        replyHeader.setComId(requestPacket.getHeader().getReplyComId());
        
        TrdpPacket replyPacket = new TrdpPacket(replyHeader, replyData);
        byte[] encodedPacket = replyPacket.encode();
        
        udpTransport.send(encodedPacket, replyAddress, replyPort);
        
        logger.debug("Sent UDP MD reply: ComID={}, SeqNo={}",
                   replyHeader.getComId(), replyHeader.getSequenceCounter());
    }

    private void sendReply(TrdpPacket requestPacket, byte[] replyData, Socket clientSocket) throws IOException {
        TrdpMdHeader replyHeader = new TrdpMdHeader();
        replyHeader.setSequenceCounter(requestPacket.getHeader().getSequenceCounter());
        replyHeader.setMessageType(TrdpMessageType.MD_REPLY);
        replyHeader.setComId(requestPacket.getHeader().getReplyComId());

        TrdpPacket replyPacket = new TrdpPacket(replyHeader, replyData);
        byte[] encodedPacket = replyPacket.encode();

        OutputStream out = clientSocket.getOutputStream();
        out.write(encodedPacket);
        out.flush();

        logger.debug("Sent TCP MD reply: ComID={}, SeqNo={}",
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

        try {
            tcpListener.close();
        } catch (IOException e) {
            logger.error("Error closing TCP listener", e);
        }

        udpTransport.close();
        logger.info("MD Replier closed");
    }
}
