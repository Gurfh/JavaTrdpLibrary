package com.trdp.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class TrdpPacket {
    private TrdpHeader header;
    private byte[] payload;
    private int dataFcs;
    
    public TrdpPacket(TrdpHeader header, byte[] payload) {
        this.header = header;
        this.payload = payload != null ? payload : new byte[0];
        this.header.setDatasetLength(this.payload.length);
    }
    
    public byte[] encode() {
        byte[] headerBytes = header.encode();
        
        // 1. Calculate required padding
        int padding = (4 - (payload.length % 4)) % 4;
        
        // 2. Prepare data for FCS calculation (Payload + Padding)
        byte[] dataToCrc = new byte[payload.length + padding];
        System.arraycopy(payload, 0, dataToCrc, 0, payload.length);
        // Padding bytes in dataToCrc are already 0
        
        // 3. Calculate FCS over Payload + Padding
        this.dataFcs = calculateDataFcs(dataToCrc);
        
        int totalSize = headerBytes.length + payload.length + padding + TrdpConstants.TRDP_FCS_SIZE;
        
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        buffer.put(headerBytes);
        buffer.put(payload);
        
        // 4. Write Padding
        for (int i = 0; i < padding; i++) {
            buffer.put((byte) 0);
        }
        
        // 5. Write FCS in Little Endian (Standard compliance)
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(dataFcs);
        
        return buffer.array();
    }
    
    public static TrdpPacket decode(byte[] data) {
        if (data.length < TrdpConstants.TRDP_PD_HEADER_SIZE + TrdpConstants.TRDP_FCS_SIZE) {
            throw new IllegalArgumentException("Data too short for TRDP packet");
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.BIG_ENDIAN);
        int messageTypeCode = buffer.getShort(6) & 0xFFFF;
        TrdpMessageType messageType = TrdpMessageType.fromCode(messageTypeCode);

        TrdpHeader header;
        int headerSize;

        if (messageType.isMd()) {
            header = TrdpMdHeader.decode(data);
            headerSize = TrdpConstants.TRDP_MD_HEADER_SIZE;
        } else {
            header = TrdpPdHeader.decode(data);
            headerSize = TrdpConstants.TRDP_PD_HEADER_SIZE;
        }
        
        int payloadLength = header.getDatasetLength();
        
        // 1. Calculate Padding to skip
        int padding = (4 - (payloadLength % 4)) % 4;
        
        if (data.length < headerSize + payloadLength + padding + TrdpConstants.TRDP_FCS_SIZE) {
            throw new IllegalArgumentException("Data length mismatch");
        }
        
        byte[] payload = Arrays.copyOfRange(data, headerSize, headerSize + payloadLength);
        
        // 2. Position buffer after Payload AND Padding
        buffer.position(headerSize + payloadLength + padding);
        
        // 3. Read FCS in Little Endian
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        int receivedDataFcs = buffer.getInt();
        
        // 4. Calculate Expected FCS (Payload + Padding)
        byte[] dataToCrc = new byte[payload.length + padding];
        System.arraycopy(payload, 0, dataToCrc, 0, payload.length);
        
        int calculatedDataFcs = calculateDataFcs(dataToCrc);
        
        if (calculatedDataFcs != receivedDataFcs) {
            throw new IllegalStateException("Data FCS mismatch");
        }
        
        TrdpPacket packet = new TrdpPacket(header, payload);
        packet.dataFcs = receivedDataFcs;
        return packet;
    }
    
    private static int calculateDataFcs(byte[] data) {
        int crc = 0xFFFFFFFF;
        
        for (int i = 0; i < data.length; i++) {
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
    
    public TrdpHeader getHeader() { return header; }
    public byte[] getPayload() { return payload; }
    public int getDataFcs() { return dataFcs; }
}
