package com.trdp.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class TrdpDecoder {
    
    private final ByteBuffer buffer;
    
    public TrdpDecoder(byte[] data) {
        this.buffer = ByteBuffer.wrap(data);
        this.buffer.order(ByteOrder.BIG_ENDIAN);
    }
    
    public boolean getBool8() {
        return buffer.get() != 0;
    }
    
    public char getChar8() {
        return (char) (buffer.get() & 0xFF);
    }
    
    public char getUtf16() {
        return buffer.getChar();
    }
    
    public byte getInt8() {
        return buffer.get();
    }
    
    public short getInt16() {
        return buffer.getShort();
    }
    
    public int getInt32() {
        return buffer.getInt();
    }
    
    public long getInt64() {
        return buffer.getLong();
    }
    
    public int getUInt8() {
        return buffer.get() & 0xFF;
    }
    
    public int getUInt16() {
        return buffer.getShort() & 0xFFFF;
    }
    
    public long getUInt32() {
        return buffer.getInt() & 0xFFFFFFFFL;
    }
    
    public long getUInt64() {
        return buffer.getLong();
    }
    
    public float getReal32() {
        return buffer.getFloat();
    }
    
    public double getReal64() {
        return buffer.getDouble();
    }
    
    public Instant getTimeDate32() {
        long seconds = buffer.getInt() & 0xFFFFFFFFL;
        return Instant.ofEpochSecond(seconds);
    }
    
    public Instant getTimeDate48() {
        long seconds = buffer.getInt() & 0xFFFFFFFFL;
        int microsLow = buffer.getShort() & 0xFFFF;
        return Instant.ofEpochSecond(seconds, microsLow * 1000L);
    }
    
    public Instant getTimeDate64() {
        long seconds = buffer.getInt() & 0xFFFFFFFFL;
        int micros = buffer.getInt();
        return Instant.ofEpochSecond(seconds, micros * 1000L);
    }
    
    public String getString(int length) {
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        
        int actualLength = 0;
        for (int i = 0; i < length; i++) {
            if (bytes[i] == 0) {
                break;
            }
            actualLength++;
        }
        
        return new String(bytes, 0, actualLength, StandardCharsets.UTF_8);
    }
    
    public byte[] getBytes(int length) {
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return bytes;
    }
    
    public void skip(int bytes) {
        buffer.position(buffer.position() + bytes);
    }
    
    public void align(int alignment) {
        int position = buffer.position();
        int padding = (alignment - (position % alignment)) % alignment;
        buffer.position(position + padding);
    }
    
    public int position() {
        return buffer.position();
    }
    
    public int remaining() {
        return buffer.remaining();
    }
    
    public void rewind() {
        buffer.rewind();
    }
}
