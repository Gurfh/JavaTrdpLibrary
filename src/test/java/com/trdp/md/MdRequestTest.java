package com.trdp.md;

import com.trdp.protocol.TrdpMessageType;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class MdRequestTest {

    @Test
    void testConstructorAndGetters() throws Exception {
        UUID sessionId = UUID.randomUUID();
        byte[] data = {1, 2, 3};
        InetAddress addr = InetAddress.getLoopbackAddress();

        MdRequest request = new MdRequest(1000, data, sessionId, "srcUri", "dstUri", addr, 5000, 42,
                TrdpMessageType.MD_REQUEST);

        assertThat(request.getComId()).isEqualTo(1000);
        assertThat(request.getData()).containsExactly(1, 2, 3);
        assertThat(request.getSessionId()).isEqualTo(sessionId);
        assertThat(request.getSourceUri()).isEqualTo("srcUri");
        assertThat(request.getDestinationUri()).isEqualTo("dstUri");
        assertThat(request.getSourceAddress()).isEqualTo(addr);
        assertThat(request.getSourcePort()).isEqualTo(5000);
        assertThat(request.getSequenceCounter()).isEqualTo(42);
        assertThat(request.getMessageType()).isEqualTo(TrdpMessageType.MD_REQUEST);
        assertThat(request.isNotification()).isFalse();
    }

    @Test
    void testNotificationType() throws Exception {
        MdRequest request = new MdRequest(1000, null, UUID.randomUUID(), null, null,
            InetAddress.getLoopbackAddress(), 0, 0, TrdpMessageType.MD_NOTIFICATION);

        assertThat(request.getMessageType()).isEqualTo(TrdpMessageType.MD_NOTIFICATION);
        assertThat(request.isNotification()).isTrue();
    }

    @Test
    void testNullData() throws Exception {
        MdRequest request = new MdRequest(1000, null, UUID.randomUUID(), null, null,
            InetAddress.getLoopbackAddress(), 0, 0, TrdpMessageType.MD_REQUEST);

        assertThat(request.getData()).isNull();
        assertThat(request.getSourceUri()).isNull();
        assertThat(request.getDestinationUri()).isNull();
    }

    @Test
    void testToString() throws Exception {
        UUID sessionId = UUID.randomUUID();
        MdRequest request = new MdRequest(2000, new byte[]{10, 20}, sessionId, "src", "dst",
            InetAddress.getLoopbackAddress(), 8080, 5, TrdpMessageType.MD_REQUEST);

        String str = request.toString();
        assertThat(str).contains("MdRequest");
        assertThat(str).contains("2000");
        assertThat(str).contains(sessionId.toString());
        assertThat(str).contains("5");
        assertThat(str).contains("src");
        assertThat(str).contains("dst");
        assertThat(str).contains("8080");
        assertThat(str).contains("2"); // dataLen
    }

    @Test
    void testToStringWithNullData() throws Exception {
        MdRequest request = new MdRequest(100, null, UUID.randomUUID(), "s", "d",
            InetAddress.getLoopbackAddress(), 0, 0, TrdpMessageType.MD_REQUEST);

        String str = request.toString();
        assertThat(str).contains("dataLen=0");
    }
}
