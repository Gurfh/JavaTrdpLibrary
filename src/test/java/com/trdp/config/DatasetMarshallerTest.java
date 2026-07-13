package com.trdp.config;

import com.trdp.util.TrdpDataType;
import com.trdp.util.TrdpDataset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasetMarshallerTest {

    private static DatasetMarshaller marshaller;

    @BeforeAll
    static void loadConfig() throws TrdpConfigException {
        DeviceConfig config = TrdpConfig.load(Path.of("src/test/resources/trdp-config-full.xml"));
        marshaller = DatasetMarshaller.from(config);
    }

    // --- Schema resolution ---

    @Test
    void hasSchemaForTelegramWithDataSet() {
        // ComID 1000 references data-set-id 1001
        assertThat(marshaller.hasSchema(1000)).isTrue();
    }

    @Test
    void hasSchemaForNestedDataSet() {
        // ComID 3000 references data-set-id 1002 which nests 1001
        assertThat(marshaller.hasSchema(3000)).isTrue();
    }

    @Test
    void noSchemaForTelegramWithoutDataSet() {
        // ComID 2000 has no data-set-id
        assertThat(marshaller.hasSchema(2000)).isFalse();
    }

    @Test
    void noSchemaForUnknownComId() {
        assertThat(marshaller.hasSchema(9999)).isFalse();
    }

    // --- Schema structure ---

    @Test
    void schemaForSimpleDataSet() {
        // DataSet 1001: UINT32 speed, INT16 temperature[3], BOOL8 doorOpen
        List<TrdpDataset.FieldDefinition> schema = marshaller.getSchema(1000);
        assertThat(schema).hasSize(5);

        assertThat(schema.get(0).getName()).isEqualTo("speed");
        assertThat(schema.get(0).getType()).isEqualTo(TrdpDataType.UINT32);

        // temperature array expands to 3 indexed fields
        assertThat(schema.get(1).getName()).isEqualTo("temperature[0]");
        assertThat(schema.get(1).getType()).isEqualTo(TrdpDataType.INT16);
        assertThat(schema.get(2).getName()).isEqualTo("temperature[1]");
        assertThat(schema.get(3).getName()).isEqualTo("temperature[2]");

        assertThat(schema.get(4).getName()).isEqualTo("doorOpen");
        assertThat(schema.get(4).getType()).isEqualTo(TrdpDataType.BOOL8);
    }

    @Test
    void schemaForNestedDataSet() {
        // DataSet 1002: UINT8 errorCode, nested[2] (ref to 1001)
        // 1001 has: UINT32 speed, INT16 temperature[3], BOOL8 doorOpen = 5 fields
        // So 1002 = 1 + 2*5 = 11 fields
        List<TrdpDataset.FieldDefinition> schema = marshaller.getSchema(3000);
        assertThat(schema).hasSize(11);

        assertThat(schema.get(0).getName()).isEqualTo("errorCode");
        assertThat(schema.get(0).getType()).isEqualTo(TrdpDataType.UINT8);

        // First nested instance: nested[0].speed, nested[0].temperature[0..2], nested[0].doorOpen
        assertThat(schema.get(1).getName()).isEqualTo("nested[0].speed");
        assertThat(schema.get(1).getType()).isEqualTo(TrdpDataType.UINT32);
        assertThat(schema.get(2).getName()).isEqualTo("nested[0].temperature[0]");
        assertThat(schema.get(5).getName()).isEqualTo("nested[0].doorOpen");

        // Second nested instance
        assertThat(schema.get(6).getName()).isEqualTo("nested[1].speed");
        assertThat(schema.get(10).getName()).isEqualTo("nested[1].doorOpen");
    }

    @Test
    void getSchemaThrowsForUnknownComId() {
        assertThatThrownBy(() -> marshaller.getSchema(9999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("9999");
    }

    // --- Marshall ---

    @Test
    void marshallEncodesValues() {
        byte[] data = marshaller.marshall(1000, Map.of(
                "speed", 100L,
                "temperature[0]", (short) 25,
                "temperature[1]", (short) -10,
                "temperature[2]", (short) 30,
                "doorOpen", true));

        // UINT32(4) + INT16(2)*3 + BOOL8(1) = 11 bytes
        assertThat(data).hasSize(11);
    }

    @Test
    void marshallDefaultsForMissingValues() {
        // All fields default to zero/false
        byte[] data = marshaller.marshall(1000, Map.of());
        assertThat(data).hasSize(11);

        // Verify all zeros
        TrdpDataset decoded = marshaller.unmarshall(1000, data);
        assertThat(decoded.getValue("speed")).isEqualTo(0L);
        assertThat(decoded.getValue("temperature[0]")).isEqualTo((short) 0);
        assertThat(decoded.getValue("doorOpen")).isEqualTo(false);
    }

    @Test
    void marshallThrowsForUnknownComId() {
        assertThatThrownBy(() -> marshaller.marshall(9999, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("9999");
    }

    // --- Unmarshall ---

    @Test
    void marshallUnmarshallRoundTrip() {
        Map<String, Object> values = Map.of(
                "speed", 42L,
                "temperature[0]", (short) 20,
                "temperature[1]", (short) -5,
                "temperature[2]", (short) 35,
                "doorOpen", true);

        byte[] encoded = marshaller.marshall(1000, values);
        TrdpDataset decoded = marshaller.unmarshall(1000, encoded);

        assertThat(decoded.getValue("speed")).isEqualTo(42L);
        assertThat(decoded.getValue("temperature[0]")).isEqualTo((short) 20);
        assertThat(decoded.getValue("temperature[1]")).isEqualTo((short) -5);
        assertThat(decoded.getValue("temperature[2]")).isEqualTo((short) 35);
        assertThat(decoded.getValue("doorOpen")).isEqualTo(true);
    }

    @Test
    void nestedDataSetRoundTrip() {
        Map<String, Object> values = Map.of(
                "errorCode", 7,
                "nested[0].speed", 100L,
                "nested[0].doorOpen", true,
                "nested[1].speed", 200L);

        byte[] encoded = marshaller.marshall(3000, values);
        TrdpDataset decoded = marshaller.unmarshall(3000, encoded);

        assertThat(decoded.getValue("errorCode")).isEqualTo(7);
        assertThat(decoded.getValue("nested[0].speed")).isEqualTo(100L);
        assertThat(decoded.getValue("nested[0].doorOpen")).isEqualTo(true);
        assertThat(decoded.getValue("nested[1].speed")).isEqualTo(200L);
        assertThat(decoded.getValue("nested[1].doorOpen")).isEqualTo(false); // defaulted
    }

    @Test
    void unmarshallThrowsForUnknownComId() {
        assertThatThrownBy(() -> marshaller.unmarshall(9999, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("9999");
    }

    // --- Cycle detection ---

    @Test
    void cyclicDatasetReferenceIsRejected() throws TrdpConfigException {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <device host-name="CYCLIC">
                  <bus-interface-list>
                    <bus-interface network-id="1" name="eth0">
                      <telegram com-id="7000" data-set-id="1001" type="source"/>
                    </bus-interface>
                  </bus-interface-list>
                  <data-set-list>
                    <data-set id="1001" name="A">
                      <element type="1002" name="b"/>
                    </data-set>
                    <data-set id="1002" name="B">
                      <element type="1001" name="a"/>
                    </data-set>
                  </data-set-list>
                </device>
                """;
        DeviceConfig config = TrdpConfig.load(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> DatasetMarshaller.from(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cyclic");
    }

    @Test
    void diamondDatasetReferenceIsAllowed() throws TrdpConfigException {
        // 1001 references 1002 twice (sibling references) — shared, not cyclic
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <device host-name="DIAMOND">
                  <bus-interface-list>
                    <bus-interface network-id="1" name="eth0">
                      <telegram com-id="7001" data-set-id="1001" type="source"/>
                    </bus-interface>
                  </bus-interface-list>
                  <data-set-list>
                    <data-set id="1001" name="A">
                      <element type="1002" name="first"/>
                      <element type="1002" name="second"/>
                    </data-set>
                    <data-set id="1002" name="B">
                      <element type="UINT8" name="value"/>
                    </data-set>
                  </data-set-list>
                </device>
                """;
        DeviceConfig config = TrdpConfig.load(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        DatasetMarshaller diamondMarshaller = DatasetMarshaller.from(config);
        assertThat(diamondMarshaller.getSchema(7001))
                .extracting(TrdpDataset.FieldDefinition::getName)
                .containsExactly("first.value", "second.value");
    }
}
