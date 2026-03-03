package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Per-telegram Message Data parameters ({@code <md-parameter>} element).
 * <p>
 * Specifies confirm/reply timeouts, marshalling, callback, and transport
 * protocol for an MD telegram.
 * <p>
 * Defaults: confirm-timeout=0, reply-timeout=0, marshall=off, callback=off,
 * protocol=UDP.
 */
public class MdParameter {

    private final long confirmTimeout;
    private final long replyTimeout;
    private final boolean marshall;
    private final String callback;
    private final String protocol;

    public MdParameter(
            @JacksonXmlProperty(localName = "confirm-timeout", isAttribute = true) Long confirmTimeout,
            @JacksonXmlProperty(localName = "reply-timeout", isAttribute = true) Long replyTimeout,
            @JacksonXmlProperty(localName = "marshall", isAttribute = true) String marshall,
            @JacksonXmlProperty(localName = "callback", isAttribute = true) String callback,
            @JacksonXmlProperty(localName = "protocol", isAttribute = true) String protocol) {
        this.confirmTimeout = confirmTimeout != null ? confirmTimeout : 0;
        this.replyTimeout = replyTimeout != null ? replyTimeout : 0;
        this.marshall = "on".equalsIgnoreCase(marshall);
        this.callback = callback != null ? callback : "off";
        this.protocol = protocol != null ? protocol : "UDP";
    }

    public long getConfirmTimeout() {
        return confirmTimeout;
    }

    public long getReplyTimeout() {
        return replyTimeout;
    }

    public boolean isMarshall() {
        return marshall;
    }

    public String getCallback() {
        return callback;
    }

    public String getProtocol() {
        return protocol;
    }

    @Override
    public String toString() {
        return String.format("MdParameter{confirmTimeout=%d, replyTimeout=%d, protocol='%s'}",
                confirmTimeout, replyTimeout, protocol);
    }
}
