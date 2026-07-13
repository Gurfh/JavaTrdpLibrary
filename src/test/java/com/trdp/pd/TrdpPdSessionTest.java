package com.trdp.pd;

import com.trdp.network.UdpTransport;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpMessageType;
import com.trdp.protocol.TrdpPacket;
import com.trdp.protocol.TrdpPdHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

class TrdpPdSessionTest {

    private TrdpPdSession session;

    @AfterEach
    void tearDown() {
        if (session != null) session.close();
    }

    @Test
    void testCreateSession() throws Exception {
        session = new TrdpPdSession(19100);
        assertThat(session.getPort()).isEqualTo(19100);
        assertThat(session.getPublisherCount()).isZero();
        assertThat(session.getSubscriberCount()).isZero();
    }

    @Test
    void testCreateSessionEphemeralPort() throws Exception {
        session = new TrdpPdSession(0);
        assertThat(session.getPort()).isGreaterThan(0);
    }

    @Test
    void testCreateSessionWithSocketOptions() throws Exception {
        session = new TrdpPdSession(0, null, 32, 3);
        assertThat(session.getPort()).isGreaterThan(0);
    }

    @Test
    void testCreateSessionWithBindAddress() throws Exception {
        session = new TrdpPdSession(0, InetAddress.getLoopbackAddress(), 64, 5);
        assertThat(session.getPort()).isGreaterThan(0);
    }

    @Test
    void testAddPublisher() throws Exception {
        session = new TrdpPdSession(19101);
        PdPublisherHandle handle = session.addPublisher(1000, "127.0.0.1", 17224, 50_000);

        assertThat(handle.getComId()).isEqualTo(1000);
        assertThat(handle.getIntervalUs()).isEqualTo(50_000);
        assertThat(session.getPublisherCount()).isEqualTo(1);
    }

    @Test
    void testAddSubscriber() throws Exception {
        session = new TrdpPdSession(19102);
        PdEventListener listener = new NoOpPdEventListener();
        PdSubscriberHandle handle = session.addSubscriber(2000, null, 100_000, listener);

        assertThat(handle.getComId()).isEqualTo(2000);
        assertThat(session.getSubscriberCount()).isEqualTo(1);
    }

    @Test
    void testAddPublisherAfterStartSucceeds() throws Exception {
        session = new TrdpPdSession(19103);
        session.start();

        PdPublisherHandle handle = session.addPublisher(1000, "127.0.0.1", 17224, 0);
        assertThat(handle.getComId()).isEqualTo(1000);
        assertThat(session.getPublisherCount()).isEqualTo(1);
    }

    @Test
    void testAddSubscriberAfterStartSucceeds() throws Exception {
        session = new TrdpPdSession(19104);
        session.start();

        PdSubscriberHandle handle = session.addSubscriber(2000, null, 100_000, new NoOpPdEventListener());
        assertThat(handle.getComId()).isEqualTo(2000);
        assertThat(session.getSubscriberCount()).isEqualTo(1);
    }

