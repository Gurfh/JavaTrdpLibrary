package com.trdp.pd;

import com.trdp.network.UdpTransport;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpPacket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.assertj.core.api.Assertions.*;

import java.io.IOException;

class PdRequesterTest {

    private PdRequester requester;
    private UdpTransport receiver;

    @AfterEach
    void tearDown() {
        if (requester != null) {
            requester.close();
        }
        if (receiver != null) {
            receiver.close();
        }
    }

    @Test
    void testCreateRequester() throws IOException {
        requester = new PdRequester(0); // Ephemeral port
        assertThat(requester).isNotNull();
    }

    @Test
    void testCreateRequesterWithFixedPort() throws IOException {
        int port = 18500;
        requester = new PdRequester(port);
        assertThat(requester).isNotNull();
    }

    @Test
    void testSetTopologyCounters() throws IOException {
        requester = new PdRequester(0);
        assertThatCode(() -> requester.setTopologyCounters(1, 2))
            .doesNotThrowAnyException();
    }

    @Test
    void testRequestDoesNotThrow() throws IOException {
        requester = new PdRequester(0);

        // Sending to a local port that might not be listening is fine for UDP (fire and forget)
        assertThatCode(() ->
            requester.request(1000, "127.0.0.1", 17224, 0, null)
        ).doesNotThrowAnyException();
    }

    @Test
    void testPerComIdSequenceCounters() throws Exception {
        int port = 18501;
        receiver = new UdpTransport(port);
        requester = new PdRequester(0);

        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];

        // Send 3 requests for comId 1000
        for (int expected = 0; expected < 3; expected++) {
            requester.request(1000, "127.0.0.1", port, 0, null);
            int len = receiver.receive(buffer, 1000);
            assertThat(len).isGreaterThan(0);
            byte[] pktBytes = new byte[len];
            System.arraycopy(buffer, 0, pktBytes, 0, len);
            TrdpPacket pkt = TrdpPacket.decode(pktBytes);
            assertThat(pkt.getHeader().getSequenceCounter()).isEqualTo(expected);
        }

        // Send 2 requests for comId 2000 — sequence should restart at 0
        for (int expected = 0; expected < 2; expected++) {
            requester.request(2000, "127.0.0.1", port, 0, null);
            int len = receiver.receive(buffer, 1000);
            assertThat(len).isGreaterThan(0);
            byte[] pktBytes = new byte[len];
            System.arraycopy(buffer, 0, pktBytes, 0, len);
            TrdpPacket pkt = TrdpPacket.decode(pktBytes);
            assertThat(pkt.getHeader().getSequenceCounter()).isEqualTo(expected);
        }
    }

    @Test
    void testRequestWithPayload() throws Exception {
        int port = 18502;
        receiver = new UdpTransport(port);
        requester = new PdRequester(0);

        byte[] payload = {0x01, 0x02, 0x03, 0x04};
        requester.request(1000, "127.0.0.1", port, 0, null, payload);

        byte[] buffer = new byte[TrdpConstants.TRDP_MAX_PACKET_SIZE];
        int len = receiver.receive(buffer, 1000);
        assertThat(len).isGreaterThan(0);

        byte[] pktBytes = new byte[len];
        System.arraycopy(buffer, 0, pktBytes, 0, len);
        TrdpPacket pkt = TrdpPacket.decode(pktBytes);
        assertThat(pkt.getPayload()).startsWith(0x01, 0x02, 0x03, 0x04);
    }

    @Test
    void testRequestWithOversizedPayloadThrows() throws IOException {
        requester = new PdRequester(0);

        byte[] oversized = new byte[TrdpConstants.TRDP_MAX_PD_DATA_SIZE + 1];
        assertThatThrownBy(() ->
            requester.request(1000, "127.0.0.1", 17224, 0, null, oversized)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("exceeds maximum");
    }
}
