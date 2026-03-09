package com.trdp.network;

import com.trdp.protocol.TrdpConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TcpTransport implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(TcpTransport.class);

    private final Socket socket;
    private DataInputStream dataIn;

    public TcpTransport(String host, int port) throws IOException {
        this(host, port, null, 0);
    }

    /**
     * Creates a TCP transport with custom socket options.
     *
     * @param host         the remote host to connect to
     * @param port         the remote port to connect to
     * @param bindAddress  the local address to bind to, or {@code null} for any local address
     * @param trafficClass the IP traffic class byte (use {@link UdpTransport#qosToTrafficClass(int)}
     *                     to convert from QoS), or 0 to leave at OS default
     * @throws IOException if connection fails
     */
    public TcpTransport(String host, int port, InetAddress bindAddress, int trafficClass) throws IOException {
        if (bindAddress != null) {
            this.socket = new Socket();
            this.socket.bind(new InetSocketAddress(bindAddress, 0));
            this.socket.connect(new InetSocketAddress(host, port));
        } else {
            this.socket = new Socket(host, port);
        }
        if (trafficClass != 0) {
            this.socket.setTrafficClass(trafficClass);
        }
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

    /**
     * Reads a complete TRDP MD frame from the TCP stream.
     * Reads the fixed-size MD header first to determine payload length,
     * then reads the exact payload.
     *
     * @param buffer Buffer to receive the frame (must be at least
     *               {@link TrdpConstants#TRDP_MD_HEADER_SIZE} +
     *               {@link TrdpConstants#TRDP_MAX_MD_DATA_SIZE} bytes).
     * @param timeoutMs Socket timeout in milliseconds.
     * @return Total bytes read (header + payload), 0 on timeout, -1 on EOF.
     */
    public int receive(byte[] buffer, int timeoutMs) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("TCP socket not connected.");
        }
        socket.setSoTimeout(timeoutMs);

        if (dataIn == null) {
            dataIn = new DataInputStream(socket.getInputStream());
        }

        try {
            // 1. Read MD header (fixed size) to determine payload length
            dataIn.readFully(buffer, 0, TrdpConstants.TRDP_MD_HEADER_SIZE);

            // 2. Extract datasetLength (Big Endian int at header offset 20)
            int datasetLength = ByteBuffer.wrap(buffer, 20, 4)
                .order(ByteOrder.BIG_ENDIAN).getInt();

            int totalLength = TrdpConstants.TRDP_MD_HEADER_SIZE;

            // 3. Read payload if present
            if (datasetLength > 0) {
                if (datasetLength > TrdpConstants.TRDP_MAX_MD_DATA_SIZE) {
                    throw new IOException("Oversized payload declared: " + datasetLength);
                }
                dataIn.readFully(buffer, TrdpConstants.TRDP_MD_HEADER_SIZE, datasetLength);
                totalLength += datasetLength;

                // Consume 4-byte alignment padding
                int padding = (4 - (datasetLength % 4)) % 4;
                if (padding > 0) {
                    dataIn.readFully(new byte[padding]);
                }
            }

            logger.trace("Received {} bytes (header + {} payload)", totalLength, datasetLength);
            return totalLength;

        } catch (SocketTimeoutException e) {
            return 0;
        } catch (EOFException e) {
            return -1;
        }
    }

    public InputStream getInputStream() throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("TCP socket not connected.");
        }
        return socket.getInputStream();
    }

    public void setSoTimeout(int timeoutMs) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("TCP socket not connected.");
        }
        socket.setSoTimeout(timeoutMs);
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
