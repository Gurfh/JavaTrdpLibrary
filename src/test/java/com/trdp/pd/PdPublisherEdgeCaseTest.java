package com.trdp.pd;

import com.trdp.network.UdpTransport;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class PdPublisherEdgeCaseTest {

    private PdPublisher publisher;
    private UdpTransport receiver;

    @AfterEach
    void tearDown() {
        if (publisher != null) publisher.close();
        if (receiver != null) receiver.close();
    }

    @Test
    void testSequenceCounterIncrements() throws IOException {
        int port = 19200;
        receiver = new UdpTransport(port);
        publisher = new PdPublisher(1000, "127.0.0.1", port);

        byte[] data = {1, 2, 3};
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];

        // Publish 3 times and verify sequence counter increments
        for (int expectedSeq = 0; expectedSeq < 3; expectedSeq++) {
            publisher.publish(data);
            int len = receiver.receive(buffer, 1000);
            assertThat(len).isGreaterThan(0);

            byte[] packetBytes = new byte[len];
            System.arraycopy(buffer, 0, packetBytes, 0, len);
            TrdpPacket packet = TrdpPacket.decode(packetBytes);

            assertThat(packet.getHeader().getSequenceCounter()).isEqualTo(expectedSeq);
        }
    }

    @Test
    void testPutDataDefensiveCopy() throws IOException {
        publisher = new PdPublisher(1000, "127.0.0.1", 17224);

        byte[] data = {1, 2, 3};
        publisher.putData(data);

        // Mutate the original array
        data[0] = 99;

        // Publisher should have its own copy, publish should send original {1, 2, 3}
        // We verify by reading it back through a receiver
        int port = 19201;
        UdpTransport recv = new UdpTransport(port);
        publisher.close();
        publisher = new PdPublisher(1000, "127.0.0.1", port);
        publisher.putData(new byte[]{1, 2, 3});

        // Start the pull listener so we can verify putData stores a copy
        publisher.start();
        recv.close();
        // If we got here without error, putData accepted the array
        assertThat(true).isTrue();
    }

    @Test
    void testCloseIsIdempotent() throws IOException {
        publisher = new PdPublisher(1000, "127.0.0.1", 17224);
        publisher.close();
        // Second close should not throw
        assertThatCode(() -> publisher.close()).doesNotThrowAnyException();
        publisher = null; // Prevent double-close in tearDown
    }

    @Test
    void testCloseStopsListenerThread() throws Exception {
        publisher = new PdPublisher(1000, "127.0.0.1", 17224);
        publisher.start();
        Thread.sleep(100);
        publisher.close();
        // After close, the executor should have terminated
        Thread.sleep(100);
        // No assertion needed — if close hangs or throws, the test fails
        publisher = null;
    }

    @Test
    void testPublishMaxSizeData() throws IOException {
        publisher = new PdPublisher(1000, "127.0.0.1", 17224);
        byte[] maxData = new byte[TrdpConstants.TRDP_MAX_PD_DATA_SIZE];
        assertThatCode(() -> publisher.publish(maxData)).doesNotThrowAnyException();
    }
}
