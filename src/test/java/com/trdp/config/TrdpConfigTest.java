package com.trdp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class TrdpConfigTest {

    // --- Load from Path ---

    @Test
    void loadMinimalFromPath() throws TrdpConfigException {
        DeviceConfig config = TrdpConfig.load(Path.of("src/test/resources/trdp-config-minimal.xml"));
        assertThat(config.getHostName()).isEqualTo("minDevice");
        assertThat(config.getBusInterfaces()).isEmpty();
        assertThat(config.getDataSets()).isEmpty();
        assertThat(config.getComParameters()).isEmpty();
        assertThat(config.getServices()).isEmpty();
        assertThat(config.getMappedDevices()).isEmpty();
        assertThat(config.getType()).isNull();
        assertThat(config.getLeaderName()).isNull();
        assertThat(config.getDeviceConfiguration()).isNull();
        assertThat(config.getDebug()).isNull();
    }

    // --- Load from InputStream ---

    @Test
    void loadMinimalFromInputStream() throws TrdpConfigException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("trdp-config-minimal.xml");
        DeviceConfig config = TrdpConfig.load(is);
        assertThat(config.getHostName()).isEqualTo("minDevice");
    }

    // --- XSD validation failure ---

    @Test
    void loadInvalidXmlThrowsException() {
        assertThatThrownBy(() -> TrdpConfig.load(Path.of("src/test/resources/trdp-config-invalid.xml")))
                .isInstanceOf(TrdpConfigException.class)
                .hasMessageContaining("XML validation failed");
    }

    @Test
    void loadNonExistentFileThrowsException() {
        assertThatThrownBy(() -> TrdpConfig.load(Path.of("src/test/resources/nonexistent.xml")))
                .isInstanceOf(TrdpConfigException.class)
                .hasMessageContaining("Failed to read configuration file");
    }

    @Test
    void loadMalformedXmlThrowsException() {
        String malformed = "<device host-name=\"test\"><not-closed>";
        InputStream is = new ByteArrayInputStream(malformed.getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> TrdpConfig.load(is))
                .isInstanceOf(TrdpConfigException.class);
    }

    // --- Default values ---

    @Test
    void defaultValuesAppliedWhenOmitted() throws TrdpConfigException {
        DeviceConfig config = TrdpConfig.load(Path.of("src/test/resources/trdp-config-defaults.xml"));
        assertThat(config.getHostName()).isEqualTo("defaultDev");

        BusInterface bi = config.getBusInterfaces().get(0);
        assertThat(bi.getNetworkId()).isEqualTo(1);
        assertThat(bi.getName()).isEqualTo("eth0");

        TrdpProcessConfig tp = bi.getTrdpProcess();
        assertThat(tp).isNotNull();
        assertThat(tp.getCycleTime()).isEqualTo(10000);
        assertThat(tp.isBlocking()).isFalse();
        assertThat(tp.isTrafficShaping()).isTrue();
        assertThat(tp.getPriority()).isEqualTo(64);

        PdComParameter pdCom = bi.getPdComParameter();
        assertThat(pdCom).isNotNull();
        assertThat(pdCom.getTimeoutValue()).isEqualTo(100000);
        assertThat(pdCom.getTtl()).isEqualTo(64);
        assertThat(pdCom.getQos()).isEqualTo(5);
        assertThat(pdCom.getPort()).isEqualTo(17224);
        assertThat(pdCom.getValidityBehavior()).isEqualTo("zero");

        MdComParameter mdCom = bi.getMdComParameter();
        assertThat(mdCom).isNotNull();
        assertThat(mdCom.getConfirmTimeout()).isEqualTo(1000000);
        assertThat(mdCom.getReplyTimeout()).isEqualTo(5000000);
        assertThat(mdCom.getConnectTimeout()).isEqualTo(60000000);
        assertThat(mdCom.getTtl()).isEqualTo(64);
        assertThat(mdCom.getQos()).isEqualTo(3);
        assertThat(mdCom.getRetries()).isEqualTo(2);
        assertThat(mdCom.getProtocol()).isEqualTo("UDP");
        assertThat(mdCom.getUdpPort()).isEqualTo(17225);
        assertThat(mdCom.getTcpPort()).isEqualTo(17225);

        // bus-interface without trdp-process, pd-com-parameter, md-com-parameter elements
        BusInterface bi2 = config.getBusInterfaces().get(1);
        assertThat(bi2.getNetworkId()).isEqualTo(2);
        assertThat(bi2.getName()).isEqualTo("eth1");
        assertThat(bi2.getTrdpProcess()).isNotNull();
        assertThat(bi2.getTrdpProcess().getCycleTime()).isEqualTo(10000);
        assertThat(bi2.getTrdpProcess().getPriority()).isEqualTo(64);
        assertThat(bi2.getPdComParameter()).isNotNull();
        assertThat(bi2.getPdComParameter().getPort()).isEqualTo(17224);
        assertThat(bi2.getPdComParameter().getTimeoutValue()).isEqualTo(100000);
        assertThat(bi2.getMdComParameter()).isNotNull();
        assertThat(bi2.getMdComParameter().getUdpPort()).isEqualTo(17225);
        assertThat(bi2.getMdComParameter().getReplyTimeout()).isEqualTo(5000000);
    }

    // --- Full parse verification ---

    @Test
    void fullConfigDeviceAttributes() throws TrdpConfigException {
        DeviceConfig config = loadFull();
        assertThat(config.getHostName()).isEqualTo("CST_VCU");
        assertThat(config.getType()).isEqualTo("VCU_C");
        assertThat(config.getLeaderName()).isEqualTo("CST_lead");
    }

    @Test
    void fullConfigDeviceConfiguration() throws TrdpConfigException {
        DeviceConfig config = loadFull();
        DeviceConfiguration dc = config.getDeviceConfiguration();
        assertThat(dc).isNotNull();
        assertThat(dc.getMemorySize()).isEqualTo(8388608);
        assertThat(dc.getMemBlocks()).hasSize(2);
        assertThat(dc.getMemBlocks().get(0).getSize()).isEqualTo(128);
        assertThat(dc.getMemBlocks().get(0).getPreallocate()).isEqualTo(10);
        assertThat(dc.getMemBlocks().get(1).getSize()).isEqualTo(1480);
    }

    @Test
    void fullConfigDebug() throws TrdpConfigException {
        DeviceConfig config = loadFull();
        DebugConfig debug = config.getDebug();
        assertThat(debug).isNotNull();
        assertThat(debug.getFileName()).isEqualTo("/tmp/trdp.log");
        assertThat(debug.getFileSize()).isEqualTo(131072);
        assertThat(debug.getLevel()).isEqualTo("W");
        assertThat(debug.getInfo()).isEqualTo("AFC");
    }

    @Test
    void fullConfigBusInterfaces() throws TrdpConfigException {
        DeviceConfig config = loadFull();
        assertThat(config.getBusInterfaces()).hasSize(2);

        BusInterface bi1 = config.getBusInterfaces().get(0);
        assertThat(bi1.getNetworkId()).isEqualTo(1);
        assertThat(bi1.getName()).isEqualTo("eth0");
        assertThat(bi1.getHostIp()).isEqualTo("127.0.0.1");
        assertThat(bi1.getLeaderIp()).isEqualTo("10.0.1.1");

        // trdp-process
        TrdpProcessConfig tp = bi1.getTrdpProcess();
        assertThat(tp.getCycleTime()).isEqualTo(5000);
        assertThat(tp.isBlocking()).isTrue();
        assertThat(tp.isTrafficShaping()).isTrue();
        assertThat(tp.getPriority()).isEqualTo(128);

        // pd-com-parameter
        PdComParameter pdCom = bi1.getPdComParameter();
        assertThat(pdCom.getTimeoutValue()).isEqualTo(200000);
        assertThat(pdCom.getValidityBehavior()).isEqualTo("keep");
        assertThat(pdCom.getTtl()).isEqualTo(32);
        assertThat(pdCom.getQos()).isEqualTo(3);
        assertThat(pdCom.isMarshall()).isTrue();
        assertThat(pdCom.getCallback()).isEqualTo("always");
        assertThat(pdCom.getPort()).isEqualTo(17300);

        // md-com-parameter
        MdComParameter mdCom = bi1.getMdComParameter();
        assertThat(mdCom.getConfirmTimeout()).isEqualTo(2000000);
        assertThat(mdCom.getReplyTimeout()).isEqualTo(3000000);
        assertThat(mdCom.getConnectTimeout()).isEqualTo(30000000);
        assertThat(mdCom.getTtl()).isEqualTo(32);
        assertThat(mdCom.getQos()).isEqualTo(5);
        assertThat(mdCom.getRetries()).isEqualTo(1);
        assertThat(mdCom.getProtocol()).isEqualTo("TCP");
        assertThat(mdCom.isMarshall()).isTrue();
        assertThat(mdCom.getCallback()).isEqualTo("on");
        assertThat(mdCom.getUdpPort()).isEqualTo(17400);
        assertThat(mdCom.getTcpPort()).isEqualTo(17401);
        assertThat(mdCom.getNumSessions()).isEqualTo(500);
    }

    @Test
    void fullConfigTelegrams() throws TrdpConfigException {
        DeviceConfig config = loadFull();
        BusInterface bi1 = config.getBusInterfaces().get(0);
        assertThat(bi1.getTelegrams()).hasSize(2);

        // First telegram (PD)
        TelegramConfig t1 = bi1.getTelegrams().get(0);
        assertThat(t1.getComId()).isEqualTo(1000);
        assertThat(t1.getDataSetId()).isEqualTo(1001L);
        assertThat(t1.getComParameterId()).isEqualTo(1L);
        assertThat(t1.getName()).isEqualTo("pdTest");
        assertThat(t1.getType()).isEqualTo("source");
        assertThat(t1.isCreate()).isTrue();

        // pd-parameter
        PdParameter pdParam = t1.getPdParameter();
        assertThat(pdParam).isNotNull();
        assertThat(pdParam.getCycle()).isEqualTo(50000);
        assertThat(pdParam.getTimeout()).isEqualTo(150000);
        assertThat(pdParam.getValidityBehavior()).isEqualTo("keep");
        assertThat(pdParam.getRedundant()).isEqualTo(1);
        assertThat(pdParam.isMarshall()).isTrue();
        assertThat(pdParam.getCallback()).isEqualTo("always");

        // source with sdt-parameter
        assertThat(t1.getSources()).hasSize(1);
        SourceConfig src = t1.getSources().get(0);
        assertThat(src.getId()).isEqualTo(1);
        assertThat(src.getUri1()).isEqualTo("10.0.1.100");
        assertThat(src.getUri2()).isEqualTo("10.0.1.101");
        assertThat(src.getName()).isEqualTo("src1");
        SdtParameter sdt = src.getSdtParameter();
        assertThat(sdt).isNotNull();
        assertThat(sdt.getSmi1()).isEqualTo(100);
        assertThat(sdt.getSmi2()).isEqualTo(200);
        assertThat(sdt.getUdv()).isEqualTo(256);
        assertThat(sdt.getRxPeriod()).isEqualTo(50);
        assertThat(sdt.getTxPeriod()).isEqualTo(50);
        assertThat(sdt.getNRxsafe()).isEqualTo(5);
        assertThat(sdt.getNGuard()).isEqualTo(200);
        assertThat(sdt.getCmThr()).isEqualTo(50);
        assertThat(sdt.getLmiMax()).isEqualTo(60);

        // destination with sdtv4-parameter
        assertThat(t1.getDestinations()).hasSize(1);
        DestinationConfig dst = t1.getDestinations().get(0);
        assertThat(dst.getId()).isEqualTo(2);
        assertThat(dst.getUri()).isEqualTo("239.255.1.1");
        assertThat(dst.getName()).isEqualTo("dst1");
        Sdtv4Parameter sdtv4 = dst.getSdtv4Parameter();
        assertThat(sdtv4).isNotNull();
        assertThat(sdtv4.getSmi1()).isEqualTo(300);
        assertThat(sdtv4.getUdvMain()).isEqualTo(1);

        // Second telegram (MD)
        TelegramConfig t2 = bi1.getTelegrams().get(1);
        assertThat(t2.getComId()).isEqualTo(2000);
        assertThat(t2.getName()).isEqualTo("mdTest");
        assertThat(t2.getType()).isEqualTo("sink");
        assertThat(t2.isCreate()).isFalse();
        MdParameter mdParam = t2.getMdParameter();
        assertThat(mdParam).isNotNull();
        assertThat(mdParam.getConfirmTimeout()).isEqualTo(3000000);
        assertThat(mdParam.getReplyTimeout()).isEqualTo(6000000);
        assertThat(mdParam.isMarshall()).isTrue();
        assertThat(mdParam.getProtocol()).isEqualTo("TCP");
    }

    @Test
    void fullConfigSdtv4SrvInstParameters() throws TrdpConfigException {
        DeviceConfig config = loadFull();
        BusInterface bi2 = config.getBusInterfaces().get(1);
        TelegramConfig t3 = bi2.getTelegrams().get(0);
        assertThat(t3.getComId()).isEqualTo(3000);
        assertThat(t3.getSdtv4SrvInstParameters()).hasSize(2);
        assertThat(t3.getSdtv4SrvInstParameters().get(0).getInstanceId()).isEqualTo(1);
        assertThat(t3.getSdtv4SrvInstParameters().get(0).getSmi1()).isEqualTo(500);
        assertThat(t3.getSdtv4SrvInstParameters().get(1).getInstanceId()).isEqualTo(2);
    }

    @Test
    void fullConfigMappedDevices() throws TrdpConfigException {
        DeviceConfig config = loadFull();
        assertThat(config.getMappedDevices()).hasSize(1);
        MappedDevice md = config.getMappedDevices().get(0);
        assertThat(md.getHostName()).isEqualTo("remoteA");
        assertThat(md.getLeaderName()).isEqualTo("remoteLead");
        assertThat(md.getMappedBusInterfaces()).hasSize(1);

        MappedBusInterface mbi = md.getMappedBusInterfaces().get(0);
        assertThat(mbi.getName()).isEqualTo("eth0");
        assertThat(mbi.getHostIp()).isEqualTo("10.0.2.50");
        assertThat(mbi.getMappedTelegrams()).hasSize(1);

        MappedTelegram mt = mbi.getMappedTelegrams().get(0);
        assertThat(mt.getComId()).isEqualTo(1000);
        assertThat(mt.getName()).isEqualTo("pdMapped");
        assertThat(mt.getMappedPdParameter()).isNotNull();
        assertThat(mt.getMappedPdParameter().getOffsetAddress()).isEqualTo(128);

        assertThat(mt.getMappedSources()).hasSize(1);
        MappedSource ms = mt.getMappedSources().get(0);
        assertThat(ms.getId()).isEqualTo(1);
        assertThat(ms.getUri1()).isEqualTo("10.0.2.50");
        assertThat(ms.getUri2()).isEqualTo("10.0.2.51");
        assertThat(ms.getMappedSdtParameter()).isNotNull();
        assertThat(ms.getMappedSdtParameter().getSmi1()).isEqualTo(400);
        assertThat(ms.getMappedSdtParameter().getSmi2()).isEqualTo(401);

        assertThat(mt.getMappedDestinations()).hasSize(1);
        MappedDestination mdst = mt.getMappedDestinations().get(0);
        assertThat(mdst.getId()).isEqualTo(2);
        assertThat(mdst.getUri()).isEqualTo("239.255.2.1");
        assertThat(mdst.getMappedSdtParameter().getSmi1()).isEqualTo(500);
        assertThat(mdst.getMappedSdtParameter().getSmi2()).isEqualTo(0);
    }

    @Test
    void fullConfigDataSets() throws TrdpConfigException {
        DeviceConfig config = loadFull();
        assertThat(config.getDataSets()).hasSize(2);

        DataSetDefinition ds1 = config.getDataSets().get(0);
        assertThat(ds1.getId()).isEqualTo(1001);
        assertThat(ds1.getName()).isEqualTo("StatusDataSet");
        assertThat(ds1.getElements()).hasSize(3);

        DataSetElement e1 = ds1.getElements().get(0);
        assertThat(e1.getType()).isEqualTo("UINT32");
        assertThat(e1.getName()).isEqualTo("speed");
        assertThat(e1.getArraySize()).isEqualTo(1);
        assertThat(e1.getUnit()).isEqualTo("km/h");
        assertThat(e1.getScale()).isEqualTo(0.1f);
        assertThat(e1.getOffset()).isEqualTo(0);

        DataSetElement e2 = ds1.getElements().get(1);
        assertThat(e2.getType()).isEqualTo("INT16");
        assertThat(e2.getArraySize()).isEqualTo(3);

        DataSetElement e3 = ds1.getElements().get(2);
        assertThat(e3.getType()).isEqualTo("BOOL8");
        assertThat(e3.getArraySize()).isEqualTo(1); // default

        // Nested dataset reference
        DataSetDefinition ds2 = config.getDataSets().get(1);
        assertThat(ds2.getId()).isEqualTo(1002);
        DataSetElement nested = ds2.getElements().get(1);
        assertThat(nested.getType()).isEqualTo("1001");
        assertThat(nested.getArraySize()).isEqualTo(2);
    }

    @Test
    void fullConfigComParameters() throws TrdpConfigException {
        DeviceConfig config = loadFull();
        assertThat(config.getComParameters()).hasSize(2);

        ComParameter cp1 = config.getComParameters().get(0);
        assertThat(cp1.getId()).isEqualTo(1);
        assertThat(cp1.getQos()).isEqualTo(5);
        assertThat(cp1.getTtl()).isEqualTo(32);
        assertThat(cp1.getRetries()).isEqualTo(3);

        ComParameter cp2 = config.getComParameters().get(1);
        assertThat(cp2.getId()).isEqualTo(2);
        assertThat(cp2.getQos()).isEqualTo(3);
        assertThat(cp2.getTtl()).isEqualTo(64); // default
        assertThat(cp2.getRetries()).isEqualTo(2); // default
    }

    @Test
    void fullConfigServices() throws TrdpConfigException {
        DeviceConfig config = loadFull();
        assertThat(config.getServices()).hasSize(1);

        ServiceDefinition svc = config.getServices().get(0);
        assertThat(svc.getName()).isEqualTo("DiagService");
        assertThat(svc.getId()).isEqualTo(100);
        assertThat(svc.getTtl()).isEqualTo(30000);
        assertThat(svc.isDummyService()).isFalse();

        assertThat(svc.getEvents()).hasSize(1);
        ServiceEvent evt = svc.getEvents().get(0);
        assertThat(evt.getId()).isEqualTo(1);
        assertThat(evt.getComId()).isEqualTo(5000);
        assertThat(evt.getType()).isEqualTo("MD");
        assertThat(evt.getName()).isEqualTo("diagEvent");

        assertThat(svc.getFields()).hasSize(1);
        assertThat(svc.getFields().get(0).getComId()).isEqualTo(5001);

        assertThat(svc.getMethods()).hasSize(1);
        ServiceMethod method = svc.getMethods().get(0);
        assertThat(method.getComId()).isEqualTo(5002);
        assertThat(method.getReplyComId()).isEqualTo(5003);
        assertThat(method.isConfirm()).isTrue();

        assertThat(svc.getServiceDevices()).hasSize(1);
        ServiceDevice sd = svc.getServiceDevices().get(0);
        assertThat(sd.getSrcUri()).isEqualTo("10.0.1.100");
        assertThat(sd.getDstUri()).isEqualTo("10.0.1.200");
        assertThat(sd.getRedUri()).isEqualTo("10.0.1.201");
        assertThat(sd.getInstances()).hasSize(2);

        assertThat(svc.getTelegramRefs()).hasSize(1);
        TelegramRef tr = svc.getTelegramRefs().get(0);
        assertThat(tr.getComId()).isEqualTo(5000);
        assertThat(tr.getSrcId()).isEqualTo(1);
        assertThat(tr.getDstId()).isEqualTo(2);
    }

    @Test
    void emptyListsAreNonNull() throws TrdpConfigException {
        DeviceConfig config = TrdpConfig.load(Path.of("src/test/resources/trdp-config-minimal.xml"));
        assertThat(config.getBusInterfaces()).isNotNull().isEmpty();
        assertThat(config.getDataSets()).isNotNull().isEmpty();
        assertThat(config.getComParameters()).isNotNull().isEmpty();
        assertThat(config.getServices()).isNotNull().isEmpty();
        assertThat(config.getMappedDevices()).isNotNull().isEmpty();
    }

    @Test
    void toStringContainsHostName() throws TrdpConfigException {
        DeviceConfig config = loadFull();
        assertThat(config.toString()).contains("CST_VCU");
    }

    private DeviceConfig loadFull() throws TrdpConfigException {
        return TrdpConfig.load(Path.of("src/test/resources/trdp-config-full.xml"));
    }
}
