package com.trdp.md;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.assertj.core.api.Assertions.*;
import java.io.IOException;
import java.net.InetAddress;

class MdReplierTest {
    
    private MdReplier replier;
    
    @AfterEach
    void tearDown() {
        if (replier != null) {
            replier.close();
        }
    }
    
    @Test
    void testCreateReplier() throws IOException {
        MdRequestHandler handler = (request) -> new MdResponse("Reply".getBytes());
        replier = new MdReplier(17227, handler);
        assertThat(replier).isNotNull();
    }
    
    @Test
    void testStartReplier() throws IOException {
        MdRequestHandler handler = (request) -> new MdResponse("Reply".getBytes());
        replier = new MdReplier(17228, handler);

        assertThatCode(() -> replier.start()).doesNotThrowAnyException();
    }

    @Test
    void testCreateReplierWithSocketOptions() throws IOException {
        MdRequestHandler handler = (request) -> new MdResponse("Reply".getBytes());
        replier = new MdReplier(0, handler, 1_000_000,
                InetAddress.getLoopbackAddress(), 32, 5);
        assertThat(replier).isNotNull();
        assertThat(replier.getConfirmTimeoutUs()).isEqualTo(1_000_000);
    }

    @Test
    void testCreateReplierWithNullBindAddress() throws IOException {
        MdRequestHandler handler = (request) -> new MdResponse("Reply".getBytes());
        replier = new MdReplier(0, handler, 1_000_000, null, 64, 3);
        assertThat(replier).isNotNull();
    }
}
