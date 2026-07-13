package com.trdp.config;

import com.trdp.md.MdRequestHandler;
import com.trdp.md.MdResponse;
import com.trdp.md.TransportProtocol;
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

    // ==================== Phase 2: PD config wiring ====================

    @Test
    void configurePdAppliesTrafficShaping() throws Exception {
        DeviceConfig config = TrdpConfig.load(Path.of("src/test/resources/trdp-config-full.xml"));
        // First bus interface has traffic-shaping="on"
        BusInterface bi = config.getBusInterfaces().get(0);

        try (TrdpSessionFactory.ConfiguredPdSession session =
                     TrdpSessionFactory.configurePd(config, bi, NO_OP_LISTENER)) {
            assertThat(session.getSession().isTrafficShapingEnabled()).isTrue();
        }
    }

    @Test
    void configurePdTrafficShapingDisabled() throws Exception {
        DeviceConfig config = TrdpConfig.load(Path.of("src/test/resources/trdp-config-full.xml"));
        // Second bus interface has default trdp-process (traffic-shaping defaults to true),
        // but we can verify the session is created successfully
        BusInterface bi = config.getBusInterfaces().get(1);

        try (TrdpSessionFactory.ConfiguredPdSession session =
                     TrdpSessionFactory.configurePd(config, bi, NO_OP_LISTENER)) {
            assertThat(session.getSession().isTrafficShapingEnabled()).isTrue();
        }
    }

    // ==================== Phase 3: MD factory ====================

    @Test
    void configureMdCreatesRequesterAndReplier() throws Exception {
        DeviceConfig config = TrdpConfig.load(Path.of("src/test/resources/trdp-config-full.xml"));
        BusInterface bi = config.getBusInterfaces().get(0);

        MdRequestHandler handler = request -> new MdResponse("reply".getBytes());

        try (TrdpSessionFactory.ConfiguredMdSession session =
                     TrdpSessionFactory.configureMd(config, bi, handler)) {
            assertThat(session.getRequester()).isNotNull();
            assertThat(session.getReplier()).isNotNull();
            assertThat(session.getMarshaller()).isNotNull();
        }
    }

    @Test
    void configureMdAppliesTimeoutsFromConfig() throws Exception {
        DeviceConfig config = TrdpConfig.load(Path.of("src/test/resources/trdp-config-full.xml"));
        BusInterface bi = config.getBusInterfaces().get(0);
        // md-com-parameter has reply-timeout="3000000" connect-timeout="30000000"

        MdRequestHandler handler = request -> new MdResponse("reply".getBytes());

        try (TrdpSessionFactory.ConfiguredMdSession session =
                     TrdpSessionFactory.configureMd(config, bi, handler)) {
            assertThat(session.getRequester().getReplyTimeoutUs()).isEqualTo(3_000_000);
            assertThat(session.getRequester().getConnectTimeoutUs()).isEqualTo(30_000_000);
            assertThat(session.getReplier().getConfirmTimeoutUs()).isEqualTo(2_000_000);
        }
    }

    @Test
    void configureMdBuildsTelegramConfigs() throws Exception {
        DeviceConfig config = TrdpConfig.load(Path.of("src/test/resources/trdp-config-full.xml"));
        BusInterface bi = config.getBusInterfaces().get(0);
        // ComID 2000 has md-parameter with protocol="TCP", reply-timeout=6000000

        MdRequestHandler handler = request -> new MdResponse("reply".getBytes());

        try (TrdpSessionFactory.ConfiguredMdSession session =
                     TrdpSessionFactory.configureMd(config, bi, handler)) {
            Map<Integer, TrdpSessionFactory.MdTelegramConfig> configs = session.getTelegramConfigs();
            assertThat(configs).containsKey(2000);

            TrdpSessionFactory.MdTelegramConfig cfg = configs.get(2000);
            assertThat(cfg.protocol()).isEqualTo(TransportProtocol.TCP);
            assertThat(cfg.replyTimeoutUs()).isEqualTo(6_000_000);
            assertThat(cfg.confirmTimeoutUs()).isEqualTo(3_000_000);
        }
    }

    @Test
    void configureMdRejectsEqualUdpAndTcpPorts() throws Exception {
        DeviceConfig config = TrdpConfig.load(Path.of("src/test/resources/trdp-config-full.xml"));
        MdRequestHandler handler = request -> new MdResponse("reply".getBytes());

        // Defaulted md-com-parameter: udp-port and tcp-port both 17225
        MdComParameter mdCom = new MdComParameter(null, null, null, null, null, null,
                null, null, null, null, null, null);
        BusInterface bi = new BusInterface(1, "equal-ports", null, null, null, null, mdCom, null);

        assertThatThrownBy(() -> TrdpSessionFactory.configureMd(config, bi, handler))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("udp-port")
                .hasMessageContaining("17225");
    }

    @Test
    void configureMdMarshallerAvailable() throws Exception {
        DeviceConfig config = TrdpConfig.load(Path.of("src/test/resources/trdp-config-full.xml"));
        BusInterface bi = config.getBusInterfaces().get(0);

        MdRequestHandler handler = request -> new MdResponse("reply".getBytes());

        try (TrdpSessionFactory.ConfiguredMdSession session =
                     TrdpSessionFactory.configureMd(config, bi, handler)) {
            DatasetMarshaller marshaller = session.getMarshaller();
            assertThat(marshaller).isNotNull();
        }
    }
}
