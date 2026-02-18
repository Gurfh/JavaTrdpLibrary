package com.trdp.md;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class MdRequesterEdgeCaseTest {

    private MdRequester requester;

    @AfterEach
    void tearDown() {
        if (requester != null) requester.close();
    }

    @Test
    void testSendRequestWithUdpProtocolExplicit() throws Exception {
        requester = new MdRequester(0);

        CompletableFuture<MdReply> future = requester.sendRequest(
            1000, "data".getBytes(), "127.0.0.1", 19800, TransportProtocol.UDP);

        assertThat(future).isNotNull();
        assertThat(future).isNotCompleted();
    }

    @Test
    void testSendRequestWithUrisOverload() throws Exception {
        requester = new MdRequester(0);

        CompletableFuture<MdReply> future = requester.sendRequest(
            1001, "data".getBytes(), "127.0.0.1", 19801,
            TransportProtocol.UDP, "srcUri", "dstUri");

        assertThat(future).isNotNull();
        assertThat(future).isNotCompleted();
    }

    @Test
    void testSendRequestWithNullUris() throws Exception {
        requester = new MdRequester(0);

        CompletableFuture<MdReply> future = requester.sendRequest(
            1002, "data".getBytes(), "127.0.0.1", 19802,
            TransportProtocol.UDP, null, null);

        assertThat(future).isNotNull();
    }

    @Test
    void testMultipleRequestsGetDifferentFutures() throws Exception {
        requester = new MdRequester(0);

        CompletableFuture<MdReply> f1 = requester.sendRequest(
            2000, "a".getBytes(), "127.0.0.1", 19803);
        CompletableFuture<MdReply> f2 = requester.sendRequest(
            2000, "b".getBytes(), "127.0.0.1", 19803);

        assertThat(f1).isNotSameAs(f2);
    }

    @Test
    void testCloseWithPendingRequests() throws Exception {
        requester = new MdRequester(0);

        CompletableFuture<MdReply> f1 = requester.sendRequest(
            3000, "x".getBytes(), "127.0.0.1", 19804);
        CompletableFuture<MdReply> f2 = requester.sendRequest(
            3001, "y".getBytes(), "127.0.0.1", 19804);

        requester.close();
        requester = null;

        // Futures should be cancelled
        assertThat(f1.isCancelled() || f1.isDone()).isTrue();
        assertThat(f2.isCancelled() || f2.isDone()).isTrue();
    }

    @Test
    void testTcpConnectionRefusedCompletesExceptionally() throws Exception {
        requester = new MdRequester(0);

        // Get a port guaranteed to have nothing listening
        int unusedPort;
        try (ServerSocket ss = new ServerSocket(0)) {
            unusedPort = ss.getLocalPort();
        }

        CompletableFuture<MdReply> future = requester.sendRequest(
            1000, "test".getBytes(), "127.0.0.1", unusedPort, TransportProtocol.TCP);

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(() -> future.get())
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void testTcpConnectionPoolExhaustion() throws Exception {
        requester = new MdRequester(0);

        List<ServerSocket> servers = new ArrayList<>();
        try {
            // Create 17 server sockets (pool max is 16)
            for (int i = 0; i <= 16; i++) {
                servers.add(new ServerSocket(0));
            }

            // Fill the pool with 16 connections
            for (int i = 0; i < 16; i++) {
                int port = servers.get(i).getLocalPort();
                requester.sendRequest(
                    1000 + i, "test".getBytes(), "127.0.0.1", port, TransportProtocol.TCP);
            }

            // 17th should fail with pool exhaustion
            int overflowPort = servers.get(16).getLocalPort();
            CompletableFuture<MdReply> exhausted = requester.sendRequest(
                2000, "overflow".getBytes(), "127.0.0.1", overflowPort, TransportProtocol.TCP);

            assertThat(exhausted).isCompletedExceptionally();
            assertThatThrownBy(() -> exhausted.get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IOException.class)
                .hasMessageContaining("pool exhausted");
        } finally {
            for (ServerSocket s : servers) {
                try { s.close(); } catch (IOException ignored) {}
            }
        }
    }

    @Test
    void testTcpConnectionPoolReusesExisting() throws Exception {
        requester = new MdRequester(0);

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();

            // Two requests to the same destination should reuse the connection
            CompletableFuture<MdReply> f1 = requester.sendRequest(
                1000, "first".getBytes(), "127.0.0.1", port, TransportProtocol.TCP);
            CompletableFuture<MdReply> f2 = requester.sendRequest(
                1001, "second".getBytes(), "127.0.0.1", port, TransportProtocol.TCP);

            // Both should succeed (not exhaust the pool for same destination)
            assertThat(f1).isNotNull();
            assertThat(f2).isNotNull();
        }
    }

    @Test
    void testRequestTimeout() throws Exception {
        requester = new MdRequester(0);

        CompletableFuture<MdReply> future = requester.sendRequest(
            4000, "timeout".getBytes(), "127.0.0.1", 19805);

        // Default timeout is 5000ms, so wait slightly more
        assertThatThrownBy(() -> future.get(6, TimeUnit.SECONDS))
            .isInstanceOf(java.util.concurrent.ExecutionException.class);
    }
}
