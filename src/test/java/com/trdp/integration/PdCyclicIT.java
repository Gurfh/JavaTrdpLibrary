package com.trdp.integration;

import com.trdp.pd.PdPublisher;
import com.trdp.pd.PdSubscriber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class PdCyclicIT {

    private PdPublisher publisher;
    private PdSubscriber subscriber;

    @AfterEach
    void tearDown() {
        if (publisher != null) publisher.close();
        if (subscriber != null) subscriber.close();
    }

    @Test
    void testCyclicPublishSubscribe() throws Exception {
        int comId = 1200;
        int port = 19500;
        long intervalUs = 50_000; // 50ms

        AtomicInteger receivedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(5);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.addListener((c, data, seq) -> {
            receivedCount.incrementAndGet();
            latch.countDown();
        });
        subscriber.start();

        Thread.sleep(200);

        publisher = new PdPublisher(comId, "127.0.0.1", port, 0, intervalUs);
        publisher.putData("cyclic-data".getBytes());
        publisher.start();

        boolean received = latch.await(3, TimeUnit.SECONDS);

        assertThat(received).as("Subscriber should receive at least 5 cyclic packets").isTrue();
        assertThat(receivedCount.get()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void testImmediateSendWithCyclicSubscriber() throws Exception {
        int comId = 1201;
        int port = 19501;
        long intervalUs = 100_000; // 100ms

        AtomicReference<byte[]> lastPayload = new AtomicReference<>();
        AtomicInteger receivedCount = new AtomicInteger(0);
        CountDownLatch immediateLatch = new CountDownLatch(1);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.addListener((c, data, seq) -> {
            lastPayload.set(data);
            receivedCount.incrementAndGet();
            if (new String(data).equals("immediate")) {
                immediateLatch.countDown();
            }
        });
        subscriber.start();

        Thread.sleep(200);

        publisher = new PdPublisher(comId, "127.0.0.1", port, 0, intervalUs);
        publisher.putData("cyclic".getBytes());
        publisher.start();

        // Wait for a cyclic packet
        Thread.sleep(200);
        int countBefore = receivedCount.get();
        assertThat(countBefore).as("Should have received at least 1 cyclic packet").isGreaterThanOrEqualTo(1);

        // Send immediate
        publisher.putDataImmediate("immediate".getBytes());

        boolean gotImmediate = immediateLatch.await(2, TimeUnit.SECONDS);
        assertThat(gotImmediate).as("Subscriber should receive the immediate packet").isTrue();

        // Verify total count grew (both cyclic and immediate)
        assertThat(receivedCount.get()).isGreaterThan(countBefore);
    }
}
