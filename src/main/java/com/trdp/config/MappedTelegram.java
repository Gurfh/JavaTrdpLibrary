package com.trdp.config;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Mapped telegram override ({@code <mapped-telegram>} element).
 * <p>
 * Overrides PD parameters, source URIs, and destination URIs for a
 * specific ComID on a mapped device.
 */
public class MappedTelegram {

    private final long comId;
    private final String name;
    private final MappedPdParameter mappedPdParameter;
    private final List<MappedSource> mappedSources;
    private final List<MappedDestination> mappedDestinations;

    public MappedTelegram(
            @JacksonXmlProperty(localName = "com-id", isAttribute = true) long comId,
            @JacksonXmlProperty(localName = "name", isAttribute = true) String name,
            @JacksonXmlProperty(localName = "mapped-pd-parameter") MappedPdParameter mappedPdParameter,
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "mapped-source") List<MappedSource> mappedSources,
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "mapped-destination") List<MappedDestination> mappedDestinations) {
        this.comId = comId;
        this.name = name;
        this.mappedPdParameter = mappedPdParameter;
        this.mappedSources = mappedSources != null
                ? Collections.unmodifiableList(mappedSources) : Collections.emptyList();
        this.mappedDestinations = mappedDestinations != null
                ? Collections.unmodifiableList(mappedDestinations) : Collections.emptyList();
    }

    public long getComId() {
        return comId;
    }

    public String getName() {
        return name;
    }

    public MappedPdParameter getMappedPdParameter() {
        return mappedPdParameter;
    }

    public List<MappedSource> getMappedSources() {
        return mappedSources;
    }

    public List<MappedDestination> getMappedDestinations() {
        return mappedDestinations;
    }

    @Override
    public String toString() {
        return String.format("MappedTelegram{comId=%d, name='%s'}", comId, name);
    }
}
