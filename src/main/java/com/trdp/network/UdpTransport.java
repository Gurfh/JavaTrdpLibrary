package com.trdp.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;

public class UdpTransport implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(UdpTransport.class);

    private final DatagramChannel channel;
    private final DatagramSocket socket;
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
            logger.debug("UDP Transport created on port {} (ttl={}, tc=0x{}))",
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

    /**
     * Sets the unicast IP_TTL socket option via {@code setsockopt(IPPROTO_IP, IP_TTL)}.
     * <p>
     * Java provides no public API for unicast TTL — {@code IP_MULTICAST_TTL} only
     * affects multicast packets. This method accesses the channel's native file
     * descriptor via reflection and calls {@code sun.nio.ch.Net.setIntOption0()}.
     * <p>
     * Requires JVM flag: {@code --add-opens java.base/sun.nio.ch=ALL-UNNAMED}
     */
    private void setUnicastTtl(int ttl) {
        try {
            // Get the native FileDescriptor from the DatagramChannelImpl
            Field fdField = channel.getClass().getDeclaredField("fd");
            fdField.setAccessible(true);
            FileDescriptor fd = (FileDescriptor) fdField.get(channel);

            Class<?> netClass = Class.forName("sun.nio.ch.Net");
            Method setIntOption0 = netClass.getDeclaredMethod("setIntOption0",
                    FileDescriptor.class, boolean.class, int.class, int.class,
                    int.class, boolean.class);
            setIntOption0.setAccessible(true);

            // IPPROTO_IP = 0 on all platforms
            // IP_TTL: Windows = 4, Linux/macOS = 2
            int ipTtlOpt = System.getProperty("os.name", "").toLowerCase()
                    .contains("win") ? 4 : 2;
            setIntOption0.invoke(null, fd, false, 0, ipTtlOpt, ttl, false);
            logger.debug("Unicast IP_TTL set to {}", ttl);
        } catch (Exception e) {
            logger.debug("Could not set unicast IP_TTL: {}. "
                    + "Unicast packets will use the OS default TTL. "
                    + "Add JVM flag: --add-opens java.base/sun.nio.ch=ALL-UNNAMED",
                    e.getMessage());
        }
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
