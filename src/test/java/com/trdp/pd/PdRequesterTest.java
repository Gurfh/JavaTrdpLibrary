package com.trdp.pd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.assertj.core.api.Assertions.*;

import java.io.IOException;

class PdRequesterTest {
    
    private PdRequester requester;
    
    @AfterEach
    void tearDown() {
        if (requester != null) {
            requester.close();
        }
    }
    
    @Test
    void testCreateRequester() throws IOException {
        requester = new PdRequester(0); // Ephemeral port
        assertThat(requester).isNotNull();
    }
    
    @Test
    void testCreateRequesterWithFixedPort() throws IOException {
        int port = 18500;
        requester = new PdRequester(port);
        assertThat(requester).isNotNull();
    }
    
    @Test
    void testSetTopologyCounters() throws IOException {
        requester = new PdRequester(0);
        assertThatCode(() -> requester.setTopologyCounters(1, 2))
            .doesNotThrowAnyException();
    }
    
    @Test
    void testRequestDoesNotThrow() throws IOException {
        requester = new PdRequester(0);
        
        // Sending to a local port that might not be listening is fine for UDP (fire and forget)
        assertThatCode(() -> 
            requester.request(1000, "127.0.0.1", 17224, 0, null)
        ).doesNotThrowAnyException();
    }
}
