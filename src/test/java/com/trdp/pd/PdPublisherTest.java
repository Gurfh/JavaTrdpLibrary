package com.trdp.pd;

import com.trdp.protocol.TrdpConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.assertj.core.api.Assertions.*;
import java.io.IOException;

class PdPublisherTest {
    
    private PdPublisher publisher;
    
    @AfterEach
    void tearDown() {
        if (publisher != null) {
            publisher.close();
        }
    }
    
    @Test
    void testCreatePublisher() throws IOException {
        publisher = new PdPublisher(1000, "127.0.0.1", 17224);
        assertThat(publisher).isNotNull();
    }
    
    @Test
    void testPublishData() throws IOException {
        publisher = new PdPublisher(1000, "127.0.0.1", 17224);
        
        byte[] data = "Test Data".getBytes();
        assertThatCode(() -> publisher.publish(data)).doesNotThrowAnyException();
    }
    
    @Test
    void testPublishOversizedData() throws IOException {
        publisher = new PdPublisher(1000, "127.0.0.1", 17224);
        
        byte[] oversizedData = new byte[TrdpConstants.TRDP_MAX_PD_DATA_SIZE + 1];
        
        assertThatThrownBy(() -> publisher.publish(oversizedData))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds maximum");
    }
}
