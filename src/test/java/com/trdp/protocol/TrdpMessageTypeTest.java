package com.trdp.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TrdpMessageTypeTest {
    
    @Test
    void testMessageTypeCodes() {
        assertThat(TrdpMessageType.PD.getCode()).isEqualTo(0x5064);
        assertThat(TrdpMessageType.PD_REQUEST.getCode()).isEqualTo(0x5072);
        assertThat(TrdpMessageType.MD_REQUEST.getCode()).isEqualTo(0x4D72);
        assertThat(TrdpMessageType.MD_REPLY.getCode()).isEqualTo(0x4D70);
        assertThat(TrdpMessageType.MD_CONFIRM.getCode()).isEqualTo(0x4D63);
        assertThat(TrdpMessageType.MD_ERROR.getCode()).isEqualTo(0x4D65);
    }
    
    @Test
    void testFromCode() {
        assertThat(TrdpMessageType.fromCode(0x5064)).isEqualTo(TrdpMessageType.PD);
        assertThat(TrdpMessageType.fromCode(0x4D72)).isEqualTo(TrdpMessageType.MD_REQUEST);
        assertThat(TrdpMessageType.fromCode(0x4D70)).isEqualTo(TrdpMessageType.MD_REPLY);
    }
    
    @Test
    void testFromInvalidCode() {
        assertThatThrownBy(() -> TrdpMessageType.fromCode(0xFFFF))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown TRDP message type");
    }
}
