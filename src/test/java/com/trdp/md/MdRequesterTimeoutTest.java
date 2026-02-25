package com.trdp.md;

import com.trdp.network.UdpTransport;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpMdHeader;
import com.trdp.protocol.TrdpPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class MdRequesterTimeoutTest {

    private MdRequester requester;
    private UdpTransport captureTransport;

    @AfterEach
    void tearDown() {
        if (requester != null) requester.close();
        if (captureTransport != null) captureTransport.close();
    }

    @Test
    void testDefaultReplyTimeout() throws Exception {
        requester = new MdRequester(0);
        assertThat(requester.getReplyTimeoutUs()).isEqualTo(5_000_000);
    }

    @Test
    void testCustomReplyTimeout() throws Exception {
        requester = new MdRequester(0, 2_000_000);
        assertThat(requester.getReplyTimeoutUs()).isEqualTo(2_000_000);
    }

    @Test
    void testCustomReplyAndConnectTimeout() throws Exception {
        requester = new MdRequester(0, 2_000_000, 30_000_000);
        assertThat(requester.getReplyTimeoutUs()).isEqualTo(2_000_000);
        assertThat(requester.getConnectTimeoutUs()).isEqualTo(30_000_000);
    }

    @Test
    void testDefaultConnectTimeout() throws Exception {
        requester = new MdRequester(0);
        assertThat(requester.getConnectTimeoutUs()).isEqualTo(60_000_000);
    }

    @Test
    void testPerRequestTimeoutOverride() throws Exception {
        // Instance default is 5s, but per-request override is 500ms
        requester = new MdRequester(0, 5_000_000);

        long start = System.nanoTime();
        CompletableFuture<MdReply> future = requester.sendRequest(
            7000, "test".getBytes(), "127.0.0.1", 19900,
            TransportProtocol.UDP, null, null, 500_000); // 500ms

        assertThatThrownBy(() -> future.get(3, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class);

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs)
            .as("Should timeout around 500ms, not 5s")
            .isLessThan(2000);
    }

    @Test
    void testPerRequestTimeoutZeroUsesDefault() throws Exception {
        requester = new MdRequester(0, 500_000); // 500ms default

        long start = System.nanoTime();
        CompletableFuture<MdReply> future = requester.sendRequest(
            7001, "test".getBytes(), "127.0.0.1", 19901,
            TransportProtocol.UDP, null, null, 0); // 0 means use default

        assertThatThrownBy(() -> future.get(3, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class);

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs)
            .as("Should timeout around 500ms (instance default)")
            .isLessThan(2000);
    }

    @Test
    void testReplyTimeoutEncodedInHeader() throws Exception {
        // Set up a transport to capture the packet the requester sends
        captureTransport = new UdpTransport(19902);

        requester = new MdRequester(0, 3_000_000); // 3s

        requester.sendRequest(7002, "data".getBytes(), "127.0.0.1", 19902);

        // Receive the packet
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        var received = captureTransport.receiveWithSource(buffer, 2000);
        assertThat(received).isNotNull();

        byte[] data = new byte[received.getLength()];
        System.arraycopy(received.getData(), 0, data, 0, received.getLength());
        TrdpPacket packet = TrdpPacket.decode(data);
        TrdpMdHeader header = (TrdpMdHeader) packet.getHeader();

        assertThat(header.getReplyTimeout()).isEqualTo(3_000_000);
    }

    @Test
    void testPerRequestTimeoutEncodedInHeader() throws Exception {
        captureTransport = new UdpTransport(19903);

        requester = new MdRequester(0, 5_000_000); // 5s default

        // Send with per-request override of 1s
        requester.sendRequest(7003, "data".getBytes(), "127.0.0.1", 19903,
            TransportProtocol.UDP, null, null, 1_000_000);

        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        var received = captureTransport.receiveWithSource(buffer, 2000);
        assertThat(received).isNotNull();

        byte[] data = new byte[received.getLength()];
        System.arraycopy(received.getData(), 0, data, 0, received.getLength());
        TrdpPacket packet = TrdpPacket.decode(data);
        TrdpMdHeader header = (TrdpMdHeader) packet.getHeader();

        assertThat(header.getReplyTimeout()).isEqualTo(1_000_000);
    }
}
