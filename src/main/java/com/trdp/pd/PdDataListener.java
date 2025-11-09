package com.trdp.pd;

@FunctionalInterface
public interface PdDataListener {
    void onDataReceived(int comId, byte[] data, int sequenceNumber);
}
