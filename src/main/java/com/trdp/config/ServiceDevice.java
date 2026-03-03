package com.trdp.config;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Service device binding ({@code <service-device>} element within a service).
 * <p>
 * Binds a service to a specific end device identified by source, destination,
 * and optional redundancy URIs. Contains service instance definitions.
 */
public class ServiceDevice {

    private final String srcUri;
    private final String dstUri;
    private final String redUri;
    private final List<ServiceInstance> instances;

    public ServiceDevice(
            @JacksonXmlProperty(localName = "src-uri", isAttribute = true) String srcUri,
            @JacksonXmlProperty(localName = "dst-uri", isAttribute = true) String dstUri,
            @JacksonXmlProperty(localName = "red-uri", isAttribute = true) String redUri,
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "instance") List<ServiceInstance> instances) {
        this.srcUri = srcUri;
        this.dstUri = dstUri;
        this.redUri = redUri;
        this.instances = instances != null ? Collections.unmodifiableList(instances) : Collections.emptyList();
    }

    public String getSrcUri() {
        return srcUri;
    }

    public String getDstUri() {
        return dstUri;
    }

    public String getRedUri() {
        return redUri;
    }

    public List<ServiceInstance> getInstances() {
        return instances;
    }

    @Override
    public String toString() {
        return String.format("ServiceDevice{srcUri='%s', dstUri='%s'}", srcUri, dstUri);
    }
}
