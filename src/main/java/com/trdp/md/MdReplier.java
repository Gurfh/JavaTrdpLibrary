package com.trdp.md;

import com.trdp.network.ReceivedPacket;
import com.trdp.network.UdpTransport;
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
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MdReplier implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MdReplier.class);
    private static final int MAX_WORKER_THREADS = 16;
    private static final int WORKER_QUEUE_CAPACITY = 64;

    private final UdpTransport udpTransport;
    private final ServerSocket tcpListener;
    private final MdRequestHandler handler;
    private final ExecutorService executor; // Manages listener threads
    private final ExecutorService workerPool; // Manages user request processing
    private volatile boolean running;

    private final long confirmTimeoutUs;
    private final ConcurrentHashMap<UUID, Long> pendingConfirmations;
    private ScheduledExecutorService confirmTimeoutChecker;
    private volatile ScheduledFuture<?> pendingConfirmCheck;

    private int actualEtbTopoCnt = 0;
    private int actualOpTrnTopoCnt = 0;

    public MdReplier(int port, MdRequestHandler handler) throws IOException {
        this(port, handler, TrdpConstants.DEFAULT_MD_CONFIRM_TIMEOUT_US);
    }

    public MdReplier(int port, MdRequestHandler handler, long confirmTimeoutUs) throws IOException {
        this.confirmTimeoutUs = confirmTimeoutUs;
        this.pendingConfirmations = new ConcurrentHashMap<>();
        this.udpTransport = new UdpTransport(port);
        try {
            this.tcpListener = new ServerSocket(port);
        } catch (IOException e) {
            udpTransport.close();
            throw e;
        }
        this.handler = handler;

        // Listener threads (UDP + TCP Accept)
        this.executor = Executors.newFixedThreadPool(2);

        // Bounded worker pool with CallerRunsPolicy for backpressure:
        // when pool and queue are full, the submitting listener thread processes
        // the request itself, naturally throttling intake.
        this.workerPool = new ThreadPoolExecutor(
            2, MAX_WORKER_THREADS,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(WORKER_QUEUE_CAPACITY),
            new ThreadPoolExecutor.CallerRunsPolicy());

        logger.info("MD Replier created on port {}", port);
    }

    public void setTopologyCounters(int etbTopoCnt, int opTrnTopoCnt) {
        this.actualEtbTopoCnt = etbTopoCnt;
        this.actualOpTrnTopoCnt = opTrnTopoCnt;
    }

    public long getConfirmTimeoutUs() {
        return confirmTimeoutUs;
    }

    public int getPendingConfirmationCount() {
        return pendingConfirmations.size();
    }

    public void start() {
        if (running) return;
        running = true;
        executor.submit(this::udpReceiveLoop);
        executor.submit(this::tcpAcceptLoop);

        if (confirmTimeoutUs > 0) {
            createConfirmTimeoutChecker();
        }

        logger.info("MD Replier started");
    }

    // --- UDP Handling ---

    private void udpReceiveLoop() {
        int pollTimeoutMs = (int) Math.min(confirmTimeoutUs > 0 ? confirmTimeoutUs / 1000 : 5000, 5000);
        if (pollTimeoutMs <= 0) {
            pollTimeoutMs = 5000;
        }

        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        while (running) {
            try {
                ReceivedPacket received = udpTransport.receiveWithSource(buffer, pollTimeoutMs);
                if (received != null) {
                    // Copy data because the buffer is reused in the loop
                    byte[] dataCopy = Arrays.copyOf(received.getData(), received.getLength());

                    // Offload processing to worker pool
                    workerPool.submit(() ->
                        processRequest(dataCopy, received.getSourceAddress(), received.getSourcePort(), null)
                    );
                }
            } catch (IOException e) {
                if (running) logger.error("Error receiving UDP MD request", e);
            }
        }
    }

    // --- TCP Handling ---

    private void tcpAcceptLoop() {
        while (running) {
            try {
                Socket clientSocket = tcpListener.accept();
                // Handle each TCP connection in its own thread (managed by worker pool)
                workerPool.submit(() -> handleTcpConnection(clientSocket));
            } catch (SocketException e) {
                // Socket closed, normal shutdown
            } catch (IOException e) {
                if (running) logger.error("Error accepting TCP connection", e);
            }
        }
    }

    /**
     * Handles a persistent TCP connection.
     * Reads exact frame sizes to handle fragmentation/coalescing correctly.
     */
    private void handleTcpConnection(Socket clientSocket) {
        String remoteInfo = clientSocket.getRemoteSocketAddress().toString();
        logger.debug("New TCP connection from {}", remoteInfo);

        try (clientSocket;
             DataInputStream in = new DataInputStream(clientSocket.getInputStream())) {

            while (running && !clientSocket.isClosed()) {
                byte[] headerBytes = new byte[TrdpConstants.TRDP_MD_HEADER_SIZE];

                try {
                    in.readFully(headerBytes);
                } catch (EOFException e) {
                    break; // Connection closed by peer
                }

                // Decode Header to find payload length
                TrdpMdHeader header;
                try {
                    header = TrdpMdHeader.decode(headerBytes);
                } catch (Exception e) {
                    logger.warn("Invalid TRDP Header received from {}: {}", remoteInfo, e.getMessage());
                    break;
                }

                // Read Payload
                int datasetLen = header.getDatasetLength();
                byte[] payload = new byte[0];

                if (datasetLen > 0) {
                    if (datasetLen > TrdpConstants.TRDP_MAX_MD_DATA_SIZE) {
                         logger.warn("Oversized payload declared: {}", datasetLen);
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

                TrdpPacket packet = new TrdpPacket(header, payload);
                processRequestObject(packet, clientSocket.getInetAddress(), clientSocket.getPort(), clientSocket);
            }
        } catch (IOException e) {
            if (running) logger.debug("TCP connection ended with {}: {}", remoteInfo, e.getMessage());
        }
    }

    // --- Processing ---

    private void processRequestObject(TrdpPacket requestPacket, InetAddress sourceAddress, int sourcePort, Socket tcpSocket) {
        try {
            if (!(requestPacket.getHeader() instanceof TrdpMdHeader)) {
                return;
            }
            TrdpMdHeader reqHeader = (TrdpMdHeader) requestPacket.getHeader();

            // Topology Check (IEC 61375-2-3 A.7.7)
            if (!TrdpTopologyUtils.isValidTopology(actualEtbTopoCnt, actualOpTrnTopoCnt, reqHeader.getEtbTopoCnt(), reqHeader.getOpTrnTopoCnt())) {
                if (logger.isDebugEnabled()) {
                    logger.debug("MD Request discarded: Topo mismatch (Local ETB: {}, Rx ETB: {})",
                                 actualEtbTopoCnt, reqHeader.getEtbTopoCnt());
                }
                return;
            }

            if (reqHeader.getMessageType() == TrdpMessageType.MD_REQUEST ||
                reqHeader.getMessageType() == TrdpMessageType.MD_NOTIFICATION) {

                MdRequest request = new MdRequest(
                    reqHeader.getComId(),
                    requestPacket.getPayload(),
                    reqHeader.getSessionIdAsUuid(),
                    reqHeader.getSourceUriString(),
                    reqHeader.getDestinationUriString(),
                    sourceAddress,
                    sourcePort,
                    reqHeader.getSequenceCounter()
                );

                MdResponse response = handler.handleRequest(request);

                if (response != null) {
                    sendReply(reqHeader, response, sourceAddress, sourcePort, tcpSocket);
                }
            }
            else if (reqHeader.getMessageType() == TrdpMessageType.MD_CONFIRM) {
                UUID sessionId = reqHeader.getSessionIdAsUuid();
                Long sentTime = pendingConfirmations.remove(sessionId);
                if (sentTime != null) {
                    long elapsedMs = (System.nanoTime() - sentTime) / 1_000_000;
                    logger.debug("Received MD Confirmation (Mc) for SessionID: {} ({}ms)", sessionId, elapsedMs);
                } else {
                    logger.debug("Received MD Confirmation (Mc) for SessionID: {}", sessionId);
                }
            }

        } catch (Exception e) {
            logger.error("Error processing MD request", e);
        }
    }

    /**
     * Adapter for UDP loop which has raw bytes
     */
    private void processRequest(byte[] rawData, InetAddress sourceAddress, int sourcePort, Socket tcpSocket) {
        try {
            TrdpPacket packet = TrdpPacket.decode(rawData);
            processRequestObject(packet, sourceAddress, sourcePort, tcpSocket);
        } catch (Exception e) {
            logger.warn("Failed to decode UDP packet from {}", sourceAddress);
        }
    }

    private void sendReply(TrdpMdHeader reqHeader, MdResponse response,
                           InetAddress destAddress, int destPort, Socket tcpSocket) throws IOException {

        TrdpMdHeader replyHeader = new TrdpMdHeader();
        replyHeader.setSequenceCounter(reqHeader.getSequenceCounter());

        if (response.isConfirmationRequested()) {
            replyHeader.setMessageType(TrdpMessageType.MD_REPLY_CONFIRM);
        } else {
            replyHeader.setMessageType(TrdpMessageType.MD_REPLY);
        }

        replyHeader.setComId(response.getReplyComId() != 0 ? response.getReplyComId() : reqHeader.getComId());
        replyHeader.setSessionId(reqHeader.getSessionId());
        replyHeader.setReplyStatus(0);
        replyHeader.setSourceUri(reqHeader.getDestinationUriString());
        replyHeader.setDestinationUri(reqHeader.getSourceUriString());

        replyHeader.setEtbTopoCnt(actualEtbTopoCnt);
        replyHeader.setOpTrnTopoCnt(actualOpTrnTopoCnt);

        TrdpPacket replyPacket = new TrdpPacket(replyHeader, response.getData());
        byte[] encoded = replyPacket.encode();

        if (tcpSocket != null) {
            synchronized (tcpSocket) {
                OutputStream out = tcpSocket.getOutputStream();
                out.write(encoded);
                out.flush();
            }
        } else {
            udpTransport.send(encoded, destAddress, destPort);
        }

        // Track pending confirmation if Mq was sent
        if (response.isConfirmationRequested() && confirmTimeoutUs > 0) {
            long entryTime = System.nanoTime();
            pendingConfirmations.put(reqHeader.getSessionIdAsUuid(), entryTime);
            scheduleConfirmCheckIfNeeded(entryTime);
        }

        logger.debug("Sent MD Reply ({}): SessionID={}", replyHeader.getMessageType(), reqHeader.getSessionIdAsUuid());
    }

    private void createConfirmTimeoutChecker() {
        ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "MD-Replier-Confirm-Timeout");
            t.setDaemon(true);
            return t;
        });
        exec.prestartCoreThread();
        confirmTimeoutChecker = exec;
    }

    private void scheduleConfirmCheckIfNeeded(long entryNanos) {
        if (confirmTimeoutChecker == null) return;
        ScheduledFuture<?> existing = pendingConfirmCheck;
        if (existing == null || existing.isDone()) {
            long delayNanos = Math.max((entryNanos + confirmTimeoutUs * 1000) - System.nanoTime(), 0);
            pendingConfirmCheck = confirmTimeoutChecker.schedule(
                this::checkConfirmTimeoutsAndReschedule,
                delayNanos, TimeUnit.NANOSECONDS);
        }
    }

    private void checkConfirmTimeoutsAndReschedule() {
        long now = System.nanoTime();
        long timeoutNanos = confirmTimeoutUs * 1000;
        long earliestExpiry = Long.MAX_VALUE;

        Iterator<Map.Entry<UUID, Long>> it = pendingConfirmations.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            long elapsed = now - entry.getValue();
            if (elapsed > timeoutNanos) {
                it.remove();
                logger.warn("MD Confirmation timeout expired for SessionID: {}", entry.getKey());
            } else {
                long expiry = entry.getValue() + timeoutNanos;
                if (expiry < earliestExpiry) {
                    earliestExpiry = expiry;
                }
            }
        }

        // Reschedule for the earliest remaining expiry, or go quiescent
        if (earliestExpiry < Long.MAX_VALUE) {
            long delayNanos = Math.max(earliestExpiry - System.nanoTime(), 0);
            try {
                pendingConfirmCheck = confirmTimeoutChecker.schedule(
                    this::checkConfirmTimeoutsAndReschedule,
                    delayNanos, TimeUnit.NANOSECONDS);
            } catch (RejectedExecutionException e) {
                // Executor shut down during reschedule — normal during close()
            }
        } else {
            pendingConfirmCheck = null;
        }
    }

    @Override
    public void close() {
        running = false;

        // 1. Close I/O resources first to unblock listener threads
        try {
            tcpListener.close();
        } catch (IOException e) {
            logger.error("Error closing TCP listener", e);
        }
        udpTransport.close();

        // 2. Shutdown confirm timeout checker
        ScheduledFuture<?> f = pendingConfirmCheck;
        if (f != null) f.cancel(false);
        if (confirmTimeoutChecker != null) {
            confirmTimeoutChecker.shutdownNow();
        }
        pendingConfirmations.clear();

        // 3. Shutdown executors (threads should exit quickly now that sockets are closed)
        executor.shutdownNow();
        workerPool.shutdown();

        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            if (!workerPool.awaitTermination(2, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("MD Replier closed");
    }
}
