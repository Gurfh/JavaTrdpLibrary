package com.trdp.protocol;

public interface TrdpHeader {
    byte[] encode();
    
    int getSequenceCounter();
    void setSequenceCounter(int sequenceCounter);
    
    int getProtocolVersion();
    
    TrdpMessageType getMessageType();
    void setMessageType(TrdpMessageType messageType);
    
    int getComId();
    void setComId(int comId);
    
    int getDatasetLength();
    void setDatasetLength(int datasetLength);
    
    int getReplyComId();
    void setReplyComId(int replyComId);
    
    int getReplyIpAddress();
    void setReplyIpAddress(int replyIpAddress);
    
    int getHeaderFcs();
}
