package com.trdp.integration;

import com.trdp.pd.PdEvent;
import com.trdp.pd.PdEventListener;
import com.trdp.pd.PdPublisher;
import com.trdp.pd.PdSubscriber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;

class PdSequenceValidationIT {

    private PdPublisher publisher;
    private PdPublisher publisher2;
    private PdSubscriber subscriber;

    @AfterEach
    void tearDown() {
        if (publisher != null) publisher.close();
        if (publisher2 != null) publisher2.close();
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
    void testCyclicPublisherSequenceAccepted() throws Exception {
        int comId = 1300;
        int port = 19650;
        long intervalUs = 50_000; // 50ms

        AtomicInteger receivedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(5);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.addListener(dataOnly(event -> {
            receivedCount.incrementAndGet();
            latch.countDown();
        }));
        subscriber.start();

        Thread.sleep(200);

        publisher = new PdPublisher(comId, "127.0.0.1", port, 0, intervalUs);
        publisher.putData("cyclic-seq".getBytes());
        publisher.start();

        boolean received = latch.await(3, TimeUnit.SECONDS);

        assertThat(received).as("Should receive at least 5 cyclic packets").isTrue();
        assertThat(receivedCount.get()).isGreaterThanOrEqualTo(5);
        assertThat(subscriber.getMissedCount()).isEqualTo(0);
        assertThat(subscriber.getDuplicateCount()).isEqualTo(0);
    }

    @Test
    void testMultiplePublishersToSameSubscriber() throws Exception {
        int comId = 1301;
        int port = 19651;

        AtomicInteger receivedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(6);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.addListener(dataOnly(event -> {
            receivedCount.incrementAndGet();
            latch.countDown();
        }));
        subscriber.start();

        Thread.sleep(200);

        // Both publishers use loopback, so they share the same source IP and SourceKey.
        // Send all from publisher1 first (seqCnt 0,1,2), then publisher2 (seqCnt 0,1,2).
        // Publisher2's seqCnt=0 triggers a restart-reset, so all 6 packets are accepted.
        publisher = new PdPublisher(comId, "127.0.0.1", port);
        for (int i = 0; i < 3; i++) {
            publisher.putDataImmediate(("pub1-" + i).getBytes());
            Thread.sleep(50);
        }

        publisher2 = new PdPublisher(comId, "127.0.0.1", port);
        for (int i = 0; i < 3; i++) {
            publisher2.putDataImmediate(("pub2-" + i).getBytes());
            Thread.sleep(50);
        }

        boolean received = latch.await(5, TimeUnit.SECONDS);

        assertThat(received).as("Should receive packets from both publishers").isTrue();
        assertThat(receivedCount.get()).isGreaterThanOrEqualTo(6);
        assertThat(subscriber.getDuplicateCount()).isEqualTo(0);
    }

    @Test
    void testPublisherRestartDetectedViaSeqZero() throws Exception {
        int comId = 1302;
        int port = 19652;

        List<Integer> receivedSeqCounters = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(4);

        subscriber = new PdSubscriber(comId, "127.0.0.1", port);
        subscriber.addListener(dataOnly(event -> {
            synchronized (receivedSeqCounters) {
                receivedSeqCounters.add(event.getSequenceCounter());
            }
            latch.countDown();
        }));
        subscriber.start();

        Thread.sleep(200);

        // First publisher sends packets 0, 1
        publisher = new PdPublisher(comId, "127.0.0.1", port);
        publisher.putDataImmediate("before-restart-1".getBytes());
        Thread.sleep(100);
        publisher.putDataImmediate("before-restart-2".getBytes());
        Thread.sleep(100);
        publisher.close();

        // Simulate restart: new publisher starts from seqCnt 0 again
        publisher = new PdPublisher(comId, "127.0.0.1", port);
        publisher.putDataImmediate("after-restart-1".getBytes());
        Thread.sleep(100);
        publisher.putDataImmediate("after-restart-2".getBytes());

        boolean received = latch.await(5, TimeUnit.SECONDS);

        assertThat(received).as("All 4 packets should be accepted (restart via seqCnt 0)").isTrue();
        synchronized (receivedSeqCounters) {
            assertThat(receivedSeqCounters).hasSize(4);
            // First publisher: 0, 1; Second publisher: 0, 1
            assertThat(receivedSeqCounters.get(0)).isEqualTo(0);
            assertThat(receivedSeqCounters.get(1)).isEqualTo(1);
            assertThat(receivedSeqCounters.get(2)).isEqualTo(0); // Restart
            assertThat(receivedSeqCounters.get(3)).isEqualTo(1);
        }
        assertThat(subscriber.getDuplicateCount()).isEqualTo(0);
    }
}
