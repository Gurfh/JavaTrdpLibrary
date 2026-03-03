package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Mapped SDT parameter override ({@code <mapped-sdt-parameter>} element).
 * <p>
 * Overrides Safety Message Identifiers (SMI1/SMI2) for a mapped
 * source or destination.
 * <p>
 * Default: smi2=0.
 */
public class MappedSdtParameter {

    private final long smi1;
    private final long smi2;

    public MappedSdtParameter(
            @JacksonXmlProperty(localName = "smi1", isAttribute = true) long smi1,
            @JacksonXmlProperty(localName = "smi2", isAttribute = true) Long smi2) {
        this.smi1 = smi1;
        this.smi2 = smi2 != null ? smi2 : 0;
    }

    public long getSmi1() {
        return smi1;
    }

    public long getSmi2() {
        return smi2;
    }

    @Override
    public String toString() {
        return String.format("MappedSdtParameter{smi1=%d, smi2=%d}", smi1, smi2);
    }
}
