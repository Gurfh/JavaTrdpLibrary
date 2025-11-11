package com.trdp.util;

public final class FcsUtils {

    private FcsUtils() {
        // Prevent instantiation
    }

    /**
     * Calculates the Frame Check Sequence (FCS) using the CRC-32 algorithm as specified
     * in IEEE 802.3.
     *
     * @param data   the data to calculate the checksum for.
     * @param offset the starting offset in the byte array.
     * @param length the number of bytes to include in the calculation.
     * @return the calculated FCS value.
     */
    public static int calculateFcs(byte[] data, int offset, int length) {
        int crc = 0xFFFFFFFF;

        for (int i = offset; i < offset + length; i++) {
            crc ^= (data[i] & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0xEDB88320;
                } else {
                    crc = crc >>> 1;
                }
            }
        }

        return ~crc;
    }
}
