package com.trdp.pd;

import com.trdp.network.UdpTransport;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpMessageType;
import com.trdp.protocol.TrdpPacket;
import com.trdp.protocol.TrdpPdHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class PdSubscriberEdgeCaseTest {

    private PdSubscriber subscriber;
    private UdpTransport sender;

    @AfterEach
    void tearDown() {
        if (subscriber != null) subscriber.close();
        if (sender != null) sender.close();
    }

    /** Helper that ignores timeout and validity-restored events. */
    private static PdEventListener dataOnly(DataCallback callback) {
        return new PdEventListener() {
            @Override public void onData(PdEvent event) { callback.accept(event); }
            @Override public void onTimeout(PdEvent event) {}
            @Override public void onValidityRestored(PdEvent event) {}
        };
    }

    @FunctionalInterface
    interface DataCallback { void accept(PdEvent event); }

    @Test
    void testReceivesMatchingComId() throws Exception {
        int comId = 8000;
        int port = 19700;

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PdEvent> received = new AtomicReference<>();

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.addListener(dataOnly(event -> {
            received.set(event);
            latch.countDown();
        }));
        subscriber.start();

        Thread.sleep(200);

        // Send a matching PD packet
        sender = new UdpTransport(0);
        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(42);
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(comId);
        header.setDatasetLength(3);

        TrdpPacket packet = new TrdpPacket(header, new byte[]{10, 20, 30});
        sender.send(packet.encode(), InetAddress.getLoopbackAddress(), port);

        boolean done = latch.await(3, TimeUnit.SECONDS);
        assertThat(done).isTrue();

        PdEvent event = received.get();
        assertThat(event.getData()).containsExactly(10, 20, 30);
        assertThat(event.getSequenceCounter()).isEqualTo(42);
        assertThat(event.getComId()).isEqualTo(comId);
        assertThat(event.getType()).isEqualTo(PdEvent.Type.DATA);
        assertThat(event.getSourceAddress()).isNotNull();
        assertThat(event.getResultCode()).isEqualTo(0);
    }

    @Test
    void testIgnoresNonMatchingComId() throws Exception {
        int comId = 8001;
        int port = 19701;

        AtomicInteger callCount = new AtomicInteger(0);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.addListener(dataOnly(event -> callCount.incrementAndGet()));
        subscriber.start();

        Thread.sleep(200);

        // Send a packet with wrong comId
        sender = new UdpTransport(0);
        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(0);
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(9999); // Wrong comId
        header.setDatasetLength(1);

        TrdpPacket packet = new TrdpPacket(header, new byte[]{1});
        sender.send(packet.encode(), InetAddress.getLoopbackAddress(), port);

        Thread.sleep(500);
        assertThat(callCount.get()).isEqualTo(0);
    }

    @Test
    void testIgnoresNonPdMessageType() throws Exception {
        int comId = 8002;
        int port = 19702;

        AtomicInteger callCount = new AtomicInteger(0);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.addListener(dataOnly(event -> callCount.incrementAndGet()));
        subscriber.start();

        Thread.sleep(200);

        // Send a PD_REQUEST (not PD or PD_REPLY), subscriber should ignore it
        sender = new UdpTransport(0);
        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(0);
        header.setMessageType(TrdpMessageType.PD_REQUEST);
        header.setComId(comId);
        header.setDatasetLength(1);

        TrdpPacket packet = new TrdpPacket(header, new byte[]{1});
        sender.send(packet.encode(), InetAddress.getLoopbackAddress(), port);

        Thread.sleep(500);
        assertThat(callCount.get()).isEqualTo(0);
    }

    @Test
    void testAcceptsPdReplyMessageType() throws Exception {
        int comId = 8003;
        int port = 19703;

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PdEvent> received = new AtomicReference<>();

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.addListener(dataOnly(event -> {
            received.set(event);
            latch.countDown();
        }));
        subscriber.start();

        Thread.sleep(200);

        // Send a PD_REPLY (Pull pattern response)
        sender = new UdpTransport(0);
        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(0);
        header.setMessageType(TrdpMessageType.PD_REPLY);
        header.setComId(comId);
        header.setDatasetLength(2);

        TrdpPacket packet = new TrdpPacket(header, new byte[]{5, 6});
        sender.send(packet.encode(), InetAddress.getLoopbackAddress(), port);

        boolean done = latch.await(3, TimeUnit.SECONDS);
        assertThat(done).isTrue();
        assertThat(received.get().getType()).isEqualTo(PdEvent.Type.REPLY);
    }

    @Test
    void testRequestSendsCorrectPacket() throws Exception {
        int comId = 8004;
        int subscriberPort = 19704;
        int targetPort = 19705;

        subscriber = new PdSubscriber(comId, "127.0.0.1", subscriberPort);

        // Listen on the target port
        UdpTransport listener = new UdpTransport(targetPort);
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];

        subscriber.request(comId, "127.0.0.1", targetPort, 0, null);

        int len = listener.receive(buffer, 2000);
        assertThat(len).isGreaterThan(0);

        byte[] packetBytes = new byte[len];
        System.arraycopy(buffer, 0, packetBytes, 0, len);
        TrdpPacket packet = TrdpPacket.decode(packetBytes);

        assertThat(packet.getHeader().getMessageType()).isEqualTo(TrdpMessageType.PD_REQUEST);
        assertThat(packet.getHeader().getComId()).isEqualTo(comId);

        listener.close();
    }

    @Test
    void testRequestWithReplyComIdAndIp() throws Exception {
        int comId = 8005;
        int subscriberPort = 19706;
        int targetPort = 19707;

        subscriber = new PdSubscriber(comId, "127.0.0.1", subscriberPort);

        UdpTransport listener = new UdpTransport(targetPort);
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];

        subscriber.request(comId, "127.0.0.1", targetPort, 9000, "192.168.1.100");

        int len = listener.receive(buffer, 2000);
        assertThat(len).isGreaterThan(0);

        byte[] packetBytes = new byte[len];
        System.arraycopy(buffer, 0, packetBytes, 0, len);
        TrdpPacket packet = TrdpPacket.decode(packetBytes);
        TrdpPdHeader pdHeader = (TrdpPdHeader) packet.getHeader();

        assertThat(pdHeader.getReplyComId()).isEqualTo(9000);
        assertThat(pdHeader.getReplyIpAddress()).isNotEqualTo(0);

        listener.close();
    }

    @Test
    void testListenerExceptionDoesNotStopOtherListeners() throws Exception {
        int comId = 8006;
        int port = 19708;

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<byte[]> received = new AtomicReference<>();

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);

        // First listener throws
        subscriber.addListener(new PdEventListener() {
            @Override public void onData(PdEvent event) {
                throw new RuntimeException("Listener error");
            }
            @Override public void onTimeout(PdEvent event) {}
            @Override public void onValidityRestored(PdEvent event) {}
        });

        // Second listener should still be called
        subscriber.addListener(dataOnly(event -> {
            received.set(event.getData());
            latch.countDown();
        }));

        subscriber.start();
        Thread.sleep(200);

        sender = new UdpTransport(0);
        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(0);
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(comId);
        header.setDatasetLength(1);
        TrdpPacket packet = new TrdpPacket(header, new byte[]{77});
        sender.send(packet.encode(), InetAddress.getLoopbackAddress(), port);

        boolean done = latch.await(3, TimeUnit.SECONDS);
        assertThat(done).isTrue();
        assertThat(received.get()).containsExactly(77);
    }

    @Test
    void testTimeoutNotification() throws Exception {
        int comId = 8007;
        int port = 19709;

        CountDownLatch timeoutLatch = new CountDownLatch(1);
        AtomicReference<PdEvent> timeoutEvent = new AtomicReference<>();

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.addListener(new PdEventListener() {
            @Override public void onData(PdEvent event) {}
            @Override public void onTimeout(PdEvent event) {
                timeoutEvent.set(event);
                timeoutLatch.countDown();
            }
            @Override public void onValidityRestored(PdEvent event) {}
        });
        subscriber.start();

        // Wait for the timeout to fire (DEFAULT_PD_TIMEOUT_US is 100ms)
        boolean done = timeoutLatch.await(3, TimeUnit.SECONDS);
        assertThat(done).isTrue();

        PdEvent event = timeoutEvent.get();
        assertThat(event.getType()).isEqualTo(PdEvent.Type.TIMEOUT);
        assertThat(event.getComId()).isEqualTo(comId);
        assertThat(event.getData()).isNull();
        assertThat(event.getResultCode()).isEqualTo(1);
    }

    @Test
    void testTimeoutFiresOnlyOnce() throws Exception {
        int comId = 8008;
        int port = 19710;

        AtomicInteger timeoutCount = new AtomicInteger(0);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.addListener(new PdEventListener() {
            @Override public void onData(PdEvent event) {}
            @Override public void onTimeout(PdEvent event) { timeoutCount.incrementAndGet(); }
            @Override public void onValidityRestored(PdEvent event) {}
        });
        subscriber.start();

        // Wait long enough for multiple timeout periods
        Thread.sleep(2500);
        assertThat(timeoutCount.get()).isEqualTo(1);
    }

    @Test
    void testValidityRestoredAfterTimeout() throws Exception {
        int comId = 8009;
        int port = 19711;

        CountDownLatch timeoutLatch = new CountDownLatch(1);
        CountDownLatch restoredLatch = new CountDownLatch(1);
        CountDownLatch dataLatch = new CountDownLatch(1);
        AtomicReference<PdEvent> restoredEvent = new AtomicReference<>();

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.addListener(new PdEventListener() {
            @Override public void onData(PdEvent event) { dataLatch.countDown(); }
            @Override public void onTimeout(PdEvent event) { timeoutLatch.countDown(); }
            @Override public void onValidityRestored(PdEvent event) {
                restoredEvent.set(event);
                restoredLatch.countDown();
            }
        });
        subscriber.start();

        // Wait for timeout
        boolean timedOut = timeoutLatch.await(3, TimeUnit.SECONDS);
        assertThat(timedOut).isTrue();

        // Now send a valid packet to trigger validity-restored
        sender = new UdpTransport(0);
        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(1);
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(comId);
        header.setDatasetLength(2);
        TrdpPacket packet = new TrdpPacket(header, new byte[]{99, 100});
        sender.send(packet.encode(), InetAddress.getLoopbackAddress(), port);

        boolean restored = restoredLatch.await(3, TimeUnit.SECONDS);
        assertThat(restored).isTrue();

        PdEvent event = restoredEvent.get();
        assertThat(event.getType()).isEqualTo(PdEvent.Type.VALIDITY_RESTORED);
        assertThat(event.getComId()).isEqualTo(comId);
        assertThat(event.getData()).containsExactly(99, 100);
        assertThat(event.getResultCode()).isEqualTo(0);

        // onData should also be called for the same packet
        boolean dataReceived = dataLatch.await(1, TimeUnit.SECONDS);
        assertThat(dataReceived).isTrue();
    }

    @Test
    void testPdEventDefensiveCopy() {
        byte[] original = {1, 2, 3};
        PdEvent event = new PdEvent(PdEvent.Type.DATA, 100, original, 0,
                null, null, 0, 0, 0);

        // Mutating the original should not affect the event
        original[0] = 99;
        assertThat(event.getData()).containsExactly(1, 2, 3);

        // Mutating the returned data should not affect the event
        byte[] returned = event.getData();
        returned[0] = 88;
        assertThat(event.getData()).containsExactly(1, 2, 3);
    }

    @Test
    void testPdEventNullDataForTimeout() {
        PdEvent event = new PdEvent(PdEvent.Type.TIMEOUT, 100, null, 0,
                null, null, 0, 0, 1);
        assertThat(event.getData()).isNull();
        assertThat(event.getResultCode()).isEqualTo(1);
    }

    @Test
    void testCustomTimeoutParameter() throws Exception {
        int comId = 8011;
        int port = 19713;
        long timeoutUs = 50_000; // 50ms

        CountDownLatch timeoutLatch = new CountDownLatch(1);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port, timeoutUs);
        subscriber.addListener(new PdEventListener() {
            @Override public void onData(PdEvent event) {}
            @Override public void onTimeout(PdEvent event) { timeoutLatch.countDown(); }
            @Override public void onValidityRestored(PdEvent event) {}
        });
        subscriber.start();

        // Should time out within ~150ms (50ms timeout + socket timeout + scheduling slack)
        boolean done = timeoutLatch.await(1, TimeUnit.SECONDS);
        assertThat(done).isTrue();
    }

    @Test
    void testIsTimedOutAccessor() throws Exception {
        int comId = 8012;
        int port = 19714;
        long timeoutUs = 50_000; // 50ms

        CountDownLatch timeoutLatch = new CountDownLatch(1);
        CountDownLatch dataLatch = new CountDownLatch(1);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port, timeoutUs);
        subscriber.addListener(new PdEventListener() {
            @Override public void onData(PdEvent event) { dataLatch.countDown(); }
            @Override public void onTimeout(PdEvent event) { timeoutLatch.countDown(); }
            @Override public void onValidityRestored(PdEvent event) {}
        });

        assertThat(subscriber.isTimedOut()).isFalse();

        subscriber.start();

        boolean done = timeoutLatch.await(1, TimeUnit.SECONDS);
        assertThat(done).isTrue();
        assertThat(subscriber.isTimedOut()).isTrue();

        // Send a valid packet to clear timeout
        sender = new UdpTransport(0);
        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(0);
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(comId);
        header.setDatasetLength(1);
        TrdpPacket packet = new TrdpPacket(header, new byte[]{1});
        sender.send(packet.encode(), InetAddress.getLoopbackAddress(), port);

        // Wait for the packet to be processed, then check immediately
        assertThat(dataLatch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(subscriber.isTimedOut()).isFalse();
    }

    @Test
    void testTimeoutNotResetByNonMatchingPacket() throws Exception {
        int comId = 8013;
        int port = 19715;
        long timeoutUs = 80_000; // 80ms

        CountDownLatch timeoutLatch = new CountDownLatch(1);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port, timeoutUs);
        subscriber.addListener(new PdEventListener() {
            @Override public void onData(PdEvent event) {}
            @Override public void onTimeout(PdEvent event) { timeoutLatch.countDown(); }
            @Override public void onValidityRestored(PdEvent event) {}
        });
        subscriber.start();

        // Send a packet with wrong comId — should NOT reset the timeout timer
        sender = new UdpTransport(0);
        Thread.sleep(40); // wait ~half the timeout
        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(0);
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(9999); // non-matching
        header.setDatasetLength(1);
        TrdpPacket packet = new TrdpPacket(header, new byte[]{1});
        sender.send(packet.encode(), InetAddress.getLoopbackAddress(), port);

        // Timeout should still fire despite the non-matching packet
        boolean done = timeoutLatch.await(1, TimeUnit.SECONDS);
        assertThat(done).isTrue();
    }

    @Test
    void testTimeoutResumesAfterValidityRestored() throws Exception {
        int comId = 8014;
        int port = 19716;
        long timeoutUs = 50_000; // 50ms

        AtomicInteger timeoutCount = new AtomicInteger(0);
        CountDownLatch firstTimeout = new CountDownLatch(1);
        CountDownLatch secondTimeout = new CountDownLatch(2);
        CountDownLatch restoredLatch = new CountDownLatch(1);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port, timeoutUs);
        subscriber.addListener(new PdEventListener() {
            @Override public void onData(PdEvent event) {}
            @Override public void onTimeout(PdEvent event) {
                timeoutCount.incrementAndGet();
                firstTimeout.countDown();
                secondTimeout.countDown();
            }
            @Override public void onValidityRestored(PdEvent event) { restoredLatch.countDown(); }
        });
        subscriber.start();

        // Wait for first timeout
        assertThat(firstTimeout.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(timeoutCount.get()).isEqualTo(1);

        // Send a valid packet to restore validity
        sender = new UdpTransport(0);
        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(0);
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(comId);
        header.setDatasetLength(1);
        TrdpPacket packet = new TrdpPacket(header, new byte[]{1});
        sender.send(packet.encode(), InetAddress.getLoopbackAddress(), port);

        assertThat(restoredLatch.await(1, TimeUnit.SECONDS)).isTrue();

        // Wait for second timeout (data stops again)
        assertThat(secondTimeout.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(timeoutCount.get()).isEqualTo(2);
    }

    @Test
    void testPacketsReceivedCounter() throws Exception {
        int comId = 8020;
        int port = 19730;
        int packetCount = 5;

        CountDownLatch latch = new CountDownLatch(packetCount);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port, 0);
        subscriber.addListener(dataOnly(event -> latch.countDown()));
        subscriber.start();

        Thread.sleep(200);

        sender = new UdpTransport(0);
        for (int i = 0; i < packetCount; i++) {
            TrdpPdHeader header = new TrdpPdHeader();
            header.setSequenceCounter(i);
            header.setMessageType(TrdpMessageType.PD);
            header.setComId(comId);
            header.setDatasetLength(1);
            TrdpPacket packet = new TrdpPacket(header, new byte[]{(byte) i});
            sender.send(packet.encode(), InetAddress.getLoopbackAddress(), port);
        }

        boolean done = latch.await(3, TimeUnit.SECONDS);
        assertThat(done).isTrue();
        assertThat(subscriber.getPacketsReceived()).isEqualTo(packetCount);
    }

    @Test
    void testTimeoutCountIncrements() throws Exception {
        int comId = 8021;
        int port = 19731;
        long timeoutUs = 50_000; // 50ms

        CountDownLatch firstTimeout = new CountDownLatch(1);
        CountDownLatch secondTimeout = new CountDownLatch(2);
        CountDownLatch restoredLatch = new CountDownLatch(1);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port, timeoutUs);
        subscriber.addListener(new PdEventListener() {
            @Override public void onData(PdEvent event) {}
            @Override public void onTimeout(PdEvent event) {
                firstTimeout.countDown();
                secondTimeout.countDown();
            }
            @Override public void onValidityRestored(PdEvent event) { restoredLatch.countDown(); }
        });
        subscriber.start();

        // Wait for first timeout
        assertThat(firstTimeout.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(subscriber.getTimeoutCount()).isEqualTo(1);

        // Send a valid packet to restore validity
        sender = new UdpTransport(0);
        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(0);
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(comId);
        header.setDatasetLength(1);
        TrdpPacket packet = new TrdpPacket(header, new byte[]{1});
        sender.send(packet.encode(), InetAddress.getLoopbackAddress(), port);

        assertThat(restoredLatch.await(1, TimeUnit.SECONDS)).isTrue();

        // Wait for second timeout
        assertThat(secondTimeout.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(subscriber.getTimeoutCount()).isEqualTo(2);
    }

    @Test
    void testResetStatisticsIncludesNewCounters() throws Exception {
        int comId = 8022;
        int port = 19732;
        long timeoutUs = 50_000; // 50ms

        CountDownLatch timeoutLatch = new CountDownLatch(1);
        CountDownLatch dataLatch = new CountDownLatch(1);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port, timeoutUs);
        subscriber.addListener(new PdEventListener() {
            @Override public void onData(PdEvent event) { dataLatch.countDown(); }
            @Override public void onTimeout(PdEvent event) { timeoutLatch.countDown(); }
            @Override public void onValidityRestored(PdEvent event) {}
        });
        subscriber.start();

        // Wait for timeout to increment timeoutCount
        assertThat(timeoutLatch.await(1, TimeUnit.SECONDS)).isTrue();

        // Send a valid packet to increment packetsReceived
        sender = new UdpTransport(0);
        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(0);
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(comId);
        header.setDatasetLength(1);
        TrdpPacket packet = new TrdpPacket(header, new byte[]{1});
        sender.send(packet.encode(), InetAddress.getLoopbackAddress(), port);

        assertThat(dataLatch.await(1, TimeUnit.SECONDS)).isTrue();

        assertThat(subscriber.getPacketsReceived()).isGreaterThan(0);
        assertThat(subscriber.getTimeoutCount()).isGreaterThan(0);

        subscriber.resetStatistics();

        assertThat(subscriber.getPacketsReceived()).isZero();
        assertThat(subscriber.getTimeoutCount()).isZero();
    }

    @Test
    void testEventSourceAddressPopulated() throws Exception {
        int comId = 8010;
        int port = 19712;

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PdEvent> received = new AtomicReference<>();

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.addListener(dataOnly(event -> {
            received.set(event);
            latch.countDown();
        }));
        subscriber.start();

        Thread.sleep(200);

        sender = new UdpTransport(0);
        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(0);
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(comId);
        header.setDatasetLength(1);
        TrdpPacket packet = new TrdpPacket(header, new byte[]{1});
        sender.send(packet.encode(), InetAddress.getLoopbackAddress(), port);

        boolean done = latch.await(3, TimeUnit.SECONDS);
        assertThat(done).isTrue();
        assertThat(received.get().getSourceAddress()).isEqualTo(InetAddress.getLoopbackAddress());
    }
}
