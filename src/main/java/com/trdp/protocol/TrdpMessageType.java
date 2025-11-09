package com.trdp.protocol;

public enum TrdpMessageType {
    PD(0x5064),
    PD_REQUEST(0x5072),
    MD_REQUEST(0x4D72),
    MD_REPLY(0x4D70),
    MD_CONFIRM(0x4D63),
    MD_ERROR(0x4D65);
    
    private final int code;
    
    TrdpMessageType(int code) {
        this.code = code;
    }
    
    public int getCode() {
        return code;
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
