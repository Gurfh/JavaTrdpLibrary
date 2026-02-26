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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class PdPublisher implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(PdPublisher.class);
    
    private final UdpTransport transport;
    private final int comId;
    private final InetAddress destinationAddress;
    private final int destinationPort;
    private final long intervalUs;
    private final AtomicInteger sequenceCounter;

    private final AtomicLong packetsSent = new AtomicLong(0);
    private final AtomicLong sendErrors = new AtomicLong(0);

    // Pull Pattern Support
    private final AtomicReference<byte[]> currentData;
    private final ExecutorService executor;
    private ScheduledExecutorService scheduler;
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
     * Creates a PD Publisher with optional Pull pattern support (no cyclic send).
     *
     * @param comId The ComID to publish.
     * @param destinationAddress The default destination for Push messages (Multicast/Unicast).
     * @param destinationPort The default destination port for Push messages.
     * @param listeningPort The local port to listen on for Pull requests. If 0, an ephemeral port is used.
     */
    public PdPublisher(int comId, String destinationAddress, int destinationPort, int listeningPort) throws IOException {
        this(comId, destinationAddress, destinationPort, listeningPort, 0);
    }

    /**
     * Creates a PD Publisher with optional Pull pattern support and cyclic send.
     *
     * @param comId The ComID to publish.
     * @param destinationAddress The default destination for Push messages (Multicast/Unicast).
     * @param destinationPort The default destination port for Push messages.
     * @param listeningPort The local port to listen on for Pull requests. If 0, an ephemeral port is used.
     * @param intervalUs The cyclic send interval in microseconds. 0 means no cyclic send (PULL-only or manual send).
     */
    public PdPublisher(int comId, String destinationAddress, int destinationPort, int listeningPort, long intervalUs) throws IOException {
        if (intervalUs < 0) {
            throw new IllegalArgumentException("intervalUs must be >= 0");
        }
        this.comId = comId;
        this.destinationAddress = InetAddress.getByName(destinationAddress);
        this.destinationPort = destinationPort;
        this.intervalUs = intervalUs;
        this.transport = new UdpTransport(listeningPort);
        this.sequenceCounter = new AtomicInteger(0);
        this.currentData = new AtomicReference<>(new byte[0]);

        // Executor for handling incoming requests (Pull pattern)
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PD-Publisher-Listener-" + comId);
            t.setDaemon(true);
            return t;
        });

        logger.info("PD Publisher created for ComID {} (Push to {}:{}), listening on port {}, interval={}us",
                    comId, destinationAddress, destinationPort, transport.getLocalPort(), intervalUs);
    }

    public void setTopologyCounters(int etbTopoCnt, int opTrnTopoCnt) {
        this.etbTopoCnt = etbTopoCnt;
        this.opTrnTopoCnt = opTrnTopoCnt;
    }
    
    /**
     * Starts the listener for Pull requests and the cyclic send scheduler (if intervalUs &gt; 0).
     */
    public void start() {
        if (running) return;
        running = true;
        executor.submit(this::requestListenerLoop);

        if (intervalUs > 0) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "PD-Publisher-Cyclic-" + comId);
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleAtFixedRate(this::cyclicSend, intervalUs, intervalUs, TimeUnit.MICROSECONDS);
        }
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
     * Does not reset the cyclic timer.
     */
    public void putDataImmediate(byte[] data) throws IOException {
        putData(data);
        sendPd(data, destinationAddress, destinationPort, TrdpMessageType.PD, 0);
    }

    private void cyclicSend() {
        byte[] data = currentData.get();
        if (data.length == 0) return;
        try {
            sendPd(data, destinationAddress, destinationPort, TrdpMessageType.PD, 0);
        } catch (IOException e) {
            sendErrors.incrementAndGet();
            logger.error("Cyclic PD send failed for ComID {}", comId, e);
        }
    }

    /**
     * Returns the cyclic send interval in microseconds. 0 means no cyclic send.
     */
    public long getIntervalUs() {
        return intervalUs;
    }

    private synchronized void sendPd(byte[] data, InetAddress destAddr, int destPort, TrdpMessageType type, int forcedComId) throws IOException {
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
        packetsSent.incrementAndGet();
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
            sendErrors.incrementAndGet();
            logger.error("Failed to process incoming PD packet in Publisher", e);
        }
    }
    
    public long getPacketsSent() {
        return packetsSent.get();
    }

    public long getSendErrors() {
        return sendErrors.get();
    }

    public void resetStatistics() {
        packetsSent.set(0);
        sendErrors.set(0);
    }

    @Override
    public void close() {
        running = false;
        transport.close();
        executor.shutdownNow();
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
            if (scheduler != null) {
                scheduler.awaitTermination(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logger.info("PD Publisher closed for ComID {}", comId);
    }
}
