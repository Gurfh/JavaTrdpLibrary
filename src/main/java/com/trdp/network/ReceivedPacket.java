package com.trdp.network;

import java.net.InetAddress;
import java.util.Arrays;

public class ReceivedPacket {
    private final byte[] data;
    private final int length;
    private final InetAddress sourceAddress;
    private final int sourcePort;

    public ReceivedPacket(byte[] data, int length, InetAddress sourceAddress, int sourcePort) {
        this.data = Arrays.copyOf(data, length);
        this.length = length;
        this.sourceAddress = sourceAddress;
        this.sourcePort = sourcePort;
    }

    public byte[] getData() { return Arrays.copyOf(data, data.length); }
    public int getLength() { return length; }
    public InetAddress getSourceAddress() { return sourceAddress; }
    public int getSourcePort() { return sourcePort; }
}
