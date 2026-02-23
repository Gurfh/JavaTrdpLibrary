package com.trdp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trdp.md.MdRequester;
import com.trdp.md.TransportProtocol;
import com.trdp.pd.PdPublisher;
import com.trdp.pd.PdRequester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Wire-format compliance test: verifies that packets produced by this library
 * are correctly recognised and dissected by Wireshark/tshark's built-in TRDP
 * protocol dissector (IEC 61375-2-3).
 *
 * <p>Requires {@code tshark} on the system PATH and permission to capture on
 * the loopback interface.
 */
class TrdpWiresharkIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PdPublisher pdPublisher;
    private PdRequester pdRequester;
    private MdRequester mdRequester;

    // ----- ports chosen to avoid conflicts with other ITs -----
    private static final int PD_PORT = 17224;
    private static final int MD_PORT = 17225;

    @BeforeAll
    static void checkTsharkAvailable() throws Exception {
        boolean available;
        try {
            Process p = new ProcessBuilder("tshark", "--version")
                    .redirectErrorStream(true).start();
            available = p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
            p.destroyForcibly();
        } catch (IOException e) {
            available = false;
        }
        assumeThat(available)
                .as("tshark must be installed and accessible")
                .isTrue();
    }

    @AfterEach
    void tearDown() {
        if (pdPublisher != null) pdPublisher.close();
        if (pdRequester != null) pdRequester.close();
        if (mdRequester != null) mdRequester.close();
    }

    // ==================== PD Push ====================

    @Test
    void tsharkDecodesPdPushPacket() throws Exception {
        int comId = 5000;
        int etbTopo = 0xABCD0001;
        int opTrnTopo = 0xDEAD0002;
        byte[] payload = "TRDP-PD-PUSH".getBytes(StandardCharsets.UTF_8);

        Process tshark = startTshark(PD_PORT, 1);
        Thread.sleep(1000); // let tshark settle on the interface

        pdPublisher = new PdPublisher(comId, "127.0.0.1", PD_PORT);
        pdPublisher.setTopologyCounters(etbTopo, opTrnTopo);
        pdPublisher.putDataImmediate(payload);

        List<JsonNode> packets = stopAndParse(tshark);

        assertThat(packets).as("tshark should capture at least 1 TRDP PD packet").isNotEmpty();

        JsonNode trdp = trdpLayer(packets.get(0));
        assertThat(trdp).as("tshark must recognise the packet as TRDP protocol").isNotNull();

        // Message type 0x5064 == "Pd" (Process Data push)
        assertField(trdp, "trdp.msgtype", "0x5064");
        assertFieldDecimal(trdp, "trdp.comid", comId);
        assertFieldDecimal(trdp, "trdp.seq", 0);
        // Note: trdp.ver JSON value is always 0 due to Wireshark dissector passing
        // literal 0 to proto_tree_add_uint_format_value (display shows "1.0" correctly).
        assertFieldPresent(trdp, "trdp.ver");
        assertField(trdp, "trdp.etb_topo", String.format("0x%08x", etbTopo));
        assertField(trdp, "trdp.oper_topo", String.format("0x%08x", opTrnTopo));
        assertFieldDecimal(trdp, "trdp.len", payload.length);
    }

    // ==================== PD Request ====================

    @Test
    void tsharkDecodesPdRequestPacket() throws Exception {
        int comId = 6000;
        int replyComId = 6001;
        String replyIp = "127.0.0.1";
        int etbTopo = 0x00001111;
        int opTrnTopo = 0x00002222;

        Process tshark = startTshark(PD_PORT, 1);
        Thread.sleep(1000);

        pdRequester = new PdRequester(0);
        pdRequester.setTopologyCounters(etbTopo, opTrnTopo);
        pdRequester.request(comId, "127.0.0.1", PD_PORT, replyComId, replyIp);

        List<JsonNode> packets = stopAndParse(tshark);

        assertThat(packets).as("tshark should capture at least 1 TRDP PD Request packet").isNotEmpty();

        JsonNode trdp = trdpLayer(packets.get(0));
        assertThat(trdp).as("tshark must recognise the packet as TRDP protocol").isNotNull();

        // Message type 0x5072 == "Pr" (PD Request)
        assertField(trdp, "trdp.msgtype", "0x5072");
        assertFieldDecimal(trdp, "trdp.comid", comId);
        assertFieldDecimal(trdp, "trdp.reply_comid", replyComId);
        assertField(trdp, "trdp.reply_ipaddr", replyIp);
        assertField(trdp, "trdp.etb_topo", String.format("0x%08x", etbTopo));
        assertField(trdp, "trdp.oper_topo", String.format("0x%08x", opTrnTopo));
    }

    // ==================== MD Request ====================

    @Test
    void tsharkDecodesMdRequestPacket() throws Exception {
        int comId = 7000;
        String sourceUri = "srcApp";
        String destinationUri = "dstApp";
        byte[] payload = "TRDP-MD-REQ".getBytes(StandardCharsets.UTF_8);
        int etbTopo = 0x00003333;
        int opTrnTopo = 0x00004444;

        Process tshark = startTshark(MD_PORT, 1);
        Thread.sleep(1000);

        mdRequester = new MdRequester(0);
        mdRequester.setTopologyCounters(etbTopo, opTrnTopo);

        // Send MD request — nobody is listening, the future will timeout, but the
        // packet still goes on the wire and tshark will capture it.
        mdRequester.sendRequest(comId, payload, "127.0.0.1", MD_PORT,
                TransportProtocol.UDP, sourceUri, destinationUri);

        List<JsonNode> packets = stopAndParse(tshark);

        assertThat(packets).as("tshark should capture at least 1 TRDP MD packet").isNotEmpty();

        JsonNode trdp = trdpLayer(packets.get(0));
        assertThat(trdp).as("tshark must recognise the packet as TRDP protocol").isNotNull();

        // Message type 0x4d72 == "Mr" (MD Request)
        assertField(trdp, "trdp.msgtype", "0x4d72");
        assertFieldDecimal(trdp, "trdp.comid", comId);
        assertFieldPresent(trdp, "trdp.ver");
        assertField(trdp, "trdp.etb_topo", String.format("0x%08x", etbTopo));
        assertField(trdp, "trdp.oper_topo", String.format("0x%08x", opTrnTopo));
        assertFieldDecimal(trdp, "trdp.len", payload.length);
        // Note: trdp.reply_timeout JSON value is always 0 (same dissector quirk as
        // trdp.ver — formatted display is correct but stored tree value is literal 0).
        assertFieldPresent(trdp, "trdp.reply_timeout");

        // Session ID must be present (non-zero UUID)
        String sessionId = fieldValue(trdp, "trdp.session_id");
        assertThat(sessionId)
                .as("session_id should be present and non-empty")
                .isNotNull()
                .isNotEmpty();

        // URI fields
        assertThat(fieldValue(trdp, "trdp.source_uri")).isEqualTo(sourceUri);
        assertThat(fieldValue(trdp, "trdp.dest_uri")).isEqualTo(destinationUri);
    }

    // ==================== All-in-one capture ====================

    @Test
    void tsharkDecodesMultiplePacketTypes() throws Exception {
        // Capture all three packet types in a single tshark session
        Process tshark = startTshark(PD_PORT, MD_PORT, 3);
        Thread.sleep(1000);

        // 1. PD Push
        pdPublisher = new PdPublisher(1000, "127.0.0.1", PD_PORT);
        pdPublisher.putDataImmediate("multi-pd".getBytes(StandardCharsets.UTF_8));

        // 2. PD Request
        pdRequester = new PdRequester(0);
        pdRequester.request(2000, "127.0.0.1", PD_PORT, 2001, "127.0.0.1");

        // 3. MD Request
        mdRequester = new MdRequester(0);
        mdRequester.sendRequest(3000, "multi-md".getBytes(StandardCharsets.UTF_8),
                "127.0.0.1", MD_PORT, TransportProtocol.UDP, "src", "dst");

        List<JsonNode> packets = stopAndParse(tshark);

        assertThat(packets)
                .as("tshark should capture all 3 TRDP packets")
                .hasSizeGreaterThanOrEqualTo(3);

        // Verify each packet was decoded as TRDP
        for (JsonNode pkt : packets) {
            assertThat(trdpLayer(pkt))
                    .as("every captured packet should be recognised as TRDP")
                    .isNotNull();
        }

        // Verify we see all three message types
        List<String> msgTypes = packets.stream()
                .map(p -> fieldValue(trdpLayer(p), "trdp.msgtype"))
                .collect(Collectors.toList());
        assertThat(msgTypes).contains("0x5064"); // PD
        assertThat(msgTypes).contains("0x5072"); // PD Request
        assertThat(msgTypes).contains("0x4d72"); // MD Request
    }

    // ==================== FCS Validation ====================

    @Test
    void tsharkAcceptsFcsAsValid() throws Exception {
        // If tshark dissects the packet as TRDP (not just raw UDP), the FCS was
        // accepted — an invalid FCS causes the dissector to reject the frame.
        // We verify the fcs field is present and non-zero.

        Process tshark = startTshark(PD_PORT, 1);
        Thread.sleep(1000);

        pdPublisher = new PdPublisher(8000, "127.0.0.1", PD_PORT);
        pdPublisher.putDataImmediate("fcs-check".getBytes(StandardCharsets.UTF_8));

        List<JsonNode> packets = stopAndParse(tshark);

        assertThat(packets).isNotEmpty();
        JsonNode trdp = trdpLayer(packets.get(0));
        assertThat(trdp).isNotNull();

        String fcs = fieldValue(trdp, "trdp.fcs");
        assertThat(fcs)
                .as("Header FCS field should be present")
                .isNotNull()
                .isNotEmpty();
        // FCS must be non-zero for any real packet
        assertThat(Long.decode(fcs))
                .as("Header FCS should be non-zero")
                .isNotZero();
    }

    // ==================== Helpers ====================

    /**
     * Starts tshark capturing on loopback for a single port.
     */
    private Process startTshark(int port, int packetCount) throws IOException {
        return startTsharkWithFilter(
                String.format("udp port %d", port), packetCount);
    }

    /**
     * Starts tshark capturing on loopback for two ports.
     */
    private Process startTshark(int port1, int port2, int packetCount) throws IOException {
        return startTsharkWithFilter(
                String.format("udp port %d or udp port %d", port1, port2), packetCount);
    }

    private Process startTsharkWithFilter(String captureFilter, int packetCount) throws IOException {
        List<String> cmd = List.of(
                "tshark",
                "-i", "lo",
                "-f", captureFilter,
                "-T", "json",
                "-c", String.valueOf(packetCount),
                "-a", "duration:10",
                "-l"  // line-buffered output
        );
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        return pb.start();
    }

    /**
     * Waits for tshark to finish, reads its JSON output, and parses it.
     */
    private List<JsonNode> stopAndParse(Process tshark) throws Exception {
        // Give tshark a moment to process buffered packets
        boolean exited = tshark.waitFor(15, TimeUnit.SECONDS);
        if (!exited) {
            tshark.destroyForcibly();
            tshark.waitFor(5, TimeUnit.SECONDS);
        }

        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(tshark.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }

        // Read and discard stderr so it doesn't block
        try (BufferedReader err = new BufferedReader(
                new InputStreamReader(tshark.getErrorStream(), StandardCharsets.UTF_8))) {
            err.lines().forEach(line -> {}); // drain
        }

        if (output.isBlank()) {
            return List.of();
        }

        JsonNode root = MAPPER.readTree(output);
        List<JsonNode> packets = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode element : root) {
                packets.add(element);
            }
        }
        return packets;
    }

    /**
     * Extracts the {@code trdp} layer from a tshark JSON packet element.
     * tshark JSON structure: [{_source: {layers: {trdp: {...}}}}]
     */
    private JsonNode trdpLayer(JsonNode packet) {
        JsonNode layers = packet.path("_source").path("layers");
        if (layers.isMissingNode()) return null;
        JsonNode trdp = layers.path("trdp");
        return trdp.isMissingNode() ? null : trdp;
    }

    /**
     * Gets the first text value for a tshark field.
     * tshark JSON fields are arrays: {"trdp.comid": ["5000"]}
     */
    private String fieldValue(JsonNode trdpLayer, String fieldName) {
        if (trdpLayer == null) return null;
        JsonNode field = trdpLayer.path(fieldName);
        if (field.isMissingNode()) return null;
        if (field.isArray() && field.size() > 0) {
            return field.get(0).asText();
        }
        return field.asText();
    }

    private void assertFieldPresent(JsonNode trdp, String fieldName) {
        assertThat(fieldValue(trdp, fieldName))
                .as("tshark field %s should be present", fieldName)
                .isNotNull();
    }

    private void assertField(JsonNode trdp, String fieldName, String expectedValue) {
        String actual = fieldValue(trdp, fieldName);
        assertThat(actual)
                .as("tshark field %s", fieldName)
                .isEqualToIgnoringCase(expectedValue);
    }

    private void assertFieldDecimal(JsonNode trdp, String fieldName, long expectedValue) {
        String actual = fieldValue(trdp, fieldName);
        assertThat(actual)
                .as("tshark field %s should be present", fieldName)
                .isNotNull();
        assertThat(Long.parseLong(actual))
                .as("tshark field %s", fieldName)
                .isEqualTo(expectedValue);
    }
}
