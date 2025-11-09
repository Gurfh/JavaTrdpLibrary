package com.trdp.util;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class TrdpEncoderDecoderTest {
    
    @Test
    void testBool8() {
        TrdpEncoder encoder = new TrdpEncoder(10);
        encoder.putBool8(true).putBool8(false);
        
        byte[] data = encoder.toByteArray();
        TrdpDecoder decoder = new TrdpDecoder(data);
        
        assertThat(decoder.getBool8()).isTrue();
        assertThat(decoder.getBool8()).isFalse();
    }
    
    @Test
    void testChar8() {
        TrdpEncoder encoder = new TrdpEncoder(10);
        encoder.putChar8('A').putChar8('Z');
        
        byte[] data = encoder.toByteArray();
        TrdpDecoder decoder = new TrdpDecoder(data);
        
        assertThat(decoder.getChar8()).isEqualTo('A');
        assertThat(decoder.getChar8()).isEqualTo('Z');
    }
    
    @Test
    void testInt8() {
        TrdpEncoder encoder = new TrdpEncoder(10);
        encoder.putInt8((byte) 127).putInt8((byte) -128);
        
        byte[] data = encoder.toByteArray();
        TrdpDecoder decoder = new TrdpDecoder(data);
        
        assertThat(decoder.getInt8()).isEqualTo((byte) 127);
        assertThat(decoder.getInt8()).isEqualTo((byte) -128);
    }
    
    @Test
    void testInt16() {
        TrdpEncoder encoder = new TrdpEncoder(10);
        encoder.putInt16((short) 32767).putInt16((short) -32768);
        
        byte[] data = encoder.toByteArray();
        TrdpDecoder decoder = new TrdpDecoder(data);
        
        assertThat(decoder.getInt16()).isEqualTo((short) 32767);
        assertThat(decoder.getInt16()).isEqualTo((short) -32768);
    }
    
    @Test
    void testInt32() {
        TrdpEncoder encoder = new TrdpEncoder(10);
        encoder.putInt32(2147483647).putInt32(-2147483648);
        
        byte[] data = encoder.toByteArray();
        TrdpDecoder decoder = new TrdpDecoder(data);
        
        assertThat(decoder.getInt32()).isEqualTo(2147483647);
        assertThat(decoder.getInt32()).isEqualTo(-2147483648);
    }
    
    @Test
    void testInt64() {
        TrdpEncoder encoder = new TrdpEncoder(20);
        encoder.putInt64(9223372036854775807L).putInt64(-9223372036854775808L);
        
        byte[] data = encoder.toByteArray();
        TrdpDecoder decoder = new TrdpDecoder(data);
        
        assertThat(decoder.getInt64()).isEqualTo(9223372036854775807L);
        assertThat(decoder.getInt64()).isEqualTo(-9223372036854775808L);
    }
    
    @Test
    void testUInt8() {
        TrdpEncoder encoder = new TrdpEncoder(10);
        encoder.putUInt8(0).putUInt8(255);
        
        byte[] data = encoder.toByteArray();
        TrdpDecoder decoder = new TrdpDecoder(data);
        
        assertThat(decoder.getUInt8()).isEqualTo(0);
        assertThat(decoder.getUInt8()).isEqualTo(255);
    }
    
    @Test
    void testUInt16() {
        TrdpEncoder encoder = new TrdpEncoder(10);
        encoder.putUInt16(0).putUInt16(65535);
        
        byte[] data = encoder.toByteArray();
        TrdpDecoder decoder = new TrdpDecoder(data);
        
        assertThat(decoder.getUInt16()).isEqualTo(0);
        assertThat(decoder.getUInt16()).isEqualTo(65535);
    }
    
    @Test
    void testUInt32() {
        TrdpEncoder encoder = new TrdpEncoder(10);
        encoder.putUInt32(0L).putUInt32(4294967295L);
        
        byte[] data = encoder.toByteArray();
        TrdpDecoder decoder = new TrdpDecoder(data);
        
        assertThat(decoder.getUInt32()).isEqualTo(0L);
        assertThat(decoder.getUInt32()).isEqualTo(4294967295L);
    }
    
    @Test
    void testReal32() {
        TrdpEncoder encoder = new TrdpEncoder(10);
        encoder.putReal32(3.14159f).putReal32(-2.71828f);
        
        byte[] data = encoder.toByteArray();
        TrdpDecoder decoder = new TrdpDecoder(data);
        
        assertThat(decoder.getReal32()).isCloseTo(3.14159f, within(0.00001f));
        assertThat(decoder.getReal32()).isCloseTo(-2.71828f, within(0.00001f));
    }
    
    @Test
    void testReal64() {
        TrdpEncoder encoder = new TrdpEncoder(20);
        encoder.putReal64(3.141592653589793).putReal64(-2.718281828459045);
        
        byte[] data = encoder.toByteArray();
        TrdpDecoder decoder = new TrdpDecoder(data);
        
        assertThat(decoder.getReal64()).isCloseTo(3.141592653589793, within(0.0000000001));
        assertThat(decoder.getReal64()).isCloseTo(-2.718281828459045, within(0.0000000001));
    }
    
    @Test
    void testTimeDate64() {
        Instant now = Instant.now();
        
        TrdpEncoder encoder = new TrdpEncoder(20);
        encoder.putTimeDate64(now);
        
        byte[] data = encoder.toByteArray();
        TrdpDecoder decoder = new TrdpDecoder(data);
        
        Instant decoded = decoder.getTimeDate64();
        
        assertThat(decoded.getEpochSecond()).isEqualTo(now.getEpochSecond());
        assertThat(decoded.getNano() / 1000).isCloseTo(now.getNano() / 1000, within(1000));
    }
    
    @Test
    void testString() {
        TrdpEncoder encoder = new TrdpEncoder(50);
        encoder.putString("Hello TRDP", 20);
        
        byte[] data = encoder.toByteArray();
        TrdpDecoder decoder = new TrdpDecoder(data);
        
        assertThat(decoder.getString(20)).isEqualTo("Hello TRDP");
    }
    
    @Test
    void testStringPadding() {
        TrdpEncoder encoder = new TrdpEncoder(50);
        encoder.putString("Test", 10);
        
        byte[] data = encoder.toByteArray();
        
        assertThat(data).hasSize(10);
        assertThat(data[4]).isEqualTo((byte) 0);
    }
    
    @Test
    void testAlignment() {
        TrdpEncoder encoder = new TrdpEncoder(50);
        encoder.putInt8((byte) 1);
        encoder.align(4);
        encoder.putInt32(42);
        
        byte[] data = encoder.toByteArray();
        
        assertThat(data).hasSize(8);
        
        TrdpDecoder decoder = new TrdpDecoder(data);
        decoder.getInt8();
        decoder.align(4);
        assertThat(decoder.getInt32()).isEqualTo(42);
    }
    
    @Test
    void testMixedTypes() {
        TrdpEncoder encoder = new TrdpEncoder(100);
        encoder.putBool8(true)
               .putInt16((short) 1000)
               .putReal32(3.14f)
               .putUInt32(12345L)
               .putString("Test", 8);
        
        byte[] data = encoder.toByteArray();
        TrdpDecoder decoder = new TrdpDecoder(data);
        
        assertThat(decoder.getBool8()).isTrue();
        assertThat(decoder.getInt16()).isEqualTo((short) 1000);
        assertThat(decoder.getReal32()).isCloseTo(3.14f, within(0.01f));
        assertThat(decoder.getUInt32()).isEqualTo(12345L);
        assertThat(decoder.getString(8)).isEqualTo("Test");
    }
    
    @Test
    void testUInt8Validation() {
        TrdpEncoder encoder = new TrdpEncoder(10);
        
        assertThatThrownBy(() -> encoder.putUInt8(-1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("UINT8");
        
        assertThatThrownBy(() -> encoder.putUInt8(256))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("UINT8");
    }
    
    @Test
    void testUInt16Validation() {
        TrdpEncoder encoder = new TrdpEncoder(10);
        
        assertThatThrownBy(() -> encoder.putUInt16(-1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("UINT16");
        
        assertThatThrownBy(() -> encoder.putUInt16(65536))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("UINT16");
    }
    
    @Test
    void testBigEndianByteOrder() {
        TrdpEncoder encoder = new TrdpEncoder(10);
        encoder.putInt16((short) 0x1234);
        
        byte[] data = encoder.toByteArray();
        
        assertThat(data[0]).isEqualTo((byte) 0x12);
        assertThat(data[1]).isEqualTo((byte) 0x34);
    }
}
