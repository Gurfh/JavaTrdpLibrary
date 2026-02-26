package com.trdp.integration;

import com.trdp.network.UdpTransport;
import com.trdp.pd.*;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpMessageType;
import com.trdp.protocol.TrdpPacket;
import com.trdp.protocol.TrdpPdHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

class PdSessionIT {

    private TrdpPdSession session;
    private UdpTransport externalTransport;

    @AfterEach
    void tearDown() {
        if (session != null) session.close();
        if (externalTransport != null) externalTransport.close();
    }

    @Test
    void testSinglePublishSubscribe() throws Exception {
        int port = 19130;
        int comId = 3000;
        byte[] testData = "Session PD Test".getBytes();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<byte[]> receivedData = new AtomicReference<>();

        session = new TrdpPdSession(port);
        session.addPublisher(comId, "127.0.0.1", port, 0);
        session.addSubscriber(comId, null, 0, new PdEventListener() {
            @Override public void onData(PdEvent event) {
                receivedData.set(event.getData());
                latch.countDown();
            }
            @Override public void onTimeout(PdEvent event) {}
            @Override public void onValidityRestored(PdEvent event) {}
        });
        session.start();

        // Send from external source to the session port
        externalTransport = new UdpTransport(0);
        sendPdPacket(externalTransport, comId, testData, "127.0.0.1", port, TrdpMessageType.PD, 0);

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedData.get()).isEqualTo(testData);
    }

    @Test
    void testMultipleComIds() throws Exception {
        int port = 19131;
        int[] comIds = {3100, 3101, 3102};
        CountDownLatch latch = new CountDownLatch(3);
        ConcurrentHashMap<Integer, byte[]> received = new ConcurrentHashMap<>();

        session = new TrdpPdSession(port);
        for (int comId : comIds) {
            session.addSubscriber(comId, null, 0, new PdEventListener() {
                @Override public void onData(PdEvent event) {
                    received.put(event.getComId(), event.getData());
                    latch.countDown();
                }
                @Override public void onTimeout(PdEvent event) {}
                @Override public void onValidityRestored(PdEvent event) {}
            });
        }
        session.start();

        externalTransport = new UdpTransport(0);
        for (int comId : comIds) {
            byte[] data = ("Data-" + comId).getBytes();
            sendPdPacket(externalTransport, comId, data, "127.0.0.1", port, TrdpMessageType.PD, 0);
        }

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        for (int comId : comIds) {
            assertThat(received.get(comId)).isEqualTo(("Data-" + comId).getBytes());
        }
    }

    @Test
    void testCyclicSend() throws Exception {
        int port = 19132;
        int comId = 3200;
        byte[] testData = "Cyclic Data".getBytes();

        // Receiver on same port as destination
        UdpTransport receiver = new UdpTransport(port);
        try {
            session = new TrdpPdSession(0);
            PdPublisherHandle pub = session.addPublisher(comId, "127.0.0.1", port, 50_000); // 50ms
            session.start();

            pub.putData(testData);

            // Wait for at least 3 cyclic sends
            int count = 0;
            byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
            for (int i = 0; i < 5; i++) {
                int len = receiver.receive(buffer, 500);
                if (len > 0) {
                    TrdpPacket pkt = TrdpPacket.decode(Arrays.copyOf(buffer, len));
                    if (pkt.getHeader().getComId() == comId) {
                        count++;
                    }
                }
                if (count >= 3) break;
            }
            assertThat(count).isGreaterThanOrEqualTo(3);
        } finally {
            receiver.close();
        }
    }

    @Test
    void testSubscriberTimeout() throws Exception {
        int port = 19133;
        int comId = 3300;
        CountDownLatch timeoutLatch = new CountDownLatch(1);

        session = new TrdpPdSession(port);
        PdSubscriberHandle sub = session.addSubscriber(comId, null, 50_000, new PdEventListener() {
            @Override public void onData(PdEvent event) {}
            @Override public void onTimeout(PdEvent event) {
                timeoutLatch.countDown();
            }
            @Override public void onValidityRestored(PdEvent event) {}
        });
        session.start();

        // No data sent — timeout should fire
        assertThat(timeoutLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(sub.isTimedOut()).isTrue();
    }

    @Test
    void testValidityRestored() throws Exception {
        int port = 19134;
        int comId = 3400;
        CountDownLatch timeoutLatch = new CountDownLatch(1);
        CountDownLatch restoredLatch = new CountDownLatch(1);
        CountDownLatch dataLatch = new CountDownLatch(1);

        session = new TrdpPdSession(port);
        PdSubscriberHandle sub = session.addSubscriber(comId, null, 50_000, new PdEventListener() {
            @Override public void onData(PdEvent event) {
                dataLatch.countDown();
            }
            @Override public void onTimeout(PdEvent event) {
                timeoutLatch.countDown();
            }
            @Override public void onValidityRestored(PdEvent event) {
                restoredLatch.countDown();
            }
        });
        session.start();

        // Wait for timeout
        assertThat(timeoutLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(sub.isTimedOut()).isTrue();

        // Send data to restore validity
        externalTransport = new UdpTransport(0);
        sendPdPacket(externalTransport, comId, "Restored".getBytes(), "127.0.0.1", port, TrdpMessageType.PD, 0);

        assertThat(restoredLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(dataLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(sub.isTimedOut()).isFalse();
    }

    @Test
    void testSequenceValidation() throws Exception {
        int port = 19135;
        int comId = 3500;
        CountDownLatch latch = new CountDownLatch(2); // expect 2 data callbacks (seq 0 and seq 5)

        session = new TrdpPdSession(port);
        PdSubscriberHandle sub = session.addSubscriber(comId, null, 0, new PdEventListener() {
            @Override public void onData(PdEvent event) {
                latch.countDown();
            }
            @Override public void onTimeout(PdEvent event) {}
            @Override public void onValidityRestored(PdEvent event) {}
        });
        session.start();

        externalTransport = new UdpTransport(0);

        // Send seq 0 (accepted), then seq 5 (accepted, gap of 4 missed)
        sendPdPacketWithSeq(externalTransport, comId, "A".getBytes(), "127.0.0.1", port, 0);
        Thread.sleep(50);
        sendPdPacketWithSeq(externalTransport, comId, "B".getBytes(), "127.0.0.1", port, 5);

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        // Gap from seq 1..4 = 4 missed
        assertThat(sub.getMissedCount()).isEqualTo(4);
    }

    @Test
    void testDuplicateSequenceDiscarded() throws Exception {
        int port = 19136;
        int comId = 3600;
        AtomicInteger dataCount = new AtomicInteger();
        CountDownLatch firstLatch = new CountDownLatch(1);

        session = new TrdpPdSession(port);
        PdSubscriberHandle sub = session.addSubscriber(comId, null, 0, new PdEventListener() {
            @Override public void onData(PdEvent event) {
                dataCount.incrementAndGet();
                firstLatch.countDown();
            }
            @Override public void onTimeout(PdEvent event) {}
            @Override public void onValidityRestored(PdEvent event) {}
        });
        session.start();

        externalTransport = new UdpTransport(0);

        // Send seq 5, then seq 3 (duplicate/old)
        sendPdPacketWithSeq(externalTransport, comId, "A".getBytes(), "127.0.0.1", port, 5);
        assertThat(firstLatch.await(2, TimeUnit.SECONDS)).isTrue();

        sendPdPacketWithSeq(externalTransport, comId, "B".getBytes(), "127.0.0.1", port, 3);
        Thread.sleep(200);

        assertThat(dataCount.get()).isEqualTo(1);
        assertThat(sub.getDuplicateCount()).isEqualTo(1);
    }

    @Test
    void testTopologyMismatch() throws Exception {
        int port = 19137;
        int comId = 3700;
        AtomicInteger dataCount = new AtomicInteger();

        session = new TrdpPdSession(port);
        session.setTopologyCounters(42, 7);
        PdSubscriberHandle sub = session.addSubscriber(comId, null, 0, new PdEventListener() {
            @Override public void onData(PdEvent event) {
                dataCount.incrementAndGet();
            }
            @Override public void onTimeout(PdEvent event) {}
            @Override public void onValidityRestored(PdEvent event) {}
        });
        session.start();

        externalTransport = new UdpTransport(0);

        // Send with wrong topology (99, 99) — should be discarded
        sendPdPacketWithTopo(externalTransport, comId, "bad".getBytes(), "127.0.0.1", port, 99, 99);
        Thread.sleep(200);

        assertThat(dataCount.get()).isZero();
        assertThat(sub.getTopoErrorCount()).isEqualTo(1);
    }

    @Test
    void testPullPattern() throws Exception {
        int port = 19138;
        int comId = 3800;
        byte[] stagedData = "Pull Reply Data".getBytes();

        session = new TrdpPdSession(port);
        PdPublisherHandle pub = session.addPublisher(comId, "127.0.0.1", port, 0);
        session.start();

        pub.putData(stagedData);
        Thread.sleep(100);

        // Send PD_REQUEST from external transport
        externalTransport = new UdpTransport(0);
        sendPdPacket(externalTransport, comId, new byte[0], "127.0.0.1", port, TrdpMessageType.PD_REQUEST, 0);

        // Receive the PD_REPLY
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        var received = externalTransport.receiveWithSource(buffer, 2000);
        assertThat(received).isNotNull();

        TrdpPacket reply = TrdpPacket.decode(Arrays.copyOf(received.getData(), received.getLength()));
        assertThat(reply.getHeader().getMessageType()).isEqualTo(TrdpMessageType.PD_REPLY);
        assertThat(reply.getPayload()).isEqualTo(stagedData);
    }

    @Test
    void testPutDataImmediateReceived() throws Exception {
        int port = 19140;
        int comId = 4000;
        byte[] testData = "Immediate Data".getBytes();

        // External receiver
        UdpTransport receiver = new UdpTransport(port);
        try {
            session = new TrdpPdSession(0);
            PdPublisherHandle pub = session.addPublisher(comId, "127.0.0.1", port, 0);
            session.start();

            pub.putDataImmediate(testData);

            byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
            int len = receiver.receive(buffer, 2000);
            assertThat(len).isGreaterThan(0);

            TrdpPacket pkt = TrdpPacket.decode(Arrays.copyOf(buffer, len));
            assertThat(pkt.getPayload()).isEqualTo(testData);
        } finally {
            receiver.close();
        }
    }

    @Test
    void testManyPublishersSubscribers() throws Exception {
        int port = 19141;
        int count = 20;
        CountDownLatch latch = new CountDownLatch(count);
        ConcurrentHashMap<Integer, byte[]> received = new ConcurrentHashMap<>();

        session = new TrdpPdSession(port);

        // Register 20 subscribers with different ComIds
        for (int i = 0; i < count; i++) {
            int comId = 4100 + i;
            session.addSubscriber(comId, null, 0, new PdEventListener() {
                @Override public void onData(PdEvent event) {
                    received.put(event.getComId(), event.getData());
                    latch.countDown();
                }
                @Override public void onTimeout(PdEvent event) {}
                @Override public void onValidityRestored(PdEvent event) {}
            });
        }

        // Register 20 publishers with different ComIds
        for (int i = 0; i < count; i++) {
            int comId = 4200 + i;
            session.addPublisher(comId, "127.0.0.1", port, 0);
        }

        session.start();

        // Verify thread count: should only have 1 recv thread (no cyclic publishers → no send thread)
        Set<String> sessionThreads = Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .filter(n -> n.startsWith("PD-Session-"))
                .collect(Collectors.toSet());
        assertThat(sessionThreads)
                .as("Only 1 session thread expected (recv only, no cyclic)")
                .hasSize(1);

        // Send data to each subscriber from external transport
        externalTransport = new UdpTransport(0);
        for (int i = 0; i < count; i++) {
            int comId = 4100 + i;
            sendPdPacket(externalTransport, comId, ("D" + i).getBytes(), "127.0.0.1", port,
                    TrdpMessageType.PD, 0);
        }

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(count);
    }

    @Test
    void testManyPublishersWithCyclicHasTwoThreads() throws Exception {
        int port = 19142;

        session = new TrdpPdSession(port);
        session.addPublisher(5000, "127.0.0.1", port, 100_000); // cyclic
        session.addSubscriber(5000, null, 0, new PdEventListener() {
            @Override public void onData(PdEvent event) {}
            @Override public void onTimeout(PdEvent event) {}
            @Override public void onValidityRestored(PdEvent event) {}
        });
        session.start();
        Thread.sleep(100);

        Set<String> sessionThreads = Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .filter(n -> n.startsWith("PD-Session-"))
                .collect(Collectors.toSet());
        assertThat(sessionThreads)
                .as("2 session threads expected (recv + send)")
                .hasSize(2);
    }

    @Test
    void testSessionCloseStopsAllActivity() throws Exception {
        int port = 19143;
        int comId = 5100;

        UdpTransport receiver = new UdpTransport(port);
        try {
            session = new TrdpPdSession(0);
            PdPublisherHandle pub = session.addPublisher(comId, "127.0.0.1", port, 50_000);
            session.start();
            pub.putData("active".getBytes());

            // Verify at least one packet arrives
            byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
            int len = receiver.receive(buffer, 2000);
            assertThat(len).isGreaterThan(0);

            // Close session
            session.close();
            session = null;
            Thread.sleep(200);

            // No more packets should arrive
            len = receiver.receive(buffer, 300);
            assertThat(len).isZero();
        } finally {
            receiver.close();
        }
    }

    @Test
    void testTrafficShapingStaggersPacketTiming() throws Exception {
        int receiverPort = 19150;
        UdpTransport receiver = new UdpTransport(receiverPort);
        try {
            int n = 4;
            long intervalUs = 200_000; // 200ms → offset = 50ms

            session = new TrdpPdSession(0);
            for (int i = 0; i < n; i++) {
                PdPublisherHandle pub = session.addPublisher(6000 + i, "127.0.0.1", receiverPort, intervalUs);
                pub.putData(new byte[]{(byte) i});
            }

            long startNanos = System.nanoTime();
            session.start();

            // Receive n packets and record arrival times
            long[] arrivalTimesMs = new long[n];
            byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
            for (int i = 0; i < n; i++) {
                int len = receiver.receive(buffer, 2000);
                assertThat(len).isGreaterThan(0);
                arrivalTimesMs[i] = (System.nanoTime() - startNanos) / 1_000_000;
            }

            // With stagger: packets arrive at ~0ms, ~50ms, ~100ms, ~150ms
            // Without stagger: all packets arrive at ~200ms
            // First packet should arrive well before the interval (200ms)
            assertThat(arrivalTimesMs[0])
                    .as("First staggered packet should arrive before half the interval")
                    .isLessThan(intervalUs / 2000); // < 100ms

            // Spread between first and last should be significant (at least 75ms for 150ms theoretical)
            long spreadMs = arrivalTimesMs[n - 1] - arrivalTimesMs[0];
            assertThat(spreadMs)
                    .as("Staggered packets should be spread across the interval")
                    .isGreaterThan(50);
        } finally {
            receiver.close();
        }
    }

    @Test
    void testTrafficShapingDisabledAllFireTogether() throws Exception {
        int receiverPort = 19151;
        UdpTransport receiver = new UdpTransport(receiverPort);
        try {
            int n = 4;
            long intervalUs = 200_000; // 200ms

            session = new TrdpPdSession(0);
            session.setTrafficShapingEnabled(false);
            for (int i = 0; i < n; i++) {
                PdPublisherHandle pub = session.addPublisher(6100 + i, "127.0.0.1", receiverPort, intervalUs);
                pub.putData(new byte[]{(byte) i});
            }

            long startNanos = System.nanoTime();
            session.start();

            // Receive n packets and record arrival times
            long[] arrivalTimesMs = new long[n];
            byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
            for (int i = 0; i < n; i++) {
                int len = receiver.receive(buffer, 2000);
                assertThat(len).isGreaterThan(0);
                arrivalTimesMs[i] = (System.nanoTime() - startNanos) / 1_000_000;
            }

            // Without stagger: all packets arrive at ~200ms, spread should be minimal
            // First packet should arrive near the interval (not before half the interval)
            assertThat(arrivalTimesMs[0])
                    .as("Without shaping, first packet should fire at the interval")
                    .isGreaterThan(intervalUs / 2000 - 20); // > ~80ms

            // All packets should arrive close together (within 50ms of each other)
            long spreadMs = arrivalTimesMs[n - 1] - arrivalTimesMs[0];
            assertThat(spreadMs)
                    .as("Without shaping, all packets should fire together")
                    .isLessThan(50);
        } finally {
            receiver.close();
        }
    }

    // --- Helpers ---

    private void sendPdPacket(UdpTransport transport, int comId, byte[] data,
                              String destIp, int destPort, TrdpMessageType type,
                              int seqCounter) throws Exception {
        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(seqCounter);
        header.setMessageType(type);
        header.setComId(comId);
        header.setDatasetLength(data.length);

        TrdpPacket packet = new TrdpPacket(header, data);
        transport.send(packet.encode(), InetAddress.getByName(destIp), destPort);
    }

    private void sendPdPacketWithSeq(UdpTransport transport, int comId, byte[] data,
                                     String destIp, int destPort, int seqCounter) throws Exception {
        sendPdPacket(transport, comId, data, destIp, destPort, TrdpMessageType.PD, seqCounter);
    }

    private void sendPdPacketWithTopo(UdpTransport transport, int comId, byte[] data,
                                      String destIp, int destPort,
                                      int etbTopo, int opTrnTopo) throws Exception {
        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(0);
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(comId);
        header.setEtbTopoCnt(etbTopo);
        header.setOpTrnTopoCnt(opTrnTopo);
        header.setDatasetLength(data.length);

        TrdpPacket packet = new TrdpPacket(header, data);
        transport.send(packet.encode(), InetAddress.getByName(destIp), destPort);
    }
}
