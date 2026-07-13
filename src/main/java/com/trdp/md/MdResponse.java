package com.trdp.md;

import java.util.Arrays;

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
        this.data = data != null ? Arrays.copyOf(data, data.length) : null;
        this.confirmationRequested = confirmationRequested;
        this.replyComId = replyComId;
    }

    public byte[] getData() { return data != null ? Arrays.copyOf(data, data.length) : null; }
    public boolean isConfirmationRequested() { return confirmationRequested; }
    public int getReplyComId() { return replyComId; }
}