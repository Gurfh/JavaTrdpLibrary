package com.trdp.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TrdpMdHeaderTest {

    @Test
    void testEncodeAndDecode() {
        TrdpMdHeader original = new TrdpMdHeader();
        original.setSequenceCounter(42);
        original.setMessageType(TrdpMessageType.MD_REQUEST);
        original.setComId(1000);
        original.setReplyComId(2000);
        original.setReplyIpAddress(0xC0A80001);

        byte[] encoded = original.encode();
        assertThat(encoded).hasSize(TrdpConstants.TRDP_MD_HEADER_SIZE);

        TrdpMdHeader decoded = TrdpMdHeader.decode(encoded);

        assertThat(decoded.getSequenceCounter()).isEqualTo(42);
        assertThat(decoded.getMessageType()).isEqualTo(TrdpMessageType.MD_REQUEST);
        assertThat(decoded.getComId()).isEqualTo(1000);
        assertThat(decoded.getReplyComId()).isEqualTo(2000);
        assertThat(decoded.getReplyIpAddress()).isEqualTo(0xC0A80001);
    }
}
