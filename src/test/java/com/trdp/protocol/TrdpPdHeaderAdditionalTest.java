package com.trdp.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests covering TrdpPdHeader toString() and getHeaderFcs() methods.
 */
class TrdpPdHeaderAdditionalTest {

    @Test
    void testToString() {
        TrdpPdHeader header = new TrdpPdHeader();
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(1234);
        header.setSequenceCounter(5);
        header.setDatasetLength(100);
        header.setEtbTopoCnt(10);
        header.setOpTrnTopoCnt(20);

        String str = header.toString();
        assertThat(str).contains("PdHeader");
        assertThat(str).contains("PD");
        assertThat(str).contains("1234");
        assertThat(str).contains("5");
        assertThat(str).contains("100");
        assertThat(str).contains("10");
        assertThat(str).contains("20");
    }

    @Test
    void testGetHeaderFcsAfterEncode() {
        TrdpPdHeader header = new TrdpPdHeader();
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(1000);
        header.setSequenceCounter(1);
        header.setDatasetLength(0);

        // Encode calculates FCS
        header.encode();

        int fcs = header.getHeaderFcs();
        assertThat(fcs).isNotEqualTo(0);
    }

    @Test
    void testEncodeDecodeRoundTrip() {
        TrdpPdHeader original = new TrdpPdHeader();
        original.setMessageType(TrdpMessageType.PD_REQUEST);
        original.setComId(2000);
        original.setSequenceCounter(42);
        original.setDatasetLength(0);
        original.setEtbTopoCnt(5);
        original.setOpTrnTopoCnt(10);
        original.setReplyComId(3000);
        original.setReplyIpAddress(0x7F000001); // 127.0.0.1

        byte[] encoded = original.encode();
        TrdpPdHeader decoded = TrdpPdHeader.decode(encoded);

        assertThat(decoded.getMessageType()).isEqualTo(TrdpMessageType.PD_REQUEST);
        assertThat(decoded.getComId()).isEqualTo(2000);
        assertThat(decoded.getSequenceCounter()).isEqualTo(42);
        assertThat(decoded.getEtbTopoCnt()).isEqualTo(5);
        assertThat(decoded.getOpTrnTopoCnt()).isEqualTo(10);
        assertThat(decoded.getReplyComId()).isEqualTo(3000);
        assertThat(decoded.getReplyIpAddress()).isEqualTo(0x7F000001);
        assertThat(decoded.getHeaderFcs()).isNotEqualTo(0);
    }
}
