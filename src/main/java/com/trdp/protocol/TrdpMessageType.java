package com.trdp.protocol;

public enum TrdpMessageType {
    PD(0x5064),          // Process Data
    PD_REQUEST(0x5072), // Process Data Request
    PD_REPLY(0x5070),   // Process Data Reply
    PD_ERROR(0x5065),   // Process Data Error
    MD_REQUEST(0x4D72), // Message Data Request
    MD_REPLY(0x4D70),   // Message Data Reply
    MD_CONFIRM(0x4D63), // Message Data Confirm
    MD_ERROR(0x4D65),   // Message Data Error
    MD_NOTIFICATION(0x4D6E), // Message Data Notification
    MD_REPLY_CONFIRM(0x4D71); // Message Data Reply with Confirm
    
    private final int code;
    
    TrdpMessageType(int code) {
        this.code = code;
    }
    
    public int getCode() {
        return code;
    }
    
    public boolean isMd() {
        return this == MD_REQUEST || this == MD_REPLY || this == MD_CONFIRM || this == MD_ERROR || this == MD_NOTIFICATION || this == MD_REPLY_CONFIRM;
    }

    public static TrdpMessageType fromCode(int code) {
        for (TrdpMessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TRDP message type: 0x" + Integer.toHexString(code));
    }
}
