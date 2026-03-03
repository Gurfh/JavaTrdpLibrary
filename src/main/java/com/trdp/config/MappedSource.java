package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Mapped source override ({@code <mapped-source>} element).
 * <p>
 * Overrides source URIs and SDT parameters for a mapped telegram.
 */
public class MappedSource {

    private final long id;
    private final String uri1;
    private final String uri2;
    private final String name;
    private final MappedSdtParameter mappedSdtParameter;

    public MappedSource(
            @JacksonXmlProperty(localName = "id", isAttribute = true) long id,
            @JacksonXmlProperty(localName = "uri1", isAttribute = true) String uri1,
            @JacksonXmlProperty(localName = "uri2", isAttribute = true) String uri2,
            @JacksonXmlProperty(localName = "name", isAttribute = true) String name,
            @JacksonXmlProperty(localName = "mapped-sdt-parameter") MappedSdtParameter mappedSdtParameter) {
        this.id = id;
        this.uri1 = uri1;
        this.uri2 = uri2;
        this.name = name;
        this.mappedSdtParameter = mappedSdtParameter;
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

    public MappedSdtParameter getMappedSdtParameter() {
        return mappedSdtParameter;
    }

    @Override
    public String toString() {
        return String.format("MappedSource{id=%d, uri1='%s'}", id, uri1);
    }
}
