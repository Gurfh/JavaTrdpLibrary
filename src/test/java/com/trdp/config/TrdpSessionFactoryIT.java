package com.trdp.config;

import com.trdp.pd.PdEvent;
import com.trdp.pd.PdEventListener;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrdpSessionFactoryIT {

    private static final PdEventListener NO_OP_LISTENER = new PdEventListener() {
        @Override
        public void onData(PdEvent event) {}

        @Override
        public void onTimeout(PdEvent event) {}

        @Override
        public void onValidityRestored(PdEvent event) {}
    };

    @Test
    void configurePdCreatesPublishersAndSubscribers() throws Exception {
        DeviceConfig config = TrdpConfig.load(Path.of("src/test/resources/trdp-config-full.xml"));
        BusInterface bi = config.getBusInterfaces().get(0);

        try (TrdpSessionFactory.ConfiguredPdSession session =
                     TrdpSessionFactory.configurePd(config, bi, NO_OP_LISTENER)) {
            // ComID 1000 is type="source" → publisher
            assertThat(session.getPublishers()).containsKey(1000);
            assertThat(session.getPublishers().get(1000).getComId()).isEqualTo(1000);

            // ComID 2000 is type="sink" with md-parameter only (no pd-parameter) → skipped
            assertThat(session.getPublishers()).doesNotContainKey(2000);
            assertThat(session.getSubscribers()).doesNotContainKey(2000);
        }
    }

    @Test
    void configurePdSourceSinkCreatesBoth() throws Exception {
        DeviceConfig config = TrdpConfig.load(Path.of("src/test/resources/trdp-config-full.xml"));
        // Second bus interface has type="source-sink" telegram (ComID 3000)
        BusInterface bi = config.getBusInterfaces().get(1);

        try (TrdpSessionFactory.ConfiguredPdSession session =
                     TrdpSessionFactory.configurePd(config, bi, NO_OP_LISTENER)) {
            assertThat(session.getPublishers()).containsKey(3000);
            assertThat(session.getSubscribers()).containsKey(3000);
        }
    }

    @Test
    void putDataWithMapUsesMarshaller() throws Exception {
        DeviceConfig config = TrdpConfig.load(Path.of("src/test/resources/trdp-config-full.xml"));
        BusInterface bi = config.getBusInterfaces().get(0);

        try (TrdpSessionFactory.ConfiguredPdSession session =
                     TrdpSessionFactory.configurePd(config, bi, NO_OP_LISTENER)) {
            // Should not throw — marshalls and stages data
            session.putData(1000, Map.of("speed", 42L, "doorOpen", true));
        }
    }

    @Test
    void putDataWithRawBytes() throws Exception {
        DeviceConfig config = TrdpConfig.load(Path.of("src/test/resources/trdp-config-full.xml"));
        BusInterface bi = config.getBusInterfaces().get(0);

        try (TrdpSessionFactory.ConfiguredPdSession session =
                     TrdpSessionFactory.configurePd(config, bi, NO_OP_LISTENER)) {
            session.putData(1000, new byte[11]);
        }
    }

    @Test
    void putDataThrowsForUnknownComId() throws Exception {
        DeviceConfig config = TrdpConfig.load(Path.of("src/test/resources/trdp-config-full.xml"));
        BusInterface bi = config.getBusInterfaces().get(0);

        try (TrdpSessionFactory.ConfiguredPdSession session =
                     TrdpSessionFactory.configurePd(config, bi, NO_OP_LISTENER)) {
            assertThatThrownBy(() -> session.putData(9999, new byte[1]))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("9999");
        }
    }

    @Test
    void marshallerAvailableFromSession() throws Exception {
        DeviceConfig config = TrdpConfig.load(Path.of("src/test/resources/trdp-config-full.xml"));
        BusInterface bi = config.getBusInterfaces().get(0);

        try (TrdpSessionFactory.ConfiguredPdSession session =
                     TrdpSessionFactory.configurePd(config, bi, NO_OP_LISTENER)) {
            DatasetMarshaller marshaller = session.getMarshaller();
            assertThat(marshaller.hasSchema(1000)).isTrue();
        }
    }
}
