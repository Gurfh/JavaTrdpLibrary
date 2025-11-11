package com.trdp.protocol;

import com.trdp.util.FcsUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
        buffer.putInt(getDatasetLength());
        buffer.putInt(getReplyComId());
        buffer.putInt(getReplyIpAddress());

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
        header.setDatasetLength(buffer.getInt());
        header.setReplyComId(buffer.getInt());
        header.setReplyIpAddress(buffer.getInt());

        header.replyStatus = buffer.getInt();
        buffer.get(header.sessionId);
        header.replyTimeout = buffer.getInt();
        buffer.get(header.sourceUri);
        buffer.get(header.destinationUri);

        return header;
    }
}
