package com.trdp.md;

import com.trdp.network.ReceivedPacket;
import com.trdp.network.UdpTransport;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Dispatches incoming datagrams from a single shared UDP socket to an
 * {@link MdRequester} and an {@link MdReplier} by TRDP message type.
 * <p>
 * IEC 61375-2-3 uses one well-known MD port (17225) for all message data, so
 * a device that both initiates and answers requests over UDP must share one
 * socket: reply types (Mp, Mq, Me) are routed to the requester; request,
 * notification, and confirm types (Mr, Mn, Mc) to the replier. Create the
 * endpoints with {@link MdRequester#forSharedTransport} and
 * {@link MdReplier#forSharedTransport}, then {@link #start()} the dispatcher.
 * <p>
 * The dispatcher owns the shared transport and closes it on {@link #close()};
 * requester and replier must be created in shared mode so they do not close
 * it themselves. Packets for the replier are dropped until
 * {@link MdReplier#start()} has been called.
 */
public class MdUdpDispatcher implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MdUdpDispatcher.class);
    private static final int POLL_TIMEOUT_MS = 1000;

    private final UdpTransport transport;
    private final MdRequester requester;
    private final MdReplier replier;
    private final Thread receiveThread;
    private volatile boolean running;

    public MdUdpDispatcher(UdpTransport transport, MdRequester requester, MdReplier replier) {
        this.transport = transport;
        this.requester = requester;
        this.replier = replier;
        this.receiveThread = new Thread(this::receiveLoop,
                "MD-UDP-Dispatcher-" + transport.getLocalPort());
        this.receiveThread.setDaemon(true);
    }

    /**
     * Starts the shared receive loop.
     */
    public void start() {
        if (running) return;
        running = true;
        receiveThread.start();
        logger.info("MD UDP dispatcher started on port {}", transport.getLocalPort());
    }

    /**
     * Returns the local port of the shared UDP socket.
     */
    public int getPort() {
        return transport.getLocalPort();
    }

    private void receiveLoop() {
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        while (running) {
            try {
                ReceivedPacket received = transport.receiveWithSource(buffer, POLL_TIMEOUT_MS);
                if (received != null) {
                    route(received);
                }
            } catch (IOException e) {
                if (running) {
                    logger.error("MD dispatcher receive error on port {}", transport.getLocalPort(), e);
                }
            }
        }
    }

    private void route(ReceivedPacket received) {
        if (received.getLength() < TrdpConstants.TRDP_MD_HEADER_SIZE) {
            logger.debug("MD dispatcher: dropped short packet ({} bytes)", received.getLength());
            return;
        }
        // Message type is at header offset 6 (Big Endian UINT16)
        byte[] data = received.getData();
        int typeCode = ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
        TrdpMessageType type;
        try {
            type = TrdpMessageType.fromCode(typeCode);
        } catch (IllegalArgumentException e) {
            logger.debug("MD dispatcher: dropped packet with unknown message type 0x{}",
                    Integer.toHexString(typeCode));
            return;
        }
        switch (type) {
            case MD_REPLY, MD_REPLY_CONFIRM, MD_ERROR -> requester.dispatchUdp(received);
            case MD_REQUEST, MD_NOTIFICATION, MD_CONFIRM -> {
                if (replier.isStarted()) {
                    replier.dispatchUdp(received);
                } else {
                    logger.debug("MD dispatcher: dropped {} — replier not started", type);
                }
            }
            default -> logger.debug("MD dispatcher: dropped non-MD packet type {}", type);
        }
    }

    @Override
    public void close() {
        running = false;
        transport.close();
        try {
            receiveThread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logger.info("MD UDP dispatcher closed");
    }
}
