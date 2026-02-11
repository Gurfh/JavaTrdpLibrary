package com.trdp.md;

public class MdReply {
    private final int comId;
    private final byte[] data;
    private final int sequenceNumber;
    
    public MdReply(int comId, byte[] data, int sequenceNumber) {
        this.comId = comId;
        this.data = data;
        this.sequenceNumber = sequenceNumber;
    }
    
    public int getComId() { return comId; }
    public byte[] getData() { return data; }
    public int getSequenceNumber() { return sequenceNumber; }

    @Override
    public String toString() {
        return String.format("MdReply{comId=%d, seq=%d, dataLen=%d}",
            comId, sequenceNumber, data != null ? data.length : 0);
    }
}
