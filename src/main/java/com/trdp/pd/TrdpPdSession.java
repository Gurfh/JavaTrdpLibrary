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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * High-performance PD session manager that shares a single UDP socket and
 * a minimal number of threads across multiple publishers and subscribers.
 * <p>
 * Manages multiple PD publishers and subscribers on a single UDP socket
 * with a single receive thread and shared cyclic send scheduler (2 threads
 * and 1 socket total).
 * <p>
 * Usage:
 * <pre>{@code
 * try (TrdpPdSession session = new TrdpPdSession(17224)) {
 *     PdPublisherHandle pub = session.addPublisher(1000, "239.255.0.1", 17224, 100_000);
 *     PdSubscriberHandle sub = session.addSubscriber(2000, "239.255.0.1", 100_000, listener);
 *     session.start();
 *     pub.putData(data);
 *     // Dynamic add/remove after start:
 *     PdPublisherHandle pub2 = session.addPublisher(1001, "239.255.0.1", 17224, 50_000);
 *     session.removePublisher(1001);
 * }
 * }</pre>
 * <p>
 * Registration methods ({@link #addPublisher}, {@link #addSubscriber}) can be called
 * before or after {@link #start()}. Publishers added after start are not traffic-shaped.
 * {@link #removePublisher(int)} and {@link #removeSubscribers(int)} allow removal at any time.
 * Callbacks run on the receive thread.
 */
public class TrdpPdSession implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(TrdpPdSession.class);

    private record SourceKey(InetAddress sourceAddress, int comId, TrdpMessageType messageType) {}

    private final UdpTransport transport;
    private final int port;
    private volatile boolean running;
    private volatile boolean started;
    private boolean trafficShapingEnabled = true;

    // Publisher registry: ComId -> entry (one publisher per ComId)
    private final ConcurrentHashMap<Integer, PublisherEntry> publishers = new ConcurrentHashMap<>();

    // Subscriber registry: ComId -> list of entries (multiple subscribers per ComId allowed)
    private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<SubscriberEntry>> subscribers = new ConcurrentHashMap<>();

    // Multicast groups already joined (avoid duplicate joins)
    private final Set<InetAddress> joinedGroups = ConcurrentHashMap.newKeySet();

    // Shared cyclic send scheduler (created in start(), used by dynamic addPublisher)
    private volatile ScheduledExecutorService sendScheduler;

    // Receive thread
    private final ExecutorService receiveExecutor;

    // Topology counters (session-wide)
    private volatile int etbTopoCnt;
    private volatile int opTrnTopoCnt;

    // Session-level FCS error counter
    private final AtomicLong fcsErrorCount = new AtomicLong();

    /**
     * Creates a PD session on the specified port.
     *
     * @param port The UDP port to bind to. Use 0 for an ephemeral port.
     * @throws IOException If socket creation fails.
     */
    public TrdpPdSession(int port) throws IOException {
        this(port, null, 64, 5);
    }

    /**
     * Creates a PD session with custom socket options.
     *
     * @param port        The UDP port to bind to. Use 0 for an ephemeral port.
     * @param bindAddress The local address to bind to, or {@code null} for wildcard.
     * @param ttl         The IP time-to-live for outgoing packets.
     * @param qos         The QoS value (IP Precedence 0..7).
     * @throws IOException If socket creation fails.
     */
    public TrdpPdSession(int port, InetAddress bindAddress, int ttl, int qos) throws IOException {
        this.port = port;
        this.transport = new UdpTransport(port, bindAddress, ttl, UdpTransport.qosToTrafficClass(qos));
        this.receiveExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PD-Session-Recv-" + transport.getLocalPort());
            t.setDaemon(true);
            return t;
        });
        logger.info("PD Session created on port {}", transport.getLocalPort());
    }

    /**
     * Registers a publisher for the given ComId.
     * Can be called before or after {@link #start()}. Publishers added after start
     * use their interval as initial delay (no traffic shaping stagger).
     *
     * @param comId The ComID to publish.
     * @param destinationAddress The destination address for push/cyclic sends.
     * @param destinationPort The destination port for push/cyclic sends.
     * @param intervalUs The cyclic send interval in microseconds. 0 means no cyclic send.
     * @return A handle for staging and sending data.
     * @throws IllegalArgumentException If a publisher for this ComId already exists, or intervalUs is negative.
     */
    public PdPublisherHandle addPublisher(int comId, String destinationAddress,
                                          int destinationPort, long intervalUs) throws IOException {
        if (intervalUs < 0) {
            throw new IllegalArgumentException("intervalUs must be >= 0");
        }

        PublisherEntry entry = new PublisherEntry(comId, InetAddress.getByName(destinationAddress),
                destinationPort, intervalUs);
        if (publishers.putIfAbsent(comId, entry) != null) {
            throw new IllegalArgumentException("Publisher already registered for ComId " + comId);
        }

        if (started && entry.intervalUs > 0) {
            entry.cyclicTask = sendScheduler.scheduleAtFixedRate(
                    () -> cyclicSend(entry),
                    entry.intervalUs, entry.intervalUs, TimeUnit.MICROSECONDS);
        }

        logger.info("PD Session: added publisher ComID {} -> {}:{}, interval={}us",
                comId, destinationAddress, destinationPort, intervalUs);
        return entry;
    }

    /**
     * Registers a subscriber for the given ComId.
     * Can be called before or after {@link #start()}.
     *
     * @param comId The ComID to subscribe to.
     * @param multicastGroup The multicast group to join, or null for unicast-only.
     * @param timeoutUs Timeout in microseconds (0 to disable timeout detection).
     * @param listener The listener to receive callbacks.
     * @return A handle for querying status and statistics.
     */
    public PdSubscriberHandle addSubscriber(int comId, String multicastGroup,
                                            long timeoutUs, PdEventListener listener) throws IOException {
        // Join multicast group if needed (deduplicated)
        if (multicastGroup != null) {
            InetAddress groupAddr = InetAddress.getByName(multicastGroup);
            if (groupAddr.isMulticastAddress() && joinedGroups.add(groupAddr)) {
                transport.joinMulticastGroup(groupAddr);
                logger.info("PD Session: joined multicast group {}", multicastGroup);
            }
        }

        SubscriberEntry entry = new SubscriberEntry(comId, timeoutUs, listener);
        if (started) {
            entry.lastReceivedTimeNanos = System.nanoTime();
        }
        subscribers.computeIfAbsent(comId, k -> new CopyOnWriteArrayList<>()).add(entry);

        logger.info("PD Session: added subscriber ComID {}, timeout={}us", comId, timeoutUs);
        return entry;
    }

    /**
     * Removes the publisher for the given ComId.
     *
     * @param comId The ComID of the publisher to remove.
     * @return The removed publisher handle, or {@code null} if no publisher existed for this ComId.
     */
    public PdPublisherHandle removePublisher(int comId) {
        PublisherEntry entry = publishers.remove(comId);
        if (entry != null) {
            if (entry.cyclicTask != null) {
                entry.cyclicTask.cancel(false);
            }
            logger.info("PD Session: removed publisher ComID {}", comId);
        }
        return entry;
    }

    /**
     * Removes all subscribers for the given ComId.
     *
     * @param comId The ComID of the subscribers to remove.
     * @return The list of removed subscriber handles, or an empty list if none existed.
     */
    public List<PdSubscriberHandle> removeSubscribers(int comId) {
        CopyOnWriteArrayList<SubscriberEntry> removed = subscribers.remove(comId);
        if (removed != null && !removed.isEmpty()) {
            logger.info("PD Session: removed {} subscriber(s) for ComID {}", removed.size(), comId);
            return List.copyOf(removed);
        }
        return List.of();
    }

    /**
     * Sets topology counters for the entire session (all publishers and subscribers).
     */
    public void setTopologyCounters(int etbTopoCnt, int opTrnTopoCnt) {
        this.etbTopoCnt = etbTopoCnt;
        this.opTrnTopoCnt = opTrnTopoCnt;
    }

    /**
     * Enables or disables traffic shaping for this session.
     * When enabled, cyclic publishers sharing the same interval have their
     * initial delays staggered evenly across the interval window, preventing
     * network bursts. Must be called before {@link #start()}.
     *
     * @param enabled {@code true} to enable traffic shaping (default), {@code false} to disable.
     * @throws IllegalStateException If the session has already been started.
     */
    public void setTrafficShapingEnabled(boolean enabled) {
        if (started) {
            throw new IllegalStateException("Cannot change traffic shaping after session has started");
        }
        this.trafficShapingEnabled = enabled;
    }

    /**
     * Returns whether traffic shaping is enabled for this session.
     */
    public boolean isTrafficShapingEnabled() {
        return trafficShapingEnabled;
    }

    /**
     * Starts the session: begins cyclic sends and the receive loop.
     * Publishers and subscribers may still be added or removed after this call.
     *
     * @throws IllegalStateException If the session has already been started.
     */
    public void start() {
        if (started) {
            throw new IllegalStateException("Session already started");
        }
        started = true;
        running = true;

        // Always create send scheduler (supports dynamic addPublisher after start)
        sendScheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "PD-Session-Send-" + transport.getLocalPort());
            t.setDaemon(true);
            return t;
        });
        Map<Integer, Long> initialDelays = computeInitialDelays();
        for (PublisherEntry entry : publishers.values()) {
            if (entry.intervalUs > 0) {
                long initialDelay = initialDelays.getOrDefault(entry.comId, entry.intervalUs);
                entry.cyclicTask = sendScheduler.scheduleAtFixedRate(
                        () -> cyclicSend(entry),
                        initialDelay, entry.intervalUs, TimeUnit.MICROSECONDS);
            }
        }

        // Initialize subscriber timeout tracking
        long now = System.nanoTime();
        for (var list : subscribers.values()) {
            for (SubscriberEntry entry : list) {
                entry.lastReceivedTimeNanos = now;
            }
        }

        // Start receive thread
        receiveExecutor.submit(this::receiveLoop);
        logger.info("PD Session started on port {} ({} publishers, {} subscribers)",
                transport.getLocalPort(), publishers.size(), getSubscriberCount());
    }

    /**
     * Returns the number of registered publishers.
     */
    public int getPublisherCount() {
        return publishers.size();
    }

    /**
     * Returns the total number of registered subscribers across all ComIds.
     */
    public int getSubscriberCount() {
        return subscribers.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Returns the local port this session is bound to.
     */
    public int getPort() {
        return transport.getLocalPort();
    }

    /**
     * Returns the number of packets rejected due to FCS (CRC) validation failure.
     */
    public long getFcsErrorCount() {
        return fcsErrorCount.get();
    }

    @Override
    public void close() {
        running = false;

        // 1. Cancel all cyclic tasks
        for (PublisherEntry entry : publishers.values()) {
            if (entry.cyclicTask != null) {
                entry.cyclicTask.cancel(false);
            }
        }

        // 2. Shutdown send scheduler
        if (sendScheduler != null) {
            sendScheduler.shutdownNow();
        }

        // 3. Close transport (unblocks receive thread)
        transport.close();

        // 4. Shutdown receive executor
        receiveExecutor.shutdownNow();

        // 5. Await termination
        try {
            receiveExecutor.awaitTermination(2, TimeUnit.SECONDS);
            if (sendScheduler != null) {
                sendScheduler.awaitTermination(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.info("PD Session closed on port {}", transport.getLocalPort());
    }

    /**
     * Computes staggered initial delays for cyclic publishers.
     * Package-private for testing.
     */
    Map<Integer, Long> computeInitialDelays() {
        Map<Integer, Long> delays = new HashMap<>();

        Map<Long, List<PublisherEntry>> byInterval = publishers.values().stream()
                .filter(e -> e.intervalUs > 0)
                .collect(Collectors.groupingBy(e -> e.intervalUs));

        for (var group : byInterval.values()) {
            long interval = group.get(0).intervalUs;
            int n = group.size();
            long offset = interval / n;
            boolean stagger = trafficShapingEnabled && (2 * offset <= interval);

            int index = 0;
            for (PublisherEntry entry : group) {
                long initialDelay = stagger ? offset * index : interval;
                delays.put(entry.comId, initialDelay);
                index++;
            }
        }
        return delays;
    }

    // --- Receive loop ---

    private void receiveLoop() {
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        int socketTimeoutMs = computeSocketTimeout();

        while (running) {
            try {
                ReceivedPacket received = transport.receiveWithSource(buffer, socketTimeoutMs);

                if (received != null) {
                    processReceivedPacket(received);
                }

                checkSubscriberTimeouts();
            } catch (IOException e) {
                if (running) {
                    logger.error("PD Session receive error on port {}", transport.getLocalPort(), e);
                }
            }
        }
    }

    private int computeSocketTimeout() {
        // Use half of the minimum subscriber timeout, clamped to [10, 1000] ms
        long minTimeoutUs = subscribers.values().stream()
                .flatMap(List::stream)
                .mapToLong(e -> e.timeoutUs)
                .filter(t -> t > 0)
                .min()
                .orElse(1_000_000); // 1s default if no timeouts configured
        return Math.max(10, Math.min(1000, (int) (minTimeoutUs / 2000)));
    }

    private void processReceivedPacket(ReceivedPacket received) {
        try {
            byte[] packetData = received.getData();
            TrdpPacket packet = TrdpPacket.decode(packetData);
            TrdpPdHeader header = (TrdpPdHeader) packet.getHeader();
            TrdpMessageType type = header.getMessageType();
            int comId = header.getComId();

            if (type == TrdpMessageType.PD_REQUEST) {
                // Dispatch to matching publisher for pull reply
                PublisherEntry pub = publishers.get(comId);
                if (pub != null) {
                    handlePullRequest(pub, header, received);
                }
            } else if (type == TrdpMessageType.PD || type == TrdpMessageType.PD_REPLY) {
                // Dispatch to matching subscribers
                CopyOnWriteArrayList<SubscriberEntry> entries = subscribers.get(comId);
                if (entries != null) {
                    for (SubscriberEntry entry : entries) {
                        processForSubscriber(entry, packet, header, received);
                    }
                }
            }
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("FCS mismatch")) {
                fcsErrorCount.incrementAndGet();
                logger.debug("PD Session: FCS error on port {}", transport.getLocalPort());
            } else {
                logger.error("PD Session: error processing packet on port {}", transport.getLocalPort(), e);
            }
        } catch (Exception e) {
            logger.error("PD Session: error processing packet on port {}", transport.getLocalPort(), e);
        }
    }

    // --- Subscriber processing ---

    private void processForSubscriber(SubscriberEntry entry, TrdpPacket packet,
                                      TrdpPdHeader header, ReceivedPacket received) {
        // Topology check
        if (!TrdpTopologyUtils.isValidTopology(etbTopoCnt, opTrnTopoCnt,
                header.getEtbTopoCnt(), header.getOpTrnTopoCnt())) {
            entry.topoErrorCount.incrementAndGet();
            return;
        }

        entry.packetsReceived.incrementAndGet();
        entry.lastReceivedTimeNanos = System.nanoTime();
        entry.lastSourceAddress = received.getSourceAddress();

        TrdpMessageType type = header.getMessageType();
        PdEvent.Type eventType = (type == TrdpMessageType.PD_REPLY)
                ? PdEvent.Type.REPLY : PdEvent.Type.DATA;

        boolean wasTimedOut = entry.timedOut;
        entry.timedOut = false;

        if (wasTimedOut) {
            PdEvent restoredEvent = new PdEvent(PdEvent.Type.VALIDITY_RESTORED, entry.comId,
                    packet.getPayload(), header.getSequenceCounter(),
                    received.getSourceAddress(), null,
                    header.getReplyComId(), header.getReplyIpAddress(), 0);
            try {
                entry.listener.onValidityRestored(restoredEvent);
            } catch (Exception e) {
                logger.error("Error in PD session validity-restored callback", e);
            }
        }

        SourceKey sourceKey = new SourceKey(received.getSourceAddress(), entry.comId, type);
        if (!validateSequenceCounter(entry, sourceKey, header.getSequenceCounter(), wasTimedOut)) {
            return;
        }

        PdEvent event = new PdEvent(eventType, entry.comId,
                packet.getPayload(), header.getSequenceCounter(),
                received.getSourceAddress(), null,
                header.getReplyComId(), header.getReplyIpAddress(), 0);
        try {
            entry.listener.onData(event);
        } catch (Exception e) {
            logger.error("Error in PD session data callback", e);
        }
    }

    private boolean validateSequenceCounter(SubscriberEntry entry, SourceKey sourceKey,
                                            int seqCnt, boolean wasTimedOut) {
        Integer lastSeqCnt = entry.lastSequenceCounters.get(sourceKey);

        // First packet from this source, or seqCnt == 0 (sender restart), or subscriber was timed out
        if (lastSeqCnt == null || seqCnt == 0 || wasTimedOut) {
            entry.lastSequenceCounters.put(sourceKey, seqCnt);
            return true;
        }

        int cmp = Integer.compareUnsigned(seqCnt, lastSeqCnt);
        if (cmp > 0) {
            // seqCnt > lastSeqCnt: accept, count gap as missed
            int gap = Integer.compareUnsigned(seqCnt, lastSeqCnt + 1) >= 0
                    ? seqCnt - lastSeqCnt - 1 : 0;
            if (gap > 0) {
                entry.missedCount.addAndGet(gap);
            }
            entry.lastSequenceCounters.put(sourceKey, seqCnt);
            return true;
        } else {
            // seqCnt <= lastSeqCnt: duplicate or old packet
            entry.duplicateCount.incrementAndGet();
            return false;
        }
    }

    // --- Timeout checking ---

    private void checkSubscriberTimeouts() {
        long now = System.nanoTime();
        for (var entryList : subscribers.values()) {
            for (SubscriberEntry entry : entryList) {
                if (entry.timeoutUs > 0 && !entry.timedOut) {
                    long elapsedUs = (now - entry.lastReceivedTimeNanos) / 1000;
                    if (elapsedUs > entry.timeoutUs) {
                        entry.timedOut = true;
                        entry.timeoutCount.incrementAndGet();
                        entry.lastSequenceCounters.clear();
                        PdEvent event = new PdEvent(PdEvent.Type.TIMEOUT, entry.comId, null, 0,
                                entry.lastSourceAddress, null, 0, 0, 1);
                        try {
                            entry.listener.onTimeout(event);
                        } catch (Exception e) {
                            logger.error("Error in PD session timeout callback", e);
                        }
                    }
                }
            }
        }
    }

    // --- Pull pattern ---

    private void handlePullRequest(PublisherEntry pub, TrdpPdHeader requestHeader,
                                   ReceivedPacket received) {
        try {
            // Determine reply address
            InetAddress replyAddr;
            if (requestHeader.getReplyIpAddress() != 0) {
                byte[] ipBytes = ByteBuffer.allocate(4).putInt(requestHeader.getReplyIpAddress()).array();
                replyAddr = InetAddress.getByAddress(ipBytes);
            } else {
                replyAddr = received.getSourceAddress();
            }

            int replyPort = received.getSourcePort();
            int replyComId = (requestHeader.getReplyComId() != 0)
                    ? requestHeader.getReplyComId() : pub.comId;

            sendPd(pub, pub.currentData.get(), replyAddr, replyPort,
                    TrdpMessageType.PD_REPLY, replyComId);
        } catch (Exception e) {
            pub.sendErrors.incrementAndGet();
            logger.error("PD Session: error handling pull request for ComID {}", pub.comId, e);
        }
    }

    // --- Send ---

    private void cyclicSend(PublisherEntry entry) {
        byte[] data = entry.currentData.get();
        if (data.length == 0) return;
        try {
            sendPd(entry, data, entry.destinationAddress, entry.destinationPort,
                    TrdpMessageType.PD, 0);
        } catch (IOException e) {
            entry.sendErrors.incrementAndGet();
            logger.error("PD Session: cyclic send failed for ComID {}", entry.comId, e);
        }
    }

    private synchronized void sendPd(PublisherEntry entry, byte[] data,
                                     InetAddress destAddr, int destPort,
                                     TrdpMessageType type, int forcedComId) throws IOException {
        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(entry.sequenceCounter.getAndIncrement());
        header.setMessageType(type);
        header.setComId(forcedComId != 0 ? forcedComId : entry.comId);
        header.setEtbTopoCnt(etbTopoCnt);
        header.setOpTrnTopoCnt(opTrnTopoCnt);
        header.setDatasetLength(data.length);

        TrdpPacket packet = new TrdpPacket(header, data);
        byte[] encodedPacket = packet.encode();

        transport.send(encodedPacket, destAddr, destPort);
        entry.packetsSent.incrementAndGet();
        logger.debug("PD Session sent {} to {}:{}: ComID={}, SeqNo={}, Size={}",
                type, destAddr.getHostAddress(), destPort,
                header.getComId(), header.getSequenceCounter(), data.length);
    }

    // --- Inner classes ---

    private class PublisherEntry implements PdPublisherHandle {
        final int comId;
        final InetAddress destinationAddress;
        final int destinationPort;
        final long intervalUs;
        final AtomicInteger sequenceCounter = new AtomicInteger(0);
        final AtomicReference<byte[]> currentData = new AtomicReference<>(new byte[0]);
        volatile ScheduledFuture<?> cyclicTask;
        final AtomicLong packetsSent = new AtomicLong();
        final AtomicLong sendErrors = new AtomicLong();

        PublisherEntry(int comId, InetAddress destinationAddress, int destinationPort, long intervalUs) {
            this.comId = comId;
            this.destinationAddress = destinationAddress;
            this.destinationPort = destinationPort;
            this.intervalUs = intervalUs;
        }

        @Override
        public void putData(byte[] data) {
            if (data.length > TrdpConstants.TRDP_MAX_PD_DATA_SIZE) {
                throw new IllegalArgumentException("Data size exceeds maximum PD data size");
            }
            currentData.set(Arrays.copyOf(data, data.length));
        }

        @Override
        public void putDataImmediate(byte[] data) throws IOException {
            if (intervalUs > 0) {
                throw new IllegalStateException(
                        "putDataImmediate() cannot be used on cyclic publishers (intervalUs=" + intervalUs
                                + "). Use putData() to stage data for cyclic sending.");
            }
            putData(data);
            sendPd(this, Arrays.copyOf(data, data.length), destinationAddress, destinationPort,
                    TrdpMessageType.PD, 0);
        }

        @Override
        public int getComId() {
            return comId;
        }

        @Override
        public long getIntervalUs() {
            return intervalUs;
        }

        @Override
        public long getPacketsSent() {
            return packetsSent.get();
        }

        @Override
        public long getSendErrors() {
            return sendErrors.get();
        }

        @Override
        public void resetStatistics() {
            packetsSent.set(0);
            sendErrors.set(0);
        }
    }

    private static class SubscriberEntry implements PdSubscriberHandle {
        final int comId;
        final long timeoutUs;
        final PdEventListener listener;

        volatile long lastReceivedTimeNanos;
        volatile boolean timedOut;
        volatile InetAddress lastSourceAddress;

        final ConcurrentHashMap<SourceKey, Integer> lastSequenceCounters = new ConcurrentHashMap<>();
        final AtomicLong missedCount = new AtomicLong();
        final AtomicLong duplicateCount = new AtomicLong();
        final AtomicLong topoErrorCount = new AtomicLong();
        final AtomicLong packetsReceived = new AtomicLong();
        final AtomicLong timeoutCount = new AtomicLong();

        SubscriberEntry(int comId, long timeoutUs, PdEventListener listener) {
            this.comId = comId;
            this.timeoutUs = timeoutUs;
            this.listener = listener;
        }

        @Override
        public int getComId() {
            return comId;
        }

        @Override
        public boolean isTimedOut() {
            return timedOut;
        }

        @Override
        public long getMissedCount() {
            return missedCount.get();
        }

        @Override
        public long getDuplicateCount() {
            return duplicateCount.get();
        }

        @Override
        public long getTopoErrorCount() {
            return topoErrorCount.get();
        }

        @Override
        public long getPacketsReceived() {
            return packetsReceived.get();
        }

        @Override
        public long getTimeoutCount() {
            return timeoutCount.get();
        }

        @Override
        public void resetStatistics() {
            missedCount.set(0);
            duplicateCount.set(0);
            topoErrorCount.set(0);
            packetsReceived.set(0);
            timeoutCount.set(0);
        }
    }
}
