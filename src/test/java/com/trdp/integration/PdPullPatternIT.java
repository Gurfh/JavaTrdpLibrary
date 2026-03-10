package com.trdp.integration;

import com.trdp.network.UdpTransport;
import com.trdp.pd.PdPublisherHandle;
import com.trdp.pd.TrdpPdSession;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpMessageType;
import com.trdp.protocol.TrdpPacket;
import com.trdp.protocol.TrdpPdHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.assertj.core.api.Assertions.*;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

class PdPullPatternIT {

    private TrdpPdSession pubSession;
    private UdpTransport externalTransport;

    @AfterEach
    void tearDown() {
        if (externalTransport != null) externalTransport.close();
        if (pubSession != null) pubSession.close();
    }

    @Test
    void testPullPatternWithReplyAddress() throws Exception {
        int comId = 5000;
        int publisherListenPort = 19601;
        byte[] expectedData = "Pulled Data".getBytes();

        // Publisher session (listening for requests)
        pubSession = new TrdpPdSession(publisherListenPort);
        PdPublisherHandle pub = pubSession.addPublisher(comId, "127.0.0.1", 17224, 0);
        pub.putData(expectedData);
        pubSession.start();

        Thread.sleep(100);

        // Send PD_REQUEST with explicit replyIpAddress
        externalTransport = new UdpTransport(0);
        sendPdRequest(externalTransport, comId, publisherListenPort, 0, "127.0.0.1");

        // Receive PD_REPLY routed to the specified reply address
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        var received = externalTransport.receiveWithSource(buffer, 2000);

        assertThat(received).as("Should receive the pulled data via reply address").isNotNull();
        TrdpPacket reply = TrdpPacket.decode(Arrays.copyOf(received.getData(), received.getLength()));
        assertThat(reply.getHeader().getMessageType()).isEqualTo(TrdpMessageType.PD_REPLY);
        assertThat(reply.getPayload()).isEqualTo(expectedData);
    }

    @Test
    void testPullPatternWithReplyComId() throws Exception {
        int requestComId = 5001;
        int replyComId = 5002;
        int publisherListenPort = 19603;
        byte[] expectedData = "Reply ComId Pull".getBytes();

        // Publisher session (listening for requests)
        pubSession = new TrdpPdSession(publisherListenPort);
        PdPublisherHandle pub = pubSession.addPublisher(requestComId, "127.0.0.1", 17224, 0);
        pub.putData(expectedData);
        pubSession.start();

        Thread.sleep(100);

        // Send PD_REQUEST with explicit replyComId
        externalTransport = new UdpTransport(0);
        sendPdRequest(externalTransport, requestComId, publisherListenPort, replyComId, null);

        // Receive PD_REPLY with the specified reply ComId
        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        var received = externalTransport.receiveWithSource(buffer, 2000);

        assertThat(received).as("Should receive the pulled data with reply ComId").isNotNull();
        TrdpPacket reply = TrdpPacket.decode(Arrays.copyOf(received.getData(), received.getLength()));
        assertThat(reply.getHeader().getMessageType()).isEqualTo(TrdpMessageType.PD_REPLY);
        assertThat(reply.getHeader().getComId()).isEqualTo(replyComId);
        assertThat(reply.getPayload()).isEqualTo(expectedData);
    }

    private void sendPdRequest(UdpTransport transport, int comId, int destPort,
                                int replyComId, String replyIpAddress) throws Exception {
        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(0);
        header.setMessageType(TrdpMessageType.PD_REQUEST);
        header.setComId(comId);
        header.setDatasetLength(0);
        if (replyComId != 0) {
            header.setReplyComId(replyComId);
        }
        if (replyIpAddress != null) {
            InetAddress inet = InetAddress.getByName(replyIpAddress);
            header.setReplyIpAddress(ByteBuffer.wrap(inet.getAddress()).getInt());
        }

        TrdpPacket packet = new TrdpPacket(header, new byte[0]);
        transport.send(packet.encode(), InetAddress.getByName("127.0.0.1"), destPort);
    }
}
