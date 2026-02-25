package com.trdp.integration;

import com.trdp.md.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class MdTimeoutIT {

    private MdRequester requester;
    private MdReplier replier;

    @AfterEach
    void tearDown() {
        if (requester != null) requester.close();
        if (replier != null) replier.close();
    }

    @Test
    void testCustomReplyTimeoutTooShort() throws Exception {
        int replierPort = 19960;

        // Replier that takes 300ms to respond
        replier = new MdReplier(replierPort, request -> {
            try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return new MdResponse("slow".getBytes());
        });
        replier.start();
        Thread.sleep(200);

        // Requester with 200ms timeout — should time out
        requester = new MdRequester(0, 200_000);

        CompletableFuture<MdReply> future = requester.sendRequest(
            10000, "test".getBytes(), "127.0.0.1", replierPort);

        assertThatThrownBy(() -> future.get(3, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class);
    }

    @Test
    void testCustomReplyTimeoutSufficient() throws Exception {
        int replierPort = 19961;

        // Replier that takes 100ms to respond
        replier = new MdReplier(replierPort, request -> {
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return new MdResponse("fast-enough".getBytes());
        });
        replier.start();
        Thread.sleep(200);

        // Requester with 2s timeout — should succeed
        requester = new MdRequester(0, 2_000_000);

        CompletableFuture<MdReply> future = requester.sendRequest(
            10001, "test".getBytes(), "127.0.0.1", replierPort);

        MdReply reply = future.get(5, TimeUnit.SECONDS);
        assertThat(reply).isNotNull();
        assertThat(reply.getData()).isEqualTo("fast-enough".getBytes());
    }

    @Test
    void testPerRequestTimeoutOverrideEndToEnd() throws Exception {
        int replierPort = 19962;

        // Replier that takes 400ms to respond
        replier = new MdReplier(replierPort, request -> {
            try { Thread.sleep(400); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return new MdResponse("delayed".getBytes());
        });
        replier.start();
        Thread.sleep(200);

        // Requester with 5s default — both requests use per-request override
        requester = new MdRequester(0, 5_000_000);

        // Request 1: 200ms timeout — should time out (reply takes 400ms)
        CompletableFuture<MdReply> fast = requester.sendRequest(
            10002, "a".getBytes(), "127.0.0.1", replierPort,
            TransportProtocol.UDP, null, null, 200_000);

        assertThatThrownBy(() -> fast.get(3, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class);

        // Request 2: 2s timeout — should succeed
        CompletableFuture<MdReply> slow = requester.sendRequest(
            10003, "b".getBytes(), "127.0.0.1", replierPort,
            TransportProtocol.UDP, null, null, 2_000_000);

        MdReply reply = slow.get(5, TimeUnit.SECONDS);
        assertThat(reply).isNotNull();
        assertThat(reply.getData()).isEqualTo("delayed".getBytes());
    }

    @Test
    void testTcpIdleEvictionEndToEnd() throws Exception {
        // 500ms connect timeout, demand-driven eviction
        requester = new MdRequester(0, 5_000_000, 500_000);

        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(5000);
            int port = server.getLocalPort();

            // First TCP request
            requester.sendRequest(10004, "first".getBytes(), "127.0.0.1", port, TransportProtocol.TCP);
            Socket conn1 = server.accept();

            // Wait for idle eviction: 500ms timeout + margin
            Thread.sleep(1500);

            // Second TCP request should create a new connection
            requester.sendRequest(10005, "second".getBytes(), "127.0.0.1", port, TransportProtocol.TCP);
            Socket conn2 = server.accept();

            assertThat(conn2).isNotNull();
            assertThat(conn2.getPort()).isNotEqualTo(conn1.getPort());

            conn1.close();
            conn2.close();
        }
    }
}
