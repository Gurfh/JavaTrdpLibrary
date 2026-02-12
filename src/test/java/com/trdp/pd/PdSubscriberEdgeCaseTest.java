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

    @Test
    void testReceivesMatchingComId() throws Exception {
        int comId = 8000;
        int port = 19700;

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<byte[]> received = new AtomicReference<>();
        AtomicInteger receivedSeq = new AtomicInteger(-1);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.addListener((c, data, seq) -> {
            received.set(data);
            receivedSeq.set(seq);
            latch.countDown();
        });
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
        assertThat(received.get()).containsExactly(10, 20, 30);
        assertThat(receivedSeq.get()).isEqualTo(42);
    }

    @Test
    void testIgnoresNonMatchingComId() throws Exception {
        int comId = 8001;
        int port = 19701;

        AtomicInteger callCount = new AtomicInteger(0);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.addListener((c, data, seq) -> callCount.incrementAndGet());
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
        subscriber.addListener((c, data, seq) -> callCount.incrementAndGet());
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

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.addListener((c, data, seq) -> latch.countDown());
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
        subscriber.addListener((c, data, seq) -> {
            throw new RuntimeException("Listener error");
        });

        // Second listener should still be called
        subscriber.addListener((c, data, seq) -> {
            received.set(data);
            latch.countDown();
        });

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
}
