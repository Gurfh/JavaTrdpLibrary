package com.trdp.md;

import com.trdp.network.ReceivedPacket;
import com.trdp.network.UdpTransport;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpMdHeader;
import com.trdp.protocol.TrdpMessageType;
import com.trdp.protocol.TrdpPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IEC 61375-2-3 compliance tests for {@link MdReplier}: notifications (Mn)
 * must never be replied to, and repeated requests (retries reusing the
 * session UUID) must not re-invoke the handler.
 */
class MdReplierProtocolTest {

    private MdReplier replier;
    private UdpTransport client;
    private final AtomicInteger handlerInvocations = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        client = new UdpTransport(0);
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (replier != null) replier.close();
    }

    private void startReplier(MdRequestHandler handler) throws Exception {
        replier = new MdReplier(0, handler, 1_000_000);
        replier.start();
    }

    private void sendToReplier(TrdpMessageType type, int comId, UUID sessionId, int seq, byte[] payload)
            throws Exception {
        TrdpMdHeader header = new TrdpMdHeader();
        header.setSequenceCounter(seq);
        header.setMessageType(type);
        header.setComId(comId);
        header.setSessionId(sessionId);
        byte[] packet = new TrdpPacket(header, payload).encode();
        client.send(packet, InetAddress.getLoopbackAddress(), replier.getUdpPort());
    }

    private TrdpPacket receiveReply(int timeoutMs) throws Exception {
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        ReceivedPacket received = client.receiveWithSource(buffer, timeoutMs);
        if (received == null) {
            return null;
        }
        return TrdpPacket.decode(Arrays.copyOf(received.getData(), received.getLength()));
    }

    @Test
    void notificationIsNeverReplied() throws Exception {
        // Handler misbehaves and returns a response for the notification
        startReplier(request -> {
            handlerInvocations.incrementAndGet();
            assertThat(request.isNotification()).isTrue();
            return new MdResponse("must-not-be-sent".getBytes());
        });

        sendToReplier(TrdpMessageType.MD_NOTIFICATION, 4100, UUID.randomUUID(), 0, "notify".getBytes());

        assertThat(receiveReply(700)).as("Mn must not be replied to").isNull();
        assertThat(handlerInvocations.get()).isEqualTo(1);
    }

    @Test
    void duplicateRequestRepeatsCachedReplyWithoutReinvokingHandler() throws Exception {
        startReplier(request -> {
            handlerInvocations.incrementAndGet();
            return new MdResponse("pong".getBytes());
        });

        UUID sessionId = UUID.randomUUID();
        sendToReplier(TrdpMessageType.MD_REQUEST, 4200, sessionId, 0, "ping".getBytes());

        TrdpPacket firstReply = receiveReply(2000);
        assertThat(firstReply).isNotNull();
        assertThat(firstReply.getHeader().getMessageType()).isEqualTo(TrdpMessageType.MD_REPLY);
        assertThat(firstReply.getPayload()).isEqualTo("pong".getBytes());

        // Retry: same session UUID, incremented sequence counter (per Table A.24)
        sendToReplier(TrdpMessageType.MD_REQUEST, 4200, sessionId, 1, "ping".getBytes());

        TrdpPacket repeatedReply = receiveReply(2000);
        assertThat(repeatedReply).as("duplicate must be answered with the cached reply").isNotNull();
        assertThat(repeatedReply.getPayload()).isEqualTo("pong".getBytes());
        assertThat(repeatedReply.getHeader().getSequenceCounter())
                .isEqualTo(firstReply.getHeader().getSequenceCounter());

        assertThat(handlerInvocations.get()).as("handler must run once per session").isEqualTo(1);
    }

    @Test
    void duplicateWhileHandlerInProgressIsDropped() throws Exception {
        startReplier(request -> {
            handlerInvocations.incrementAndGet();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new MdResponse("slow-pong".getBytes());
        });

        UUID sessionId = UUID.randomUUID();
        sendToReplier(TrdpMessageType.MD_REQUEST, 4300, sessionId, 0, "ping".getBytes());
        // Duplicate arrives while the handler is still processing the original
        sendToReplier(TrdpMessageType.MD_REQUEST, 4300, sessionId, 1, "ping".getBytes());

        TrdpPacket reply = receiveReply(3000);
        assertThat(reply).isNotNull();
        assertThat(reply.getPayload()).isEqualTo("slow-pong".getBytes());

        assertThat(receiveReply(700)).as("in-progress duplicate must not produce a second reply").isNull();
        assertThat(handlerInvocations.get()).isEqualTo(1);
    }

    @Test
    void distinctSessionsEachInvokeHandler() throws Exception {
        startReplier(request -> {
            handlerInvocations.incrementAndGet();
            return new MdResponse("pong".getBytes());
        });

        sendToReplier(TrdpMessageType.MD_REQUEST, 4400, UUID.randomUUID(), 0, "a".getBytes());
        assertThat(receiveReply(2000)).isNotNull();

        sendToReplier(TrdpMessageType.MD_REQUEST, 4400, UUID.randomUUID(), 0, "b".getBytes());
        assertThat(receiveReply(2000)).isNotNull();

        assertThat(handlerInvocations.get()).isEqualTo(2);
    }
}
