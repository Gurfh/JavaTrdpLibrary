package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Default Process Data communication parameters ({@code <pd-com-parameter>} element).
 * <p>
 * Defines interface-level defaults for PD timeout, TTL, QoS, marshalling,
 * callback behavior, and port. Individual telegrams may override these
 * via their own {@link PdParameter}.
 * <p>
 * Defaults: timeout-value=100000us, ttl=64, qos=5, port=17224.
 */
public class PdComParameter {

    private final long timeoutValue;
    private final String validityBehavior;
    private final long ttl;
    private final long qos;
    private final boolean marshall;
    private final String callback;
    private final long port;

    public PdComParameter(
            @JacksonXmlProperty(localName = "timeout-value", isAttribute = true) Long timeoutValue,
            @JacksonXmlProperty(localName = "validity-behavior", isAttribute = true) String validityBehavior,
            @JacksonXmlProperty(localName = "ttl", isAttribute = true) Long ttl,
            @JacksonXmlProperty(localName = "qos", isAttribute = true) Long qos,
            @JacksonXmlProperty(localName = "marshall", isAttribute = true) String marshall,
            @JacksonXmlProperty(localName = "callback", isAttribute = true) String callback,
            @JacksonXmlProperty(localName = "port", isAttribute = true) Long port) {
        this.timeoutValue = timeoutValue != null ? timeoutValue : 100000;
        this.validityBehavior = validityBehavior != null ? validityBehavior : "zero";
        this.ttl = ttl != null ? ttl : 64;
        this.qos = qos != null ? qos : 5;
        this.marshall = "on".equalsIgnoreCase(marshall);
        this.callback = callback != null ? callback : "off";
        this.port = port != null ? port : 17224;
    }

    public long getTimeoutValue() {
        return timeoutValue;
    }

    public String getValidityBehavior() {
        return validityBehavior;
    }

    public long getTtl() {
        return ttl;
    }

    public long getQos() {
        return qos;
    }

    public boolean isMarshall() {
        return marshall;
    }

    public String getCallback() {
        return callback;
    }

    public long getPort() {
        return port;
    }

    @Override
    public String toString() {
        return String.format("PdComParameter{timeoutValue=%d, ttl=%d, qos=%d, port=%d}",
                timeoutValue, ttl, qos, port);
    }
}
