package com.trdp.integration;

import com.trdp.pd.PdEvent;
import com.trdp.pd.PdEventListener;
import com.trdp.pd.PdPublisherHandle;
import com.trdp.pd.PdRequester;
import com.trdp.pd.TrdpPdSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

class PdPullPatternIT {

    private TrdpPdSession pubSession;
    private TrdpPdSession subSession;
    private PdRequester requester;

    @AfterEach
    void tearDown() {
        if (requester != null) requester.close();
        if (subSession != null) subSession.close();
        if (pubSession != null) pubSession.close();
    }

    private static PdEventListener dataOnly(Consumer<PdEvent> callback) {
        return new PdEventListener() {
            @Override public void onData(PdEvent event) { callback.accept(event); }
            @Override public void onTimeout(PdEvent event) {}
            @Override public void onValidityRestored(PdEvent event) {}
        };
    }

    @Test
    void testPullPatternMulticast() throws Exception {
        int comId = 5000;
        String multicastGroup = "239.255.0.5";
        int sharedPort = 19600;
        int publisherListenPort = 19601;

        byte[] expectedData = "Pulled Data".getBytes();

        // 1. Publisher session (listening for requests on publisherListenPort)
        pubSession = new TrdpPdSession(publisherListenPort);
        PdPublisherHandle pub = pubSession.addPublisher(comId, "127.0.0.1", 17224, 0);
        pub.putData(expectedData);
        pubSession.start();

        // 2. Subscriber session (listening on multicast group)
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<byte[]> receivedData = new AtomicReference<>();

        subSession = new TrdpPdSession(sharedPort);
        subSession.addSubscriber(comId, multicastGroup, 0, dataOnly(event -> {
            receivedData.set(event.getData());
            latch.countDown();
        }));
        subSession.start();

        // 3. Requester sends request for reply to multicast group
        requester = new PdRequester(sharedPort);

        Thread.sleep(100);

        requester.request(comId, "127.0.0.1", publisherListenPort, 0, multicastGroup);

        // 4. Verify reception
        boolean received = latch.await(2, TimeUnit.SECONDS);

        assertThat(received).as("Subscriber should receive the pulled data via multicast").isTrue();
        assertThat(receivedData.get()).isEqualTo(expectedData);
    }

    @Test
    void testPullPatternWithReplyComId() throws Exception {
        int requestComId = 5001;
        int replyComId = 5002;
        String multicastGroup = "239.255.0.6";
        int sharedPort = 19602;
        int publisherListenPort = 19603;

        byte[] expectedData = "Reply ComId Pull".getBytes();

        // 1. Publisher session (listening for requests)
        pubSession = new TrdpPdSession(publisherListenPort);
        PdPublisherHandle pub = pubSession.addPublisher(requestComId, "127.0.0.1", 17224, 0);
        pub.putData(expectedData);
        pubSession.start();

        // 2. Subscriber session (listening for reply ComId on multicast)
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<byte[]> receivedData = new AtomicReference<>();

        subSession = new TrdpPdSession(sharedPort);
        subSession.addSubscriber(replyComId, multicastGroup, 0, dataOnly(event -> {
            receivedData.set(event.getData());
            latch.countDown();
        }));
        subSession.start();

        // 3. Requester sends request with explicit replyComId
        requester = new PdRequester(sharedPort);

        Thread.sleep(100);

        requester.request(requestComId, "127.0.0.1", publisherListenPort, replyComId, multicastGroup);

        // 4. Verify reception on replyComId
        boolean received = latch.await(2, TimeUnit.SECONDS);

        assertThat(received).as("Subscriber should receive the pulled data on reply ComId").isTrue();
        assertThat(receivedData.get()).isEqualTo(expectedData);
    }
}
