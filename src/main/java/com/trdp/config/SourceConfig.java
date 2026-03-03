package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Source filter for a telegram ({@code <source>} element).
 * <p>
 * Identifies the sender(s) by URI for PD/MD filtering on the receiver side.
 * May include optional SDTv2 or SDTv4 safety parameters.
 */
public class SourceConfig {

    private final long id;
    private final String uri1;
    private final String uri2;
    private final String name;
    private final SdtParameter sdtParameter;
    private final Sdtv4Parameter sdtv4Parameter;

    public SourceConfig(
            @JacksonXmlProperty(localName = "id", isAttribute = true) long id,
            @JacksonXmlProperty(localName = "uri1", isAttribute = true) String uri1,
            @JacksonXmlProperty(localName = "uri2", isAttribute = true) String uri2,
            @JacksonXmlProperty(localName = "name", isAttribute = true) String name,
            @JacksonXmlProperty(localName = "sdt-parameter") SdtParameter sdtParameter,
            @JacksonXmlProperty(localName = "sdtv4-parameter") Sdtv4Parameter sdtv4Parameter) {
        this.id = id;
        this.uri1 = uri1;
        this.uri2 = uri2;
        this.name = name;
        this.sdtParameter = sdtParameter;
        this.sdtv4Parameter = sdtv4Parameter;
    }

    public long getId() {
        return id;
    }

    public String getUri1() {
        return uri1;
    }

    public String getUri2() {
        return uri2;
    }

    public String getName() {
        return name;
    }

    public SdtParameter getSdtParameter() {
        return sdtParameter;
    }

    public Sdtv4Parameter getSdtv4Parameter() {
        return sdtv4Parameter;
    }

    @Override
    public String toString() {
        return String.format("SourceConfig{id=%d, uri1='%s'}", id, uri1);
    }
}
