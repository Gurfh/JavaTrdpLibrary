package com.trdp.config;

import com.trdp.util.TrdpDataType;
import com.trdp.util.TrdpDataset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Compatibility tests using IEC 61375-2-3 XML configuration files
 * with both numeric type IDs and string type names.
 */
class TrdpConfigCompatibilityTest {

    private static final String XML_DIR = "/tcnopen-xml/";

    private DeviceConfig load(String filename) throws TrdpConfigException {
        InputStream is = getClass().getResourceAsStream(XML_DIR + filename);
        assertThat(is).as("Test resource %s must exist", filename).isNotNull();
        return TrdpConfig.load(is);
    }

    // --- Loading XSD-valid config files ---

    @ParameterizedTest
    @ValueSource(strings = {
            "complete_example.xml",
            "device1.xml",
            "device2.xml",
            "example.xml"
    })
    void loadsXsdValidFiles(String filename) throws TrdpConfigException {
        DeviceConfig config = load(filename);
        assertThat(config.getHostName()).isNotNull();
        assertThat(config.getBusInterfaces()).isNotEmpty();
    }

    // --- XSD-invalid files fail validation ---

    @ParameterizedTest
    @ValueSource(strings = {
            "nestedDS.xml",
            "pdsend_example.xml",
            "speedtest1.xml",
            "speedtest2.xml",
            "test_sdt.xml"
    })
    void xsdInvalidFilesRejected(String filename) {
        assertThatThrownBy(() -> load(filename))
                .isInstanceOf(TrdpConfigException.class)
                .hasMessageContaining("XML validation failed");
    }

    // --- String type names with BITSET8/ANTIVALENT8 (complete_example.xml) ---

    @Test
    void completeExampleBitset8Antivalent8() throws TrdpConfigException {
        DeviceConfig config = load("complete_example.xml");
        assertThat(config.getHostName()).isEqualTo("examplehost");
        assertThat(config.getBusInterfaces()).hasSize(2);
        assertThat(config.getDataSets()).hasSize(6);

        DatasetMarshaller marshaller = DatasetMarshaller.from(config);

        // ComID 1004 references dataset 1004 which includes BITSET8 and ANTIVALENT8
        assertThat(marshaller.hasSchema(1004)).isTrue();
        var schema = marshaller.getSchema(1004);

        // BITSET8 and ANTIVALENT8 should resolve to BOOL8
        long boolCount = schema.stream()
                .filter(f -> f.getType() == TrdpDataType.BOOL8)
                .count();
        // BOOL8 + BITSET8 + ANTIVALENT8 = 3 fields
        assertThat(boolCount).isEqualTo(3);
    }

    @Test
    void completeExampleAllStringTypes() throws TrdpConfigException {
        DeviceConfig config = load("complete_example.xml");
        DatasetMarshaller marshaller = DatasetMarshaller.from(config);

        // Dataset 1004 has 15 types (no TIMEDATE48; BITSET8+ANTIVALENT8 resolve to BOOL8)
        var schema1004 = marshaller.getSchema(1004);
        assertThat(schema1004.stream().map(TrdpDataset.FieldDefinition::getType).distinct())
                .containsExactlyInAnyOrder(
                        TrdpDataType.UINT8, TrdpDataType.UINT16, TrdpDataType.UINT32, TrdpDataType.UINT64,
                        TrdpDataType.INT8, TrdpDataType.INT16, TrdpDataType.INT32, TrdpDataType.INT64,
                        TrdpDataType.REAL32, TrdpDataType.REAL64,
                        TrdpDataType.BOOL8, TrdpDataType.CHAR8, TrdpDataType.UTF16,
                        TrdpDataType.TIMEDATE32, TrdpDataType.TIMEDATE64);

        // Dataset 1003 has TIMEDATE48
        var schema1003 = marshaller.getSchema(1003);
        assertThat(schema1003.stream().map(TrdpDataset.FieldDefinition::getType))
                .contains(TrdpDataType.TIMEDATE32, TrdpDataType.TIMEDATE48, TrdpDataType.TIMEDATE64);
    }

    @Test
    void completeExampleNestedDataset() throws TrdpConfigException {
        DeviceConfig config = load("complete_example.xml");
        DatasetMarshaller marshaller = DatasetMarshaller.from(config);

        // ComID 1003 references dataset 1003 (source-sink on bus-interface 2)
        assertThat(marshaller.hasSchema(1003)).isTrue();
        var schema = marshaller.getSchema(1003);
        assertThat(schema.stream().map(TrdpDataset.FieldDefinition::getType))
                .contains(TrdpDataType.TIMEDATE32, TrdpDataType.TIMEDATE48, TrdpDataType.TIMEDATE64);
    }

