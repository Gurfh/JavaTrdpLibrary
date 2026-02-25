package com.trdp.md;

import com.trdp.network.UdpTransport;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpMdHeader;
import com.trdp.protocol.TrdpMessageType;
import com.trdp.protocol.TrdpPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

class MdReplierConfirmTimeoutTest {

    private MdReplier replier;
    private UdpTransport clientTransport;

    @AfterEach
    void tearDown() {
        if (replier != null) replier.close();
        if (clientTransport != null) clientTransport.close();
    }

    @Test
    void testDefaultConfirmTimeout() throws Exception {
        replier = new MdReplier(0, req -> new MdResponse("ok".getBytes()));
        assertThat(replier.getConfirmTimeoutUs()).isEqualTo(1_000_000);
    }

    @Test
    void testCustomConfirmTimeout() throws Exception {
        replier = new MdReplier(0, req -> new MdResponse("ok".getBytes()), 500_000);
        assertThat(replier.getConfirmTimeoutUs()).isEqualTo(500_000);
    }

    @Test
    void testConfirmTimeoutExpiresSession() throws Exception {
        // Use a short confirm timeout (300ms) so the test runs quickly
        replier = new MdReplier(0, req -> new MdResponse("reply".getBytes(), true));
        int replierPort = replier.getConfirmTimeoutUs() > 0 ? 0 : 0; // just for clarity
        replier.start();

        // We need the actual port the replier is listening on.
        // Send a request that triggers MD_REPLY_CONFIRM, but don't send Mc.
        // The replier's port is the ServerSocket port, but UDP also listens on it.
        // We'll use a known port approach.
        replier.close();

        // Recreate with known port and short timeout
        int port = 19950;
        replier = new MdReplier(port, req -> new MdResponse("reply".getBytes(), true), 300_000);
        replier.start();
        Thread.sleep(100);

        clientTransport = new UdpTransport(0);

        // Send MD_REQUEST
        UUID sessionId = UUID.randomUUID();
        TrdpMdHeader reqHeader = new TrdpMdHeader();
        reqHeader.setSequenceCounter(1);
        reqHeader.setMessageType(TrdpMessageType.MD_REQUEST);
        reqHeader.setComId(9000);
        reqHeader.setSessionId(sessionId);
        reqHeader.setReplyTimeout(5_000_000);

        TrdpPacket reqPacket = new TrdpPacket(reqHeader, "test".getBytes());
        clientTransport.send(reqPacket.encode(), InetAddress.getLoopbackAddress(), port);

        // Wait for the replier to process and send MD_REPLY_CONFIRM
        Thread.sleep(200);

        // Pending confirmation should exist
        assertThat(replier.getPendingConfirmationCount())
            .as("Should have 1 pending confirmation after sending Mq")
            .isEqualTo(1);

        // Wait for confirm timeout to expire (300ms + checker interval)
        Thread.sleep(500);

        assertThat(replier.getPendingConfirmationCount())
            .as("Pending confirmation should be expired after timeout")
            .isEqualTo(0);
    }

    @Test
    void testConfirmReceivedClearsSession() throws Exception {
        int port = 19951;
        replier = new MdReplier(port, req -> new MdResponse("reply".getBytes(), true), 5_000_000);
        replier.start();
        Thread.sleep(100);

        clientTransport = new UdpTransport(0);

        // Send MD_REQUEST
        UUID sessionId = UUID.randomUUID();
        TrdpMdHeader reqHeader = new TrdpMdHeader();
        reqHeader.setSequenceCounter(1);
        reqHeader.setMessageType(TrdpMessageType.MD_REQUEST);
        reqHeader.setComId(9001);
        reqHeader.setSessionId(sessionId);
        reqHeader.setReplyTimeout(5_000_000);

        TrdpPacket reqPacket = new TrdpPacket(reqHeader, "test".getBytes());
        clientTransport.send(reqPacket.encode(), InetAddress.getLoopbackAddress(), port);

        // Wait for the replier to process
        Thread.sleep(200);

        assertThat(replier.getPendingConfirmationCount())
            .as("Should have 1 pending confirmation")
            .isEqualTo(1);

        // Now send MD_CONFIRM
        TrdpMdHeader confirmHeader = new TrdpMdHeader();
        confirmHeader.setSequenceCounter(1);
        confirmHeader.setMessageType(TrdpMessageType.MD_CONFIRM);
        confirmHeader.setComId(9001);
        confirmHeader.setSessionId(sessionId);

        TrdpPacket confirmPacket = new TrdpPacket(confirmHeader, new byte[0]);
        clientTransport.send(confirmPacket.encode(), InetAddress.getLoopbackAddress(), port);

        Thread.sleep(200);

        assertThat(replier.getPendingConfirmationCount())
            .as("Pending confirmation should be cleared after Mc received")
            .isEqualTo(0);
    }

    @Test
    void testConfirmTimeoutZeroDisablesTracking() throws Exception {
        replier = new MdReplier(0, req -> new MdResponse("ok".getBytes()), 0);
        replier.start();
        Thread.sleep(100);

        Set<String> checkerThreads = Thread.getAllStackTraces().keySet().stream()
            .map(Thread::getName)
            .filter(name -> name.startsWith("MD-Replier-Confirm-Timeout"))
            .collect(Collectors.toSet());

        assertThat(checkerThreads)
            .as("No confirm timeout checker when confirmTimeoutUs=0")
            .isEmpty();
    }
}
