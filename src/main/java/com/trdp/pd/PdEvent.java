package com.trdp.pd;

import java.net.InetAddress;
import java.util.Arrays;

public class PdEvent {

    public enum Type { DATA, REPLY, REQUEST, TIMEOUT, VALIDITY_RESTORED }

    private final Type type;
    private final int comId;
    private final byte[] data;
    private final int sequenceCounter;
    private final InetAddress sourceAddress;
    private final InetAddress destinationAddress;
    private final int replyComId;
    private final int replyIpAddress;
    private final int resultCode;

    public PdEvent(Type type, int comId, byte[] data, int sequenceCounter,
                   InetAddress sourceAddress, InetAddress destinationAddress,
                   int replyComId, int replyIpAddress, int resultCode) {
        this.type = type;
        this.comId = comId;
        this.data = data != null ? Arrays.copyOf(data, data.length) : null;
        this.sequenceCounter = sequenceCounter;
        this.sourceAddress = sourceAddress;
        this.destinationAddress = destinationAddress;
        this.replyComId = replyComId;
        this.replyIpAddress = replyIpAddress;
        this.resultCode = resultCode;
    }

    public Type getType() { return type; }
    public int getComId() { return comId; }
    public byte[] getData() { return data != null ? Arrays.copyOf(data, data.length) : null; }
    public int getSequenceCounter() { return sequenceCounter; }
    public InetAddress getSourceAddress() { return sourceAddress; }
    public InetAddress getDestinationAddress() { return destinationAddress; }
    public int getReplyComId() { return replyComId; }
    public int getReplyIpAddress() { return replyIpAddress; }
    public int getResultCode() { return resultCode; }
}
