package com.trdp.md;

import com.trdp.network.TcpTransport;
import com.trdp.network.UdpTransport;
import com.trdp.network.ReceivedPacket;

import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpMdHeader;
import com.trdp.protocol.TrdpMessageType;
import com.trdp.protocol.TrdpPacket;
import com.trdp.util.TrdpTopologyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class MdRequester implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MdRequester.class);
    private static final int MAX_TCP_CONNECTIONS = 16;

    private final UdpTransport udpTransport;
    private final ConcurrentHashMap<String, TcpTransport> tcpConnections;
    private final List<Thread> listenerThreads = new CopyOnWriteArrayList<>();
    private final AtomicInteger sequenceCounter;

    // Map SessionID (UUID) to Future, as per IEC 61375-2-3 A.7.8.1
    private final Map<UUID, CompletableFuture<MdReply>> pendingSessions;

    // Map SessionID to TcpTransport for TCP confirmation routing
    private final Map<UUID, TcpTransport> tcpSessionTransports;

    private final long replyTimeoutUs;
    private final long connectTimeoutUs;
    private final ConcurrentHashMap<String, Long> tcpLastUsedNanos;
    private final ScheduledExecutorService tcpIdleEvictor;
    private volatile ScheduledFuture<?> pendingEviction;

    private volatile boolean running;
    private final CountDownLatch listenerReadyLatch = new CountDownLatch(1);

    private int actualEtbTopoCnt = 0;
    private int actualOpTrnTopoCnt = 0;

    public MdRequester(int localPort) throws IOException {
        this(localPort, TrdpConstants.DEFAULT_MD_REPLY_TIMEOUT_US);
    }

    public MdRequester(int localPort, long replyTimeoutUs) throws IOException {
        this(localPort, replyTimeoutUs, TrdpConstants.DEFAULT_MD_CONNECT_TIMEOUT_US);
    }

    public MdRequester(int localPort, long replyTimeoutUs, long connectTimeoutUs) throws IOException {
        this.replyTimeoutUs = replyTimeoutUs;
        this.connectTimeoutUs = connectTimeoutUs;
        this.udpTransport = new UdpTransport(localPort);
        this.tcpConnections = new ConcurrentHashMap<>();
        this.tcpLastUsedNanos = new ConcurrentHashMap<>();
        this.sequenceCounter = new AtomicInteger(0);
        this.pendingSessions = new ConcurrentHashMap<>();
        this.tcpSessionTransports = new ConcurrentHashMap<>();
        this.running = true;

        if (connectTimeoutUs > 0) {
            this.tcpIdleEvictor = createTcpIdleEvictor();
        } else {
            this.tcpIdleEvictor = null;
        }

        startUdpReplyListener();

        try {
            if (!listenerReadyLatch.await(5, TimeUnit.SECONDS)) {
                running = false;
                shutdownEvictor();
                udpTransport.close();
                throw new IOException("MD Requester listener thread failed to start in time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
            shutdownEvictor();
            udpTransport.close();
            throw new IOException("Interrupted while waiting for listener to start", e);
        }

        logger.info("MD Requester created on port {}", localPort);
    }

    public void setTopologyCounters(int etbTopoCnt, int opTrnTopoCnt) {
        this.actualEtbTopoCnt = etbTopoCnt;
        this.actualOpTrnTopoCnt = opTrnTopoCnt;
    }

    public long getReplyTimeoutUs() {
        return replyTimeoutUs;
    }

    public long getConnectTimeoutUs() {
        return connectTimeoutUs;
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
        return sendRequest(comId, data, destinationAddress, destinationPort, protocol, sourceUri, destinationUri, 0);
    }

    public CompletableFuture<MdReply> sendRequest(int comId, byte[] data,
                                                   String destinationAddress, int destinationPort,
                                                   TransportProtocol protocol,
                                                   String sourceUri, String destinationUri,
                                                   long perRequestReplyTimeoutUs) {

        if (data.length > TrdpConstants.TRDP_MAX_MD_DATA_SIZE) {
            CompletableFuture<MdReply> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Data size exceeds maximum MD data size"));
            return future;
        }

        long effectiveTimeoutUs = perRequestReplyTimeoutUs > 0 ? perRequestReplyTimeoutUs : this.replyTimeoutUs;

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
            header.setReplyTimeout((int) effectiveTimeoutUs);

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
                tcpSessionTransports.put(sessionId, tcpTransport);
                tcpTransport.send(encodedPacket);
            }

            logger.debug("Sent MD request: ComID={}, SessionID={}, Dest={}:{}",
                       comId, sessionId, destinationAddress, destinationPort);

            if (effectiveTimeoutUs > 0) {
                long effectiveTimeoutMs = effectiveTimeoutUs / 1000;
                future.orTimeout(effectiveTimeoutMs, TimeUnit.MILLISECONDS)
                      .whenComplete((reply, ex) -> {
                          if (ex != null) {
                              pendingSessions.remove(sessionId);
                              tcpSessionTransports.remove(sessionId);
                              if (ex instanceof TimeoutException) {
                                  logger.warn("MD request timeout: SessionID={}", sessionId);
                              }
                          }
                      });
            }

        } catch (IOException e) {
            future.completeExceptionally(e);
            pendingSessions.remove(sessionId);
            tcpSessionTransports.remove(sessionId);
        }

        return future;
    }

    private void startUdpReplyListener() {
        int pollTimeoutMs = (int) Math.min(replyTimeoutUs / 1000, 5000);
        if (pollTimeoutMs <= 0) {
            pollTimeoutMs = 5000;
        }
        final int soTimeout = pollTimeoutMs;

        Thread listener = new Thread(() -> {
            byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
            listenerReadyLatch.countDown();

            while (running) {
                try {
                    ReceivedPacket packet = udpTransport.receiveWithSource(buffer, soTimeout);
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
        listenerThreads.add(listener);
        listener.start();
    }

    private void startTcpReplyListener(TcpTransport tcpTransport) {
        int pollTimeoutMs = (int) Math.min(replyTimeoutUs / 1000, 5000);
        if (pollTimeoutMs <= 0) {
            pollTimeoutMs = 5000;
        }
        final int soTimeout = pollTimeoutMs;

        Thread listener = new Thread(() -> {
            try {
                tcpTransport.setSoTimeout(soTimeout);
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

                            // Consume 4-byte alignment padding
                            int padding = (4 - (datasetLen % 4)) % 4;
                            if (padding > 0) {
                                in.readFully(new byte[padding]);
                            }
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
        listenerThreads.add(listener);
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
            if (!TrdpTopologyUtils.isValidTopology(actualEtbTopoCnt, actualOpTrnTopoCnt, header.getEtbTopoCnt(), header.getOpTrnTopoCnt())) {
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
                tcpSessionTransports.remove(sessionId);

            } else if (type == TrdpMessageType.MD_ERROR) {
                // Me -> Exception
                future.completeExceptionally(new RuntimeException("TRDP Error received. Status: " + header.getReplyStatus()));
                pendingSessions.remove(sessionId);
                tcpSessionTransports.remove(sessionId);
            }

        } catch (Exception e) {
            logger.error("Error processing MD reply packet", e);
        }
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

            UUID sessionId = replyHeader.getSessionIdAsUuid();
            if (destAddress != null) {
                udpTransport.send(confirmPacket.encode(), destAddress, destPort);
                logger.debug("Sent MD Confirmation (Mc) via UDP for SessionID: {}", sessionId);
            } else {
                // TCP: send confirmation on the same TCP connection used for the request
                TcpTransport tcpTransport = tcpSessionTransports.get(sessionId);
                if (tcpTransport != null && !tcpTransport.isClosed()) {
                    tcpTransport.send(confirmPacket.encode());
                    logger.debug("Sent MD Confirmation (Mc) via TCP for SessionID: {}", sessionId);
                } else {
                    logger.warn("No TCP connection available for Mc: SessionID={}", sessionId);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to send MD confirmation", e);
        }
    }

    private ScheduledExecutorService createTcpIdleEvictor() {
        ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "MD-Requester-TCP-Evictor");
            t.setDaemon(true);
            return t;
        });
        exec.prestartCoreThread();
        return exec;
    }

    private void scheduleEvictionIfNeeded() {
        if (tcpIdleEvictor == null) return;
        ScheduledFuture<?> existing = pendingEviction;
        if (existing == null || existing.isDone()) {
            pendingEviction = tcpIdleEvictor.schedule(
                this::runEvictionAndReschedule,
                connectTimeoutUs * 1000, TimeUnit.NANOSECONDS);
        }
    }

    private void runEvictionAndReschedule() {
        long now = System.nanoTime();
        long connectTimeoutNanos = connectTimeoutUs * 1000;
        long earliestExpiry = Long.MAX_VALUE;

        for (Map.Entry<String, Long> entry : tcpLastUsedNanos.entrySet()) {
            String key = entry.getKey();
            long lastUsed = entry.getValue();
            if (now - lastUsed > connectTimeoutNanos) {
                TcpTransport transport = tcpConnections.remove(key);
                tcpLastUsedNanos.remove(key);
                if (transport != null) {
                    try {
                        transport.close();
                        logger.debug("Evicted idle TCP connection: {}", key);
                    } catch (IOException e) {
                        logger.error("Error closing evicted TCP connection: {}", key, e);
                    }
                }
            } else {
                long expiry = lastUsed + connectTimeoutNanos;
                if (expiry < earliestExpiry) {
                    earliestExpiry = expiry;
                }
            }
        }

        // Reschedule for the earliest remaining expiry, or go quiescent
        if (earliestExpiry < Long.MAX_VALUE) {
            long delayNanos = Math.max(earliestExpiry - System.nanoTime(), 0);
            try {
                pendingEviction = tcpIdleEvictor.schedule(
                    this::runEvictionAndReschedule,
                    delayNanos, TimeUnit.NANOSECONDS);
            } catch (RejectedExecutionException e) {
                // Executor shut down during reschedule — normal during close()
            }
        } else {
            pendingEviction = null;
        }
    }

    private synchronized TcpTransport getOrCreateTcpConnection(String host, int port) throws IOException {
        String key = host + ":" + port;

        // Evict closed/stale connections
        tcpConnections.entrySet().removeIf(entry -> entry.getValue().isClosed());

        // Reuse existing healthy connection
        TcpTransport existing = tcpConnections.get(key);
        if (existing != null && !existing.isClosed()) {
            tcpLastUsedNanos.put(key, System.nanoTime());
            scheduleEvictionIfNeeded();
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
        tcpLastUsedNanos.put(key, System.nanoTime());
        scheduleEvictionIfNeeded();
        startTcpReplyListener(newTransport);
        logger.debug("TCP connection pool: added {} ({}/{})", key, tcpConnections.size(), MAX_TCP_CONNECTIONS);
        return newTransport;
    }

    private void shutdownEvictor() {
        ScheduledFuture<?> f = pendingEviction;
        if (f != null) f.cancel(false);
        if (tcpIdleEvictor != null) {
            tcpIdleEvictor.shutdownNow();
        }
    }

    @Override
    public void close() {
        running = false;

        // 1. Cancel pending sessions
        pendingSessions.values().forEach(f -> f.cancel(true));
        pendingSessions.clear();
        tcpSessionTransports.clear();

        // 2. Shutdown TCP idle evictor
        shutdownEvictor();

        // 3. Close all I/O resources first to unblock listener threads
        tcpConnections.values().forEach(transport -> {
            try {
                transport.close();
            } catch (IOException e) {
                logger.error("Error closing TCP connection", e);
            }
        });
        tcpConnections.clear();
        tcpLastUsedNanos.clear();
        udpTransport.close();

        // 4. Wait for listener threads to finish (should be fast now that sockets are closed)
        for (Thread t : listenerThreads) {
            try {
                t.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        listenerThreads.clear();

        logger.info("MD Requester closed");
    }
}
