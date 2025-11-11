package com.trdp.protocol;

import com.trdp.util.FcsUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TrdpPdHeader implements TrdpHeader {
    private int sequenceCounter;
    private int protocolVersion;
    private TrdpMessageType messageType;
    private int comId;
    private int etbTopoCnt;
    private int opTrnTopoCnt;
    private int datasetLength;
    private int reserved;
    private int replyComId;
    private int replyIpAddress;
    private int headerFcs;

    public TrdpPdHeader() {
        this.protocolVersion = TrdpConstants.PROTOCOL_VERSION;
    }

    @Override
    public byte[] encode() {
        ByteBuffer buffer = ByteBuffer.allocate(TrdpConstants.TRDP_PD_HEADER_SIZE);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(sequenceCounter);
        buffer.putShort((short) protocolVersion);
        buffer.putShort((short) messageType.getCode());
        buffer.putInt(comId);
        buffer.putInt(etbTopoCnt);
        buffer.putInt(opTrnTopoCnt);
        buffer.putInt(datasetLength);
        buffer.putInt(reserved);
        buffer.putInt(replyComId);
        buffer.putInt(replyIpAddress);

        byte[] headerWithoutFcs = buffer.array();
        this.headerFcs = FcsUtils.calculateFcs(headerWithoutFcs, 0, TrdpConstants.TRDP_PD_HEADER_SIZE - 4);

        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(headerFcs);

        return buffer.array();
    }

    public static TrdpPdHeader decode(byte[] data) {
        if (data.length < TrdpConstants.TRDP_PD_HEADER_SIZE) {
            throw new IllegalArgumentException("Data too short for TRDP header");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.BIG_ENDIAN);

        TrdpPdHeader header = new TrdpPdHeader();
        header.sequenceCounter = buffer.getInt();
        header.protocolVersion = buffer.getShort() & 0xFFFF;
        header.messageType = TrdpMessageType.fromCode(buffer.getShort() & 0xFFFF);
        header.comId = buffer.getInt();
        header.etbTopoCnt = buffer.getInt();
        header.opTrnTopoCnt = buffer.getInt();
        header.datasetLength = buffer.getInt();
        header.reserved = buffer.getInt();
        header.replyComId = buffer.getInt();
        header.replyIpAddress = buffer.getInt();

        buffer.order(ByteOrder.LITTLE_ENDIAN);
        header.headerFcs = buffer.getInt();

        int calculatedFcs = FcsUtils.calculateFcs(data, 0, TrdpConstants.TRDP_PD_HEADER_SIZE - 4);
        if (calculatedFcs != header.headerFcs) {
            throw new IllegalStateException("Header FCS mismatch");
        }

        return header;
    }

    @Override
    public int getSequenceCounter() { return sequenceCounter; }
    @Override
    public void setSequenceCounter(int sequenceCounter) { this.sequenceCounter = sequenceCounter; }

    @Override
    public int getProtocolVersion() { return protocolVersion; }
    public void setProtocolVersion(int protocolVersion) { this.protocolVersion = protocolVersion; }

    @Override
    public TrdpMessageType getMessageType() { return messageType; }
    @Override
    public void setMessageType(TrdpMessageType messageType) { this.messageType = messageType; }

    @Override
    public int getComId() { return comId; }
    @Override
    public void setComId(int comId) { this.comId = comId; }

    public int getEtbTopoCnt() { return etbTopoCnt; }
    public void setEtbTopoCnt(int etbTopoCnt) { this.etbTopoCnt = etbTopoCnt; }

    public int getOpTrnTopoCnt() { return opTrnTopoCnt; }
    public void setOpTrnTopoCnt(int opTrnTopoCnt) { this.opTrnTopoCnt = opTrnTopoCnt; }

    @Override
    public int getDatasetLength() { return datasetLength; }
    @Override
    public void setDatasetLength(int datasetLength) { this.datasetLength = datasetLength; }

    @Override
    public int getReplyComId() { return replyComId; }
    @Override
    public void setReplyComId(int replyComId) { this.replyComId = replyComId; }

    @Override
    public int getReplyIpAddress() { return replyIpAddress; }
    @Override
    public void setReplyIpAddress(int replyIpAddress) { this.replyIpAddress = replyIpAddress; }

    @Override
    public int getHeaderFcs() { return headerFcs; }
}
