package com.trdp.pd;

import com.trdp.network.UdpTransport;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpMessageType;
import com.trdp.protocol.TrdpPacket;
import com.trdp.protocol.TrdpPdHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
    void testAddPublisherAfterStartThrows() throws Exception {
        session = new TrdpPdSession(19103);
        session.start();

        assertThatThrownBy(() -> session.addPublisher(1000, "127.0.0.1", 17224, 0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testAddSubscriberAfterStartThrows() throws Exception {
        session = new TrdpPdSession(19104);
        session.start();

        assertThatThrownBy(() -> session.addSubscriber(2000, null, 0, new NoOpPdEventListener()))
                .isInstanceOf(IllegalStateException.class);
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
}
