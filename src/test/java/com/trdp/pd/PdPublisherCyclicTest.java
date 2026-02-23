package com.trdp.pd;

import com.trdp.network.UdpTransport;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class PdPublisherCyclicTest {

    private PdPublisher publisher;
    private UdpTransport receiver;

    @AfterEach
    void tearDown() {
        if (publisher != null) publisher.close();
        if (receiver != null) receiver.close();
    }

    @Test
    void testCyclicSendAtInterval() throws Exception {
        int port = 19400;
        long intervalUs = 50_000; // 50ms
        receiver = new UdpTransport(port);
        publisher = new PdPublisher(1000, "127.0.0.1", port, 0, intervalUs);

        publisher.putData(new byte[]{1, 2, 3});
        publisher.start();

        // Wait ~250ms, expect at least 3 cyclic sends
        int count = 0;
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        long deadline = System.currentTimeMillis() + 350;
        while (System.currentTimeMillis() < deadline) {
            int len = receiver.receive(buffer, 100);
            if (len > 0) count++;
        }

        assertThat(count).as("Should receive at least 3 cyclic packets in ~300ms at 50ms interval")
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    void testCyclicSkipsWhenNoData() throws Exception {
        int port = 19401;
        long intervalUs = 50_000; // 50ms
        receiver = new UdpTransport(port);
        publisher = new PdPublisher(1000, "127.0.0.1", port, 0, intervalUs);

        // Start without calling putData — currentData is empty byte[]
        publisher.start();

        // Wait 200ms — cyclic should skip sending because data is empty
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        int count = 0;
        long deadline = System.currentTimeMillis() + 200;
        while (System.currentTimeMillis() < deadline) {
            int len = receiver.receive(buffer, 50);
            if (len > 0) count++;
        }

        assertThat(count).as("No packets should be sent when data buffer is empty").isZero();
    }

    @Test
    void testCyclicPicksUpNewData() throws Exception {
        int port = 19402;
        long intervalUs = 50_000; // 50ms
        receiver = new UdpTransport(port);
        publisher = new PdPublisher(1000, "127.0.0.1", port, 0, intervalUs);

        publisher.putData(new byte[]{1});
        publisher.start();

        // Receive first packet and verify payload
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        int len = receiver.receive(buffer, 500);
        assertThat(len).isGreaterThan(0);
        byte[] pktBytes = new byte[len];
        System.arraycopy(buffer, 0, pktBytes, 0, len);
        TrdpPacket pkt1 = TrdpPacket.decode(pktBytes);
        assertThat(pkt1.getPayload()).startsWith((byte) 1);

        // Update data
        publisher.putData(new byte[]{2});

        // Drain until we see a packet with the new payload (may take a cycle)
        boolean foundNew = false;
        long deadline = System.currentTimeMillis() + 500;
        while (System.currentTimeMillis() < deadline) {
            len = receiver.receive(buffer, 100);
            if (len > 0) {
                pktBytes = new byte[len];
                System.arraycopy(buffer, 0, pktBytes, 0, len);
                TrdpPacket pkt2 = TrdpPacket.decode(pktBytes);
                if (pkt2.getPayload()[0] == 2) {
                    foundNew = true;
                    break;
                }
            }
        }
        assertThat(foundNew).as("Should eventually receive a packet with updated payload {2}").isTrue();
    }

    @Test
    void testCyclicSequenceCounterIncrements() throws Exception {
        int port = 19403;
        long intervalUs = 50_000; // 50ms
        receiver = new UdpTransport(port);
        publisher = new PdPublisher(1000, "127.0.0.1", port, 0, intervalUs);

        publisher.putData(new byte[]{1});
        publisher.start();

        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        int prevSeq = -1;
        for (int i = 0; i < 3; i++) {
            int len = receiver.receive(buffer, 500);
            assertThat(len).isGreaterThan(0);
            byte[] pktBytes = new byte[len];
            System.arraycopy(buffer, 0, pktBytes, 0, len);
            TrdpPacket pkt = TrdpPacket.decode(pktBytes);
            int seq = pkt.getHeader().getSequenceCounter();
            assertThat(seq).as("Sequence counter should increase monotonically")
                    .isGreaterThan(prevSeq);
            prevSeq = seq;
        }
    }

    @Test
    void testPullOnlyModeNoCyclicSend() throws Exception {
        int port = 19404;
        receiver = new UdpTransport(port);
        // intervalUs = 0 means no cyclic send
        publisher = new PdPublisher(1000, "127.0.0.1", port, 0, 0);

        publisher.putData(new byte[]{1, 2, 3});
        publisher.start();

        // Wait 200ms — should get 0 packets
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        int count = 0;
        long deadline = System.currentTimeMillis() + 200;
        while (System.currentTimeMillis() < deadline) {
            int len = receiver.receive(buffer, 50);
            if (len > 0) count++;
        }

        assertThat(count).as("No cyclic packets when intervalUs is 0").isZero();
    }

    @Test
    void testConstructorRejectsNegativeInterval() {
        assertThatThrownBy(() ->
            new PdPublisher(1000, "127.0.0.1", 17224, 0, -1)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("intervalUs");
    }

    @Test
    void testGetIntervalUs() throws IOException {
        publisher = new PdPublisher(1000, "127.0.0.1", 17224, 0, 100_000);
        assertThat(publisher.getIntervalUs()).isEqualTo(100_000);
    }
}
