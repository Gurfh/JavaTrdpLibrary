package com.trdp.util;

import org.junit.jupiter.api.Test;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class TrdpEncoderDecoderEdgeCaseTest {

    @Test
    void testTimeDate48RoundTripAtHalfSecond() {
        // 500ms = 500_000_000 nanos — previously broken by truncated microseconds
        Instant ts = Instant.ofEpochSecond(1_700_000_000L, 500_000_000L);

        TrdpEncoder encoder = new TrdpEncoder(10);
        encoder.putTimeDate48(ts);
        TrdpDecoder decoder = new TrdpDecoder(encoder.toByteArray());

        Instant decoded = decoder.getTimeDate48();

        assertThat(decoded.getEpochSecond()).isEqualTo(ts.getEpochSecond());
        // ~15.26us precision from 1/65536s ticks
        assertThat(Math.abs(decoded.getNano() - ts.getNano())).isLessThan(16_000);
    }

    @Test
    void testTimeDate48RoundTripAtZeroFraction() {
        Instant ts = Instant.ofEpochSecond(1_700_000_000L, 0);

        TrdpEncoder encoder = new TrdpEncoder(10);
        encoder.putTimeDate48(ts);
        TrdpDecoder decoder = new TrdpDecoder(encoder.toByteArray());

        Instant decoded = decoder.getTimeDate48();
        assertThat(decoded).isEqualTo(ts);
    }

    @Test
    void testTimeDate48RoundTripNearEndOfSecond() {
        // 999ms
        Instant ts = Instant.ofEpochSecond(1_700_000_000L, 999_000_000L);

        TrdpEncoder encoder = new TrdpEncoder(10);
        encoder.putTimeDate48(ts);
        TrdpDecoder decoder = new TrdpDecoder(encoder.toByteArray());

        Instant decoded = decoder.getTimeDate48();

        assertThat(decoded.getEpochSecond()).isEqualTo(ts.getEpochSecond());
        assertThat(Math.abs(decoded.getNano() - ts.getNano())).isLessThan(16_000);
    }

    @Test
    void testTimeDate32RoundTrip() {
        Instant ts = Instant.ofEpochSecond(1_700_000_000L, 123_000_000);

        TrdpEncoder encoder = new TrdpEncoder(10);
        encoder.putTimeDate32(ts);
        TrdpDecoder decoder = new TrdpDecoder(encoder.toByteArray());

        Instant decoded = decoder.getTimeDate32();

        // TimeDate32 only has second precision
        assertThat(decoded.getEpochSecond()).isEqualTo(ts.getEpochSecond());
        assertThat(decoded.getNano()).isZero();
    }

    @Test
    void testUInt64FullRange() {
        TrdpEncoder encoder = new TrdpEncoder(24);
        // 0, max positive long, and a value that requires unsigned interpretation
        encoder.putUInt64(0L);
        encoder.putUInt64(Long.MAX_VALUE);
        encoder.putUInt64(-1L); // Represents 2^64 - 1 in unsigned

        TrdpDecoder decoder = new TrdpDecoder(encoder.toByteArray());

        assertThat(decoder.getUInt64()).isEqualTo(0L);
        assertThat(decoder.getUInt64()).isEqualTo(Long.MAX_VALUE);
        long maxUnsigned = decoder.getUInt64();
        assertThat(maxUnsigned).isEqualTo(-1L);
        assertThat(Long.toUnsignedString(maxUnsigned)).isEqualTo("18446744073709551615");
    }

    @Test
    void testUInt32Validation() {
        TrdpEncoder encoder = new TrdpEncoder(10);

        assertThatThrownBy(() -> encoder.putUInt32(-1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("UINT32");

        assertThatThrownBy(() -> encoder.putUInt32(0x100000000L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("UINT32");
    }

    @Test
    void testEncoderBufferOverflow() {
        TrdpEncoder encoder = new TrdpEncoder(2);
        encoder.putInt16((short) 1);

        // Buffer is full, next write should overflow
        assertThatThrownBy(() -> encoder.putInt8((byte) 1))
            .isInstanceOf(BufferOverflowException.class);
    }

    @Test
    void testDecoderBufferUnderflow() {
        byte[] data = new byte[2];
        TrdpDecoder decoder = new TrdpDecoder(data);
        decoder.getInt16(); // Reads 2 bytes, buffer now empty

        assertThatThrownBy(() -> decoder.getInt8())
            .isInstanceOf(BufferUnderflowException.class);
    }

    @Test
    void testDecoderSkipBeyondBounds() {
        byte[] data = new byte[4];
        TrdpDecoder decoder = new TrdpDecoder(data);

        assertThatThrownBy(() -> decoder.skip(5))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEncoderReset() {
        TrdpEncoder encoder = new TrdpEncoder(10);
        encoder.putInt32(42);
        assertThat(encoder.position()).isEqualTo(4);

        encoder.reset();
        assertThat(encoder.position()).isZero();

        // Can write again after reset
        encoder.putInt32(99);
        TrdpDecoder decoder = new TrdpDecoder(encoder.toByteArray());
        assertThat(decoder.getInt32()).isEqualTo(99);
    }

    @Test
    void testDecoderRemainingAndRewind() {
        byte[] data = new byte[8];
        TrdpDecoder decoder = new TrdpDecoder(data);

        assertThat(decoder.remaining()).isEqualTo(8);
        decoder.getInt32();
        assertThat(decoder.remaining()).isEqualTo(4);
        assertThat(decoder.position()).isEqualTo(4);

        decoder.rewind();
        assertThat(decoder.remaining()).isEqualTo(8);
        assertThat(decoder.position()).isZero();
    }

    @Test
    void testAlignmentAtBoundary() {
        // When already aligned, align should be a no-op
        TrdpEncoder encoder = new TrdpEncoder(20);
        encoder.putInt32(1); // 4 bytes, already 4-byte aligned
        encoder.align(4);
        assertThat(encoder.position()).isEqualTo(4); // No padding added
    }

    @Test
    void testUtf16RoundTrip() {
        TrdpEncoder encoder = new TrdpEncoder(10);
        encoder.putUtf16('\u00E9'); // e-acute

        TrdpDecoder decoder = new TrdpDecoder(encoder.toByteArray());
        assertThat(decoder.getUtf16()).isEqualTo('\u00E9');
    }
}
