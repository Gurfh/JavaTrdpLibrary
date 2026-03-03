package com.trdp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DeviceConfigTest {

    private static DeviceConfig config;

    @BeforeAll
    static void loadConfig() throws TrdpConfigException {
        Path path = Path.of("src/test/resources/trdp-config-full.xml");
        config = TrdpConfig.load(path);
    }

    @Test
    void getDataSetByIdReturnsFirstMatch() {
        Optional<DataSetDefinition> ds = config.getDataSetById(1001);
        assertThat(ds).isPresent();
        assertThat(ds.get().getName()).isEqualTo("StatusDataSet");
    }

    @Test
    void getDataSetByIdReturnsEmptyForUnknownId() {
        Optional<DataSetDefinition> ds = config.getDataSetById(9999);
        assertThat(ds).isEmpty();
    }

    @Test
    void getComParameterByIdReturnsFirstMatch() {
        Optional<ComParameter> cp = config.getComParameterById(1);
        assertThat(cp).isPresent();
        assertThat(cp.get().getQos()).isEqualTo(5);
    }

    @Test
    void getComParameterByIdReturnsEmptyForUnknownId() {
        Optional<ComParameter> cp = config.getComParameterById(9999);
        assertThat(cp).isEmpty();
    }
}
