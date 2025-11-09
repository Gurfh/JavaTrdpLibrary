package com.trdp.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class TrdpEncoder {
    
    private final ByteBuffer buffer;
    
    public TrdpEncoder(int capacity) {
        this.buffer = ByteBuffer.allocate(capacity);
        this.buffer.order(ByteOrder.BIG_ENDIAN);
    }
    
    public TrdpEncoder putBool8(boolean value) {
        buffer.put((byte) (value ? 1 : 0));
        return this;
    }
    
    public TrdpEncoder putChar8(char value) {
        buffer.put((byte) value);
        return this;
    }
    
    public TrdpEncoder putUtf16(char value) {
        buffer.putChar(value);
        return this;
    }
    
    public TrdpEncoder putInt8(byte value) {
        buffer.put(value);
        return this;
    }
    
    public TrdpEncoder putInt16(short value) {
        buffer.putShort(value);
        return this;
    }
    
    public TrdpEncoder putInt32(int value) {
        buffer.putInt(value);
        return this;
    }
    
    public TrdpEncoder putInt64(long value) {
        buffer.putLong(value);
        return this;
    }
    
    public TrdpEncoder putUInt8(int value) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("UINT8 value must be 0-255");
        }
        buffer.put((byte) value);
        return this;
    }
    
    public TrdpEncoder putUInt16(int value) {
        if (value < 0 || value > 65535) {
            throw new IllegalArgumentException("UINT16 value must be 0-65535");
        }
        buffer.putShort((short) value);
        return this;
    }
    
    public TrdpEncoder putUInt32(long value) {
        if (value < 0 || value > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("UINT32 value must be 0-4294967295");
        }
        buffer.putInt((int) value);
        return this;
    }
    
    public TrdpEncoder putUInt64(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("UINT64 value must be non-negative");
        }
        buffer.putLong(value);
        return this;
    }
    
    public TrdpEncoder putReal32(float value) {
        buffer.putFloat(value);
        return this;
    }
    
    public TrdpEncoder putReal64(double value) {
        buffer.putDouble(value);
        return this;
    }
    
    public TrdpEncoder putTimeDate32(Instant timestamp) {
        long seconds = timestamp.getEpochSecond();
        buffer.putInt((int) seconds);
        return this;
    }
    
    public TrdpEncoder putTimeDate48(Instant timestamp) {
        long seconds = timestamp.getEpochSecond();
        int micros = timestamp.getNano() / 1000;
        buffer.putInt((int) seconds);
        buffer.putShort((short) (micros & 0xFFFF));
        return this;
    }
    
    public TrdpEncoder putTimeDate64(Instant timestamp) {
        long seconds = timestamp.getEpochSecond();
        int micros = timestamp.getNano() / 1000;
        buffer.putInt((int) seconds);
        buffer.putInt(micros);
        return this;
    }
    
    public TrdpEncoder putString(String value, int maxLength) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(bytes.length, maxLength);
        buffer.put(bytes, 0, length);
        for (int i = length; i < maxLength; i++) {
            buffer.put((byte) 0);
        }
        return this;
    }
    
    public TrdpEncoder putBytes(byte[] value) {
        buffer.put(value);
        return this;
    }
    
    public TrdpEncoder align(int alignment) {
        int position = buffer.position();
        int padding = (alignment - (position % alignment)) % alignment;
        for (int i = 0; i < padding; i++) {
            buffer.put((byte) 0);
        }
        return this;
    }
    
    public byte[] toByteArray() {
        byte[] result = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(result);
        return result;
    }
    
    public int position() {
        return buffer.position();
    }
    
    public void reset() {
        buffer.clear();
        buffer.order(ByteOrder.BIG_ENDIAN);
    }
}