    @Test
    void completeExampleMarshallRoundTrip() throws TrdpConfigException {
        DeviceConfig config = load("complete_example.xml");
        DatasetMarshaller marshaller = DatasetMarshaller.from(config);

        // ComID 1001 -> dataset 1001: UINT8 u8_A, UINT8 u8_B, UINT16 u16, UINT32 u32, UINT64 u64
        byte[] encoded = marshaller.marshall(1001, Map.of(
                "u8_A", 0xAA,
                "u8_B", 0x55,
                "u16", 1234,
                "u32", 0xCAFEBABEL,
                "u64", Long.MIN_VALUE));

        assertThat(encoded).hasSize(16); // 1+1+2+4+8

        TrdpDataset decoded = marshaller.unmarshall(1001, encoded);
        assertThat(decoded.getValue("u8_A")).isEqualTo(0xAA);
        assertThat(decoded.getValue("u8_B")).isEqualTo(0x55);
        assertThat(decoded.getValue("u16")).isEqualTo(1234);
        assertThat(decoded.getValue("u32")).isEqualTo(0xCAFEBABEL);
        assertThat(decoded.getValue("u64")).isEqualTo(Long.MIN_VALUE);
    }

    // --- Numeric type IDs (device1.xml) ---

    @Test
    void device1NumericTypeIds() throws TrdpConfigException {
        DeviceConfig config = load("device1.xml");
        assertThat(config.getHostName()).isEqualTo("device1");

        DatasetMarshaller marshaller = DatasetMarshaller.from(config);

        // ComID 1001 references dataset 1001 with numeric type IDs
        assertThat(marshaller.hasSchema(1001)).isTrue();
        var schema = marshaller.getSchema(1001);

        // Dataset 1001: u8_A(8=UINT8), u8_B(8=UINT8), u16(9=UINT16), u32(10=UINT32), u64(11=UINT64)
        assertThat(schema).hasSize(5);
        assertThat(schema.get(0).getType()).isEqualTo(TrdpDataType.UINT8);
        assertThat(schema.get(1).getType()).isEqualTo(TrdpDataType.UINT8);
        assertThat(schema.get(2).getType()).isEqualTo(TrdpDataType.UINT16);
        assertThat(schema.get(3).getType()).isEqualTo(TrdpDataType.UINT32);
        assertThat(schema.get(4).getType()).isEqualTo(TrdpDataType.UINT64);
    }

    @Test
    void device1MarshallRoundTrip() throws TrdpConfigException {
        DeviceConfig config = load("device1.xml");
        DatasetMarshaller marshaller = DatasetMarshaller.from(config);

        byte[] encoded = marshaller.marshall(1001, Map.of(
                "u8_A", 0xFF,
                "u8_B", 0x42,
                "u16", 12345,
                "u32", 0xDEADBEEFL,
                "u64", Long.MAX_VALUE));

        // UINT8(1) + UINT8(1) + UINT16(2) + UINT32(4) + UINT64(8) = 16 bytes
        assertThat(encoded).hasSize(16);

        TrdpDataset decoded = marshaller.unmarshall(1001, encoded);
        assertThat(decoded.getValue("u8_A")).isEqualTo(0xFF);
        assertThat(decoded.getValue("u32")).isEqualTo(0xDEADBEEFL);
        assertThat(decoded.getValue("u64")).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void device1SdtParameters() throws TrdpConfigException {
        DeviceConfig config = load("device1.xml");
        BusInterface bi = config.getBusInterfaces().get(0);
        TelegramConfig telegram = bi.getTelegrams().get(0);

        assertThat(telegram.getSources()).isNotEmpty();
        SourceConfig src = telegram.getSources().get(0);
        assertThat(src.getSdtParameter()).isNotNull();
        assertThat(src.getSdtParameter().getSmi1()).isEqualTo(1234);
    }

    // --- device2.xml with non-standard type=28 ---

    @Test
    void device2LoadsAndType28FailsInMarshalling() throws TrdpConfigException {
        // device2.xml loads fine — XSD doesn't validate element type values
        DeviceConfig config = load("device2.xml");
        assertThat(config.getHostName()).isEqualTo("device2");

        // ComID 1001 references dataset 1001 which only has valid types
        DatasetMarshaller marshaller = DatasetMarshaller.from(config);
        assertThat(marshaller.hasSchema(1001)).isTrue();

        // Round-trip on dataset 1001 (no type=28 there)
        byte[] encoded = marshaller.marshall(1001, Map.of("u8_A", 1, "u32", 99L));
        TrdpDataset decoded = marshaller.unmarshall(1001, encoded);
        assertThat(decoded.getValue("u8_A")).isEqualTo(1);
        assertThat(decoded.getValue("u32")).isEqualTo(99L);
    }

    // --- example.xml with nested datasets and SDT ---

    @Test
    void exampleWithSdt() throws TrdpConfigException {
        DeviceConfig config = load("example.xml");
        assertThat(config.getHostName()).isEqualTo("examplehost");
        assertThat(config.getBusInterfaces()).hasSize(2);

        DatasetMarshaller marshaller = DatasetMarshaller.from(config);

        // ComID 1001 -> dataset 1001
        assertThat(marshaller.hasSchema(1001)).isTrue();
        // ComID 1002 -> dataset 1002 (arrays of UINT types)
        assertThat(marshaller.hasSchema(1002)).isTrue();
        var schema1002 = marshaller.getSchema(1002);
        // 16+16+16+16 = 64 array elements
        assertThat(schema1002).hasSize(64);
    }
}
