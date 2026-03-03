package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * TRDP process/thread configuration ({@code <trdp-process>} element).
 * <p>
 * Controls the cycle time, traffic shaping, blocking behavior, priority,
 * and VLAN ID for a bus interface's TRDP processing thread.
 * <p>
 * Defaults (applied when XML attributes are omitted): cycle-time=10000us,
 * traffic-shaping=on, blocking=no, priority=64, vlan-id=0.
 */
public class TrdpProcessConfig {

    private final long cycleTime;
    private final int vlanId;
    private final boolean blocking;
    private final boolean trafficShaping;
    private final long priority;

    public TrdpProcessConfig(
            @JacksonXmlProperty(localName = "cycle-time", isAttribute = true) Long cycleTime,
            @JacksonXmlProperty(localName = "vlan-id", isAttribute = true) Integer vlanId,
            @JacksonXmlProperty(localName = "blocking", isAttribute = true) String blocking,
            @JacksonXmlProperty(localName = "traffic-shaping", isAttribute = true) String trafficShaping,
            @JacksonXmlProperty(localName = "priority", isAttribute = true) Long priority) {
        this.cycleTime = cycleTime != null ? cycleTime : 10000;
        this.vlanId = vlanId != null ? vlanId : 0;
        this.blocking = "yes".equalsIgnoreCase(blocking);
        this.trafficShaping = trafficShaping == null || "on".equalsIgnoreCase(trafficShaping);
        this.priority = priority != null ? priority : 64;
    }

    public long getCycleTime() {
        return cycleTime;
    }

    public int getVlanId() {
        return vlanId;
    }

    public boolean isBlocking() {
        return blocking;
    }

    public boolean isTrafficShaping() {
        return trafficShaping;
    }

    public long getPriority() {
        return priority;
    }

    @Override
    public String toString() {
        return String.format("TrdpProcessConfig{cycleTime=%d, trafficShaping=%s, priority=%d}",
                cycleTime, trafficShaping, priority);
    }
}
