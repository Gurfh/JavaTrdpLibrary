package com.trdp.integration;

import com.trdp.pd.PdPublisher;
import com.trdp.pd.PdSubscriber;
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

class DataTypeIntegrationIT {
    
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
    void testPublishSubscribeWithStructuredData() throws Exception {
        int comId = 3000;
        String multicastGroup = "239.255.0.1";
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
        
        subscriber = new PdSubscriber(comId, multicastGroup, port);
        subscriber.addListener((comIdReceived, data, seqNo) -> {
            receivedData.set(data);
            latch.countDown();
        });
        subscriber.start();
        
        Thread.sleep(500);
        
        publisher = new PdPublisher(comId, multicastGroup, port);
        publisher.publish(encodedData);
        
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
