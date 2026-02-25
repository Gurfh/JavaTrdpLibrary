package com.trdp.md;

import com.trdp.network.UdpTransport;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpMdHeader;
import com.trdp.protocol.TrdpPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;

class MdRequesterRetryTest {

    private MdRequester requester;
    private UdpTransport captureTransport;

    @AfterEach
    void tearDown() {
        if (requester != null) requester.close();
        if (captureTransport != null) captureTransport.close();
    }

    @Test
    void testMaxRetriesExceedsSpecLimit() throws Exception {
        requester = new MdRequester(0);

        CompletableFuture<MdReply> future = requester.sendRequest(
            8000, "test".getBytes(), "127.0.0.1", 19920,
            TransportProtocol.UDP, null, null, 0, 3);

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(() -> future.get())
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testMaxRetriesNegative() throws Exception {
        requester = new MdRequester(0);

        CompletableFuture<MdReply> future = requester.sendRequest(
            8001, "test".getBytes(), "127.0.0.1", 19921,
            TransportProtocol.UDP, null, null, 0, -1);

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(() -> future.get())
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testUdpRetrySendsCorrectPackets() throws Exception {
        captureTransport = new UdpTransport(19922);
        requester = new MdRequester(0, 200_000); // 200ms per retry

        CompletableFuture<MdReply> future = requester.sendRequest(
            8002, "retry".getBytes(), "127.0.0.1", 19922,
            TransportProtocol.UDP, null, null, 0, 2);

        // Capture all 3 packets (1 original + 2 retries)
        List<TrdpMdHeader> headers = new ArrayList<>();
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        for (int i = 0; i < 3; i++) {
            var received = captureTransport.receiveWithSource(buffer, 2000);
            assertThat(received).as("Expected packet %d", i + 1).isNotNull();
            byte[] data = new byte[received.getLength()];
            System.arraycopy(received.getData(), 0, data, 0, received.getLength());
            headers.add((TrdpMdHeader) TrdpPacket.decode(data).getHeader());
        }

        assertThat(headers).hasSize(3);

        // All packets must share the same session UUID
        UUID sessionId = headers.get(0).getSessionIdAsUuid();
        assertThat(headers.get(1).getSessionIdAsUuid()).isEqualTo(sessionId);
        assertThat(headers.get(2).getSessionIdAsUuid()).isEqualTo(sessionId);

        // Sequence counters must increment
        assertThat(headers.get(1).getSequenceCounter()).isGreaterThan(headers.get(0).getSequenceCounter());
        assertThat(headers.get(2).getSequenceCounter()).isGreaterThan(headers.get(1).getSequenceCounter());

        // Future completes exceptionally with timeout after all retries
        assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void testTcpIgnoresMaxRetries() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            requester = new MdRequester(0, 300_000); // 300ms

            CompletableFuture<MdReply> future = requester.sendRequest(
                8005, "tcp".getBytes(), "127.0.0.1", port,
                TransportProtocol.TCP, null, null, 0, 2);

            long start = System.nanoTime();
            assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            // TCP should timeout in ~300ms, not 900ms (no retries)
            assertThat(elapsedMs)
                .as("TCP should not retry")
                .isLessThan(800);
        }
    }

    @Test
    void testExplicitZeroRetriesDisablesRetry() throws Exception {
        requester = new MdRequester(0, 300_000); // 300ms

        long start = System.nanoTime();
        CompletableFuture<MdReply> future = requester.sendRequest(
            8006, "no-retry".getBytes(), "127.0.0.1", 19925,
            TransportProtocol.UDP, null, null, 0, 0);

        assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // Should timeout in ~300ms, not 900ms
        assertThat(elapsedMs)
            .as("0 retries should timeout after single attempt")
            .isLessThan(800);
    }

    @Test
    void testTotalTimeoutWithRetries() throws Exception {
        requester = new MdRequester(0, 200_000); // 200ms per attempt

        long start = System.nanoTime();
        CompletableFuture<MdReply> future = requester.sendRequest(
            8007, "timing".getBytes(), "127.0.0.1", 19926,
            TransportProtocol.UDP, null, null, 0, 2);

        assertThatThrownBy(() -> future.get(3, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(TimeoutException.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // Total: (2+1) * 200ms = 600ms
        assertThat(elapsedMs)
            .as("Total timeout should be ~600ms (3 x 200ms)")
            .isBetween(400L, 1200L);
    }
}
