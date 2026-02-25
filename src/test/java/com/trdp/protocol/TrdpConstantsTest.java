package com.trdp.protocol;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.*;

class TrdpConstantsTest {

    @Test
    void testConstantValues() {
        assertThat(TrdpConstants.TRDP_PD_HEADER_SIZE).isEqualTo(40);
        assertThat(TrdpConstants.TRDP_MD_HEADER_SIZE).isEqualTo(116);
        assertThat(TrdpConstants.TRDP_FCS_SIZE).isEqualTo(4);
        assertThat(TrdpConstants.TRDP_MAX_PD_DATA_SIZE).isEqualTo(1432);
        assertThat(TrdpConstants.TRDP_MAX_PACKET_SIZE).isEqualTo(40 + 1432);
        assertThat(TrdpConstants.TRDP_MAX_MD_DATA_SIZE).isEqualTo(1472 - 116);
        assertThat(TrdpConstants.PROTOCOL_VERSION).isEqualTo(0x0100);
        assertThat(TrdpConstants.DEFAULT_PD_PORT).isEqualTo(17224);
        assertThat(TrdpConstants.DEFAULT_MD_PORT).isEqualTo(17225);
        assertThat(TrdpConstants.DEFAULT_MULTICAST_GROUP).isEqualTo("239.255.0.1");
        assertThat(TrdpConstants.DEFAULT_PD_TIMEOUT_US).isEqualTo(100_000);
        assertThat(TrdpConstants.DEFAULT_MD_REPLY_TIMEOUT_US).isEqualTo(5_000_000);
        assertThat(TrdpConstants.DEFAULT_MD_CONFIRM_TIMEOUT_US).isEqualTo(1_000_000);
        assertThat(TrdpConstants.DEFAULT_MD_CONNECT_TIMEOUT_US).isEqualTo(60_000_000);
    }

    @Test
    void testPrivateConstructorThrows() throws Exception {
        Constructor<TrdpConstants> constructor = TrdpConstants.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
            .isInstanceOf(InvocationTargetException.class)
            .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
