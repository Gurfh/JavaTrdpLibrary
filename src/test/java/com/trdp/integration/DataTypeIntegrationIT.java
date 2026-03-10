package com.trdp.integration;

import com.trdp.pd.PdEvent;
import com.trdp.pd.PdEventListener;
import com.trdp.pd.PdPublisherHandle;
import com.trdp.pd.TrdpPdSession;
import com.trdp.util.TrdpDataset;
import com.trdp.util.TrdpDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

class DataTypeIntegrationIT {

    private TrdpPdSession pubSession;
    private TrdpPdSession subSession;

    @AfterEach
    void tearDown() {
        if (pubSession != null) pubSession.close();
        if (subSession != null) subSession.close();
    }

    private static PdEventListener dataOnly(Consumer<PdEvent> callback) {
        return new PdEventListener() {
            @Override public void onData(PdEvent event) { callback.accept(event); }
            @Override public void onTimeout(PdEvent event) {}
            @Override public void onValidityRestored(PdEvent event) {}
        };
    }

    @Test
    void testPublishSubscribeWithStructuredData() throws Exception {
        int comId = 3000;
        int port = 19200;

        TrdpDataset trainData = new TrdpDataset()
            .addUInt16("trainId", 1234)
            .addUInt8("carNumber", 3)
            .addReal32("speed", 85.5f)
            .addReal32("temperature", 22.3f)
            .addBool8("doorsClosed", true)
            .addBool8("emergencyBrake", false)
            .addUInt32("odometer", 567890L)
            .addTimeDate64("timestamp", Instant.now());

        byte[] encodedData = trainData.encode();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<byte[]> receivedData = new AtomicReference<>();

        List<TrdpDataset.FieldDefinition> schema = Arrays.asList(
            new TrdpDataset.FieldDefinition("trainId", TrdpDataType.UINT16),
            new TrdpDataset.FieldDefinition("carNumber", TrdpDataType.UINT8),
            new TrdpDataset.FieldDefinition("speed", TrdpDataType.REAL32),
            new TrdpDataset.FieldDefinition("temperature", TrdpDataType.REAL32),
            new TrdpDataset.FieldDefinition("doorsClosed", TrdpDataType.BOOL8),
            new TrdpDataset.FieldDefinition("emergencyBrake", TrdpDataType.BOOL8),
            new TrdpDataset.FieldDefinition("odometer", TrdpDataType.UINT32),
            new TrdpDataset.FieldDefinition("timestamp", TrdpDataType.TIMEDATE64)
        );

        subSession = new TrdpPdSession(port);
        subSession.addSubscriber(comId, null, 0, dataOnly(event -> {
            receivedData.set(event.getData());
            latch.countDown();
        }));
        subSession.start();

        Thread.sleep(500);

        pubSession = new TrdpPdSession(0);
        PdPublisherHandle pub = pubSession.addPublisher(comId, "127.0.0.1", port, 0);
        pub.putDataImmediate(encodedData);

        boolean received = latch.await(3, TimeUnit.SECONDS);

        assertThat(received).isTrue();

        TrdpDataset decoded = TrdpDataset.decode(receivedData.get(), schema);

        assertThat(decoded.getValue("trainId")).isEqualTo(1234);
        assertThat(decoded.getValue("carNumber")).isEqualTo(3);
        assertThat((Float) decoded.getValue("speed")).isCloseTo(85.5f, within(0.1f));
        assertThat((Float) decoded.getValue("temperature")).isCloseTo(22.3f, within(0.1f));
        assertThat(decoded.getValue("doorsClosed")).isEqualTo(true);
        assertThat(decoded.getValue("emergencyBrake")).isEqualTo(false);
        assertThat(decoded.getValue("odometer")).isEqualTo(567890L);
    }
}
