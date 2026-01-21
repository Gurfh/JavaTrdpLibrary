package com.trdp.md;

public class MdResponse {
    private final byte[] data;
    private final boolean confirmationRequested;
    private final int replyComId;

    public MdResponse(byte[] data) {
        this(data, false, 0);
    }

    public MdResponse(byte[] data, boolean confirmationRequested) {
        this(data, confirmationRequested, 0);
    }
    
    public MdResponse(byte[] data, boolean confirmationRequested, int replyComId) {
        this.data = data;
        this.confirmationRequested = confirmationRequested;
        this.replyComId = replyComId;
    }

    public byte[] getData() { return data; }
    public boolean isConfirmationRequested() { return confirmationRequested; }
    public int getReplyComId() { return replyComId; }
}