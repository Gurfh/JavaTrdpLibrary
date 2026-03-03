package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Per-telegram Process Data parameters ({@code <pd-parameter>} element).
 * <p>
 * Specifies the transmission cycle time (required), timeout, validity behavior,
 * redundancy group, marshalling, and callback settings for a PD telegram.
 * <p>
 * Defaults: timeout=0 (disabled), validity-behavior="zero", redundant=0,
 * marshall=off, callback=off, offset-address=0.
 */
public class PdParameter {

    private final long cycle;
    private final long timeout;
    private final String validityBehavior;
    private final long redundant;
    private final boolean marshall;
    private final String callback;
    private final long offsetAddress;

    public PdParameter(
            @JacksonXmlProperty(localName = "cycle", isAttribute = true) long cycle,
            @JacksonXmlProperty(localName = "timeout", isAttribute = true) Long timeout,
            @JacksonXmlProperty(localName = "validity-behavior", isAttribute = true) String validityBehavior,
            @JacksonXmlProperty(localName = "redundant", isAttribute = true) Long redundant,
            @JacksonXmlProperty(localName = "marshall", isAttribute = true) String marshall,
            @JacksonXmlProperty(localName = "callback", isAttribute = true) String callback,
            @JacksonXmlProperty(localName = "offset-address", isAttribute = true) Long offsetAddress) {
        this.cycle = cycle;
        this.timeout = timeout != null ? timeout : 0;
        this.validityBehavior = validityBehavior != null ? validityBehavior : "zero";
        this.redundant = redundant != null ? redundant : 0;
        this.marshall = "on".equalsIgnoreCase(marshall);
        this.callback = callback != null ? callback : "off";
        this.offsetAddress = offsetAddress != null ? offsetAddress : 0;
    }

    public long getCycle() {
        return cycle;
    }

    public long getTimeout() {
        return timeout;
    }

    public String getValidityBehavior() {
        return validityBehavior;
    }

    public long getRedundant() {
        return redundant;
    }

    public boolean isMarshall() {
        return marshall;
    }

    public String getCallback() {
        return callback;
    }

    public long getOffsetAddress() {
        return offsetAddress;
    }

    @Override
    public String toString() {
        return String.format("PdParameter{cycle=%d, timeout=%d, validityBehavior='%s'}",
                cycle, timeout, validityBehavior);
    }
}
