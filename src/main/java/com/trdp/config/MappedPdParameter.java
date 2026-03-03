package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Mapped PD parameter override ({@code <mapped-pd-parameter>} element).
 * <p>
 * Overrides the offset address for a mapped telegram.
 * Default: offset-address=0.
 */
public class MappedPdParameter {

    private final int offsetAddress;

    public MappedPdParameter(
            @JacksonXmlProperty(localName = "offset-address", isAttribute = true) Integer offsetAddress) {
        this.offsetAddress = offsetAddress != null ? offsetAddress : 0;
    }

    public int getOffsetAddress() {
        return offsetAddress;
    }

    @Override
    public String toString() {
        return String.format("MappedPdParameter{offsetAddress=%d}", offsetAddress);
    }
}
