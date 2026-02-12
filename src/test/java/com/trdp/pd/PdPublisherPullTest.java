package com.trdp.pd;

import com.trdp.network.UdpTransport;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpMessageType;
import com.trdp.protocol.TrdpPacket;
import com.trdp.protocol.TrdpPdHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.*;

class PdPublisherPullTest {

    private PdPublisher publisher;
    private UdpTransport requesterTransport;

    @AfterEach
    void tearDown() {
        if (publisher != null) publisher.close();
        if (requesterTransport != null) requesterTransport.close();
    }

    @Test
    void testPullRequestReply() throws Exception {
        int comId = 9000;
        int publisherPort = 19900;

        publisher = new PdPublisher(comId, "127.0.0.1", publisherPort, publisherPort);
        publisher.putData(new byte[]{11, 22, 33});
        publisher.start();

        Thread.sleep(200);

        // Simulate a PD_REQUEST
        requesterTransport = new UdpTransport(0);

        TrdpPdHeader reqHeader = new TrdpPdHeader();
        reqHeader.setSequenceCounter(0);
        reqHeader.setMessageType(TrdpMessageType.PD_REQUEST);
        reqHeader.setComId(comId);
        reqHeader.setDatasetLength(0);
        reqHeader.setReplyComId(0);
        reqHeader.setReplyIpAddress(0); // Use source address

        TrdpPacket reqPacket = new TrdpPacket(reqHeader, new byte[0]);
        requesterTransport.send(reqPacket.encode(), InetAddress.getLoopbackAddress(), publisherPort);

        // Receive reply
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        int len = requesterTransport.receive(buffer, 3000);
        assertThat(len).isGreaterThan(0);

        byte[] packetBytes = new byte[len];
        System.arraycopy(buffer, 0, packetBytes, 0, len);
        TrdpPacket replyPacket = TrdpPacket.decode(packetBytes);

        assertThat(replyPacket.getHeader().getMessageType()).isEqualTo(TrdpMessageType.PD_REPLY);
        assertThat(replyPacket.getHeader().getComId()).isEqualTo(comId);
        assertThat(replyPacket.getPayload()).containsExactly(11, 22, 33);
    }

    @Test
    void testPullRequestWithReplyComId() throws Exception {
        int comId = 9001;
        int replyComId = 9099;
        int publisherPort = 19901;

        publisher = new PdPublisher(comId, "127.0.0.1", publisherPort, publisherPort);
        publisher.putData(new byte[]{44});
        publisher.start();

        Thread.sleep(200);

        requesterTransport = new UdpTransport(0);

        TrdpPdHeader reqHeader = new TrdpPdHeader();
        reqHeader.setSequenceCounter(0);
        reqHeader.setMessageType(TrdpMessageType.PD_REQUEST);
        reqHeader.setComId(comId);
        reqHeader.setDatasetLength(0);
        reqHeader.setReplyComId(replyComId);
        reqHeader.setReplyIpAddress(0);

        TrdpPacket reqPacket = new TrdpPacket(reqHeader, new byte[0]);
        requesterTransport.send(reqPacket.encode(), InetAddress.getLoopbackAddress(), publisherPort);

        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        int len = requesterTransport.receive(buffer, 3000);
        assertThat(len).isGreaterThan(0);

        byte[] packetBytes = new byte[len];
        System.arraycopy(buffer, 0, packetBytes, 0, len);
        TrdpPacket replyPacket = TrdpPacket.decode(packetBytes);

        assertThat(replyPacket.getHeader().getComId()).isEqualTo(replyComId);
    }

    @Test
    void testPullRequestWithReplyIpAddress() throws Exception {
        int comId = 9002;
        int publisherPort = 19902;
        int replyPort = 19903;

        publisher = new PdPublisher(comId, "127.0.0.1", publisherPort, publisherPort);
        publisher.putData(new byte[]{55});
        publisher.start();

        Thread.sleep(200);

        // This transport sends the request
        UdpTransport senderTransport = new UdpTransport(0);
        // This transport receives the reply at the specified IP
        UdpTransport replyReceiver = new UdpTransport(replyPort);

        TrdpPdHeader reqHeader = new TrdpPdHeader();
        reqHeader.setSequenceCounter(0);
        reqHeader.setMessageType(TrdpMessageType.PD_REQUEST);
        reqHeader.setComId(comId);
        reqHeader.setDatasetLength(0);
        reqHeader.setReplyComId(0);
        // Set reply address to 127.0.0.1 (loopback)
        reqHeader.setReplyIpAddress(ByteBuffer.wrap(InetAddress.getLoopbackAddress().getAddress()).getInt());

        TrdpPacket reqPacket = new TrdpPacket(reqHeader, new byte[0]);
        // Send from senderTransport, but reply will come to replyReceiver at 127.0.0.1:senderPort
        // Note: reply goes to the source port of the request sender, but to the specified IP
        senderTransport.send(reqPacket.encode(), InetAddress.getLoopbackAddress(), publisherPort);

        // Reply goes back to sender's source port at 127.0.0.1
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        int len = senderTransport.receive(buffer, 3000);
        assertThat(len).isGreaterThan(0);

        senderTransport.close();
        replyReceiver.close();
    }

    @Test
    void testPullRequestIgnoresWrongComId() throws Exception {
        int comId = 9003;
        int publisherPort = 19904;

        publisher = new PdPublisher(comId, "127.0.0.1", publisherPort, publisherPort);
        publisher.putData(new byte[]{66});
        publisher.start();

        Thread.sleep(200);

        requesterTransport = new UdpTransport(0);

        TrdpPdHeader reqHeader = new TrdpPdHeader();
        reqHeader.setSequenceCounter(0);
        reqHeader.setMessageType(TrdpMessageType.PD_REQUEST);
        reqHeader.setComId(9999); // Wrong comId
        reqHeader.setDatasetLength(0);

        TrdpPacket reqPacket = new TrdpPacket(reqHeader, new byte[0]);
        requesterTransport.send(reqPacket.encode(), InetAddress.getLoopbackAddress(), publisherPort);

        // Should not get a reply
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        int len = requesterTransport.receive(buffer, 1000);
        assertThat(len).isEqualTo(0); // Timeout, no reply
    }

    @Test
    void testPullRequestIgnoresNonRequestType() throws Exception {
        int comId = 9004;
        int publisherPort = 19905;

        publisher = new PdPublisher(comId, "127.0.0.1", publisherPort, publisherPort);
        publisher.putData(new byte[]{77});
        publisher.start();

        Thread.sleep(200);

        requesterTransport = new UdpTransport(0);

        // Send a PD (push) packet instead of PD_REQUEST
        TrdpPdHeader reqHeader = new TrdpPdHeader();
        reqHeader.setSequenceCounter(0);
        reqHeader.setMessageType(TrdpMessageType.PD);
        reqHeader.setComId(comId);
        reqHeader.setDatasetLength(1);

        TrdpPacket reqPacket = new TrdpPacket(reqHeader, new byte[]{1});
        requesterTransport.send(reqPacket.encode(), InetAddress.getLoopbackAddress(), publisherPort);

        // Should not get a reply
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        int len = requesterTransport.receive(buffer, 1000);
        assertThat(len).isEqualTo(0);
    }

    @Test
    void testStartIdempotent() throws Exception {
        publisher = new PdPublisher(9005, "127.0.0.1", 19906, 19906);
        publisher.start();
        assertThatCode(() -> publisher.start()).doesNotThrowAnyException();
    }
}
