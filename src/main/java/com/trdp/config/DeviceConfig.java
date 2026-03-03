package com.trdp.config;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Root configuration object representing a TRDP {@code <device>} element.
 * <p>
 * Contains all configuration for a single TRDP device: bus interfaces (with their
 * telegrams and communication parameters), data set definitions, global communication
 * parameters, service definitions, and mapped device overrides.
 * <p>
 * Instances are created by {@link TrdpConfig#load(java.nio.file.Path)} or
 * {@link TrdpConfig#load(java.io.InputStream)}. All list getters return non-null,
 * unmodifiable lists.
 *
 * @see TrdpConfig
 * @see BusInterface
 * @see DataSetDefinition
 */
@JacksonXmlRootElement(localName = "device")
public class DeviceConfig {

    @JacksonXmlProperty(localName = "host-name", isAttribute = true)
    private String hostName;

    @JacksonXmlProperty(localName = "type", isAttribute = true)
    private String type;

    @JacksonXmlProperty(localName = "leader-name", isAttribute = true)
    private String leaderName;

    @JacksonXmlProperty(localName = "device-configuration")
    private DeviceConfiguration deviceConfiguration;

    @JacksonXmlProperty(localName = "debug")
    private DebugConfig debug;

    @JacksonXmlElementWrapper(localName = "bus-interface-list")
    @JacksonXmlProperty(localName = "bus-interface")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private List<BusInterface> busInterfaces = Collections.emptyList();

    @JacksonXmlElementWrapper(localName = "mapped-device-list")
    @JacksonXmlProperty(localName = "mapped-device")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private List<MappedDevice> mappedDevices = Collections.emptyList();

    @JacksonXmlElementWrapper(localName = "data-set-list")
    @JacksonXmlProperty(localName = "data-set")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private List<DataSetDefinition> dataSets = Collections.emptyList();

    @JacksonXmlElementWrapper(localName = "com-parameter-list")
    @JacksonXmlProperty(localName = "com-parameter")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private List<ComParameter> comParameters = Collections.emptyList();

    @JacksonXmlElementWrapper(localName = "service-list")
    @JacksonXmlProperty(localName = "service")
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private List<ServiceDefinition> services = Collections.emptyList();

    @JsonCreator
    private DeviceConfig() {
    }

    /** Returns the device host name (required, max 15 characters). */
    public String getHostName() {
        return hostName;
    }

    /** Returns the device type (e.g. "VCU-C"), or {@code null} if not specified. */
    public String getType() {
        return type;
    }

    /** Returns the leader device name for redundancy, or {@code null} if not specified. */
    public String getLeaderName() {
        return leaderName;
    }

    /** Returns the device-level configuration (memory settings), or {@code null} if not specified. */
    public DeviceConfiguration getDeviceConfiguration() {
        return deviceConfiguration;
    }

    /** Returns the debug configuration, or {@code null} if not specified. */
    public DebugConfig getDebug() {
        return debug;
    }

    /** Returns the bus interfaces defined for this device. */
    public List<BusInterface> getBusInterfaces() {
        return Collections.unmodifiableList(busInterfaces);
    }

    /** Returns the mapped device overrides. */
    public List<MappedDevice> getMappedDevices() {
        return Collections.unmodifiableList(mappedDevices);
    }

    /** Returns the data set definitions. */
    public List<DataSetDefinition> getDataSets() {
        return Collections.unmodifiableList(dataSets);
    }

    /** Returns the global communication parameter sets. */
    public List<ComParameter> getComParameters() {
        return Collections.unmodifiableList(comParameters);
    }

    /** Returns the service-oriented interface definitions. */
    public List<ServiceDefinition> getServices() {
        return Collections.unmodifiableList(services);
    }

    /**
     * Looks up a data set definition by its ID (first-found-wins).
     *
     * @param id the data set ID (must be &gt;= 1000 per XSD).
     * @return the matching data set, or empty if not found.
     */
    public Optional<DataSetDefinition> getDataSetById(int id) {
        return dataSets.stream()
                .filter(ds -> ds.getId() == id)
                .findFirst();
    }

    /**
     * Looks up a communication parameter set by its ID (first-found-wins).
     *
     * @param id the com-parameter ID.
     * @return the matching parameter set, or empty if not found.
     */
    public Optional<ComParameter> getComParameterById(long id) {
        return comParameters.stream()
                .filter(cp -> cp.getId() == id)
                .findFirst();
    }

    @Override
    public String toString() {
        return String.format("DeviceConfig{hostName='%s', busInterfaces=%d, dataSets=%d, comParameters=%d}",
                hostName, busInterfaces.size(), dataSets.size(), comParameters.size());
    }
}
