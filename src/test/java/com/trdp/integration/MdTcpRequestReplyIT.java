package com.trdp.integration;

import com.trdp.md.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for MD Request/Reply over TCP transport.
 * Covers MdRequester TCP send path, startTcpReplyListener(),
 * MdReplier tcpAcceptLoop(), handleTcpConnection(), and TCP sendReply().
 */
class MdTcpRequestReplyIT {

    private MdRequester requester;
    private MdReplier replier;

    @AfterEach
    void tearDown() {
        if (requester != null) requester.close();
        if (replier != null) replier.close();
    }

    @Test
    void testTcpRequestReplyRoundTrip() throws Exception {
        int port = 19450;

        replier = new MdReplier(port, request -> {
            byte[] data = request.getData();
            byte[] response = new byte[data.length + 1];
            response[0] = (byte) 0xAA;
            System.arraycopy(data, 0, response, 1, data.length);
            return new MdResponse(response);
        });
        replier.start();

        Thread.sleep(300);

        requester = new MdRequester(0);

        CompletableFuture<MdReply> future = requester.sendRequest(
            4000, new byte[]{1, 2, 3}, "127.0.0.1", port, TransportProtocol.TCP);

        MdReply reply = future.get(10, TimeUnit.SECONDS);

        assertThat(reply).isNotNull();
        assertThat(reply.getComId()).isEqualTo(4000);
        assertThat(reply.getData()).containsExactly((byte) 0xAA, 1, 2, 3);
    }

    @Test
    void testTcpRequestReplyWithConfirmation() throws Exception {
        int port = 19451;

        replier = new MdReplier(port, request ->
            new MdResponse("tcp-confirmed".getBytes(), true));
        replier.start();

        Thread.sleep(300);

        requester = new MdRequester(0);

        CompletableFuture<MdReply> future = requester.sendRequest(
            4001, "hello".getBytes(), "127.0.0.1", port, TransportProtocol.TCP);

        MdReply reply = future.get(10, TimeUnit.SECONDS);

        assertThat(reply).isNotNull();
        assertThat(reply.getData()).isEqualTo("tcp-confirmed".getBytes());
    }

    @Test
    void testTcpRequestReplyWithCustomReplyComId() throws Exception {
        int port = 19452;

        replier = new MdReplier(port, request ->
            new MdResponse("ok".getBytes(), false, 7777));
        replier.start();

        Thread.sleep(300);

        requester = new MdRequester(0);

        CompletableFuture<MdReply> future = requester.sendRequest(
            4002, "data".getBytes(), "127.0.0.1", port, TransportProtocol.TCP);

        MdReply reply = future.get(10, TimeUnit.SECONDS);

        assertThat(reply).isNotNull();
        assertThat(reply.getComId()).isEqualTo(7777);
    }

    @Test
    void testTcpRequestReplyWithUris() throws Exception {
        int port = 19453;

        replier = new MdReplier(port, request -> {
            assertThat(request.getSourceUri()).isEqualTo("tcpSrc");
            assertThat(request.getDestinationUri()).isEqualTo("tcpDst");
            return new MdResponse("uri-ok".getBytes());
        });
        replier.start();

        Thread.sleep(300);

        requester = new MdRequester(0);

        CompletableFuture<MdReply> future = requester.sendRequest(
            4003, "test".getBytes(), "127.0.0.1", port,
            TransportProtocol.TCP, "tcpSrc", "tcpDst");

        MdReply reply = future.get(10, TimeUnit.SECONDS);
        assertThat(reply).isNotNull();
        assertThat(reply.getData()).isEqualTo("uri-ok".getBytes());
    }

    @Test
    void testTcpLargePayload() throws Exception {
        int port = 19454;

        replier = new MdReplier(port, request -> {
            // Echo back the data
            return new MdResponse(request.getData());
        });
        replier.start();

        Thread.sleep(300);

        requester = new MdRequester(0);

        // Send a larger payload (near max)
        byte[] largeData = new byte[1000];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i & 0xFF);
        }

        CompletableFuture<MdReply> future = requester.sendRequest(
            4010, largeData, "127.0.0.1", port, TransportProtocol.TCP);
        MdReply reply = future.get(10, TimeUnit.SECONDS);

        assertThat(reply).isNotNull();
        assertThat(reply.getData()).isEqualTo(largeData);
    }

    /**
     * Sends two requests over the same persistent TCP connection using
     * non-4-byte-aligned payloads.  TrdpPacket.encode() adds padding bytes
     * to reach 4-byte alignment.  If the receiver does not consume those
     * padding bytes, they corrupt the next message's header on the stream.
     */
    @Test
    void testTcpMultipleRequestsOnSameConnection() throws Exception {
        int port = 19456;

        // Reply with a 3-byte payload (non-aligned → 1 byte padding on the wire)
        replier = new MdReplier(port, request ->
            new MdResponse(new byte[]{(byte) 0xBB, (byte) 0xCC, (byte) 0xDD}));
        replier.start();

        Thread.sleep(300);

        requester = new MdRequester(0);

        // First request — 3-byte payload (padding = 1)
        CompletableFuture<MdReply> f1 = requester.sendRequest(
            5000, new byte[]{1, 2, 3}, "127.0.0.1", port, TransportProtocol.TCP);
        MdReply r1 = f1.get(10, TimeUnit.SECONDS);

        assertThat(r1).isNotNull();
        assertThat(r1.getData()).containsExactly((byte) 0xBB, (byte) 0xCC, (byte) 0xDD);

        // Second request — reuses the same TCP connection.
        // With the padding bug, the leftover byte from the first exchange
        // would be consumed as the first byte of the next header, causing
        // a decode/FCS failure and the request would time out.
        CompletableFuture<MdReply> f2 = requester.sendRequest(
            5001, new byte[]{4, 5, 6}, "127.0.0.1", port, TransportProtocol.TCP);
        MdReply r2 = f2.get(10, TimeUnit.SECONDS);

        assertThat(r2).isNotNull();
        assertThat(r2.getData()).containsExactly((byte) 0xBB, (byte) 0xCC, (byte) 0xDD);
    }

    @Test
    void testTcpTopologyMatch() throws Exception {
        int port = 19455;

        replier = new MdReplier(port, request ->
            new MdResponse("topo-ok".getBytes()));
        replier.setTopologyCounters(50, 60);
        replier.start();

        Thread.sleep(300);

        requester = new MdRequester(0);
        requester.setTopologyCounters(50, 60);

        CompletableFuture<MdReply> future = requester.sendRequest(
            4020, "test".getBytes(), "127.0.0.1", port, TransportProtocol.TCP);

        MdReply reply = future.get(10, TimeUnit.SECONDS);
        assertThat(reply).isNotNull();
        assertThat(reply.getData()).isEqualTo("topo-ok".getBytes());
    }
}
