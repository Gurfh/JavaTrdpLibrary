package com.trdp.md;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.assertj.core.api.Assertions.*;
import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;

class MdRequesterTest {
    
    private MdRequester requester;
    
    @AfterEach
    void tearDown() {
        if (requester != null) {
            requester.close();
        }
    }
    
    @Test
    void testCreateRequester() throws IOException {
        requester = new MdRequester(17225);
        assertThat(requester).isNotNull();
    }
    
    @Test
    void testSendRequestReturnsFuture() throws IOException {
        requester = new MdRequester(0);
        
        byte[] requestData = "Request".getBytes();
        // Uses the simple overload
        CompletableFuture<MdReply> future = requester.sendRequest(2000, requestData, "127.0.0.1", 17226);
        
        assertThat(future).isNotNull();
        assertThat(future).isNotCompleted();
    }

    @Test
    void testCreateRequesterWithSocketOptions() throws IOException {
        requester = new MdRequester(0, 5_000_000, 60_000_000,
                InetAddress.getLoopbackAddress(), 32, 5);
        assertThat(requester).isNotNull();
        assertThat(requester.getReplyTimeoutUs()).isEqualTo(5_000_000);
        assertThat(requester.getConnectTimeoutUs()).isEqualTo(60_000_000);
    }

    @Test
    void testCreateRequesterWithNullBindAddress() throws IOException {
        requester = new MdRequester(0, 5_000_000, 60_000_000, null, 64, 3);
        assertThat(requester).isNotNull();
    }

    @Test
    void testSetTopologyCounters() throws IOException {
        requester = new MdRequester(0);
        // Verify no exception is thrown
        assertThatCode(() -> requester.setTopologyCounters(1, 1))
            .doesNotThrowAnyException();
    }
}
