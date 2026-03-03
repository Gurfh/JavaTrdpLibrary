package com.trdp.config;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Mapped bus interface override ({@code <mapped-bus-interface>} element).
 * <p>
 * Overrides the host/leader IP and telegram parameters for a specific
 * bus interface on a mapped device.
 */
public class MappedBusInterface {

    private final String name;
    private final String hostIp;
    private final String leaderIp;
    private final List<MappedTelegram> mappedTelegrams;

    public MappedBusInterface(
            @JacksonXmlProperty(localName = "name", isAttribute = true) String name,
            @JacksonXmlProperty(localName = "host-ip", isAttribute = true) String hostIp,
            @JacksonXmlProperty(localName = "leader-ip", isAttribute = true) String leaderIp,
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "mapped-telegram") List<MappedTelegram> mappedTelegrams) {
        this.name = name;
        this.hostIp = hostIp;
        this.leaderIp = leaderIp;
        this.mappedTelegrams = mappedTelegrams != null
                ? Collections.unmodifiableList(mappedTelegrams) : Collections.emptyList();
    }

    public String getName() {
        return name;
    }

    public String getHostIp() {
        return hostIp;
    }

    public String getLeaderIp() {
        return leaderIp;
    }

    public List<MappedTelegram> getMappedTelegrams() {
        return mappedTelegrams;
    }

    @Override
    public String toString() {
        return String.format("MappedBusInterface{name='%s', hostIp='%s'}", name, hostIp);
    }
}
