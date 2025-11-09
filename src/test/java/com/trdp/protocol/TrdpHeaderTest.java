package com.trdp.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TrdpHeaderTest {
    
    @Test
    void testEncodeAndDecode() {
        TrdpHeader original = new TrdpHeader();
        original.setSequenceCounter(42);
        original.setMessageType(TrdpMessageType.PD);
        original.setComId(1000);
        original.setEtbTopoCnt(1);
        original.setOpTrnTopoCnt(2);
        original.setDatasetLength(100);
        original.setReplyComId(2000);
        original.setReplyIpAddress(0xC0A80001);
        
        byte[] encoded = original.encode();
        assertThat(encoded).hasSize(TrdpConstants.TRDP_HEADER_SIZE);
        
        TrdpHeader decoded = TrdpHeader.decode(encoded);
        
        assertThat(decoded.getSequenceCounter()).isEqualTo(42);
        assertThat(decoded.getMessageType()).isEqualTo(TrdpMessageType.PD);
        assertThat(decoded.getComId()).isEqualTo(1000);
        assertThat(decoded.getEtbTopoCnt()).isEqualTo(1);
        assertThat(decoded.getOpTrnTopoCnt()).isEqualTo(2);
        assertThat(decoded.getDatasetLength()).isEqualTo(100);
        assertThat(decoded.getReplyComId()).isEqualTo(2000);
        assertThat(decoded.getReplyIpAddress()).isEqualTo(0xC0A80001);
    }
    
    @Test
    void testInvalidHeaderSize() {
        byte[] tooShort = new byte[10];
        
        assertThatThrownBy(() -> TrdpHeader.decode(tooShort))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("too short");
    }
    
    @Test
    void testFcsValidation() {
        TrdpHeader header = new TrdpHeader();
        header.setSequenceCounter(1);
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(100);
        
        byte[] encoded = header.encode();
        
        encoded[TrdpConstants.TRDP_HEADER_SIZE - 1] ^= 0xFF;
        
        assertThatThrownBy(() -> TrdpHeader.decode(encoded))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("FCS mismatch");
    }
}
