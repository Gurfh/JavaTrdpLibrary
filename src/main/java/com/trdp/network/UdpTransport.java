package com.trdp.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;

public class UdpTransport implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(UdpTransport.class);
    
    private final MulticastSocket socket;
    private final int port;
    
    public UdpTransport() throws IOException {
        this(0);
    }
    
    public UdpTransport(int port) throws IOException {
        this.port = port;
        this.socket = new MulticastSocket(port);
        this.socket.setReuseAddress(true);
        
        if (port > 0) {
            logger.debug("UDP Transport created on port {}", port);
        } else {
            logger.debug("UDP Transport created on ephemeral port {}", socket.getLocalPort());
        }
    }
    
    public void joinMulticastGroup(InetAddress group) throws IOException {
        NetworkInterface networkInterface = NetworkInterface.getByInetAddress(
            InetAddress.getLocalHost());
        
        if (networkInterface == null) {
            networkInterface = NetworkInterface.getNetworkInterfaces().nextElement();
        }
        
        socket.joinGroup(new InetSocketAddress(group, port), networkInterface);
        logger.debug("Joined multicast group {} on port {}", group.getHostAddress(), port);
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
    
    @Override
    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            logger.debug("UDP Transport closed");
        }
    }
}
