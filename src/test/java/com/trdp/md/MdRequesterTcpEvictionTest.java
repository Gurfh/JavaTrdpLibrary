package com.trdp.md;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

class MdRequesterTcpEvictionTest {

    private MdRequester requester;

    @AfterEach
    void tearDown() {
        if (requester != null) requester.close();
    }

    @Test
    void testIdleTcpConnectionEvicted() throws Exception {
        // 500ms connect timeout so eviction happens quickly
        requester = new MdRequester(0, 5_000_000, 500_000);

        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(5000);
            int port = server.getLocalPort();

            // First request creates the TCP connection
            requester.sendRequest(8000, "first".getBytes(), "127.0.0.1", port, TransportProtocol.TCP);
            Socket firstClient = server.accept();

            // Wait for eviction: 500ms timeout + 250ms evictor interval + margin
            Thread.sleep(1500);

            // Second request should trigger a new connection (old one evicted)
            requester.sendRequest(8001, "second".getBytes(), "127.0.0.1", port, TransportProtocol.TCP);
            Socket secondClient = server.accept();

            // We got a second accept, proving the first connection was evicted
            assertThat(secondClient).isNotNull();
            assertThat(secondClient).isNotSameAs(firstClient);

            firstClient.close();
            secondClient.close();
        }
    }

    @Test
    void testConnectTimeoutZeroDisablesEviction() throws Exception {
        requester = new MdRequester(0, 5_000_000, 0);

        Thread.sleep(100);

        Set<String> evictorThreads = Thread.getAllStackTraces().keySet().stream()
            .map(Thread::getName)
            .filter(name -> name.startsWith("MD-Requester-TCP-Evictor"))
            .collect(Collectors.toSet());

        assertThat(evictorThreads)
            .as("No evictor thread should exist when connectTimeoutUs=0")
            .isEmpty();
    }

    @Test
    void testEvictorThreadCleansUpOnClose() throws Exception {
        requester = new MdRequester(0, 5_000_000, 500_000);
        Thread.sleep(100);

        Set<String> before = Thread.getAllStackTraces().keySet().stream()
            .map(Thread::getName)
            .filter(name -> name.startsWith("MD-Requester-TCP-Evictor"))
            .collect(Collectors.toSet());
        assertThat(before).isNotEmpty();

        requester.close();
        requester = null;
        Thread.sleep(200);

        Set<String> after = Thread.getAllStackTraces().keySet().stream()
            .map(Thread::getName)
            .filter(name -> name.startsWith("MD-Requester-TCP-Evictor"))
            .collect(Collectors.toSet());
        assertThat(after).isEmpty();
    }
}
