package com.trdp.util;

import java.util.HashMap;
import java.util.Map;

public enum TrdpDataType {
    BOOL8(1, 1, "Boolean 8-bit"),
    CHAR8(2, 1, "Character 8-bit"),
    UTF16(3, 2, "Unicode character 16-bit"),

    INT8(4, 1, "Signed integer 8-bit"),
    INT16(5, 2, "Signed integer 16-bit"),
    INT32(6, 4, "Signed integer 32-bit"),
    INT64(7, 8, "Signed integer 64-bit"),

    UINT8(8, 1, "Unsigned integer 8-bit"),
    UINT16(9, 2, "Unsigned integer 16-bit"),
    UINT32(10, 4, "Unsigned integer 32-bit"),
    UINT64(11, 8, "Unsigned integer 64-bit"),

    REAL32(12, 4, "IEEE 754 single-precision float"),
    REAL64(13, 8, "IEEE 754 double-precision float"),

    TIMEDATE32(14, 4, "32-bit time (seconds since epoch)"),
    TIMEDATE48(15, 6, "48-bit time (seconds + microseconds)"),
    TIMEDATE64(16, 8, "64-bit time (seconds + microseconds)");

    private static final Map<Integer, TrdpDataType> BY_ID = new HashMap<>();
    private static final Map<String, TrdpDataType> BY_NAME = new HashMap<>();

    static {
        for (TrdpDataType type : values()) {
            BY_ID.put(type.typeId, type);
            BY_NAME.put(type.name(), type);
        }
        // IEC 61375-2-3 aliases — same wire format as BOOL8 (type ID 1)
        BY_NAME.put("BITSET8", BOOL8);
        BY_NAME.put("ANTIVALENT8", BOOL8);
    }

    private final int typeId;
    private final int size;
    private final String description;

    TrdpDataType(int typeId, int size, String description) {
        this.typeId = typeId;
        this.size = size;
        this.description = description;
    }

    /**
     * Returns the IEC 61375-2-3 numeric type identifier (1..16).
     */
    public int getTypeId() {
        return typeId;
    }

    public int getSize() {
        return size;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Looks up a data type by its IEC 61375-2-3 numeric type identifier.
     *
     * @param typeId the numeric type ID (1..16)
     * @return the matching data type, or null if not a primitive type ID
     */
    public static TrdpDataType fromTypeId(int typeId) {
        return BY_ID.get(typeId);
    }

    /**
     * Looks up a data type by name, including IEC 61375-2-3 aliases
     * (BITSET8 and ANTIVALENT8 resolve to BOOL8).
     *
     * @param name the type name
     * @return the matching data type, or null if not found
     */
    public static TrdpDataType fromName(String name) {
        return BY_NAME.get(name);
    }
}
