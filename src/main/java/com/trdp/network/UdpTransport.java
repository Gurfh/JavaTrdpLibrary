package com.trdp.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
        setUnicastTtl(ttl);
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
    
    /**
     * Sets the unicast IP_TTL socket option via JDK internals.
     * {@link MulticastSocket#setTimeToLive(int)} only sets {@code IP_MULTICAST_TTL},
     * which has no effect on unicast packets — the OS default TTL is used instead
     * (128 on Windows, 64 on Linux). This method uses reflection to call
     * {@code setsockopt(IPPROTO_IP, IP_TTL)} for unicast TTL control.
     * <p>
     * Requires JVM flags: {@code --add-opens java.base/java.net=ALL-UNNAMED
     * --add-opens java.base/sun.nio.ch=ALL-UNNAMED}
     */
    private void setUnicastTtl(int ttl) {
        try {
            FileDescriptor fd = getSocketFd();

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
                    + "Add JVM flags: --add-opens java.base/java.net=ALL-UNNAMED "
                    + "--add-opens java.base/sun.nio.ch=ALL-UNNAMED",
                    e.getMessage());
        }
    }

    /**
     * Extracts the native {@link FileDescriptor} from the socket, handling both
     * JDK 17 ({@code DatagramSocket.impl → DatagramSocketImpl.fd}) and
     * JDK 21+ ({@code DatagramSocket.delegate → DatagramSocketAdaptor.dc → fd})
     * internal layouts.
     */
    private FileDescriptor getSocketFd() throws ReflectiveOperationException {
        // JDK 21+: DatagramSocket.delegate → DatagramSocketAdaptor.dc → fd
        try {
            Field delegateField = DatagramSocket.class.getDeclaredField("delegate");
            delegateField.setAccessible(true);
            Object delegate = delegateField.get(socket);

            Field dcField = delegate.getClass().getDeclaredField("dc");
            dcField.setAccessible(true);
            Object dc = dcField.get(delegate);

            return findFd(dc);
        } catch (NoSuchFieldException ignored) {
            // fall through to JDK 17 path
        }

        // JDK 17: DatagramSocket.impl → DatagramSocketImpl.fd
        Field implField = DatagramSocket.class.getDeclaredField("impl");
        implField.setAccessible(true);
        Object impl = implField.get(socket);
        return findFd(impl);
    }

    private static FileDescriptor findFd(Object obj) throws ReflectiveOperationException {
        Class<?> c = obj.getClass();
        while (c != null && c != Object.class) {
            try {
                Field fdField = c.getDeclaredField("fd");
                if (fdField.getType() == FileDescriptor.class) {
                    fdField.setAccessible(true);
                    return (FileDescriptor) fdField.get(obj);
                }
            } catch (NoSuchFieldException ignored) {
                // continue up the hierarchy
            }
            c = c.getSuperclass();
        }
        throw new NoSuchFieldException("fd not found in " + obj.getClass().getName());
    }

    @Override
    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            logger.debug("UDP Transport closed");
        }
    }
}
