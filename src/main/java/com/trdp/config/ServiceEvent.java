package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Service event definition ({@code <event>} element within a service).
 * <p>
 * Associates an event identifier with a ComID and transport type (PD or MD).
 * Default type is "PD".
 */
public class ServiceEvent {

    private final int id;
    private final long comId;
    private final String type;
    private final String name;

    public ServiceEvent(
            @JacksonXmlProperty(localName = "id", isAttribute = true) int id,
            @JacksonXmlProperty(localName = "com-id", isAttribute = true) long comId,
            @JacksonXmlProperty(localName = "type", isAttribute = true) String type,
            @JacksonXmlProperty(localName = "name", isAttribute = true) String name) {
        this.id = id;
        this.comId = comId;
        this.type = type != null ? type : "PD";
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public long getComId() {
        return comId;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return String.format("ServiceEvent{id=%d, comId=%d, type='%s'}", id, comId, type);
    }
}