    @Test
    void testDuplicatePublisherComIdThrows() throws Exception {
        session = new TrdpPdSession(19105);
        session.addPublisher(1000, "127.0.0.1", 17224, 0);

        assertThatThrownBy(() -> session.addPublisher(1000, "127.0.0.2", 17224, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNegativeIntervalThrows() throws Exception {
        session = new TrdpPdSession(19106);

        assertThatThrownBy(() -> session.addPublisher(1000, "127.0.0.1", 17224, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testPutDataOversize() throws Exception {
        session = new TrdpPdSession(19107);
        PdPublisherHandle handle = session.addPublisher(1000, "127.0.0.1", 17224, 0);

        byte[] oversized = new byte[TrdpConstants.TRDP_MAX_PD_DATA_SIZE + 1];
        assertThatThrownBy(() -> handle.putData(oversized))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testPublisherPutDataImmediate() throws Exception {
        // Use a receiver to verify the packet arrives
        UdpTransport receiver = new UdpTransport(19108);
        try {
            session = new TrdpPdSession(0);
            PdPublisherHandle handle = session.addPublisher(1000, "127.0.0.1", 19108, 0);
            session.start();

            byte[] testData = "Hello Session".getBytes();
            handle.putDataImmediate(testData);

            byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
            int len = receiver.receive(buffer, 2000);
            assertThat(len).isGreaterThan(0);

            TrdpPacket packet = TrdpPacket.decode(java.util.Arrays.copyOf(buffer, len));
            assertThat(packet.getPayload()).isEqualTo(testData);
            assertThat(packet.getHeader().getComId()).isEqualTo(1000);
        } finally {
            receiver.close();
        }
    }

    @Test
    void testSubscriberInitialState() throws Exception {
        session = new TrdpPdSession(19109);
        PdSubscriberHandle handle = session.addSubscriber(2000, null, 100_000, new NoOpPdEventListener());

        assertThat(handle.isTimedOut()).isFalse();
        assertThat(handle.getMissedCount()).isZero();
        assertThat(handle.getDuplicateCount()).isZero();
        assertThat(handle.getTopoErrorCount()).isZero();
    }

    @Test
    void testSubscriberResetStatistics() throws Exception {
        session = new TrdpPdSession(19110);
        PdSubscriberHandle handle = session.addSubscriber(2000, null, 100_000, new NoOpPdEventListener());

        // Statistics start at 0; reset should be a no-op but not throw
        handle.resetStatistics();
        assertThat(handle.getMissedCount()).isZero();
    }

    @Test
    void testMultipleSubscribersSameComId() throws Exception {
        session = new TrdpPdSession(19111);
        PdSubscriberHandle h1 = session.addSubscriber(2000, null, 0, new NoOpPdEventListener());
        PdSubscriberHandle h2 = session.addSubscriber(2000, null, 0, new NoOpPdEventListener());

        assertThat(session.getSubscriberCount()).isEqualTo(2);
        assertThat(h1).isNotSameAs(h2);
    }

    @Test
    void testDoubleStartThrows() throws Exception {
        session = new TrdpPdSession(19112);
        session.start();

        assertThatThrownBy(() -> session.start())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testCloseIdempotent() throws Exception {
        session = new TrdpPdSession(19113);
        session.start();

        assertThatCode(() -> {
            session.close();
            session.close();
        }).doesNotThrowAnyException();
        session = null; // Prevent double-close in tearDown
    }

    @Test
    void testThreadsCleanedUpAfterClose() throws Exception {
        int port = 19114;
        session = new TrdpPdSession(port);
        session.addPublisher(1000, "127.0.0.1", 17224, 100_000);
        session.addSubscriber(2000, null, 100_000, new NoOpPdEventListener());
        session.start();
        Thread.sleep(100);

        // Threads should be alive
        assertThat(findThreadsByPrefix("PD-Session-Recv-" + port)).isNotEmpty();
        assertThat(findThreadsByPrefix("PD-Session-Send-" + port)).isNotEmpty();

        session.close();
        Thread.sleep(300);

        assertThat(findThreadsByPrefix("PD-Session-Recv-" + port))
                .as("Receive thread should be dead after close")
                .isEmpty();
        assertThat(findThreadsByPrefix("PD-Session-Send-" + port))
                .as("Send thread should be dead after close")
                .isEmpty();
        session = null;
    }

    @Test
    void testTopologyCountersBeforeStart() throws Exception {
        session = new TrdpPdSession(19115);
        // Should not throw
        assertThatCode(() -> session.setTopologyCounters(42, 7))
                .doesNotThrowAnyException();
    }

    // --- Statistics tests ---

    @Test
    void testPublisherStatistics() throws Exception {
        UdpTransport receiver = new UdpTransport(19130);
        try {
            session = new TrdpPdSession(0);
            PdPublisherHandle handle = session.addPublisher(1000, "127.0.0.1", 19130, 0);
            session.start();

            assertThat(handle.getPacketsSent()).isZero();
            assertThat(handle.getSendErrors()).isZero();

            handle.putDataImmediate("data1".getBytes());
            assertThat(handle.getPacketsSent()).isEqualTo(1);

            handle.putDataImmediate("data2".getBytes());
            assertThat(handle.getPacketsSent()).isEqualTo(2);

            handle.resetStatistics();
            assertThat(handle.getPacketsSent()).isZero();
            assertThat(handle.getSendErrors()).isZero();
        } finally {
            receiver.close();
        }
    }

    @Test
    void testSubscriberPacketsReceivedCounter() throws Exception {
        int port = 19131;
        int comId = 3000;
        int packetCount = 3;

        CountDownLatch latch = new CountDownLatch(packetCount);
        PdEventListener listener = new PdEventListener() {
            @Override public void onData(PdEvent event) { latch.countDown(); }
            @Override public void onTimeout(PdEvent event) {}
            @Override public void onValidityRestored(PdEvent event) {}
        };

        session = new TrdpPdSession(port);
        PdSubscriberHandle handle = session.addSubscriber(comId, null, 0, listener);
        session.start();

        Thread.sleep(200);

        UdpTransport sender = new UdpTransport(0);
        try {
            for (int i = 0; i < packetCount; i++) {
                TrdpPdHeader header = new TrdpPdHeader();
                header.setSequenceCounter(i);
                header.setMessageType(TrdpMessageType.PD);
                header.setComId(comId);
                header.setDatasetLength(1);
                TrdpPacket packet = new TrdpPacket(header, new byte[]{(byte) i});
                sender.send(packet.encode(), InetAddress.getByName("127.0.0.1"), port);
            }

            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(handle.getPacketsReceived()).isEqualTo(packetCount);
        } finally {
            sender.close();
        }
    }

    @Test
    void testSubscriberTimeoutCounter() throws Exception {
        int port = 19132;
        int comId = 3001;
        long timeoutUs = 50_000; // 50ms

        CountDownLatch firstTimeout = new CountDownLatch(1);
        CountDownLatch secondTimeout = new CountDownLatch(2);
        CountDownLatch restoredLatch = new CountDownLatch(1);

        PdEventListener listener = new PdEventListener() {
            @Override public void onData(PdEvent event) {}
            @Override public void onTimeout(PdEvent event) {
                firstTimeout.countDown();
                secondTimeout.countDown();
            }
            @Override public void onValidityRestored(PdEvent event) { restoredLatch.countDown(); }
        };

        session = new TrdpPdSession(port);
        PdSubscriberHandle handle = session.addSubscriber(comId, null, timeoutUs, listener);
        session.start();

        // Wait for first timeout
        assertThat(firstTimeout.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(handle.getTimeoutCount()).isEqualTo(1);

        // Send a valid packet to restore validity
        UdpTransport sender = new UdpTransport(0);
        try {
            TrdpPdHeader header = new TrdpPdHeader();
            header.setSequenceCounter(0);
            header.setMessageType(TrdpMessageType.PD);
            header.setComId(comId);
            header.setDatasetLength(1);
            TrdpPacket packet = new TrdpPacket(header, new byte[]{1});
            sender.send(packet.encode(), InetAddress.getByName("127.0.0.1"), port);

            assertThat(restoredLatch.await(1, TimeUnit.SECONDS)).isTrue();

            // Wait for second timeout
            assertThat(secondTimeout.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(handle.getTimeoutCount()).isEqualTo(2);
        } finally {
            sender.close();
        }
    }

    // --- FCS error counter tests ---

    @Test
    void testFcsErrorCounterInitiallyZero() throws Exception {
        session = new TrdpPdSession(19160);
        assertThat(session.getFcsErrorCount()).isZero();
    }

    @Test
    void testFcsErrorCounterIncrementsOnCorruptedPacket() throws Exception {
        int port = 19161;
        int comId = 4000;

        session = new TrdpPdSession(port);
        session.addSubscriber(comId, null, 0, new NoOpPdEventListener());
        session.start();

        Thread.sleep(200);

        // Build a valid packet, then corrupt the header to cause FCS mismatch
        UdpTransport sender = new UdpTransport(0);
        try {
            TrdpPdHeader header = new TrdpPdHeader();
            header.setSequenceCounter(0);
            header.setMessageType(TrdpMessageType.PD);
            header.setComId(comId);
            header.setDatasetLength(1);
            TrdpPacket packet = new TrdpPacket(header, new byte[]{42});
            byte[] encoded = packet.encode();

            // Corrupt a byte in the header (not the FCS field) to trigger FCS mismatch
            encoded[0] ^= 0xFF;

            sender.send(encoded, InetAddress.getByName("127.0.0.1"), port);

            Thread.sleep(500);
            assertThat(session.getFcsErrorCount()).isEqualTo(1);
        } finally {
            sender.close();
        }
    }

    @Test
    void testFcsErrorCounterMultipleCorruptedPackets() throws Exception {
        int port = 19162;
        int comId = 4001;

        session = new TrdpPdSession(port);
        session.addSubscriber(comId, null, 0, new NoOpPdEventListener());
        session.start();

        Thread.sleep(200);

        UdpTransport sender = new UdpTransport(0);
        try {
            for (int i = 0; i < 3; i++) {
                TrdpPdHeader header = new TrdpPdHeader();
                header.setSequenceCounter(i);
                header.setMessageType(TrdpMessageType.PD);
                header.setComId(comId);
                header.setDatasetLength(1);
                TrdpPacket packet = new TrdpPacket(header, new byte[]{(byte) i});
                byte[] encoded = packet.encode();
                encoded[0] ^= 0xFF;
                sender.send(encoded, InetAddress.getByName("127.0.0.1"), port);
            }

            Thread.sleep(500);
            assertThat(session.getFcsErrorCount()).isEqualTo(3);
        } finally {
            sender.close();
        }
    }

    @Test
    void testFcsErrorDoesNotIncrementSubscriberPacketsReceived() throws Exception {
        int port = 19163;
        int comId = 4002;

        CountDownLatch validLatch = new CountDownLatch(1);
        PdEventListener listener = new PdEventListener() {
            @Override public void onData(PdEvent event) { validLatch.countDown(); }
            @Override public void onTimeout(PdEvent event) {}
            @Override public void onValidityRestored(PdEvent event) {}
        };

        session = new TrdpPdSession(port);
        PdSubscriberHandle handle = session.addSubscriber(comId, null, 0, listener);
        session.start();

        Thread.sleep(200);

        UdpTransport sender = new UdpTransport(0);
        try {
            // Send a corrupted packet
            TrdpPdHeader header = new TrdpPdHeader();
            header.setSequenceCounter(0);
            header.setMessageType(TrdpMessageType.PD);
            header.setComId(comId);
            header.setDatasetLength(1);
            TrdpPacket packet = new TrdpPacket(header, new byte[]{1});
            byte[] encoded = packet.encode();
            encoded[0] ^= 0xFF;
            sender.send(encoded, InetAddress.getByName("127.0.0.1"), port);

            Thread.sleep(300);

            // FCS error should be counted, but subscriber should not see the packet
            assertThat(session.getFcsErrorCount()).isEqualTo(1);
            assertThat(handle.getPacketsReceived()).isZero();

            // Now send a valid packet
            header = new TrdpPdHeader();
            header.setSequenceCounter(1);
            header.setMessageType(TrdpMessageType.PD);
            header.setComId(comId);
            header.setDatasetLength(1);
            packet = new TrdpPacket(header, new byte[]{2});
            sender.send(packet.encode(), InetAddress.getByName("127.0.0.1"), port);

            assertThat(validLatch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(handle.getPacketsReceived()).isEqualTo(1);
            assertThat(session.getFcsErrorCount()).isEqualTo(1);
        } finally {
            sender.close();
        }
    }

    // --- Dynamic add/remove tests ---

    @Test
    void testAddCyclicPublisherAfterStartSendsData() throws Exception {
        UdpTransport receiver = new UdpTransport(19200);
        try {
            session = new TrdpPdSession(0);
            session.start();

            PdPublisherHandle pub = session.addPublisher(1000, "127.0.0.1", 19200, 50_000);
            pub.putData("dynamic".getBytes());

            byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
            int len = receiver.receive(buffer, 2000);
            assertThat(len).isGreaterThan(0);

            TrdpPacket packet = TrdpPacket.decode(java.util.Arrays.copyOf(buffer, len));
            assertThat(packet.getPayload()).isEqualTo("dynamic".getBytes());
        } finally {
            receiver.close();
        }
    }

    @Test
    void testAddNonCyclicPublisherAfterStartSupportsImmediate() throws Exception {
        UdpTransport receiver = new UdpTransport(19201);
        try {
            session = new TrdpPdSession(0);
            session.start();

            PdPublisherHandle pub = session.addPublisher(1000, "127.0.0.1", 19201, 0);
            pub.putDataImmediate("immediate".getBytes());

            byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
            int len = receiver.receive(buffer, 2000);
            assertThat(len).isGreaterThan(0);

            TrdpPacket packet = TrdpPacket.decode(java.util.Arrays.copyOf(buffer, len));
            assertThat(packet.getPayload()).isEqualTo("immediate".getBytes());
        } finally {
            receiver.close();
        }
    }

    @Test
    void testAddSubscriberAfterStartReceivesData() throws Exception {
        int port = 19202;
        int comId = 5000;

        session = new TrdpPdSession(port);
        session.start();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<byte[]> receivedData = new AtomicReference<>();
        session.addSubscriber(comId, null, 0, new PdEventListener() {
            @Override public void onData(PdEvent event) {
                receivedData.set(event.getData());
                latch.countDown();
            }
            @Override public void onTimeout(PdEvent event) {}
            @Override public void onValidityRestored(PdEvent event) {}
        });

        Thread.sleep(100);

        UdpTransport sender = new UdpTransport(0);
        try {
            TrdpPdHeader header = new TrdpPdHeader();
            header.setSequenceCounter(0);
            header.setMessageType(TrdpMessageType.PD);
            header.setComId(comId);
            header.setDatasetLength(4);
            TrdpPacket packet = new TrdpPacket(header, new byte[]{1, 2, 3, 4});
            sender.send(packet.encode(), InetAddress.getByName("127.0.0.1"), port);

            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(receivedData.get()).isEqualTo(new byte[]{1, 2, 3, 4});
        } finally {
            sender.close();
        }
    }

    @Test
    void testAddSubscriberAfterStartTimeoutWorks() throws Exception {
        int port = 19203;
        int comId = 5001;

        session = new TrdpPdSession(port);
        session.start();

        CountDownLatch timeoutLatch = new CountDownLatch(1);
        PdSubscriberHandle handle = session.addSubscriber(comId, null, 50_000, new PdEventListener() {
            @Override public void onData(PdEvent event) {}
            @Override public void onTimeout(PdEvent event) { timeoutLatch.countDown(); }
            @Override public void onValidityRestored(PdEvent event) {}
        });

        assertThat(timeoutLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(handle.isTimedOut()).isTrue();
    }

    @Test
    void testRemovePublisher() throws Exception {
        session = new TrdpPdSession(19204);
        session.addPublisher(1000, "127.0.0.1", 17224, 0);
        assertThat(session.getPublisherCount()).isEqualTo(1);

        PdPublisherHandle removed = session.removePublisher(1000);
        assertThat(removed).isNotNull();
        assertThat(removed.getComId()).isEqualTo(1000);
        assertThat(session.getPublisherCount()).isZero();

        // Re-remove returns null
        assertThat(session.removePublisher(1000)).isNull();
    }

    @Test
    void testRemovePublisherCancelsCyclicTask() throws Exception {
        UdpTransport receiver = new UdpTransport(19205);
        try {
            session = new TrdpPdSession(0);
            PdPublisherHandle pub = session.addPublisher(1000, "127.0.0.1", 19205, 50_000);
            session.start();
            pub.putData("cyclic".getBytes());

            // Wait for at least one packet
            byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
            int len = receiver.receive(buffer, 2000);
            assertThat(len).isGreaterThan(0);

            // Remove publisher
            session.removePublisher(1000);
            Thread.sleep(200);

            // No more packets should arrive
            len = receiver.receive(buffer, 300);
            assertThat(len).isZero();
        } finally {
            receiver.close();
        }
    }

    @Test
    void testRemovePublisherBeforeStart() throws Exception {
        session = new TrdpPdSession(19206);
        session.addPublisher(1000, "127.0.0.1", 17224, 100_000);

        PdPublisherHandle removed = session.removePublisher(1000);
        assertThat(removed).isNotNull();
        assertThat(session.getPublisherCount()).isZero();
    }

    @Test
    void testRemoveSubscribers() throws Exception {
        session = new TrdpPdSession(19207);
        session.addSubscriber(2000, null, 0, new NoOpPdEventListener());
        session.addSubscriber(2000, null, 0, new NoOpPdEventListener());
        assertThat(session.getSubscriberCount()).isEqualTo(2);

        List<PdSubscriberHandle> removed = session.removeSubscribers(2000);
        assertThat(removed).hasSize(2);
        assertThat(session.getSubscriberCount()).isZero();
    }

    @Test
    void testRemoveSubscribersStopsCallbacks() throws Exception {
        int port = 19208;
        int comId = 5002;
        AtomicInteger callbackCount = new AtomicInteger();
        CountDownLatch firstLatch = new CountDownLatch(1);

        session = new TrdpPdSession(port);
        session.addSubscriber(comId, null, 0, new PdEventListener() {
            @Override public void onData(PdEvent event) {
                callbackCount.incrementAndGet();
                firstLatch.countDown();
            }
            @Override public void onTimeout(PdEvent event) {}
            @Override public void onValidityRestored(PdEvent event) {}
        });
        session.start();

        UdpTransport sender = new UdpTransport(0);
        try {
            TrdpPdHeader header = new TrdpPdHeader();
            header.setSequenceCounter(0);
            header.setMessageType(TrdpMessageType.PD);
            header.setComId(comId);
            header.setDatasetLength(1);
            TrdpPacket packet = new TrdpPacket(header, new byte[]{1});
            sender.send(packet.encode(), InetAddress.getByName("127.0.0.1"), port);

            assertThat(firstLatch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(callbackCount.get()).isEqualTo(1);

            // Remove subscribers
            session.removeSubscribers(comId);
            Thread.sleep(100);

            // Send another packet — should not trigger callback
            header = new TrdpPdHeader();
            header.setSequenceCounter(1);
            header.setMessageType(TrdpMessageType.PD);
            header.setComId(comId);
            header.setDatasetLength(1);
            packet = new TrdpPacket(header, new byte[]{2});
            sender.send(packet.encode(), InetAddress.getByName("127.0.0.1"), port);
            Thread.sleep(300);

            assertThat(callbackCount.get()).isEqualTo(1);
        } finally {
            sender.close();
        }
    }

    @Test
    void testRemoveNonexistentPublisherReturnsNull() throws Exception {
        session = new TrdpPdSession(19209);
        assertThat(session.removePublisher(9999)).isNull();
    }

    @Test
    void testRemoveNonexistentSubscribersReturnsEmptyList() throws Exception {
        session = new TrdpPdSession(19210);
        assertThat(session.removeSubscribers(9999)).isEmpty();
    }

    @Test
    void testAddPublisherAfterStartDuplicateThrows() throws Exception {
        session = new TrdpPdSession(19211);
        session.addPublisher(1000, "127.0.0.1", 17224, 0);
        session.start();

        assertThatThrownBy(() -> session.addPublisher(1000, "127.0.0.2", 17224, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testReAddPublisherAfterRemove() throws Exception {
        session = new TrdpPdSession(19212);
        session.addPublisher(1000, "127.0.0.1", 17224, 0);
        session.removePublisher(1000);

        PdPublisherHandle handle = session.addPublisher(1000, "127.0.0.1", 17224, 50_000);
        assertThat(handle.getComId()).isEqualTo(1000);
        assertThat(session.getPublisherCount()).isEqualTo(1);
    }

    @Test
    void testReAddSubscriberAfterRemove() throws Exception {
        session = new TrdpPdSession(19213);
        session.addSubscriber(2000, null, 0, new NoOpPdEventListener());
        session.removeSubscribers(2000);
        assertThat(session.getSubscriberCount()).isZero();

        session.addSubscriber(2000, null, 0, new NoOpPdEventListener());
        assertThat(session.getSubscriberCount()).isEqualTo(1);
    }

    // --- Traffic shaping tests ---

    @Test
    void testTrafficShapingEnabledByDefault() throws Exception {
        session = new TrdpPdSession(19120);
        assertThat(session.isTrafficShapingEnabled()).isTrue();
    }

    @Test
    void testSetTrafficShapingDisabled() throws Exception {
        session = new TrdpPdSession(19121);
        session.setTrafficShapingEnabled(false);
        assertThat(session.isTrafficShapingEnabled()).isFalse();
    }

    @Test
    void testSetTrafficShapingAfterStartThrows() throws Exception {
        session = new TrdpPdSession(19122);
        session.start();

        assertThatThrownBy(() -> session.setTrafficShapingEnabled(false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testStaggeredDelaysTenPublishersSameInterval() throws Exception {
        session = new TrdpPdSession(19123);
        for (int i = 0; i < 10; i++) {
            session.addPublisher(1000 + i, "127.0.0.1", 17224, 10_000);
        }

        Map<Integer, Long> delays = session.computeInitialDelays();
        assertThat(delays).hasSize(10);

        // offset = 10_000 / 10 = 1_000us per publisher
        List<Long> sortedDelays = delays.values().stream().sorted().toList();
        assertThat(sortedDelays).containsExactly(
                0L, 1000L, 2000L, 3000L, 4000L, 5000L, 6000L, 7000L, 8000L, 9000L);
    }

    @Test
    void testStaggeredDelaysSinglePublisherUnchanged() throws Exception {
        session = new TrdpPdSession(19124);
        session.addPublisher(1000, "127.0.0.1", 17224, 50_000);

        Map<Integer, Long> delays = session.computeInitialDelays();
        // Single publisher: offset = 50_000, 2 * 50_000 > 50_000 → no stagger
        assertThat(delays).containsEntry(1000, 50_000L);
    }

    @Test
    void testStaggeredDelaysDifferentIntervalsIndependent() throws Exception {
        session = new TrdpPdSession(19125);
        // Group 1: 2 publishers at 100_000us
        session.addPublisher(1000, "127.0.0.1", 17224, 100_000);
        session.addPublisher(1001, "127.0.0.1", 17224, 100_000);
        // Group 2: 2 publishers at 200_000us
        session.addPublisher(2000, "127.0.0.1", 17224, 200_000);
        session.addPublisher(2001, "127.0.0.1", 17224, 200_000);

        Map<Integer, Long> delays = session.computeInitialDelays();

        // Group 1: offset = 50_000. Delays: 0, 50_000
        List<Long> group1Delays = delays.entrySet().stream()
                .filter(e -> e.getKey() >= 1000 && e.getKey() < 2000)
                .map(Map.Entry::getValue)
                .sorted()
                .toList();
        assertThat(group1Delays).containsExactly(0L, 50_000L);

        // Group 2: offset = 100_000. Delays: 0, 100_000
        List<Long> group2Delays = delays.entrySet().stream()
                .filter(e -> e.getKey() >= 2000)
                .map(Map.Entry::getValue)
                .sorted()
                .toList();
        assertThat(group2Delays).containsExactly(0L, 100_000L);
    }

    @Test
    void testStaggeredDelaysDisabled() throws Exception {
        session = new TrdpPdSession(19126);
        session.setTrafficShapingEnabled(false);
        session.addPublisher(1000, "127.0.0.1", 17224, 100_000);
        session.addPublisher(1001, "127.0.0.1", 17224, 100_000);

        Map<Integer, Long> delays = session.computeInitialDelays();
        // All delays should equal the interval (no stagger)
        assertThat(delays.values()).allMatch(d -> d == 100_000L);
    }

    @Test
    void testNonCyclicPublishersExcludedFromDelays() throws Exception {
        session = new TrdpPdSession(19127);
        session.addPublisher(1000, "127.0.0.1", 17224, 0);       // non-cyclic
        session.addPublisher(1001, "127.0.0.1", 17224, 100_000);  // cyclic

        Map<Integer, Long> delays = session.computeInitialDelays();
        assertThat(delays).hasSize(1);
        assertThat(delays).containsKey(1001);
        assertThat(delays).doesNotContainKey(1000);
    }

    private static Set<String> findThreadsByPrefix(String prefix) {
        return Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .filter(name -> name.startsWith(prefix))
                .collect(Collectors.toSet());
    }

    private static class NoOpPdEventListener implements PdEventListener {
        @Override public void onData(PdEvent event) {}
        @Override public void onTimeout(PdEvent event) {}
        @Override public void onValidityRestored(PdEvent event) {}
    }

    @Test
    void testOversizedSupplierDataSkippedAndCounted() throws Exception {
        session = new TrdpPdSession(0);
        PdPublisherHandle pub = session.addPublisher(19360, "127.0.0.1", 19961, 20_000);
        pub.setDataSupplier(() -> new byte[TrdpConstants.TRDP_MAX_PD_DATA_SIZE + 1]);
        session.start();

        Thread.sleep(150);

        assertThat(pub.getSendErrors()).as("oversized supplier data must be counted as send errors").isGreaterThan(0);
        assertThat(pub.getPacketsSent()).as("oversized supplier data must never be sent").isZero();
    }
}
