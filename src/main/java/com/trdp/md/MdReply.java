package com.trdp.md;

import java.util.Arrays;

public class MdReply {
    private final int comId;
    private final byte[] data;
    private final int sequenceNumber;

    public MdReply(int comId, byte[] data, int sequenceNumber) {
        this.comId = comId;
        this.data = data != null ? Arrays.copyOf(data, data.length) : null;
        this.sequenceNumber = sequenceNumber;
    }

    public int getComId() { return comId; }
    public byte[] getData() { return data != null ? Arrays.copyOf(data, data.length) : null; }
    public int getSequenceNumber() { return sequenceNumber; }

    @Override
    public String toString() {
        return String.format("MdReply{comId=%d, seq=%d, dataLen=%d}",
            comId, sequenceNumber, data != null ? data.length : 0);
    }
}
