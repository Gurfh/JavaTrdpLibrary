package com.trdp.pd;

import com.trdp.network.ReceivedPacket;
import com.trdp.network.UdpTransport;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpHeader;
import com.trdp.protocol.TrdpPdHeader;
import com.trdp.protocol.TrdpMessageType;
import com.trdp.protocol.TrdpPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class PdPublisher implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(PdPublisher.class);
    
    private final UdpTransport transport;
    private final int comId;
    private final InetAddress destinationAddress;
    private final int destinationPort;
    private final AtomicInteger sequenceCounter;
    
    // Pull Pattern Support
    private final AtomicReference<byte[]> currentData;
    private final ExecutorService executor;
    private volatile boolean running;
    private int etbTopoCnt = 0;
    private int opTrnTopoCnt = 0;
    
    /**
     * Creates a PD Publisher for Push pattern only (legacy constructor).
     */
    public PdPublisher(int comId, String destinationAddress, int destinationPort) throws IOException {
        this(comId, destinationAddress, destinationPort, 0);
    }

    /**
     * Creates a PD Publisher with optional Pull pattern support.
     * * @param comId The ComID to publish.
     * @param destinationAddress The default destination for Push messages (Multicast/Unicast).
     * @param destinationPort The default destination port for Push messages.
     * @param listeningPort The local port to listen on for Pull requests. If 0, an ephemeral port is used (Pull unlikely to work unless Requester knows this dynamic port).
     */
    public PdPublisher(int comId, String destinationAddress, int destinationPort, int listeningPort) throws IOException {
        this.comId = comId;
        this.destinationAddress = InetAddress.getByName(destinationAddress);
        this.destinationPort = destinationPort;
        this.transport = new UdpTransport(listeningPort);
        this.sequenceCounter = new AtomicInteger(0);
        this.currentData = new AtomicReference<>(new byte[0]);
        
        // Executor for handling incoming requests (Pull pattern)
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PD-Publisher-Listener-" + comId);
            t.setDaemon(true);
            return t;
        });

        logger.info("PD Publisher created for ComID {} (Push to {}:{}), listening on port {}", 
                    comId, destinationAddress, destinationPort, transport.getLocalPort());
    }

    public void setTopologyCounters(int etbTopoCnt, int opTrnTopoCnt) {
        this.etbTopoCnt = etbTopoCnt;
        this.opTrnTopoCnt = opTrnTopoCnt;
    }
    
    /**
     * Starts the listener for Pull requests.
     */
    public void start() {
        if (running) return;
        running = true;
        executor.submit(this::requestListenerLoop);
    }

    /**
     * Updates the current process data without sending a Push message.
     * Used for preparing data for the Pull pattern or cyclic Push.
     */
    public void putData(byte[] data) {
        if (data.length > TrdpConstants.TRDP_MAX_PD_DATA_SIZE) {
            throw new IllegalArgumentException("Data size exceeds maximum PD data size");
        }
        currentData.set(Arrays.copyOf(data, data.length));
    }

    /**
     * Updates the data and immediately sends it to the configured Push destination.
     */
    public void publish(byte[] data) throws IOException {
        putData(data);
        sendPd(data, destinationAddress, destinationPort, TrdpMessageType.PD, 0);
    }
    
    private void sendPd(byte[] data, InetAddress destAddr, int destPort, TrdpMessageType type, int forcedComId) throws IOException {
        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(sequenceCounter.getAndIncrement());
        header.setMessageType(type);
        header.setComId(forcedComId != 0 ? forcedComId : comId);
        header.setEtbTopoCnt(etbTopoCnt);
        header.setOpTrnTopoCnt(opTrnTopoCnt);
        header.setDatasetLength(data.length);
        
        TrdpPacket packet = new TrdpPacket(header, data);
        byte[] encodedPacket = packet.encode();
        
        transport.send(encodedPacket, destAddr, destPort);
        logger.debug("Sent PD message ({}) to {}:{}: ComID={}, SeqNo={}, Size={}", 
                    type, destAddr.getHostAddress(), destPort, header.getComId(), header.getSequenceCounter(), data.length);
    }

    private void requestListenerLoop() {
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        while (running) {
            try {
                ReceivedPacket packet = transport.receiveWithSource(buffer, 1000);
                if (packet != null) {
                    processRequest(packet);
                }
            } catch (IOException e) {
                if (running) logger.error("Error receiving PD request", e);
            }
        }
    }

    private void processRequest(ReceivedPacket packet) {
        try {
            // Decode packet to check if it's a PD Request for our ComID
            byte[] dataCopy = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), 0, dataCopy, 0, packet.getLength());
            
            TrdpPacket trdpPacket = TrdpPacket.decode(dataCopy);
            TrdpHeader header = trdpPacket.getHeader();

            if (header.getMessageType() == TrdpMessageType.PD_REQUEST && header.getComId() == comId) {
                // Topology check could be implemented here (IEC 61375-2-3 A.6.7)
                
                TrdpPdHeader pdHeader = (TrdpPdHeader) header;
                
                // Determine destination IP
                InetAddress replyAddr;
                if (pdHeader.getReplyIpAddress() != 0) {
                    byte[] ipBytes = ByteBuffer.allocate(4).putInt(pdHeader.getReplyIpAddress()).array();
                    replyAddr = InetAddress.getByAddress(ipBytes);
                } else {
                    replyAddr = packet.getSourceAddress();
                }
                
                // Determine destination port (Usually same as source for UDP req/rep)
                int replyPort = packet.getSourcePort(); 
                // Note: If multicast reply is requested via IP, the port must be known/agreed. 
                // Standard doesn't explicitly pass reply port in header, implies usage of configured ports or source port.
                
                // Determine Reply ComID
                int replyComId = (pdHeader.getReplyComId() != 0) ? pdHeader.getReplyComId() : comId;

                // Send Reply
                sendPd(currentData.get(), replyAddr, replyPort, TrdpMessageType.PD_REPLY, replyComId);
            }
        } catch (Exception e) {
            logger.warn("Failed to process incoming PD packet in Publisher", e);
        }
    }
    
    @Override
    public void close() {
        running = false;
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        transport.close();
        logger.info("PD Publisher closed for ComID {}", comId);
    }
}
