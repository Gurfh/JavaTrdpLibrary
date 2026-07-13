package com.trdp.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class TrdpPacket {
    private TrdpHeader header;
    private final byte[] payload;
    
    public TrdpPacket(TrdpHeader header, byte[] payload) {
        this.header = header;
        this.payload = payload != null ? Arrays.copyOf(payload, payload.length) : new byte[0];
        // Ensure the header knows the payload length
        this.header.setDatasetLength(this.payload.length);
    }
    
    public byte[] encode() {
        byte[] headerBytes = header.encode();
        
        // Calculate required padding to reach 4-byte boundary
        int padding = (4 - (payload.length % 4)) % 4;
        
        // Total size = Header + Payload + Padding
        // Note: No Data FCS is added here as per IEC 61375-2-3 for standard PD/MD
        int totalSize = headerBytes.length + payload.length + padding;
        
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        buffer.put(headerBytes);
        buffer.put(payload);
        
        // Write zero-padding
        for (int i = 0; i < padding; i++) {
            buffer.put((byte) 0);
        }
        
        return buffer.array();
    }
    
    public static TrdpPacket decode(byte[] data) {
        // Basic check: Data must be at least as large as the smallest header (PD Header)
        if (data.length < TrdpConstants.TRDP_PD_HEADER_SIZE) {
            throw new IllegalArgumentException("Data too short for TRDP packet");
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        // Message Type is at offset 6 (after 4 bytes SeqCounter + 2 bytes ProtocolVersion)
        int messageTypeCode = buffer.getShort(6) & 0xFFFF;
        TrdpMessageType messageType = TrdpMessageType.fromCode(messageTypeCode);

        TrdpHeader header;
        int headerSize;

        if (messageType.isMd()) {
            if (data.length < TrdpConstants.TRDP_MD_HEADER_SIZE) {
                throw new IllegalArgumentException("Data too short for TRDP MD packet");
            }
            header = TrdpMdHeader.decode(data);
            headerSize = TrdpConstants.TRDP_MD_HEADER_SIZE;
        } else {
            header = TrdpPdHeader.decode(data);
            headerSize = TrdpConstants.TRDP_PD_HEADER_SIZE;
        }
        
        int payloadLength = header.getDatasetLength();

        // Verify that the received data contains the full payload.
        // We do not strictly check for padding at the end in decode() because
        // the transport layer might deliver the exact buffer size.
        // Compare against the remaining bytes (not headerSize + payloadLength,
        // which can overflow for corrupt/hostile length fields).
        if (payloadLength < 0 || payloadLength > data.length - headerSize) {
            throw new IllegalArgumentException("Data length mismatch: declared payload " +
                                             payloadLength + " bytes, got " + (data.length - headerSize));
        }
        
        byte[] payload = Arrays.copyOfRange(data, headerSize, headerSize + payloadLength);
        
        return new TrdpPacket(header, payload);
    }
    
    public TrdpHeader getHeader() { return header; }
    public byte[] getPayload() { return Arrays.copyOf(payload, payload.length); }
}
