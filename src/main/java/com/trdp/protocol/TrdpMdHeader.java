package com.trdp.protocol;

import com.trdp.util.FcsUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class TrdpMdHeader extends TrdpPdHeader {
    private int replyStatus;
    private byte[] sessionId = new byte[16];
    private int replyTimeout;
    private byte[] sourceUri = new byte[32];
    private byte[] destinationUri = new byte[32];

    public TrdpMdHeader() {
        super();
    }

    @Override
    public byte[] encode() {
        ByteBuffer buffer = ByteBuffer.allocate(TrdpConstants.TRDP_MD_HEADER_SIZE);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(getSequenceCounter());
        buffer.putShort((short) getProtocolVersion());
        buffer.putShort((short) getMessageType().getCode());
        buffer.putInt(getComId());
        
        buffer.putInt(getEtbTopoCnt());
        buffer.putInt(getOpTrnTopoCnt());
        
        buffer.putInt(getDatasetLength());
        
        buffer.putInt(replyStatus);
        buffer.put(sessionId);
        buffer.putInt(replyTimeout);
        buffer.put(sourceUri);
        buffer.put(destinationUri);

        // FCS calculation
        byte[] headerBytesForFcs = new byte[TrdpConstants.TRDP_MD_HEADER_SIZE - 4];
        buffer.position(0);
        buffer.get(headerBytesForFcs);
        int headerFcs = FcsUtils.calculateFcs(headerBytesForFcs, 0, headerBytesForFcs.length);

        // Write FCS to the last 4 bytes
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(headerFcs);

        return buffer.array();
    }

    public static TrdpMdHeader decode(byte[] data) {
        if (data.length < TrdpConstants.TRDP_MD_HEADER_SIZE) {
            throw new IllegalArgumentException("Data too short for TRDP MD header");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.BIG_ENDIAN);

        TrdpMdHeader header = new TrdpMdHeader();
        header.setSequenceCounter(buffer.getInt());
        header.setProtocolVersion(buffer.getShort() & 0xFFFF);
        header.setMessageType(TrdpMessageType.fromCode(buffer.getShort() & 0xFFFF));
        header.setComId(buffer.getInt());
        
        header.setEtbTopoCnt(buffer.getInt());
        header.setOpTrnTopoCnt(buffer.getInt());
        
        header.setDatasetLength(buffer.getInt());
        
        header.replyStatus = buffer.getInt();
        buffer.get(header.sessionId);
        header.replyTimeout = buffer.getInt();
        buffer.get(header.sourceUri);
        buffer.get(header.destinationUri);

        // Validate FCS
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        int receivedFcs = buffer.getInt();
        int calculatedFcs = FcsUtils.calculateFcs(data, 0, TrdpConstants.TRDP_MD_HEADER_SIZE - 4);
        
        if (receivedFcs != calculatedFcs) {
             throw new IllegalStateException("MD Header FCS mismatch");
        }

        return header;
    }

    // --- Session ID Handling (UUID) ---

    public void setSessionId(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(this.sessionId);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
    }

    public void setSessionId(byte[] bytes) {
        if (bytes.length != 16) throw new IllegalArgumentException("Session ID must be 16 bytes");
        System.arraycopy(bytes, 0, this.sessionId, 0, 16);
    }

    public UUID getSessionIdAsUuid() {
        ByteBuffer bb = ByteBuffer.wrap(this.sessionId);
        long high = bb.getLong();
        long low = bb.getLong();
        return new UUID(high, low);
    }

    public byte[] getSessionId() {
        return sessionId;
    }

    // --- URI Handling ---

    public void setSourceUri(String uri) {
        this.sourceUri = stringToBytes(uri, 32);
    }

    public String getSourceUriString() {
        return bytesToString(this.sourceUri);
    }

    public void setDestinationUri(String uri) {
        this.destinationUri = stringToBytes(uri, 32);
    }

    public String getDestinationUriString() {
        return bytesToString(this.destinationUri);
    }

    // --- Getters/Setters for other fields ---

    public int getReplyStatus() { return replyStatus; }
    public void setReplyStatus(int replyStatus) { this.replyStatus = replyStatus; }

    public int getReplyTimeout() { return replyTimeout; }
    public void setReplyTimeout(int replyTimeout) { this.replyTimeout = replyTimeout; }

    // --- Helpers ---

    private byte[] stringToBytes(String s, int length) {
        byte[] b = new byte[length];
        if (s != null) {
            byte[] strBytes = s.getBytes(StandardCharsets.UTF_8);
            if (strBytes.length <= length) {
                System.arraycopy(strBytes, 0, b, 0, strBytes.length);
            } else {
                // Truncate at a valid UTF-8 character boundary
                int copyLen = 0;
                int i = 0;
                while (i < strBytes.length) {
                    int charLen;
                    byte lead = strBytes[i];
                    if ((lead & 0x80) == 0) charLen = 1;
                    else if ((lead & 0xE0) == 0xC0) charLen = 2;
                    else if ((lead & 0xF0) == 0xE0) charLen = 3;
                    else charLen = 4;

                    if (i + charLen > length) break;
                    i += charLen;
                    copyLen = i;
                }
                System.arraycopy(strBytes, 0, b, 0, copyLen);
            }
        }
        return b;
    }

    private String bytesToString(byte[] b) {
        int len = 0;
        while (len < b.length && b[len] != 0) len++;
        return new String(b, 0, len, StandardCharsets.UTF_8);
    }
}