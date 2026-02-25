package com.trdp.md;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class MdTcpConfirmIT {

    private MdRequester requester;
    private MdReplier replier;

    @AfterEach
    void tearDown() {
        if (requester != null) requester.close();
        if (replier != null) replier.close();
    }

    @Test
    void testTcpRequestReplyConfirmCycle() throws Exception {
        // Replier returns Mq (confirmation requested)
        int port = 19960;
        replier = new MdReplier(port,
            req -> new MdResponse(("echo:" + new String(req.getData())).getBytes(), true),
            5_000_000);
        replier.start();
        Thread.sleep(100);

        requester = new MdRequester(0);

        CompletableFuture<MdReply> future = requester.sendRequest(
            7000, "tcpConfirm".getBytes(), "127.0.0.1", port, TransportProtocol.TCP);

        // Future should complete (reply received)
        MdReply reply = future.get(5, TimeUnit.SECONDS);
        assertThat(reply).isNotNull();
        assertThat(new String(reply.getData())).isEqualTo("echo:tcpConfirm");

        // Wait for confirmation to be received by the replier
        Thread.sleep(500);

        // The replier should have received the Mc — no pending confirmations
        assertThat(replier.getPendingConfirmationCount())
            .as("Confirmation should have been received by the replier")
            .isEqualTo(0);
    }

    @Test
    void testTcpRequestReplyWithoutConfirmDoesNotTrack() throws Exception {
        // Replier returns Mp (no confirmation requested)
        int port = 19961;
        replier = new MdReplier(port,
            req -> new MdResponse("simple".getBytes(), false),
            5_000_000);
        replier.start();
        Thread.sleep(100);

        requester = new MdRequester(0);

        CompletableFuture<MdReply> future = requester.sendRequest(
            7001, "noConfirm".getBytes(), "127.0.0.1", port, TransportProtocol.TCP);

        MdReply reply = future.get(5, TimeUnit.SECONDS);
        assertThat(new String(reply.getData())).isEqualTo("simple");

        // No pending confirmations should exist (Mp doesn't track)
        assertThat(replier.getPendingConfirmationCount()).isEqualTo(0);
    }

    @Test
    void testMultipleTcpConfirmationsOnSameConnection() throws Exception {
        int port = 19962;
        replier = new MdReplier(port,
            req -> new MdResponse(("r:" + new String(req.getData())).getBytes(), true),
            5_000_000);
        replier.start();
        Thread.sleep(100);

        requester = new MdRequester(0);

        // Send two sequential TCP requests on the same connection
        CompletableFuture<MdReply> f1 = requester.sendRequest(
            7002, "first".getBytes(), "127.0.0.1", port, TransportProtocol.TCP);
        MdReply r1 = f1.get(5, TimeUnit.SECONDS);
        assertThat(new String(r1.getData())).isEqualTo("r:first");

        CompletableFuture<MdReply> f2 = requester.sendRequest(
            7002, "second".getBytes(), "127.0.0.1", port, TransportProtocol.TCP);
        MdReply r2 = f2.get(5, TimeUnit.SECONDS);
        assertThat(new String(r2.getData())).isEqualTo("r:second");

        // Wait for confirmations to be processed
        Thread.sleep(500);

        assertThat(replier.getPendingConfirmationCount())
            .as("Both confirmations should have been received")
            .isEqualTo(0);
    }
}
