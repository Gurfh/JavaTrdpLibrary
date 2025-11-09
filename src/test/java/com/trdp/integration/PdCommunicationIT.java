package com.trdp.integration;

import com.trdp.pd.PdPublisher;
import com.trdp.pd.PdSubscriber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class PdCommunicationIT {
    
    private PdPublisher publisher;
    private PdSubscriber subscriber;
    
    @AfterEach
    void tearDown() {
        if (publisher != null) {
            publisher.close();
        }
        if (subscriber != null) {
            subscriber.close();
        }
    }
    
    @Test
    void testPublishSubscribe() throws Exception {
        int comId = 1000;
        String multicastGroup = "239.255.0.1";
        int port = 19000;
        
        byte[] testData = "Hello TRDP PD".getBytes();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<byte[]> receivedData = new AtomicReference<>();
        AtomicReference<Integer> receivedComId = new AtomicReference<>();
        
        subscriber = new PdSubscriber(comId, multicastGroup, port);
        subscriber.addListener((comIdReceived, data, seqNo) -> {
            receivedData.set(data);
            receivedComId.set(comIdReceived);
            latch.countDown();
        });
        subscriber.start();
        
        Thread.sleep(500);
        
        publisher = new PdPublisher(comId, multicastGroup, port);
        publisher.publish(testData);
        
        boolean received = latch.await(3, TimeUnit.SECONDS);
        
        assertThat(received).isTrue();
        assertThat(receivedComId.get()).isEqualTo(comId);
        assertThat(receivedData.get()).isEqualTo(testData);
    }
    
    @Test
    void testMultiplePublishEvents() throws Exception {
        int comId = 1001;
        String multicastGroup = "239.255.0.1";
        int port = 19001;
        
        int messageCount = 5;
        CountDownLatch latch = new CountDownLatch(messageCount);
        
        subscriber = new PdSubscriber(comId, multicastGroup, port);
        subscriber.addListener((comIdReceived, data, seqNo) -> latch.countDown());
        subscriber.start();
        
        Thread.sleep(500);
        
        publisher = new PdPublisher(comId, multicastGroup, port);
        for (int i = 0; i < messageCount; i++) {
            publisher.publish(("Message " + i).getBytes());
            Thread.sleep(100);
        }
        
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertThat(received).isTrue();
    }
}
