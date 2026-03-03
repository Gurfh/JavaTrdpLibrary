package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Global communication parameter set ({@code <com-parameter>} element).
 * <p>
 * Referenced by telegrams via {@code com-parameter-id}. Defines QoS (0..7),
 * TTL (default 64), and retry count (default 2).
 *
 * @see DeviceConfig#getComParameterById(long)
 */
public class ComParameter {

    private final long id;
    private final long qos;
    private final long ttl;
    private final long retries;

    public ComParameter(
            @JacksonXmlProperty(localName = "id", isAttribute = true) long id,
            @JacksonXmlProperty(localName = "qos", isAttribute = true) long qos,
            @JacksonXmlProperty(localName = "ttl", isAttribute = true) Long ttl,
            @JacksonXmlProperty(localName = "retries", isAttribute = true) Long retries) {
        this.id = id;
        this.qos = qos;
        this.ttl = ttl != null ? ttl : 64;
        this.retries = retries != null ? retries : 2;
    }

    public long getId() {
        return id;
    }

    public long getQos() {
        return qos;
    }

    public long getTtl() {
        return ttl;
    }

    public long getRetries() {
        return retries;
    }

    @Override
    public String toString() {
        return String.format("ComParameter{id=%d, qos=%d, ttl=%d, retries=%d}", id, qos, ttl, retries);
    }
}
