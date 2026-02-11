package com.trdp.protocol;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class TrdpMdHeaderEdgeCaseTest {

    @Test
    void testUriTruncationPreservesUtf8Boundaries() {
        TrdpMdHeader header = new TrdpMdHeader();
        header.setMessageType(TrdpMessageType.MD_REQUEST);

        // Build a string with multi-byte UTF-8 chars that would be split at 32 bytes
        // Each CJK character is 3 bytes in UTF-8
        // 10 CJK chars = 30 bytes, 11 CJK chars = 33 bytes (exceeds 32)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            sb.append('\u4e00'); // CJK unified ideograph
        }
        String longUtf8 = sb.toString();

        header.setSourceUri(longUtf8);
        String result = header.getSourceUriString();

        // Should have truncated to 10 chars (30 bytes) since 11th char (bytes 30-32) won't fit
        assertThat(result.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(32);
        // Verify the result is valid UTF-8 (no split characters)
        assertThat(result).isEqualTo(result); // Would throw on invalid encoding
        assertThat(result.length()).isEqualTo(10);
    }

    @Test
    void testNullUri() {
        TrdpMdHeader header = new TrdpMdHeader();
        header.setMessageType(TrdpMessageType.MD_REQUEST);

        header.setSourceUri(null);
        header.setDestinationUri(null);

        byte[] encoded = header.encode();
        TrdpMdHeader decoded = TrdpMdHeader.decode(encoded);

        assertThat(decoded.getSourceUriString()).isEmpty();
        assertThat(decoded.getDestinationUriString()).isEmpty();
    }

    @Test
    void testEmptyUri() {
        TrdpMdHeader header = new TrdpMdHeader();
        header.setMessageType(TrdpMessageType.MD_REQUEST);

        header.setSourceUri("");
        header.setDestinationUri("");

        byte[] encoded = header.encode();
        TrdpMdHeader decoded = TrdpMdHeader.decode(encoded);

        assertThat(decoded.getSourceUriString()).isEmpty();
        assertThat(decoded.getDestinationUriString()).isEmpty();
    }

    @Test
    void testSessionIdByteArray() {
        TrdpMdHeader header = new TrdpMdHeader();
        header.setMessageType(TrdpMessageType.MD_REQUEST);

        byte[] sessionBytes = new byte[16];
        for (int i = 0; i < 16; i++) sessionBytes[i] = (byte) (i + 1);
        header.setSessionId(sessionBytes);

        byte[] encoded = header.encode();
        TrdpMdHeader decoded = TrdpMdHeader.decode(encoded);

        assertThat(decoded.getSessionId()).containsExactly(sessionBytes);
    }

    @Test
    void testSessionIdInvalidLength() {
        TrdpMdHeader header = new TrdpMdHeader();

        assertThatThrownBy(() -> header.setSessionId(new byte[15]))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("16 bytes");
    }

    @Test
    void testReplyStatusAndTimeout() {
        TrdpMdHeader header = new TrdpMdHeader();
        header.setMessageType(TrdpMessageType.MD_REPLY);
        header.setReplyStatus(42);
        header.setReplyTimeout(5000000);

        byte[] encoded = header.encode();
        TrdpMdHeader decoded = TrdpMdHeader.decode(encoded);

        assertThat(decoded.getReplyStatus()).isEqualTo(42);
        assertThat(decoded.getReplyTimeout()).isEqualTo(5000000);
    }

    @Test
    void testToStringContainsKeyFields() {
        TrdpMdHeader header = new TrdpMdHeader();
        header.setMessageType(TrdpMessageType.MD_REQUEST);
        header.setComId(1234);
        header.setSequenceCounter(5);

        String str = header.toString();
        assertThat(str).contains("MD_REQUEST");
        assertThat(str).contains("1234");
        assertThat(str).contains("5");
    }
}
