package com.trdp.pd;

import com.trdp.network.UdpTransport;
import com.trdp.protocol.TrdpMessageType;
import com.trdp.protocol.TrdpPacket;
import com.trdp.protocol.TrdpPdHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class PdSubscriberTopologyTest {

    private PdSubscriber subscriber;
    private UdpTransport sender;

    @AfterEach
    void tearDown() {
        if (subscriber != null) subscriber.close();
        if (sender != null) sender.close();
    }

    private static PdEventListener dataOnly(DataCallback callback) {
        return new PdEventListener() {
            @Override public void onData(PdEvent event) { callback.accept(event); }
            @Override public void onTimeout(PdEvent event) {}
            @Override public void onValidityRestored(PdEvent event) {}
        };
    }

    @FunctionalInterface
    interface DataCallback { void accept(PdEvent event); }

    private void sendPacket(int comId, int seqCnt, int port, int etbTopoCnt, int opTrnTopoCnt) throws Exception {
        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(seqCnt);
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(comId);
        header.setEtbTopoCnt(etbTopoCnt);
        header.setOpTrnTopoCnt(opTrnTopoCnt);
        header.setDatasetLength(1);
        TrdpPacket packet = new TrdpPacket(header, new byte[]{(byte) seqCnt});
        sender.send(packet.encode(), InetAddress.getLoopbackAddress(), port);
    }

    @Test
    void testMatchingTopologyAccepted() throws Exception {
        int comId = 8200;
        int port = 19800;

        CountDownLatch latch = new CountDownLatch(1);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.setTopologyCounters(100, 200);
        subscriber.addListener(dataOnly(event -> latch.countDown()));
        subscriber.start();
        Thread.sleep(200);

        sender = new UdpTransport(0);
        sendPacket(comId, 1, port, 100, 200);

        boolean done = latch.await(3, TimeUnit.SECONDS);
        assertThat(done).isTrue();
        assertThat(subscriber.getTopoErrorCount()).isEqualTo(0);
    }

    @Test
    void testMismatchedEtbTopologyDiscarded() throws Exception {
        int comId = 8201;
        int port = 19801;

        CountDownLatch firstLatch = new CountDownLatch(1);
        AtomicInteger callCount = new AtomicInteger(0);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.setTopologyCounters(100, 200);
        subscriber.addListener(dataOnly(event -> {
            callCount.incrementAndGet();
            firstLatch.countDown();
        }));
        subscriber.start();
        Thread.sleep(200);

        sender = new UdpTransport(0);

        // Send packet with mismatched ETB topo counter
        sendPacket(comId, 1, port, 999, 200);
        Thread.sleep(500);

        assertThat(callCount.get()).isEqualTo(0);
        assertThat(subscriber.getTopoErrorCount()).isEqualTo(1);

        // Send a valid packet to confirm subscriber is still working
        sendPacket(comId, 2, port, 100, 200);
        boolean done = firstLatch.await(3, TimeUnit.SECONDS);
        assertThat(done).isTrue();
        assertThat(callCount.get()).isEqualTo(1);
        assertThat(subscriber.getTopoErrorCount()).isEqualTo(1);
    }

    @Test
    void testMismatchedOpTrnTopologyDiscarded() throws Exception {
        int comId = 8202;
        int port = 19802;

        AtomicInteger callCount = new AtomicInteger(0);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.setTopologyCounters(100, 200);
        subscriber.addListener(dataOnly(event -> callCount.incrementAndGet()));
        subscriber.start();
        Thread.sleep(200);

        sender = new UdpTransport(0);
        sendPacket(comId, 1, port, 100, 999);
        Thread.sleep(500);

        assertThat(callCount.get()).isEqualTo(0);
        assertThat(subscriber.getTopoErrorCount()).isEqualTo(1);
    }

    @Test
    void testLocalWildcardZeroAcceptsAll() throws Exception {
        int comId = 8203;
        int port = 19803;

        CountDownLatch latch = new CountDownLatch(1);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.setTopologyCounters(0, 0); // wildcards
        subscriber.addListener(dataOnly(event -> latch.countDown()));
        subscriber.start();
        Thread.sleep(200);

        sender = new UdpTransport(0);
        sendPacket(comId, 1, port, 999, 888);

        boolean done = latch.await(3, TimeUnit.SECONDS);
        assertThat(done).isTrue();
        assertThat(subscriber.getTopoErrorCount()).isEqualTo(0);
    }

    @Test
    void testRemoteWildcardZeroAccepted() throws Exception {
        int comId = 8204;
        int port = 19804;

        CountDownLatch latch = new CountDownLatch(1);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.setTopologyCounters(100, 200);
        subscriber.addListener(dataOnly(event -> latch.countDown()));
        subscriber.start();
        Thread.sleep(200);

        sender = new UdpTransport(0);
        sendPacket(comId, 1, port, 0, 0); // remote wildcards

        boolean done = latch.await(3, TimeUnit.SECONDS);
        assertThat(done).isTrue();
        assertThat(subscriber.getTopoErrorCount()).isEqualTo(0);
    }

    @Test
    void testTopoErrorCountIncrementsOnMultipleMismatches() throws Exception {
        int comId = 8205;
        int port = 19805;

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.setTopologyCounters(100, 200);
        subscriber.addListener(dataOnly(event -> {}));
        subscriber.start();
        Thread.sleep(200);

        sender = new UdpTransport(0);
        sendPacket(comId, 1, port, 999, 200);
        sendPacket(comId, 2, port, 100, 999);
        sendPacket(comId, 3, port, 888, 777);
        Thread.sleep(500);

        assertThat(subscriber.getTopoErrorCount()).isEqualTo(3);
    }

    @Test
    void testResetStatisticsClearsTopoErrorCount() throws Exception {
        int comId = 8206;
        int port = 19806;

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.setTopologyCounters(100, 200);
        subscriber.addListener(dataOnly(event -> {}));
        subscriber.start();
        Thread.sleep(200);

        sender = new UdpTransport(0);
        sendPacket(comId, 1, port, 999, 200);
        Thread.sleep(500);

        assertThat(subscriber.getTopoErrorCount()).isEqualTo(1);

        subscriber.resetStatistics();

        assertThat(subscriber.getTopoErrorCount()).isEqualTo(0);
        assertThat(subscriber.getMissedCount()).isEqualTo(0);
        assertThat(subscriber.getDuplicateCount()).isEqualTo(0);
    }

    @Test
    void testPartialWildcardStillValidatesOtherCounter() throws Exception {
        int comId = 8207;
        int port = 19807;

        AtomicInteger callCount = new AtomicInteger(0);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.setTopologyCounters(100, 200);
        subscriber.addListener(dataOnly(event -> callCount.incrementAndGet()));
        subscriber.start();
        Thread.sleep(200);

        sender = new UdpTransport(0);
        // ETB wildcard (0) but OpTrn mismatches
        sendPacket(comId, 1, port, 0, 999);
        Thread.sleep(500);

        assertThat(callCount.get()).isEqualTo(0);
        assertThat(subscriber.getTopoErrorCount()).isEqualTo(1);
    }
}
