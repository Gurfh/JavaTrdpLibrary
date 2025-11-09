package com.trdp.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TrdpPacketTest {
    
    @Test
    void testEncodeAndDecode() {
        TrdpHeader header = new TrdpHeader();
        header.setSequenceCounter(10);
        header.setMessageType(TrdpMessageType.MD_REQUEST);
        header.setComId(500);
        
        byte[] payload = "Test Data".getBytes();
        TrdpPacket original = new TrdpPacket(header, payload);
        
        byte[] encoded = original.encode();
        
        TrdpPacket decoded = TrdpPacket.decode(encoded);
        
        assertThat(decoded.getHeader().getSequenceCounter()).isEqualTo(10);
        assertThat(decoded.getHeader().getMessageType()).isEqualTo(TrdpMessageType.MD_REQUEST);
        assertThat(decoded.getHeader().getComId()).isEqualTo(500);
        assertThat(decoded.getPayload()).isEqualTo(payload);
    }
    
    @Test
    void testEmptyPayload() {
        TrdpHeader header = new TrdpHeader();
        header.setSequenceCounter(1);
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(100);
        
        TrdpPacket packet = new TrdpPacket(header, new byte[0]);
        
        byte[] encoded = packet.encode();
        TrdpPacket decoded = TrdpPacket.decode(encoded);
        
        assertThat(decoded.getPayload()).isEmpty();
    }
    
    @Test
    void testNullPayload() {
        TrdpHeader header = new TrdpHeader();
        header.setSequenceCounter(1);
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(100);
        
        TrdpPacket packet = new TrdpPacket(header, null);
        
        byte[] encoded = packet.encode();
        TrdpPacket decoded = TrdpPacket.decode(encoded);
        
        assertThat(decoded.getPayload()).isEmpty();
    }
    
    @Test
    void testDataFcsValidation() {
        TrdpHeader header = new TrdpHeader();
        header.setSequenceCounter(1);
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(100);
        
        byte[] payload = "Valid Data".getBytes();
        TrdpPacket packet = new TrdpPacket(header, payload);
        
        byte[] encoded = packet.encode();
        
        encoded[encoded.length - 1] ^= 0xFF;
        
        assertThatThrownBy(() -> TrdpPacket.decode(encoded))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Data FCS mismatch");
    }
}
