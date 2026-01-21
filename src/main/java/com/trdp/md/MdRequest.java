package com.trdp.md;

import java.net.InetAddress;
import java.util.UUID;

public class MdRequest {
    private final int comId;
    private final byte[] data;
    private final UUID sessionId;
    private final String sourceUri;
    private final String destinationUri;
    private final InetAddress sourceAddress;
    private final int sourcePort;
    private final int sequenceCounter;

    public MdRequest(int comId, byte[] data, UUID sessionId, String sourceUri, 
                     String destinationUri, InetAddress sourceAddress, int sourcePort, int sequenceCounter) {
        this.comId = comId;
        this.data = data;
        this.sessionId = sessionId;
        this.sourceUri = sourceUri;
        this.destinationUri = destinationUri;
        this.sourceAddress = sourceAddress;
        this.sourcePort = sourcePort;
        this.sequenceCounter = sequenceCounter;
    }

    public int getComId() { return comId; }
    public byte[] getData() { return data; }
    public UUID getSessionId() { return sessionId; }
    public String getSourceUri() { return sourceUri; }
    public String getDestinationUri() { return destinationUri; }
    public InetAddress getSourceAddress() { return sourceAddress; }
    public int getSourcePort() { return sourcePort; }
    public int getSequenceCounter() { return sequenceCounter; }
}