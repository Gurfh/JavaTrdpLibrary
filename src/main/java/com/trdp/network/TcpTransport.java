package com.trdp.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class TcpTransport implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(TcpTransport.class);

    private final Socket socket;

    public TcpTransport(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        logger.info("TCP Transport connected to {}:{}", host, port);
    }

    public void send(byte[] data) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("TCP socket not connected.");
        }
        OutputStream out = socket.getOutputStream();
        out.write(data);
        out.flush();
        logger.trace("Sent {} bytes", data.length);
    }

    public int receive(byte[] buffer, int timeoutMs) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("TCP socket not connected.");
        }
        socket.setSoTimeout(timeoutMs);
        InputStream in = socket.getInputStream();
        try {
            int bytesRead = in.read(buffer);
            if (bytesRead > 0) {
                logger.trace("Received {} bytes", bytesRead);
            }
            return bytesRead;
        } catch (SocketTimeoutException e) {
            return 0; // Timeout
        }
    }

    @Override
    public void close() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        logger.debug("TCP Transport closed");
    }

    public boolean isClosed() {
        return socket == null || socket.isClosed();
    }
}
