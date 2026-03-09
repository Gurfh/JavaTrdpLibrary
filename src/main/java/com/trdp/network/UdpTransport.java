package com.trdp.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;

public class UdpTransport implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(UdpTransport.class);

    private final MulticastSocket socket;
    private final int port;

    /**
     * Converts a QoS value (IP Precedence 0..7) to the traffic class byte
     * for {@link java.net.DatagramSocket#setTrafficClass(int)}.
     * <p>
     * IP Precedence occupies bits 7-5 of the TOS/DSCP byte.
     *
     * @param qos QoS value (0..7)
     * @return traffic class byte with IP Precedence in bits 7-5
     */
    public static int qosToTrafficClass(int qos) {
        return (qos & 0x07) << 5;
    }

    public UdpTransport() throws IOException {
        this(0);
    }

    public UdpTransport(int port) throws IOException {
        this(port, null, 64, 0);
    }

    /**
     * Creates a UDP transport with custom socket options.
     *
     * @param port         the UDP port to bind to (0 for ephemeral)
     * @param bindAddress  the local address to bind to, or {@code null} for wildcard
     * @param ttl          the IP time-to-live for outgoing packets
     * @param trafficClass the IP traffic class byte (use {@link #qosToTrafficClass(int)} to convert from QoS)
     * @throws IOException if socket creation fails
     */
    public UdpTransport(int port, InetAddress bindAddress, int ttl, int trafficClass) throws IOException {
        this.port = port;
        if (bindAddress != null) {
            this.socket = new MulticastSocket(new InetSocketAddress(bindAddress, port));
        } else {
            this.socket = new MulticastSocket(port);
        }
        this.socket.setReuseAddress(true);
        this.socket.setTimeToLive(ttl);
        if (trafficClass != 0) {
            this.socket.setTrafficClass(trafficClass);
        }

        if (port > 0) {
            logger.debug("UDP Transport created on port {} (ttl={}, tc=0x{}))",
                    port, ttl, Integer.toHexString(trafficClass));
        } else {
            logger.debug("UDP Transport created on ephemeral port {} (ttl={}, tc=0x{})",
                    socket.getLocalPort(), ttl, Integer.toHexString(trafficClass));
        }
    }
    
    public void joinMulticastGroup(InetAddress group) throws IOException {
        NetworkInterface networkInterface = NetworkInterface.getByInetAddress(
            InetAddress.getLocalHost());

        if (networkInterface == null) {
            networkInterface = NetworkInterface.getNetworkInterfaces().nextElement();
        }

        logger.debug("Auto-selected network interface: {}", networkInterface.getName());
        joinMulticastGroup(group, networkInterface);
    }

    public void joinMulticastGroup(InetAddress group, NetworkInterface networkInterface) throws IOException {
        socket.joinGroup(new InetSocketAddress(group, port), networkInterface);
        logger.debug("Joined multicast group {} on port {} via {}", group.getHostAddress(), port, networkInterface.getName());
    }
    
    public void send(byte[] data, InetAddress address, int port) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        socket.send(packet);
        logger.trace("Sent {} bytes to {}:{}", data.length, address.getHostAddress(), port);
    }
    
    public int receive(byte[] buffer, int timeoutMs) throws IOException {
        socket.setSoTimeout(timeoutMs);
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        
        try {
            socket.receive(packet);
            logger.trace("Received {} bytes from {}:{}", 
                       packet.getLength(), 
                       packet.getAddress().getHostAddress(), 
                       packet.getPort());
            return packet.getLength();
        } catch (SocketTimeoutException e) {
            return 0;
        }
    }
    
    public ReceivedPacket receiveWithSource(byte[] buffer, int timeoutMs) throws IOException {
        socket.setSoTimeout(timeoutMs);
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        
        try {
            socket.receive(packet);
            logger.trace("Received {} bytes from {}:{}", 
                       packet.getLength(), 
                       packet.getAddress().getHostAddress(), 
                       packet.getPort());
            return new ReceivedPacket(buffer, packet.getLength(), 
                                    packet.getAddress(), packet.getPort());
        } catch (SocketTimeoutException e) {
            return null;
        }
    }
    
    public int getLocalPort() {
        return socket.getLocalPort();
    }

    /**
     * Returns the IP time-to-live value configured on this socket.
     *
     * @throws IOException if the socket option cannot be read
     */
    public int getTimeToLive() throws IOException {
        return socket.getTimeToLive();
    }

    /**
     * Returns the IP traffic class byte configured on this socket.
     *
     * @throws java.net.SocketException if the socket option cannot be read
     */
    public int getTrafficClass() throws java.net.SocketException {
        return socket.getTrafficClass();
    }
    
    @Override
    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            logger.debug("UDP Transport closed");
        }
    }
}
