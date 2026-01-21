package com.trdp.integration;

import com.trdp.md.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class MdCommunicationIT {
    
    private MdRequester requester;
    private MdReplier replier;
    
    @AfterEach
    void tearDown() {
        if (requester != null) requester.close();
        if (replier != null) replier.close();
    }
    
    @Test
    void testStandardRequestReply() throws Exception {
        int comId = 2000;
        int replierPort = 19100;
        int requesterPort = 17225;
        
        byte[] requestData = "Request".getBytes();
        byte[] replyData = "Reply".getBytes();
        
        // 1. Setup Replier
        replier = new MdReplier(replierPort, (request) -> {
            assertThat(request.getData()).isEqualTo(requestData);
            assertThat(request.getComId()).isEqualTo(comId);
            return new MdResponse(replyData); // Standard response
        });
        replier.start();
        
        Thread.sleep(500);
        
        // 2. Setup Requester
        requester = new MdRequester(requesterPort);
        
        CompletableFuture<MdReply> future = requester.sendRequest(
            comId, requestData, "127.0.0.1", replierPort);
        
        MdReply reply = future.get(5, TimeUnit.SECONDS);
        
        assertThat(reply).isNotNull();
        assertThat(reply.getData()).isEqualTo(replyData);
    }

    @Test
    void testConfirmedRequestReply() throws Exception {
        int comId = 2001;
        int replierPort = 19101;
        int requesterPort = 17226;
        
        byte[] requestData = "ConfirmedReq".getBytes();
        byte[] replyData = "ConfirmedRep".getBytes();
        
        replier = new MdReplier(replierPort, (request) -> {
            // Request confirmation (Mq)
            return new MdResponse(replyData, true);
        });
        replier.start();
        Thread.sleep(500);
        
        requester = new MdRequester(requesterPort);
        
        CompletableFuture<MdReply> future = requester.sendRequest(
            comId, requestData, "127.0.0.1", replierPort);
        
        MdReply reply = future.get(5, TimeUnit.SECONDS);
        
        assertThat(reply).isNotNull();
        assertThat(reply.getData()).isEqualTo(replyData);
        // Note: The internal sending of 'Mc' happens asynchronously in MdRequester.
        // We verify the future completes successfully, which implies the Mq was handled.
    }

    @Test
    void testTopologyMismatch() throws Exception {
        int comId = 2002;
        int replierPort = 19102;
        int requesterPort = 17227;
        
        // Replier expects ETB=10
        replier = new MdReplier(replierPort, (request) -> new MdResponse("ShouldNotHappen".getBytes()));
        replier.setTopologyCounters(10, 0); 
        replier.start();
        Thread.sleep(500);
        
        requester = new MdRequester(requesterPort);
        // Requester sends ETB=5 (Mismatch)
        requester.setTopologyCounters(5, 0);
        
        CompletableFuture<MdReply> future = requester.sendRequest(
            comId, "Data".getBytes(), "127.0.0.1", replierPort);
        
        // Expect timeout because Replier discards the packet
        assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
            .isInstanceOf(TimeoutException.class);
    }

    @Test
    void testTopologyMatch() throws Exception {
        int comId = 2003;
        int replierPort = 19103;
        int requesterPort = 17228;
        
        replier = new MdReplier(replierPort, (request) -> new MdResponse("Success".getBytes()));
        replier.setTopologyCounters(10, 20);
        replier.start();
        Thread.sleep(500);
        
        requester = new MdRequester(requesterPort);
        requester.setTopologyCounters(10, 20); // Match
        
        CompletableFuture<MdReply> future = requester.sendRequest(
            comId, "Data".getBytes(), "127.0.0.1", replierPort);
        
        MdReply reply = future.get(5, TimeUnit.SECONDS);
        assertThat(reply).isNotNull();
    }
    
    @Test
    void testUriPassing() throws Exception {
        int comId = 2004;
        int replierPort = 19104;
        
        String srcUri = "src.uri";
        String dstUri = "dst.uri";
        
        replier = new MdReplier(replierPort, (request) -> {
            assertThat(request.getSourceUri()).isEqualTo(srcUri);
            assertThat(request.getDestinationUri()).isEqualTo(dstUri);
            return new MdResponse("OK".getBytes());
        });
        replier.start();
        Thread.sleep(500);
        
        requester = new MdRequester(0);
        CompletableFuture<MdReply> future = requester.sendRequest(
            comId, "Data".getBytes(), "127.0.0.1", replierPort, 
            TransportProtocol.UDP, srcUri, dstUri);
            
        future.get(5, TimeUnit.SECONDS);
    }
}