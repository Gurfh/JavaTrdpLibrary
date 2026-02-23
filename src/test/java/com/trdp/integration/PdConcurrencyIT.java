package com.trdp.integration;

import com.trdp.pd.PdEvent;
import com.trdp.pd.PdEventListener;
import com.trdp.pd.PdPublisher;
import com.trdp.pd.PdSubscriber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;

class PdConcurrencyIT {

    private PdPublisher publisher;
    private PdSubscriber subscriber;

    @AfterEach
    void tearDown() {
        if (publisher != null) publisher.close();
        if (subscriber != null) subscriber.close();
    }

    private static PdEventListener dataOnly(Consumer<PdEvent> callback) {
        return new PdEventListener() {
            @Override public void onData(PdEvent event) { callback.accept(event); }
            @Override public void onTimeout(PdEvent event) {}
            @Override public void onValidityRestored(PdEvent event) {}
        };
    }

    @Test
    void testConcurrentPublishes() throws Exception {
        int comId = 1100;
        int port = 19300;
        int threadCount = 4;
        int publishesPerThread = 10;
        int totalExpected = threadCount * publishesPerThread;

        AtomicInteger receivedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(totalExpected);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.addListener(dataOnly(event -> {
            receivedCount.incrementAndGet();
            latch.countDown();
        }));
        subscriber.start();

        Thread.sleep(200);

        publisher = new PdPublisher(comId, "127.0.0.1", port);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            pool.submit(() -> {
                for (int i = 0; i < publishesPerThread; i++) {
                    try {
                        publisher.putDataImmediate(("T" + threadId + "-" + i).getBytes());
                        Thread.sleep(10);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        boolean allReceived = latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(allReceived).isTrue();
        assertThat(receivedCount.get()).isEqualTo(totalExpected);
    }

    @Test
    void testMultipleListenersReceiveIndependentCopies() throws Exception {
        int comId = 1101;
        int port = 19301;

        CountDownLatch latch = new CountDownLatch(2);
        byte[][] received1 = new byte[1][];
        byte[][] received2 = new byte[1][];

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.addListener(dataOnly(event -> {
            byte[] data = event.getData();
            received1[0] = data.clone();
            data[0] = 99;
            latch.countDown();
        }));
        subscriber.addListener(dataOnly(event -> {
            received2[0] = event.getData().clone();
            latch.countDown();
        }));
        subscriber.start();

        Thread.sleep(200);

        publisher = new PdPublisher(comId, "127.0.0.1", port);
        publisher.putDataImmediate(new byte[]{1, 2, 3});

        boolean done = latch.await(3, TimeUnit.SECONDS);
        assertThat(done).isTrue();

        // Both listeners should have received {1, 2, 3}
        assertThat(received1[0]).containsExactly(1, 2, 3);
        assertThat(received2[0]).containsExactly(1, 2, 3);
    }

    @Test
    void testResourceCleanupAfterClose() throws Exception {
        // Verify that creating, starting, and closing many publisher/subscriber pairs
        // doesn't leak resources (sockets, threads)
        for (int i = 0; i < 10; i++) {
            PdPublisher pub = new PdPublisher(2000 + i, "127.0.0.1", 19310 + i);
            pub.start();
            pub.putDataImmediate(new byte[]{1});
            pub.close();

            PdSubscriber sub = new PdSubscriber(2000 + i, "127.0.0.1", 19320 + i);
            sub.start();
            sub.close();
        }
        // If we get here without SocketException/OutOfMemory, resources were cleaned up
    }
}
