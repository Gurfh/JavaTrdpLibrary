package com.trdp.util;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class TrdpDatasetTest {
    
    @Test
    void testSimpleDataset() {
        TrdpDataset dataset = new TrdpDataset()
            .addBool8("enabled", true)
            .addInt32("value", 42)
            .addReal32("temperature", 23.5f);
        
        byte[] encoded = dataset.encode();
        
        assertThat(encoded).isNotEmpty();
        assertThat(encoded.length).isEqualTo(1 + 4 + 4);
    }
    
    @Test
    void testDatasetEncodeAndDecode() {
        TrdpDataset original = new TrdpDataset()
            .addBool8("enabled", true)
            .addInt16("counter", (short) 1000)
            .addUInt32("timestamp", 123456789L)
            .addReal32("value", 3.14159f);
        
        byte[] encoded = original.encode();
        
        List<TrdpDataset.FieldDefinition> schema = Arrays.asList(
            new TrdpDataset.FieldDefinition("enabled", TrdpDataType.BOOL8),
            new TrdpDataset.FieldDefinition("counter", TrdpDataType.INT16),
            new TrdpDataset.FieldDefinition("timestamp", TrdpDataType.UINT32),
            new TrdpDataset.FieldDefinition("value", TrdpDataType.REAL32)
        );
        
        TrdpDataset decoded = TrdpDataset.decode(encoded, schema);
        
        assertThat(decoded.getValue("enabled")).isEqualTo(true);
        assertThat(decoded.getValue("counter")).isEqualTo((short) 1000);
        assertThat(decoded.getValue("timestamp")).isEqualTo(123456789L);
        assertThat((Float) decoded.getValue("value")).isCloseTo(3.14159f, within(0.0001f));
    }
    
    @Test
    void testDatasetWithAllTypes() {
        Instant now = Instant.now();
        
        TrdpDataset dataset = new TrdpDataset()
            .addBool8("flag", true)
            .addChar8("code", 'A')
            .addInt8("tiny", (byte) 127)
            .addInt16("small", (short) 1000)
            .addInt32("medium", 100000)
            .addInt64("large", 10000000000L)
            .addUInt8("ubyte", 255)
            .addUInt16("ushort", 65535)
            .addUInt32("uint", 4294967295L)
            .addReal32("float", 3.14f)
            .addReal64("double", 2.71828)
            .addTimeDate64("time", now);
        
        byte[] encoded = dataset.encode();
        
        assertThat(encoded).isNotEmpty();
        
        int expectedSize = 1 + 1 + 1 + 2 + 4 + 8 + 1 + 2 + 4 + 4 + 8 + 8;
        assertThat(encoded.length).isEqualTo(expectedSize);
    }
    
    @Test
    void testGetValue() {
        TrdpDataset dataset = new TrdpDataset()
            .addInt32("speed", 120)
            .addReal32("temperature", 25.5f)
            .addBool8("active", true);
        
        assertThat(dataset.getValue("speed")).isEqualTo(120);
        assertThat(dataset.getValue("temperature")).isEqualTo(25.5f);
        assertThat(dataset.getValue("active")).isEqualTo(true);
    }
    
    @Test
    void testGetValueNotFound() {
        TrdpDataset dataset = new TrdpDataset()
            .addInt32("value", 42);
        
        assertThatThrownBy(() -> dataset.getValue("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Field not found");
    }
    
    @Test
    void testGetFields() {
        TrdpDataset dataset = new TrdpDataset()
            .addInt32("first", 1)
            .addInt32("second", 2)
            .addInt32("third", 3);
        
        List<TrdpDataset.Field> fields = dataset.getFields();
        
        assertThat(fields).hasSize(3);
        assertThat(fields.get(0).getName()).isEqualTo("first");
        assertThat(fields.get(1).getName()).isEqualTo("second");
        assertThat(fields.get(2).getName()).isEqualTo("third");
    }
    
    @Test
    void testTrainSensorDataExample() {
        TrdpDataset trainData = new TrdpDataset()
            .addUInt16("trainId", 1234)
            .addUInt8("carNumber", 5)
            .addReal32("speed", 120.5f)
            .addReal32("temperature", 23.7f)
            .addBool8("doorsClosed", true)
            .addBool8("brakesApplied", false)
            .addUInt32("odometer", 1234567L)
            .addTimeDate64("timestamp", Instant.now());
        
        byte[] encoded = trainData.encode();
        
        assertThat(encoded).isNotEmpty();
        
        List<TrdpDataset.FieldDefinition> schema = Arrays.asList(
            new TrdpDataset.FieldDefinition("trainId", TrdpDataType.UINT16),
            new TrdpDataset.FieldDefinition("carNumber", TrdpDataType.UINT8),
            new TrdpDataset.FieldDefinition("speed", TrdpDataType.REAL32),
            new TrdpDataset.FieldDefinition("temperature", TrdpDataType.REAL32),
            new TrdpDataset.FieldDefinition("doorsClosed", TrdpDataType.BOOL8),
            new TrdpDataset.FieldDefinition("brakesApplied", TrdpDataType.BOOL8),
            new TrdpDataset.FieldDefinition("odometer", TrdpDataType.UINT32),
            new TrdpDataset.FieldDefinition("timestamp", TrdpDataType.TIMEDATE64)
        );
        
        TrdpDataset decoded = TrdpDataset.decode(encoded, schema);
        
        assertThat(decoded.getValue("trainId")).isEqualTo(1234);
        assertThat(decoded.getValue("carNumber")).isEqualTo(5);
        assertThat((Float) decoded.getValue("speed")).isCloseTo(120.5f, within(0.01f));
        assertThat(decoded.getValue("doorsClosed")).isEqualTo(true);
    }
}
