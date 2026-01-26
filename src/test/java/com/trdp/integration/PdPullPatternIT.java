package com.trdp.integration;

import com.trdp.pd.PdPublisher;
import com.trdp.pd.PdRequester;
import com.trdp.pd.PdSubscriber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class PdPullPatternIT {
    
    private PdPublisher publisher;
    private PdSubscriber subscriber;
    private PdRequester requester;
    
    @AfterEach
    void tearDown() {
        if (requester != null) requester.close();
        if (subscriber != null) subscriber.close();
        if (publisher != null) publisher.close();
    }
    
    @Test
    void testPullPatternMulticast() throws Exception {
        // Configuration
        int comId = 5000;
        String multicastGroup = "239.255.0.5";
        int sharedPort = 19600; // Port used by Subscriber AND Requester
        int publisherListenPort = 19601;
        
        byte[] expectedData = "Pulled Data".getBytes();
        
        // 1. Setup Publisher (Listening for requests on 19601)
        // Note: The destination args (Push args) are unused for this test but required by constructor
        publisher = new PdPublisher(comId, "127.0.0.1", 17224, publisherListenPort);
        publisher.putData(expectedData); // Prepare data
        publisher.start(); // Start listening
        
        // 2. Setup Subscriber (Listening on Multicast Group)
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<byte[]> receivedData = new AtomicReference<>();
        
        subscriber = new PdSubscriber(comId, multicastGroup, sharedPort);
        subscriber.addListener((id, data, seq) -> {
            receivedData.set(data);
            latch.countDown();
        });
        subscriber.start();
        
        // 3. Setup Requester (Binding to the SAME port as Subscriber so the reply goes to the Subscriber)
        // This relies on SO_REUSEADDR which is enabled in UdpTransport
        requester = new PdRequester(sharedPort);
        
        // Allow sockets to bind
        Thread.sleep(100);
        
        // 4. Send Request
        // Requesting reply to the Multicast Group. 
        // The Publisher sees source port = sharedPort, so it sends to multicastGroup:sharedPort
        requester.request(comId, "127.0.0.1", publisherListenPort, 0, multicastGroup);
        
        // 5. Verify Reception
        boolean received = latch.await(2, TimeUnit.SECONDS);
        
        assertThat(received).as("Subscriber should receive the pulled data").isTrue();
        assertThat(receivedData.get()).isEqualTo(expectedData);
    }
    
    @Test
    void testPullPatternUnicast() throws Exception {
        // Configuration
        int comId = 5001;
        int requesterPort = 19602;
        int publisherListenPort = 19603;
        
        byte[] expectedData = "Unicast Pull".getBytes();
        
        // 1. Setup Publisher
        publisher = new PdPublisher(comId, "127.0.0.1", 17224, publisherListenPort);
        publisher.putData(expectedData);
        publisher.start();
        
        // 2. Setup Subscriber (Listening on Unicast Port)
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<byte[]> receivedData = new AtomicReference<>();
        
        subscriber = new PdSubscriber(comId, "0.0.0.0", requesterPort);
        subscriber.addListener((id, data, seq) -> {
            receivedData.set(data);
            latch.countDown();
        });
        subscriber.start();
        
        // 3. Setup Requester (Same port)
        requester = new PdRequester(requesterPort);
        
        Thread.sleep(100);
        
        // 4. Send Request (No specific reply IP, defaults to source IP/Port)
        requester.request(comId, "127.0.0.1", publisherListenPort, 0, null);
        
        // 5. Verify Reception
        boolean received = latch.await(2, TimeUnit.SECONDS);
        
        assertThat(received).isTrue();
        assertThat(receivedData.get()).isEqualTo(expectedData);
    }
}
