package com.trdp.config;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Device-level configuration ({@code <device-configuration>} element).
 * <p>
 * Specifies the total dynamic memory size and optional preallocated memory
 * block definitions.
 * <p>
 * Default: memory-size=4194304 (4 MB).
 */
public class DeviceConfiguration {

    @JacksonXmlProperty(localName = "memory-size", isAttribute = true)
    private long memorySize = 4194304;

    @JacksonXmlElementWrapper(localName = "mem-block-list")
    @JacksonXmlProperty(localName = "mem-block")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private List<MemBlock> memBlocks = Collections.emptyList();

    private DeviceConfiguration() {
    }

    public long getMemorySize() {
        return memorySize;
    }

    public List<MemBlock> getMemBlocks() {
        return Collections.unmodifiableList(memBlocks);
    }

    @Override
    public String toString() {
        return String.format("DeviceConfiguration{memorySize=%d, memBlocks=%d}", memorySize, memBlocks.size());
    }
}
