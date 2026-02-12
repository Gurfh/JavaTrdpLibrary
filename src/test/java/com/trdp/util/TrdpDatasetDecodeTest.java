package com.trdp.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests covering TrdpDataset.decode() for all type branches,
 * and inner class getters for Field and FieldDefinition.
 */
class TrdpDatasetDecodeTest {

    @Test
    void testDecodeChar8() {
        TrdpDataset original = new TrdpDataset().addChar8("ch", 'Z');
        byte[] encoded = original.encode();

        List<TrdpDataset.FieldDefinition> schema = List.of(
            new TrdpDataset.FieldDefinition("ch", TrdpDataType.CHAR8));
        TrdpDataset decoded = TrdpDataset.decode(encoded, schema);

        assertThat(decoded.getValue("ch")).isEqualTo('Z');
    }

    @Test
    void testDecodeInt8() {
        TrdpDataset original = new TrdpDataset().addInt8("val", (byte) -42);
        byte[] encoded = original.encode();

        List<TrdpDataset.FieldDefinition> schema = List.of(
            new TrdpDataset.FieldDefinition("val", TrdpDataType.INT8));
        TrdpDataset decoded = TrdpDataset.decode(encoded, schema);

        assertThat(decoded.getValue("val")).isEqualTo((byte) -42);
    }

    @Test
    void testDecodeInt32() {
        TrdpDataset original = new TrdpDataset().addInt32("val", 123456789);
        byte[] encoded = original.encode();

        List<TrdpDataset.FieldDefinition> schema = List.of(
            new TrdpDataset.FieldDefinition("val", TrdpDataType.INT32));
        TrdpDataset decoded = TrdpDataset.decode(encoded, schema);

        assertThat(decoded.getValue("val")).isEqualTo(123456789);
    }

    @Test
    void testDecodeInt64() {
        TrdpDataset original = new TrdpDataset().addInt64("val", 9876543210L);
        byte[] encoded = original.encode();

        List<TrdpDataset.FieldDefinition> schema = List.of(
            new TrdpDataset.FieldDefinition("val", TrdpDataType.INT64));
        TrdpDataset decoded = TrdpDataset.decode(encoded, schema);

        assertThat(decoded.getValue("val")).isEqualTo(9876543210L);
    }

    @Test
    void testDecodeReal64() {
        TrdpDataset original = new TrdpDataset().addReal64("val", 3.141592653589793);
        byte[] encoded = original.encode();

        List<TrdpDataset.FieldDefinition> schema = List.of(
            new TrdpDataset.FieldDefinition("val", TrdpDataType.REAL64));
        TrdpDataset decoded = TrdpDataset.decode(encoded, schema);

        assertThat((Double) decoded.getValue("val")).isCloseTo(3.141592653589793, within(1e-15));
    }

    @Test
    void testDecodeAllTypesRoundTrip() {
        Instant now = Instant.ofEpochSecond(1700000000, 500_000_000);

        TrdpDataset original = new TrdpDataset()
            .addBool8("flag", false)
            .addChar8("ch", 'X')
            .addInt8("i8", (byte) -128)
            .addInt16("i16", (short) -30000)
            .addInt32("i32", -100000)
            .addInt64("i64", -9999999999L)
            .addUInt8("u8", 200)
            .addUInt16("u16", 50000)
            .addUInt32("u32", 3000000000L)
            .addReal32("f32", 1.5f)
            .addReal64("f64", 2.718281828)
            .addTimeDate64("time", now);

        byte[] encoded = original.encode();

        List<TrdpDataset.FieldDefinition> schema = Arrays.asList(
            new TrdpDataset.FieldDefinition("flag", TrdpDataType.BOOL8),
            new TrdpDataset.FieldDefinition("ch", TrdpDataType.CHAR8),
            new TrdpDataset.FieldDefinition("i8", TrdpDataType.INT8),
            new TrdpDataset.FieldDefinition("i16", TrdpDataType.INT16),
            new TrdpDataset.FieldDefinition("i32", TrdpDataType.INT32),
            new TrdpDataset.FieldDefinition("i64", TrdpDataType.INT64),
            new TrdpDataset.FieldDefinition("u8", TrdpDataType.UINT8),
            new TrdpDataset.FieldDefinition("u16", TrdpDataType.UINT16),
            new TrdpDataset.FieldDefinition("u32", TrdpDataType.UINT32),
            new TrdpDataset.FieldDefinition("f32", TrdpDataType.REAL32),
            new TrdpDataset.FieldDefinition("f64", TrdpDataType.REAL64),
            new TrdpDataset.FieldDefinition("time", TrdpDataType.TIMEDATE64));

        TrdpDataset decoded = TrdpDataset.decode(encoded, schema);

        assertThat(decoded.getValue("flag")).isEqualTo(false);
        assertThat(decoded.getValue("ch")).isEqualTo('X');
        assertThat(decoded.getValue("i8")).isEqualTo((byte) -128);
        assertThat(decoded.getValue("i16")).isEqualTo((short) -30000);
        assertThat(decoded.getValue("i32")).isEqualTo(-100000);
        assertThat(decoded.getValue("i64")).isEqualTo(-9999999999L);
        assertThat(decoded.getValue("u8")).isEqualTo(200);
        assertThat(decoded.getValue("u16")).isEqualTo(50000);
        assertThat(decoded.getValue("u32")).isEqualTo(3000000000L);
        assertThat((Float) decoded.getValue("f32")).isCloseTo(1.5f, within(0.001f));
        assertThat((Double) decoded.getValue("f64")).isCloseTo(2.718281828, within(1e-9));
    }

    @Test
    void testFieldGetters() {
        TrdpDataset dataset = new TrdpDataset().addInt32("myField", 42);
        TrdpDataset.Field field = dataset.getFields().get(0);

        assertThat(field.getName()).isEqualTo("myField");
        assertThat(field.getType()).isEqualTo(TrdpDataType.INT32);
        assertThat(field.getValue()).isEqualTo(42);
    }

    @Test
    void testFieldDefinitionGetters() {
        TrdpDataset.FieldDefinition def = new TrdpDataset.FieldDefinition("temp", TrdpDataType.REAL32);
        assertThat(def.getName()).isEqualTo("temp");
        assertThat(def.getType()).isEqualTo(TrdpDataType.REAL32);
    }
}
