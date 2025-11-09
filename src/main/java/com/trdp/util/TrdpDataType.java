package com.trdp.util;

public enum TrdpDataType {
    BOOL8(1, "Boolean 8-bit"),
    CHAR8(1, "Character 8-bit"),
    UTF16(2, "Unicode character 16-bit"),
    
    INT8(1, "Signed integer 8-bit"),
    INT16(2, "Signed integer 16-bit"),
    INT32(4, "Signed integer 32-bit"),
    INT64(8, "Signed integer 64-bit"),
    
    UINT8(1, "Unsigned integer 8-bit"),
    UINT16(2, "Unsigned integer 16-bit"),
    UINT32(4, "Unsigned integer 32-bit"),
    UINT64(8, "Unsigned integer 64-bit"),
    
    REAL32(4, "IEEE 754 single-precision float"),
    REAL64(8, "IEEE 754 double-precision float"),
    
    TIMEDATE32(4, "32-bit time (seconds since epoch)"),
    TIMEDATE48(6, "48-bit time (seconds + microseconds)"),
    TIMEDATE64(8, "64-bit time (seconds + microseconds)");
    
    private final int size;
    private final String description;
    
    TrdpDataType(int size, String description) {
        this.size = size;
        this.description = description;
    }
    
    public int getSize() {
        return size;
    }
    
    public String getDescription() {
        return description;
    }
}
