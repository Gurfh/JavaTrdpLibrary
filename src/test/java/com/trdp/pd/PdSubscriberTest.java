package com.trdp.pd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.assertj.core.api.Assertions.*;
import java.io.IOException;

class PdSubscriberTest {
    
    private PdSubscriber subscriber;
    
    @AfterEach
    void tearDown() {
        if (subscriber != null) {
            subscriber.close();
        }
    }
    
    @Test
    void testCreateSubscriber() throws IOException {
        subscriber = new PdSubscriber(1000, "239.255.0.1", 17224);
        assertThat(subscriber).isNotNull();
    }
    
    @Test
    void testAddListener() throws IOException {
        subscriber = new PdSubscriber(1000, "239.255.0.1", 17224);
        
        PdDataListener listener = (comId, data, seqNo) -> {};
        assertThatCode(() -> subscriber.addListener(listener)).doesNotThrowAnyException();
    }
    
    @Test
    void testRemoveListener() throws IOException {
        subscriber = new PdSubscriber(1000, "239.255.0.1", 17224);
        
        PdDataListener listener = (comId, data, seqNo) -> {};
        subscriber.addListener(listener);
        
        assertThatCode(() -> subscriber.removeListener(listener)).doesNotThrowAnyException();
    }
    
    @Test
    void testStartSubscriber() throws IOException {
        subscriber = new PdSubscriber(1000, "239.255.0.1", 17224);
        assertThatCode(() -> subscriber.start()).doesNotThrowAnyException();
    }
}
