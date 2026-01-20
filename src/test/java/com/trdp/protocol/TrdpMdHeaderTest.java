package com.trdp.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TrdpMdHeaderTest {

    @Test
    void testEncodeAndDecode() {
        TrdpMdHeader header = new TrdpMdHeader();
        header.setSequenceCounter(123);
        header.setProtocolVersion(0x0100);
        header.setMessageType(TrdpMessageType.MD_REQUEST);
        header.setComId(2000);
        header.setEtbTopoCnt(10);
        header.setOpTrnTopoCnt(20);
        header.setDatasetLength(50);
        // ReplyComId and ReplyIpAddress are NOT part of MD Header wire format
        // So we do not set/expect them here for MD
        
        byte[] encoded = header.encode();
        TrdpMdHeader decoded = TrdpMdHeader.decode(encoded);

        assertThat(decoded.getSequenceCounter()).isEqualTo(123);
        assertThat(decoded.getProtocolVersion()).isEqualTo(0x0100);
        assertThat(decoded.getMessageType()).isEqualTo(TrdpMessageType.MD_REQUEST);
        assertThat(decoded.getComId()).isEqualTo(2000);
        
        assertThat(decoded.getEtbTopoCnt()).isEqualTo(10);
        assertThat(decoded.getOpTrnTopoCnt()).isEqualTo(20);
        
        assertThat(decoded.getDatasetLength()).isEqualTo(50);
    }
    
    @Test
    void testHeaderSize() {
        TrdpMdHeader header = new TrdpMdHeader();
        header.setMessageType(TrdpMessageType.MD_REQUEST);
        byte[] encoded = header.encode();
        assertThat(encoded.length).isEqualTo(TrdpConstants.TRDP_MD_HEADER_SIZE);
        assertThat(encoded.length).isEqualTo(116);
    }
}