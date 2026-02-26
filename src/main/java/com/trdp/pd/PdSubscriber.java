package com.trdp.pd;

import com.trdp.network.ReceivedPacket;
import com.trdp.network.UdpTransport;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpMessageType;
import com.trdp.protocol.TrdpPacket;
import com.trdp.protocol.TrdpPdHeader;
import com.trdp.util.TrdpTopologyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PdSubscriber implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(PdSubscriber.class);

    private record SourceKey(InetAddress sourceAddress, int comId, TrdpMessageType messageType) {}

    private final UdpTransport transport;
    private final int comId;
    private final long timeoutUs;
    private final CopyOnWriteArrayList<PdEventListener> listeners;
    private final ExecutorService executor;
    private final AtomicInteger requestSequenceCounter;
    private volatile boolean running;

    private int etbTopoCnt = 0;
    private int opTrnTopoCnt = 0;

    private volatile boolean timedOut = false;
    private volatile long lastReceivedTimeNanos;
    private volatile InetAddress lastSourceAddress;

    private final ConcurrentHashMap<SourceKey, Integer> lastSequenceCounters = new ConcurrentHashMap<>();
    private final AtomicLong missedCount = new AtomicLong(0);
    private final AtomicLong duplicateCount = new AtomicLong(0);
    private final AtomicLong topoErrorCount = new AtomicLong(0);
    private final AtomicLong packetsReceived = new AtomicLong(0);
    private final AtomicLong timeoutCount = new AtomicLong(0);

    public PdSubscriber(int comId, String address, int port) throws IOException {
        this(comId, address, port, TrdpConstants.DEFAULT_PD_TIMEOUT_US);
    }

    public PdSubscriber(int comId, String address, int port, long timeoutUs) throws IOException {
        this.comId = comId;
        this.timeoutUs = timeoutUs;
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
        lastReceivedTimeNanos = System.nanoTime();

        int socketTimeoutMs = timeoutUs > 0
                ? Math.max(1, (int) (timeoutUs / 1000))
                : 1000;

        while (running) {
            try {
                ReceivedPacket received = transport.receiveWithSource(buffer, socketTimeoutMs);
                if (received != null) {
                    processReceivedData(received);
                }
                if (timeoutUs > 0) {
                    long elapsedUs = (System.nanoTime() - lastReceivedTimeNanos) / 1000;
                    if (elapsedUs > timeoutUs && !timedOut) {
                        handleTimeout();
                    }
                }
            } catch (IOException e) {
                if (running) {
                    logger.error("Error receiving PD data for ComID {}", comId, e);
                }
            }
        }
    }

    private void handleTimeout() {
        if (!timedOut) {
            timedOut = true;
            timeoutCount.incrementAndGet();
            lastSequenceCounters.clear();
            PdEvent event = new PdEvent(PdEvent.Type.TIMEOUT, comId, null, 0,
                    lastSourceAddress, null, 0, 0, 1);
            for (PdEventListener listener : listeners) {
                try {
                    listener.onTimeout(event);
                } catch (Exception e) {
                    logger.error("Error in PD timeout listener callback", e);
                }
            }
            logger.debug("PD timeout detected for ComID {}", comId);
        }
    }

    private void processReceivedData(ReceivedPacket received) {
        try {
            byte[] packetData = received.getData();

            TrdpPacket packet = TrdpPacket.decode(packetData);
            TrdpMessageType type = packet.getHeader().getMessageType();

            // Accept both PD (Push) and PD_REPLY (Pull)
            if (type != TrdpMessageType.PD && type != TrdpMessageType.PD_REPLY) {
                logger.debug("Received message type {}, ignoring in PD Subscriber", type);
                return;
            }

            // Topology Check (IEC 61375-2-3)
            TrdpPdHeader pdHeader = (TrdpPdHeader) packet.getHeader();
            if (!TrdpTopologyUtils.isValidTopology(etbTopoCnt, opTrnTopoCnt, pdHeader.getEtbTopoCnt(), pdHeader.getOpTrnTopoCnt())) {
                topoErrorCount.incrementAndGet();
                logger.debug("PD packet discarded: topology mismatch (Local ETB: {}, Rx ETB: {})",
                             etbTopoCnt, pdHeader.getEtbTopoCnt());
                return;
            }

            if (packet.getHeader().getComId() == comId) {
                packetsReceived.incrementAndGet();
                lastReceivedTimeNanos = System.nanoTime();
                lastSourceAddress = received.getSourceAddress();

                PdEvent.Type eventType = (type == TrdpMessageType.PD_REPLY)
                        ? PdEvent.Type.REPLY : PdEvent.Type.DATA;

                boolean wasTimedOut = timedOut;
                timedOut = false;

                if (wasTimedOut) {
                    PdEvent restoredEvent = new PdEvent(PdEvent.Type.VALIDITY_RESTORED, comId,
                            packet.getPayload(), pdHeader.getSequenceCounter(),
                            received.getSourceAddress(), null,
                            pdHeader.getReplyComId(), pdHeader.getReplyIpAddress(), 0);
                    for (PdEventListener listener : listeners) {
                        try {
                            listener.onValidityRestored(restoredEvent);
                        } catch (Exception e) {
                            logger.error("Error in PD validity-restored listener callback", e);
                        }
                    }
                    logger.debug("PD validity restored for ComID {}", comId);
                }

                SourceKey sourceKey = new SourceKey(received.getSourceAddress(), comId, type);
                if (!validateSequenceCounter(sourceKey, pdHeader.getSequenceCounter(), wasTimedOut)) {
                    return;
                }

                PdEvent event = new PdEvent(eventType, comId,
                        packet.getPayload(), pdHeader.getSequenceCounter(),
                        received.getSourceAddress(), null,
                        pdHeader.getReplyComId(), pdHeader.getReplyIpAddress(), 0);
                notifyListeners(event);
            }
        } catch (Exception e) {
            logger.error("Error processing received PD packet", e);
        }
    }

    private void notifyListeners(PdEvent event) {
        for (PdEventListener listener : listeners) {
            try {
                listener.onData(event);
            } catch (Exception e) {
                logger.error("Error in PD listener callback", e);
            }
        }
    }

    /**
     * Validates the sequence counter per IEC 61375-2-3 Table A.3.
     *
     * @return true if the packet should be accepted, false if it should be discarded
     */
    private boolean validateSequenceCounter(SourceKey sourceKey, int seqCnt, boolean wasTimedOut) {
        Integer lastSeqCnt = lastSequenceCounters.get(sourceKey);

        // First packet from this source, or seqCnt == 0 (sender restart), or subscriber was timed out
        if (lastSeqCnt == null || seqCnt == 0 || wasTimedOut) {
            lastSequenceCounters.put(sourceKey, seqCnt);
            return true;
        }

        int cmp = Integer.compareUnsigned(seqCnt, lastSeqCnt);
        if (cmp > 0) {
            // seqCnt > lastSeqCnt: accept, count gap as missed
            int gap = Integer.compareUnsigned(seqCnt, lastSeqCnt + 1) >= 0
                    ? seqCnt - lastSeqCnt - 1 : 0;
            if (gap > 0) {
                missedCount.addAndGet(gap);
                logger.debug("PD sequence gap for ComID {}: expected {}, got {} ({} missed)",
                        sourceKey.comId(), lastSeqCnt + 1, seqCnt, gap);
            }
            lastSequenceCounters.put(sourceKey, seqCnt);
            return true;
        } else {
            // seqCnt <= lastSeqCnt: duplicate or old packet
            duplicateCount.incrementAndGet();
            logger.debug("PD duplicate/old packet discarded for ComID {}: seqCnt={}, lastSeqCnt={}",
                    sourceKey.comId(), Integer.toUnsignedString(seqCnt), Integer.toUnsignedString(lastSeqCnt));
            return false;
        }
    }

    public long getMissedCount() {
        return missedCount.get();
    }

    public long getDuplicateCount() {
        return duplicateCount.get();
    }

    public long getTopoErrorCount() {
        return topoErrorCount.get();
    }

    public long getPacketsReceived() {
        return packetsReceived.get();
    }

    public long getTimeoutCount() {
        return timeoutCount.get();
    }

    public void resetStatistics() {
        missedCount.set(0);
        duplicateCount.set(0);
        topoErrorCount.set(0);
        packetsReceived.set(0);
        timeoutCount.set(0);
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public void addListener(PdEventListener listener) {
        listeners.add(listener);
        logger.debug("Added listener to PD Subscriber for ComID {}", comId);
    }

    public void removeListener(PdEventListener listener) {
        listeners.remove(listener);
        logger.debug("Removed listener from PD Subscriber for ComID {}", comId);
    }

    @Override
    public void close() {
        running = false;
        transport.close();
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("PD Subscriber closed for ComID {}", comId);
    }
}
