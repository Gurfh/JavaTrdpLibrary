package com.trdp.md;

import com.trdp.protocol.TrdpConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.assertj.core.api.Assertions.*;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

class MdRequesterTest {
    
    private MdRequester requester;
    
    @AfterEach
    void tearDown() {
        if (requester != null) {
            requester.close();
        }
    }
    
    @Test
    void testCreateRequester() throws IOException {
        requester = new MdRequester(17225);
        assertThat(requester).isNotNull();
    }
    
    @Test
    void testSendRequest() throws IOException {
        requester = new MdRequester(0);
        
        byte[] requestData = "Request".getBytes();
        CompletableFuture<MdReply> future = requester.sendRequest(2000, requestData, "127.0.0.1", 17226);
        
        assertThat(future).isNotNull();
        assertThat(future).isNotCompleted();
    }
    
    @Test
    void testSendOversizedRequest() throws IOException {
        requester = new MdRequester(0);
        
        byte[] oversizedData = new byte[TrdpConstants.TRDP_MAX_MD_DATA_SIZE + 1];
        CompletableFuture<MdReply> future = requester.sendRequest(2000, oversizedData, "127.0.0.1", 17226);
        
        assertThat(future).isCompletedExceptionally();
    }
}
