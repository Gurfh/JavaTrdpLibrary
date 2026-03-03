package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Default Message Data communication parameters ({@code <md-com-parameter>} element).
 * <p>
 * Defines interface-level defaults for MD timeouts, TTL, QoS, retries,
 * transport protocol, marshalling, callback behavior, and ports. Individual
 * telegrams may override these via their own {@link MdParameter}.
 * <p>
 * Defaults: confirm-timeout=1000000us, reply-timeout=5000000us,
 * connect-timeout=60000000us, ttl=64, qos=3, retries=2, protocol=UDP,
 * udp-port=17225, tcp-port=17225, num-sessions=1000.
 */
public class MdComParameter {

    private final long confirmTimeout;
    private final long replyTimeout;
    private final long connectTimeout;
    private final long ttl;
    private final long qos;
    private final long retries;
    private final String protocol;
    private final boolean marshall;
    private final String callback;
    private final long udpPort;
    private final long tcpPort;
    private final long numSessions;

    public MdComParameter(
            @JacksonXmlProperty(localName = "confirm-timeout", isAttribute = true) Long confirmTimeout,
            @JacksonXmlProperty(localName = "reply-timeout", isAttribute = true) Long replyTimeout,
            @JacksonXmlProperty(localName = "connect-timeout", isAttribute = true) Long connectTimeout,
            @JacksonXmlProperty(localName = "ttl", isAttribute = true) Long ttl,
            @JacksonXmlProperty(localName = "qos", isAttribute = true) Long qos,
            @JacksonXmlProperty(localName = "retries", isAttribute = true) Long retries,
            @JacksonXmlProperty(localName = "protocol", isAttribute = true) String protocol,
            @JacksonXmlProperty(localName = "marshall", isAttribute = true) String marshall,
            @JacksonXmlProperty(localName = "callback", isAttribute = true) String callback,
            @JacksonXmlProperty(localName = "udp-port", isAttribute = true) Long udpPort,
            @JacksonXmlProperty(localName = "tcp-port", isAttribute = true) Long tcpPort,
            @JacksonXmlProperty(localName = "num-sessions", isAttribute = true) Long numSessions) {
        this.confirmTimeout = confirmTimeout != null ? confirmTimeout : 1000000;
        this.replyTimeout = replyTimeout != null ? replyTimeout : 5000000;
        this.connectTimeout = connectTimeout != null ? connectTimeout : 60000000;
        this.ttl = ttl != null ? ttl : 64;
        this.qos = qos != null ? qos : 3;
        this.retries = retries != null ? retries : 2;
        this.protocol = protocol != null ? protocol : "UDP";
        this.marshall = "on".equalsIgnoreCase(marshall);
        this.callback = callback != null ? callback : "off";
        this.udpPort = udpPort != null ? udpPort : 17225;
        this.tcpPort = tcpPort != null ? tcpPort : 17225;
        this.numSessions = numSessions != null ? numSessions : 1000;
    }

    public long getConfirmTimeout() {
        return confirmTimeout;
    }

    public long getReplyTimeout() {
        return replyTimeout;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public long getTtl() {
        return ttl;
    }

    public long getQos() {
        return qos;
    }

    public long getRetries() {
        return retries;
    }

    public String getProtocol() {
        return protocol;
    }

    public boolean isMarshall() {
        return marshall;
    }

    public String getCallback() {
        return callback;
    }

    public long getUdpPort() {
        return udpPort;
    }

    public long getTcpPort() {
        return tcpPort;
    }

    public long getNumSessions() {
        return numSessions;
    }

    @Override
    public String toString() {
        return String.format("MdComParameter{confirmTimeout=%d, replyTimeout=%d, protocol='%s', udpPort=%d, tcpPort=%d}",
                confirmTimeout, replyTimeout, protocol, udpPort, tcpPort);
    }
}
