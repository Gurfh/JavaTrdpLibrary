package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Service field definition ({@code <field>} element within a service).
 * <p>
 * Associates a field identifier with a ComID used for field access.
 */
public class ServiceField {

    private final int id;
    private final long comId;
    private final String name;

    public ServiceField(
            @JacksonXmlProperty(localName = "id", isAttribute = true) int id,
            @JacksonXmlProperty(localName = "com-id", isAttribute = true) long comId,
            @JacksonXmlProperty(localName = "name", isAttribute = true) String name) {
        this.id = id;
        this.comId = comId;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public long getComId() {
        return comId;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return String.format("ServiceField{id=%d, comId=%d}", id, comId);
    }
}
