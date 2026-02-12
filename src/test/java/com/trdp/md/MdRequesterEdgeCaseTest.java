package com.trdp.md;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
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
    void testRequestTimeout() throws Exception {
        requester = new MdRequester(0);

        CompletableFuture<MdReply> future = requester.sendRequest(
            4000, "timeout".getBytes(), "127.0.0.1", 19805);

        // Default timeout is 5000ms, so wait slightly more
        assertThatThrownBy(() -> future.get(6, TimeUnit.SECONDS))
            .isInstanceOf(java.util.concurrent.ExecutionException.class);
    }
}
