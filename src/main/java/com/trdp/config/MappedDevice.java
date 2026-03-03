package com.trdp.config;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Mapped device override ({@code <mapped-device>} element).
 * <p>
 * Provides per-remote-device overrides for bus interface and telegram
 * parameters (e.g. IP addresses, SDT parameters, PD offsets).
 *
 * @see DeviceConfig#getMappedDevices()
 */
public class MappedDevice {

    private final String hostName;
    private final String leaderName;
    private final List<MappedBusInterface> mappedBusInterfaces;

    public MappedDevice(
            @JacksonXmlProperty(localName = "host-name", isAttribute = true) String hostName,
            @JacksonXmlProperty(localName = "leader-name", isAttribute = true) String leaderName,
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "mapped-bus-interface") List<MappedBusInterface> mappedBusInterfaces) {
        this.hostName = hostName;
        this.leaderName = leaderName;
        this.mappedBusInterfaces = mappedBusInterfaces != null
                ? Collections.unmodifiableList(mappedBusInterfaces) : Collections.emptyList();
    }

    public String getHostName() {
        return hostName;
    }

    public String getLeaderName() {
        return leaderName;
    }

    public List<MappedBusInterface> getMappedBusInterfaces() {
        return mappedBusInterfaces;
    }

    @Override
    public String toString() {
        return String.format("MappedDevice{hostName='%s'}", hostName);
    }
}
