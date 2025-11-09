package com.trdp.integration;

import com.trdp.md.MdReply;
import com.trdp.md.MdReplier;
import com.trdp.md.MdRequester;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

class MdCommunicationIT {
    
    private MdRequester requester;
    private MdReplier replier;
    
    @AfterEach
    void tearDown() {
        if (requester != null) {
            requester.close();
        }
        if (replier != null) {
            replier.close();
        }
    }
    
    @Test
    void testRequestReply() throws Exception {
        int comId = 2000;
        int replierPort = 19100;
        int requesterPort = 19101;
        
        byte[] requestData = "Hello TRDP MD".getBytes();
        byte[] replyData = "Reply from TRDP MD".getBytes();
        
        replier = new MdReplier(replierPort, (receivedComId, data) -> {
            assertThat(data).isEqualTo(requestData);
            return replyData;
        });
        replier.start();
        
        Thread.sleep(500);
        
        requester = new MdRequester(requesterPort);
        
        CompletableFuture<MdReply> future = requester.sendRequest(
            comId, requestData, "127.0.0.1", replierPort);
        
        MdReply reply = future.get(5, TimeUnit.SECONDS);
        
        assertThat(reply).isNotNull();
        assertThat(reply.getData()).isEqualTo(replyData);
    }
}
