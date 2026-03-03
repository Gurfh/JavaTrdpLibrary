package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Mapped destination override ({@code <mapped-destination>} element).
 * <p>
 * Overrides the destination URI and SDT parameters for a mapped telegram.
 */
public class MappedDestination {

    private final long id;
    private final String uri;
    private final String name;
    private final MappedSdtParameter mappedSdtParameter;

    public MappedDestination(
            @JacksonXmlProperty(localName = "id", isAttribute = true) long id,
            @JacksonXmlProperty(localName = "uri", isAttribute = true) String uri,
            @JacksonXmlProperty(localName = "name", isAttribute = true) String name,
            @JacksonXmlProperty(localName = "mapped-sdt-parameter") MappedSdtParameter mappedSdtParameter) {
        this.id = id;
        this.uri = uri;
        this.name = name;
        this.mappedSdtParameter = mappedSdtParameter;
    }

    public long getId() {
        return id;
    }

    public String getUri() {
        return uri;
    }

    public String getName() {
        return name;
    }

    public MappedSdtParameter getMappedSdtParameter() {
        return mappedSdtParameter;
    }

    @Override
    public String toString() {
        return String.format("MappedDestination{id=%d, uri='%s'}", id, uri);
    }
}
