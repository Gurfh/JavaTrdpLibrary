package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Destination for a telegram ({@code <destination>} element).
 * <p>
 * Identifies the target by URI (unicast or multicast address).
 * May include optional SDTv2 or SDTv4 safety parameters.
 */
public class DestinationConfig {

    private final long id;
    private final String uri;
    private final String name;
    private final SdtParameter sdtParameter;
    private final Sdtv4Parameter sdtv4Parameter;

    public DestinationConfig(
            @JacksonXmlProperty(localName = "id", isAttribute = true) long id,
            @JacksonXmlProperty(localName = "uri", isAttribute = true) String uri,
            @JacksonXmlProperty(localName = "name", isAttribute = true) String name,
            @JacksonXmlProperty(localName = "sdt-parameter") SdtParameter sdtParameter,
            @JacksonXmlProperty(localName = "sdtv4-parameter") Sdtv4Parameter sdtv4Parameter) {
        this.id = id;
        this.uri = uri;
        this.name = name;
        this.sdtParameter = sdtParameter;
        this.sdtv4Parameter = sdtv4Parameter;
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

    public SdtParameter getSdtParameter() {
        return sdtParameter;
    }

    public Sdtv4Parameter getSdtv4Parameter() {
        return sdtv4Parameter;
    }

    @Override
    public String toString() {
        return String.format("DestinationConfig{id=%d, uri='%s'}", id, uri);
    }
}
