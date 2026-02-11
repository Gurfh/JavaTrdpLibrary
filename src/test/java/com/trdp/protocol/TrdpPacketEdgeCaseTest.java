package com.trdp.protocol;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.*;

class TrdpPacketEdgeCaseTest {

    @Test
    void testDecodeTooShortForAnyHeader() {
        byte[] tooShort = new byte[10];
        assertThatThrownBy(() -> TrdpPacket.decode(tooShort))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("too short");
    }

    @Test
    void testDecodeInvalidMessageTypeCode() {
        // Build a buffer with a valid-looking PD header size but bogus message type
        byte[] data = new byte[TrdpConstants.TRDP_PD_HEADER_SIZE];
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(0);          // sequenceCounter
        buf.putShort((short) 0x0100); // protocolVersion
        buf.putShort((short) 0xFFFF); // invalid message type

        assertThatThrownBy(() -> TrdpPacket.decode(data))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown TRDP message type");
    }

    @Test
    void testDecodeMdTypeWithTooShortData() {
        // Create data that has an MD message type but is only PD header size
        byte[] data = new byte[TrdpConstants.TRDP_PD_HEADER_SIZE];
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(0);          // sequenceCounter
        buf.putShort((short) 0x0100); // protocolVersion
        buf.putShort((short) TrdpMessageType.MD_REQUEST.getCode());

        assertThatThrownBy(() -> TrdpPacket.decode(data))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("too short for TRDP MD");
    }

    @Test
    void testDecodePayloadLengthExceedsData() {
        // Encode a valid PD header claiming a large payload, but provide no payload bytes
        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(0);
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(100);
        header.setDatasetLength(500);

        byte[] encoded = header.encode();
        // encoded is only 40 bytes (header), but claims 500 bytes of payload

        assertThatThrownBy(() -> TrdpPacket.decode(encoded))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Data length mismatch");
    }

    @Test
    void testDecodeCorruptedFcs() {
        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(1);
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(100);

        TrdpPacket packet = new TrdpPacket(header, "test".getBytes());
        byte[] encoded = packet.encode();

        // Corrupt a byte in the header (not FCS itself, so FCS won't match)
        encoded[8] ^= 0xFF;

        assertThatThrownBy(() -> TrdpPacket.decode(encoded))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("FCS mismatch");
    }

    @Test
    void testPayloadDefensiveCopy() {
        TrdpPdHeader header = new TrdpPdHeader();
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(100);

        byte[] original = {1, 2, 3, 4};
        TrdpPacket packet = new TrdpPacket(header, original);

        // Mutating original should not affect packet
        original[0] = 99;
        assertThat(packet.getPayload()[0]).isEqualTo((byte) 1);

        // Mutating getPayload() result should not affect packet
        byte[] payload = packet.getPayload();
        payload[0] = 88;
        assertThat(packet.getPayload()[0]).isEqualTo((byte) 1);
    }

    @Test
    void testPdHeaderFcsCorruption() {
        TrdpMdHeader header = new TrdpMdHeader();
        header.setSequenceCounter(5);
        header.setMessageType(TrdpMessageType.MD_REPLY);
        header.setComId(200);

        byte[] encoded = header.encode();
        // Corrupt a header byte
        encoded[10] ^= 0xFF;

        assertThatThrownBy(() -> TrdpMdHeader.decode(encoded))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("FCS mismatch");
    }

    @Test
    void testEncodeDecodeAllPdMessageTypes() {
        for (TrdpMessageType type : new TrdpMessageType[]{
                TrdpMessageType.PD, TrdpMessageType.PD_REQUEST,
                TrdpMessageType.PD_REPLY, TrdpMessageType.PD_ERROR}) {
            TrdpPdHeader header = new TrdpPdHeader();
            header.setSequenceCounter(1);
            header.setMessageType(type);
            header.setComId(100);

            TrdpPacket packet = new TrdpPacket(header, new byte[]{1, 2, 3});
            byte[] encoded = packet.encode();
            TrdpPacket decoded = TrdpPacket.decode(encoded);

            assertThat(decoded.getHeader().getMessageType()).isEqualTo(type);
            assertThat(decoded.getPayload()).containsExactly(1, 2, 3);
        }
    }

    @Test
    void testEncodeDecodeAllMdMessageTypes() {
        for (TrdpMessageType type : new TrdpMessageType[]{
                TrdpMessageType.MD_REQUEST, TrdpMessageType.MD_REPLY,
                TrdpMessageType.MD_CONFIRM, TrdpMessageType.MD_ERROR,
                TrdpMessageType.MD_NOTIFICATION, TrdpMessageType.MD_REPLY_CONFIRM}) {
            TrdpMdHeader header = new TrdpMdHeader();
            header.setSequenceCounter(1);
            header.setMessageType(type);
            header.setComId(200);

            TrdpPacket packet = new TrdpPacket(header, new byte[]{4, 5, 6});
            byte[] encoded = packet.encode();
            TrdpPacket decoded = TrdpPacket.decode(encoded);

            assertThat(decoded.getHeader().getMessageType()).isEqualTo(type);
            assertThat(decoded.getPayload()).containsExactly(4, 5, 6);
        }
    }
}
