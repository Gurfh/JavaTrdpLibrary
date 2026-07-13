package com.trdp.md;

import com.trdp.protocol.TrdpMessageType;

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
    private final TrdpMessageType messageType;

    public MdRequest(int comId, byte[] data, UUID sessionId, String sourceUri,
                     String destinationUri, InetAddress sourceAddress, int sourcePort,
                     int sequenceCounter, TrdpMessageType messageType) {
        this.comId = comId;
        this.data = data;
        this.sessionId = sessionId;
        this.sourceUri = sourceUri;
        this.destinationUri = destinationUri;
        this.sourceAddress = sourceAddress;
        this.sourcePort = sourcePort;
        this.sequenceCounter = sequenceCounter;
        this.messageType = messageType;
    }

    public int getComId() { return comId; }
    public byte[] getData() { return data; }
    public UUID getSessionId() { return sessionId; }
    public String getSourceUri() { return sourceUri; }
    public String getDestinationUri() { return destinationUri; }
    public InetAddress getSourceAddress() { return sourceAddress; }
    public int getSourcePort() { return sourcePort; }
    public int getSequenceCounter() { return sequenceCounter; }

    /**
     * Returns the wire message type: {@link TrdpMessageType#MD_REQUEST} (Mr) or
     * {@link TrdpMessageType#MD_NOTIFICATION} (Mn).
     */
    public TrdpMessageType getMessageType() { return messageType; }

    /**
     * Returns whether this is a notification (Mn). Notifications are
     * fire-and-forget: any response returned by the handler is discarded,
     * as replying to an Mn would violate IEC 61375-2-3.
     */
    public boolean isNotification() { return messageType == TrdpMessageType.MD_NOTIFICATION; }

    @Override
    public String toString() {
        return String.format("MdRequest{type=%s, comId=%d, session=%s, seq=%d, src='%s', dst='%s', from=%s:%d, dataLen=%d}",
            messageType, comId, sessionId, sequenceCounter, sourceUri, destinationUri,
            sourceAddress, sourcePort, data != null ? data.length : 0);
    }
}
