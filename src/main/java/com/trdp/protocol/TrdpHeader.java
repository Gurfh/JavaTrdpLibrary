package com.trdp.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TrdpHeader {
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
    
    public TrdpHeader() {
        this.protocolVersion = TrdpConstants.PROTOCOL_VERSION;
    }
    
    public byte[] encode() {
        ByteBuffer buffer = ByteBuffer.allocate(TrdpConstants.TRDP_HEADER_SIZE);
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
        this.headerFcs = calculateFcs(headerWithoutFcs, 0, TrdpConstants.TRDP_HEADER_SIZE - 4);
        
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(headerFcs);
        
        return buffer.array();
    }
    
    public static TrdpHeader decode(byte[] data) {
        if (data.length < TrdpConstants.TRDP_HEADER_SIZE) {
            throw new IllegalArgumentException("Data too short for TRDP header");
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        TrdpHeader header = new TrdpHeader();
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
        
        int calculatedFcs = calculateFcs(data, 0, TrdpConstants.TRDP_HEADER_SIZE - 4);
        if (calculatedFcs != header.headerFcs) {
            throw new IllegalStateException("Header FCS mismatch");
        }
        
        return header;
    }
    
    private static int calculateFcs(byte[] data, int offset, int length) {
        int crc = 0xFFFFFFFF;
        
        for (int i = offset; i < offset + length; i++) {
            crc ^= (data[i] & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0xEDB88320;
                } else {
                    crc = crc >>> 1;
                }
            }
        }
        
        return ~crc;
    }
    
    public int getSequenceCounter() { return sequenceCounter; }
    public void setSequenceCounter(int sequenceCounter) { this.sequenceCounter = sequenceCounter; }
    
    public int getProtocolVersion() { return protocolVersion; }
    public void setProtocolVersion(int protocolVersion) { this.protocolVersion = protocolVersion; }
    
    public TrdpMessageType getMessageType() { return messageType; }
    public void setMessageType(TrdpMessageType messageType) { this.messageType = messageType; }
    
    public int getComId() { return comId; }
    public void setComId(int comId) { this.comId = comId; }
    
    public int getEtbTopoCnt() { return etbTopoCnt; }
    public void setEtbTopoCnt(int etbTopoCnt) { this.etbTopoCnt = etbTopoCnt; }
    
    public int getOpTrnTopoCnt() { return opTrnTopoCnt; }
    public void setOpTrnTopoCnt(int opTrnTopoCnt) { this.opTrnTopoCnt = opTrnTopoCnt; }
    
    public int getDatasetLength() { return datasetLength; }
    public void setDatasetLength(int datasetLength) { this.datasetLength = datasetLength; }
    
    public int getReplyComId() { return replyComId; }
    public void setReplyComId(int replyComId) { this.replyComId = replyComId; }
    
    public int getReplyIpAddress() { return replyIpAddress; }
    public void setReplyIpAddress(int replyIpAddress) { this.replyIpAddress = replyIpAddress; }
    
    public int getHeaderFcs() { return headerFcs; }
}
