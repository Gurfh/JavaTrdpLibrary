package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Memory block definition ({@code <mem-block>} element).
 * <p>
 * Defines a preallocated memory pool of a specific block size.
 * Valid sizes are: 32, 72, 128, 256, 512, 1024, 1480, 2048, 4096,
 * 11520, 16384, 32768, 65536, 131072.
 */
public class MemBlock {

    private final long size;
    private final long preallocate;

    public MemBlock(
            @JacksonXmlProperty(localName = "size", isAttribute = true) long size,
            @JacksonXmlProperty(localName = "preallocate", isAttribute = true) Long preallocate) {
        this.size = size;
        this.preallocate = preallocate != null ? preallocate : 0;
    }

    public long getSize() {
        return size;
    }

    public long getPreallocate() {
        return preallocate;
    }

    @Override
    public String toString() {
        return String.format("MemBlock{size=%d, preallocate=%d}", size, preallocate);
    }
}
