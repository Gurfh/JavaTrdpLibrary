package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Service instance definition ({@code <instance>} element within a service device).
 * <p>
 * Identifies a specific instance of a service by ID and destination URI.
 */
public class ServiceInstance {

    private final int id;
    private final String dstUri;
    private final String name;

    public ServiceInstance(
            @JacksonXmlProperty(localName = "id", isAttribute = true) int id,
            @JacksonXmlProperty(localName = "dst-uri", isAttribute = true) String dstUri,
            @JacksonXmlProperty(localName = "name", isAttribute = true) String name) {
        this.id = id;
        this.dstUri = dstUri;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getDstUri() {
        return dstUri;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return String.format("ServiceInstance{id=%d, dstUri='%s'}", id, dstUri);
    }
}
