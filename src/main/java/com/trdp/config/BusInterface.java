package com.trdp.config;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Configuration for a single network bus interface ({@code <bus-interface>} element).
 * <p>
 * Each bus interface has a network ID (1..4), an optional host IP for static addressing,
 * TRDP process settings, PD/MD communication parameters, and a list of telegrams.
 * Process, PD, and MD configuration are never null — if absent from XML, fully-defaulted
 * instances with IEC 61375-2-3 standard defaults are used.
 *
 * @see DeviceConfig#getBusInterfaces()
 * @see TelegramConfig
 */
public class BusInterface {

    private final int networkId;
    private final String name;
    private final String hostIp;
    private final String leaderIp;
    private final TrdpProcessConfig trdpProcess;
    private final PdComParameter pdComParameter;
    private final MdComParameter mdComParameter;
    private final List<TelegramConfig> telegrams;

    public BusInterface(
            @JacksonXmlProperty(localName = "network-id", isAttribute = true) int networkId,
            @JacksonXmlProperty(localName = "name", isAttribute = true) String name,
            @JacksonXmlProperty(localName = "host-ip", isAttribute = true) String hostIp,
            @JacksonXmlProperty(localName = "leader-ip", isAttribute = true) String leaderIp,
            @JacksonXmlProperty(localName = "trdp-process") TrdpProcessConfig trdpProcess,
            @JacksonXmlProperty(localName = "pd-com-parameter") PdComParameter pdComParameter,
            @JacksonXmlProperty(localName = "md-com-parameter") MdComParameter mdComParameter,
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "telegram") List<TelegramConfig> telegrams) {
        this.networkId = networkId;
        this.name = name;
        this.hostIp = hostIp;
        this.leaderIp = leaderIp;
        this.trdpProcess = trdpProcess != null ? trdpProcess
                : new TrdpProcessConfig(null, null, null, null, null);
        this.pdComParameter = pdComParameter != null ? pdComParameter
                : new PdComParameter(null, null, null, null, null, null, null);
        this.mdComParameter = mdComParameter != null ? mdComParameter
                : new MdComParameter(null, null, null, null, null, null, null, null, null, null, null, null);
        this.telegrams = telegrams != null ? Collections.unmodifiableList(telegrams) : Collections.emptyList();
    }

    public int getNetworkId() {
        return networkId;
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

    public TrdpProcessConfig getTrdpProcess() {
        return trdpProcess;
    }

    public PdComParameter getPdComParameter() {
        return pdComParameter;
    }

    public MdComParameter getMdComParameter() {
        return mdComParameter;
    }

    public List<TelegramConfig> getTelegrams() {
        return telegrams;
    }

    @Override
    public String toString() {
        return String.format("BusInterface{networkId=%d, name='%s', hostIp='%s'}", networkId, name, hostIp);
    }
}
