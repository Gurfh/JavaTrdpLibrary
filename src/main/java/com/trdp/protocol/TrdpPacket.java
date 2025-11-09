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
        int totalSize = headerBytes.length + payload.length + TrdpConstants.TRDP_FCS_SIZE;
        
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        buffer.put(headerBytes);
        buffer.put(payload);
        
        this.dataFcs = calculateDataFcs(payload);
        buffer.putInt(dataFcs);
        
        return buffer.array();
    }
    
    public static TrdpPacket decode(byte[] data) {
        if (data.length < TrdpConstants.TRDP_HEADER_SIZE + TrdpConstants.TRDP_FCS_SIZE) {
            throw new IllegalArgumentException("Data too short for TRDP packet");
        }
        
        TrdpHeader header = TrdpHeader.decode(data);
        
        int payloadLength = header.getDatasetLength();
        if (data.length < TrdpConstants.TRDP_HEADER_SIZE + payloadLength + TrdpConstants.TRDP_FCS_SIZE) {
            throw new IllegalArgumentException("Data length mismatch");
        }
        
        byte[] payload = Arrays.copyOfRange(data, TrdpConstants.TRDP_HEADER_SIZE, 
                                            TrdpConstants.TRDP_HEADER_SIZE + payloadLength);
        
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.position(TrdpConstants.TRDP_HEADER_SIZE + payloadLength);
        int receivedDataFcs = buffer.getInt();
        
        int calculatedDataFcs = calculateDataFcs(payload);
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
