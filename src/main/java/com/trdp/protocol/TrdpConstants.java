package com.trdp.protocol;

public final class TrdpConstants {
    
    public static final int TRDP_HEADER_SIZE = 40;
    public static final int TRDP_FCS_SIZE = 4;
    public static final int TRDP_MAX_PACKET_SIZE = 1432;
    public static final int TRDP_MAX_PD_DATA_SIZE = TRDP_MAX_PACKET_SIZE - TRDP_HEADER_SIZE - TRDP_FCS_SIZE;
    public static final int TRDP_MAX_MD_DATA_SIZE = TRDP_MAX_PACKET_SIZE - TRDP_HEADER_SIZE - TRDP_FCS_SIZE - 24;
    
    public static final int PROTOCOL_VERSION = 0x0100;
    
    public static final int DEFAULT_PD_PORT = 17224;
    public static final int DEFAULT_MD_PORT = 17225;
    
    public static final String DEFAULT_MULTICAST_GROUP = "239.255.0.1";
    
    public static final int DEFAULT_PD_TIMEOUT_MS = 1000;
    public static final int DEFAULT_MD_TIMEOUT_MS = 5000;
    
    private TrdpConstants() {
        throw new UnsupportedOperationException("Utility class");
    }
}
