package com.trdp.protocol;

public final class TrdpConstants {
    
    public static final int TRDP_PD_HEADER_SIZE = 40;
    public static final int TRDP_MD_HEADER_SIZE = 116;
    public static final int TRDP_FCS_SIZE = 4;
    public static final int TRDP_MAX_PD_DATA_SIZE = 1432;
    public static final int TRDP_MAX_PACKET_SIZE = TRDP_PD_HEADER_SIZE + TRDP_MAX_PD_DATA_SIZE;
    public static final int TRDP_MAX_MD_DATA_SIZE = TRDP_MAX_PACKET_SIZE - TRDP_MD_HEADER_SIZE;
    
    public static final int PROTOCOL_VERSION = 0x0100;
    
    public static final int DEFAULT_PD_PORT = 17224;
    public static final int DEFAULT_MD_PORT = 17225;
    
    public static final String DEFAULT_MULTICAST_GROUP = "239.255.0.1";
    
    public static final long DEFAULT_PD_TIMEOUT_US = 100_000; // 100ms in microseconds
    public static final long DEFAULT_MD_REPLY_TIMEOUT_US = 5_000_000;    // 5s
    public static final long DEFAULT_MD_CONFIRM_TIMEOUT_US = 1_000_000;  // 1s
    public static final long DEFAULT_MD_CONNECT_TIMEOUT_US = 60_000_000; // 60s
    
    private TrdpConstants() {
        throw new UnsupportedOperationException("Utility class");
    }
}
