package com.trdp.md;

import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpMdHeader;
import com.trdp.protocol.TrdpMessageType;
import com.trdp.protocol.TrdpPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class MdRequesterTcpConfirmTest {

    private MdRequester requester;
    private ServerSocket server;

    @AfterEach
    void tearDown() {
        if (requester != null) requester.close();
        if (server != null) {
            try { server.close(); } catch (Exception ignored) {}
        }
    }

    @Test
    void testTcpConfirmationSentOnSameConnection() throws Exception {
        server = new ServerSocket(0);
        server.setSoTimeout(5000);
        int port = server.getLocalPort();

        requester = new MdRequester(0);

        // Send a TCP request
        CompletableFuture<MdReply> future = requester.sendRequest(
            6000, "hello".getBytes(), "127.0.0.1", port, TransportProtocol.TCP);

        // Accept the connection and read the request
        Socket clientSocket = server.accept();
        clientSocket.setSoTimeout(5000);
        DataInputStream in = new DataInputStream(clientSocket.getInputStream());

        byte[] reqHeaderBytes = new byte[TrdpConstants.TRDP_MD_HEADER_SIZE];
        in.readFully(reqHeaderBytes);
        TrdpMdHeader reqHeader = TrdpMdHeader.decode(reqHeaderBytes);

        int reqPayloadLen = reqHeader.getDatasetLength();
        if (reqPayloadLen > 0) {
            in.readFully(new byte[reqPayloadLen]);
            int padding = (4 - (reqPayloadLen % 4)) % 4;
            if (padding > 0) in.readFully(new byte[padding]);
        }

        assertThat(reqHeader.getMessageType()).isEqualTo(TrdpMessageType.MD_REQUEST);

        // Send back MD_REPLY_CONFIRM (Mq) on the same socket
        TrdpMdHeader replyHeader = new TrdpMdHeader();
        replyHeader.setSequenceCounter(reqHeader.getSequenceCounter());
        replyHeader.setMessageType(TrdpMessageType.MD_REPLY_CONFIRM);
        replyHeader.setComId(reqHeader.getComId());
        replyHeader.setSessionId(reqHeader.getSessionId());
        replyHeader.setReplyStatus(0);

        byte[] replyData = "world".getBytes();
        TrdpPacket replyPacket = new TrdpPacket(replyHeader, replyData);
        byte[] encodedReply = replyPacket.encode();

        OutputStream out = clientSocket.getOutputStream();
        out.write(encodedReply);
        out.flush();

        // The requester should complete the future
        MdReply reply = future.get(5, TimeUnit.SECONDS);
        assertThat(reply).isNotNull();
        assertThat(new String(reply.getData())).isEqualTo("world");

        // Read the MD_CONFIRM (Mc) that the requester should have sent back
        byte[] mcHeaderBytes = new byte[TrdpConstants.TRDP_MD_HEADER_SIZE];
        in.readFully(mcHeaderBytes);
        TrdpMdHeader mcHeader = TrdpMdHeader.decode(mcHeaderBytes);

        // Consume any payload (should be empty) and padding
        int mcPayloadLen = mcHeader.getDatasetLength();
        if (mcPayloadLen > 0) {
            in.readFully(new byte[mcPayloadLen]);
            int padding = (4 - (mcPayloadLen % 4)) % 4;
            if (padding > 0) in.readFully(new byte[padding]);
        }

        assertThat(mcHeader.getMessageType()).isEqualTo(TrdpMessageType.MD_CONFIRM);
        assertThat(mcHeader.getSessionIdAsUuid()).isEqualTo(reqHeader.getSessionIdAsUuid());
        assertThat(mcHeader.getComId()).isEqualTo(reqHeader.getComId());

        clientSocket.close();
    }

    @Test
    void testTcpReplyWithoutConfirmDoesNotSendMc() throws Exception {
        server = new ServerSocket(0);
        server.setSoTimeout(5000);
        int port = server.getLocalPort();

        requester = new MdRequester(0);

        // Send a TCP request
        CompletableFuture<MdReply> future = requester.sendRequest(
            6001, "ping".getBytes(), "127.0.0.1", port, TransportProtocol.TCP);

        // Accept and read request
        Socket clientSocket = server.accept();
        clientSocket.setSoTimeout(2000);
        DataInputStream in = new DataInputStream(clientSocket.getInputStream());

        byte[] reqHeaderBytes = new byte[TrdpConstants.TRDP_MD_HEADER_SIZE];
        in.readFully(reqHeaderBytes);
        TrdpMdHeader reqHeader = TrdpMdHeader.decode(reqHeaderBytes);

        int reqPayloadLen = reqHeader.getDatasetLength();
        if (reqPayloadLen > 0) {
            in.readFully(new byte[reqPayloadLen]);
            int padding = (4 - (reqPayloadLen % 4)) % 4;
            if (padding > 0) in.readFully(new byte[padding]);
        }

        // Send back MD_REPLY (Mp, not Mq) — no confirmation expected
        TrdpMdHeader replyHeader = new TrdpMdHeader();
        replyHeader.setSequenceCounter(reqHeader.getSequenceCounter());
        replyHeader.setMessageType(TrdpMessageType.MD_REPLY);
        replyHeader.setComId(reqHeader.getComId());
        replyHeader.setSessionId(reqHeader.getSessionId());

        TrdpPacket replyPacket = new TrdpPacket(replyHeader, "pong".getBytes());
        OutputStream out = clientSocket.getOutputStream();
        out.write(replyPacket.encode());
        out.flush();

        // Future should complete
        MdReply reply = future.get(5, TimeUnit.SECONDS);
        assertThat(new String(reply.getData())).isEqualTo("pong");

        // No Mc should be sent — reading should timeout
        assertThatThrownBy(() -> {
            byte[] buf = new byte[TrdpConstants.TRDP_MD_HEADER_SIZE];
            in.readFully(buf);
        }).isInstanceOf(java.net.SocketTimeoutException.class);

        clientSocket.close();
    }
}
