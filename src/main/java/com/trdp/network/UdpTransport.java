package com.trdp.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link DatagramChannel}-based UDP transport with multicast group management
 * and configurable socket options (bind address, TTL, QoS).
 * <p>
 * Sets both multicast TTL ({@link StandardSocketOptions#IP_MULTICAST_TTL}) and
 * unicast TTL ({@code IP_TTL} via JNA {@code setsockopt} through
 * {@link NativeSocketOptions}). Unicast TTL requires JVM flag
 * {@code --add-opens java.base/sun.nio.ch=ALL-UNNAMED} for native fd extraction;
 * without it, unicast packets use the OS default TTL.
 */
public class UdpTransport implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(UdpTransport.class);

    private final DatagramChannel channel;
    private final DatagramSocket socket;
    private final int port;
    private final ConcurrentHashMap<InetAddress, MembershipKey> memberships = new ConcurrentHashMap<>();

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
     * <p>
     * Sets both {@code IP_MULTICAST_TTL} (via {@link StandardSocketOptions}) and
     * unicast {@code IP_TTL} (via JNA {@code setsockopt} through {@link NativeSocketOptions}).
     *
     * @param port         the UDP port to bind to (0 for ephemeral)
     * @param bindAddress  the local address to bind to, or {@code null} for wildcard
     * @param ttl          the IP time-to-live for both multicast and unicast outgoing packets
     * @param trafficClass the IP traffic class byte (use {@link #qosToTrafficClass(int)} to convert from QoS)
     * @throws IOException if socket creation fails
     */
    public UdpTransport(int port, InetAddress bindAddress, int ttl, int trafficClass) throws IOException {
        this.port = port;
        this.channel = DatagramChannel.open(StandardProtocolFamily.INET);
        this.socket = channel.socket();
        this.socket.setReuseAddress(true);

        if (bindAddress != null) {
            channel.bind(new InetSocketAddress(bindAddress, port));
        } else {
            channel.bind(new InetSocketAddress(port));
        }

        channel.setOption(StandardSocketOptions.IP_MULTICAST_TTL, ttl);
        setUnicastTtl(ttl);
        if (trafficClass != 0) {
            this.socket.setTrafficClass(trafficClass);
        }

        int boundPort = socket.getLocalPort();
        if (port > 0) {
            logger.debug("UDP Transport created on port {} (ttl={}, tc=0x{})",
                    port, ttl, Integer.toHexString(trafficClass));
        } else {
            logger.debug("UDP Transport created on ephemeral port {} (ttl={}, tc=0x{})",
                    boundPort, ttl, Integer.toHexString(trafficClass));
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
        MembershipKey key = channel.join(group, networkInterface);
        memberships.put(group, key);
        logger.debug("Joined multicast group {} on port {} via {}", group.getHostAddress(), port, networkInterface.getName());
    }

    /**
     * Leaves a previously joined multicast group.
     *
     * @param group the multicast group to leave
     * @return {@code true} if the group was joined on this transport and has been left,
     *         {@code false} if no membership existed
     */
    public boolean leaveMulticastGroup(InetAddress group) {
        MembershipKey key = memberships.remove(group);
        if (key == null) {
            return false;
        }
        key.drop();
        logger.debug("Left multicast group {} on port {}", group.getHostAddress(), port);
        return true;
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
     * Returns the IP multicast time-to-live value configured on this channel.
     *
     * @throws IOException if the socket option cannot be read
     */
    public int getTimeToLive() throws IOException {
        return channel.getOption(StandardSocketOptions.IP_MULTICAST_TTL);
    }

    /**
     * Returns the IP traffic class byte configured on this socket.
     *
     * @throws java.net.SocketException if the socket option cannot be read
     */
    public int getTrafficClass() throws java.net.SocketException {
        return socket.getTrafficClass();
    }

    private void setUnicastTtl(int ttl) {
        NativeSocketOptions.setUnicastTtl(channel, ttl);
    }

    /**
     * Returns the unicast {@code IP_TTL} value configured on this channel via
     * JNA {@code getsockopt(IPPROTO_IP, IP_TTL)}.
     *
     * @return the current unicast TTL value, or {@code -1} if unavailable
     */
    public int getUnicastTtl() {
        return NativeSocketOptions.getUnicastTtl(channel);
    }

    @Override
    public void close() {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
                logger.debug("UDP Transport closed");
            }
        } catch (IOException e) {
            logger.debug("Error closing UDP Transport: {}", e.getMessage());
        }
    }
}
