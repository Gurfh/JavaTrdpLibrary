package com.trdp.protocol;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class TrdpMdHeaderTest {

    @Test
    void testEncodeAndDecodeBasic() {
        TrdpMdHeader header = new TrdpMdHeader();
        header.setSequenceCounter(123);
        header.setProtocolVersion(0x0100);
        header.setMessageType(TrdpMessageType.MD_REQUEST);
        header.setComId(2000);
        header.setEtbTopoCnt(10);
        header.setOpTrnTopoCnt(20);
        header.setDatasetLength(50);
        // ReplyComId and ReplyIpAddress are NOT part of MD Header wire format
        // So we do not set/expect them here for MD
        
        byte[] encoded = header.encode();
        TrdpMdHeader decoded = TrdpMdHeader.decode(encoded);

        assertThat(decoded.getSequenceCounter()).isEqualTo(123);
        assertThat(decoded.getProtocolVersion()).isEqualTo(0x0100);
        assertThat(decoded.getMessageType()).isEqualTo(TrdpMessageType.MD_REQUEST);
        assertThat(decoded.getComId()).isEqualTo(2000);
        
        assertThat(decoded.getEtbTopoCnt()).isEqualTo(10);
        assertThat(decoded.getOpTrnTopoCnt()).isEqualTo(20);
        
        assertThat(decoded.getDatasetLength()).isEqualTo(50);
    }

    @Test
    void testSessionIdHandling() {
        TrdpMdHeader header = new TrdpMdHeader();
        UUID uuid = UUID.randomUUID();
        
        header.setSessionId(uuid);
        
        byte[] encoded = header.encode();
        TrdpMdHeader decoded = TrdpMdHeader.decode(encoded);
        
        assertThat(decoded.getSessionIdAsUuid()).isEqualTo(uuid);
        assertThat(decoded.getSessionId()).hasSize(16);
    }

    @Test
    void testUriHandling() {
        TrdpMdHeader header = new TrdpMdHeader();
        String source = "train.cst.app1";
        String dest = "train.vcu.manager";
        
        header.setSourceUri(source);
        header.setDestinationUri(dest);
        
        byte[] encoded = header.encode();
        TrdpMdHeader decoded = TrdpMdHeader.decode(encoded);
        
        assertThat(decoded.getSourceUriString()).isEqualTo(source);
        assertThat(decoded.getDestinationUriString()).isEqualTo(dest);
    }
    
    @Test
    void testUriTruncation() {
        TrdpMdHeader header = new TrdpMdHeader();
        String longUri = "this.is.a.very.long.uri.that.exceeds.thirty.two.bytes";
        
        header.setSourceUri(longUri);
        
        // Should truncate to 32 bytes
        assertThat(header.getSourceUriString().length()).isLessThanOrEqualTo(32);
        
        byte[] encoded = header.encode();
        TrdpMdHeader decoded = TrdpMdHeader.decode(encoded);
        
        assertThat(decoded.getSourceUriString()).startsWith("this.is.a.very.long");
    }

    @Test
    void testHeaderSize() {
        TrdpMdHeader header = new TrdpMdHeader();
        header.setMessageType(TrdpMessageType.MD_REQUEST);
        byte[] encoded = header.encode();
        assertThat(encoded.length).isEqualTo(TrdpConstants.TRDP_MD_HEADER_SIZE);
        assertThat(encoded.length).isEqualTo(116);
    }
}