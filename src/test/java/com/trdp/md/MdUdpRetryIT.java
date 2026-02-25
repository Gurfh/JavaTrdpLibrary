package com.trdp.md;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;

class MdUdpRetryIT {

    private MdRequester requester;
    private MdReplier replier;

    @AfterEach
    void tearDown() {
        if (requester != null) requester.close();
        if (replier != null) replier.close();
    }

    @Test
    void testUdpRequestWithRetriesSucceeds() throws Exception {
        int port = 19970;
        replier = new MdReplier(port,
            req -> new MdResponse(("echo:" + new String(req.getData())).getBytes(), false));
        replier.start();
        Thread.sleep(100);

        requester = new MdRequester(0, 1_000_000); // 1s per retry

        CompletableFuture<MdReply> future = requester.sendRequest(
            9000, "hello".getBytes(), "127.0.0.1", port,
            TransportProtocol.UDP, null, null, 0, 2);

        MdReply reply = future.get(5, TimeUnit.SECONDS);
        assertThat(new String(reply.getData())).isEqualTo("echo:hello");
    }

    @Test
    void testUdpRetryExhaustedTimesOut() throws Exception {
        // No replier — all attempts should fail
        requester = new MdRequester(0, 200_000); // 200ms per retry

        long start = System.nanoTime();
        CompletableFuture<MdReply> future = requester.sendRequest(
            9001, "no-reply".getBytes(), "127.0.0.1", 19971,
            TransportProtocol.UDP, null, null, 0, 2);

        assertThatThrownBy(() -> future.get(3, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(TimeoutException.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs)
            .as("Total timeout: (2+1) x 200ms = 600ms")
            .isBetween(400L, 1500L);
    }

    @Test
    void testUdpRetrySucceedsOnLaterAttempt() throws Exception {
        // Start requester BEFORE replier, so first attempt fails
        int port = 19972;
        requester = new MdRequester(0, 1_000_000); // 1s per retry

        CompletableFuture<MdReply> future = requester.sendRequest(
            9002, "retry-me".getBytes(), "127.0.0.1", port,
            TransportProtocol.UDP, null, null, 0, 2);

        // Start replier after 500ms — will catch a retry
        Thread.sleep(500);
        replier = new MdReplier(port,
            req -> new MdResponse(("got:" + new String(req.getData())).getBytes(), false));
        replier.start();

        MdReply reply = future.get(5, TimeUnit.SECONDS);
        assertThat(new String(reply.getData())).isEqualTo("got:retry-me");
    }
}
