package com.trdp.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests covering TrdpDecoder.getBytes() and skip() methods,
 * plus TrdpDataType.getDescription().
 */
class TrdpDecoderAdditionalTest {

    @Test
    void testGetBytes() {
        byte[] data = {10, 20, 30, 40, 50};
        TrdpDecoder decoder = new TrdpDecoder(data);

        byte[] first = decoder.getBytes(3);
        assertThat(first).containsExactly(10, 20, 30);

        byte[] second = decoder.getBytes(2);
        assertThat(second).containsExactly(40, 50);
    }

    @Test
    void testSkip() {
        byte[] data = {1, 2, 3, 4, 5, 6};
        TrdpDecoder decoder = new TrdpDecoder(data);

        assertThat(decoder.position()).isEqualTo(0);

        decoder.skip(2);
        assertThat(decoder.position()).isEqualTo(2);

        byte val = decoder.getInt8();
        assertThat(val).isEqualTo((byte) 3);
    }

    @Test
    void testGetBytesAndSkipCombined() {
        byte[] data = {0, 0, 0, 99, 0, 0};
        TrdpDecoder decoder = new TrdpDecoder(data);

        decoder.skip(3);
        byte[] result = decoder.getBytes(1);
        assertThat(result).containsExactly(99);
        assertThat(decoder.remaining()).isEqualTo(2);
    }

    @Test
    void testGetStringWithNullTerminator() {
        // "Hi" + null + padding
        byte[] data = {'H', 'i', 0, 0, 0};
        TrdpDecoder decoder = new TrdpDecoder(data);

        String str = decoder.getString(5);
        assertThat(str).isEqualTo("Hi");
    }

    @Test
    void testGetStringNoNullTerminator() {
        // Full string without null terminator
        byte[] data = {'A', 'B', 'C', 'D'};
        TrdpDecoder decoder = new TrdpDecoder(data);

        String str = decoder.getString(4);
        assertThat(str).isEqualTo("ABCD");
    }

    @Test
    void testTrdpDataTypeGetDescription() {
        assertThat(TrdpDataType.BOOL8.getDescription()).isEqualTo("Boolean 8-bit");
        assertThat(TrdpDataType.INT32.getDescription()).isEqualTo("Signed integer 32-bit");
        assertThat(TrdpDataType.REAL64.getDescription()).isEqualTo("IEEE 754 double-precision float");
        assertThat(TrdpDataType.TIMEDATE48.getDescription()).isEqualTo("48-bit time (seconds + microseconds)");
    }
}
