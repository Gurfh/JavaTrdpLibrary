package com.trdp.integration;

import com.trdp.md.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class MdRequestReplyIT {

    private MdRequester requester;
    private MdReplier replier;

    @AfterEach
    void tearDown() {
        if (requester != null) requester.close();
        if (replier != null) replier.close();
    }

    @Test
    void testUdpRequestReplyRoundTrip() throws Exception {
        int replierPort = 19400;

        // Set up replier that echoes data with a prefix
        replier = new MdReplier(replierPort, request -> {
            byte[] reqData = request.getData();
            byte[] respData = new byte[reqData.length + 1];
            respData[0] = 42; // prefix byte
            System.arraycopy(reqData, 0, respData, 1, reqData.length);
            return new MdResponse(respData);
        });
        replier.start();

        Thread.sleep(200);

        requester = new MdRequester(0);

        byte[] requestData = {1, 2, 3};
        CompletableFuture<MdReply> future = requester.sendRequest(
            3000, requestData, "127.0.0.1", replierPort);

        MdReply reply = future.get(5, TimeUnit.SECONDS);

        assertThat(reply).isNotNull();
        assertThat(reply.getComId()).isEqualTo(3000);
        assertThat(reply.getData()).containsExactly(42, 1, 2, 3);
    }

    @Test
    void testUdpRequestReplyWithConfirmation() throws Exception {
        int replierPort = 19401;

        replier = new MdReplier(replierPort, request ->
            new MdResponse("confirmed".getBytes(), true));
        replier.start();

        Thread.sleep(200);

        requester = new MdRequester(0);

        CompletableFuture<MdReply> future = requester.sendRequest(
            3001, "test".getBytes(), "127.0.0.1", replierPort);

        MdReply reply = future.get(5, TimeUnit.SECONDS);

        assertThat(reply).isNotNull();
        assertThat(reply.getData()).isEqualTo("confirmed".getBytes());
    }

    @Test
    void testUdpRequestReplyWithCustomReplyComId() throws Exception {
        int replierPort = 19402;

        replier = new MdReplier(replierPort, request ->
            new MdResponse("ok".getBytes(), false, 9999));
        replier.start();

        Thread.sleep(200);

        requester = new MdRequester(0);

        CompletableFuture<MdReply> future = requester.sendRequest(
            3002, "data".getBytes(), "127.0.0.1", replierPort);

        MdReply reply = future.get(5, TimeUnit.SECONDS);

        assertThat(reply).isNotNull();
        assertThat(reply.getComId()).isEqualTo(9999);
    }

    @Test
    void testUdpRequestReplyWithUris() throws Exception {
        int replierPort = 19403;

        replier = new MdReplier(replierPort, request -> {
            assertThat(request.getSourceUri()).isNotEmpty();
            assertThat(request.getDestinationUri()).isNotEmpty();
            return new MdResponse("uri-ok".getBytes());
        });
        replier.start();

        Thread.sleep(200);

        requester = new MdRequester(0);

        CompletableFuture<MdReply> future = requester.sendRequest(
            3003, "test".getBytes(), "127.0.0.1", replierPort,
            TransportProtocol.UDP, "srcApp", "dstApp");

        MdReply reply = future.get(5, TimeUnit.SECONDS);
        assertThat(reply).isNotNull();
    }

    @Test
    void testRequestDataTooLarge() throws Exception {
        requester = new MdRequester(0);

        byte[] oversized = new byte[2000];
        CompletableFuture<MdReply> future = requester.sendRequest(
            3004, oversized, "127.0.0.1", 19404);

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(() -> future.get())
            .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testTopologyMismatchDiscards() throws Exception {
        int replierPort = 19405;

        replier = new MdReplier(replierPort, request ->
            new MdResponse("should-not-arrive".getBytes()));
        replier.setTopologyCounters(100, 200);
        replier.start();

        Thread.sleep(200);

        // Requester with different topology
        requester = new MdRequester(0);
        requester.setTopologyCounters(999, 888);

        CompletableFuture<MdReply> future = requester.sendRequest(
            3005, "test".getBytes(), "127.0.0.1", replierPort);

        // Should timeout because replier discards due to topo mismatch
        assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
            .isInstanceOfAny(java.util.concurrent.ExecutionException.class,
                             java.util.concurrent.TimeoutException.class);
    }

    @Test
    void testNullResponseFromHandler() throws Exception {
        int replierPort = 19406;

        // Handler returns null (notification handling)
        replier = new MdReplier(replierPort, request -> null);
        replier.start();

        Thread.sleep(200);

        requester = new MdRequester(0);

        CompletableFuture<MdReply> future = requester.sendRequest(
            3006, "test".getBytes(), "127.0.0.1", replierPort);

        // Should timeout since handler returns null (no reply sent)
        assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
            .isInstanceOfAny(java.util.concurrent.ExecutionException.class,
                             java.util.concurrent.TimeoutException.class);
    }

    @Test
    void testReplierStartIdempotent() throws Exception {
        int replierPort = 19407;
        replier = new MdReplier(replierPort, request -> new MdResponse("ok".getBytes()));
        replier.start();
        // Second start should be a no-op
        assertThatCode(() -> replier.start()).doesNotThrowAnyException();
    }

    @Test
    void testRequesterCloseIsClean() throws Exception {
        requester = new MdRequester(0);
        // Send a request that won't be answered
        requester.sendRequest(4000, "data".getBytes(), "127.0.0.1", 19499);
        // Close should cancel pending futures and clean up
        assertThatCode(() -> requester.close()).doesNotThrowAnyException();
        requester = null;
    }
}
