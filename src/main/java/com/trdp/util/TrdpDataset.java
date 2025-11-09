package com.trdp.util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TrdpDataset {
    
    private final List<Field> fields;
    
    public TrdpDataset() {
        this.fields = new ArrayList<>();
    }
    
    public TrdpDataset addBool8(String name, boolean value) {
        fields.add(new Field(name, TrdpDataType.BOOL8, value));
        return this;
    }
    
    public TrdpDataset addChar8(String name, char value) {
        fields.add(new Field(name, TrdpDataType.CHAR8, value));
        return this;
    }
    
    public TrdpDataset addInt8(String name, byte value) {
        fields.add(new Field(name, TrdpDataType.INT8, value));
        return this;
    }
    
    public TrdpDataset addInt16(String name, short value) {
        fields.add(new Field(name, TrdpDataType.INT16, value));
        return this;
    }
    
    public TrdpDataset addInt32(String name, int value) {
        fields.add(new Field(name, TrdpDataType.INT32, value));
        return this;
    }
    
    public TrdpDataset addInt64(String name, long value) {
        fields.add(new Field(name, TrdpDataType.INT64, value));
        return this;
    }
    
    public TrdpDataset addUInt8(String name, int value) {
        fields.add(new Field(name, TrdpDataType.UINT8, value));
        return this;
    }
    
    public TrdpDataset addUInt16(String name, int value) {
        fields.add(new Field(name, TrdpDataType.UINT16, value));
        return this;
    }
    
    public TrdpDataset addUInt32(String name, long value) {
        fields.add(new Field(name, TrdpDataType.UINT32, value));
        return this;
    }
    
    public TrdpDataset addReal32(String name, float value) {
        fields.add(new Field(name, TrdpDataType.REAL32, value));
        return this;
    }
    
    public TrdpDataset addReal64(String name, double value) {
        fields.add(new Field(name, TrdpDataType.REAL64, value));
        return this;
    }
    
    public TrdpDataset addTimeDate64(String name, Instant value) {
        fields.add(new Field(name, TrdpDataType.TIMEDATE64, value));
        return this;
    }
    
    public byte[] encode() {
        int totalSize = 0;
        for (Field field : fields) {
            totalSize += field.type.getSize();
        }
        
        TrdpEncoder encoder = new TrdpEncoder(totalSize);
        
        for (Field field : fields) {
            switch (field.type) {
                case BOOL8:
                    encoder.putBool8((Boolean) field.value);
                    break;
                case CHAR8:
                    encoder.putChar8((Character) field.value);
                    break;
                case INT8:
                    encoder.putInt8((Byte) field.value);
                    break;
                case INT16:
                    encoder.putInt16((Short) field.value);
                    break;
                case INT32:
                    encoder.putInt32((Integer) field.value);
                    break;
                case INT64:
                    encoder.putInt64((Long) field.value);
                    break;
                case UINT8:
                    encoder.putUInt8((Integer) field.value);
                    break;
                case UINT16:
                    encoder.putUInt16((Integer) field.value);
                    break;
                case UINT32:
                    encoder.putUInt32((Long) field.value);
                    break;
                case REAL32:
                    encoder.putReal32((Float) field.value);
                    break;
                case REAL64:
                    encoder.putReal64((Double) field.value);
                    break;
                case TIMEDATE64:
                    encoder.putTimeDate64((Instant) field.value);
                    break;
                default:
                    throw new IllegalStateException("Unsupported type: " + field.type);
            }
        }
        
        return encoder.toByteArray();
    }
    
    public static TrdpDataset decode(byte[] data, List<FieldDefinition> schema) {
        TrdpDataset dataset = new TrdpDataset();
        TrdpDecoder decoder = new TrdpDecoder(data);
        
        for (FieldDefinition def : schema) {
            switch (def.type) {
                case BOOL8:
                    dataset.addBool8(def.name, decoder.getBool8());
                    break;
                case CHAR8:
                    dataset.addChar8(def.name, decoder.getChar8());
                    break;
                case INT8:
                    dataset.addInt8(def.name, decoder.getInt8());
                    break;
                case INT16:
                    dataset.addInt16(def.name, decoder.getInt16());
                    break;
                case INT32:
                    dataset.addInt32(def.name, decoder.getInt32());
                    break;
                case INT64:
                    dataset.addInt64(def.name, decoder.getInt64());
                    break;
                case UINT8:
                    dataset.addUInt8(def.name, decoder.getUInt8());
                    break;
                case UINT16:
                    dataset.addUInt16(def.name, decoder.getUInt16());
                    break;
                case UINT32:
                    dataset.addUInt32(def.name, decoder.getUInt32());
                    break;
                case REAL32:
                    dataset.addReal32(def.name, decoder.getReal32());
                    break;
                case REAL64:
                    dataset.addReal64(def.name, decoder.getReal64());
                    break;
                case TIMEDATE64:
                    dataset.addTimeDate64(def.name, decoder.getTimeDate64());
                    break;
                default:
                    throw new IllegalStateException("Unsupported type: " + def.type);
            }
        }
        
        return dataset;
    }
    
    public Object getValue(String name) {
        for (Field field : fields) {
            if (field.name.equals(name)) {
                return field.value;
            }
        }
        throw new IllegalArgumentException("Field not found: " + name);
    }
    
    public List<Field> getFields() {
        return new ArrayList<>(fields);
    }
    
    public static class Field {
        private final String name;
        private final TrdpDataType type;
        private final Object value;
        
        public Field(String name, TrdpDataType type, Object value) {
            this.name = name;
            this.type = type;
            this.value = value;
        }
        
        public String getName() { return name; }
        public TrdpDataType getType() { return type; }
        public Object getValue() { return value; }
    }
    
    public static class FieldDefinition {
        private final String name;
        private final TrdpDataType type;
        
        public FieldDefinition(String name, TrdpDataType type) {
            this.name = name;
            this.type = type;
        }
        
        public String getName() { return name; }
        public TrdpDataType getType() { return type; }
    }
}
