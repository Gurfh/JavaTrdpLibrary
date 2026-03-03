package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * A single element within a data set definition ({@code <element>} element).
 * <p>
 * The {@code type} is either a predefined name (e.g. "UINT32", "BOOL8") or a
 * numeric data set ID for nested types. The {@code array-size} defaults to 1;
 * 0 indicates a dynamically-sized array.
 */
public class DataSetElement {

    private final String type;
    private final String name;
    private final long arraySize;
    private final String unit;
    private final Float scale;
    private final Integer offset;

    public DataSetElement(
            @JacksonXmlProperty(localName = "type", isAttribute = true) String type,
            @JacksonXmlProperty(localName = "name", isAttribute = true) String name,
            @JacksonXmlProperty(localName = "array-size", isAttribute = true) Long arraySize,
            @JacksonXmlProperty(localName = "unit", isAttribute = true) String unit,
            @JacksonXmlProperty(localName = "scale", isAttribute = true) Float scale,
            @JacksonXmlProperty(localName = "offset", isAttribute = true) Integer offset) {
        this.type = type;
        this.name = name;
        this.arraySize = arraySize != null ? arraySize : 1;
        this.unit = unit;
        this.scale = scale;
        this.offset = offset;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public long getArraySize() {
        return arraySize;
    }

    public String getUnit() {
        return unit;
    }

    public Float getScale() {
        return scale;
    }

    public Integer getOffset() {
        return offset;
    }

    @Override
    public String toString() {
        return String.format("DataSetElement{type='%s', name='%s', arraySize=%d}", type, name, arraySize);
    }
}
